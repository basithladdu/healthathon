import tl from './timeline.json';

export type Seg =
  | {kind: 'opening'; id: string; start: number; frames: number; ticks: number[]}
  | {kind: 'chapter'; n: number; name: string; start: number; frames: number; ticks: number[]}
  | {kind: 'feature'; n: number; chapter: number; idx: number; start: number; frames: number; ticks: number[]}
  | {kind: 'close'; id: string; start: number; frames: number; ticks: number[]};

export const segments: Seg[] = [];
let cur = 0;
const push = (s: any) => {
  segments.push({...s, start: cur});
  cur += s.frames;
};
tl.opening.forEach((o) => push({kind: 'opening', ...o}));
let idx = 0;
tl.chapters.forEach((c) => {
  push({kind: 'chapter', n: c.n, name: c.name, frames: tl.chapterFrames, ticks: [6, 20]});
  c.features.forEach((fn) => {
    const f = (tl.features as any)[String(fn)];
    push({kind: 'feature', n: fn, chapter: c.n, idx: idx++, frames: f.frames, ticks: f.ticks});
  });
});
tl.close.forEach((o) => push({kind: 'close', ...o}));
export const TOTAL_FRAMES = cur;
export const CHAPTER_NAMES = tl.chapters.map((c) => c.name);
