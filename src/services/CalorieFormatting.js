export function formatCalories(value) {
    return value === null ? '—' : new Intl.NumberFormat('en-GB', {minimumFractionDigits: 2, maximumFractionDigits: 2}).format(value);
}
