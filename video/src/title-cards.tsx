// Section title cards for the deck, in the film's chapter style (ChapterScene look, band green).
import React from 'react';
import {AbsoluteFill, Composition, registerRoot, useCurrentFrame} from 'remotion';
import {inOutE, outE, prog} from './anim';
import {Words} from './common';
import {loadFonts} from './fonts';
import {C, FONT_BODY, FONT_HEAD, FPS, H, MARGIN, W} from './theme';

loadFonts();

/** The film's 66 frames, or longer so the last word lands (12 + 5 per word + 22) and the full title holds 20 frames before the 14-frame fade. */
const framesFor = (name: string) => Math.max(66, 68 + 5 * name.split(' ').length);
const TITLES: Array<[number, string]> = [[2, 'Goals of care discussion'], [3, 'Symptom tracking'], [4, 'Patient emergency QR card'], [5, 'Upload and ask your documents'], [6, 'Simple mode for older and non-tech-savvy people']];

/** ChapterScene's look and timing, laid out top-down so a two-line title pushes the underline below it. */
const SectionTitle: React.FC<{n: number; name: string; frames: number}> = ({n, name, frames}) => {
  const f = useCurrentFrame();
  const o = prog(f, 0, 14, outE) * (1 - prog(f, frames - 14, frames - 2, inOutE));
  const line = prog(f, 14, 44, inOutE);
  const k = prog(f, 6, 26, outE);
  return (
    <div style={{position: 'absolute', inset: 0, opacity: o, color: '#fff', fontFamily: FONT_BODY}}>
      <div style={{position: 'absolute', left: MARGIN, top: 250, width: 1500}}>
        <div style={{height: 60, fontSize: 32, letterSpacing: 4, textTransform: 'uppercase', color: C.onBandSub, fontWeight: 600, transform: `translateY(${(1 - k) * 20}px)`, opacity: k}}>Chapter {n}</div>
        <Words text={name} start={12} stagger={5} style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 120, lineHeight: 1.1}} />
        <div style={{marginTop: 38, width: 220 * line, height: 5, borderRadius: 3, background: '#DCEBE3'}} />
      </div>
    </div>
  );
};

const Card: React.FC<{n: number; name: string}> = ({n, name}) => (
  <AbsoluteFill style={{background: C.band}}>
    <SectionTitle n={n} name={name} frames={framesFor(name)} />
  </AbsoluteFill>
);

const Root: React.FC = () => (
  <>
    {TITLES.map(([n, name]) => (
      <Composition key={n} id={`Title${n}`} component={Card} durationInFrames={framesFor(name)} fps={FPS} width={W} height={H} defaultProps={{n, name}} />
    ))}
  </>
);

registerRoot(Root);
