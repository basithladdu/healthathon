"""The ElevenLabs take fades by about 22 dB in its second half. Ride the gain so the voice holds one level.
Reads vo_orig.wav (made from vo.wav on first run), writes vo.wav."""
import wave, os, shutil, numpy as np
H = os.path.dirname(os.path.abspath(__file__))
src, dst = os.path.join(H, 'vo_orig.wav'), os.path.join(H, 'vo.wav')
if not os.path.exists(src):
    shutil.copy(dst, src)
w = wave.open(src)
sr, ch, n = w.getframerate(), w.getnchannels(), w.getnframes()
x = np.frombuffer(w.readframes(n), np.int16).astype(float) / 32768
x = x.reshape(-1, ch)
mono = x.mean(1)
b = int(0.1 * sr)
nb = len(mono) // b
r = np.sqrt((mono[:nb * b].reshape(nb, b) ** 2).mean(1)) + 1e-9
env = np.zeros(nb)
half = 25  # +-2.5 s
for i in range(nb):
    seg = r[max(0, i - half):i + half + 1]
    sp = seg[seg > seg.max() * 10 ** (-18 / 20)]  # the speech blocks in the window
    env[i] = np.sqrt((sp ** 2).mean())
ref = np.median(env[:int(120 / 0.1)])
g = np.clip(ref / env, 10 ** (-3 / 20), 10 ** (28 / 20))
k = np.ones(21) / 21
g = np.convolve(np.pad(g, 10, mode='edge'), k, 'valid')
gs = np.interp(np.arange(len(mono)), (np.arange(nb) + 0.5) * b, g)
y = x * gs[:, None]
# second pass: trim every sentence to the same speech level, measured over its words only
import json
W = json.load(open(os.path.join(H, '..', 'src', 'journey', 'words.json'), encoding='utf-8'))['words']
ids = []
for q in W:
    if q['id'] not in ids:
        ids.append(q['id'])
ym = y.mean(1)
cen, lv = [], []
for i in ids:
    ws = [q for q in W if q['id'] == i]
    e = sum(float((ym[int(q['s'] * sr):int(q['e'] * sr)] ** 2).sum()) for q in ws)
    m_ = sum(int(q['e'] * sr) - int(q['s'] * sr) for q in ws)
    cen.append((ws[0]['s'] + ws[-1]['e']) / 2); lv.append(10 * np.log10(e / max(m_, 1) + 1e-12))
tg = np.median(lv[:20])
trim = np.clip(tg - np.array(lv), -3, 3)
y = y * (10 ** (np.interp(np.arange(len(ym)) / sr, cen, trim) / 20))[:, None]
pk = np.abs(y).max()
if pk > 0.98:
    # soft limit the few peaks instead of pulling everything down
    y = np.tanh(y / 0.98 * 1.0) * 0.98 if pk > 1.3 else y / pk * 0.98
with wave.open(dst, 'wb') as o:
    o.setnchannels(ch); o.setsampwidth(2); o.setframerate(sr)
    o.writeframes(np.int16(np.clip(y, -1, 1) * 32767).tobytes())
for t in (60, 150, 180, 200, 230, 260):
    print('%3d s  gain %+5.1f dB' % (t, 20 * np.log10(g[int(t / 0.1)])))
print('peak before limit %.2f' % pk)
