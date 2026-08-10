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
  ({ id: `fr.${id}`, kind: 'FRAME', name, level, a, b, c, style, motion: 'NONE', pattern: 'PLAIN', text: '' });
const back = (id, name, level, top, bottom, oval, pattern = 'PLAIN', motion = 'NONE') =>
  ({ id: `bk.${id}`, kind: 'BACK', name, level, a: top, b: bottom, c: oval, style: 'SOLID', motion, pattern, text: '' });
const felt = (id, name, level, light, mid, dark, pattern = 'PLAIN', motion = 'NONE') =>
  ({ id: `ft.${id}`, kind: 'FELT', name, level, a: light, b: mid, c: dark, style: 'SOLID', motion, pattern, text: '' });
const title = (id, name, level) =>
  ({ id: `ti.${id}`, kind: 'TITLE', name, level, a: '', b: '', c: '', style: 'SOLID', motion: 'NONE', pattern: 'PLAIN', text: '' });
const named = (id, name, level, a, b = '', c = '', motion = 'NONE') =>
  ({ id: `nm.${id}`, kind: 'NAME', name, level, a, b, c, style: 'SOLID', motion, pattern: 'PLAIN', text: '' });
const sticker = (id, name, level, text) =>
  ({ id: `st.${id}`, kind: 'STICKER', name, level, a: '', b: '', c: '', style: 'SOLID', motion: 'NONE', pattern: 'PLAIN', text });

export const FRAMES = [
  frame('encre', 'Encre', 1, 'SOLID', '#3b475d'),
  frame('braise', 'Braise', 2, 'SOLID', '#f23b2e'),
  frame('menthe', 'Menthe', 4, 'SOLID', '#41c258'),
  frame('azur', 'Azur', 6, 'SOLID', '#2e9cf2'),
  frame('safran', 'Safran', 8, 'SOLID', '#ffc21a'),
  frame('amethyste', 'Améthyste', 10, 'SOLID', '#7a5cf0'),
  frame('couchant', 'Couchant', 12, 'DUO', '#ff8a1e', '#f23b2e'),
  frame('lagon', 'Lagon', 14, 'DUO', '#19b79b', '#2e9cf2'),
  frame('pointilles', 'Pointillés', 16, 'DASH', '#f3f6fb'),
  frame('barbapapa', 'Barbe à papa', 18, 'DUO', '#f25da8', '#7a5cf0'),
  frame('perles', 'Perles', 20, 'BEADS', '#2ef2c4'),
  frame('cuivre', 'Cuivre', 22, 'DUAL', '#c87137', '#f0a862'),
  frame('argent', 'Argent', 24, 'SHINE', '#a8b4c6', '#fdfbf4'),
  frame('feuillage', 'Feuillage', 26, 'DUO', '#41c258', '#19b79b'),
  frame('orage', 'Orage', 28, 'NOTCH', '#2e9cf2', '#3b475d'),
  frame('flammeches', 'Flammèches', 30, 'FLAME', '#ff8a1e', '#ffd23f'),
  frame('lave', 'Lave', 33, 'GLOW', '#ff5a1e'),
  frame('etincelle', 'Étincelle', 36, 'BOLT', '#ffe27a', '#fdfbf4'),
  frame('givre', 'Givre', 39, 'GLOW', '#9fe8ff'),
  frame('ressac', 'Ressac', 42, 'WAVE', '#2e9cf2', '#9fe8ff'),
  frame('bitume', 'Bitume', 45, 'NOTCH', '#9daabf', '#4a5568'),
  frame('satellite', 'Satellite', 48, 'ORBIT', '#2ef2c4', '#1d3a4a'),
  frame('or', 'Or', 51, 'SHINE', '#ffc531', '#fff3c4'),
  frame('sangdencre', "Sang d'encre", 54, 'DUAL', '#c01c12', '#f23b2e'),
  frame('constellation', 'Constellation', 57, 'STARS', '#fdfbf4', '#2e4b8c'),
  frame('prisme', 'Prisme', 60, 'SPIN', '#f23b2e', '#ffc21a', '#2e9cf2'),
  frame('brasier', 'Brasier', 63, 'FLAME', '#f23b2e', '#ffc531'),
  frame('aurore', 'Aurore', 66, 'SPIN', '#2ef2c4', '#7a5cf0', '#2e9cf2'),
  frame('rubis', 'Rubis', 69, 'GLOW', '#ff2d55'),
  frame('foudre', 'Foudre', 72, 'BOLT', '#b98bff', '#fdfbf4'),
  frame('emeraude', 'Émeraude', 75, 'GLOW', '#2ee06a'),
  frame('maree', 'Marée', 78, 'WAVE', '#19b79b', '#d8f6ff'),
  frame('saphir', 'Saphir', 81, 'GLOW', '#3d7dff'),
  frame('onyx', 'Onyx', 84, 'DUAL', '#2b3242', '#6b7688'),
  frame('anneau', "Anneau d'or", 87, 'ORBIT', '#ffc531', '#4a3a12'),
  frame('cendre', 'Cendre ardente', 90, 'SPIN', '#ff8a1e', '#c01c12', '#2b1a12'),
  frame('lactee', 'Voie lactée', 93, 'STARS', '#fdfbf4', '#4a2e8c'),
  frame('platine', 'Platine', 96, 'SHINE', '#d8e0ec', '#fdfbf4'),
  frame('enfer', 'Enfer', 98, 'FLAME', '#fff3c4', '#ff5a1e'),
  frame('couronne', 'Couronne', 100, 'CROWN', '#ffc531', '#fff3c4', '#dc9200'),
];

export const BACKS = [
  back('classique', 'Classique', 1, '#2b3242', '#161a24', '#f23b2e'),
  back('brique', 'Brique', 3, '#5a1f1a', '#2a0e0b', '#ff8a1e'),
  back('foret', 'Forêt', 5, '#1e4030', '#0c1d16', '#41c258', 'STRIPES'),
  back('ocean', 'Océan', 7, '#16344f', '#081826', '#2e9cf2', 'WAVES'),
  back('dore', 'Doré', 9, '#4a3a12', '#221a07', '#ffc531', 'RAYS'),
  back('violine', 'Violine', 11, '#382357', '#190f28', '#7a5cf0', 'DOTS'),
  back('reglisse', 'Réglisse', 13, '#1a1a1e', '#07070a', '#f3f6fb', 'STRIPES'),
  back('sable', 'Sable', 15, '#5c4b2e', '#2a2113', '#ffc21a', 'HEX'),
  back('confettis', 'Confettis', 17, '#2a2440', '#14101f', '#f25da8', 'CONFETTI'),
  back('menthe', 'Menthe glaciale', 19, '#17423c', '#091e1b', '#2ef2c4', 'WAVES', 'SHEEN'),
  back('cerise', 'Cerise noire', 21, '#3e0e1e', '#1b040c', '#f25da8', 'DOTS', 'PULSE'),
  back('cuivre', 'Cuivre chaud', 23, '#52341a', '#24160a', '#c87137', 'RAYS', 'SHEEN'),
  back('nuit', 'Bleu de nuit', 25, '#17203d', '#070b1a', '#6e8cff', 'DOTS', 'DRIFT'),
  back('brasier', 'Brasier', 29, '#4a1a0c', '#200806', '#ff8a1e', 'FLAMES', 'PULSE'),
  back('poudre', 'Rose poudré', 32, '#54293d', '#25101b', '#f25da8', 'HEX', 'PULSE'),
  back('tonnerre', 'Tonnerre', 35, '#1e2136', '#0a0c18', '#ffe27a', 'BOLTS', 'SHEEN'),
  back('vertdegris', 'Vert-de-gris', 38, '#2a423b', '#121d1a', '#19b79b', 'STRIPES', 'DRIFT'),
  back('abysses', 'Abysses', 41, '#0e2a3a', '#04121c', '#3d7dff', 'WAVES', 'DRIFT'),
  back('pourpre', 'Pourpre royal', 47, '#421338', '#1d0718', '#ffc531', 'CROWNS', 'SHEEN'),
  back('orblanc', 'Or blanc', 53, '#3b3f49', '#171a20', '#d8e0ec', 'RAYS', 'SHEEN'),
  back('retro', 'Néon rétro', 59, '#201242', '#0b0620', '#2ef2c4', 'BOLTS', 'DRIFT'),
  back('fournaise', 'Fournaise', 65, '#3a0b06', '#190403', '#ff5a1e', 'FLAMES', 'SHEEN'),
  back('carnaval', 'Carnaval', 71, '#2e1240', '#13061c', '#ffc531', 'CONFETTI', 'PULSE'),
  back('centfaces', 'Cent faces', 83, '#4a3a12', '#14161d', '#ffc531', 'CROWNS', 'SHEEN'),
];

export const FELTS = [
  felt('nuit', 'Table de nuit', 1, '#2a3444', '#171d27', '#080a10'),
  felt('feutre', 'Feutre vert', 27, '#27503a', '#14301f', '#06130c', 'STRIPES'),
  felt('bordeaux', 'Bordeaux', 31, '#54202a', '#2e1017', '#120508', 'RAYS'),
  felt('encre', 'Encre bleue', 34, '#22375c', '#111d35', '#050a14', 'HEX'),
  felt('cendre', 'Cendre', 37, '#3d4148', '#212429', '#0b0c0e', 'DOTS'),
  felt('prune', 'Prune', 40, '#3e2b57', '#221733', '#0c0714', 'CONFETTI'),
  felt('profonde', 'Forêt profonde', 43, '#1e4231', '#0f2519', '#040d08', 'STRIPES'),
  felt('sable', 'Sable chaud', 46, '#54452c', '#2e2617', '#120e07', 'RAYS'),
  felt('cuivre', 'Cuivre', 49, '#5a3a22', '#301d11', '#130a05', 'HEX', 'SHEEN'),
  felt('abysse', 'Abysse', 52, '#16323a', '#0a1b21', '#02080b', 'WAVES', 'DRIFT'),
  felt('braise', 'Braise', 55, '#5e2e18', '#33170b', '#140803', 'FLAMES', 'PULSE'),
  felt('orageuse', 'Table orageuse', 58, '#243050', '#121a2e', '#05080f', 'BOLTS', 'SHEEN'),
  felt('jade', 'Jade', 61, '#1d4a45', '#0f2926', '#040f0e', 'WAVES', 'SHEEN'),
  felt('volcan', 'Volcan', 64, '#5a1f10', '#2c0d07', '#100301', 'FLAMES', 'DRIFT'),
  felt('nebuleuse', 'Nébuleuse', 67, '#3a2660', '#1c1236', '#070414', 'DOTS', 'DRIFT'),
  felt('recif', 'Récif', 70, '#13424a', '#092329', '#030d10', 'WAVES', 'PULSE'),
  felt('crepuscule', 'Crépuscule', 73, '#5a3352', '#2e1a2c', '#120810', 'RAYS', 'PULSE'),
  felt('foudroyee', 'Table foudroyée', 76, '#2a2440', '#141020', '#06040c', 'BOLTS', 'DRIFT'),
  felt('obsidienne', 'Obsidienne', 79, '#262a33', '#12141a', '#030406', 'HEX', 'SHEEN'),
  felt('fete', 'Table de fête', 82, '#3a2050', '#1c0f28', '#08040e', 'CONFETTI', 'PULSE'),
  felt('couronnee', 'Table couronnée', 85, '#4a3a12', '#241c09', '#0a0803', 'CROWNS', 'SHEEN'),
  felt('cercle', 'Cercle des cent', 99, '#5a4614', '#2a2109', '#0c0902', 'CROWNS', 'DRIFT'),
];

export const TITLES = [
  // Wearing none is a choice, so it is an entry rather than a special case.
  title('aucun', 'Aucun', 1),
  title('chair', 'Chair à pioche', 2),
  title('douze', 'Toujours 12 cartes', 5),
  title('piochetout', 'Pioche-tout', 9),
  title('distributeur', 'Distributeur de +2', 13),
  title('empileur', 'Empileur compulsif', 17),
  title('toxique', 'Ami toxique', 21),
  title('mainlegere', 'Main légère', 25),
  title('compteur', 'Compte les cartes (mal)', 29),
  title('balance', 'Balance ton +4', 33),
  title('sanspitie', 'Sans pitié', 37),
  title('briscard', 'Vieux briscard', 41),
  title('chasseur', 'Chasseur de +4', 44),
  title('briseur', "Briseur d'amitiés", 50),
  title('stratege', 'Stratège du dimanche', 56),
  title('passecasse', 'Ça passe ou ça casse', 62),
  title('espionchef', 'Espion en chef', 68),
  title('coeurdepierre', 'Cœur de pierre', 74),
  title('tempete', 'Tempête de +4', 77),
  title('intouchable', 'Intouchable', 80),
  title('requin', 'Requin de table', 86),
  title('maitrecumul', 'Maître du cumul', 88),
  title('javaisunquatre', "J'avais un +4", 89),
  title('mangeur', 'Mangeur de pioche', 91),
  title('karma', 'Karma en attente', 92),
  title('increvable', 'Increvable', 94),
  title('legende', 'Légende du salon', 95),
  title('cauchemar', 'Cauchemar récurrent', 97),
  title('dieudutapis', 'Dieu du tapis', 99),
  title('centurion', 'Centurion', 100),
];

export const NAMES = [
  named('blanc', 'Blanc', 1, '#f3f6fb'),
  named('rouge', 'Rouge', 6, '#ff4a3d'),
  named('vert', 'Vert', 11, '#4ede6a'),
  named('bleu', 'Bleu', 16, '#3fb0ff'),
  named('jaune', 'Jaune', 26, '#ffd23f'),
  named('rose', 'Rose', 31, '#ff6fb8'),
  named('turquoise', 'Turquoise', 43, '#2fd9bd'),
  named('violet', 'Violet', 50, '#9b7bff'),
  named('or', 'Or', 56, '#ffe27a', '#ffb200', '', 'SHEEN'),
  named('braise', 'Braise', 62, '#ffd23f', '#ff8a1e', '#f23b2e', 'SHEEN'),
  named('glacier', 'Glacier', 68, '#d8f6ff', '#6fd4ff', '#2e6cf2', 'SHEEN'),
  named('neon', 'Néon', 74, '#b6ff3f', '#2ef2c4', '', 'SHEEN'),
  named('foudre', 'Foudre', 80, '#fdfbf4', '#b98bff', '#6e8cff', 'SHEEN'),
  named('prisme', 'Prisme', 86, '#ff5da8', '#9b7bff', '#3fb0ff', 'SHEEN'),
  named('magma', 'Magma', 92, '#fff3c4', '#ff5a1e', '#c01c12', 'SHEEN'),
  named('abysse', 'Abysse', 95, '#2fd9bd', '#2e6cf2', '#1b1046', 'SHEEN'),
  named('centieme', 'Centième', 97, '#fff3c4', '#ffc531', '#ff8a1e', 'SHEEN'),
  named('couronne', 'Couronné', 100, '#fdfbf4', '#ffc531', '#dc9200', 'SHEEN'),
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
  sticker('sanglot', 'Sanglot', 15, '😭'),
  sticker('clown', 'Clown', 20, '🤡'),
  sticker('eclair', 'Éclair', 28, '⚡'),
  sticker('couronne', 'Couronne', 34, '👑'),
  sticker('trefle', 'Trèfle', 40, '🍀'),
  sticker('glacon', 'Glaçon', 46, '🥶'),
  sticker('vague', 'Vague', 52, '🌊'),
  sticker('salut', 'Salut militaire', 58, '🫡'),
  sticker('crane', 'Crâne', 64, '💀'),
  sticker('bombe', 'Bombe', 70, '💣'),
  sticker('poignee', 'Poignée de main', 76, '🤝'),
  sticker('gobe', 'Gobe-mouches', 88, '😐'),
  sticker('trophee', 'Trophée', 90, '🏆'),
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
