#!/usr/bin/env node
/**
 * Prints routes from src/docs/api-routes.json
 * Usage: node scripts/list-api-routes.js [--json]
 */
const path = require('path');
const fs = require('fs');

const manifestPath = path.join(__dirname, '..', 'src', 'docs', 'api-routes.json');
const raw = fs.readFileSync(manifestPath, 'utf8');
const manifest = JSON.parse(raw);

const args = process.argv.slice(2);
const asJson = args.includes('--json');

if (asJson) {
  console.log(JSON.stringify(manifest, null, 2));
  process.exit(0);
}

console.log(manifest.title);
console.log('');

console.log('Non-API');
for (const r of manifest.nonApi || []) {
  console.log(`  ${r.method.padEnd(6)} ${r.path}  (${r.description || ''})`);
}
console.log('');

console.log('API routes');
for (const r of manifest.routes || []) {
  console.log(`  ${r.method.padEnd(6)} ${r.path}  [${r.auth}]  (${r.group})`);
}
console.log('');

const n = (manifest.routes || []).length;
console.log(`Total API routes: ${n}`);
console.log(`MyBus Android paths: ${(manifest.myBusAndroidRetrofit?.pathsRelativeToApi || []).length} (see manifest)`);
