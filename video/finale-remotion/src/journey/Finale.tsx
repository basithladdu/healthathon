/* Grand Finale cut (v4): the film is assembled from a running order, finale_seq.json, in 30 fps frames:
   {src: [a, b]}            play source film frames a..b of the original Journey film
   {hold: f, durF}          hold source frame f (stillness for the presenter)
   {scene, durF}            a drawn scene from FinaleScenes / V2-V5
   {section, n, durF}       a section slide that wipes over the previous shot and off onto the next
   journey/mix_finale.py builds public/journey/mix.wav from the same list, so sound stays in sync. */
import React from 'react';
import {AbsoluteFill, Audio, Freeze, staticFile, useCurrentFrame} from 'remotion';
import {FPS, Finish, TOTAL_FRAMES} from './kit';
import {Journey} from './Journey';
import seq from './finale_seq.json';
import * as S from './FinaleScenes';
import * as V from './FinaleV2';
import * as V3 from './FinaleV3';
import * as V5 from './FinaleV5';
import * as V6 from './FinaleV6';
import * as V7 from './FinaleV7';
import * as V8 from './FinaleV8';
import {P} from './kit';

type Item = {src?: number[]; rate?: number; fade?: boolean; hold?: number; scene?: string; section?: string; n?: number; durF?: number};
const K = FPS / 30;
type Span = Item & {o0: number; o1: number};
const SPANS: Span[] = (() => {
  let o = 0;
  return (seq as Item[]).map((it) => {
    const d = Math.round((it.src ? (it.src[1] - it.src[0]) / (it.rate ?? 1) : it.durF ?? 0) * K);
    const s = {...it, o0: o, o1: o + d};
    o += d;
    return s;
  });
})();
export const FINALE_FRAMES = SPANS[SPANS.length - 1].o1;

const SCENES: Record<string, React.FC<S.SP>> = {
  stats4: V5.Stats4,
  team2: V5.Team2,
  surprise: V5.Surprise,
  toolsreal: V5.ToolsReal,
  visitcal: V5.VisitCal,
  qrreal: V5.QrReal,
  rag: V5.Rag,
  playlive: V5.PlayLive,
  gaps: V5.Gaps,
  whofor: S.WhoFor,
  safety: S.Safety,
  morphine: S.Morphine,
  sarvam23: S.Sarvam23,
  whisper: S.Whisper,
  process: S.Process,
  pilot: S.Pilot,
  tagline: S.Tagline,
  trend: V3.Trend,
  deploy: V3.Deploy,
  ertalk: V6.ErTalk,
  pivot: V6.PivotCard,
  goctitle: V7.GocTitle,
  transcript: V7.Transcript,
  erinfo: V7.ErInfo,
  carer: V7.Carer,
  mapzoom: V8.MapZoom,
  erqr: V8.ErQr,
};

const film = (sf: number, captions = true) => (
  <Freeze frame={Math.max(0, Math.min(Math.round(sf), TOTAL_FRAMES - 1))}>
    <Journey audio={false} captions={captions} />
  </Freeze>
);

const bodyAt = (f: number, depth = 0): React.ReactNode => {
  const i = SPANS.findIndex((s) => f >= s.o0 && f < s.o1);
  const s = SPANS[i < 0 ? SPANS.length - 1 : i];
  const lf = f - s.o0;
  if (s.src) {
    const fr = film(s.src[0] * K + lf * (s.rate ?? 1));
    if (!s.fade) return fr;
    const a = 1 - lf / (0.6 * FPS);
    return (
      <AbsoluteFill>
        {fr}
        {a > 0 && <div style={{position: 'absolute', inset: 0, background: P.cream, opacity: a}} />}
      </AbsoluteFill>
    );
  }
  if (s.hold !== undefined) return film(s.hold * K);
  if (s.section) {
    const lt = lf / FPS;
    const dur = (s.o1 - s.o0) / FPS;
    const first = s.o0 === 0;
    return (
      <AbsoluteFill>
        {depth === 0 && !first && lt < 0.5 && bodyAt(s.o0 - 1, 1)}
        {depth === 0 && lt > dur - 0.5 && s.o1 < FINALE_FRAMES && bodyAt(s.o1, 1)}
        <V.Section t={lt} dur={dur} n={s.n ?? 1} title={s.section} first={first} />
      </AbsoluteFill>
    );
  }
  const C = SCENES[s.scene ?? ''];
  if (!C) throw new Error('no scene ' + s.scene);
  return (
    <AbsoluteFill>
      <C t={lf / FPS} />
      <Finish />
    </AbsoluteFill>
  );
};

export const JourneyFinale: React.FC<{audio?: boolean}> = ({audio = true}) => {
  const f = useCurrentFrame();
  return (
    <AbsoluteFill style={{background: '#0E1A2B'}}>
      {bodyAt(f)}
      {audio && <Audio src={staticFile('journey/mix_v5.wav')} />}
    </AbsoluteFill>
  );
};
