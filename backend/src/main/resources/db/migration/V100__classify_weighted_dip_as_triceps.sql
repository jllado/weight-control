-- Task #420: use Triceps as the dip's single primary group in current and historical balances.
update exercises set primary_muscle_group = 'TRICEPS' where lower(name) = 'weighted dip';
