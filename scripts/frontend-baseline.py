#!/usr/bin/env python3
"""Capture source inventory and existing production artifacts; does not build the app."""
import collections
import gzip
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/frontend-modernization/evidence'
OUT.mkdir(exist_ok=True)

def write(name, value):
    (OUT / name).write_text(json.dumps(value, indent=2, sort_keys=True) + '\n')

def relative(path):
    return str(path.relative_to(ROOT))

sources = {relative(p): p.read_text() for p in (ROOT / 'src').rglob('*') if p.suffix in ('.vue', '.js', '.css')}
imports = re.compile(r'''(?:from\s*|import\s*\(\s*|require\s*\(\s*|import\s*)['"]([^'"]+)['"]''')
graph = {}
for file, source in sources.items():
    graph[file] = []
    for target in imports.findall(source):
        if target.startswith('@/'):
            base = ROOT / 'src' / target[2:]
        elif target.startswith('.'):
            base = (ROOT / file).parent / target
        else:
            continue
        for candidate in [base, Path(str(base) + '.vue'), Path(str(base) + '.js')]:
            if candidate.is_file():
                graph[file].append(relative(candidate.resolve()))
                break

def descendants(file, seen=None, blocked=()):
    seen = set() if seen is None else seen
    if file not in seen and file not in blocked:
        seen.add(file)
        for child in graph.get(file, []):
            descendants(child, seen, blocked)
    return seen

router = sources['src/router.js']
route_imports = dict(re.findall(r'''import\s+(\w+)\s+from\s+['"](@/components/[^'"]+)''', router))
routes = {}
for path, component in re.findall(r'''path:\s*['"]([^'"]+)['"][\s\S]*?component:\s*([^,\n}]+)''', router):
    target = route_imports.get(component.strip())
    if not target:
        match = re.search(r'''import\(['"]([^'"]+)''', component)
        target = match[1] if match else None
    if target:
        file = 'src/' + target[2:]
        if file not in sources:
            file += '.vue'
        routes[path] = {'entry': file, 'files': sorted(descendants(file)), 'lazy': 'import(' in component}
shell = descendants('src/App.vue', blocked=('src/router.js',))

def owners(file):
    return (['app shell'] if file in shell else []) + [path for path, route in routes.items() if file in route['files']]

prime_imports = {}
for file, source in sources.items():
    for alias, module in re.findall(r'''import\s+(\w+)\s+from\s+['"](primevue/[^'"]+)''', source):
        item = prime_imports.setdefault(module, {'aliases': set(), 'imports': set(), 'consumers': []})
        item['aliases'].add(alias)
        item['imports'].add(file)
for module, item in prime_imports.items():
    for file, source in sources.items():
        if any(re.search(r'<'+alias+r'(?:\s|/?>)', source) for alias in item['aliases']):
            item['consumers'].append({'file': file, 'routes': owners(file)})
    item['aliases'] = sorted(item['aliases'])
    item['imports'] = sorted(item['imports'])
    item['kind'] = 'component' if item['consumers'] else 'registration/service/config (inspect imports)'
css = (ROOT / 'node_modules/primeflex/primeflex.css').read_text()
flex = set(re.findall(r'\.([\w-]+)', css))
classes = collections.defaultdict(set)
icons = collections.defaultdict(set)
directives = collections.defaultdict(set)
for file, source in sources.items():
    for token in set(re.findall(r'\bp-[\w-]+', source)) & flex:
        classes[token].add(file)
    for token in set(re.findall(r'\bpi-[\w-]+', source)):
        icons[token].add(file)
    for token in set(re.findall(r'\bv-[\w-]+', source)):
        directives[token].add(file)
write('source-inventory.json', {'sourceRevision': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(), 'routes': routes, 'primevue': prime_imports, 'primeflex': {k: sorted(v) for k,v in classes.items()}, 'primeicons': {k: sorted(v) for k,v in icons.items()}, 'vueDirectives': {k: sorted(v) for k,v in directives.items()}, 'styles': [{'file': f, 'import': i} for f,s in sources.items() for i in imports.findall(s) if i.endswith('.css')], 'method': 'Static imports and template tags; routes follow transitive local imports. Utility/icon tokens include literal conditional strings; inspect computed expressions separately.'})
manifest = json.loads((ROOT / 'package.json').read_text())
packages = {}
def resolve(name, parent):
    for folder in [parent, *parent.parents]:
        candidate = folder / 'node_modules' / name / 'package.json'
        if candidate.exists():
            return candidate
    raise FileNotFoundError(name)

def visit(name, parent, recursive=True):
    path = resolve(name, parent)
    key = relative(path.parent)
    if key in packages:
        return
    meta = json.loads(path.read_text())
    notices = [p for p in path.parent.iterdir() if p.is_file() and re.match(r'(?i)^(licen[cs]e|copying|notice|copyright)([.-].*)?$',p.name)]
    if not notices and meta['name'].startswith(('@vue/', 'vue')):
        notices = [ROOT / 'node_modules/vue/LICENSE']
    packages[key] = {'name': meta['name'], 'version': meta['version'], 'license': meta.get('license', meta.get('licenses', 'UNDECLARED')), 'notices': [relative(p) for p in sorted(notices)], 'dependencies': meta.get('dependencies', {})}
    if recursive:
        for child in meta.get('dependencies', {}):
            visit(child, path.parent)
for name in manifest['dependencies']:
    visit(name, ROOT)
# vue-loader's export helper appears in the distributed source maps.
visit('vue-loader', ROOT, recursive=False)
for name in ('workbox-core', 'workbox-routing', 'workbox-strategies', 'workbox-precaching'):
    visit(name, ROOT)
write('dependencies.json', {'direct': {group: {name: {'requested': version, 'installed': json.loads(resolve(name, ROOT).read_text())['version'], 'license': json.loads(resolve(name, ROOT).read_text()).get('license', 'UNDECLARED')} for name, version in manifest[group].items()} for group in ('dependencies','devDependencies')}, 'productionClosure': packages, 'note': 'Installed production dependency closure plus the bundled vue-loader export helper and Workbox runtime modules. Not every package is bundled; bundled source-map package names were checked against this inventory. Build-only dependency trees are outside the runtime notice inventory.'})
notice_text = ['Third-party notices — Weight Control frontend baseline\n\nInstalled production dependency closure and bundled vue-loader/Workbox helpers; includes packages that may be tree-shaken.\nAnyChart has a separate proprietary license; this notice does not grant a license.\n']
for key, meta in sorted(packages.items()):
    notice_text.append('\n'+'='*72+'\n'+meta['name']+' '+meta['version']+'\nLicense metadata: '+str(meta['license'])+'\n')
    if meta['name'] == 'mitt':
        notice_text.append('MIT License, Copyright (c) Jason Miller (attribution from installed README).\n' + 'Permission is hereby granted' + (ROOT / 'node_modules/vue/LICENSE').read_text().split('Permission is hereby granted', 1)[1])
    elif not meta['notices']:
        notice_text.append('No standalone notice file in the installed package; retain upstream source headers and review the package metadata.\n')
    for file in meta['notices']:
        notice_text.append('\n'+Path(file).name+'\n'+(ROOT/file).read_text(errors='replace')+'\n')
(ROOT / 'public/third-party-notices.txt').write_text(''.join(notice_text).rstrip() + '\n')
assets = []
for path in sorted((ROOT / 'dist').rglob('*')):
    if path.is_file():
        data = path.read_bytes()
        assets.append({'path': str(path.relative_to(ROOT / 'dist')), 'bytes': len(data), 'gzipBytes': len(gzip.compress(data, mtime=0)), 'sha256': hashlib.sha256(data).hexdigest()})
html = (ROOT/'dist/index.html').read_text()
initial = sorted(set(re.findall(r'(?:src|href)=["\']?(/(?:js|css)/[^"\' >]+)',html)))
contributions = collections.Counter()
for path in (ROOT / 'dist').rglob('*.js.map'):
    data = json.loads(path.read_text())
    for source, content in zip(data['sources'], data.get('sourcesContent', [])):
        if content and '/node_modules/' in source:
            name = source.split('/node_modules/')[-1].split('/')
            package = '/'.join(name[:2]) if name[0].startswith('@') else name[0]
            contributions[package] += len(content.encode())
write('assets.json', {'assets': assets, 'htmlAssetReferences': initial, 'packageSourceBytes': dict(contributions.most_common()), 'method': 'Raw and independent gzip bytes; source-map package sizes are unminified attribution estimates, not additive compressed bundle sizes. HTML references may include prefetch; see baseline for initial vs deferred classification.'})
print(f'Captured {len(routes)} routes, {len(prime_imports)} PrimeVue imports, {len(classes)} utilities, {len(icons)} icons, {len(packages)} production packages, {len(assets)} assets.')
