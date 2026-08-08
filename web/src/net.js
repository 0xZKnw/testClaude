// The link between phones: WebRTC data channels, paired by QR code.
//
// It carries exactly the messages the Android app puts on its Bluetooth link — same
// names, same short keys — so the two are the same protocol over two different wires.
// The host is authoritative: guests send intentions and receive whole board snapshots,
// which is what keeps a slow or reconnecting phone from ever desyncing.
//
// There is no signalling server. The offer and the answer travel through a QR code, so
// each guest is paired one at a time.

import { encode, decode } from './sdp-codec.js';

/** Vanilla ICE: with nothing to trickle through, wait for the full candidate list. */
async function describe(pc, kind) {
  const description = kind === 'offer' ? await pc.createOffer() : await pc.createAnswer();
  await pc.setLocalDescription(description);
  if (pc.iceGatheringState !== 'complete') {
    await new Promise((resolve) => {
      const done = () => {
        if (pc.iceGatheringState === 'complete') {
          pc.removeEventListener('icegatheringstatechange', done);
          resolve();
        }
      };
      pc.addEventListener('icegatheringstatechange', done);
      // Some stacks never report completion when there is nothing to gather.
      setTimeout(resolve, 4000);
    });
  }
  return encode(pc.localDescription);
}

const newKey = () => Math.random().toString(36).slice(2, 10);

export class WebRtcHost {
  constructor(handlers) {
    this.handlers = handlers;
    this.peers = new Map();
    this.pending = null;
  }

  /** Opens a seat and returns the offer to show as a QR. */
  async invite() {
    this.closePending();
    const key = newKey();
    const pc = new RTCPeerConnection({ iceServers: [] });
    const channel = pc.createDataChannel('uno', { ordered: true });
    const peer = { key, pc, channel, ready: false };

    channel.onopen = () => {
      peer.ready = true;
      this.handlers.onGuestReady?.(key);
    };
    channel.onmessage = (e) => this.deliver(key, e.data);
    channel.onclose = () => this.drop(key);
    pc.onconnectionstatechange = () => {
      if (pc.connectionState === 'failed' || pc.connectionState === 'closed') this.drop(key);
    };

    this.pending = peer;
    return describe(pc, 'offer');
  }

  /** Completes the pairing with the answer scanned back from the guest. */
  async acceptAnswer(packed) {
    const peer = this.pending;
    if (!peer) throw new Error("Aucune invitation en attente");
    await peer.pc.setRemoteDescription(await decode(packed));
    this.peers.set(peer.key, peer);
    this.pending = null;
    return peer.key;
  }

  deliver(key, raw) {
    let msg;
    try {
      msg = JSON.parse(raw);
    } catch {
      return;
    }
    this.handlers.onMessage?.(key, msg);
  }

  drop(key) {
    if (!this.peers.has(key)) return;
    this.peers.delete(key);
    this.handlers.onGuestGone?.(key);
  }

  send(key, msg) {
    const peer = this.peers.get(key);
    if (peer && peer.channel.readyState === 'open') peer.channel.send(JSON.stringify(msg));
  }

  broadcast(msg) {
    const text = JSON.stringify(msg);
    for (const peer of this.peers.values()) {
      if (peer.channel.readyState === 'open') peer.channel.send(text);
    }
  }

  get guestCount() {
    return this.peers.size;
  }

  closePending() {
    if (this.pending) {
      try { this.pending.pc.close(); } catch { /* already gone */ }
      this.pending = null;
    }
  }

  stop() {
    this.closePending();
    for (const peer of this.peers.values()) {
      try { peer.pc.close(); } catch { /* already gone */ }
    }
    this.peers.clear();
  }
}

export class WebRtcGuest {
  constructor(handlers) {
    this.handlers = handlers;
    this.pc = null;
    this.channel = null;
  }

  /** Takes the host's offer and returns the answer to show back as a QR. */
  async answerTo(packedOffer) {
    this.stop();
    const pc = new RTCPeerConnection({ iceServers: [] });
    this.pc = pc;
    pc.ondatachannel = (e) => {
      this.channel = e.channel;
      e.channel.onopen = () => this.handlers.onReady?.();
      e.channel.onmessage = (m) => {
        let msg;
        try {
          msg = JSON.parse(m.data);
        } catch {
          return;
        }
        this.handlers.onMessage?.(msg);
      };
      e.channel.onclose = () => this.handlers.onClosed?.();
      if (e.channel.readyState === 'open') this.handlers.onReady?.();
    };
    pc.onconnectionstatechange = () => {
      if (pc.connectionState === 'failed') this.handlers.onClosed?.();
    };
    await pc.setRemoteDescription(await decode(packedOffer));
    return describe(pc, 'answer');
  }

  send(msg) {
    if (this.channel && this.channel.readyState === 'open') {
      this.channel.send(JSON.stringify(msg));
    }
  }

  stop() {
    if (this.pc) {
      try { this.pc.close(); } catch { /* already gone */ }
    }
    this.pc = null;
    this.channel = null;
  }
}
