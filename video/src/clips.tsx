import React, {useEffect, useState} from 'react';
import clipMeta from './clipMeta.json';
import {continueRender, delayRender, OffthreadVideo, staticFile, useVideoConfig} from 'remotion';

export type ZoomHint = {t: number; x: number; y: number; scale: number; dur: number; rect?: [number, number, number, number]};
export type ClipInfo = {file: string; exists: boolean; duration?: number; zoom?: ZoomHint};

let manifestP: Promise<any> | null = null;
const existsCache: Record<string, Promise<boolean>> = {};

const loadManifest = () => {
  if (!manifestP) {
    manifestP = fetch(staticFile('clips/manifest.json'))
      .then((r) => (r.ok ? r.json() : null))
      .catch(() => null);
  }
  return manifestP;
};
const clipExists = (file: string) => {
  if (!existsCache[file]) {
    existsCache[file] = fetch(staticFile(`clips/${file}`), {method: 'HEAD'})
      .then((r) => r.ok)
      .catch(() => false);
  }
  return existsCache[file];
};

const num = (...v: any[]) => v.find((x) => typeof x === 'number' && isFinite(x));

/** Tolerant manifest reader: manifest[file] | manifest.clips[file] | array of {file|name|clip}. */
const findEntry = (m: any, file: string): any => {
  if (!m) return null;
  const base = file.replace(/\.mp4$/, '');
  const pools = [m, m.clips, m.items, m.files].filter(Boolean);
  for (const p of pools) {
    if (Array.isArray(p)) {
      const hit = p.find((e) => [e.file, e.name, e.clip, e.id].some((n) => typeof n === 'string' && (n === file || n === base || n.endsWith('/' + file))));
      if (hit) return hit;
    } else if (typeof p === 'object') {
      if (p[file]) return p[file];
      if (p[base]) return p[base];
      const k = Object.keys(p).find((key) => key.startsWith(base.slice(0, 3)));
      if (k && typeof p[k] === 'object') return p[k];
    }
  }
  return null;
};

const parseZoom = (e: any): ZoomHint | undefined => {
  const z = e?.zoom ?? e?.zooms ?? e?.zoomHint ?? e?.zoomHints ?? e?.callout ?? e?.callouts;
  const one = Array.isArray(z) ? z[0] : z;
  if (!one || typeof one !== 'object') return undefined;
  const x = num(one.x, one.cx, one.focusX);
  const y = num(one.y, one.cy, one.focusY);
  if (x === undefined || y === undefined) return undefined;
  const w = num(e.width, one.width) ?? 1;
  const h = num(e.height, one.height) ?? 1;
  return {
    t: num(one.t, one.at, one.time, one.start, one.startSec) ?? 3,
    x: x > 1 ? x / w : x,
    y: y > 1 ? y / h : y,
    scale: num(one.scale, one.zoom, one.factor) ?? 2,
    dur: num(one.dur, one.duration, one.hold) ?? 3,
  };
};

const Ctx = React.createContext<Record<string, ClipInfo>>({});
export const ClipsProvider: React.FC<{files: string[]; children: React.ReactNode}> = ({files, children}) => {
  const [map, setMap] = useState<Record<string, ClipInfo>>({});
  const [handle] = useState(() => delayRender('clips'));
  useEffect(() => {
    Promise.all([Promise.all(files.map(clipExists)), loadManifest()]).then(([ex, m]) => {
      const out: Record<string, ClipInfo> = {};
      files.forEach((file, i) => {
        const e = findEntry(m, file);
        out[file] = {file, exists: ex[i], duration: num(e?.duration, e?.durationSec, e?.durationSeconds, e?.seconds, (clipMeta as Record<string, number>)[file]), zoom: parseZoom(e)};
      });
      setMap(out);
      continueRender(handle);
    });
  }, [files.join('|'), handle]);
  return <Ctx.Provider value={map}>{children}</Ctx.Provider>;
};
export const useClipInfo = (file: string): ClipInfo => React.useContext(Ctx)[file] ?? {file, exists: false};

export const ClipVideo: React.FC<{file: string; n: number}> = ({n}) => (
  <OffthreadVideo
    src={staticFile(`cut/f${String(n).padStart(2, '0')}.mp4`)}
    muted
    style={{width: '100%', height: '100%', objectFit: 'cover'}}
  />
);
