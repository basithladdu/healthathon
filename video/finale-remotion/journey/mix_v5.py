"""v5 finale audio (soft stem without swooshes/thunks/scratches, no transition whooshes, about 8 dB quieter than v4): sound effects only (no voice, no music), softened. Built from src/journey/finale_seq.json, the same
running order the picture uses (1600 samples per 30 fps frame): source spans copy the film's sfx stem, holds are
silence, scenes and section slides get their own gentle cues. Writes public/journey/mix.wav."""
import json, os, subprocess, wave
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
SR = 48000
SPF = SR // 30
rng = np.random.RandomState(5)


def read(p):
    raw = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', p, '-ar', str(SR), '-ac', '2', '-f', 's16le', '-'])
    return np.frombuffer(raw, dtype=np.int16).astype(np.float32).reshape(-1, 2) / 32768


def write(p, a):
    pcm = np.int16(np.clip(a, -1, 1) * 32767)
    with wave.open(p, 'wb') as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())


def tt(d): return np.arange(int(d * SR)) / SR
def mtof(m): return 440.0 * 2 ** ((m - 69) / 12.0)
def lowpass(x, fc):
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / SR); X *= 1 / (1 + (f / fc) ** 4); return np.fft.irfft(X, len(x))
def hipass(x, fc):
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / SR); X *= 1 - 1 / (1 + (f / max(fc, 1)) ** 4); return np.fft.irfft(X, len(x))
def whoosh(d=0.7, f0=400, f1=1800, vel=1.0):
    t = tt(d); n = rng.randn(len(t)); out = np.zeros(len(t)); seg = 1024
    for i in range(0, len(t), seg):
        u = i / len(t); fc = f0 + (f1 - f0) * np.sin(np.pi * u); c = n[i:i + seg]
        out[i:i + seg] = lowpass(c, fc)[:len(c)] if len(c) > 8 else 0
    return out * np.sin(np.pi * t / d) ** 2 * vel
def pop_(f=520, vel=1.0):
    t = tt(0.16); fr = f * (1 + 0.8 * np.exp(-t / 0.02)); return np.sin(2 * np.pi * np.cumsum(fr) / SR) * np.exp(-t / 0.04) * vel
def thunk(f=110, vel=1.0):
    t = tt(0.35); fr = f * (1 + 1.5 * np.exp(-t / 0.03)); s = np.sin(2 * np.pi * np.cumsum(fr) / SR) * np.exp(-t / 0.09)
    s += lowpass(rng.randn(len(t)), 900) * np.exp(-t / 0.02) * 0.6; return s * vel
def tick(f=2400, vel=1.0):
    t = tt(0.05); return np.sin(2 * np.pi * f * t) * np.exp(-t / 0.008) * vel
def chime(m=84, vel=1.0, d=2.5):
    t = tt(d); f = mtof(m)
    s = sum(np.sin(2 * np.pi * f * r * t) * np.exp(-t / (1.2 / r)) * a for r, a in ((1, 1), (2.76, 0.4), (5.4, 0.2)))
    return s * np.minimum(1, t / 0.003) * vel
def page(vel=1.0): return hipass(whoosh(0.28, 1500, 5000), 1200) * vel
def soft(f=600, vel=0.6): return pop_(f, vel) * 0.7


_z = lambda *a, **k: np.zeros(int(0.05 * SR))
whoosh = _z
thunk = _z
page = _z


def cues(kind, dur):
    W = (0.0, whoosh(0.6, 300, 1400, 0.35), 0.6)
    if kind == 'section':
        return [(0.0, whoosh(0.55, 300, 1500, 0.5), -0.5), (0.45, thunk(80, 0.5), 0), (0.47, chime(79, 0.35, 2.2), 0), (dur - 0.5, whoosh(0.5, 400, 1600, 0.4), 0.5)]
    c = [W]
    if kind == 'stats4':
        B = [0.3, 4.6, 8.9]
        for i, b in enumerate(B):
            c += [(b - 0.4, whoosh(0.5, 300, 1200, 0.35), 0), (b + 0.6, chime(76 + i * 3, 0.3, 1.6), 0)]
        c += [(B[0] + 0.6 + k * 0.09, tick(1600 + k * 15, 0.12), 0) for k in range(18)]
        c += [(B[1] + 0.6 + k * 0.07, page(0.25), 0.3) for k in range(8)]
        c += [(B[2] + 0.8 + k * 0.05, soft(500 + k * 12, 0.25), (k % 5 - 2) / 3) for k in range(22)]
    elif kind == 'team2':
        c += [(0.5 + i * 0.25, soft(480 + i * 50, 0.5), -0.6 + i * 0.3) for i in range(5)]
        c += [(4.0, whoosh(1.4, 250, 900, 0.4), 0), (5.4, chime(84, 0.45, 2.5), 0), (5.42, chime(91, 0.2, 2.5), 0.2)]
    elif kind == 'whofor':
        c += [(x, soft(520 + i * 60, 0.6), -0.6 + i * 0.4) for i, x in enumerate([0.4, 1.3, 2.2, 3.1])]
    elif kind == 'surprise':
        c += [(0.3, whoosh(0.6, 300, 1200, 0.3), 0), (3.6, tick(2200, 0.6), 0), (4.0, soft(640, 0.5), 0), (5.0, chime(81, 0.3, 1.5), 0)]
    elif kind == 'safety':
        c += [(x, soft(560 + i * 40, 0.5), 0.3) for i, x in enumerate([0.9, 2.5, 4.1])]
        c += [(2.6, page(0.5), -0.3), (3.1, page(0.4), -0.2), (7.8, tick(2000, 0.4), 0)]
    elif kind == 'trend':
        c += [(0.6, whoosh(1.8, 300, 1000, 0.25), 0)]
    elif kind == 'toolsreal':
        for i, b in enumerate([0.4, 5.6, 10.8]):
            c += [(b, tick(2200, 0.35), 0), (b + 1.6, whoosh(0.8, 400, 1500, 0.3), 0.3), (b + 2.6, soft(600 + i * 80, 0.5), 0.4)]
    elif kind == 'visitcal':
        c += [(0.5, soft(600, 0.5), -0.4)]
    elif kind == 'qrreal':
        c += [(0.15, soft(520, 0.5), 0), (3.6, whoosh(0.8, 300, 1200, 0.35), 0.4), (4.4, tick(2400, 0.5), 0.4), (4.6, tick(2600, 0.5), 0.4), (5.4, whoosh(0.8, 500, 1800, 0.3), 0.4), (6.1, chime(84, 0.35, 1.8), 0)]
    elif kind == 'morphine':
        c += [(0.05, whoosh(1.1, 250, 900, 0.5), 0), (1.4, thunk(110, 0.4), 0), (1.42, soft(760, 0.5), 0), (1.6, chime(84, 0.3, 1.6), 0), (3.3, soft(620, 0.5), 0.2)]
    elif kind == 'sarvam23':
        c += [(0.1, soft(500, 0.6), -0.4)] + [(1.7 + k * 0.11, tick(1800 + k * 40, 0.25), (k % 6 - 2.5) / 3) for k in range(23)]
    elif kind == 'whisper':
        c += [(0.3, soft(520, 0.5), -0.5), (2.3, soft(640, 0.5), 0.5)] + [(1.3 + k * 0.12, tick(2600, 0.18), -0.4) for k in range(10)]
    elif kind == 'rag':
        c += [(0.8, page(0.4), -0.5), (1.2, page(0.4), -0.4), (2.4, soft(560, 0.45), 0.4), (3.3, soft(620, 0.45), 0.4), (4.2, chime(84, 0.3, 1.6), 0.2)]
    elif kind == 'process':
        c += [(x, soft(520 + i * 60, 0.5), 0) for i, x in enumerate([0.3, 1.1, 1.9, 2.7])]
        c += [(3.8 + k * 0.28, tick(2000 + k * 50, 0.3), -0.8 + k * 0.14) for k in range(12)]
        c += [(3.8 + 12 * 0.28 + 0.3, chime(79, 0.35, 2.0), 0)]
    elif kind == 'gaps':
        c += [(0.8 + r * 0.32, tick(1800 + r * 50, 0.25), 0) for r in range(9)]
        c += [(0.8 + 9 * 0.32 + 0.6, whoosh(0.9, 300, 1200, 0.35), 0.5), (0.8 + 9 * 0.32 + 1.0, chime(84, 0.4, 2.5), 0), (0.8 + 9 * 0.32 + 1.02, chime(91, 0.2, 2.5), 0.3)]
    elif kind == 'playlive':
        c += [(0.2, soft(600, 0.5), 0), (1.0, soft(720, 0.5), 0)]
    elif kind == 'pilot':
        c += [(x, soft(520 + i * 50, 0.5), -0.6 + i * 0.25) for i, x in enumerate([1.0, 1.8, 2.6, 3.4, 4.2, 5.0])]
    elif kind == 'deploy':
        for b in [0.15, 6.4]:
            c += [(b - 0.3, whoosh(0.5, 350, 1400, 0.35), 0)] + [(b + d, soft(520 + j * 80, 0.5), -0.5 + j * 0.5) for j, d in enumerate([0.4, 1.6, 2.8])]
    elif kind == 'ertalk':
        c += [(x, soft(520 + i * 40, 0.4), (-0.5 + i * 0.33)) for i, x in enumerate([0.6, 2.4, 4.2, 6.0])]
    elif kind == 'pivot':
        c += [(0.6 + i * 1.8, soft(520 + i * 60, 0.45), -0.5 + i * 0.5) for i in range(3)]
    elif kind == 'goctitle':
        c += [(0.2, chime(81, 0.3, 2.0), 0)]
    elif kind == 'transcript':
        c += [(x, soft(560, 0.35), 0) for x in [0.6, 2.9, 5.2]]
    elif kind == 'erinfo':
        c += [(0.3, chime(84, 0.35, 2.0), 0)] + [(1.0 + i * 0.6, soft(620, 0.35), 0.3) for i in range(3)]
    elif kind == 'carer':
        c += [(x, soft(540 + i * 60, 0.45), -0.5 + i * 0.5) for i, x in enumerate([0.4, 2.2, 4.0])]
    elif kind == 'tagline':
        c = [(0.05, chime(84, 0.5, 3.5), 0), (0.05, chime(91, 0.25, 3.5), 0)]
    return c


def render_cues(kind, n, dur):
    out = np.zeros((n, 2))
    for t0, sig, pan in cues(kind, dur):
        i = int(max(0, t0) * SR)
        if i >= n:
            continue
        j = min(n, i + len(sig))
        out[i:j, 0] += sig[:j - i] * np.cos((pan + 1) * np.pi / 4)
        out[i:j, 1] += sig[:j - i] * np.sin((pan + 1) * np.pi / 4)
    return out


v = read(os.path.join(HERE, 'vo.wav'))
s = read(os.path.join(HERE, 'sfx_soft.wav'))
n = max(len(v), len(s))
s = np.vstack([s, np.zeros((n - len(s), 2), np.float32)])
gs = (np.abs(v).max() * 10 ** (-10.5 / 20)) / max(np.abs(s).max(), 1e-9)
a = s * gs
sfx_peak = np.abs(a).max()
seq = json.load(open(os.path.join(ROOT, 'src', 'journey', 'finale_seq.json')))
fade = int(0.03 * SR)
ramp_in = np.linspace(0, 1, fade, dtype=np.float32)[:, None]
out = []
for it in seq:
    if 'src' in it:
        seg = a[it['src'][0] * SPF: it['src'][1] * SPF].copy()
        rate = it.get('rate', 1)
        want = round((it['src'][1] - it['src'][0]) / rate) * SPF
        if rate != 1 and len(seg):
            xi = np.linspace(0, len(seg) - 1, want)
            seg = np.stack([np.interp(xi, np.arange(len(seg)), seg[:, c]) for c in (0, 1)], 1).astype(np.float32)
        if len(seg) < want:
            seg = np.vstack([seg, np.zeros((want - len(seg), 2), np.float32)])
        seg[:fade] *= ramp_in
        seg[-fade:] *= ramp_in[::-1]
    elif 'hold' in it:
        seg = np.zeros((it['durF'] * SPF, 2), np.float32)
    else:
        kind = 'section' if 'section' in it else it['scene']
        seg = render_cues(kind, it['durF'] * SPF, it['durF'] / 30)
        pk = np.abs(seg).max()
        if pk > 0:
            seg *= 0.7 * sfx_peak / pk
        seg = seg.astype(np.float32)
    out.append(seg)
mix = np.vstack(out)
# softer: low-pass at 6.5 kHz, rounded peaks, about 6 dB below v2
X = np.fft.rfft(mix, axis=0)
fq = np.fft.rfftfreq(len(mix), 1 / SR)
X *= (1 / np.sqrt(1 + (fq / 6500) ** 4))[:, None]
mix = np.fft.irfft(X, len(mix), axis=0).astype(np.float32)
mix /= max(np.abs(mix).max(), 1e-9)
mix = np.tanh(mix * 1.6) / np.tanh(1.6)
mix *= 0.14 / max(np.abs(mix).max(), 1e-9)
dst = os.path.join(ROOT, 'public', 'journey', 'mix_v5.wav')
write(dst, mix)
print('frames %d, audio %.3f s' % (sum((round((i['src'][1] - i['src'][0]) / i.get('rate', 1)) if 'src' in i else i['durF']) for i in seq), len(mix) / SR))
