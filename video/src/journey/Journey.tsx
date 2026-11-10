import React from 'react';
import {AbsoluteFill, Audio, staticFile} from 'remotion';
import {Finish, SceneWindow} from './kit';
import {Inserts, Overlays} from './Inserts';
import {loadFonts} from '../fonts';
import {Scene as A} from './scenes/A_er';
import {Scene as B} from './scenes/B_origin';
import {Scene as C} from './scenes/C_build';
import {Scene as D} from './scenes/D_pivot';
import {Scene as E} from './scenes/E_talk';
import {Scene as F} from './scenes/F_lang';
import {Scene as X} from './scenes/X_features';
import {Scene as G} from './scenes/G_scan';
import {Scene as H} from './scenes/H_carer';
import {Scene as I} from './scenes/I_close';
import {WIN} from './windows';
import {Captions} from './Captions';

loadFonts();

const S: Record<string, React.FC> = {A, B, C, D, E, X, F, G, H, I};

export const Journey: React.FC<{audio?: boolean}> = ({audio = true}) => (
  <AbsoluteFill style={{background: '#0E1A2B'}}>
    {Object.entries(WIN).map(([k, [a, b]]) => {
      const Comp = S[k];
      return (
        <SceneWindow key={k} from={a} to={b}>
          <Comp />
        </SceneWindow>
      );
    })}
    <Inserts />
    <Overlays />
    <Finish />
    <Captions />
        {audio && <Audio src={staticFile('journey/mix.wav')} />}
  </AbsoluteFill>
);

