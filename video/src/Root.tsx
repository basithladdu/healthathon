import React from 'react';
import {Composition} from 'remotion';
import {Film} from './Film';
import {TOTAL_FRAMES} from './segments';
import {FPS, H, W} from './theme';

export const Root: React.FC = () => (
  <>
    <Composition id="Film" component={Film} durationInFrames={TOTAL_FRAMES} fps={FPS} width={W} height={H} defaultProps={{audio: false}} />
    <Composition id="FilmAudio" component={Film} durationInFrames={TOTAL_FRAMES} fps={FPS} width={W} height={H} defaultProps={{audio: true}} />
  </>
);
