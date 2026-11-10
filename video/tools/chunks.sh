#!/bin/bash
# render the film in chunks (retrying a failed chunk), then join them
cd /c/Users/basit/sahara-video || exit 1
TOTAL=${1:-8238}; STEP=750; mkdir -p out/ch; : > out/ch/list.txt
a=0
while [ $a -lt $TOTAL ]; do
  b=$((a + STEP - 1)); [ $b -ge $TOTAL ] && b=$((TOTAL - 1))
  f=out/ch/c$(printf %05d $a).mp4
  ok=0
  if [ -s "$f.ok" ]; then ok=1; fi
  for try in 1 2 3; do
    [ $ok = 1 ] && break
    npx remotion render src/index.ts JourneyAudio "$f" --frames=$a-$b --concurrency=2 --crf=16 --gl=angle --muted --timeout=120000 --log=error > out/ch/log.txt 2>&1 && [ -s "$f" ] && ok=1 && echo done > "$f.ok"
    [ $ok = 1 ] || { echo "chunk $a try $try failed: $(grep -v '^$' out/ch/log.txt | head -1 | cut -c1-200)"; }
  done
  [ $ok = 1 ] || { echo "GIVING UP at $a"; exit 2; }
  echo "chunk $a-$b ok"
  echo "file 'c$(printf %05d $a).mp4'" >> out/ch/list.txt
  a=$((b + 1))
done
ffmpeg -y -v error -f concat -safe 0 -i out/ch/list.txt -c copy out/journey-v4.mp4 && ls -la out/journey-v4.mp4
