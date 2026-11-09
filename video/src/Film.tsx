import React from 'react';
import {AbsoluteFill, Audio, Sequence, staticFile} from 'remotion';
import {segments} from './segments';
import {Background, ProgressTop} from './common';
import {PhoneLayer} from './Phone';
import {ChapterScene, CloseScene, FeatureScene, OpeningScene} from './Scenes';
import {ClipsProvider} from './clips';
import {FEATURES} from './features';
import {loadFonts} from './fonts';

loadFonts();

const files = Object.values(FEATURES).map((d) => d.clip);

export const Film: React.FC<{audio?: boolean}> = ({audio = true}) => (
  <ClipsProvider files={files}>
    <AbsoluteFill>
      <Background />
      {segments.map((s, i) => {
        if (s.kind === 'feature') return null;
        return (
          <Sequence key={i} from={s.start} durationInFrames={s.frames} layout="none">
            {s.kind === 'opening' && <OpeningScene id={s.id} frames={s.frames} />}
            {s.kind === 'close' && <CloseScene id={s.id} frames={s.frames} />}
            {s.kind === 'chapter' && <ChapterScene n={s.n} name={s.name} frames={s.frames} />}
          </Sequence>
        );
      })}
      <PhoneLayer />
      {segments.map((s, i) =>
        s.kind === 'feature' ? (
          <Sequence key={i} from={s.start} durationInFrames={s.frames} layout="none">
            <FeatureScene n={s.n} frames={s.frames} />
          </Sequence>
        ) : null,
      )}
      <ProgressTop />
      {audio && <Audio src={staticFile('audio/sahara-bed.wav')} />}
    </AbsoluteFill>
  </ClipsProvider>
);
