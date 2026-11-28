"""Finale mix: voice + sfx only (no music). SFX sit exactly as in mix.py (peaks 10.5 dB under the voice peak).
Applies src/journey/finale.json on the same frames as the picture (1600 samples per frame): scene kinds insert their
own sound effects over silence, kind "cut" removes the span. Then loudness to -14 LUFS / -2.2 dBTP like mix.py.
Writes public/journey/mix.wav (the old public mix is kept once as public/journey/mix_with_music.wav)."""
import json, os, shutil, subprocess, wave
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


# ---- the same little synths as journey/audio.py ----
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


# cue times mirror the constants in src/journey/FinaleScenes.tsx
EV_BEATS = [0.25, 2.35, 4.45, 6.55, 8.65]
WF_T = [0.4, 1.3, 2.2, 3.1]
SF_T = [0.9, 2.4, 3.9, 5.4]
TL_T = [0.4, 4.9, 9.4]
MO_T = [1.4, 3.3]
SV_T0, SV_STEP = 1.7, 0.11
WH_T = [0.3, 2.3]
PR_M0, PR_MSTEP = 7.7, 0.66
PR_END = PR_M0 + 12 * PR_MSTEP + 0.3
PR_OFF = 7440 / 30 - 240.509
OT_T0, OT_Q = 0.4, [2.4, 3.5]
PI_T = [1.0, 1.6, 2.2, 2.8, 3.4]
PY_T = [0.4, 1.3, 2.2]


def cues(kind, dur=0):
    c = []
    if kind == 'hold':
        return c
    if kind == 'section':
        return [(0.0, whoosh(0.5, 300, 1800, 0.6), -0.5), (0.42, thunk(80, 0.7), 0), (0.45, chime(79, 0.4, 2.2), 0), (0.47, chime(86, 0.2, 2.0), 0.3), (dur - 0.5, whoosh(0.5, 400, 2000, 0.5), 0.5)]
    if kind != 'process2':
        c.append((0.0, whoosh(0.55, 350, 1600, 0.45), 0.8))
    if kind == 'evidence2':
        for i, b in enumerate(EV_BEATS):
            c += [(b - 0.35, whoosh(0.45, 300, 1400, 0.5), 0.3 * (-1) ** i), (b - 0.03, thunk(90 if i < 4 else 70, 1.0), 0), (b + 0.3, pop_(700, 0.45), 0.2)]
        c.append((EV_BEATS[-1] + 0.6, chime(76, 0.45, 2.0), 0))
    elif kind == 'whofor':
        c += [(x, pop_(520 + i * 60, 0.7), -0.6 + i * 0.4) for i, x in enumerate(WF_T)]
    elif kind == 'safety':
        c += [(x, pop_(560 + i * 40, 0.6), 0.3) for i, x in enumerate(SF_T)]
        c += [(SF_T[1], page(0.7), -0.3), (SF_T[1] + 0.5, page(0.6), -0.2)]
    elif kind == 'tools':
        c += [(x, tick(2200, 0.7), 0) for x in TL_T] + [(x + 0.9, pop_(600 + i * 80, 0.7), 0.4) for i, x in enumerate(TL_T)]
        c += [(x - 0.3, whoosh(0.4, 400, 1800, 0.4), 0) for x in TL_T[1:]]
    elif kind == 'morphine':
        c += [(0.05, whoosh(1.1, 250, 900, 0.7), 0), (MO_T[0] - 0.03, thunk(110, 0.7), 0), (MO_T[0], pop_(760, 0.7), 0),
              (MO_T[0] + 0.2, chime(84, 0.35, 1.6), 0), (MO_T[1], pop_(620, 0.7), 0.2)]
    elif kind == 'sarvam23':
        c += [(0.1, pop_(500, 0.8), -0.4)]
        c += [(SV_T0 + k * SV_STEP, tick(1800 + k * 40, 0.4), (k % 6 - 2.5) / 3) for k in range(23)]
        c += [(SV_T0 + 23 * SV_STEP + 0.6, pop_(700, 0.6), 0.6)]
    elif kind == 'whisper':
        c += [(WH_T[0], pop_(520, 0.7), -0.5), (WH_T[1], pop_(640, 0.7), 0.5)]
        c += [(WH_T[0] + 1.0 + k * 0.12, tick(2600, 0.25), -0.4) for k in range(10)]
    elif kind == 'process2':
        c += [(PR_M0 + k * PR_MSTEP - PR_OFF, tick(2000 + k * 60, 0.55), -0.8 + k * 0.14) for k in range(12) if PR_M0 + k * PR_MSTEP - PR_OFF >= 0]
        c += [(PR_END - PR_OFF, chime(79, 0.4, 2.0), 0), (PR_END + 0.75 - PR_OFF, pop_(700, 0.7), 0)]
    elif kind == 'others':
        c += [(OT_T0 + k * 0.2, pop_(480 + k * 50, 0.55), -0.6 + (k % 3) * 0.6) for k in range(6)]
        c += [(OT_Q[1], chime(84, 0.4, 2.0), 0)]
    elif kind == 'pilot':
        c += [(x, pop_(520 + i * 60, 0.65), -0.6 + i * 0.3) for i, x in enumerate(PI_T)]
    elif kind == 'pays':
        c += [(x, pop_(560 + i * 80, 0.7), -0.5 + i * 0.5) for i, x in enumerate(PY_T)]
        c += [(PY_T[2] + 0.1 + k * 0.09, tick(2200, 0.25), 0.5) for k in range(15)]
    elif kind == 'problems':
        c += [(x, pop_(480 + i * 60, 0.7), -0.6 + i * 0.3) for i, x in enumerate([0.6, 1.6, 2.6, 3.6, 4.6])]
    elif kind == 'deploy':
        for b in [0.4, 5.4, 12.4]:
            c += [(b - 0.3, whoosh(0.45, 350, 1600, 0.45), 0)] + [(b + d, pop_(520 + j * 80, 0.6), -0.5 + j * 0.5) for j, d in enumerate([0.3, 1.3, 2.3])]
    elif kind == 'compare':
        c += [(1.0 + r * 0.55, tick(1800 + r * 50, 0.4), 0) for r in range(9)]
    elif kind == 'qr':
        c += [(0.15, pop_(520, 0.6), 0), (0.9, whoosh(1.2, 600, 2400, 0.25), 0), (2.2, chime(84, 0.35, 1.5), 0)]
    elif kind == 'team':
        c += [(0.7 + i * 0.18, pop_(500 + i * 50, 0.5), -0.6 + i * 0.3) for i in range(5)]
    elif kind == 'trend':
        c += [(0.6, whoosh(1.8, 300, 1200, 0.3), 0)]
    elif kind == 'tagline':
        c = [(0.05, chime(84, 0.7, 3.5), 0), (0.05, chime(91, 0.35, 3.5), 0), (0.9, pop_(600, 0.5), 0)]
    return c


def render_cues(kind, n, dur=0):
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
s = read(os.path.join(HERE, 'sfx.wav'))
n = max(len(v), len(s))
pad = lambda a: np.vstack([a, np.zeros((n - len(a), 2), np.float32)])
v, s = pad(v), pad(s)
gs = (np.abs(v).max() * 10 ** (-10.5 / 20)) / max(np.abs(s).max(), 1e-9)
a = s * gs  # owner: no voice-over, no music, sound effects only
sfx_peak = np.abs(s * gs).max()
print('sfx gain %.1f dB' % (20 * np.log10(gs)))

plan = sorted(json.load(open(os.path.join(ROOT, 'src', 'journey', 'finale.json'))), key=lambda c: c['atF'])
fade = int(0.03 * SR)
fin = np.linspace(0, 1, fade, dtype=np.float32)[:, None]
out, prev = [], 0
for c in plan:
    cut = c['atF'] * SPF
    if cut > prev:
        seg = a[prev:cut].copy()
        seg[-fade:] *= fin[::-1]
        out.append(seg)
        prev = cut
    if c['kind'] == 'cut':
        prev = cut + c['durF'] * SPF
        a[prev:prev + fade] *= fin
        continue
    fx = render_cues(c['kind'], c['durF'] * SPF, c['durF'] / 30)
    pk = np.abs(fx).max()
    if pk > 0:
        fx *= 0.8 * sfx_peak / pk
    out.append(fx.astype(np.float32))
    a[prev:prev + fade] *= fin
out.append(a[prev:])
mix = np.vstack(out)
pk = np.abs(mix).max()
if pk > 0.99:
    mix /= pk / 0.99
# owner: softer. gentle low-pass at 6.5 kHz, soft-knee tame of the peaks, then about 6 dB lower than before
X = np.fft.rfft(mix, axis=0)
fq = np.fft.rfftfreq(len(mix), 1 / SR)
X *= (1 / np.sqrt(1 + (fq / 6500) ** 4))[:, None]
mix = np.fft.irfft(X, len(mix), axis=0).astype(np.float32)
mix /= max(np.abs(mix).max(), 1e-9)
mix = np.tanh(mix * 1.6) / np.tanh(1.6)  # rounds off the sharp hits
mix *= 0.35 / max(np.abs(mix).max(), 1e-9)
pub = os.path.join(ROOT, 'public', 'journey')
bak = os.path.join(pub, 'mix_with_music.wav')
if not os.path.exists(bak):
    shutil.copy2(os.path.join(pub, 'mix.wav'), bak)
dst = os.path.join(pub, 'mix.wav')
write(dst, mix)
d = float(subprocess.check_output(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', dst]))
print('frames expected %d, audio %.3f s (%.1f frames)' % (int(np.ceil(274.594 * 30)) + sum(-c['durF'] if c['kind'] == 'cut' else c['durF'] for c in plan), d, d * 30))
