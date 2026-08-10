// The game itself.
//
// Mirrors the Android app's AppViewModel: the host owns the one engine and broadcasts a
// per-seat snapshot after every change, guests send intentions and render what comes
// back. That is what makes a reconnecting or slow phone impossible to desync — and it is
// also why a guest needs no rules at all.

import {
  UnoEngine, Phase, Color, Kind, PLAYABLE_COLORS, HOST_SEAT, MIN_PLAYERS, MAX_PLAYERS,
  view as V, isWild, Mod, MOD_ORDER, MOD_INFO, orderedMods,
} from './engine.js';
import { decide, DIFFICULTY_ORDER, DIFFICULTY_INFO } from './bot.js';
import { cardFace, cardBack, colorChip, PALETTE } from './cards.js';
import { WebRtcHost, WebRtcGuest } from './net.js';
import { RULES } from './rules.js';
import {
  loadProfile, saveProfile, recordRound, resetStats, AVATAR_COLORS, shrinkPhoto,
  winRate, perRound, drawRate, aggression, averageLoss, closingRate,
  levelOf, wornOf, stickersOf, wear, addXp,
} from './profile.js';
import * as Cosm from './cosmetics.js';
import * as Lv from './levels.js';
import { STICKERS, cleanLine, MAX_CHARS } from './talk.js';

const $ = (id) => document.getElementById(id);
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => (
  { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

const BOT_THINK_MS = 750;
const BOT_AVATAR = 5;
const BOT_AVATAR_ALT = 2;

const state = {
  profile: loadProfile(),
  role: null,           // 'host' | 'guest' | 'solo'
  engine: null,
  solo: null,           // Difficulty when playing the machine
  players: [],          // [{s, n, a}] seats in join order
  photos: {},           // seat -> data URL
  mySeat: HOST_SEAT,
  myView: null,
  mods: [],             // the optional rules this room plays with
  rematch: new Set(),
  seatOfKey: new Map(),
  keyOfSeat: new Map(),
  net: null,
  botTimer: null,
  pendingWild: null,
  lastRecordedRound: -1,
  lastXp: 0,
  xpBefore: 0,
  levelUp: null,
  toastTimer: null,
  // Chat and stickers. None of it touches the rules, so none of it is in a snapshot.
  social: { enabled: false, chat: [], flash: [], open: false, unread: 0 },
  socialId: 1,
};

// --------------------------------------------------------------------- screens

const SCREENS = ['home', 'profile', 'wardrobe', 'create', 'solo', 'rules', 'host', 'join', 'lobby', 'game'];
let previous = 'home';

function show(name) {
  const current = SCREENS.find((s) => $(`screen-${s}`).classList.contains('on'));
  if (current && current !== name) previous = current;
  SCREENS.forEach((s) => $(`screen-${s}`).classList.toggle('on', s === name));
}

function dialog(title, body) {
  $('dialog-title').textContent = title;
  $('dialog-body').textContent = body;
  $('dialog').classList.add('on');
}
$('dialog-ok').onclick = () => $('dialog').classList.remove('on');

document.querySelectorAll('[data-back]').forEach((b) => {
  b.onclick = () => {
    if (state.role) teardown();
    show('home');
    renderHome();
  };
});

// --------------------------------------------------------------------- profile

function avatarStyle(photo, colorIndex) {
  const color = AVATAR_COLORS[(colorIndex ?? 0) % AVATAR_COLORS.length];
  return photo
    ? `background-image:url(${photo});background-color:${color}`
    : `background-color:${color}`;
}

function avatarHtml(name, colorIndex, photo, size, extraClass = '') {
  const initial = (name || '?').trim().charAt(0).toUpperCase() || '?';
  const style = `width:${size}px;height:${size}px;font-size:${Math.round(size * 0.44)}px;` +
    avatarStyle(photo, colorIndex);
  return `<div class="avatar ${extraClass}" style="${style}">${photo ? '' : esc(initial)}</div>`;
}

const clamp = (value, low, high) => Math.min(Math.max(value, low), high);

/**
 * An avatar inside its unlocked ring. The ring is drawn outside the face, so putting a
 * frame on never shrinks the picture.
 */
function framedHtml(frame, size, inner) {
  const band = clamp(size * 0.115, 3.5, 10);
  const ink = clamp(size * 0.045, 1.5, 3.5);
  const outer = size + (band + ink) * 2;
  const vars = `--band:${band}px;--ink-w:${ink}px;--fa:${frame.a};--fb:${frame.b || frame.a};`
    + `--fc:${frame.c || frame.b || frame.a}`;
  return `<div class="frame f-${frame.style}" style="width:${outer}px;height:${outer}px;${vars}">`
    + `${inner}</div>`;
}

function framedAvatarHtml(name, colorIndex, photo, size, frame, extraClass = '') {
  return framedHtml(frame, size, avatarHtml(name, colorIndex, photo, size, extraClass));
}

/**
 * A pseudo painted in its unlocked colour, with an ink outline: flat for one colour, a
 * gradient across the word for two or three.
 */
function pseudoHtml(name, item, size) {
  const stops = [item.a, item.b, item.c].filter(Boolean);
  const style = stops.length >= 2
    // A two-stop gradient repeats its last colour, so the CSS can always name three.
    ? `--na:${stops[0]};--nb:${stops[1]};--nc:${stops[2] || stops[1]};font-size:${size}px`
    : `color:${stops[0] || '#f3f6fb'};font-size:${size}px`;
  return `<span class="pseudo${stops.length >= 2 ? ' grad' : ''}" style="${style}">${esc(name)}</span>`;
}

function levelBadgeHtml(level, size) {
  const maxed = level >= Lv.MAX_LEVEL;
  // Three digits have to fit the same disc two do, so the type shrinks rather than the
  // number spilling over the keyline.
  const scale = String(level).length >= 3 ? 0.3 : 0.42;
  return `<div class="level-badge${maxed ? ' maxed' : ''}" style="width:${size}px;height:${size}px;`
    + `font-size:${Math.round(size * scale)}px;border-width:${clamp(size * 0.09, 2, 4)}px">${level}</div>`;
}

/** Level, bar, and what is still owed — the same three lines the phone shows. */
function levelBarHtml(xp, badge = 46, id = '') {
  const level = Lv.levelAt(xp);
  const maxed = level >= Lv.MAX_LEVEL;
  return `<div class="level-row"${id ? ` id="${id}"` : ''}>
    ${levelBadgeHtml(level, badge)}
    <div class="grow">
      <div class="level-head">
        <b>${maxed ? 'Niveau maximum' : `Niveau ${level}`}</b>
        <span>${maxed ? `${xp} XP` : `${Lv.into(xp)} / ${Lv.span(xp)} XP`}</span>
      </div>
      <div class="xp-track"><div class="xp-fill" style="width:${Lv.percent(xp)}%"></div></div>
      ${maxed ? '' : `<div class="level-foot">Encore ${Lv.toNext(xp)} XP avant le niveau ${level + 1}</div>`}
    </div>
  </div>`;
}

let xpAnimation = null;

/**
 * The bar counting up after a round.
 *
 * It starts where the round found you and runs to where it left you, so what is being
 * rewarded is the movement rather than a number. Crossing a level needs no special case:
 * the fraction is computed from the experience being animated, so the bar simply reaches
 * the end, drops to nothing and carries on — which is what levelling up looks like.
 */
function runXpBar(target, before, gained) {
  cancelAnimationFrame(xpAnimation);
  const node = $(target);
  if (!node) return;
  node.innerHTML = levelBarHtml(before, 40);
  const badge = node.querySelector('.level-badge');
  const head = node.querySelector('.level-head b');
  const prize = node.querySelector('.level-head span');
  const fill = node.querySelector('.xp-fill');
  const foot = node.querySelector('.level-foot');
  // The prize, in the same gold as the bar it is filling.
  prize.textContent = `+${gained} XP`;
  prize.style.color = 'var(--gold)';
  prize.style.fontWeight = '900';
  // No CSS transition: the width is driven frame by frame, and a transition would fight
  // it — especially across a level, where the bar has to snap back to nothing.
  fill.style.transition = 'none';

  const DELAY = 380;
  const RUN = 1150;
  const start = performance.now();
  const step = (now) => {
    const t = Math.min(Math.max(now - start - DELAY, 0) / RUN, 1);
    // Same ease as the phone: quick off the mark, gentle into the finish.
    const eased = t < 0.5 ? 2 * t * t : 1 - ((-2 * t + 2) ** 2) / 2;
    const xp = before + gained * eased;
    const level = Lv.levelAt(Math.trunc(xp));
    const floor = Lv.totalTo(level);
    const width = Lv.costOf(level);
    // Straight off the float rather than off Lv.percent: a level worth twenty points
    // would otherwise crawl up in twenty visible steps.
    const part = width <= 0 ? 1 : Math.min(Math.max((xp - floor) / width, 0), 1);
    fill.style.width = `${part * 100}%`;
    badge.textContent = String(level);
    badge.classList.toggle('maxed', level >= Lv.MAX_LEVEL);
    head.textContent = level >= Lv.MAX_LEVEL ? 'Niveau maximum' : `Niveau ${level}`;
    if (foot) foot.textContent = `${Math.trunc(xp) - floor} / ${width} XP`;
    if (t < 1) xpAnimation = requestAnimationFrame(step);
  };
  xpAnimation = requestAnimationFrame(step);
}

function renderHome() {
  const p = state.profile;
  $('home-avatar').style.cssText =
    `width:52px;height:52px;font-size:23px;` + avatarStyle(p.photo, p.avatarColor);
  $('home-avatar').textContent = p.photo ? '' : (p.name.trim().charAt(0).toUpperCase() || '?');
  $('home-name').textContent = p.name || 'Choisis ton pseudo';
  $('home-record').textContent = p.stats.roundsPlayed === 0
    ? 'Aucune manche jouée'
    : `${p.stats.roundsWon} victoires · ${winRate(p.stats)} %`;

  const fan = [
    { i: 0, c: Color.BLUE, k: Kind.NUMBER, n: 7 },
    { i: 1, c: Color.WILD, k: Kind.DRAW_FOUR, n: -1 },
    { i: 2, c: Color.GREEN, k: Kind.DRAW_TWO, n: -1 },
  ];
  $('logo-fan').innerHTML = fan.map((c, i) => {
    const tilt = [-18, 0, 18][i];
    const lift = i === 1 ? -12 : 0;
    return `<div class="card" style="width:82px;margin-left:${i ? -28 : 0}px;
      transform:rotate(${tilt}deg) translateY(${lift}px)">${cardFace(c)}</div>`;
  }).join('');
}

function renderProfile() {
  const p = state.profile;
  $('name-input').value = p.name;
  const refresh = () => {
    const shown = ($('name-input').value || '?').trim();
    const title = Cosm.worn(wornOf(p, 'TITLE'));
    // Framed and badged, exactly as the other players see you.
    $('profile-avatar').outerHTML = `<div id="profile-avatar" style="display:flex;
      flex-direction:column;align-items:center;gap:8px">
      <div style="position:relative;display:flex">
        ${framedAvatarHtml(shown, p.avatarColor, p.photo, 96, wornOf(p, 'FRAME'))}
        <div style="position:absolute;right:0;bottom:0">${levelBadgeHtml(levelOf(p), 34)}</div>
      </div>
      ${title ? `<span class="chip">${esc(title)}</span>` : ''}
    </div>`;
  };
  refresh();
  $('name-input').oninput = refresh;

  $('color-picker').innerHTML = AVATAR_COLORS.map((c, i) => `
    <div data-color="${i}" style="width:40px;height:40px;border-radius:50%;background:${c};
      border:3px solid ${i === p.avatarColor ? PALETTE.gold : PALETTE.outline};cursor:pointer"></div>`).join('');
  $('color-picker').querySelectorAll('[data-color]').forEach((node) => {
    node.onclick = () => { p.avatarColor = Number(node.dataset.color); renderProfile(); };
  });

  const level = levelOf(p);
  $('profile-level').innerHTML = levelBarHtml(p.xp || 0);
  $('profile-xp-note').textContent =
    `Manche gagnée : +${Lv.XP_WIN} XP   ·   perdue : +${Lv.XP_LOSS} XP`;
  $('profile-next').innerHTML = level >= Lv.MAX_LEVEL ? '' : `
    <div class="label" style="margin:14px 0 4px">À venir</div>
    ${Cosm.upcoming(level, 4).map((item) => `
      <div class="row-line" style="padding:4px 0">
        <span class="chip">Niv. ${item.level}</span>
        <span class="grow ellipsis" style="font-size:13px;font-weight:700">${esc(item.name)}</span>
        <span style="color:var(--dim);font-size:11px;font-weight:700">${Cosm.KINDS[item.kind].label}</span>
      </div>`).join('')}`;

  renderStats(p.stats);
}

// -------------------------------------------------------------------- wardrobe

let wardrobeTab = 'FRAME';

/**
 * Every item on show, earned or not — a locked tile that names the level it costs is
 * the whole reason to keep playing, and hiding it would leave the screen looking empty
 * for the first fifty levels.
 */
function renderWardrobe() {
  const p = state.profile;
  const level = levelOf(p);

  $('wardrobe-level').innerHTML = levelBarHtml(p.xp || 0);

  $('kind-tabs').innerHTML = Cosm.KIND_ORDER.map((kind) => `
    <button type="button" data-kind="${kind}" class="${kind === wardrobeTab ? 'on' : ''}">
      ${Cosm.KINDS[kind].label}</button>`).join('');
  $('kind-tabs').querySelectorAll('[data-kind]').forEach((node) => {
    node.onclick = () => { wardrobeTab = node.dataset.kind; renderWardrobe(); };
  });

  const items = Cosm.ofKind(wardrobeTab);
  $('kind-blurb').textContent = Cosm.KINDS[wardrobeTab].blurb;
  $('kind-count').textContent = `${items.filter((i) => i.level <= level).length} / ${items.length}`;

  $('wardrobe').innerHTML = items.map((item) => {
    const locked = item.level > level;
    const worn = wornOf(p, item.kind).id === item.id;
    const tag = worn
      ? '<span class="chip gold">Porté</span>'
      : (locked
        ? `<span class="chip" style="background:#0b0e14">Niveau ${item.level}</span>`
        : '<span class="chip">Débloqué</span>');
    // A title's preview *is* its name, so printing it again underneath would just be
    // the same words twice.
    const named = item.kind === 'TITLE' ? '' : `<div class="who">${esc(item.name)}</div>`;
    return `<div class="cosmetic${worn ? ' worn' : ''}${locked ? ' locked' : ''}"
      ${locked ? '' : `data-wear="${item.id}"`}>
      <div class="shot">${previewHtml(item, p)}</div>
      ${named}
      <div class="tag">${tag}</div>
    </div>`;
  }).join('');

  $('wardrobe').querySelectorAll('[data-wear]').forEach((node) => {
    node.onclick = () => {
      state.profile = wear(node.dataset.wear);
      renderWardrobe();
      // A frame or a title is worn for the others, not for you: tell the room.
      announceMyself();
    };
  });
}

function previewHtml(item, p) {
  switch (item.kind) {
    case 'FRAME':
      return framedAvatarHtml(p.name, p.avatarColor, p.photo, 46, item);
    case 'BACK':
      return `<div class="card">${cardBack(item)}</div>`;
    case 'FELT':
      return `<div class="felt-shot" style="background:radial-gradient(circle at 50% 38%,
        ${item.a}, ${item.b} 45%, ${item.c})"></div>`;
    case 'TITLE':
      return `<div class="title-shot">${esc(Cosm.worn(item) || '—')}</div>`;
    case 'NAME':
      return pseudoHtml(p.name || 'Joueur', item, 17);
    default:
      return `<div class="sticker-shot">${item.text}</div>`;
  }
}

$('go-wardrobe').onclick = () => { show('wardrobe'); renderWardrobe(); };
$('wardrobe-back').onclick = () => { show('profile'); renderProfile(); };

/**
 * The same six panels the Android profile shows, in the same order and with the same
 * wording — a player comparing the two screens should not be able to tell them apart.
 */
function renderStats(s) {
  const line = ([label, value, tint]) =>
    `<div class="stat"><b>${esc(label)}</b><span${tint ? ` style="color:${tint}"` : ''}>${esc(String(value))}</span></div>`;
  const section = (title, rows) =>
    `<div class="stat-group"><div class="label">${title}</div>${rows.map(line).join('')}</div>`;

  const big = (value, label, tint) =>
    `<div class="bigstat"><b style="color:${tint}">${esc(value)}</b><span>${label}</span></div>`;

  $('stats-list').innerHTML = `
    <div class="bigstats">
      ${big(String(s.roundsWon), 'gagnées', PALETTE.G)}
      ${big(String(s.roundsLost), 'perdues', PALETTE.R)}
      ${big(`${winRate(s)} %`, 'victoires', PALETTE.gold)}
    </div>
    <div class="stat-chips">
      <span class="chip">${s.roundsPlayed} manches</span>
      <span class="chip">Série : ${s.currentStreak}</span>
      <span class="chip">Record : ${s.bestStreak}</span>
    </div>
    ${section('Séries et records', [
    ['Série de victoires en cours', s.currentStreak, PALETTE.G],
    ['Meilleure série', s.bestStreak, PALETTE.G],
    ['Pire série de défaites', s.worstStreak, PALETTE.R],
    // Zero means "never won one", not "won without playing a card".
    ['Victoire la plus expéditive', s.fastestWin === 0 ? '—' : `${s.fastestWin} cartes`, PALETTE.gold],
    ["Pire main à l'arrivée", s.worstHand, PALETTE.R],
  ])}
    ${section('Rythme', [
    ['Cartes posées par manche', perRound(s, s.cardsPlayed)],
    ['Cartes piochées par manche', perRound(s, s.cardsDrawn)],
    ['Part de pioche', `${drawRate(s)} %`],
    ["Part d'attaque", `${aggression(s)} %`],
    ['Cartes restantes quand tu perds', averageLoss(s)],
  ])}
    ${section('Cartes', [
    ['Cartes posées', s.cardsPlayed],
    ['Cartes piochées', s.cardsDrawn],
    ['Chiffres posés', s.numbersPlayed],
    ['Passe et sens interdit', s.skipsPlayed],
    ['Jokers posés', s.wildsPlayed],
    ['Coups doubles', s.doublePlaysPlayed],
    ['Espions', s.spiesPlayed],
  ])}
    ${section('Guerre des cumuls', [
    ['+2 posés', s.drawTwosPlayed],
    ['+4 posés', s.drawFoursPlayed],
    ['+8 posés', s.drawEightsPlayed],
    ['+12 posés', s.drawTwelvesPlayed],
    ['Contres réussis', s.countersPlayed],
    ['Cartes encaissées', s.penaltyCardsTaken],
    ['Encaissées par manche', perRound(s, s.penaltyCardsTaken)],
    ['Plus gros cumul infligé', s.biggestStackDealt, PALETTE.G],
    ['Plus gros cumul encaissé', s.biggestStackTaken, PALETTE.R],
  ])}
    ${section('Dernière carte', [
    ["Fois où tu as touché l'UNO", s.unoReached, PALETTE.gold],
    ['Transformées en victoire', `${closingRate(s)} %`, PALETTE.gold],
  ])}`;
}

$('profile-bar').onclick = () => { show('profile'); renderProfile(); };
$('save-profile').onclick = () => {
  state.profile.name = $('name-input').value.trim().slice(0, 14);
  saveProfile(state.profile);
  show('home');
  renderHome();
};
$('reset-stats').onclick = () => { state.profile = resetStats(); renderProfile(); };
$('pick-photo').onclick = () => $('photo-input').click();
$('drop-photo').onclick = () => { state.profile.photo = null; saveProfile(state.profile); renderProfile(); };
$('photo-input').onchange = async (e) => {
  const file = e.target.files?.[0];
  if (!file) return;
  try {
    state.profile.photo = await shrinkPhoto(file);
    saveProfile(state.profile);
    renderProfile();
  } catch {
    dialog('Photo illisible', "Ce fichier n'a pas pu être lu comme une image.");
  }
};

// ----------------------------------------------------------------------- rules

$('rules-list').innerHTML = RULES.map((section) => `
  <div class="panel">
    <div class="label">${esc(section.title)}</div>
    ${section.lines.map((l) => `<p style="margin:10px 0 0;font-size:14px">${esc(l)}</p>`).join('')}
  </div>`).join('');

$('go-rules').onclick = () => show('rules');
$('go-diag').onclick = () => { window.location.href = 'diagnostic.html'; };

// ------------------------------------------------------------------------ solo

$('difficulty-list').innerHTML = DIFFICULTY_ORDER.map((d) => {
  const info = DIFFICULTY_INFO[d];
  const color = { EASY: 3, MEDIUM: 1, HARD: 0 }[d];
  return `<div class="panel" data-difficulty="${d}" style="cursor:pointer">
    <div class="row-line">
      ${avatarHtml(info.label, color, null, 46)}
      <div class="grow">
        <div style="font-weight:900;font-size:18px">${esc(info.label)}</div>
        <div style="color:var(--dim);font-size:13px">${esc(info.blurb)}</div>
      </div>
      <span class="chip gold">Jouer</span>
    </div>
  </div>`;
}).join('');

$('difficulty-list').querySelectorAll('[data-difficulty]').forEach((node) => {
  node.onclick = () => startSolo(node.dataset.difficulty, soloMods.slice());
});

/** Picked before the difficulty, because the difficulty card is what starts the game. */
let soloMods = [];
mountModPicker('solo-mods', () => soloMods, (next) => { soloMods = next; });

$('go-solo').onclick = () => show('solo');

// -------------------------------------------------------------- starting a game

function teardown() {
  clearTimeout(state.botTimer);
  state.net?.stop();
  Object.assign(state, {
    role: null, engine: null, solo: null, players: [], photos: {}, mySeat: HOST_SEAT,
    myView: null, mods: [], rematch: new Set(), seatOfKey: new Map(), keyOfSeat: new Map(),
    net: null, botTimer: null, pendingWild: null, lastRecordedRound: -1,
    nextStarter: 1,
    social: { enabled: false, chat: [], flash: [], open: false, unread: 0 },
  });
  $('chat').classList.remove('on');
  $('emote-layer').innerHTML = '';
  $('over').classList.remove('on');
  $('picker').classList.remove('on');
  $('slam').classList.remove('on');
  stopCamera();
}

/**
 * Me, as the other browsers should see me. The frame, the title and the pseudo colour
 * travel because they are worn *for* other people; the card back and the cloth stay
 * home, because those are my view of my own table.
 */
function meAsPlayer(seat) {
  const p = state.profile;
  return {
    s: seat,
    n: myName(),
    a: p.avatarColor,
    fr: wornOf(p, 'FRAME').id,
    ti: wornOf(p, 'TITLE').id,
    nm: wornOf(p, 'NAME').id,
    lv: levelOf(p),
  };
}

/** Resolves what one seat announced, falling back rather than leaving a hole. */
const frameOf = (player) => Cosm.resolve(player?.fr || '', 'FRAME', player?.lv || 1);
const titleOf = (player) => Cosm.worn(Cosm.resolve(player?.ti || '', 'TITLE', player?.lv || 1));
const nameColorOf = (player) => Cosm.resolve(player?.nm || '', 'NAME', player?.lv || 1);
const playerAt = (seat) => state.players.find((p) => p.s === seat);

/**
 * Tells the room what I look like now — after changing an outfit, and after a round that
 * pushed me up a level. Its own message rather than a second hello: a hello mid-game is
 * a rejoin, with a seat handed out and a welcome sent back. This says only "here is what
 * I look like now", which is the whole of what the other screens need.
 */
function announceMyself() {
  if (!state.role || state.role === 'solo' || !state.players.length) return;
  if (state.role === 'host') {
    state.players = [...state.players.filter((p) => p.s !== HOST_SEAT), meAsPlayer(HOST_SEAT)]
      .sort((a, b) => a.s - b.s);
    broadcastLobby();
    if (state.myView) renderGame(); else renderHostLobby();
  } else {
    state.net?.send({ ...meAsPlayer(state.mySeat), t: 'wearing' });
  }
}

function myName() {
  return state.profile.name.trim() || 'Joueur';
}

function newSeed() {
  const a = new Uint32Array(2);
  crypto.getRandomValues(a);
  return [a[0] | 0, a[1] | 0];
}

function startSolo(difficulty, mods = []) {
  teardown();
  state.role = 'solo';
  state.solo = difficulty;
  state.mods = orderedMods(mods);
  state.mySeat = HOST_SEAT;
  const botColor = state.profile.avatarColor === BOT_AVATAR ? BOT_AVATAR_ALT : BOT_AVATAR;
  state.players = [
    meAsPlayer(HOST_SEAT),
    { s: 1, n: DIFFICULTY_INFO[difficulty].botName, a: botColor },
  ];
  state.photos = state.profile.photo ? { [HOST_SEAT]: state.profile.photo } : {};
  state.botRng = { nextIntBelow: (n) => Math.floor(Math.random() * n) };
  startRound(true);
  show('game');
}

function startRound(firstRound) {
  const players = [...state.players].sort((a, b) => a.s - b.s);
  if (players.length < MIN_PLAYERS) return;
  let e = state.engine;
  const mods = orderedMods(state.mods);
  if (!e || e.playerCount !== players.length || e.mods.join() !== mods.join()) {
    const [low, high] = newSeed();
    e = new UnoEngine(low, high, players.length, mods);
    state.engine = e;
  }
  players.forEach((p) => { e.setName(p.s, p.n); e.setAvatar(p.s, p.a); });
  const starter = firstRound ? HOST_SEAT : (state.nextStarter ?? 1);
  state.nextStarter = (starter + 1) % players.length;
  state.rematch = new Set();
  e.startRound(starter);
  broadcast();
}

function broadcast() {
  const e = state.engine;
  if (!e) return;
  // Anything the player has no say in — eating a stack they cannot counter — resolves
  // here, so whoever is on turn always has a real choice.
  e.autoAdvance();
  state.myView = e.viewFor(state.mySeat, state.rematch);
  recordIfFinished(state.myView);
  for (const [seat, key] of state.keyOfSeat) {
    state.net?.send(key, { t: 'state', v: e.viewFor(seat, state.rematch) });
  }
  renderGame();
  scheduleBot();
}

/** Hands the turn to the bot once it is its own, after a pause you can see. */
function scheduleBot() {
  clearTimeout(state.botTimer);
  const e = state.engine;
  if (!state.solo || !e || e.phase === Phase.GAME_OVER || e.turn === HOST_SEAT) return;
  state.botTimer = setTimeout(() => {
    const engine = state.engine;
    if (!engine || engine.phase === Phase.GAME_OVER || engine.turn === HOST_SEAT) return;
    const seat = engine.turn;
    const move = decide(engine.viewFor(seat), state.solo, state.botRng);
    let acted;
    if (move.kind === 'play') acted = engine.playCard(seat, move.cardId, move.color);
    else if (move.kind === 'draw') acted = engine.draw(seat);
    else acted = engine.pass(seat);
    if (!acted && !engine.draw(seat)) return;
    broadcast();
  }, BOT_THINK_MS);
}

/**
 * Closes out a finished round, exactly once.
 *
 * The tally and the experience follow two different rules on purpose. The tally is a
 * record against real people, so a game against the machine does not touch it.
 * Experience is a record of time played, so every finished round counts — a progression
 * that ignored solo would punish exactly the players with nobody to play against.
 */
function recordIfFinished(v) {
  if (!v || v.ph !== Phase.GAME_OVER) return;
  if (v.rd === state.lastRecordedRound) return;
  state.lastRecordedRound = v.rd;
  const won = V.youWon(v);
  if (!state.solo) state.profile = recordRound(won, v.st);

  const gain = addXp(won);
  state.profile = gain.profile;
  state.lastXp = gain.gained;
  state.xpBefore = gain.before;
  state.levelUp = gain.levelledUp ? gain : null;
  // The badge next to my avatar is on everybody's screen, so a level crossed between two
  // rounds has to travel — otherwise a whole evening of rematches shows me at the level
  // I opened the room with.
  if (gain.levelledUp) announceMyself();
}

/** The moment a level lands: loud, and on top of the result rather than instead of it. */
function showLevelUp() {
  const gain = state.levelUp;
  if (!gain) return;
  state.levelUp = null;
  $('levelup-title').textContent = `Niveau ${gain.to}`;
  $('levelup-sub').textContent = gain.to - gain.from > 1
    ? `${gain.to - gain.from} niveaux d'un coup.`
    : 'Un niveau de plus.';
  $('levelup-badge').innerHTML = levelBadgeHtml(gain.to, 72);
  // Capped: a jump of several levels can hand over a fistful, and a list taller than
  // the screen is not a celebration.
  const shown = gain.unlocked.slice(0, 5);
  $('levelup-list').innerHTML = gain.unlocked.length === 0 ? '' : `
    <div style="font-size:12px;font-weight:900;margin-bottom:6px">
      ${gain.unlocked.length === 1 ? 'Débloqué' : `${gain.unlocked.length} débloqués`}</div>
    ${shown.map((item) => `<div class="unlock">
      <span class="chip" style="background:#0b0e14">${Cosm.KINDS[item.kind].label}</span>
      <b>${esc(item.name)}</b></div>`).join('')}
    ${gain.unlocked.length > 5
      ? `<div style="color:var(--dim);font-size:12px;margin-top:6px">et ${gain.unlocked.length - 5} de plus</div>`
      : ''}`;
  $('levelup').classList.add('on');
}
$('levelup-ok').onclick = () => $('levelup').classList.remove('on');

// -------------------------------------------------------------- host and guest

function hostHandlers() {
  return {
    onGuestReady: () => { /* the seat is handed out on Hello */ },
    onGuestGone: (key) => {
      const seat = state.seatOfKey.get(key);
      if (seat === undefined) return;
      if (state.engine) {
        dialog('Joueur déconnecté', `${nameOfSeat(seat)} a quitté la partie.`);
        return;
      }
      state.seatOfKey.delete(key);
      state.keyOfSeat.delete(seat);
      state.players = state.players.filter((p) => p.s !== seat);
      delete state.photos[seat];
      renderHostLobby();
      broadcastLobby();
    },
    onMessage: (key, msg) => onGuestMessage(key, msg),
  };
}

function nameOfSeat(seat) {
  return state.players.find((p) => p.s === seat)?.n ?? 'Un joueur';
}

function firstFreeSeat() {
  const taken = new Set(state.players.map((p) => p.s));
  for (let s = 1; s < MAX_PLAYERS; s++) if (!taken.has(s)) return s;
  return null;
}

function broadcastLobby() {
  state.net?.broadcast({ t: 'lobby', p: state.players, st: !!state.engine, md: state.mods });
}

function onGuestMessage(key, msg) {
  const e = state.engine;
  switch (msg.t) {
    case 'wearing': {
      // The seat the host knows, never the one the guest claimed.
      const seat = state.seatOfKey.get(key);
      if (seat === undefined) return;
      state.players = state.players.map((p) => (p.s !== seat ? p : {
        ...p, fr: msg.fr || '', ti: msg.ti || '', nm: msg.nm || '', lv: msg.lv || 1,
      }));
      broadcastLobby();
      if (state.myView) renderGame(); else renderHostLobby();
      return;
    }

    case 'hello': {
      let seat = state.seatOfKey.get(key);
      if (seat === undefined) {
        if (state.engine) {
          state.net.send(key, { t: 'welcome', ok: false, r: 'La partie a déjà commencé' });
          return;
        }
        seat = firstFreeSeat();
        if (seat === null) {
          state.net.send(key, { t: 'welcome', ok: false, r: 'Salon complet' });
          return;
        }
      }
      state.seatOfKey.set(key, seat);
      state.keyOfSeat.set(seat, key);
      state.players = [...state.players.filter((p) => p.s !== seat), {
        s: seat,
        n: msg.n || 'Joueur',
        a: msg.a ?? 0,
        fr: msg.fr || '',
        ti: msg.ti || '',
        nm: msg.nm || '',
        lv: msg.lv || 1,
      }].sort((a, b) => a.s - b.s);
      state.net.send(key, { t: 'welcome', ok: true, s: seat });
      broadcastLobby();
      for (const [photoSeat, data] of Object.entries(state.photos)) {
        if (Number(photoSeat) !== seat) state.net.send(key, { t: 'photo', s: Number(photoSeat), d: data });
      }
      renderHostLobby();
      break;
    }
    case 'photo': {
      const seat = state.seatOfKey.get(key);
      if (seat === undefined) return;
      state.photos[seat] = msg.d;
      for (const [otherKey, otherSeat] of state.seatOfKey) {
        if (otherSeat !== seat) state.net.send(otherKey, { t: 'photo', s: seat, d: msg.d });
      }
      renderHostLobby();
      renderGame();
      break;
    }
    case 'play': {
      const seat = state.seatOfKey.get(key);
      if (seat === undefined || !e) return;
      if (e.playCard(seat, msg.i, msg.c ?? null)) broadcast();
      else state.net.send(key, { t: 'state', v: e.viewFor(seat, state.rematch) });
      break;
    }
    case 'draw': {
      const seat = state.seatOfKey.get(key);
      if (seat === undefined || !e) return;
      if (e.draw(seat)) broadcast();
      else state.net.send(key, { t: 'state', v: e.viewFor(seat, state.rematch) });
      break;
    }
    case 'pass': {
      const seat = state.seatOfKey.get(key);
      if (seat === undefined || !e) return;
      if (e.pass(seat)) broadcast();
      else state.net.send(key, { t: 'state', v: e.viewFor(seat, state.rematch) });
      break;
    }
    case 'rematch': {
      const seat = state.seatOfKey.get(key);
      if (seat === undefined) return;
      state.rematch.add(seat);
      if (state.rematch.size >= (state.engine?.playerCount ?? Infinity)) startRound(false);
      else broadcast();
      break;
    }
    case 'chat': {
      const seat = state.seatOfKey.get(key);
      const text = cleanLine(msg.m);
      if (seat === undefined || !text) return;
      // Relayed with the seat the host knows, never the one the guest claimed, and never
      // back to the sender — its own line is already on its screen.
      relay({ t: 'chat', s: seat, m: text }, key);
      addChat(seat, text);
      break;
    }
    case 'emo': {
      const seat = state.seatOfKey.get(key);
      if (seat === undefined || !STICKERS[msg.e]) return;
      relay({ t: 'emo', s: seat, e: msg.e }, key);
      addEmote(seat, msg.e);
      break;
    }
    case 'bye': {
      const seat = state.seatOfKey.get(key);
      dialog('Partie terminée', `${nameOfSeat(seat)} a quitté la partie.`);
      break;
    }
    default: break;
  }
}

/** Relays a message to every guest but the one it came from. */
function relay(msg, except) {
  for (const key of state.seatOfKey.keys()) {
    if (key !== except) state.net?.send(key, msg);
  }
}

function onHostMessage(msg) {
  switch (msg.t) {
    case 'welcome':
      if (!msg.ok) {
        dialog('Salon refusé', msg.r || 'Le salon a refusé la connexion.');
        return;
      }
      state.mySeat = msg.s ?? 1;
      if (state.profile.photo) state.net.send({ t: 'photo', s: state.mySeat, d: state.profile.photo });
      renderGuestLobby();
      break;
    case 'lobby':
      state.players = (msg.p || []).slice().sort((a, b) => a.s - b.s);
      state.mods = orderedMods(msg.md || []);
      renderGuestLobby();
      // Levels and outfits change mid-evening, and the table is what shows them.
      if (state.myView) renderGame();
      break;
    case 'photo':
      state.photos[msg.s] = msg.d;
      renderGuestLobby();
      renderGame();
      break;
    case 'state':
      state.myView = msg.v;
      state.mySeat = msg.v.y;
      recordIfFinished(msg.v);
      show('game');
      renderGame();
      break;
    case 'chat': {
      const text = cleanLine(msg.m);
      if (text) addChat(msg.s ?? 0, text);
      break;
    }
    case 'emo':
      addEmote(msg.s ?? 0, msg.e);
      break;
    case 'bye':
      dialog('Partie terminée', "L'hôte a quitté la partie.");
      break;
    default: break;
  }
}

// ------------------------------------------------------------------- QR pairing

function drawQr(target, text) {
  const qr = qrcode(0, 'M');
  qr.addData(text);
  qr.make();
  const box = $(target);
  box.innerHTML = qr.createSvgTag({ scalable: true, margin: 2 });
  // Kept alongside the image so the pairing can be driven without a camera — by the
  // tests, and by anyone who prefers copying a code to scanning it.
  box.dataset.text = text;
}

let cameraStream = null;
function stopCamera() {
  cameraStream?.getTracks().forEach((t) => t.stop());
  cameraStream = null;
  $('host-video').style.display = 'none';
  $('join-video').style.display = 'none';
}

async function scanQr(videoId, onFound) {
  if (!('BarcodeDetector' in window)) {
    dialog(
      'Lecture impossible ici',
      "Ce navigateur ne sait pas lire un QR code. Utilise l'appareil photo de ton " +
      'téléphone pour scanner, ou colle le code à la main.',
    );
    return;
  }
  try {
    cameraStream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
  } catch {
    dialog('Caméra refusée', "Sans la caméra, colle le code à la main.");
    return;
  }
  const video = $(videoId);
  video.style.display = 'block';
  video.srcObject = cameraStream;
  await video.play();
  const detector = new BarcodeDetector({ formats: ['qr_code'] });
  const tick = async () => {
    if (!cameraStream) return;
    const found = await detector.detect(video).catch(() => []);
    if (found.length) {
      const value = found[0].rawValue;
      stopCamera();
      onFound(value);
      return;
    }
    requestAnimationFrame(tick);
  };
  tick();
}

$('go-host').onclick = () => show('create');

let createCustom = false;
let createMods = [];

function renderCreate() {
  $('create-normal').classList.toggle('picked', !createCustom);
  $('create-custom').classList.toggle('picked', createCustom);
  $('create-mods').classList.toggle('hidden', !createCustom);
}
$('create-normal').onclick = () => { createCustom = false; renderCreate(); };
$('create-custom').onclick = () => { createCustom = true; renderCreate(); };
mountModPicker('mod-list', () => createMods, (next) => { createMods = next; });
renderCreate();

$('create-go').onclick = () => openRoom(createCustom ? createMods.slice() : []);

async function openRoom(mods) {
  teardown();
  state.role = 'host';
  state.mods = orderedMods(mods);
  state.social.enabled = true;
  state.mySeat = HOST_SEAT;
  state.players = [meAsPlayer(HOST_SEAT)];
  state.photos = state.profile.photo ? { [HOST_SEAT]: state.profile.photo } : {};
  state.net = new WebRtcHost(hostHandlers());
  show('host');
  renderHostLobby();
  await newInvite();
}

async function newInvite() {
  $('host-status').innerHTML = '<div class="spinner"></div><span>Préparation de l\'invitation…</span>';
  try {
    const offer = await state.net.invite();
    const link = `${location.href.split('#')[0].split('?')[0]}#${offer}`;
    drawQr('qrbox', link);
    $('host-invite').value = link;
    $('host-qr-panel').classList.remove('hidden');
    $('host-status').innerHTML = '<span style="color:var(--dim)">En attente de sa réponse…</span>';
  } catch (err) {
    dialog('Connexion impossible', err.message);
  }
}

$('host-invite-more').onclick = () => newInvite();
$('host-copy').onclick = async () => {
  try {
    await navigator.clipboard.writeText($('host-invite').value);
    $('host-copy').textContent = 'Lien copié';
  } catch {
    $('host-invite').select();
  }
};
$('host-scan').onclick = () => scanQr('host-video', (value) => acceptAnswer(value));
$('host-accept').onclick = () => acceptAnswer($('host-answer').value);

async function acceptAnswer(text) {
  if (!text || !text.trim()) return;
  try {
    await state.net.acceptAnswer(text.trim());
    $('host-answer').value = '';
    $('host-qr-panel').classList.add('hidden');
    $('host-status').innerHTML = '<div class="spinner"></div><span>Connexion…</span>';
  } catch (err) {
    dialog('Réponse refusée', `${err?.message || err}\n\nNavigateur : ${navigator.userAgent}`);
  }
}

$('host-start').onclick = () => {
  if (state.players.length < MIN_PLAYERS) return;
  state.net.closePending();
  startRound(true);
  broadcastLobby();
  show('game');
};

function rosterHtml(target) {
  const rows = state.players.map((p) => {
    const title = titleOf(p);
    return `
    <div class="row-line">
      <div style="position:relative;display:flex">
        ${framedAvatarHtml(p.n, p.a, state.photos[p.s], 38, frameOf(p))}
        <div style="position:absolute;right:-2px;bottom:-2px">${levelBadgeHtml(p.lv || 1, 18)}</div>
      </div>
      <div class="grow" style="min-width:0">
        <div class="ellipsis">${pseudoHtml(p.n, nameColorOf(p), 15)}</div>
        ${title ? `<div class="ellipsis" style="color:var(--dim);font-size:11px;font-weight:700">${esc(title)}</div>` : ''}
      </div>
      ${p.s === HOST_SEAT ? '<span class="chip gold">Hôte</span>' : ''}
      ${p.s === state.mySeat ? '<span class="chip">Toi</span>' : ''}
    </div>`;
  }).join('');
  const missing = Math.max(0, MIN_PLAYERS - state.players.length);
  const waiting = Array.from({ length: missing }, () => `
    <div class="row-line"><div style="width:38px;text-align:center;color:var(--dim)">…</div>
    <div class="grow" style="color:var(--dim);font-size:14px">En attente d'un joueur</div></div>`).join('');
  $(target).innerHTML = rows + waiting;
}

/**
 * The mod list, wired the same way in the create screen and the solo screen so the two
 * can never offer different rules. Every mod is independent — ticking one never unticks
 * another.
 */
function mountModPicker(target, get, set) {
  const box = $(target);
  const paint = () => {
    const chosen = get();
    box.innerHTML = MOD_ORDER.map((mod) => {
      const on = chosen.includes(mod);
      return `<div class="panel mod-row ${on ? 'picked' : ''}" data-mod="${mod}">
        <div class="row-line" style="align-items:flex-start">
          <span class="tick ${on ? 'on' : ''}">${on ? '&#10003;' : ''}</span>
          <div class="grow">
            <div class="mod-name">${esc(MOD_INFO[mod].label)}</div>
            <div class="mod-blurb">${esc(MOD_INFO[mod].blurb)}</div>
          </div>
        </div>
      </div>`;
    }).join('');
    box.querySelectorAll('[data-mod]').forEach((node) => {
      node.onclick = () => {
        const mod = node.dataset.mod;
        const current = get();
        set(current.includes(mod) ? current.filter((m) => m !== mod) : [...current, mod]);
        paint();
      };
    });
  };
  paint();
}

/** What a room is playing with, so nobody discovers the +8 by eating one. */
function modSummaryHtml(target) {
  const mods = orderedMods(state.mods);
  $(target).innerHTML = `
    <div class="label">${mods.length ? 'PARTIE PERSONNALISÉE' : 'PARTIE NORMALE'}</div>
    ${mods.length
      ? mods.map((m) => `<span class="chip gold" style="margin-top:8px">${esc(MOD_INFO[m].label)}</span>`).join(' ')
      : '<div style="color:var(--dim);font-size:13px;margin-top:8px">Règles maison habituelles, jeu de 108 cartes.</div>'}`;
}

function renderHostLobby() {
  rosterHtml('host-roster');
  modSummaryHtml('host-mods');
  $('roster-label').textContent = `JOUEURS ${state.players.length}/${MAX_PLAYERS}`;
  const ready = state.players.length >= MIN_PLAYERS;
  $('host-start').disabled = !ready;
  $('host-start').textContent = ready ? 'Lancer la partie' : 'Il manque un joueur';
  $('host-invite-more').classList.toggle('hidden', state.players.length >= MAX_PLAYERS);
}

function renderGuestLobby() {
  rosterHtml('lobby-roster');
  modSummaryHtml('lobby-mods');
  $('lobby-status').innerHTML = state.players.length
    ? '<div class="spinner"></div><span>En attente que l\'hôte lance…</span>'
    : '<div class="spinner"></div><span>Connexion au salon…</span>';
}

$('go-join').onclick = () => { teardown(); show('join'); };
$('join-scan').onclick = () => scanQr('join-video', (value) => joinWith(value));
$('join-go').onclick = () => joinWith($('join-code').value);

async function joinWith(raw) {
  const text = (raw || '').trim();
  if (!text) return;
  const offer = text.includes('#') ? decodeURIComponent(text.split('#')[1]) : text;
  teardown();
  state.role = 'guest';
  state.social.enabled = true;
  state.net = new WebRtcGuest({
    onReady: () => {
      state.net.send({
        ...meAsPlayer(state.mySeat), t: 'hello', c: '',
      });
    },
    onMessage: onHostMessage,
    onClosed: () => dialog('Connexion perdue', "Le lien avec l'hôte a été coupé."),
  });
  try {
    const answer = await state.net.answerTo(offer);
    drawQr('lobby-qrbox', answer);
    $('lobby-answer').value = answer;
    show('lobby');
    renderGuestLobby();
  } catch (err) {
    // Saying "unreadable code" for every failure hides the one that matters: a browser
    // refusing the description is not the same problem as a mistyped code, and only the
    // real message says which.
    const detail = err?.message || String(err);
    const truncated = offer.length > 24 ? `${offer.slice(0, 12)}…${offer.slice(-8)}` : offer;
    dialog(
      'Connexion impossible',
      `Le code a été lu (${offer.length} caractères : ${truncated}) mais la connexion ` +
      `a échoué.\n\nRaison exacte : ${detail}\n\n` +
      `Navigateur : ${navigator.userAgent}`,
    );
  }
}

$('lobby-copy').onclick = async () => {
  try {
    await navigator.clipboard.writeText($('lobby-answer').value);
    $('lobby-copy').textContent = 'Copié';
  } catch {
    $('lobby-answer').select();
  }
};

// ------------------------------------------------------------------- chat & stickers

const FLASH_MS = 5000;
const FADE_MS = 200;
const EMOTE_MS = 2600;
const CHAT_HISTORY = 60;

function sendChat(raw) {
  const text = cleanLine(raw);
  if (!text) return;
  const seat = state.mySeat;
  const msg = { t: 'chat', s: seat, m: text };
  if (state.role === 'guest') state.net?.send(msg);
  else state.net?.broadcast(msg);
  addChat(seat, text);
}

function sendSticker(index) {
  if (!STICKERS[index]) return;
  const seat = state.mySeat;
  const msg = { t: 'emo', s: seat, e: index };
  if (state.role === 'guest') state.net?.send(msg);
  else state.net?.broadcast(msg);
  addEmote(seat, index);
}

function addChat(seat, text) {
  const mine = seat === state.mySeat;
  const line = { id: state.socialId++, seat, name: nameOfSeat(seat), text, mine };
  const social = state.social;
  social.chat = [...social.chat, line].slice(-CHAT_HISTORY);
  // Your own line does not need popping at you, and neither does one that arrives while
  // the chat is already open in front of you.
  if (!mine && !social.open) {
    social.flash = [...social.flash, line];
    social.unread += 1;
    setTimeout(() => {
      social.flash = social.flash.filter((l) => l.id !== line.id);
      renderSocial();
    }, FLASH_MS);
  }
  renderSocial();
}

function addEmote(seat, index) {
  const sticker = STICKERS[index];
  if (!sticker) return;
  const layer = $('emote-layer');
  const node = document.createElement('div');
  // The sender's place on *this* screen: the rivals stand in a row along the top, so the
  // one on the left throws from the left. Your own comes up from your hand, which is the
  // only feedback you get that it actually went out.
  const v = state.myView;
  const rivals = v?.ri ?? [];
  const index0 = rivals.findIndex((r) => r.s === seat);
  const mine = !v || seat === v.y;
  const x = mine || index0 < 0 ? 0.5 : (index0 + 0.5) / rivals.length;
  node.className = `emote ${mine ? 'from-bottom' : 'from-top'}`;
  node.style.left = `${x * 100}%`;
  node.style.top = mine ? '72%' : '10%';
  node.textContent = sticker;
  layer.appendChild(node);
  setTimeout(() => node.remove(), EMOTE_MS);
}

function openChat() {
  state.social.open = true;
  state.social.unread = 0;
  state.social.flash = [];
  $('chat').classList.add('on');
  renderSocial();
  // Focused on open so the keyboard comes up with the sheet rather than after it.
  setTimeout(() => $('chat-input').focus(), 60);
}

function closeChat() {
  state.social.open = false;
  $('chat').classList.remove('on');
  renderSocial();
}

function renderSocial() {
  const social = state.social;
  $('chat-bar').classList.toggle('hidden', !social.enabled);
  $('sticker-rail').classList.toggle('hidden', !social.enabled);
  if (!social.enabled) {
    $('chat-flash').innerHTML = '';
    return;
  }

  const last = social.chat[social.chat.length - 1];
  const preview = $('chat-preview');
  preview.textContent = last ? `${last.name} : ${last.text}` : 'Écrire un message…';
  preview.classList.toggle('empty', !last);
  $('chat-unread').textContent = String(social.unread);
  $('chat-unread').classList.toggle('hidden', social.unread === 0);

  // Three at a time: past that the table disappears behind the conversation.
  $('chat-flash').innerHTML = social.flash.slice(-3).map((l) => `
    <div class="flash"><div class="who">${esc(l.name)}</div>
    <div class="what">${esc(l.text)}</div></div>`).join('');

  $('chat-log').innerHTML = social.chat.map((l) => `
    <div class="said ${l.mine ? 'mine' : ''}"><div class="bubble">
      ${l.mine ? '' : `<div class="who">${esc(l.name)}</div>`}
      <div>${esc(l.text)}</div>
    </div></div>`).join('');
  const log = $('chat-log');
  log.scrollTop = log.scrollHeight;
}

/**
 * Folded away by default — one button — and opened on demand. Left permanently open it
 * was a column of emoji sitting next to the table the whole game.
 */
let railOpen = false;

function renderRail() {
  const rail = $('sticker-rail');
  // Only what has been earned is offered, but the index is into the whole catalogue —
  // otherwise two players at different levels would disagree about what index 7 means.
  const mine = stickersOf(state.profile);
  rail.classList.toggle('wide', railOpen && mine.length > 6);
  rail.innerHTML = railOpen
    ? mine.map((item, i) => `<button type="button" data-sticker="${i}">${item.text}</button>`).join('')
      + '<button type="button" class="shut" data-rail="0">&#10005;</button>'
    : `<button type="button" data-rail="1">${mine[0].text}</button>`;
  rail.querySelectorAll('[data-sticker]').forEach((node) => {
    node.onclick = () => {
      railOpen = false;
      renderRail();
      sendSticker(Number(node.dataset.sticker));
    };
  });
  rail.querySelectorAll('[data-rail]').forEach((node) => {
    node.onclick = () => { railOpen = node.dataset.rail === '1'; renderRail(); };
  });
}
renderRail();

$('chat-bar').onclick = openChat;
$('chat-close').onclick = closeChat;
$('chat').onclick = (e) => { if (e.target === $('chat')) closeChat(); };
$('chat-send').onclick = () => {
  sendChat($('chat-input').value);
  $('chat-input').value = '';
  armSend();
};
$('chat-input').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') { e.preventDefault(); $('chat-send').click(); }
});
// Gold once there is something to send, grey the rest of the time.
const armSend = () => { $('chat-send').disabled = $('chat-input').value.trim() === ''; };
$('chat-input').addEventListener('input', armSend);
$('chat-input').setAttribute('maxlength', String(MAX_CHARS));
armSend();

/**
 * Keeps the sheet above the keyboard.
 *
 * There is no portable CSS for "how tall is the keyboard": the one thing every mobile
 * browser agrees on is that the visual viewport shrinks. The difference between it and
 * the layout viewport is the keyboard, so that is what the sheet is lifted by.
 */
if (window.visualViewport) {
  const lift = () => {
    const vv = window.visualViewport;
    const hidden = Math.max(0, window.innerHeight - vv.height - vv.offsetTop);
    document.documentElement.style.setProperty('--kb', `${Math.round(hidden)}px`);
  };
  window.visualViewport.addEventListener('resize', lift);
  window.visualViewport.addEventListener('scroll', lift);
  lift();
}

// ------------------------------------------------------------------- the table

/**
 * How wide the cards can be so the whole hand is visible without scrolling. One row
 * while they stay readable, two rows past that — never a scrollbar, which would hide
 * the card you need.
 */
function handMetrics(count, available) {
  const STEP = 0.62;
  const widest = (n, cap) => Math.min(cap, available / (1 + (n - 1) * STEP));
  const oneRow = widest(count, 92);
  if (oneRow >= 46 || count <= 1) {
    return { width: Math.max(oneRow, 30), perRow: count };
  }
  const half = Math.ceil(count / 2);
  return { width: Math.max(widest(half, 74), 30), perRow: half };
}

let knownCards = new Set();
let knownRound = -1;

/** What your own Espion showed you, under the tile of the rival who holds it. */
function revealedStrip(cards) {
  if (!cards || !cards.length) return '';
  return `<div class="revealed">${cards.slice(0, 3)
    .map((c) => `<div class="mini">${cardFace(c)}</div>`).join('')}</div>`;
}

function renderGame() {
  const v = state.myView;
  if (!v) return;
  renderSocial();

  // ---- my own table: the cloth and the deck I chose, nobody else's
  const felt = wornOf(state.profile, 'FELT');
  $('screen-game').style.background =
    `radial-gradient(circle at 50% 38%, ${felt.a}, ${felt.b} 45%, ${felt.c})`;
  const back = wornOf(state.profile, 'BACK');

  // ---- rivals
  const single = v.ri.length === 1 ? v.ri[0] : null;
  if (single) {
    const theirTurn = v.ts === single.s && v.ph !== Phase.GAME_OVER;
    const seat = playerAt(single.s);
    const title = titleOf(seat);
    $('rivals').innerHTML = `
      <div style="position:relative;display:flex">
        ${framedAvatarHtml(single.n, single.a, state.photos[single.s], 44, frameOf(seat), theirTurn ? 'turn' : '')}
        <div style="position:absolute;right:-2px;bottom:-2px">${levelBadgeHtml(seat?.lv || 1, 18)}</div>
      </div>
      <div class="grow">
        <div class="ellipsis">${pseudoHtml(single.n, nameColorOf(seat), 15)}</div>
        ${title ? `<div class="ellipsis" style="color:var(--dim);font-size:10px;font-weight:700">${esc(title)}</div>` : ''}
        <div style="font-size:12px;color:${single.c === 1 ? 'var(--red)' : 'var(--dim)'};
          font-weight:${single.c === 1 ? 900 : 400}">
          ${single.c} carte${single.c > 1 ? 's' : ''}${single.c === 1 ? ' · UNO !' : ''}
        </div>
      </div>
      <span class="chip">${v.ys} — ${single.p}</span>
      <button class="icon-btn" id="quit-x">✕</button>`;
  } else {
    $('rivals').innerHTML = `
      <div class="grow" style="display:flex;gap:10px;align-items:center">
        ${v.ri.map((r) => {
          const turn = v.ts === r.s && v.ph !== Phase.GAME_OVER;
          return `<div class="rival ${turn ? 'turn' : ''}">
            ${framedAvatarHtml(r.n, r.a, state.photos[r.s], 40, frameOf(playerAt(r.s)), turn ? 'turn' : '')}
            <span class="chip count ${r.c === 1 ? 'red' : ''}">${r.c}</span>
            <div class="name">${esc(r.n)}</div>
            ${revealedStrip(r.rv)}
          </div>`;
        }).join('')}
      </div>
      <button class="icon-btn" id="quit-x">✕</button>`;
  }
  $('quit-x').onclick = leaveGame;

  // ---- the fan of whoever the table waits on
  // Cards *you* have been shown by an Espion are drawn face up at the end of the fan
  // rather than off to one side: they are still in that hand, and anywhere else reads as
  // a second pile. Nobody else's screen shows them.
  const shown = single ?? v.ri.find((r) => r.s === v.ts);
  const total = Math.min(shown?.c ?? 0, 14);
  const faceUp = (shown?.rv ?? []).slice(0, total);
  const backs = total - faceUp.length;
  $('rival-fan').innerHTML =
    Array.from({ length: backs }, () => `<div class="card">${cardBack(back)}</div>`).join('')
    + faceUp.map((c) => `<div class="card">${cardFace(c)}</div>`).join('');

  // ---- deck and discard
  const mustDraw = V.mustDraw(v);
  $('deck').className = mustDraw ? 'urgent' : '';
  $('deck').innerHTML = cardBack(back) + (mustDraw ? '<div class="ring"></div>' : '');
  $('deck').onclick = () => tapDeck();
  $('deck-label').textContent = mustDraw ? 'Pioche' : String(v.dk);
  $('discard').innerHTML =
    `<div class="active-ring" style="color:${PALETTE[v.ac]}"></div>` + cardFace(v.t);

  $('pending').classList.toggle('hidden', v.pd <= 0);
  $('pending').textContent = `+${v.pd}`;

  // ---- banner
  const yours = V.yourTurn(v);
  let text;
  if (v.ph === Phase.GAME_OVER) {
    text = V.youWon(v) ? 'Tu as gagné !' : `${V.nameOf(v, v.w)} a gagné`;
  } else if (V.mustAnswerPenalty(v) && v.pt === '4') {
    text = `+${v.pd} — contre avec ${bigPenalties(v)} ou un +2 ${colorName(v.ac)}`;
  } else if (V.mustAnswerPenalty(v)) {
    text = `+${v.pd} — contre-attaque ou encaisse`;
  } else if (v.ph === Phase.DECIDE_AFTER_DRAW && yours) {
    text = 'Carte piochée : pose-la ou passe';
  } else if (V.inBonus(v) && yours) {
    text = `Coup double — ${cardsLeft(v.xp)} à poser`;
  } else if (V.inBonus(v)) {
    text = `Coup double de ${V.turnName(v)} — ${cardsLeft(v.xp)}`;
  } else if (mustDraw) {
    text = 'Rien à poser — touche la pioche';
  } else if (yours) {
    text = 'À toi de jouer';
  } else {
    text = `Au tour de ${V.turnName(v)}`;
  }
  const arrow = V.playerCount(v) > 2 ? `<span class="chip">${v.dr > 0 ? '↻' : '↺'}</span> ` : '';
  $('banner').innerHTML = arrow + `<span class="${yours ? 'mine' : ''}">${esc(text)}</span>`;

  $('pass-wrap').classList.toggle('hidden', !V.canPass(v));
  $('pass-btn').textContent = V.inBonus(v) ? 'Arrêter là' : 'Passer mon tour';

  // ---- your hand
  if (v.rd !== knownRound) { knownCards = new Set(); knownRound = v.rd; }
  const available = Math.min(window.innerWidth, 640) - 28;
  const m = handMetrics(v.h.length, available);
  const rows = [];
  for (let i = 0; i < v.h.length; i += m.perRow) rows.push(v.h.slice(i, i + m.perRow));
  const step = m.width * 0.62;
  $('hand').innerHTML = rows.map((row) => `<div class="row">` + row.map((card, i) => {
    const playable = yours && v.l.includes(card.i);
    const dimmed = yours && !v.l.includes(card.i);
    const fresh = !knownCards.has(card.i);
    return `<div class="card ${playable ? 'playable' : ''} ${fresh ? 'fresh' : ''}"
      data-card="${card.i}"
      style="width:${m.width}px;margin-left:${i ? step - m.width : 0}px">
      ${cardFace(card, { dimmed })}</div>`;
  }).join('') + `</div>`).join('');
  v.h.forEach((c) => knownCards.add(c.i));

  $('hand').querySelectorAll('[data-card]').forEach((node) => {
    node.onclick = () => tapCard(Number(node.dataset.card));
  });

  // ---- overlays
  if (v.ph === Phase.GAME_OVER) showGameOver(v); else $('over').classList.remove('on');

  // Reacting here rather than at every call site keeps the toast and the penalty slam
  // from firing twice for one event.
  reactToEvent(v);
}

function colorName(c) {
  return { R: 'rouge', Y: 'jaune', G: 'vert', B: 'bleu', W: '' }[c];
}

let lastEventId = -1;
let lastPenaltyRound = -1;

function reactToEvent(v) {
  if (!v || v.ei === lastEventId) return;
  lastEventId = v.ei;
  if (v.ev) {
    $('toast').textContent = v.ev;
    $('toast').classList.remove('hidden');
    clearTimeout(state.toastTimer);
    state.toastTimer = setTimeout(() => $('toast').classList.add('hidden'), 2600);
  }
  // Eating a stack resolves in a single snapshot, so the table stops and spells it out.
  // Never on top of the colour picker though: two overlays stack their dimming into
  // near-black, and the player is in the middle of a decision.
  if (v.pk > 0 && v.ei !== lastPenaltyRound && !$('picker').classList.contains('on')) {
    lastPenaltyRound = v.ei;
    $('slam-amount').textContent = `+${v.pk}`;
    $('slam-who').textContent = V.penaltyIsMine(v)
      ? `Tu encaisses ${v.pk} cartes`
      : `${V.nameOf(v, v.pv)} encaisse ${v.pk} cartes`;
    $('slam').classList.add('on');
    navigator.vibrate?.(120);
    setTimeout(() => $('slam').classList.remove('on'), 1900);
  }
}

function showGameOver(v) {
  // The round is settled; nothing left to choose.
  $('picker').classList.remove('on');
  $('slam').classList.remove('on');
  state.pendingWild = null;
  $('over-title').textContent = V.youWon(v) ? 'Gagné !' : 'Perdu';
  $('over-sub').textContent = V.youWon(v)
    ? "Personne n'a rien vu venir."
    : `${V.nameOf(v, v.w)} a posé sa dernière carte.`;
  $('over-scores').innerHTML = v.ri.length === 1
    ? `<div style="font-size:26px;font-weight:900">${v.ys} — ${v.ri[0].p}</div>`
    : `<span class="chip gold">Toi ${v.ys}</span> ` +
      v.ri.map((r) => `<span class="chip">${esc(r.n)} ${r.p}</span>`).join(' ');
  $('over-xp').classList.toggle('hidden', state.lastXp <= 0);
  if (state.lastXp > 0) runXpBar('over-xp', state.xpBefore, state.lastXp);
  // On top of the result, not instead of it: the score is what you came for.
  showLevelUp();
  const waiting = v.ri.filter((r) => r.r).length;
  $('rematch-btn').disabled = v.ry;
  $('rematch-btn').textContent = v.ry
    ? `En attente… ${V.rematchReady(v)}/${V.playerCount(v)}`
    : (waiting ? `Revanche (${waiting} prêt${waiting > 1 ? 's' : ''})` : 'Revanche');
  $('over').classList.add('on');
}

// ------------------------------------------------------------------ your moves

function tapCard(cardId) {
  const v = state.myView;
  if (!v || !V.yourTurn(v)) return;
  if (!v.l.includes(cardId)) {
    $('toast').textContent = illegalReason(v);
    $('toast').classList.remove('hidden');
    clearTimeout(state.toastTimer);
    state.toastTimer = setTimeout(() => $('toast').classList.add('hidden'), 2200);
    return;
  }
  const card = v.h.find((c) => c.i === cardId);
  if (isWild(card)) { openPicker(cardId); return; }
  playCard(cardId, null);
}

function illegalReason(v) {
  if (!V.yourTurn(v)) return "Ce n'est pas ton tour.";
  if (v.pd > 0 && v.pt === '4') return `Il te faut ${bigPenalties(v)}, ou un +2 ${colorName(v.ac)}.`;
  if (v.pd > 0) return `Il te faut un +2 (ou ${bigPenalties(v)}) pour continuer la pile.`;
  if (v.ph === Phase.DECIDE_AFTER_DRAW) return 'Tu ne peux poser que la carte piochée.';
  if (V.inBonus(v) && !v.l.length) return 'Plus rien à poser : arrête le coup double.';
  if (!v.l.length) return 'Rien à poser : touche la pioche.';
  return 'Carte non jouable.';
}

/** "un +4", or "un +4 ou un +8" once the mod is on — never a rule the room is not playing. */
function bigPenalties(v) {
  return (v.md || []).includes(Mod.DRAW_EIGHT) ? 'un +4 ou un +8' : 'un +4';
}

/** "1 carte" / "2 cartes" — a bonus counter that reads as French, not as a number. */
function cardsLeft(count) {
  return count > 1 ? `${count} cartes` : `${count} carte`;
}

function openPicker(cardId) {
  state.pendingWild = cardId;
  $('picker-chips').innerHTML = PLAYABLE_COLORS
    .map((c) => `<div data-pick="${c}">${colorChip(c)}</div>`).join('');
  $('picker-chips').querySelectorAll('[data-pick]').forEach((node) => {
    node.onclick = () => {
      $('picker').classList.remove('on');
      playCard(state.pendingWild, node.dataset.pick);
      state.pendingWild = null;
    };
  });
  $('picker').classList.add('on');
}
$('picker').onclick = (e) => {
  if (e.target === $('picker')) { $('picker').classList.remove('on'); state.pendingWild = null; }
};

function playCard(cardId, color) {
  navigator.vibrate?.(12);
  if (state.role === 'guest') state.net.send({ t: 'play', i: cardId, c: color });
  else if (state.engine?.playCard(state.mySeat, cardId, color)) broadcast();
}

function tapDeck() {
  const v = state.myView;
  if (!v || !V.canDraw(v)) return;
  navigator.vibrate?.(12);
  if (state.role === 'guest') state.net.send({ t: 'draw' });
  else if (state.engine?.draw(state.mySeat)) broadcast();
}

$('pass-btn').onclick = () => {
  const v = state.myView;
  if (!v || !V.canPass(v)) return;
  if (state.role === 'guest') state.net.send({ t: 'pass' });
  else if (state.engine?.pass(state.mySeat)) broadcast();
};

$('rematch-btn').onclick = () => {
  if (state.solo) { startRound(false); return; }
  if (state.role === 'guest') { state.net.send({ t: 'rematch' }); return; }
  state.rematch.add(HOST_SEAT);
  if (state.rematch.size >= (state.engine?.playerCount ?? Infinity)) startRound(false);
  else broadcast();
};

function leaveGame() {
  if (state.role === 'guest') state.net?.send({ t: 'bye' });
  else state.net?.broadcast({ t: 'bye' });
  teardown();
  show('home');
  renderHome();
}
$('quit-btn').onclick = leaveGame;

// The board is redrawn on resize because the hand is sized to fit the screen exactly.
let resizeTimer = null;
window.addEventListener('resize', () => {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(renderGame, 120);
});

// A guest that arrived by scanning the host's QR lands here with the offer in the URL.
if (location.hash.length > 2) {
  const offer = decodeURIComponent(location.hash.slice(1));
  history.replaceState(null, '', location.pathname);
  joinWith(offer);
}

renderHome();
