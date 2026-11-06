'use client';

import { useEffect, useRef } from 'react';

export type SaharaLottieName = 'heart' | 'pill' | 'document' | 'calendar' | 'success' | 'wave' | 'hug';

/**
 * A small decorative Lottie (the same JSONs as the Android app, in public/lottie). The light SVG player loads only
 * when one is on screen. With reduced motion it shows the last frame and never plays.
 */
export function SaharaLottie({ name, loop = true, className = '' }: { name: SaharaLottieName; loop?: boolean; className?: string }) {
  const box = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    const element = box.current;
    if (!element) return;
    const still = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    let stopped = false;
    let animation: { destroy: () => void; goToAndStop: (value: number, isFrame?: boolean) => void; addEventListener: (name: 'DOMLoaded', cb: () => void) => void; totalFrames: number } | null = null;
    import('lottie-web/build/player/lottie_light').then(({ default: lottie }) => {
      if (stopped) return;
      const playing = lottie.loadAnimation({ container: element, renderer: 'svg', loop: loop && !still, autoplay: !still, path: `/lottie/${name}.json` });
      animation = playing;
      if (still) playing.addEventListener('DOMLoaded', () => playing.goToAndStop(playing.totalFrames - 1, true));
    }).catch(() => undefined);
    return () => { stopped = true; animation?.destroy(); };
  }, [name, loop]);
  return <span ref={box} className={`sh-lottie ${className}`.trim()} aria-hidden="true" />;
}
