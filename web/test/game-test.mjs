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
await new Promise((r) => setTimeout(r, 6000));

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

// The banner marks its own text with .mine exactly when the view says it is your turn.
// Matching the wording instead used to work until a mod added a banner it did not know
// ("Coup double — 2 cartes à poser"), and the harness then sat waiting for a player who
// was in fact waiting for it.
const yourTurn = () => !!document.querySelector('#banner .mine');

try {
  // ------------------------------------------------------------------ solo game
  console.log('\n=== Partie solo, jusqu\'a la fin ===\n');
  const solo = await browser.newPage();
  guard(solo, 'solo');
  await solo.goto(URL);
  await solo.click('#go-solo');
  // Every mod on, so the solo pass covers the +8, the +12, the Coup double and the Espion.
  await solo.click('#solo-mods [data-mod="d8"]');
  await solo.click('#solo-mods [data-mod="x2"]');
  await solo.click('#solo-mods [data-mod="sp"]');
  await solo.click('#solo-mods [data-mod="d12"]');
  await solo.click('[data-difficulty="HARD"]');
  await solo.waitForSelector('#screen-game.on');

  let moves = 0;
  let over = false;
  // Generous: every bot turn costs its 750 ms think time, and a long round is a normal
  // round, not a hang.
  for (let i = 0; i < 900 && !over; i++) {
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
  // Through the create screen, with every mod on: the pairing, the lobby and a whole
  // modded game all get covered in one pass.
  await host.click('#go-host');
  await host.click('#create-custom');
  await host.click('[data-mod="d8"]');
  await host.click('[data-mod="x2"]');
  await host.click('[data-mod="sp"]');
  await host.click('[data-mod="d12"]');
  await host.click('#create-go');
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

  // The guest must be told what it is playing before a single card is dealt. The host
  // sees its own roster fill first, so wait for the broadcast to land over there.
  await guest.waitForFunction(
    () => document.querySelectorAll('#lobby-mods .chip').length > 0,
    null,
    { timeout: 15000 },
  );
  const guestMods = await guest.$$eval('#lobby-mods .chip', (n) => n.map((c) => c.textContent));
  console.log(`  mods annonces a l'invite : ${guestMods.join(', ') || 'aucun'}`);
  if (guestMods.length !== 4) problems.push(`le salon invite annonce ${guestMods.length} mod(s) au lieu de 4`);

  await host.click('#host-start');
  await host.waitForSelector('#screen-game.on');
  await guest.waitForSelector('#screen-game.on', { timeout: 15000 });
  console.log('  les deux ecrans sont sur la table');

  // ------------------------------------------------------------ chat and stickers
  await guest.click('#chat-bar');
  await guest.fill('#chat-input', 'salut mon reuf');
  await guest.click('#chat-send');
  await host.waitForTimeout(600);

  const flash = await host.$$eval('#chat-flash .flash .what', (n) => n.map((x) => x.textContent));
  console.log(`  bulle recue par l'hote : ${JSON.stringify(flash)}`);
  if (!flash.includes('salut mon reuf')) problems.push("le message n'arrive pas chez l'hote");
  if (await host.textContent('#chat-unread') !== '1') problems.push('le compteur de non-lus est faux');
  // The sender is not flashed at with its own line, but it is in its own log.
  if (await guest.$$eval('#chat-flash .flash', (n) => n.length) !== 0) {
    problems.push("l'expediteur voit sa propre bulle");
  }
  if (await guest.$$eval('#chat-log .said', (n) => n.length) !== 1) {
    problems.push("la ligne manque dans le log de l'expediteur");
  }
  await guest.click('#chat-close');

  await host.click('#sticker-rail [data-sticker="5"]');
  await guest.waitForTimeout(400);
  const emotes = await guest.$$eval('#emote-layer .emote', (n) => n.map((x) => x.className));
  console.log(`  emoji recu par l'invite : ${emotes.length ? emotes[0] : 'aucun'}`);
  if (!emotes.length) problems.push("l'emoji n'arrive pas chez l'invite");
  // The host is the rival along the top of the guest's screen, so it comes down.
  else if (!emotes[0].includes('from-top')) problems.push("l'emoji arrive du mauvais cote");

  // Both are transient: the sticker clears itself, the bubble folds away after 5 s.
  await guest.waitForTimeout(2800);
  if (await guest.$$eval('#emote-layer .emote', (n) => n.length) !== 0) {
    problems.push("l'emoji ne disparait pas");
  }
  // Five seconds from when it arrived, not from here: the sticker checks above already
  // burned part of that, and hard-coding the remainder is how a test starts flaking.
  const folded = await host.waitForFunction(
    () => document.querySelectorAll('#chat-flash .flash').length === 0,
    null,
    { timeout: 4000 },
  ).then(() => true).catch(() => false);
  if (!folded) problems.push('la bulle ne disparait pas apres 5 s');

  let exchanged = 0;
  for (let i = 0; i < 600; i++) {
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
