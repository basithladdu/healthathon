/* Burned-in captions: one short line at a time, bottom centre, readable on night and day scenes. */
import React from 'react';
import {useCurrentFrame} from 'remotion';
import caps from './captions.json';
import {FONT_BODY, FPS, clamp} from './kit';

type Cap = {text: string; s: number; e: number};
const LINES = caps as Cap[];

export const Captions: React.FC = () => {
  const t = useCurrentFrame() / FPS;
  const l = LINES.find((c) => t >= c.s && t < c.e);
  if (!l) return null;
  const a = clamp((t - l.s) / 0.12) * clamp((l.e - t) / 0.12);
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
