import {defineConfig} from 'vite';
import vue from '@vitejs/plugin-vue';
import {fileURLToPath} from 'node:url';
export default defineConfig(({mode}) => ({
  plugins: [vue(), {
    name: 'synthetic-save',
    configureServer(server) { simulate(server); },
    configurePreviewServer(server) { simulate(server); }
  }],
  resolve: {alias: {...(mode === 'reference' ? {primevue: fileURLToPath(new URL('./node_modules/primevue-reference', import.meta.url))} : {}), 'spike-library': fileURLToPath(new URL(`./src/${mode === 'reference' ? 'reference' : 'candidate'}.js`, import.meta.url))}, dedupe: ['vue']},
  build: {outDir: `dist/${mode === 'reference' ? 'reference' : 'candidate'}`, target: ['chrome111', 'edge111', 'firefox114', 'safari16.4'], manifest: true},
  server: {fs: {allow: ['../..']}}
}));
function simulate(server) {
  server.middlewares.use('/simulation/measurements', (request, response) => {
    let body = '';
    request.on('data', chunk => { body += chunk; });
    request.on('end', () => setTimeout(() => {
      response.statusCode = JSON.parse(body).fail ? 503 : 201;
      response.setHeader('Content-Type', 'application/json');
      response.end('{}');
    }, 1500));
  });
}
