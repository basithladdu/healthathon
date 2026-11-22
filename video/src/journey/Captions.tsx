/* Burned-in captions: one short line at a time, bottom centre, readable on night and day scenes. */
import React from 'react';
import {useCurrentFrame} from 'remotion';
import caps from './captions.json';
import {FONT_BODY, FPS, clamp} from './kit';

type Cap = {text: string; s: number; e: number};
const LINES = caps as Cap[];

export const Captions: React.FC = () => {
  const t = useCurrentFrame() / FPS;
  const i = LINES.findIndex((c) => t >= c.s && t < c.e);
  if (i < 0) return null;
  const l = LINES[i];
  // fade only at a real pause; back-to-back lines swap cleanly with no flicker
  const gapIn = i === 0 || l.s - LINES[i - 1].e > 0.05;
  const gapOut = i === LINES.length - 1 || LINES[i + 1].s - l.e > 0.05;
  const a = (gapIn ? clamp((t - l.s) / 0.15) : 1) * (gapOut ? clamp((l.e - t) / 0.15) : 1);
  return (
    <div style={{position: 'absolute', left: 0, right: 0, bottom: 56, display: 'flex', justifyContent: 'center', pointerEvents: 'none'}}>
      <div
        style={{
          fontFamily: FONT_BODY,
          fontWeight: 600,
          fontSize: 44,
          lineHeight: 1.25,
          color: '#FFFFFF',
          background: 'rgba(12,20,32,0.74)',
          padding: '10px 26px 12px',
          borderRadius: 14,
          opacity: a,
          maxWidth: 1500,
          textAlign: 'center',
          letterSpacing: 0.2,
        }}
      >
        {l.text}
      </div>
    </div>
  );
};
