"""speech level per narration sentence (RMS over the word spans only) for a wav on the film timeline"""
import wave, sys, json, os, numpy as np
H = os.path.dirname(os.path.abspath(__file__))
W = json.load(open(os.path.join(H, '..', 'src', 'journey', 'words.json'), encoding='utf-8'))['words']


def load(k):
    w = wave.open(os.path.join(H, k + '.wav'))
    sr, ch = w.getframerate(), w.getnchannels()
    x = np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768
    return x.reshape(-1, ch).mean(1), sr


def lev(x, sr, ws):
    e = n = 0
    for w in ws:
        s = x[int(w['s'] * sr):int(w['e'] * sr)]
        e += float((s ** 2).sum()); n += len(s)
    return 10 * np.log10(e / max(n, 1) + 1e-12)


names = sys.argv[1:]
D = [load(k) for k in names]
ids = []
for w in W:
    if w['id'] not in ids:
        ids.append(w['id'])
for i in ids:
    ws = [w for w in W if w['id'] == i]
    print('%-7s %6.1f ' % (i, ws[0]['s']) + ' '.join('%6.1f' % lev(x, sr, ws) for x, sr in D))
