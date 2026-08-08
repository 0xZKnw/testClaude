// Plays the actual page: a solo game to its end, then a real two-browser game paired
// over WebRTC. Any uncaught error in the page fails the run.
//
//   node web/test/game-test.mjs

import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const webRoot = join(dirname(fileURLToPath(import.meta.url)), '..');
const PORT = 8733;
const server = spawn('npx', ['http-server', webRoot, '-p', String(PORT), '-s'], { stdio: 'ignore' });
await new Promise((r) => setTimeout(r, 2500));

const URL = `http://localhost:${PORT}/index.html`;
const browser = await chromium.launch();
const problems = [];

function guard(page, who) {
  page.on('pageerror', (e) => problems.push(`${who} : ${e.message}`));
  page.on('console', (m) => {
    if (m.type() === 'error') problems.push(`${who} console : ${m.text()}`);
  });
}

/** Takes whatever turn is available: a playable card, else the deck, else pass. */
async function takeTurn(page) {
  const picked = await page.evaluate(() => {
    const overlay = document.getElementById('picker');
    if (overlay.classList.contains('on')) {
      overlay.querySelector('[data-pick]').click();
      return 'colour';
    }
    const playable = document.querySelector('#hand .card.playable');
    if (playable) { playable.click(); return 'card'; }
    if (!document.getElementById('pass-wrap').classList.contains('hidden')) {
      document.getElementById('pass-btn').click();
      return 'pass';
    }
    const banner = document.getElementById('banner').textContent;
    if (banner.includes('touche la pioche') || banner.includes('À toi de jouer')) {
      document.getElementById('deck').click();
      return 'draw';
    }
    return null;
  });
  return picked;
}

const yourTurn = () => document.getElementById('banner').textContent.includes('À toi') ||
  document.getElementById('banner').textContent.includes('touche la pioche') ||
  document.getElementById('banner').textContent.includes('pose-la ou passe') ||
  document.getElementById('banner').textContent.includes('contre');

try {
  // ------------------------------------------------------------------ solo game
  console.log('\n=== Partie solo, jusqu\'a la fin ===\n');
  const solo = await browser.newPage();
  guard(solo, 'solo');
  await solo.goto(URL);
  await solo.click('#go-solo');
  await solo.click('[data-difficulty="HARD"]');
  await solo.waitForSelector('#screen-game.on');

  let moves = 0;
  let over = false;
  for (let i = 0; i < 400 && !over; i++) {
    over = await solo.evaluate(() => document.getElementById('over').classList.contains('on'));
    if (over) break;
    const mine = await solo.evaluate(yourTurn);
    if (mine) {
      const did = await takeTurn(solo);
      if (did) moves++;
    }
    await solo.waitForTimeout(120);
  }
  const finished = await solo.evaluate(() => document.getElementById('over').classList.contains('on'));
  const title = await solo.textContent('#over-title');
  console.log(`  coups joues : ${moves}`);
  console.log(`  partie terminee : ${finished ? 'oui' : 'NON'}${finished ? ` (${title})` : ''}`);
  if (!finished) problems.push('la partie solo ne se termine pas');

  const handVisible = await solo.evaluate(() => {
    const hand = document.getElementById('hand');
    return hand.scrollHeight <= hand.clientHeight + 2;
  });
  console.log(`  main entierement visible, sans defilement : ${handVisible ? 'oui' : 'NON'}`);
  if (!handVisible) problems.push('la main deborde');

  // ------------------------------------------------------- two browsers, WebRTC
  console.log('\n=== Partie a deux, appairage WebRTC ===\n');
  const host = await browser.newPage();
  const guest = await (await browser.newContext()).newPage();
  guard(host, 'hote');
  guard(guest, 'invite');

  await host.goto(URL);
  await host.evaluate(() => localStorage.setItem('uno.profile',
    JSON.stringify({ name: 'Zak', avatarColor: 0, photo: null, stats: {} })));
  await host.reload();
  await host.click('#go-host');
  await host.waitForFunction(() => document.querySelector('#qrbox svg'), null, { timeout: 15000 });
  const invite = await host.evaluate(() =>
    document.querySelector('#qrbox svg') && window.__lastInvite);

  // The QR carries the page URL plus the offer; read it from the same place the QR did.
  const offerUrl = await host.evaluate(() => document.getElementById('qrbox').dataset.text);
  const link = offerUrl ?? invite;
  if (!link) throw new Error("impossible de recuperer l'invitation");

  await guest.goto(link.replace(/^http:\/\/[^/]+/, `http://localhost:${PORT}`));
  await guest.waitForSelector('#screen-lobby.on', { timeout: 15000 });
  const answer = await guest.inputValue('#lobby-answer');
  console.log(`  invitation : ${link.length} caracteres, reponse : ${answer.length}`);

  await host.fill('#host-answer', answer);
  await host.click('#host-accept');
  await host.waitForFunction(
    () => document.querySelectorAll('#host-roster .row-line').length >= 2,
    null,
    { timeout: 20000 },
  );
  const roster = await host.$$eval('#host-roster .row-line', (n) => n.length);
  console.log(`  joueurs dans le salon : ${roster}`);

  await host.click('#host-start');
  await host.waitForSelector('#screen-game.on');
  await guest.waitForSelector('#screen-game.on', { timeout: 15000 });
  console.log('  les deux ecrans sont sur la table');

  let exchanged = 0;
  for (let i = 0; i < 260; i++) {
    const done = await host.evaluate(() => document.getElementById('over').classList.contains('on'));
    if (done) break;
    for (const page of [host, guest]) {
      if (await page.evaluate(yourTurn)) {
        if (await takeTurn(page)) exchanged++;
      }
    }
    await host.waitForTimeout(90);
  }
  const hostOver = await host.evaluate(() => document.getElementById('over').classList.contains('on'));
  const guestOver = await guest.evaluate(() => document.getElementById('over').classList.contains('on'));
  console.log(`  coups echanges : ${exchanged}`);
  console.log(`  fin de manche vue des deux cotes : ${hostOver && guestOver ? 'oui' : 'NON'}`);
  if (!hostOver || !guestOver) problems.push('la manche ne se termine pas des deux cotes');

  // Both sides must agree on who won — that is the whole point of one authoritative engine.
  const hostWinner = await host.textContent('#over-title');
  const guestWinner = await guest.textContent('#over-title');
  const agree = (hostWinner === 'Gagné !') !== (guestWinner === 'Gagné !');
  console.log(`  les deux ecrans sont d'accord sur le vainqueur : ${agree ? 'oui' : 'NON'}`);
  if (!agree) problems.push(`desaccord sur le vainqueur : ${hostWinner} / ${guestWinner}`);
} finally {
  await browser.close();
  server.kill();
}

console.log('');
if (problems.length) {
  problems.forEach((p) => console.log('  PROBLEME : ' + p));
  process.exit(1);
}
console.log('  Le jeu tourne de bout en bout, en solo et a deux navigateurs.\n');
