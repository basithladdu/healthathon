import React from 'react';
import {C, FONT_BODY, FONT_HEAD} from './theme';
import {outE, prog, inOutE} from './anim';

export const EW = 780; // explainer card size
export const EH = 410;

export const rise = (f: number, delay: number, dist = 22, dur = 22): React.CSSProperties => {
  const t = prog(f, delay, delay + dur, outE);
  return {opacity: t, transform: `translateY(${(1 - t) * dist}px)`};
};

export const typed = (text: string, f: number, start: number, cps = 1.1) =>
  text.slice(0, Math.max(0, Math.min(text.length, Math.floor((f - start) * cps))));

export const Card: React.FC<{children: React.ReactNode; style?: React.CSSProperties}> = ({children, style}) => (
  <div
    style={{
      position: 'relative',
      width: EW,
      height: EH,
      borderRadius: 32,
      background: C.surface,
      border: `2px solid ${C.line}`,
      boxShadow: '0 18px 50px rgba(56,46,41,0.08)',
      overflow: 'hidden',
      fontFamily: FONT_BODY,
      color: C.ink,
      ...style,
    }}
  >
    <div style={{position: 'absolute', left: 32, top: 32, right: 32, bottom: 32}}>{children}</div>
  </div>
);

export const Chip: React.FC<{children: React.ReactNode; bg?: string; fg?: string; style?: React.CSSProperties}> = ({children, bg = C.primaryWash, fg = C.primaryPressed, style}) => (
  <div
    style={{
      display: 'inline-flex',
      alignItems: 'center',
      height: 44,
      padding: '0 18px',
      borderRadius: 22,
      background: bg,
      color: fg,
      fontSize: 28,
      fontWeight: 600,
      lineHeight: 1,
      paddingTop: 5,
      whiteSpace: 'nowrap',
      ...style,
    }}
  >
    {children}
  </div>
);

/** Check mark that draws itself. */
export const Check: React.FC<{size: number; t: number; color?: string; stroke?: number}> = ({size, t, color = '#fff', stroke = 5}) => (
  <svg width={size} height={size} viewBox="0 0 24 24" style={{display: 'block'}}>
    <path d="M5 12.5 L10 17.5 L19 7" fill="none" stroke={color} strokeWidth={stroke * 0.5} strokeLinecap="round" strokeLinejoin="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - t} />
  </svg>
);

export const Ripple: React.FC<{f: number; start: number; x: number; y: number; color?: string; size?: number}> = ({f, start, x, y, color = C.primary, size = 110}) => {
  const t = prog(f, start, start + 30, outE);
  if (f < start || t >= 1) return null;
  return (
    <div style={{position: 'absolute', left: x - size / 2, top: y - size / 2, width: size, height: size, borderRadius: size, border: `4px solid ${color}`, opacity: 1 - t, transform: `scale(${0.3 + t * 0.9})`}} />
  );
};

export const FingerDot: React.FC<{x: number; y: number; press: number}> = ({x, y, press}) => (
  <div
    style={{
      position: 'absolute',
      left: x - 30,
      top: y - 30,
      width: 60,
      height: 60,
      borderRadius: 30,
      background: 'rgba(56,46,41,0.22)',
      border: '3px solid rgba(56,46,41,0.35)',
      transform: `scale(${1 - press * 0.2})`,
    }}
  />
);

export const Bar: React.FC<{w: number | string; h?: number; color?: string; style?: React.CSSProperties}> = ({w, h = 14, color = C.line, style}) => (
  <div style={{width: w, height: h, borderRadius: h / 2, background: color, ...style}} />
);

export const headFont: React.CSSProperties = {fontFamily: FONT_HEAD, fontWeight: 600};
export {inOutE, outE, prog};
