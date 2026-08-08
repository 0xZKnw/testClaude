// What is actually inside the description, and what happens when a real phone offers
// far more candidates than a container ever will.
import { chromium } from 'playwright';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const codec = readFileSync(join(here, '..', 'src', 'sdp-codec.js'), 'utf8').replace(/^export /gm, '');

const browser = await chromium.launch();
const page = await browser.newPage();
await page.route('**/*', (r) => r.fulfill({ status: 200, contentType: 'text/html', body: '<!doctype html><title>x' }));
await page.goto('https://peer.test/');
await page.addScriptTag({ content: codec });

const sdp = await page.evaluate(async () => {
  const pc = new RTCPeerConnection({ iceServers: [] });
  pc.createDataChannel('uno');
  await pc.setLocalDescription(await pc.createOffer());
  if (pc.iceGatheringState !== 'complete') {
    await new Promise((r) => {
      pc.onicegatheringstatechange = () => pc.iceGatheringState === 'complete' && r();
      setTimeout(r, 5000);
    });
  }
  return pc.localDescription.sdp;
});

console.log('=== SDP brut genere ici ===');
console.log(sdp.split(/\r?\n/).filter(Boolean).map((l) => '  ' + l).join('\n'));

const cands = sdp.split(/\r?\n/).filter((l) => l.startsWith('a=candidate:'));
console.log(`\n  candidats : ${cands.length}`);
console.log(`  mDNS (.local) : ${cands.some((c) => c.includes('.local')) ? 'OUI' : 'non'}`);

// A phone offers WiFi v4, WiFi v6, and often a link-local and a cellular address too.
// Measure the packed size against a deliberately pessimistic candidate list.
const worst = await page.evaluate((n) => {
  const fake = [];
  for (let i = 0; i < n; i++) {
    fake.push(`a=candidate:${1000000000 + i} 1 udp ${2122260000 - i} ` +
      `f47ac10b-58cc-4372-a567-0e02b2c3d4${String(i).padStart(2, '0')}.local ${50000 + i} typ host generation 0 ufrag AbCd network-cost 999`);
  }
  const sdp = [
    'v=0', 'o=- 1 2 IN IP4 127.0.0.1', 's=-', 't=0 0', 'a=group:BUNDLE 0',
    'm=application 9 UDP/DTLS/SCTP webrtc-datachannel', 'c=IN IP4 0.0.0.0',
    'a=ice-ufrag:AbCd', 'a=ice-pwd:0123456789abcdef01234567',
    'a=fingerprint:sha-256 ' + Array.from({ length: 32 }, () => 'AB').join(':'),
    'a=setup:actpass', 'a=mid:0', 'a=sctp-port:5000', 'a=max-message-size:262144',
    ...fake,
  ].join('\r\n') + '\r\n';
  return { raw: sdp.length, packed: pack({ type: 'offer', sdp }).length };
}, 8);

console.log(`\n=== Pire cas simule : 8 candidats mDNS ===`);
console.log(`  SDP brut   : ${worst.raw} octets`);
console.log(`  compacte   : ${worst.packed} octets`);
console.log(`  + URL      : ${worst.packed + 32} octets`);

await browser.close();
