import React from 'react';
import {Composition} from 'remotion';
import {Film} from './Film';
import {Journey} from './journey/Journey';
import {TOTAL_FRAMES as JF} from './journey/kit';
import {TOTAL_FRAMES} from './segments';
import {FPS, H, W} from './theme';

export const Root: React.FC = () => (
  <>
    <Composition id="Film" component={Film} durationInFrames={TOTAL_FRAMES} fps={FPS} width={W} height={H} defaultProps={{audio: false}} />
    <Composition id="FilmAudio" component={Film} durationInFrames={TOTAL_FRAMES} fps={FPS} width={W} height={H} defaultProps={{audio: true}} />
    <Composition id="Journey" component={Journey} durationInFrames={JF} fps={30} width={W} height={H} defaultProps={{audio: false}} />
    <Composition id="JourneyAudio" component={Journey} durationInFrames={JF} fps={30} width={W} height={H} defaultProps={{audio: true}} />
  </>
);

