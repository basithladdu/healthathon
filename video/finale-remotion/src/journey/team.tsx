/* The real team: names, roles and photos (from the final submission deck), plus the Google Play badge.
   Use <NameTag who="sujay" .../> next to the flat character who stands for that person, and <Face/> for photo cards. */
import React from 'react';
import {Img, staticFile} from 'remotion';
import {FONT_BODY, FONT_HEAD, P} from './kit';

export type Who = 'basith' | 'awaiz' | 'sujay' | 'sharada' | 'shirin';
export const TEAM: Record<Who, {name: string; short: string; role: string; org: string; photo: string; cast: string}> = {
  basith: {name: 'Shaik Abdul Basith', short: 'Basith', role: 'Technical lead, founder', org: 'WeDevIT', photo: 'team/basith.jpg', cast: 'engA'},
  awaiz: {name: 'Shaik Muhammad Awaiz', short: 'Awaiz', role: 'Android engineer', org: 'WeDevIT', photo: 'team/awaiz.jpg', cast: 'engB'},
  sujay: {name: 'Dr Sujay Halkur Shankar', short: 'Dr Sujay', role: 'IISc, Bengaluru', org: 'IISc', photo: 'team/sujay.jpg', cast: 'teamDoctor'},
  sharada: {name: 'Dr Sharada Vinod Kutty', short: 'Dr Sharada', role: 'AIIMS Mangalagiri', org: 'AIIMS Mangalagiri', photo: 'team/sharada.jpg', cast: 'sharada'},
  shirin: {name: 'Shirin Ayub', short: 'Shirin', role: 'Bhaskar Medical College, Hyderabad', org: 'Bhaskar Medical College', photo: 'team/shirin.jpg', cast: 'medStudent'},
};
export const ORDER: Who[] = ['basith', 'awaiz', 'sujay', 'sharada', 'shirin'];

/** Round photo. x,y = centre. */
export const Face: React.FC<{who: Who; x: number; y: number; d?: number; s?: number; ring?: string; style?: React.CSSProperties}> = ({who, x, y, d = 220, s = 1, ring = '#FFFFFF', style}) => (
  <div
    style={{
      position: 'absolute',
      left: x - d / 2,
      top: y - d / 2,
      width: d,
      height: d,
      borderRadius: '50%',
      overflow: 'hidden',
      border: `${Math.max(4, d * 0.035)}px solid ${ring}`,
      boxShadow: '0 14px 36px rgba(20,25,40,0.28)',
      transform: `scale(${s})`,
      background: '#ddd',
      ...style,
    }}
  >
    <Img src={staticFile(TEAM[who].photo)} style={{width: '100%', height: '100%', objectFit: 'cover'}} />
  </div>
);

/** Small chip: photo + name, anchored with its left-centre at x,y. a = 0..1 reveal (scale/opacity driven by caller, e.g. pop()). */
export const NameTag: React.FC<{who: Who; x: number; y: number; a?: number; size?: number; dark?: boolean; showRole?: boolean}> = ({who, x, y, a = 1, size = 1, dark = false, showRole = false}) => {
  if (a <= 0.001) return null;
  const h = 64 * size;
  return (
    <div
      style={{
        position: 'absolute',
        left: x,
        top: y - h / 2,
        height: h,
        display: 'flex',
        alignItems: 'center',
        gap: 12 * size,
        padding: `0 ${22 * size}px 0 ${6 * size}px`,
        borderRadius: h,
        background: dark ? 'rgba(14,26,43,0.86)' : '#FFFFFF',
        boxShadow: '0 8px 22px rgba(20,25,40,0.22)',
        transform: `scale(${a})`,
        transformOrigin: 'left center',
        opacity: Math.min(1, a * 1.4),
        whiteSpace: 'nowrap',
      }}
    >
      <div style={{width: h - 12 * size, height: h - 12 * size, borderRadius: '50%', overflow: 'hidden', flex: 'none'}}>
        <Img src={staticFile(TEAM[who].photo)} style={{width: '100%', height: '100%', objectFit: 'cover'}} />
      </div>
      <div style={{display: 'flex', flexDirection: 'column', lineHeight: 1.05}}>
        <span style={{fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 28 * size, color: dark ? '#fff' : P.ink}}>{TEAM[who].short}</span>
        {showRole && <span style={{fontFamily: FONT_BODY, fontSize: 19 * size, color: dark ? 'rgba(255,255,255,0.75)' : '#5B6272'}}>{TEAM[who].role}</span>}
      </div>
    </div>
  );
};

/** Official-style "GET IT ON Google Play" badge (same mark the Sahara website uses). x,y = centre. */
export const PlayBadge: React.FC<{x: number; y: number; s?: number}> = ({x, y, s = 1}) => (
  <div
    style={{
      position: 'absolute',
      left: x,
      top: y,
      transform: `translate(-50%,-50%) scale(${s})`,
      display: 'flex',
      alignItems: 'center',
      gap: 16,
      background: '#000',
      color: '#fff',
      borderRadius: 16,
      border: '2px solid #A6A6A6',
      padding: '12px 30px 12px 22px',
      whiteSpace: 'nowrap',
    }}
  >
    <svg width={52} height={56} viewBox="0 0 24 26">
      <path d="M2 1.2v23.6L13.2 13z" fill="#00d3ff" />
      <path d="M13.2 13 17 9.2 3.6.6C3 .3 2.4.5 2 1.2z" fill="#00f076" />
      <path d="M13.2 13 2 24.8c.4.7 1 .9 1.6.6L17 16.8z" fill="#ff3a44" />
      <path d="M17 9.2 13.2 13 17 16.8l4.4-2.5c1.2-.7 1.2-2 0-2.6z" fill="#ffd400" />
    </svg>
    <div style={{display: 'flex', flexDirection: 'column', lineHeight: 1}}>
      <span style={{fontFamily: FONT_BODY, fontSize: 18, letterSpacing: 1}}>GET IT ON</span>
      <span style={{fontFamily: FONT_BODY, fontWeight: 600, fontSize: 40}}>Google Play</span>
    </div>
  </div>
);
