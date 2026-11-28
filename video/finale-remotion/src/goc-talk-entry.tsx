import React from 'react';
import {Composition, registerRoot} from 'remotion';
import {loadFonts} from './fonts';
import {GOC_TALK_SECONDS, GocTalkClip} from './journey/GocTalkClip';

loadFonts();
registerRoot(() => <Composition id="GocTalkClip" component={GocTalkClip} durationInFrames={GOC_TALK_SECONDS * 30} fps={30} width={1920} height={1080} />);
