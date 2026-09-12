#!/usr/bin/env python3
"""Retain reviewed synthetic screenshots and exact source/build provenance."""
import gzip
import hashlib
import json
import pathlib
import shutil

spike = pathlib.Path(__file__).resolve().parent
root = spike.parent.parent
output = root / 'docs/frontend-modernization/evidence/milestone-3'
output.mkdir(parents=True, exist_ok=True)
evidence = root / 'tmp/ui-library-spike/evidence'
variants = {}
for variant in ['reference', 'candidate']:
    build = spike / 'dist' / variant
    files = [{'file': str(path.relative_to(build)), 'bytes': path.stat().st_size,
              'gzipBytes': len(gzip.compress(path.read_bytes(), mtime=0)),
              'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
             for path in sorted(build.rglob('*')) if path.suffix in ['.js', '.css']]
    variants[variant] = {'files': files, 'rawBytes': sum(item['bytes'] for item in files),
                         'gzipBytes': sum(item['gzipBytes'] for item in files)}
    for width in [390, 575, 640, 960, 1280]:
        shutil.copy2(evidence / f'{variant}-{width}.json', output)
        for scene in ['history', 'form', 'date', 'workout', 'chart']:
            shutil.copy2(evidence / f'{variant}-{scene}-{width}.png', output)
(output / 'bundles.json').write_text(json.dumps(variants, indent=2) + '\n')
source = [path for path in spike.rglob('*') if path.is_file() and
          not set(path.relative_to(spike).parts).intersection({'node_modules', 'dist', 'test-results', 'playwright-report'})]
source.append(root / 'src/components/SaveFields.vue')
manifest = {'capturedDate': '2026-09-12', 'productionReference': '0da94488c52de17b5d6cb9e54365eceee44f61a8',
            'reference': {'primevue': '3.38.1', 'theme': 'Nova', 'vue': '3.5.42'},
            'candidate': {'primevue': '4.5.5', 'theme': 'customized Lara 2.0.3', 'vue': '3.5.42'},
            'source': {str(path.relative_to(root)): hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted(source)},
            'screenshots': {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted(output.glob('*.png'))}}
(output / 'capture.json').write_text(json.dumps(manifest, indent=2) + '\n')
html = ['<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Milestone 3 comparison</title>',
        '<style>body{font:16px system-ui;margin:24px;color:#222}h1{font-size:24px}.pair{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}img{max-width:100%;border:1px solid #ccc}figure{margin:0}figcaption{margin:8px 0}section{margin:24px 0}a{color:#006bc0}</style>',
        '<h1>Milestone 3: controlled widget comparison</h1><p>Synthetic data · 2026-09-12 · Vue 3.5.42 in both builds. Candidate appearance is not accepted for production.</p>',
        '<p><a href="../../milestone-3.md">Decision and limitations</a></p>']
for width in [390, 575, 640, 960, 1280]:
    for scene in ['history', 'form', 'date', 'workout', 'chart']:
        html.append(f'<section><h2>{scene.title()} · {width}px</h2><div class="pair">')
        for variant, label in [('reference', 'PrimeVue 3.38.1 / Nova'), ('candidate', 'PrimeVue 4.5.5 / customized Lara')]:
            name = f'{variant}-{scene}-{width}.png'
            html.append(f'<figure><figcaption>{label}</figcaption><a href="{name}"><img loading="lazy" src="{name}" alt="{label}: {scene} at {width}px"></a></figure>')
        html.append('</div></section>')
(output / 'index.html').write_text('\n'.join(html) + '</html>\n')
print(json.dumps({name: {key: value for key, value in values.items() if key != 'files'} for name, values in variants.items()}, indent=2))
