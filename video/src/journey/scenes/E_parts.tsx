/* Shared helpers for scenes E, F and I (agent E). */
import React from 'react';
import {Cam, clamp, FONT_BODY, P, PhoneClip, ramp} from '../kit';

/* The cut clips carry a non-zero stream start_time (ffprobe). OffthreadVideo seeks by absolute pts, so
   clipFrom measured with `ffmpeg -ss` must be shifted by it. Values from ffprobe format=start_time. */
const START_OFF: Record<number, number> = {1: 0, 2: 0.333, 3: 1.166, 4: 1.6, 5: 0.033, 6: 1.833, 7: 0.033, 14: 0};

export type Seg = {clip: number; at: number; clipFrom?: number; rate?: number};

/** A phone that switches between real recordings with a short crossfade. */
export const PhoneSeq: React.FC<{t: number; segs: Seg[]; x: number; y: number; h: number; rot?: number; s?: number; note?: boolean; noteColor?: string}> = ({t, segs, x, y, h, rot = 0, s = 1, note = true, noteColor = 'rgba(56,46,41,0.5)'}) => {
  let i = 0;
  segs.forEach((g, j) => {
    if (t >= g.at) i = j;
  });
  const cur = segs[i];
  const prev = i > 0 && t < cur.at + 0.25 ? segs[i - 1] : null;
  const fade = prev ? clamp((t - cur.at) / 0.22) : 1;
  const one = (g: Seg, op: number, k: string) => (
    <div key={k} style={{position: 'absolute', left: 0, top: 0, width: 1, height: 1, opacity: op}}>
      <PhoneClip clip={g.clip} x={x} y={y} h={h} rot={rot} s={s} startAt={g.at} clipFrom={(g.clipFrom ?? 0) + (START_OFF[g.clip] ?? 0)} rate={g.rate ?? 1} />
    </div>
  );
  return (
    <>
      {prev && one(prev, 1, 'p' + (i - 1))}
      {one(cur, fade, 'c' + i)}
      {note && (
        <div style={{position: 'absolute', left: x - 300, width: 600, top: y + (h / 2) * s + 14, textAlign: 'center', fontFamily: FONT_BODY, fontSize: 20, color: noteColor, transform: `rotate(${rot}deg)`}}>
          Practice data. All names are fictional.
        </div>
      )}
    </>
  );
};

/** world point -> screen point for a World of given depth */
export const toScreen = (cam: Cam, wx: number, wy: number, depth = 1) => {
  const z = Math.pow(cam.z, depth);
  const cx = 960 + (cam.x - 960) * depth;
  const cy = 540 + (cam.y - 540) * depth;
  return [(wx - cx) * z + 960, (wy - cy) * z + 540] as [number, number];
};

/** floating dust motes, deterministic */
export const Motes: React.FC<{t: number; n?: number; color?: string; w?: number; h?: number; x0?: number; y0?: number; seed?: number}> = ({t, n = 26, color = 'rgba(255,248,238,0.5)', w = 1920, h = 1080, x0 = 0, y0 = 0, seed = 1}) => (
  <svg style={{position: 'absolute', left: x0, top: y0, width: w, height: h, overflow: 'visible', pointerEvents: 'none'}}>
    {Array.from({length: n}).map((_, i) => {
      const r1 = Math.abs(Math.sin(i * 12.9898 + seed * 78.233) * 43758.5453) % 1;
      const r2 = Math.abs(Math.sin(i * 39.3468 + seed * 11.135) * 24634.6345) % 1;
      const r3 = Math.abs(Math.sin(i * 73.156 + seed * 3.7) * 9631.17) % 1;
      const yy = (((r2 * h - t * (10 + r3 * 18)) % h) + h) % h;
      const xx = r1 * w + Math.sin(t * 0.6 + i) * 18;
      return <circle key={i} cx={xx} cy={yy} r={2 + r3 * 4} fill={color} opacity={0.4 + 0.6 * Math.abs(Math.sin(t * 0.8 + i))} />;
    })}
  </svg>
);

/** potted plant, origin at pot bottom centre */
export const Plant: React.FC<{x: number; y: number; s?: number; t: number; pot?: string; leaf?: string; leaf2?: string}> = ({x, y, s = 1, t, pot = P.coral, leaf = '#2F6B57', leaf2 = '#4E8B72'}) => {
  const sw = Math.sin(t * 1.1 + x) * 3;
  return (
    <svg style={{position: 'absolute', left: x - 160 * s, top: y - 420 * s, width: 320 * s, height: 420 * s, overflow: 'visible'}} viewBox="-160 -420 320 420">
      <g transform={`rotate(${sw} 0 -110)`}>
        {[-70, -38, -8, 22, 52, 80].map((a, i) => (
          <path key={i} d={`M0 -110 Q${a * 0.6} ${-230 - (i % 2) * 40} ${a * 1.6} ${-300 - (i % 3) * 30} Q${a * 0.3} -220 0 -110 Z`} fill={i % 2 ? leaf : leaf2} />
        ))}
        {[-1, 1].map((d) => (
          <path key={d} d={`M0 -110 Q${d * 110} -170 ${d * 140} -120 Q${d * 80} -130 0 -110 Z`} fill={leaf} />
        ))}
      </g>
      <path d="M-62 -120 L62 -120 L48 0 L-48 0 Z" fill={pot} />
      <path d="M10 -120 L62 -120 L48 0 L22 0 Z" fill="rgba(30,20,40,0.15)" />
      <rect x={-70} y={-132} width={140} height={22} rx={8} fill={pot} />
    </svg>
  );
};

/** big soft foreground leaves (for depth) */
export const FgLeaves: React.FC<{x: number; y: number; s?: number; t: number; flip?: boolean; color?: string; color2?: string}> = ({x, y, s = 1, t, flip, color = '#24503F', color2 = '#2E604D'}) => (
  <svg style={{position: 'absolute', left: x - 300 * s, top: y - 500 * s, width: 600 * s, height: 600 * s, overflow: 'visible', transform: flip ? 'scaleX(-1)' : undefined}} viewBox="-300 -500 600 600">
    {[0, 1, 2, 3].map((i) => {
      const a = -60 + i * 26 + Math.sin(t * 0.9 + i) * 3;
      return (
        <g key={i} transform={`rotate(${a} 0 100)`}>
          <path d="M0 100 Q-70 -160 0 -400 Q70 -160 0 100 Z" fill={i % 2 ? color : color2} />
          <path d="M0 100 Q-8 -160 0 -390" stroke="rgba(255,255,255,0.08)" strokeWidth={6} fill="none" />
        </g>
      );
    })}
  </svg>
);

/** simple icons, drawn at (0,0) centre, size ~ s */
export const IconCheck: React.FC<{s?: number; color?: string; p?: number}> = ({s = 40, color = P.white, p = 1}) => (
  <svg width={s} height={s} viewBox="-20 -20 40 40" style={{overflow: 'visible'}}>
    <path d="M-10 0 L-3 8 L11 -8" stroke={color} strokeWidth={5.5} fill="none" strokeLinecap="round" strokeLinejoin="round" strokeDasharray={40} strokeDashoffset={40 * (1 - clamp(p))} />
  </svg>
);

export const Pen: React.FC<{s?: number; body?: string; tip?: string}> = ({s = 1, body = P.ink, tip = P.marigold}) => (
  <svg width={140 * s} height={30 * s} viewBox="0 -15 140 30" style={{overflow: 'visible', display: 'block'}}>
    <path d="M0 0 L22 -9 L120 -9 Q134 -9 134 0 Q134 9 120 9 L22 9 Z" fill={body} />
    <path d="M0 0 L22 -9 L22 9 Z" fill={tip} />
    <rect x={88} y={-13} width={30} height={5} rx={2} fill="#9AA6B2" />
  </svg>
);

/** easing helper: 0..1 between a and b */
export const pr = (t: number, a: number, b: number) => ramp(t, a, b);
