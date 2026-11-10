"""Mix voice + music + sfx. Music sits 8.5 dB under the voice (gated RMS), swelling +5 dB under the title; SFX peaks 7.5 dB under voice peak.
Then loudness to -14 LUFS with a -2.2 dBTP ceiling (ffmpeg loudnorm, two pass). Writes journey/mix.wav."""
import json, os, subprocess, wave
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
SR = 48000


def read(p):
    raw = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', p, '-ar', str(SR), '-ac', '2', '-f', 's16le', '-'])
    a = np.frombuffer(raw, dtype=np.int16).astype(np.float64).reshape(-1, 2) / 32768
    return a


v = read(os.path.join(HERE, 'vo.wav'))
m = read(os.path.join(HERE, 'music.wav'))
s = read(os.path.join(HERE, 'sfx.wav'))
n = max(len(v), len(m), len(s))
pad = lambda a: np.vstack([a, np.zeros((n - len(a), 2))])
v, m, s = pad(v), pad(m), pad(s)


def gated_rms(a):
    mono = a.mean(1)
    b = int(0.4 * SR)
    blocks = [np.sqrt(np.mean(mono[i:i + b] ** 2)) for i in range(0, len(mono) - b, b)]
    blocks = np.array(blocks)
    thr = blocks.max() * 10 ** (-40 / 20)
    return np.sqrt(np.mean(blocks[blocks > thr] ** 2))


rv, rm = gated_rms(v), gated_rms(m)
gm = rv / rm * 10 ** (-8.5 / 20)
title = json.load(open(os.path.join(HERE, '..', 'src', 'journey', 'words.json')))
TITLE = [w for w in title['words'] if w['id'] == 'm35'][0]['s']
t = np.arange(n) / SR
swell = 10 ** ((5 * np.clip((t - (TITLE - 0.6)) / 1.2, 0, 1)) / 20)
m = m * gm * swell[:, None]
gs = (np.abs(v).max() * 10 ** (-7.5 / 20)) / max(np.abs(s).max(), 1e-9)
s = s * gs
mix = v + m + s
print('voice gated rms %.1f dB, music gain %.1f dB, sfx gain %.1f dB' % (20 * np.log10(rv), 20 * np.log10(gm), 20 * np.log10(gs)))
pk = np.abs(mix).max()
if pk > 0.99:
    mix /= pk / 0.99
tmp = os.path.join(HERE, 'mix_pre.wav')
pcm = np.int16(np.clip(mix, -1, 1) * 32767)
with wave.open(tmp, 'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
# loudnorm pass 1
r = subprocess.run(['ffmpeg', '-hide_banner', '-i', tmp, '-af', 'loudnorm=I=-14:TP=-2.2:LRA=11:print_format=json', '-f', 'null', '-'], capture_output=True, text=True)
js = r.stderr[r.stderr.rfind('{'):r.stderr.rfind('}') + 1]
st = json.loads(js)
af = ('loudnorm=I=-14:TP=-2.2:LRA=11:measured_I={input_i}:measured_TP={input_tp}:measured_LRA={input_lra}:measured_thresh={input_thresh}:offset={target_offset}:linear=true,'
      'alimiter=limit=0.776:level=false,aresample=48000').format(**st)
subprocess.check_call(['ffmpeg', '-v', 'error', '-y', '-i', tmp, '-af', af, '-ar', '48000', os.path.join(HERE, 'mix.wav')])
r = subprocess.run(['ffmpeg', '-hide_banner', '-i', os.path.join(HERE, 'mix.wav'), '-af', 'ebur128=peak=true', '-f', 'null', '-'], capture_output=True, text=True)
print('\n'.join(r.stderr.strip().splitlines()[-12:]))
