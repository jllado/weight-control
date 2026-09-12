// Rebuild the last Vue CLI revision with its own frozen lockfile for real upgrade acceptance.
const {execFileSync} = require('node:child_process');
const {mkdirSync, rmSync} = require('node:fs');
const path = require('node:path');
const revision = '6bdaf31c91f3a7e45af9ab3b66d1249f6e5f6290';
const root = path.resolve('tmp/pwa-legacy');
mkdirSync(root, {recursive: true});
execFileSync('git', ['archive', revision, '-o', path.join(root, 'source.tar')]);
execFileSync('tar', ['-xf', path.join(root, 'source.tar'), '-C', root]);
rmSync(path.join(root, 'source.tar'));
const env = {...process.env, VUE_APP_GOOGLE_CLIENT_ID: 'test-client-id', VUE_APP_CHATGPT_COACH_URL: 'https://chatgpt.test/g/weight-control-coach'};
for (const args of [['install', '--frozen-lockfile', '--non-interactive'], ['build']]) execFileSync('yarn', args, {cwd: root, env, stdio: 'inherit'});
console.log(`Legacy PWA built from ${revision}`);
