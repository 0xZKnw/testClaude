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

  // --------------------------------------------------------------- le +50
  // One round in a hundred is not something a test can sit and wait for, so both sources
  // of chance are pinned and nothing else is: the roll is forced, and the engine is handed
  // a seed that happens to leave the +50 on top of the pile. Everything after that is the
  // real game — the card is drawn by clicking the deck and played by clicking it.
  console.log('\n=== Le +50, tire de force ===\n');
  const lucky = await browser.newPage();
  guard(lucky, 'jackpot');
  await lucky.goto(URL);
  await lucky.click('#go-solo');
  await lucky.evaluate(() => {
    const realBytes = crypto.getRandomValues.bind(crypto);
    let pinned = false;
    crypto.getRandomValues = (a) => {
      // Only the engine's seed — the first request for a pair of words — is pinned.
      // Anything else the page asks for stays as random as it was.
      if (!pinned && a instanceof Uint32Array && a.length === 2) {
        pinned = true;
        a[0] = 64;
        a[1] = 2380164160;
        return a;
      }
      return realBytes(a);
    };
    // The jackpot roll is the first thing to ask Math.random after this point; it is put
    // back the moment the table is dealt, so the bot plays normally.
    window.__realRandom = Math.random;
    Math.random = () => 0;
  });
  await lucky.click('[data-difficulty="EASY"]');
  await lucky.waitForSelector('#screen-game.on');
  await lucky.evaluate(() => { Math.random = window.__realRandom; });

  const rivalCount = () => Number(
    document.getElementById('rivals').textContent.match(/(\d+)\s*cartes?/)?.[1] ?? -1,
  );
  await lucky.waitForFunction(yourTurn, null, { timeout: 10000 });
  const heldBefore = await lucky.evaluate(rivalCount);
  await lucky.evaluate(() => document.getElementById('deck').click());

  let revealed = true;
  await lucky.waitForSelector('#jackpot.on', { timeout: 5000 }).catch(() => { revealed = false; });
  console.log(`  annonce a la pioche : ${revealed ? 'oui' : 'NON'}`);
  if (!revealed) problems.push("le +50 pioche n'est pas annonce");

  const inHand = await lucky.$$eval('#hand [data-card="9000"]', (n) => n.length);
  if (inHand !== 1) problems.push(`le +50 n'est pas dans la main (${inHand} exemplaire)`);

  await lucky.click('#jackpot');
  await lucky.waitForFunction(
    () => !document.getElementById('jackpot').classList.contains('on'),
  );
  await lucky.evaluate(() => document.querySelector('#hand [data-card="9000"]').click());
  await lucky.waitForSelector('#picker.on', { timeout: 5000 });
  await lucky.evaluate(() => document.querySelector('#picker [data-pick]').click());

  // The shake is deliberately short — 500 ms — so it is polled rather than awaited.
  let quaked = true;
  await lucky.waitForFunction(
    () => document.getElementById('screen-game').classList.contains('quake'),
    null,
    { timeout: 3000, polling: 20 },
  ).catch(() => { quaked = false; });
  console.log(`  l'ecran tremble quand il tombe : ${quaked ? 'oui' : 'NON'}`);
  if (!quaked) problems.push("poser le +50 ne fait pas trembler l'ecran");

  // Fifty cards change hands. Which hand depends on whether the bot holds the +2 of the
  // announced colour that sends them straight back — and if it does, the pile is on its
  // way to me instead, so the turns have to keep being played until it lands somewhere.
  const biggestHand = () => Math.max(
    Number(document.getElementById('rivals').textContent.match(/(\d+)\s*cartes?/)?.[1] ?? 0),
    document.querySelectorAll('#hand [data-card]').length,
  );
  let dealt = false;
  for (let i = 0; i < 40 && !dealt; i++) {
    dealt = (await lucky.evaluate(biggestHand)) >= 50;
    if (dealt) break;
    if (await lucky.evaluate(yourTurn)) await takeTurn(lucky);
    await lucky.waitForTimeout(150);
  }
  const heldAfter = await lucky.evaluate(rivalCount);
  const mine = await lucky.$$eval('#hand [data-card]', (n) => n.length);
  console.log(`  adversaire : ${heldBefore} cartes avant, ${heldAfter} apres ; ma main : ${mine}`);
  if (!dealt) problems.push('le +50 ne distribue pas cinquante cartes');
  await lucky.close();

  // ------------------------------------------------------- two browsers, WebRTC
  console.log('\n=== Partie a deux, appairage WebRTC ===\n');
  const host = await browser.newPage();
  const guestContext = await browser.newContext();
  // Seeded through an init script rather than by visiting first: the invite is the same
  // page with a fragment on the end, so a plain second goto would be a fragment jump the
  // browser never reloads for, and the offer would never be read.
  //
  // 2294 XP is one point short of level 28, so whatever the round does — a win is 25 XP,
  // a loss 10 — the guest is guaranteed to cross a level during it. That is what the
  // host's copy of its badge has to notice. Level 27 is also exactly where the green
  // cloth lands, and the host has never unlocked it: the two tables are then visibly
  // different, which is what the hand-over check needs.
  await guestContext.addInitScript(() => {
    localStorage.setItem('uno.profile', JSON.stringify({
      name: 'Bob', avatarColor: 4, photo: null, stats: {}, xp: 2294,
      wearing: { FELT: 'ft.feutre' },
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

  // Nothing to type at any more: the chat is gone, stickers are the whole channel.
  if (await host.$$eval('#chat-bar, #chat, #chat-input', (n) => n.length) !== 0) {
    problems.push('il reste du chat dans la page');
  }

  const badgeBefore = await host.textContent('#rivals .level-badge');
  console.log(`  niveau de l'invite vu par l'hote : ${badgeBefore}`);
  if (badgeBefore !== '27') problems.push(`l'hote voit l'invite au niveau ${badgeBefore} au lieu de 27`);

  // ------------------------------------------------------------ the cloth changes hands
  // The guest arrived wearing the green cloth, which the host has never unlocked. Both
  // screens must show the same one at any moment: the cloth of whoever the table is
  // waiting on.
  // There is always exactly one cloth switched on: the hand-over turns the new layer on
  // in the same tick it is added, so there is no frame with a bare table.
  const clothOf = (page) => page.$eval('#felt-layers .felt.on', (n) => n.style.background);
  const GREEN = '39, 80, 58';          // #27503a, the guest's cloth, as the browser reports it
  for (const [page, who] of [[host, "l'hote"], [guest, "l'invite"]]) {
    const mine = await page.evaluate(yourTurn);
    const green = (await clothOf(page)).includes(GREEN);
    // Only the guest wears green, so green is up exactly when the guest is on turn.
    const guestOnTurn = who === "l'invite" ? mine : !mine;
    console.log(`  tapis chez ${who} : ${green ? 'vert' : 'defaut'}, invite au trait : ${guestOnTurn}`);
    if (green !== guestOnTurn) problems.push(`le tapis de ${who} ne suit pas le joueur au trait`);
  }

  // And it must actually change hands when the turn does.
  //
  // One turn at a time, sampled after each: playing both players in the same pass hands
  // the table over and straight back, and the cloth would look as if it had never moved.
  // Six turns at most, so with seven cards each this cannot reach the end of the round
  // and leave its overlays blocking the checks below.
  const clothBefore = await clothOf(host);
  let clothAfter = clothBefore;
  for (let i = 0; i < 6 && clothAfter === clothBefore; i++) {
    for (const page of [host, guest]) {
      if (await page.evaluate(yourTurn)) { await takeTurn(page); break; }
    }
    await host.waitForTimeout(140);
    clothAfter = await clothOf(host);
  }
  console.log(`  le tapis a change de main : ${clothAfter !== clothBefore ? 'oui' : 'NON'}`);
  if (clothAfter === clothBefore) problems.push('le tapis ne change jamais de main');

  // A colour picker left open would block every click below it.
  for (const page of [host, guest]) {
    await page.evaluate(() => {
      const picker = document.getElementById('picker');
      if (picker.classList.contains('on')) picker.querySelector('[data-pick]').click();
    });
  }
  await host.waitForTimeout(200);

  // ---------------------------------------------------------------- stickers
  // The rail is folded away by default, so it takes a tap to unfold before picking.
  if (await host.$$eval('#sticker-rail [data-sticker]', (n) => n.length) !== 0) {
    problems.push('le rail des stickers est deja ouvert');
  }
  await host.click('#sticker-rail [data-rail="1"]');
  await host.click('#sticker-rail [data-sticker="5"]');
  // It stays open afterwards — throwing one sticker usually means throwing three — and
  // the cross is the only thing that folds it back down.
  if (await host.$$eval('#sticker-rail [data-sticker]', (n) => n.length) === 0) {
    problems.push('le rail se referme tout seul apres un envoi');
  }
  await host.click('#sticker-rail [data-rail="0"]');
  if (await host.$$eval('#sticker-rail [data-sticker]', (n) => n.length) !== 0) {
    problems.push('la croix ne referme pas le rail');
  }
  // Flush against the edge, not parked a few pixels short of it.
  const railGap = await host.evaluate(() => {
    const rail = document.getElementById('sticker-rail');
    const frame = rail.offsetParent ?? document.documentElement;
    return Math.round(frame.getBoundingClientRect().right - rail.getBoundingClientRect().right);
  });
  console.log(`  rail colle au bord droit : ${railGap} px`);
  if (railGap !== 0) problems.push(`le rail est a ${railGap} px du bord`);
  await guest.waitForTimeout(400);
  const emotes = await guest.$$eval('#emote-layer .emote', (n) => n.map((x) => x.className));
  console.log(`  emoji recu par l'invite : ${emotes.length ? emotes[0] : 'aucun'}`);
  if (!emotes.length) problems.push("l'emoji n'arrive pas chez l'invite");
  // The host is the rival along the top of the guest's screen, so it comes down.
  else if (!emotes[0].includes('from-top')) problems.push("l'emoji arrive du mauvais cote");

  // The sticker clears itself once its flight is over.
  await guest.waitForTimeout(2800);
  if (await guest.$$eval('#emote-layer .emote', (n) => n.length) !== 0) {
    problems.push("l'emoji ne disparait pas");
  }

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
  if (Number(badgeAfter) < 28) {
    problems.push(`l'hote voit toujours l'invite au niveau ${badgeAfter} apres sa montee`);
  }

  // Whether a round crosses a level depends on where it started, not on who won — the
  // guest was seeded one point short of level 5 and gets there either way. So the check
  // is against the arithmetic, not against the result. Either way the overlay must not
  // be left blocking the table.
  for (const [page, who, startLevel] of [[host, "l'hote", 1], [guest, "l'invite", 27]]) {
    const won = (await page.textContent('#over-title')) === 'Gagné !';
    const level = await page.evaluate(async () => {
      const Lv = await import('./src/levels.js');
      return Lv.levelAt(JSON.parse(localStorage.getItem('uno.profile')).xp);
    });
    const expected = level > startLevel;
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

  // Checked against the catalogue rather than against a number typed here: the catalogue
  // grows, and a test that has to be edited every time it does is a test that gets
  // edited into agreeing with whatever the code now says.
  const LEVEL = 51;
  for (const [tab, kind] of [[1, 'FRAME'], [2, 'BACK'], [3, 'FELT'], [4, 'TITLE'], [5, 'NAME'], [6, 'STICKER']]) {
    await host.click(`#kind-tabs button:nth-child(${tab})`);
    const seen = await host.$$eval('#wardrobe .cosmetic', (n) => n.length);
    const wearable = await host.$$eval('#wardrobe [data-wear]', (n) => n.map((x) => x.dataset.wear));
    const truth = await host.evaluate(async (family) => {
      const C = await import('./src/cosmetics.js');
      const all = C.ofKind(family);
      return { total: all.length, owned: all.filter((i) => i.level <= 51).map((i) => i.id) };
    }, kind);
    console.log(`  ${kind} : ${seen} affiches, ${wearable.length} portables sur ${truth.owned.length} merites`);
    if (seen !== truth.total) problems.push(`${kind} : ${seen} tuiles pour ${truth.total} cosmetiques`);
    if (wearable.join() !== truth.owned.join()) {
      problems.push(`${kind} : les portables ne suivent pas le niveau ${LEVEL}`);
    }
  }

  // The gold frame is a level-51 reward: it must be reachable, and putting it on sticks.
  await host.click('#kind-tabs button:nth-child(1)');
  await host.click('[data-wear="fr.or"]');
  const wornFrame = await host.evaluate(() => JSON.parse(localStorage.getItem('uno.profile')).wearing.FRAME);
  console.log(`  cadre porte : ${wornFrame}`);
  if (wornFrame !== 'fr.or') problems.push("le cadre choisi n'est pas enregistre");
  if (await host.$$eval('#wardrobe .cosmetic.worn', (n) => n.length) !== 1) {
    problems.push("le cadre porte n'est pas marque dans la grille");
  }
  // The crown is the last thing in the game and must stay shut at 51.
  if (await host.$$eval('[data-wear="fr.couronne"]', (n) => n.length) !== 0) {
    problems.push('le cadre du niveau 100 est portable au niveau 51');
  }
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
