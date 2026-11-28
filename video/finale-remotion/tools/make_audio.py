"""Original ambient piano + pad bed for the Sahara film. numpy only.
Writes public/audio/sahara-bed.raw.wav ; ffmpeg loudnorm (-16 LUFS) makes sahara-bed.wav."""
import json, wave, os
import numpy as np

SR = 44100
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
tl = json.load(open(os.path.join(root, 'src', 'timeline.json')))
FPS = tl['fps']

cur = 0
chapters, ticks = [], []


def seg(frames, tk, chapter=False):
    global cur
    for t in tk:
        ticks.append((cur + t) / FPS)
    if chapter:
        chapters.append(cur / FPS)
    cur += frames


for o in tl['opening']:
    seg(o['frames'], o['ticks'])
for c in tl['chapters']:
    seg(tl['chapterFrames'], [6], True)
    for fn in c['features']:
        f = tl['features'][str(fn)]
        seg(f['frames'], f['ticks'])
close_start = cur / FPS
for o in tl['close']:
    seg(o['frames'], o['ticks'])
TOTAL = cur / FPS
N = int(np.ceil(TOTAL * SR))
print('total seconds', TOTAL, 'samples', N)

rng = np.random.RandomState(7)
padL = np.zeros(N); padR = np.zeros(N)
pnoL = np.zeros(N); pnoR = np.zeros(N)
tkL = np.zeros(N); tkR = np.zeros(N)


def mtof(m):
    return 440.0 * 2 ** ((m - 69) / 12.0)


def add(bl, br, start_s, sig, pan=0.0, gain=1.0):
    i = int(start_s * SR)
    if i < 0:
        sig = sig[-i:]
        i = 0
    if i >= N or len(sig) == 0:
        return
    j = min(N, i + len(sig))
    gl = np.cos((pan + 1) * np.pi / 4)
    gr = np.sin((pan + 1) * np.pi / 4)
    bl[i:j] += sig[: j - i] * gain * gl
    br[i:j] += sig[: j - i] * gain * gr


CH = [
    [48, 55, 59, 62, 64],  # Cmaj9
    [45, 52, 60, 64, 67],  # Am7
    [41, 53, 57, 60, 64],  # Fmaj7
    [43, 50, 59, 62, 66],  # G6/9 colour
]
CHORD_LEN = 8.0
nchords = int(np.ceil(TOTAL / CHORD_LEN)) + 1


def pad_note(m, dur, att=2.6, rel=3.2):
    n = int((dur + rel) * SR)
    t = np.arange(n) / SR
    f0 = mtof(m)
    sig = np.zeros(n)
    for det in (-5, 0, 6):  # gentle chorus
        f = f0 * 2 ** (det / 1200.0)
        ph = rng.rand() * 2 * np.pi
        for h in range(1, 7):
            sig += np.sin(2 * np.pi * f * h * t + ph * h) * (1.0 / h ** 1.6)
    env = np.minimum(1, t / att) ** 2
    env *= np.where(t > dur, np.exp(-(t - dur) / (rel / 3.0)), 1.0)
    # slow breathing
    env *= 1 + 0.08 * np.sin(2 * np.pi * 0.11 * t + rng.rand() * 6)
    return sig * env


def piano_note(m, vel=0.5, dur=5.5):
    n = int(dur * SR)
    t = np.arange(n) / SR
    f0 = mtof(m)
    sig = np.zeros(n)
    B = 0.00018
    base_tau = 3.2 * (1.25 - (m - 36) / 70.0)
    for h in range(1, 11):
        fh = f0 * h * np.sqrt(1 + B * h * h)
        if fh > 7000:
            break
        tau = base_tau / (1 + 0.55 * (h - 1))
        amp = (1.0 / h ** 1.25) * (1.0 if h != 3 else 0.7)
        sig += amp * np.sin(2 * np.pi * fh * t + rng.rand() * 0.5) * np.exp(-t / tau)
    att = np.minimum(1, t / 0.006)
    sig *= att
    # soft hammer thump: short lowpassed noise
    nz = rng.randn(int(0.05 * SR))
    k = np.ones(40) / 40
    nz = np.convolve(nz, k, mode='same') * np.exp(-np.arange(len(nz)) / (0.012 * SR))
    sig[: len(nz)] += nz * 0.25 * vel
    # velocity-dependent softness: gentle lowpass via smoothing for low vel
    w = int(2 + (1 - vel) * 6)
    sig = np.convolve(sig, np.ones(w) / w, mode='same')
    return sig * vel * 0.9


# ---- pad bed
for ci in range(nchords):
    ch = CH[ci % 4]
    t0 = ci * CHORD_LEN
    for k, m in enumerate(ch):
        s = pad_note(m, CHORD_LEN + 1.2)
        pan = (k - 2) * 0.12
        add(padL, padR, t0 - 1.2, s, pan, 0.07 if m < 50 else 0.05)

# ---- sparse piano: slow arpeggio phrases, each chord
for ci in range(nchords):
    ch = CH[ci % 4]
    t0 = ci * CHORD_LEN
    up = [ch[2], ch[3], ch[4], ch[3] + 12 if ci % 2 else ch[4] + 7]
    pattern = [0, 1, 2, 1, 3] if ci % 2 == 0 else [2, 3, 1, 2, 0]
    times = [0.4, 2.2, 3.7, 5.3, 6.3]
    for p, tt in zip(pattern, times):
        if rng.rand() < 0.18:
            continue
        m = up[p] + (12 if ci % 3 == 2 and p == 3 else 0)
        add(pnoL, pnoR, t0 + tt + rng.rand() * 0.06, piano_note(m, 0.42 + 0.1 * rng.rand()), (rng.rand() - 0.5) * 0.5, 0.30)
    # low root, once per chord
    add(pnoL, pnoR, t0 + 0.05, piano_note(ch[0] - 12 if ch[0] > 40 else ch[0], 0.5, 7.0), -0.1, 0.28)

# ---- swells at chapter changes
for ts in chapters + [close_start]:
    ci = int(ts // CHORD_LEN)
    ch = CH[ci % 4]
    for m in (ch[0] + 12, ch[1] + 12, ch[3] + 12):
        n = int(4.2 * SR)
        t = np.arange(n) / SR
        env = np.where(t < 1.4, (t / 1.4) ** 2, np.exp(-(t - 1.4) / 0.9))
        sig = (np.sin(2 * np.pi * mtof(m) * t) + 0.35 * np.sin(2 * np.pi * mtof(m) * 2 * t)) * env
        add(padL, padR, ts - 1.4, sig, rng.rand() - 0.5, 0.05)
    add(pnoL, pnoR, ts, piano_note(ch[0] + 12, 0.55, 6.0), 0.0, 0.3)

# ---- soft UI ticks (very quiet, musical pentatonic)
PENT = [84, 86, 88, 91, 93]
for i, ts in enumerate(sorted(ticks)):
    m = PENT[(i * 2 + i // 3) % len(PENT)]
    n = int(0.35 * SR)
    t = np.arange(n) / SR
    f = mtof(m)
    sig = (np.sin(2 * np.pi * f * t) + 0.25 * np.sin(2 * np.pi * f * 2.01 * t)) * np.exp(-t / 0.07) * np.minimum(1, t / 0.004)
    add(tkL, tkR, ts, sig, (rng.rand() - 0.5) * 0.6, 0.010)


# ---- reverb (block FFT convolution) -------------------------------------
def make_ir(seed, length=3.0, damp=0.45):
    r = np.random.RandomState(seed)
    n = int(length * SR)
    t = np.arange(n) / SR
    nz = r.randn(n)
    # frequency-dependent decay: low band decays slower
    lp = np.convolve(nz, np.ones(24) / 24, mode='same')
    env_hi = np.exp(-t / 0.55)
    env_lo = np.exp(-t / 1.15)
    ir = nz * env_hi * 0.25 + lp * env_lo * 1.6
    pre = int(0.022 * SR)
    ir = np.concatenate([np.zeros(pre), ir])
    ir /= np.sqrt(np.sum(ir ** 2))
    return ir


def conv(x, ir, block=1 << 16):
    nfft = 1
    while nfft < block + len(ir):
        nfft <<= 1
    H = np.fft.rfft(ir, nfft)
    out = np.zeros(len(x) + len(ir))
    for i in range(0, len(x), block):
        seg_ = x[i:i + block]
        y = np.fft.irfft(np.fft.rfft(seg_, nfft) * H, nfft)
        out[i:i + len(seg_) + len(ir) - 1] += y[: len(seg_) + len(ir) - 1]
    return out[: len(x)]


irL, irR = make_ir(1), make_ir(2)


def lowpass(x, fc):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / SR)
    X *= 1.0 / (1.0 + (f / fc) ** 4)
    return np.fft.irfft(X, len(x))


# pad: soften highs; piano: slightly
padL, padR = lowpass(padL, 1800), lowpass(padR, 1800)
pnoL, pnoR = lowpass(pnoL, 5200), lowpass(pnoR, 5200)
print('reverb...')
wetL = conv(padL * 0.6 + pnoL * 0.9 + tkL * 0.8, irL)
wetR = conv(padR * 0.6 + pnoR * 0.9 + tkR * 0.8, irR)
outL = padL + pnoL + tkL + wetL * 0.55
outR = padR + pnoR + tkR + wetR * 0.55

# fades
t = np.arange(N) / SR
fade = np.minimum(1, t / 3.0) ** 1.5 * np.minimum(1, (TOTAL - t) / 5.5) ** 1.5
outL *= fade
outR *= fade
# gentle high-pass for rumble
def hp(x, fc=60):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / SR)
    X *= 1.0 - 1.0 / (1.0 + (f / fc) ** 4)
    return np.fft.irfft(X, len(x))
outL, outR = hp(outL), hp(outR)
peak = max(np.abs(outL).max(), np.abs(outR).max())
outL, outR = outL / peak * 0.8, outR / peak * 0.8
pcm = np.empty(N * 2, dtype=np.int16)
pcm[0::2] = np.int16(outL * 32767)
pcm[1::2] = np.int16(outR * 32767)
os.makedirs(os.path.join(root, 'public', 'audio'), exist_ok=True)
p = os.path.join(root, 'public', 'audio', 'sahara-bed.raw.wav')
with wave.open(p, 'wb') as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(pcm.tobytes())
print('wrote', p)
