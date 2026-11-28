import React from 'react';
import {AbsoluteFill, Freeze, Sequence, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, FONT_BODY, FONT_HEAD} from './theme';
import {ClipVideo, useClipInfo, ZoomHint} from './clips';
import {inOutE, outE, prog, lerp} from './anim';
import {segments} from './segments';
import {FEATURES, FeatureDef} from './features';

export const PW = 410; // phone outer width
export const PH = 880;
export const BEZEL = 14;
export const SW = PW - BEZEL * 2; // 382
export const SH = PH - BEZEL * 2; // 852

type Pose = {x: number; y: number; s: number; ry: number; rx: number; rz: number; o: number};
const HIDDEN: Pose = {x: 960, y: 1500, s: 0.9, ry: 0, rx: 0, rz: 0, o: 0};
const REST: Pose = {x: 960, y: 1030, s: 0.68, ry: 0, rx: 14, rz: 0, o: 1};

const featPose = (d: FeatureDef): Pose => ({
  x: d.side === 'R' ? 1440 : 480,
  y: 578,
  s: d.scale * 0.96,
  ry: d.side === 'R' ? -d.tilt : d.tilt,
  rx: d.rx,
  rz: d.rz,
  o: 1,
});

type KF = {f: number; p: Pose};
const kfs: KF[] = [];
segments.forEach((sg) => {
  let p: Pose = HIDDEN;
  if (sg.kind === 'chapter') p = REST;
  if (sg.kind === 'feature') p = featPose(FEATURES[sg.n]);
  const m = sg.kind === 'feature' ? 14 : 12;
  kfs.push({f: sg.start + m, p}, {f: sg.start + sg.frames - m, p});
});

const poseAt = (f: number): Pose => {
  if (f <= kfs[0].f) return kfs[0].p;
  for (let i = 0; i < kfs.length - 1; i++) {
    const A = kfs[i];
    const B = kfs[i + 1];
    if (f >= A.f && f <= B.f) {
      if (A.p === B.p) return A.p;
      const t = inOutE((f - A.f) / (B.f - A.f));
      const out: any = {};
      (Object.keys(A.p) as (keyof Pose)[]).forEach((key) => (out[key] = lerp(A.p[key], B.p[key], t)));
      return out;
    }
  }
  return kfs[kfs.length - 1].p;
};

/** The designed stand-in when a clip file is missing. */
const Placeholder: React.FC<{def: FeatureDef}> = ({def}) => (
  <div style={{position: 'absolute', inset: 0, background: C.ground, fontFamily: FONT_BODY}}>
    <div style={{height: 120, background: C.band, color: '#fff', padding: '60px 26px 0', fontFamily: FONT_HEAD, fontSize: 34}}>Sahara</div>
    <div style={{padding: '30px 26px', color: C.ink, fontFamily: FONT_HEAD, fontSize: 36, lineHeight: 1.2}}>{def.title}</div>
    {[0, 1, 2, 3].map((i) => (
      <div key={i} style={{margin: '0 26px 20px', height: 96, borderRadius: 22, background: i % 2 ? C.primaryWash : C.surface, border: `2px solid ${C.line}`}}>
        <div style={{margin: '26px 22px 0', width: `${70 - i * 12}%`, height: 14, borderRadius: 7, background: C.edge, opacity: 0.35}} />
      </div>
    ))}
    <div style={{position: 'absolute', bottom: 30, left: 0, right: 0, textAlign: 'center', fontSize: 28, color: C.inkFaint}}>Clip pending</div>
  </div>
);

const PRE = 80;
const ScreenContent: React.FC<{def: FeatureDef; start: number}> = ({def, start}) => {
  const info = useClipInfo(def.clip);
  const f = useCurrentFrame();
  if (!info.exists) return <Placeholder def={def} />;
  return (
    <Sequence from={start - PRE} layout="none">
      <Freeze frame={Math.max(PRE, f - (start - PRE))}>
        <Sequence from={PRE} layout="none">
          <ClipVideo file={def.clip} n={def.n} />
        </Sequence>
      </Freeze>
    </Sequence>
  );
};

/** The recurring device: one phone that glides between scenes. */
export const PhoneLayer: React.FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();
  const p = poseAt(f);
  const near = segments.filter((s) => s.kind === 'feature' && f >= s.start - 80 && f < s.start + s.frames + 10) as any[];
  const cur = near.find((s) => f >= s.start && f < s.start + s.frames) ?? near[0];
  const afterChapter = (s: any) => segments[segments.indexOf(s) - 1]?.kind === 'chapter';
  const def: FeatureDef | null = cur ? FEATURES[cur.n] : null;
  const zoom: ZoomHint | null = def ? def.zoom : null;
  const drift = Math.sin(f / 70) * 1.6;
  const breathe = Math.sin(f / 90) * 0.006;
  let push = 0;
  let lensT = 0;
  if (cur && zoom) {
    const lf = f - cur.start;
    const a = zoom.t * fps;
    const b = a + zoom.dur * fps;
    push = prog(lf, a - 20, a + 14, inOutE) * (1 - prog(lf, b - 6, b + 20, inOutE));
    lensT = prog(lf, a, a + 14, outE) * (1 - prog(lf, b - 8, b + 8, inOutE));
  }
  const pushAmt = zoom && !zoom.rect ? 0.13 : 0.05;
  const sc = p.s * (1 + push * pushAmt + breathe);
  const fx = zoom ? zoom.x : 0.5;
  const fy = zoom ? zoom.y : 0.5;
  const tx = -(fx - 0.5) * SW * 0.16 * push;
  const ty = -(fy - 0.5) * SH * 0.12 * push;
  const side = def ? def.side : 'R';
  return (
    <AbsoluteFill style={{perspective: 2400, perspectiveOrigin: '50% 45%', pointerEvents: 'none'}}>
      <div
        style={{
          position: 'absolute',
          left: p.x - PW / 2,
          top: p.y - PH / 2,
          width: PW,
          height: PH,
          opacity: p.o,
          transform: `scale(${sc}) rotateX(${p.rx}deg) rotateY(${p.ry + drift}deg) rotateZ(${p.rz}deg) translate(${tx}px, ${ty}px)`,
          transformStyle: 'preserve-3d',
        }}
      >
        <div style={{position: 'absolute', left: 30, right: 30, bottom: -26, height: 60, borderRadius: '50%', background: 'rgba(56,46,41,0.16)', filter: 'blur(26px)'}} />
        <div style={{position: 'absolute', inset: 0, borderRadius: 66, background: 'linear-gradient(145deg,#4a4540,#25201d)', boxShadow: '0 40px 80px rgba(56,46,41,0.22), inset 0 0 0 2px #6b645d'}} />
        <div style={{position: 'absolute', left: -4, top: 200, width: 5, height: 80, borderRadius: 3, background: '#4a4540'}} />
        <div style={{position: 'absolute', right: -4, top: 260, width: 5, height: 120, borderRadius: 3, background: '#4a4540'}} />
        <div style={{position: 'absolute', left: BEZEL, top: BEZEL, width: SW, height: SH, borderRadius: 52, overflow: 'hidden', background: C.ground}}>
          {near.map((s) => {
            const lf = f - s.start;
            const op = (afterChapter(s) ? prog(lf, -64, -50, inOutE) : prog(lf, -3, 8, inOutE)) * (1 - prog(lf, s.frames - 16, s.frames - 5, inOutE));
            return (
              <div key={s.n} style={{position: 'absolute', inset: 0, opacity: op}}>
                <ScreenContent def={FEATURES[s.n]} start={s.start} />
              </div>
            );
          })}
          <div style={{position: 'absolute', inset: 0, background: 'linear-gradient(115deg, rgba(255,255,255,0.10) 0%, rgba(255,255,255,0) 32%)'}} />
          {zoom && zoom.rect && lensT > 0 && (
            <div
              style={{
                position: 'absolute',
                left: zoom.rect[0] * SW - 8,
                top: zoom.rect[1] * SH - 8,
                width: zoom.rect[2] * SW + 16,
                height: zoom.rect[3] * SH + 16,
                borderRadius: 18,
                border: `4px solid ${C.primary}`,
                background: 'rgba(59,117,98,0.08)',
                opacity: lensT,
              }}
            />
          )}
        </div>
        <div style={{position: 'absolute', left: PW / 2 - 46, top: BEZEL + 14, width: 92, height: 26, borderRadius: 13, background: '#171412'}} />
        {def && zoom && zoom.rect && lensT > 0 && cur && <Callout def={def} zoom={zoom} t={lensT} side={side} start={cur.start} />}
      </div>
    </AbsoluteFill>
  );
};

/** Rounded callout card showing ONE complete element, magnified, beside the phone. */
const Callout: React.FC<{def: FeatureDef; zoom: ZoomHint; t: number; side: 'L' | 'R'; start: number}> = ({def, zoom, t, side, start}) => {
  const [rx, ry, rw, rh] = zoom.rect!;
  const pad = 6;
  let cw = Math.min(350, Math.max(220, Math.round(rw * SW * 1.8)));
  let s = cw / (rw * SW);
  if (rh * SH * s > 420) {
    s = 420 / (rh * SH);
    cw = Math.round(rw * SW * s);
  }
  const ch = Math.round(rh * SH * s);
  const left = side === 'R' ? 36 - cw - pad * 2 : PW - 36;
  const cy = Math.min(PH - ch / 2 - 60, Math.max(ch / 2 + 60, BEZEL + (ry + rh / 2) * SH));
  const top = cy - ch / 2 - pad;
  const rectEdgeX = BEZEL + (side === 'R' ? rx * SW - 8 : (rx + rw) * SW + 8);
  const rectY = BEZEL + (ry + rh / 2) * SH;
  const cardEdgeX = side === 'R' ? left + cw + pad * 2 : left;
  return (
    <>
      <svg style={{position: 'absolute', left: -500, top: 0, width: PW + 1000, height: PH, overflow: 'visible', opacity: t}} viewBox={`-500 0 ${PW + 1000} ${PH}`}>
        <path d={`M${cardEdgeX} ${cy} L${rectEdgeX} ${rectY}`} stroke={C.primary} strokeWidth={3} strokeDasharray="2 8" strokeLinecap="round" fill="none" />
      </svg>
      <div
        style={{
          position: 'absolute',
          left,
          top,
          width: cw + pad * 2,
          height: ch + pad * 2,
          borderRadius: 28,
          overflow: 'hidden',
          background: C.surface,
          padding: pad,
          boxShadow: `0 0 0 3px ${C.primary}, 0 24px 60px rgba(56,46,41,0.28)`,
          opacity: t,
          transform: `translateY(${(1 - t) * 14}px) scale(${lerp(0.94, 1, t)})`,
        }}
      >
        <div style={{position: 'relative', width: cw, height: ch, borderRadius: 22, overflow: 'hidden'}}>
          <div style={{position: 'absolute', width: SW, height: SH, left: -rx * SW * s, top: -ry * SH * s, transformOrigin: '0 0', transform: `scale(${s})`}}>
            <ScreenContent def={def} start={start} />
          </div>
        </div>
      </div>
    </>
  );
};
