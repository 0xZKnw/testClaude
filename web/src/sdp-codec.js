// Getting a WebRTC session description through a QR code.
//
// Pairing two browsers normally needs a signalling server to carry the offer and the
// answer. There is no server here, so those two blobs travel by QR code instead — which
// means they have to fit. A QR tops out at 2953 bytes and gets hard to scan off a phone
// screen well before that.
//
// The description is sent **whole**, never rebuilt. An earlier version shipped only the
// handful of fields that vary and reconstructed the rest from a template: three times
// smaller, and it worked between two Chromes — but any browser that words its
// description differently, or wants a line the template does not produce, rejects the
// result outright. Losslessly compressed it is around 570 bytes, and even uncompressed
// it fits, so there is nothing to buy by being clever.
//
//   D…   deflate-compressed, when the browser has CompressionStream
//   R…   raw, for anything older
//
// The marker travels with the payload, so the two ends need not agree in advance.

const FIELD = '|';
const LIST = '~';

/** Reads the one value of an `a=` line, or '' when the line is absent. */
function attr(sdp, name) {
  const line = sdp.split(/\r?\n/).find((l) => l.startsWith(`a=${name}:`));
  return line ? line.slice(name.length + 3).trim() : '';
}

/** Standard priority of a UDP host candidate on component 1; the same on every stack. */
const HOST_PRIORITY = 2113937151;

/**
 * A candidate line is mostly filler — foundation, priority, `generation 0`,
 * `network-cost 999`. Only the address and the port carry information, so that is all
 * that travels; the rest is rebuilt identically on the other side.
 *
 * Only host candidates are kept. Without STUN there are no others, and a reflexive
 * candidate would be useless anyway: it points at a public address that only a relay
 * could reach.
 */
function candidates(sdp) {
  return sdp
    .split(/\r?\n/)
    .filter((l) => l.startsWith('a=candidate:'))
    .map((l) => l.slice('a=candidate:'.length).trim().split(/\s+/))
    .filter((f) => f[2] && f[2].toLowerCase() === 'udp' && f[7] === 'host')
    .map((f) => {
      // Chrome and Safari hide the local IP behind an mDNS name. It has neither dot
      // nor colon, which is what tells it apart from a v4 or v6 address on the way back.
      const address = f[4].endsWith('.local') ? f[4].slice(0, -'.local'.length) : f[4];
      return `${address},${f[5]}`;
    });
}

function rebuildCandidate(compact, index) {
  const comma = compact.lastIndexOf(',');
  const address = compact.slice(0, comma);
  const port = compact.slice(comma + 1);
  const host = address.includes('.') || address.includes(':') ? address : `${address}.local`;
  return `candidate:${index + 1} 1 udp ${HOST_PRIORITY - index} ${host} ${port} typ host generation 0`;
}

/**
 * A session description as the few values that are not boilerplate. The fingerprint
 * keeps its hex form: it is already the bulk of the payload, and re-encoding it to
 * base64 would save ~50 bytes at the cost of another thing that can go wrong.
 */
export function pack(description) {
  const sdp = description.sdp;
  return [
    description.type === 'offer' ? 'o' : 'a',
    attr(sdp, 'ice-ufrag'),
    attr(sdp, 'ice-pwd'),
    attr(sdp, 'fingerprint').replace(/:/g, '').replace(' ', ':'),
    attr(sdp, 'setup'),
    candidates(sdp).join(LIST),
  ].join(FIELD);
}

export function unpack(packed) {
  const [kind, ufrag, pwd, fingerprint, setup, candidateList] = packed.split(FIELD);
  // The hash algorithm travels with the digest: assuming sha-256 works today on every
  // browser, and silently breaks the day one of them picks something else.
  const [algorithm, digest] = fingerprint.split(':');
  const hex = (digest || '').match(/.{2}/g) || [];
  const lines = [
    'v=0',
    // The session id is arbitrary and never compared, so it need not survive the trip.
    'o=- 1 2 IN IP4 127.0.0.1',
    's=-',
    't=0 0',
    'a=group:BUNDLE 0',
    'a=msid-semantic: WMS',
    'm=application 9 UDP/DTLS/SCTP webrtc-datachannel',
    'c=IN IP4 0.0.0.0',
    `a=ice-ufrag:${ufrag}`,
    `a=ice-pwd:${pwd}`,
    'a=ice-options:trickle',
    `a=fingerprint:${algorithm} ${hex.join(':').toUpperCase()}`,
    `a=setup:${setup}`,
    'a=mid:0',
    'a=sctp-port:5000',
    'a=max-message-size:262144',
  ];
  (candidateList || '')
    .split(LIST)
    .filter(Boolean)
    .forEach((c, i) => lines.push(`a=${rebuildCandidate(c, i)}`));
  // An SDP is CRLF-delimited and must end with one.
  return { type: kind === 'o' ? 'offer' : 'answer', sdp: lines.join('\r\n') + '\r\n' };
}

// ------------------------------------------------------------------ the wire format

const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';

function toBase64Url(bytes) {
  let out = '';
  for (let i = 0; i < bytes.length; i += 3) {
    const a = bytes[i];
    const b = bytes[i + 1];
    const c = bytes[i + 2];
    out += B64[a >> 2];
    out += B64[((a & 3) << 4) | ((b || 0) >> 4)];
    if (b === undefined) break;
    out += B64[((b & 15) << 2) | ((c || 0) >> 6)];
    if (c === undefined) break;
    out += B64[c & 63];
  }
  return out;
}

function fromBase64Url(text) {
  const bytes = [];
  let bits = 0;
  let value = 0;
  for (const ch of text) {
    const index = B64.indexOf(ch);
    if (index < 0) continue;
    value = (value << 6) | index;
    bits += 6;
    if (bits >= 8) {
      bits -= 8;
      bytes.push((value >> bits) & 0xff);
    }
  }
  return new Uint8Array(bytes);
}

async function through(stream, bytes) {
  const written = new Blob([bytes]).stream().pipeThrough(stream);
  return new Uint8Array(await new Response(written).arrayBuffer());
}

const hasCompression = typeof CompressionStream === 'function' &&
  typeof DecompressionStream === 'function';

/** The description as it will travel: whole, so nothing has to be rebuilt in front. */
export async function encode(description) {
  const body = `${description.type[0]}${description.sdp}`;
  const raw = new TextEncoder().encode(body);
  if (hasCompression) {
    try {
      return 'D' + toBase64Url(await through(new CompressionStream('deflate-raw'), raw));
    } catch {
      // Fall through: an uncompressed description still fits in a QR.
    }
  }
  return 'R' + toBase64Url(raw);
}

export async function decode(packed) {
  const text = (packed || '').trim();
  if (!text) throw new Error('code vide');
  const marker = text[0];
  const body = text.slice(1);

  if (marker === 'D' || marker === 'R') {
    const bytes = fromBase64Url(body);
    const raw = marker === 'D'
      ? await through(new DecompressionStream('deflate-raw'), bytes)
      : bytes;
    const sdp = new TextDecoder().decode(raw);
    if (!sdp.startsWith('o') && !sdp.startsWith('a')) throw new Error('contenu inattendu');
    return { type: sdp[0] === 'o' ? 'offer' : 'answer', sdp: sdp.slice(1) };
  }

  // The compact format the diagnostic page still speaks.
  if (marker === 'o' || marker === 'a') return unpack(text);
  throw new Error('format inconnu');
}
