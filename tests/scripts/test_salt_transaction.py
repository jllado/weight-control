"""Exercise guarded enrichment on disposable MariaDB, including exact replay."""
import subprocess
import time
import unittest
import uuid

from scripts.nutrients.salt_transaction import build_transaction


class SaltTransactionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.container = 'salt-rehearsal-' + uuid.uuid4().hex[:10]
        subprocess.run(['docker', 'run', '--rm', '-d', '--name', cls.container,
                        '-e', 'MARIADB_ROOT_PASSWORD=synthetic-test-only', '-e', 'MARIADB_DATABASE=salt',
                        'mariadb:11.8'], check=True, capture_output=True)
        cls.addClassCleanup(lambda: subprocess.run(['docker', 'rm', '-f', cls.container], check=True, capture_output=True))
        for _ in range(100):
            result = cls.query('SELECT 1;')
            if result.returncode == 0:
                break
            time.sleep(.2)
        else:
            raise AssertionError('Disposable MariaDB did not become ready')

    @classmethod
    def query(cls, sql):
        return subprocess.run(['docker', 'exec', '-i', cls.container, 'mariadb', '-uroot',
                               '-psynthetic-test-only', '--batch', '--raw', '--skip-column-names', 'salt'],
                              input=sql, text=True, capture_output=True)

    def setUp(self):
        self.schema = {}
        self.inventory = {'meals': [{'id': 10, 'user_id': 1}], 'dish_recipes': [{'id': 20, 'user_id': 1}]}
        setup = ['DROP TABLE IF EXISTS catalog_foods,meal_dishes,recipe_ingredients,meals,dish_recipes,users;',
                 'CREATE TABLE users(id BIGINT PRIMARY KEY); INSERT INTO users VALUES(1),(2);']
        for table, owner in (('meals', 'user_id'), ('dish_recipes', 'user_id'), ('catalog_foods', 'user_id'),
                             ('meal_dishes', 'meal_id'), ('recipe_ingredients', 'recipe_id')):
            fields = {'id': 'BIGINT PRIMARY KEY', owner: 'BIGINT'}
            if table not in ('meals', 'dish_recipes'):
                fields.update(name='VARCHAR(255)', quantity='DECIMAL(11,3)', reference_quantity='DECIMAL(11,3)',
                              deleted='BOOLEAN', salt_grams='DECIMAL(10,2)', reference_salt_grams='DECIMAL(10,2)',
                              salt_source='VARCHAR(500)', salt_estimated='BOOLEAN')
                self.inventory[table] = [{'id': 1, owner: {'user_id':1,'meal_id':10,'recipe_id':20}[owner],
                                         'name': "Frozen food 'quoted'", 'quantity': 50, 'reference_quantity': 100, 'deleted': True,
                                         'salt_grams': None, 'reference_salt_grams': None, 'salt_source': None, 'salt_estimated': None}]
            self.schema[table] = list(fields)
            setup.append('CREATE TABLE '+table+'('+','.join(k+' '+v for k,v in fields.items())+');')
            from scripts.nutrients.salt_transaction import literal
            for row in self.inventory[table]:
                setup.append('INSERT INTO '+table+' VALUES('+','.join(literal(row[k]) for k in fields)+');')
        self.assertEqual(0, self.query('\n'.join(setup)).returncode)
        self.manifest = {'records':[{'kind':kind,'id':1,'status':'estimated','salt':{'saltGrams':'0.50','referenceSaltGrams':'1.00',
                            'saltSource':"Reviewed label's sodium conversion",'saltEstimated':True}}
                           for kind in ('catalog_food','meal_snapshot','recipe_ingredient')]}

    def execute(self, commit=True, **kwargs):
        return self.query(build_transaction(self.inventory,self.schema,self.manifest,kwargs.get('owner_id',1),commit=commit))

    def values(self):
        return self.query("SELECT salt_grams FROM catalog_foods UNION ALL SELECT salt_grams FROM meal_dishes UNION ALL SELECT salt_grams FROM recipe_ingredients;").stdout.strip().splitlines()

    def test_dry_run_commit_exact_replay_and_other_fields(self):
        self.assertEqual(0,self.execute(commit=False).returncode)
        self.assertEqual(['NULL']*3,self.values())
        result=self.execute();self.assertEqual(0,result.returncode,result.stderr)
        self.assertEqual(['0.50']*3,self.values())
        result=self.execute();self.assertEqual(0,result.returncode,result.stderr)
        self.assertEqual(3,result.stdout.count('\t0\n'))
        self.assertEqual("Frozen food 'quoted'\t50.000\t1\n",self.query('SELECT name,quantity,deleted FROM catalog_foods;').stdout)

    def test_stale_wrong_owner_and_mixed_state_fail_without_writes(self):
        self.assertNotEqual(0,self.execute(owner_id=2).returncode)
        self.assertEqual(['NULL']*3,self.values())
        self.query("UPDATE catalog_foods SET name='Changed after inventory';")
        self.assertNotEqual(0,self.execute().returncode)
        self.assertEqual(['NULL']*3,self.values())
        self.setUp()
        self.query("UPDATE catalog_foods SET salt_grams=.5,reference_salt_grams=1,salt_source='Other value',salt_estimated=1;")
        self.assertNotEqual(0,self.execute().returncode)
        self.assertEqual(['0.50','NULL','NULL'],self.values())

    def test_preserves_known_and_unresolved_rows(self):
        row=self.inventory['catalog_foods'][0];row.update(salt_grams=0,reference_salt_grams=0,salt_source='Known zero',salt_estimated=False)
        self.query("UPDATE catalog_foods SET salt_grams=0,reference_salt_grams=0,salt_source='Known zero',salt_estimated=0;")
        self.manifest['records'][0]['status']='alreadyPopulated'
        self.manifest['records'][2].update(status='unresolved',reason='Unknown preparation')
        result=self.execute();self.assertEqual(0,result.returncode,result.stderr)
        self.assertEqual(['0.00','0.50','NULL'],self.values())

    def test_empty_inventory_and_empty_candidate_set(self):
        self.manifest['records']=[{**r,'status':'unresolved','reason':'Unknown recipe'} for r in self.manifest['records']]
        result=self.execute();self.assertEqual(0,result.returncode,result.stderr)
        self.query('DELETE FROM catalog_foods;DELETE FROM meal_dishes;DELETE FROM recipe_ingredients;DELETE FROM meals;DELETE FROM dish_recipes;')
        self.inventory={k:[] for k in self.inventory};self.manifest={'records':[]}
        result=self.execute();self.assertEqual(0,result.returncode,result.stderr)

    def test_new_row_invalidates_frozen_full_inventory(self):
        self.query("INSERT INTO catalog_foods(id,user_id,name) VALUES(2,1,'New food');")
        self.assertNotEqual(0,self.execute().returncode)
        self.assertEqual(['NULL']*4,self.values())
