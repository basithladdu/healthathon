// Pre-cuts each feature clip to [start, speed] so playback in Remotion is plain 1x from frame 0.
import {execSync} from 'node:child_process';
import fs from 'node:fs';
const CLIP = {1:[6.2,0.7],2:[6,1.5],3:[1,1.4],4:[2,1.3],5:[1,1.5],6:[2,1.5],7:[1,1.4],8:[3,1.5],9:[1,1.3],10:[0,1.1],11:[2,1.3],12:[3,1.5],13:[0,1.1],14:[3.5,1.5],15:[1,1.5]};
const idx = JSON.parse(fs.readFileSync('src/clipIndex.json','utf8'));
const tl = JSON.parse(fs.readFileSync('src/timeline.json','utf8'));
const seg14 = [[2.5,12.5,2.4],[14,22.2,2.2],[23.4,27.4,1.0]];
for (const [n,[st,rate]] of Object.entries(CLIP)) {
  if (n === '14') {
    const f = idx[n];
    const parts = seg14.map(([a,b,r],i)=>`[0:v]trim=start=${a}:end=${b},setpts=(PTS-STARTPTS)/${r}[s${i}]`).join(';');
    execSync(`ffmpeg -v error -y -i public/clips/${f} -filter_complex "${parts};[s0][s1][s2]concat=n=3:v=1:a=0,fps=30,scale=720:1600,tpad=stop_mode=clone:stop_duration=2[o]" -map "[o]" -an -c:v libx264 -crf 17 -pix_fmt yuv420p public/cut/f14.mp4`);
    console.log('cut 14'); continue;
  }
  const file = idx[n]; if (!file) continue;
  const outLen = (tl.features[n].frames + 30) / 30;
  const srcLen = outLen * rate;
  execSync(`ffmpeg -v error -y -ss ${st} -t ${srcLen} -i public/clips/${file} -vf "setpts=PTS/${rate},fps=30,scale=720:1600,tpad=stop_mode=clone:stop_duration=1" -an -c:v libx264 -crf 17 -pix_fmt yuv420p public/cut/f${String(n).padStart(2,'0')}.mp4`);
  console.log('cut', n);
}
