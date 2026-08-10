// The unlock catalogue — the same table as the Android app's Cosmetics.kt, item for
// item, level for level. The two are compared line by line by
// web/test/catalogue-conformance.mjs, so an edit on one side that is not mirrored on the
// other fails the build rather than quietly giving one version different rewards.
//
// Colours are '#rrggbb' here and packed longs on the phone; everything else is identical.

import { MAX_LEVEL } from './levels.js';

export const KINDS = {
  FRAME: { code: 'fr', label: 'Cadres', blurb: 'Le cercle autour de ta photo.' },
  BACK: { code: 'bk', label: 'Dos de carte', blurb: "Ce que montrent la pioche et les mains adverses." },
  FELT: { code: 'ft', label: 'Tapis', blurb: 'La couleur de la table.' },
  TITLE: { code: 'ti', label: 'Titres', blurb: 'La ligne sous ton pseudo.' },
  NAME: { code: 'nm', label: 'Pseudo', blurb: 'La couleur de ton nom.' },
  STICKER: { code: 'st', label: 'Stickers', blurb: 'Ce que tu peux balancer en partie.' },
};

/** Declaration order, which is also the order the tabs appear in. */
export const KIND_ORDER = ['FRAME', 'BACK', 'FELT', 'TITLE', 'NAME', 'STICKER'];

const frame = (id, name, level, style, a, b = '', c = '') =>
  ({ id: `fr.${id}`, kind: 'FRAME', name, level, a, b, c, style, text: '' });
const back = (id, name, level, top, bottom, oval) =>
  ({ id: `bk.${id}`, kind: 'BACK', name, level, a: top, b: bottom, c: oval, style: 'SOLID', text: '' });
const felt = (id, name, level, light, mid, dark) =>
  ({ id: `ft.${id}`, kind: 'FELT', name, level, a: light, b: mid, c: dark, style: 'SOLID', text: '' });
const title = (id, name, level) =>
  ({ id: `ti.${id}`, kind: 'TITLE', name, level, a: '', b: '', c: '', style: 'SOLID', text: '' });
const named = (id, name, level, a, b = '', c = '') =>
  ({ id: `nm.${id}`, kind: 'NAME', name, level, a, b, c, style: 'SOLID', text: '' });
const sticker = (id, name, level, text) =>
  ({ id: `st.${id}`, kind: 'STICKER', name, level, a: '', b: '', c: '', style: 'SOLID', text });

export const FRAMES = [
  frame('encre', 'Encre', 1, 'SOLID', '#3b475d'),
  frame('braise', 'Braise', 3, 'SOLID', '#f23b2e'),
  frame('menthe', 'Menthe', 6, 'SOLID', '#41c258'),
  frame('azur', 'Azur', 9, 'SOLID', '#2e9cf2'),
  frame('safran', 'Safran', 12, 'SOLID', '#ffc21a'),
  frame('amethyste', 'Améthyste', 15, 'SOLID', '#7a5cf0'),
  frame('couchant', 'Couchant', 18, 'DUO', '#ff8a1e', '#f23b2e'),
  frame('lagon', 'Lagon', 21, 'DUO', '#19b79b', '#2e9cf2'),
  frame('pointilles', 'Pointillés', 24, 'DASH', '#f3f6fb'),
  frame('barbapapa', 'Barbe à papa', 27, 'DUO', '#f25da8', '#7a5cf0'),
  frame('perles', 'Perles', 30, 'BEADS', '#2ef2c4'),
  frame('cuivre', 'Cuivre', 33, 'DUAL', '#c87137', '#f0a862'),
  frame('argent', 'Argent', 36, 'SHINE', '#a8b4c6', '#fdfbf4'),
  frame('feuillage', 'Feuillage', 39, 'DUO', '#41c258', '#19b79b'),
  frame('orage', 'Orage', 42, 'NOTCH', '#2e9cf2', '#3b475d'),
  frame('lave', 'Lave', 45, 'GLOW', '#ff5a1e'),
  frame('givre', 'Givre', 48, 'GLOW', '#9fe8ff'),
  frame('or', 'Or', 51, 'SHINE', '#ffc531', '#fff3c4'),
  frame('prisme', 'Prisme', 55, 'SPIN', '#f23b2e', '#ffc21a', '#2e9cf2'),
  frame('bitume', 'Bitume', 58, 'NOTCH', '#9daabf', '#4a5568'),
  frame('sangdencre', "Sang d'encre", 62, 'DUAL', '#c01c12', '#f23b2e'),
  frame('aurore', 'Aurore', 66, 'SPIN', '#2ef2c4', '#7a5cf0', '#2e9cf2'),
  frame('rubis', 'Rubis', 70, 'GLOW', '#ff2d55'),
  frame('emeraude', 'Émeraude', 74, 'GLOW', '#2ee06a'),
  frame('saphir', 'Saphir', 78, 'GLOW', '#3d7dff'),
  frame('onyx', 'Onyx', 82, 'DUAL', '#2b3242', '#6b7688'),
  frame('platine', 'Platine', 86, 'SHINE', '#d8e0ec', '#fdfbf4'),
  frame('cendre', 'Cendre ardente', 90, 'SPIN', '#ff8a1e', '#c01c12', '#2b1a12'),
  frame('couronne', 'Couronne', 95, 'DUAL', '#ffc531', '#fdfbf4'),
  frame('centieme', 'Centième', 100, 'SPIN', '#ffc531', '#f23b2e', '#2e9cf2'),
];

export const BACKS = [
  back('classique', 'Classique', 1, '#2b3242', '#161a24', '#f23b2e'),
  back('brique', 'Brique', 4, '#5a1f1a', '#2a0e0b', '#ff8a1e'),
  back('foret', 'Forêt', 10, '#1e4030', '#0c1d16', '#41c258'),
  back('ocean', 'Océan', 16, '#16344f', '#081826', '#2e9cf2'),
  back('dore', 'Doré', 22, '#4a3a12', '#221a07', '#ffc531'),
  back('violine', 'Violine', 28, '#382357', '#190f28', '#7a5cf0'),
  back('reglisse', 'Réglisse', 34, '#1a1a1e', '#07070a', '#f3f6fb'),
  back('sable', 'Sable', 40, '#5c4b2e', '#2a2113', '#ffc21a'),
  back('menthe', 'Menthe glaciale', 46, '#17423c', '#091e1b', '#2ef2c4'),
  back('cerise', 'Cerise noire', 52, '#3e0e1e', '#1b040c', '#f25da8'),
  back('cuivre', 'Cuivre chaud', 57, '#52341a', '#24160a', '#c87137'),
  back('nuit', 'Bleu de nuit', 63, '#17203d', '#070b1a', '#6e8cff'),
  back('poudre', 'Rose poudré', 68, '#54293d', '#25101b', '#f25da8'),
  back('vertdegris', 'Vert-de-gris', 73, '#2a423b', '#121d1a', '#19b79b'),
  back('pourpre', 'Pourpre royal', 80, '#421338', '#1d0718', '#ffc531'),
  back('orblanc', 'Or blanc', 85, '#3b3f49', '#171a20', '#d8e0ec'),
  back('retro', 'Néon rétro', 92, '#201242', '#0b0620', '#2ef2c4'),
  back('centfaces', 'Cent faces', 98, '#4a3a12', '#14161d', '#ffc531'),
];

export const FELTS = [
  felt('nuit', 'Table de nuit', 1, '#2a3444', '#171d27', '#080a10'),
  felt('feutre', 'Feutre vert', 5, '#27503a', '#14301f', '#06130c'),
  felt('bordeaux', 'Bordeaux', 11, '#54202a', '#2e1017', '#120508'),
  felt('encre', 'Encre bleue', 17, '#22375c', '#111d35', '#050a14'),
  felt('cendre', 'Cendre', 23, '#3d4148', '#212429', '#0b0c0e'),
  felt('prune', 'Prune', 29, '#3e2b57', '#221733', '#0c0714'),
  felt('profonde', 'Forêt profonde', 35, '#1e4231', '#0f2519', '#040d08'),
  felt('sable', 'Sable chaud', 41, '#54452c', '#2e2617', '#120e07'),
  felt('cuivre', 'Cuivre', 47, '#5a3a22', '#301d11', '#130a05'),
  felt('abysse', 'Abysse', 54, '#16323a', '#0a1b21', '#02080b'),
  felt('braise', 'Braise', 60, '#5e2e18', '#33170b', '#140803'),
  felt('jade', 'Jade', 67, '#1d4a45', '#0f2926', '#040f0e'),
  felt('nebuleuse', 'Nébuleuse', 75, '#3a2660', '#1c1236', '#070414'),
  felt('crepuscule', 'Crépuscule', 84, '#5a3352', '#2e1a2c', '#120810'),
  felt('obsidienne', 'Obsidienne', 93, '#262a33', '#12141a', '#030406'),
  felt('cercle', 'Cercle des cent', 99, '#4c3e18', '#231c0b', '#0a0803'),
];

export const TITLES = [
  // Wearing none is a choice, so it is an entry rather than a special case.
  title('aucun', 'Aucun', 1),
  title('chair', 'Chair à pioche', 2),
  title('douze', 'Toujours 12 cartes', 7),
  title('piochetout', 'Pioche-tout', 13),
  title('distributeur', 'Distributeur de +2', 19),
  title('empileur', 'Empileur compulsif', 25),
  title('toxique', 'Ami toxique', 31),
  title('mainlegere', 'Main légère', 37),
  title('compteur', 'Compte les cartes (mal)', 43),
  title('sanspitie', 'Sans pitié', 49),
  title('briscard', 'Vieux briscard', 53),
  title('chasseur', 'Chasseur de +4', 56),
  title('briseur', "Briseur d'amitiés", 59),
  title('stratege', 'Stratège du dimanche', 61),
  title('passecasse', 'Ça passe ou ça casse', 64),
  title('espionchef', 'Espion en chef', 69),
  title('coeurdepierre', 'Cœur de pierre', 71),
  title('tempete', 'Tempête de +4', 76),
  title('intouchable', 'Intouchable', 79),
  title('requin', 'Requin de table', 81),
  title('maitrecumul', 'Maître du cumul', 83),
  title('legende', 'Légende du salon', 87),
  title('javaisunquatre', "J'avais un +4", 89),
  title('mangeur', 'Mangeur de pioche', 91),
  title('increvable', 'Increvable', 94),
  title('cauchemar', 'Cauchemar récurrent', 96),
  title('centurion', 'Centurion', 100),
];

export const NAMES = [
  named('blanc', 'Blanc', 1, '#f3f6fb'),
  named('rouge', 'Rouge', 8, '#ff4a3d'),
  named('vert', 'Vert', 14, '#4ede6a'),
  named('bleu', 'Bleu', 20, '#3fb0ff'),
  named('jaune', 'Jaune', 26, '#ffd23f'),
  named('rose', 'Rose', 32, '#ff6fb8'),
  named('turquoise', 'Turquoise', 38, '#2fd9bd'),
  named('violet', 'Violet', 44, '#9b7bff'),
  named('or', 'Or', 50, '#ffe27a', '#ffb200'),
  named('braise', 'Braise', 65, '#ffd23f', '#ff8a1e', '#f23b2e'),
  named('glacier', 'Glacier', 72, '#d8f6ff', '#6fd4ff', '#2e6cf2'),
  named('neon', 'Néon', 77, '#b6ff3f', '#2ef2c4'),
  named('prisme', 'Prisme', 88, '#ff5da8', '#9b7bff', '#3fb0ff'),
  named('centieme', 'Centième', 97, '#fff3c4', '#ffc531', '#ff8a1e'),
];

/**
 * The six everybody starts with are the ones the rail has always had; the rest are
 * earned. Order matters: it is the order they appear in the rail, and the index is what
 * crosses the data channel.
 */
export const STICKER_ITEMS = [
  sticker('chat', 'Chat charmé', 1, '😻'),
  sticker('rire', 'Fou rire', 1, '😹'),
  sticker('caca', 'Bouse', 1, '💩'),
  sticker('pleure', 'Chat triste', 1, '😿'),
  sticker('peur', 'Chat terrifié', 1, '🙀'),
  sticker('doigt', "Doigt d'honneur", 1, '🖕'),
  sticker('feu', 'En feu', 10, '🔥'),
  sticker('sanglot', 'Sanglot', 20, '😭'),
  sticker('clown', 'Clown', 30, '🤡'),
  sticker('couronne', 'Couronne', 40, '👑'),
  sticker('trefle', 'Trèfle', 50, '🍀'),
  sticker('glacon', 'Glaçon', 60, '🥶'),
  sticker('salut', 'Salut militaire', 70, '🫡'),
  sticker('crane', 'Crâne', 80, '💀'),
  sticker('poignee', 'Poignée de main', 90, '🤝'),
];

export const ALL = [...FRAMES, ...BACKS, ...FELTS, ...TITLES, ...NAMES, ...STICKER_ITEMS];

const BY_ID = new Map(ALL.map((c) => [c.id, c]));

export const isDefault = (item) => item.level <= 1;

/** What actually gets drawn under a pseudo. "Aucun" is an entry, and it shows nothing. */
export const worn = (item) =>
  ((item.kind === 'TITLE' && isDefault(item)) ? '' : item.name);

export const ofKind = (kind) => ALL.filter((c) => c.kind === kind);

export const find = (id) => BY_ID.get(id) || null;

export const defaultOf = (kind) => ofKind(kind).find(isDefault);

/** Resolves a stored choice; anything unknown or unearned falls back. */
export function resolve(id, kind, level) {
  const wanted = BY_ID.get(id);
  return (wanted && wanted.kind === kind && wanted.level <= level) ? wanted : defaultOf(kind);
}

export const rewardsAt = (level) => ALL.filter((c) => c.level === level);

export const ownedAt = (level) => ALL.filter((c) => c.level <= level);

export const stickersAt = (level) => STICKER_ITEMS.filter((c) => c.level <= level);

/** Everything the next levels hand over, soonest first. */
export function upcoming(level, count) {
  const out = [];
  for (let l = level + 1; l <= MAX_LEVEL && out.length < count; l++) out.push(...rewardsAt(l));
  return out.slice(0, count);
}
