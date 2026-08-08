// Folds the page, the codec and the QR encoder into one standalone .html file.
//
// A single file can be dropped on any static host — GitHub Pages, Vercel, whatever —
// with no build step and no CDN. That matters here: the whole point of this experiment
// is that nothing but a file server is involved.
//
//   node web/build.mjs   ->   web/dist/index.html
//
// The output is named index.html so the page sits at the root of whatever hosts it.
// That matters twice over: the join link is the bare domain, and the QR that carries it
// is that much smaller.

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const root = dirname(fileURLToPath(import.meta.url));
const read = (...p) => readFileSync(join(root, ...p), 'utf8');

const codec = read('sdp-codec.js').replace(/^export /gm, '');
const qr = read('vendor', 'qrcode.js');
let html = read('index.html');

// Function replacements, not strings: `$&` and friends are magic inside a replacement
// string, and both files being injected are full of dollar signs.
html = html
  .replace('<script src="vendor/qrcode.js"></script>', () => `<script>\n${qr}\n</script>`)
  .replace(
    /<script type="module">\s*import \{ pack, unpack \} from '\.\/sdp-codec\.js';/,
    () => `<script>\n${codec}\n`,
  );

if (html.includes('import ') || html.includes('src="vendor')) {
  throw new Error('il reste une dependance externe dans la page');
}

mkdirSync(join(root, 'dist'), { recursive: true });
const out = join(root, 'dist', 'index.html');
writeFileSync(out, html);
console.log(`${out}  —  ${(html.length / 1024).toFixed(0)} Ko, aucune dependance externe`);
