// Same end-to-end check, but against the single-file build — the thing actually shipped.
//
//   node web/spike/dist-test.mjs

import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const webRoot = join(dirname(fileURLToPath(import.meta.url)), '..');
const PORT = 8732;
const server = spawn('npx', ['http-server', webRoot, '-p', String(PORT), '-s'], { stdio: 'ignore' });
await new Promise((r) => setTimeout(r, 2500));

const browser = await chromium.launch();
const errors = [];
try {
  const host = await browser.newPage();
  const guest = await (await browser.newContext()).newPage();
  host.on('pageerror', (e) => errors.push('hote: ' + e.message));
  guest.on('pageerror', (e) => errors.push('invite: ' + e.message));

  await host.goto(`http://localhost:${PORT}/dist/test-liaison.html`);
  await host.click('#beHost');
  await host.waitForFunction(() => document.getElementById('qrText').value.length > 20);
  const offer = await host.inputValue('#qrText');

  await guest.goto(offer.replace(/^http:\/\/[^/]+/, `http://localhost:${PORT}`));
  await guest.waitForFunction(() => document.getElementById('qrText').value.length > 20);

  await host.fill('#answerIn', await guest.inputValue('#qrText'));
  await host.click('#useAnswer');
  await host.waitForFunction(
    () => document.getElementById('sPing').textContent.includes('ms'),
    null,
    { timeout: 20000 },
  );
  console.log('  fichier unique : liaison etablie, aller-retour ' + (await host.textContent('#sPing')));
} finally {
  await browser.close();
  server.kill();
}

if (errors.length) {
  console.log('  erreurs : ' + errors.join(' | '));
  process.exit(1);
}
