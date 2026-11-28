import {Easing, interpolate, spring} from 'remotion';

export const outE = Easing.bezier(0.22, 1, 0.36, 1);
export const inOutE = Easing.bezier(0.65, 0, 0.35, 1);
export const softE = Easing.bezier(0.33, 0, 0.2, 1);

/** clamped 0..1 progress between frames a and b with easing. */
export const prog = (f: number, a: number, b: number, e: (t: number) => number = outE) =>
  interpolate(f, [a, b], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp', easing: e});

/** critically damped spring: no bounce. */
export const sp = (f: number, fps: number, delay = 0, dur = 30) =>
  spring({frame: f - delay, fps, config: {damping: 200, stiffness: 120, mass: 1}, durationInFrames: dur});

export const lerp = (a: number, b: number, t: number) => a + (b - a) * t;
export const clamp = (v: number, a: number, b: number) => Math.min(b, Math.max(a, v));
