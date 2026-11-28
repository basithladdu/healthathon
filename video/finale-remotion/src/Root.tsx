import React from 'react';
import {Composition} from 'remotion';
import {Film} from './Film';
import {Journey} from './journey/Journey';
import {FINALE_FRAMES, JourneyFinale} from './journey/Finale';
import {TOTAL_FRAMES as JF, FPS as JFPS} from './journey/kit';
import {TOTAL_FRAMES} from './segments';
import {FPS, H, W} from './theme';

export const Root: React.FC = () => (
  <>
    <Composition id="Film" component={Film} durationInFrames={TOTAL_FRAMES} fps={FPS} width={W} height={H} defaultProps={{audio: false}} />
    <Composition id="FilmAudio" component={Film} durationInFrames={TOTAL_FRAMES} fps={FPS} width={W} height={H} defaultProps={{audio: true}} />
    <Composition id="Journey" component={Journey} durationInFrames={JF} fps={JFPS} width={W} height={H} defaultProps={{audio: false}} />
    <Composition id="JourneyAudio" component={JourneyFinale} durationInFrames={FINALE_FRAMES} fps={JFPS} width={W} height={H} defaultProps={{audio: true}} />
  </>
);

