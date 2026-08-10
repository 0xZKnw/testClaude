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
  const guestContext = await browser.newContext();
  // Seeded through an init script rather than by visiting first: the invite is the same
  // page with a fragment on the end, so a plain second goto would be a fragment jump the
  // browser never reloads for, and the offer would never be read.
  //
  // One point short of level 5, so whatever the round does — a win is 25 XP, a loss 10 —
  // the guest is guaranteed to cross a level during it. That is what the host's copy of
  // its badge has to notice.
  await guestContext.addInitScript(() => {
    localStorage.setItem('uno.profile', JSON.stringify({
      name: 'Bob', avatarColor: 4, photo: null, stats: {}, xp: 109, wearing: {},
    }));
  });
  const guest = await guestContext.newPage();
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

  const badgeBefore = await host.textContent('#rivals .level-badge');
  console.log(`  niveau de l'invite vu par l'hote : ${badgeBefore}`);
  if (badgeBefore !== '4') problems.push(`l'hote voit l'invite au niveau ${badgeBefore} au lieu de 4`);

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

  // The rail is folded away by default, so it takes a tap to unfold before picking.
  if (await host.$$eval('#sticker-rail [data-sticker]', (n) => n.length) !== 0) {
    problems.push('le rail des stickers est deja ouvert');
  }
  await host.click('#sticker-rail [data-rail="1"]');
  await host.click('#sticker-rail [data-sticker="5"]');
  // Picking one folds the rail back down.
  if (await host.$$eval('#sticker-rail [data-sticker]', (n) => n.length) !== 0) {
    problems.push('le rail ne se referme pas apres un envoi');
  }
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

  // ------------------------------------------------------------- experience
  // Every finished round is worth something, win or lose, and the panel does not just
  // state it — the bar counts up to it.
  const prize = (await host.textContent('#over-xp .level-head span')).trim();
  console.log(`  xp annonce a l'hote : ${prize}`);
  if (!/^\+\d+ XP$/.test(prize)) problems.push("l'xp n'est pas annoncee en fin de manche");

  const readFill = (page) => page.$eval('#over-xp .xp-fill', (n) => parseFloat(n.style.width) || 0);
  const early = await readFill(host);
  await host.waitForTimeout(700);
  const mid = await readFill(host);
  await host.waitForTimeout(900);
  const settled = await readFill(host);
  console.log(`  barre d'xp : ${early}% -> ${mid}% -> ${settled}%`);
  if (mid <= early) problems.push("la barre d'xp ne se remplit pas");
  // Deliberately not "it only ever goes up": crossing a level fills the bar, drops it to
  // nothing and carries on, which is the whole point of the animation. What must hold is
  // that it lands exactly where the profile says it should.
  const wanted = await host.evaluate(async () => {
    const Lv = await import('./src/levels.js');
    return Lv.percent(JSON.parse(localStorage.getItem('uno.profile')).xp);
  });
  console.log(`  barre attendue a l'arrivee : ${wanted}%`);
  if (Math.abs(settled - wanted) > 1.5) {
    problems.push(`la barre d'xp s'arrete a ${settled}% au lieu de ${wanted}%`);
  }

  // The bug this replaces: a level crossed between two rounds used to stay invisible to
  // everybody else for the rest of the evening.
  const badgeAfter = await host.textContent('#rivals .level-badge');
  console.log(`  niveau de l'invite apres la manche : ${badgeAfter}`);
  if (Number(badgeAfter) < 5) {
    problems.push(`l'hote voit toujours l'invite au niveau ${badgeAfter} apres sa montee`);
  }

  // Whether a round crosses a level depends on where it started, not on who won — the
  // guest was seeded one point short of level 5 and gets there either way. So the check
  // is against the arithmetic, not against the result. Either way the overlay must not
  // be left blocking the table.
  for (const [page, who, startXp] of [[host, "l'hote", 0], [guest, "l'invite", 109]]) {
    const won = (await page.textContent('#over-title')) === 'Gagné !';
    const level = await page.evaluate(async () => {
      const Lv = await import('./src/levels.js');
      return Lv.levelAt(JSON.parse(localStorage.getItem('uno.profile')).xp);
    });
    const expected = level > (startXp === 0 ? 1 : 4);
    const up = await page.evaluate(() => document.getElementById('levelup').classList.contains('on'));
    console.log(`  ${who} : ${won ? 'gagne' : 'perd'}, niveau ${level}, montee ${up ? 'oui' : 'non'}`);
    if (expected !== up) problems.push(`la montee de niveau de ${who} ne suit pas l'experience`);
    if (up) {
      const title = (await page.textContent('#levelup-title')).trim();
      if (title !== `Niveau ${level}`) problems.push(`niveau annonce faux : ${title} au lieu de ${level}`);
      await page.click('#levelup-ok');
    }
  }

  // ---------------------------------------------------------------------- stats
  // The round that just ended has been folded in, so the profile has real numbers to
  // render — and every derived figure gets exercised on the way.
  await host.click('#quit-btn');
  await host.waitForSelector('#screen-home.on');
  await host.click('#profile-bar');
  await host.waitForSelector('#screen-profile.on');
  const groups = await host.$$eval('#stats-list .stat-group .label', (n) => n.map((x) => x.textContent));
  console.log(`  panneaux de stats : ${groups.length}`);
  for (const title of ['Séries et records', 'Rythme', 'Cartes', 'Guerre des cumuls', 'Dernière carte']) {
    if (!groups.includes(title)) problems.push(`le panneau « ${title} » manque dans les stats`);
  }
  const played = await host.$$eval('#stats-list .stat', (rows) => {
    const hit = rows.find((r) => r.querySelector('b').textContent === 'Cartes posées');
    return hit ? hit.querySelector('span').textContent : null;
  });
  console.log(`  cartes posees comptees : ${played}`);
  if (!played || Number(played) <= 0) problems.push("la manche jouee n'est pas comptee dans les stats");

  // ------------------------------------------------------------------ wardrobe
  // Seeded rather than played to: reaching level 51 honestly would take a thousand
  // rounds, and what is being checked here is that the catalogue gates on the level,
  // not that the arithmetic adds up — the conformance dump already covers that.
  // 7125 XP is exactly the start of level 51, which is where the gold frame lands.
  await host.evaluate(() => {
    const p = JSON.parse(localStorage.getItem('uno.profile'));
    p.xp = 7125;
    localStorage.setItem('uno.profile', JSON.stringify(p));
  });
  await host.reload();
  await host.click('#profile-bar');
  const level = (await host.textContent('#profile-level .level-head b')).trim();
  console.log(`  niveau affiche : ${level}`);
  if (level !== 'Niveau 51') problems.push(`niveau affiche faux : ${level}`);

  await host.click('#go-wardrobe');
  await host.waitForSelector('#screen-wardrobe.on');
  const tabs = await host.$$eval('#kind-tabs button', (n) => n.map((x) => x.textContent.trim()));
  console.log(`  familles de cosmetiques : ${tabs.length}`);
  if (tabs.length !== 6) problems.push(`il manque des familles de cosmetiques : ${tabs.join(', ')}`);

  const frames = await host.$$eval('#wardrobe .cosmetic', (n) => n.length);
  const wearable = await host.$$eval('#wardrobe [data-wear]', (n) => n.length);
  const locked = await host.$$eval('#wardrobe .cosmetic.locked', (n) => n.length);
  console.log(`  cadres : ${frames} au total, ${wearable} debloques, ${locked} verrouilles`);
  if (frames < 25) problems.push(`trop peu de cadres : ${frames}`);
  // Eighteen frames land at or before level 51; the rest must still be shut.
  if (wearable !== 18) problems.push(`cadres portables : ${wearable} au lieu de 18`);
  if (locked !== frames - wearable) problems.push('les cadres verrouilles ne sont pas marques comme tels');

  // The gold frame is exactly the level-51 reward: it must be reachable, and putting it
  // on must stick.
  await host.click('[data-wear="fr.or"]');
  const wornFrame = await host.evaluate(() => JSON.parse(localStorage.getItem('uno.profile')).wearing.FRAME);
  console.log(`  cadre porte : ${wornFrame}`);
  if (wornFrame !== 'fr.or') problems.push("le cadre choisi n'est pas enregistre");
  if (await host.$$eval('#wardrobe .cosmetic.worn', (n) => n.length) !== 1) {
    problems.push("le cadre porte n'est pas marque dans la grille");
  }

  // Something a level 51 has not earned must stay untouchable.
  if (await host.$$eval('[data-wear="fr.centieme"]', (n) => n.length) !== 0) {
    problems.push('un cadre du niveau 100 est portable au niveau 51');
  }

  await host.click('#kind-tabs button:nth-child(4)');
  const titleTiles = await host.$$eval('#wardrobe [data-wear]', (n) => n.map((x) => x.dataset.wear));
  console.log(`  titres portables au niveau 51 : ${titleTiles.length}`);
  if (!titleTiles.includes('ti.sanspitie')) problems.push("le titre du niveau 49 n'est pas portable");
  if (titleTiles.includes('ti.briscard')) problems.push('un titre du niveau 53 est deja portable');

  // The rail grows with the level: six at the start, ten by level 51.
  await host.click('#kind-tabs button:nth-child(6)');
  const stickers = await host.$$eval('#wardrobe [data-wear]', (n) => n.length);
  console.log(`  stickers debloques au niveau 51 : ${stickers}`);
  if (stickers !== 11) problems.push(`stickers debloques : ${stickers} au lieu de 11`);
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
