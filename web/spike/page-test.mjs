// Drives the real page the way two people would, and checks the link comes up.
//
// The QR is not photographed here — its contents are read straight out of the box that
// sits under it, which is the same string a camera would hand over. Everything else is
// the page's own code.
//
//   node web/spike/page-test.mjs

import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const webRoot = join(dirname(fileURLToPath(import.meta.url)), '..');
const PORT = 8731;

// localhost counts as a secure origin, which is what WebRTC and the clipboard need.
const server = spawn('npx', ['http-server', webRoot, '-p', String(PORT), '-s'], {
  stdio: 'ignore',
});
await new Promise((r) => setTimeout(r, 2500));

const browser = await chromium.launch();
const fail = [];

try {
  const hostPage = await browser.newPage();
  const guestPage = await (await browser.newContext()).newPage();
  for (const [name, page] of [['hote', hostPage], ['invite', guestPage]]) {
    page.on('pageerror', (e) => fail.push(`${name} : ${e.message}`));
  }

  await hostPage.goto(`http://localhost:${PORT}/diagnostic.html`);
  await hostPage.click('#beHost');
  await hostPage.waitForFunction(() => document.getElementById('qrText').value.length > 20);
  const offerUrl = await hostPage.inputValue('#qrText');
  console.log(`  QR de l'hote        : ${offerUrl.length} caracteres`);

  const svg = await hostPage.$eval('#qr', (e) => e.innerHTML.length);
  console.log(`  QR effectivement dessine : ${svg > 200 ? 'oui' : 'NON'}`);

  // The guest arrives by opening exactly that URL, camera-app style.
  await guestPage.goto(offerUrl.replace(/^http:\/\/[^/]+/, `http://localhost:${PORT}`));
  await guestPage.waitForFunction(() => document.getElementById('qrText').value.length > 20);
  const answer = await guestPage.inputValue('#qrText');
  console.log(`  QR de l'invite      : ${answer.length} caracteres`);

  await hostPage.fill('#answerIn', answer);
  await hostPage.click('#useAnswer');

  await hostPage.waitForFunction(
    () => document.getElementById('sChan').textContent === 'ouvert',
    null,
    { timeout: 20000 },
  );
  await hostPage.waitForFunction(
    () => document.getElementById('sPing').textContent.includes('ms'),
    null,
    { timeout: 10000 },
  );

  const state = await hostPage.evaluate(() => ({
    sig: document.getElementById('sSig').textContent,
    ice: document.getElementById('sIce').textContent,
    chan: document.getElementById('sChan').textContent,
    ping: document.getElementById('sPing').textContent,
    cand: document.getElementById('sCand').textContent,
  }));
  console.log(`  signalisation       : ${state.sig}`);
  console.log(`  ICE                 : ${state.ice}`);
  console.log(`  canal de donnees    : ${state.chan}`);
  console.log(`  aller-retour        : ${state.ping}`);
  console.log(`  adresses annoncees  : ${state.cand}`);

  if (state.chan !== 'ouvert') fail.push('le canal ne s\'ouvre pas');
  if (!state.ping.includes('ms')) fail.push('pas d\'aller-retour');
} finally {
  await browser.close();
  server.kill();
}

console.log(`\n  ${fail.length ? 'ECHEC : ' + fail.join(' | ') : 'La page fonctionne de bout en bout.'}`);
process.exit(fail.length ? 1 : 0);
