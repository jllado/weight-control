import fs from 'node:fs/promises';
import path from 'node:path';

const timeZone = 'Europe/Madrid';
const backupBaseDir = path.resolve('backups/current');
const inputFiles = {
    front: path.join(backupBaseDir, 'photos_front.html'),
    left: path.join(backupBaseDir, 'photos_left.html'),
    right: path.join(backupBaseDir, 'photos_right.html')
};
const backupHtmlPath = path.join(backupBaseDir, 'backup.html');
const outputBaseDir = path.join(backupBaseDir, 'import');
const outputPhotosDir = path.join(outputBaseDir, 'photos');
const outputMappingJsonPath = path.join(outputBaseDir, 'weight_photo_mapping.json');
const outputMappingCsvPath = path.join(outputBaseDir, 'weight_photo_mapping.csv');

const weights = await loadWeights();
const weightsByKey = new Map(weights.map(weight => [buildWeightKey(weight), weight]));
const sideEntries = Object.fromEntries(
    await Promise.all(Object.entries(inputFiles).map(async ([side, filePath]) => [side, await parsePhotoBackup(filePath)]))
);

const rowsByKey = new Map();

for (const [side, entries] of Object.entries(sideEntries)) {
    for (const entry of entries) {
        const key = `${entry.date}|${entry.weight}`;
        let row = rowsByKey.get(key);

        if (!row) {
            const weightRecord = weightsByKey.get(key);
            row = {
                key,
                date: entry.date,
                weight: entry.weight,
                fatPercentage: entry.fatPercentage,
                firebaseWeightId: weightRecord?.id ?? null,
                firebaseUser: weightRecord?.user ?? null,
                weightDateIso: weightRecord?.date ?? null,
                frontOriginalUrl: null,
                leftOriginalUrl: null,
                rightOriginalUrl: null,
                frontLocalPath: null,
                leftLocalPath: null,
                rightLocalPath: null
            };
            rowsByKey.set(key, row);
        }

        row[`${side}OriginalUrl`] = entry.url;
    }
}

await fs.mkdir(outputPhotosDir, { recursive: true });
await Promise.all(['front', 'left', 'right'].map(side => fs.mkdir(path.join(outputPhotosDir, side), { recursive: true })));

for (const row of rowsByKey.values()) {
    for (const side of ['front', 'left', 'right']) {
        const url = row[`${side}OriginalUrl`];

        if (!url) {
            continue;
        }

        const fileName = decodeURIComponent(new URL(url).pathname.split('/').pop());
        const outputPath = path.join(outputPhotosDir, side, fileName);
        const relativeOutputPath = path.relative(outputBaseDir, outputPath);
        row[`${side}LocalPath`] = relativeOutputPath;

        try {
            await fs.access(outputPath);
            console.log(`Skipped existing ${side} ${fileName}`);
        } catch {
            const response = await fetch(url);

            if (!response.ok) {
                throw new Error(`Failed to download ${url}: ${response.status} ${response.statusText}`);
            }

            const bytes = Buffer.from(await response.arrayBuffer());
            await fs.writeFile(outputPath, bytes);
            console.log(`Downloaded ${side} ${fileName}`);
        }
    }
}

const mapping = [...rowsByKey.values()].sort((a, b) => {
    const [dayA, monthA, yearA] = a.date.split('/').map(Number);
    const [dayB, monthB, yearB] = b.date.split('/').map(Number);
    return new Date(yearA, monthA - 1, dayA) - new Date(yearB, monthB - 1, dayB);
});

await fs.writeFile(outputMappingJsonPath, `${JSON.stringify(mapping, null, 2)}\n`);
await fs.writeFile(outputMappingCsvPath, buildCsv(mapping));

const unmatched = mapping.filter(row => row.firebaseWeightId === null);

console.log(`Created ${mapping.length} mapping rows`);
console.log(`Unmatched rows: ${unmatched.length}`);
console.log(`Mapping JSON: ${outputMappingJsonPath}`);
console.log(`Mapping CSV: ${outputMappingCsvPath}`);

function buildWeightKey(weight) {
    return `${formatDate(weight.date)}|${normalizeNumber(weight.weight)}`;
}

async function loadWeights() {
    const html = await fs.readFile(backupHtmlPath, 'utf8');
    const sections = extractBackupSections(html);

    if (sections.WEIGHTS) {
        return JSON.parse(sections.WEIGHTS);
    }

    throw new Error(`WEIGHTS section not found in ${backupHtmlPath}`);
}

function formatDate(isoDate) {
    return new Intl.DateTimeFormat('en-GB', {
        timeZone,
        day: '2-digit',
        month: '2-digit',
        year: 'numeric'
    }).format(new Date(isoDate));
}

function normalizeNumber(value) {
    return Number.parseFloat(value).toString();
}

function extractBackupSections(html) {
    const labels = ['WEIGHTS', 'BLOOD PRESSURES', 'HABITS', 'ROUTINES'];
    const markers = labels
        .map(label => ({ label, token: `<h1>${label}</h1>` }))
        .map(({ label, token }) => ({ label, start: html.indexOf(token), token }))
        .filter(section => section.start !== -1)
        .sort((a, b) => a.start - b.start);
    const sections = {};

    for (let index = 0; index < markers.length; index++) {
        const current = markers[index];
        const next = markers[index + 1];
        const contentStart = current.start + current.token.length;
        const contentEnd = next ? next.start : html.indexOf('</div><div class="p-toast', contentStart);

        if (contentEnd === -1) {
            continue;
        }

        sections[current.label] = html.slice(contentStart, contentEnd).trim();
    }

    return sections;
}

async function parsePhotoBackup(filePath) {
    const html = await fs.readFile(filePath, 'utf8');
    const pattern = /<div class="center">(\d{2}\/\d{2}\/\d{4})<\/div><div class="center">([0-9.]+) kg \(([0-9.]+)%\)\s*<\/div><div>(?:<!---->){0,2}<img(?: src="([^"]+)")?[^>]*>/g;
    const entries = [];

    for (const match of html.matchAll(pattern)) {
        entries.push({
            date: match[1],
            weight: normalizeNumber(match[2]),
            fatPercentage: normalizeNumber(match[3]),
            url: match[4] ? match[4].replaceAll('&amp;', '&') : null
        });
    }

    return entries;
}

function buildCsv(rows) {
    const headers = [
        'date',
        'weight',
        'fat_percentage',
        'firebase_weight_id',
        'firebase_user',
        'weight_date_iso',
        'front_original_url',
        'front_local_path',
        'left_original_url',
        'left_local_path',
        'right_original_url',
        'right_local_path'
    ];
    const lines = [headers.join(',')];

    for (const row of rows) {
        lines.push([
            row.date,
            row.weight,
            row.fatPercentage,
            row.firebaseWeightId,
            row.firebaseUser,
            row.weightDateIso,
            row.frontOriginalUrl,
            row.frontLocalPath,
            row.leftOriginalUrl,
            row.leftLocalPath,
            row.rightOriginalUrl,
            row.rightLocalPath
        ].map(toCsvValue).join(','));
    }

    return `${lines.join('\n')}\n`;
}

function toCsvValue(value) {
    if (value === null) {
        return '';
    }

    const stringValue = String(value);
    return /[",\n]/.test(stringValue) ? `"${stringValue.replaceAll('"', '""')}"` : stringValue;
}
