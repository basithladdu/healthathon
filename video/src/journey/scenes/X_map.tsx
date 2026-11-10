/* Scene X helpers: a stylised, hand-drawn India silhouette that fills with care-centre pins. No map tiles. */
import React from 'react';
import {random} from 'remotion';
import {clamp, easeOutQuint, P, pop, ramp, spring} from '../kit';

/* rough outline, normalised 0..1 on the map height (x right, y down) */
const RAW: [number, number][] = [
  [0.224, 0.0], [0.3, 0.03], [0.338, 0.052], [0.379, 0.093], [0.372, 0.155], [0.421, 0.221], [0.49, 0.29], [0.552, 0.328], [0.693, 0.362],
  [0.697, 0.314], [0.721, 0.345], [0.828, 0.317], [0.92, 0.29], [1.0, 0.303], [0.966, 0.345], [0.938, 0.379], [0.914, 0.448], [0.872, 0.517],
  [0.848, 0.51], [0.834, 0.448], [0.81, 0.445], [0.838, 0.41], [0.755, 0.403], [0.752, 0.379], [0.707, 0.397], [0.721, 0.517], [0.655, 0.534],
  [0.638, 0.586], [0.579, 0.617], [0.493, 0.703], [0.424, 0.741], [0.421, 0.81], [0.407, 0.879], [0.39, 0.921], [0.352, 0.969], [0.328, 0.997],
  [0.293, 0.948], [0.259, 0.862], [0.224, 0.776], [0.172, 0.672], [0.166, 0.621], [0.159, 0.552], [0.145, 0.524], [0.086, 0.559], [0.034, 0.507],
  [0.069, 0.486], [0.014, 0.462], [0.028, 0.438], [0.103, 0.428], [0.069, 0.362], [0.103, 0.31], [0.19, 0.241], [0.228, 0.176], [0.207, 0.121], [0.193, 0.083],
];
const inPoly = (x: number, y: number) => {
  let c = false;
  for (let i = 0, j = RAW.length - 1; i < RAW.length; j = i++) {
    const [xi, yi] = RAW[i];
    const [xj, yj] = RAW[j];
    if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) c = !c;
  }
  return c;
};
/** smooth closed path through the points (Catmull-Rom to cubic) */
const smoothPath = (pts: [number, number][], H: number) => {
  const n = pts.length;
  const p = (i: number) => pts[(i + n) % n].map((v) => v * H) as [number, number];
  let d = `M${p(0)[0]} ${p(0)[1]}`;
  for (let i = 0; i < n; i++) {
    const [x0, y0] = p(i - 1);
    const [x1, y1] = p(i);
    const [x2, y2] = p(i + 1);
    const [x3, y3] = p(i + 2);
    const k = 0.16;
    d += ` C${x1 + (x2 - x0) * k} ${y1 + (y2 - y0) * k} ${x2 - (x3 - x1) * k} ${y2 - (y3 - y1) * k} ${x2} ${y2}`;
  }
  return d + ' Z';
};

export type Pin = {x: number; y: number; t: number; k: number};
/** deterministic pins inside the outline; Kerala and the south are denser, like the real data */
export const makePins = (n: number, t0: number, t1: number): Pin[] => {
  const out: Pin[] = [];
  let s = 0;
  while (out.length < n && s < 9000) {
    s++;
    let x = random('px' + s);
    let y = random('py' + s);
    if (random('pk' + s) < 0.12) {
      // south-west coast cluster
      x = 0.25 + random('kx' + s) * 0.12;
      y = 0.8 + random('ky' + s) * 0.17;
    }
    if (!inPoly(x, y)) continue;
    // keep away from the edge a touch
    if (!inPoly(x + 0.012, y) || !inPoly(x - 0.012, y) || !inPoly(x, y + 0.012)) continue;
    out.push({x, y, t: 0, k: out.length});
  }
  // drop order: sweep roughly south to north with jitter so it reads as "filling"
  const order = [...out].sort((a, b) => b.y + random('j' + a.k) * 0.35 - (a.y + random('j' + b.k) * 0.35));
  order.forEach((p, i) => {
    p.t = t0 + Math.pow(i / order.length, 0.85) * (t1 - t0);
  });
  return out;
};

const PIN = 'M0 0 C-7 -9 -11 -15 -11 -21 A11 11 0 1 1 11 -21 C11 -15 7 -9 0 0 Z';

export const IndiaMap: React.FC<{
  t: number;
  x: number; // top-left in world
  y: number;
  H: number;
  pins: Pin[];
  morphine: {i: number; t: number}[];
  rippleAt: number;
}> = ({t, x, y, H, pins, morphine, rippleAt}) => {
  const d = smoothPath(RAW, H);
  const rip = ramp(t, rippleAt, rippleAt + 1.1);
  const mSet = new Map(morphine.map((m) => [m.i, m.t]));
  return (
    <svg style={{position: 'absolute', left: x, top: y, overflow: 'visible'}} width={H} height={H}>
      <defs>
        <clipPath id="xindia">
          <path d={d} />
        </clipPath>
      </defs>
      {/* soft drop shadow made of a darker copy */}
      <path d={d} fill="rgba(44,90,74,0.22)" transform="translate(14 18)" />
      <path d={d} fill={P.sage} stroke={P.sage} strokeWidth={10} strokeLinejoin="round" />
      <g clipPath="url(#xindia)">
        {/* one darker shade on the east side for form */}
        <path d={`M${H * 0.55} ${-20} L${H * 1.1} ${-20} L${H * 1.1} ${H * 1.1} L${H * 0.38} ${H * 1.1} Q${H * 0.6} ${H * 0.55} ${H * 0.55} ${-20} Z`} fill={P.sageDark} opacity={0.35} />
        {/* faint state-like seams */}
        {[0.3, 0.42, 0.55, 0.68, 0.8].map((yy, i) => (
          <path key={i} d={`M0 ${H * yy} Q${H * 0.4} ${H * (yy - 0.05 + (i % 2) * 0.08)} ${H} ${H * (yy + 0.03)}`} stroke="rgba(255,248,238,0.13)" strokeWidth={3} fill="none" />
        ))}
        {[0.2, 0.36, 0.52, 0.7].map((xx, i) => (
          <path key={'v' + i} d={`M${H * xx} 0 Q${H * (xx + 0.06)} ${H * 0.5} ${H * (xx - 0.04)} ${H}`} stroke="rgba(255,248,238,0.1)" strokeWidth={3} fill="none" />
        ))}
        {/* ripple for "every state" */}
        {rip > 0 && rip < 1 && <circle cx={H * 0.42} cy={H * 0.5} r={rip * H * 0.95} fill="none" stroke={P.cream} strokeWidth={60 * (1 - rip)} opacity={0.45 * (1 - rip)} />}
      </g>
      {/* a few island dots */}
      {[[0.93, 0.79], [0.94, 0.83], [0.95, 0.87], [0.17, 0.83]].map(([a, b], i) => (
        <circle key={i} cx={a * H} cy={b * H} r={i === 3 ? 4 : 6} fill={P.sage} />
      ))}
      {/* pins */}
      {pins.map((p) => {
        const mt = mSet.get(p.k);
        if (mt !== undefined) return null;
        const a = pop(t, p.t, 18, 0.5);
        if (a <= 0.01) return null;
        const drop = (1 - easeOutQuint(ramp(t, p.t, p.t + 0.25))) * -40;
        return (
          <g key={p.k} transform={`translate(${p.x * H} ${p.y * H + drop}) scale(${0.62 * a})`}>
            <path d={PIN} fill={P.roseLight} />
            <circle cx={0} cy={-21} r={5} fill={P.rose} />
          </g>
        );
      })}
      {/* morphine pins drawn last so they sit on top */}
      {pins
        .filter((p) => mSet.has(p.k))
        .map((p) => {
          const mt = mSet.get(p.k)!;
          const a = pop(t, p.t, 18, 0.5);
          if (a <= 0.01) return null;
          const turn = spring(t, mt, 12, 0.4);
          const sc = 0.62 * a * (1 + clamp(turn, 0, 2) * 0.55);
          const lit = t >= mt;
          const halo = lit ? (t - mt) % 1.2 : 0;
          return (
            <g key={p.k} transform={`translate(${p.x * H} ${p.y * H})`}>
              {lit && <circle cx={0} cy={-14} r={10 + halo * 40} fill="none" stroke={P.marigold} strokeWidth={4} opacity={0.8 * (1 - halo / 1.2)} />}
              <g transform={`scale(${sc})`}>
                <path d={PIN} fill={lit ? P.marigold : P.roseLight} />
                <circle cx={0} cy={-21} r={5} fill={lit ? P.cream : P.rose} />
              </g>
            </g>
          );
        })}
    </svg>
  );
};
