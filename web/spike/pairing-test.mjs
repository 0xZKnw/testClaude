// Does a serverless WebRTC pairing actually work, and does it fit in a QR code?
//
// Two real browser contexts, two real RTCPeerConnections, and nothing between them but
// the two strings a QR code would carry. If a message crosses the data channel at the
// end, the whole "no server at all" idea holds up.
//
//   node web/spike/pairing-test.mjs

import { chromium } from 'playwright';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const codec = readFileSync(join(here, '..', 'src', 'sdp-codec.js'), 'utf8')
  .replace(/^export /gm, '');

/** QR capacity in byte mode at the lowest error correction, version 40. */
const QR_BYTE_LIMIT = 2953;
/** What a phone camera reads comfortably from another phone's screen. */
const QR_COMFORTABLE = 900;

function report(label, text) {
  const bytes = new TextEncoder().encode(text).length;
  const verdict =
    bytes <= QR_COMFORTABLE ? 'confortable' :
    bytes <= QR_BYTE_LIMIT ? 'tient, mais QR dense' : 'NE TIENT PAS';
  console.log(`  ${label.padEnd(26)} ${String(bytes).padStart(5)} octets   ${verdict}`);
  return bytes;
}

const browser = await chromium.launch();

process.on('unhandledRejection', (e) => { console.error('echec :', e); process.exit(1); });

async function peer() {
  const context = await browser.newContext();
  const page = await context.newPage();
  // A secure origin is required for WebRTC; about:blank is not one.
  await page.route('**/*', (route) =>
    route.fulfill({ status: 200, contentType: 'text/html', body: '<!doctype html><title>peer</title>' }));
  await page.goto('https://peer.test/');
  await page.addScriptTag({ content: codec });
  page.on('pageerror', (e) => console.error('  erreur page :', e.message));
  return page;
}

const host = await peer();
const guest = await peer();

// ---------------------------------------------------------------- host: the offer

await host.evaluate(() => {
  window.pc = new RTCPeerConnection({ iceServers: [] });
  window.inbox = [];
  const channel = window.pc.createDataChannel('uno', { ordered: true });
  window.channel = channel;
  channel.onmessage = (e) => window.inbox.push(e.data);
  window.opened = new Promise((resolve) => { channel.onopen = resolve; });
});

/** Vanilla ICE: wait for gathering to finish so the candidates ride along in the SDP. */
const gather = async (which) => {
  const pc = window.pc;
  const description = which === 'offer' ? await pc.createOffer() : await pc.createAnswer();
  await pc.setLocalDescription(description);
  if (pc.iceGatheringState !== 'complete') {
    await new Promise((resolve) => {
      pc.onicegatheringstatechange = () => {
        if (pc.iceGatheringState === 'complete') resolve();
      };
      setTimeout(resolve, 5000);
    });
  }
  return { sdp: pc.localDescription.sdp, type: pc.localDescription.type };
};

const rawOffer = await host.evaluate(gather, 'offer');

console.log('\n=== Taille des descriptions ===\n');
console.log('Offre :');
report('SDP brut', rawOffer.sdp);
const packedOffer = await host.evaluate((d) => pack(d), rawOffer);
const offerPacked = report('compacte (champs utiles)', packedOffer);
const deflatedOffer = await host.evaluate((d) => deflate(d), rawOffer);
const offerDeflated = report('compressee (deflate)', deflatedOffer);

// ------------------------------------------------------- guest: unpack, then answer

await guest.evaluate(async (packed) => {
  window.pc = new RTCPeerConnection({ iceServers: [] });
  window.inbox = [];
  window.opened = new Promise((resolve) => {
    window.pc.ondatachannel = (e) => {
      window.channel = e.channel;
      e.channel.onmessage = (m) => window.inbox.push(m.data);
      if (e.channel.readyState === 'open') resolve();
      else e.channel.onopen = resolve;
    };
  });
  await window.pc.setRemoteDescription(unpack(packed));
}, packedOffer);

const rawAnswer = await guest.evaluate(gather, 'answer');

console.log('\nReponse :');
report('SDP brut', rawAnswer.sdp);
const packedAnswer = await guest.evaluate((d) => pack(d), rawAnswer);
const answerPacked = report('compacte (champs utiles)', packedAnswer);
const deflatedAnswer = await guest.evaluate((d) => deflate(d), rawAnswer);
const answerDeflated = report('compressee (deflate)', deflatedAnswer);

// The offer also has to carry the page URL, since the guest scans it with the phone's
// own camera app and lands on the site from that same QR.
const url = `https://uno-web.vercel.app/#${packedOffer}`;
console.log('\nQR reellement affiche par l\'hote (URL + offre) :');
report('URL complete', url);

// ------------------------------------------------------------ close the loop

await host.evaluate((packed) => window.pc.setRemoteDescription(unpack(packed)), packedAnswer);

console.log('\n=== Connexion ===\n');
const connected = await host.evaluate(async () => {
  const deadline = new Promise((resolve) => setTimeout(() => resolve('timeout'), 15000));
  const open = window.opened.then(() => 'open');
  const winner = await Promise.race([open, deadline]);
  return { winner, ice: window.pc.iceConnectionState, conn: window.pc.connectionState };
});
console.log(`  canal de donnees : ${connected.winner}`);
console.log(`  etat ICE         : ${connected.ice}`);
console.log(`  etat connexion   : ${connected.conn}`);

let exchanged = false;
if (connected.winner === 'open') {
  await guest.evaluate(() => window.opened);
  // A real board snapshot is the payload that matters, so send one of that size.
  await host.evaluate(() => window.channel.send(JSON.stringify({ t: 'state', v: 'x'.repeat(600) })));
  await guest.waitForFunction(() => window.inbox.length > 0, null, { timeout: 5000 });
  await guest.evaluate(() => window.channel.send(JSON.stringify({ t: 'play', i: 42 })));
  await host.waitForFunction(() => window.inbox.length > 0, null, { timeout: 5000 });
  exchanged = true;
  console.log('  messages         : hote -> invite OK, invite -> hote OK');
}

console.log('\n=== Verdict ===\n');
const worstPacked = Math.max(offerPacked, answerPacked, new TextEncoder().encode(url).length);
const worstDeflated = Math.max(offerDeflated, answerDeflated);
console.log(`  Appairage sans aucun serveur : ${exchanged ? 'FONCTIONNE' : 'ECHEC'}`);
console.log(`  Pire QR, methode compacte    : ${worstPacked} octets (limite ${QR_BYTE_LIMIT})`);
console.log(`  Pire QR, methode deflate     : ${worstDeflated} octets`);

await browser.close();
process.exit(exchanged && worstPacked <= QR_BYTE_LIMIT ? 0 : 1);
