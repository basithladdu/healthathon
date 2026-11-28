import clipIndex from './clipIndex.json';
import type {ZoomHint} from './clips';

export type FeatureDef = {
  n: number;
  title: string;
  sub: string;
  small?: string;
  clip: string;
  side: 'L' | 'R'; // phone side
  tilt: number;
  rx: number;
  rz: number;
  scale: number;
  zoom: ZoomHint | null;
  showApp: boolean;
  clipStart: number;
  clipRate: number;
};

const clipFor = (n: number) => (clipIndex as Record<string, string>)[String(n)] ?? `f${String(n).padStart(2, '0')}-pending.mp4`;
// Callouts: rect [x,y,w,h] = fractions of the phone screen that hold ONE complete element; t = scene seconds.
type CO = {t: number; rect?: [number, number, number, number]; push?: [number, number]};
const CALL: Record<number, CO | null> = {
  1: {t: 2.0, push: [0.5, 0.6]},
  2: {t: 3.9, rect: [0.03, 0.34, 0.5, 0.11]},
  3: {t: 2.0, push: [0.5, 0.55]},
  4: null,
  5: null,
  6: {t: 2.0, rect: [0.7, 0.055, 0.26, 0.06]},
  7: {t: 5.6, push: [0.5, 0.3]},
  8: {t: 2.0, rect: [0.03, 0.175, 0.4, 0.08]},
  9: {t: 2.0, rect: [0.06, 0.243, 0.66, 0.098]},
  10: {t: 2.6, rect: [0.06, 0.62, 0.76, 0.07]},
  11: {t: 6.0, rect: [0.06, 0.665, 0.72, 0.085]},
  12: {t: 2.0, rect: [0.06, 0.46, 0.72, 0.15]},
  13: {t: 7.3, push: [0.5, 0.55]},
  14: {t: 8.6, rect: [0.11, 0.375, 0.78, 0.065]},
  15: {t: 4.6, rect: [0.06, 0.455, 0.76, 0.125]},
};
const z = (n: number): ZoomHint | null => {
  const c = CALL[n];
  if (!c) return null;
  const r = c.rect;
  return {t: c.t, x: r ? r[0] + r[2] / 2 : c.push![0], y: r ? r[1] + r[3] / 2 : c.push![1], scale: 1, dur: 2.4, rect: r};
};

const mk = (
  n: number,
  title: string,
  sub: string,
  side: 'L' | 'R',
  tilt: number,
  rx: number,
  rz: number,
  scale: number,
  zoom: ZoomHint | null,
  small?: string,
): FeatureDef => ({n, title, sub, small, clip: clipFor(n), side, tilt, rx, rz, scale, zoom, showApp: true, clipStart: 0, clipRate: 1});

export const FEATURES: Record<number, FeatureDef> = {
  1: mk(1, 'Record the talk. AI writes it down.', 'With everyone’s consent, the doctor records. AI writes out who said what, then fills the form.', 'R', 10, 3, 2, 0.97, z(1)),
  2: mk(2, 'The doctor always checks.', 'Record, then Review, then Sign. Nothing is final until the doctor has read and edited it.', 'L', 9, 2, -2, 0.97, z(2)),
  3: mk(3, 'Nothing important gets missed.', '13 topics tick off during the talk. Before finishing, Sahara lists what wasn’t discussed.', 'R', 8, 4, 1.5, 0.98, z(3)),
  4: mk(4, 'People change their minds.', 'The latest statement replaces the earlier one. The earlier one stays visible, struck through.', 'L', 11, 2, -1.5, 0.96, z(4)),
  5: mk(5, 'A signed clinical document.', 'The talk becomes the doctor’s clinical document. Doctor and patient sign, and the version locks.', 'R', 9, 3, 2.5, 0.97, z(5)),
  6: mk(6, 'In the language you speak.', 'English, Hindi, Telugu, Urdu, Kannada, even mixed in one sentence.', 'L', 10, 3, -2, 0.97, z(6), 'Translations are being reviewed by clinicians.'),
  7: mk(7, 'One scan in an emergency.', 'The QR card and latest care document are a tap away from the home screen.', 'L', 8, 2, 0, 1.0, z(7)),
  8: mk(8, 'How are you feeling today?', 'Rate symptoms, use the distress thermometer, and mark pain on a body map.', 'R', 10, 3, 2, 0.97, z(8)),
  10: mk(10, 'Medicines from a photo.', 'Scan a prescription or tablet strip. Each dose gets its own tick.', 'L', 9, 3, -2, 0.97, z(10)),
  9: mk(9, 'The whole story, symptoms first.', 'Symptom and lab history from the very first entry. Worst symptoms on top.', 'R', 9, 2, 1.5, 0.98, z(9)),
  13: mk(13, 'Every visit in one place.', 'Date, doctor and speciality. Tap for that day’s prescription.', 'R', 10, 3, -1.5, 0.97, z(13)),
  11: mk(11, 'Ask your documents.', 'Sahara sorts bills from lab reports, and answers from your own papers, with the lines it used.', 'L', 9, 2, 2, 0.97, z(11)),
  12: mk(12, 'Help nearby.', 'Palliative centres and morphine-listed hospitals on a map, with directions in Google Maps.', 'R', 11, 3, 0, 0.97, z(12)),
  14: mk(14, 'Caring takes a toll.', 'A short burden check shows the strain on the carer, then links to counsellors, Tele-MANAS 14416 and support groups.', 'L', 10, 2, -2, 0.97, z(14)),
  15: mk(15, 'A community that understands.', 'Groups, posts and replies with people who’ve been there.', 'R', 9, 3, 1.5, 0.97, z(15), 'Preview. Posts stay on the phone until the server opens.'),
};
