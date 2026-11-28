import React from 'react';
import {useCurrentFrame} from 'remotion';
import {C, FONT_BODY, FONT_HEAD, MARGIN} from './theme';
import {outE, prog, inOutE, lerp} from './anim';
import {segments, CHAPTER_NAMES, TOTAL_FRAMES} from './segments';

/** Word-by-word staggered reveal. */
export const Words: React.FC<{text: string; start?: number; stagger?: number; style?: React.CSSProperties; dur?: number; dist?: number}> = ({text, start = 0, stagger = 4, style, dur = 22, dist = 30}) => {
  const f = useCurrentFrame();
  const words = text.split(' ');
  return (
    <div style={style}>
      {words.map((w, i) => {
        const t = prog(f, start + i * stagger, start + i * stagger + dur, outE);
        return (
          <span key={i} style={{display: 'inline-block', opacity: t, transform: `translateY(${(1 - t) * dist}px)`, marginRight: '0.28em', filter: `blur(${(1 - t) * 4}px)`}}>
            {w}
          </span>
        );
      })}
    </div>
  );
};

const hex = (c: string) => [1, 3, 5].map((i) => parseInt(c.slice(i, i + 2), 16));
export const mix = (a: string, b: string, t: number) => {
  const A = hex(a);
  const B = hex(b);
  return '#' + A.map((v, i) => Math.round(lerp(v, B[i], t)).toString(16).padStart(2, '0')).join('');
};

const bgOf = (s: (typeof segments)[number]) => {
  if (s.kind === 'chapter') return C.band;
  if (s.kind === 'opening') return s.id === 'O3' ? C.band : s.id === 'O2' ? '#F3EADF' : C.ground;
  if (s.kind === 'close') return s.id === 'C2' ? C.band : C.ground;
  return C.ground;
};
export const bgAt = (f: number) => {
  let c = bgOf(segments[0]);
  for (let j = 1; j < segments.length; j++) {
    const w = prog(f, segments[j].start - 8, segments[j].start + 10, inOutE);
    if (w > 0) c = mix(c, bgOf(segments[j]), w);
  }
  return c;
};

export const Background: React.FC = () => {
  const f = useCurrentFrame();
  const bg = bgAt(f);
  const dark = hex(bg).reduce((a, b) => a + b, 0) < 400;
  return (
    <div style={{position: 'absolute', inset: 0, background: bg, overflow: 'hidden'}}>
      <div style={{position: 'absolute', width: 900, height: 900, borderRadius: 450, left: -300 + Math.sin(f / 240) * 40, top: -380 + Math.cos(f / 300) * 30, background: dark ? 'rgba(255,255,255,0.035)' : 'rgba(221,235,227,0.55)', filter: 'blur(40px)'}} />
      <div style={{position: 'absolute', width: 1000, height: 1000, borderRadius: 500, right: -420 + Math.cos(f / 260) * 40, bottom: -520 + Math.sin(f / 280) * 30, background: dark ? 'rgba(255,255,255,0.03)' : 'rgba(239,231,223,0.7)', filter: 'blur(40px)'}} />
    </div>
  );
};

/** Thin progress indicator through the six chapters. */
export const ProgressTop: React.FC = () => {
  const f = useCurrentFrame();
  const chs = segments.filter((s) => s.kind === 'chapter');
  const first = chs[0].start;
  const lastSeg = segments.filter((s) => s.kind === 'feature').slice(-1)[0];
  const end = lastSeg.start + lastSeg.frames;
  const vis = prog(f, first + 10, first + 28, outE) * (1 - prog(f, end - 18, end - 2, outE));
  if (vis <= 0) return null;
  const bg = bgAt(f);
  const dark = hex(bg).reduce((a, b) => a + b, 0) < 400;
  const ends = chs.map((c, i) => (i < chs.length - 1 ? chs[i + 1].start : end));
  const cur = Math.max(0, chs.filter((c) => f >= c.start).length - 1);
  const track = dark ? 'rgba(255,255,255,0.25)' : C.line;
  const fillC = dark ? '#DCEBE3' : C.primary;
  const txt = dark ? '#DCEBE3' : C.inkFaint;
  const gap = 14;
  const w = (1920 - MARGIN * 2 - gap * 5) / 6;
  return (
    <div style={{position: 'absolute', left: MARGIN, top: 56, width: 1920 - MARGIN * 2, opacity: vis, fontFamily: FONT_BODY}}>
      <div style={{display: 'flex', gap}}>
        {chs.map((c, i) => {
          const t = Math.min(1, Math.max(0, (f - c.start) / (ends[i] - c.start)));
          return (
            <div key={i} style={{width: w, height: 5, borderRadius: 3, background: track, overflow: 'hidden'}}>
              <div style={{width: `${t * 100}%`, height: '100%', background: fillC}} />
            </div>
          );
        })}
      </div>
      <div style={{marginTop: 12, fontSize: 28, fontWeight: 600, color: txt, letterSpacing: 0.2}}>
        {cur + 1} of 6 &nbsp;·&nbsp; {CHAPTER_NAMES[cur]}
      </div>
    </div>
  );
};

export const SERIF = FONT_HEAD;
export {TOTAL_FRAMES};
