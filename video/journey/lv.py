"""print per-second RMS of voice / music / sfx / mix (and the music + sfx as scaled in the mix)"""
import wave, sys, numpy as np, os
H = os.path.dirname(os.path.abspath(__file__))


def rd(k):
    w = wave.open(os.path.join(H, k + '.wav'))
    sr, ch = w.getframerate(), w.getnchannels()
    x = np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(float) / 32768
    x = x.reshape(-1, ch).mean(1)
    n = len(x) // sr
    return [20 * np.log10(np.sqrt((x[i * sr:(i + 1) * sr] ** 2).mean()) + 1e-9) for i in range(n)]


R = {k: rd(k) for k in ('vo', 'music', 'sfx', 'mix')}
a, b = int(sys.argv[1]), int(sys.argv[2])
print('sec    vo  music    sfx    mix')
for t in range(a, b):
    print('%3d' % t, *['%6.1f' % R[k][t] if t < len(R[k]) else '    na' for k in R])
