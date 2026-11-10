"""Original music bed + sound effects for the Sahara journey film (numpy synthesis, no samples).
Writes journey/music.wav and journey/sfx.wav (48 kHz stereo)."""
import json, os, wave
import numpy as np

SR = 48000
HERE = os.path.dirname(os.path.abspath(__file__))
WJ = json.load(open(os.path.join(HERE, '..', 'src', 'journey', 'words.json')))
TOTAL = WJ['total']
N = int(np.ceil(TOTAL * SR)) + SR
rng = np.random.RandomState(11)
W = WJ['words']


def at(sid, w=None, nth=0):
    ws = [x for x in W if x['id'] == sid]
    if w is None:
        return ws[0]['s']
    n = lambda s: ''.join(c for c in s.lower() if c.isalnum() or c == "'")
    h = [x for x in ws if n(x['w']) == n(w)]
    return h[min(nth, len(h) - 1)]['s']


def mtof(m):
    return 440.0 * 2 ** ((m - 69) / 12.0)


class Bus:
    def __init__(self):
        self.L = np.zeros(N)
        self.R = np.zeros(N)

    def add(self, t0, sig, pan=0.0, gain=1.0):
        i = int(t0 * SR)
        if i < 0:
            sig = sig[-i:]
            i = 0
        if i >= N or len(sig) == 0:
            return
        j = min(N, i + len(sig))
        self.L[i:j] += sig[:j - i] * gain * np.cos((pan + 1) * np.pi / 4)
        self.R[i:j] += sig[:j - i] * gain * np.sin((pan + 1) * np.pi / 4)


def tt(d):
    return np.arange(int(d * SR)) / SR


def lowpass(x, fc):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    X *= 1 / (1 + (f / fc) ** 4)
    return np.fft.irfft(X, len(x))


def hipass(x, fc):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    X *= 1 - 1 / (1 + (f / max(fc, 1)) ** 4)
    return np.fft.irfft(X, len(x))


# ---------------- instruments ----------------
def pad(m, dur, att=1.8, rel=2.5):
    t = tt(dur + rel)
    s = np.zeros(len(t))
    for det in (-6, 0, 7):
        f = mtof(m) * 2 ** (det / 1200)
        for h in range(1, 6):
            s += np.sin(2 * np.pi * f * h * t + rng.rand() * 6) / h ** 1.7
    env = np.minimum(1, t / att) ** 2 * np.where(t > dur, np.exp(-(t - dur) / (rel / 3)), 1)
    return s * env * (1 + 0.07 * np.sin(2 * np.pi * 0.13 * t))


def piano(m, vel=0.5, dur=4.5):
    t = tt(dur)
    s = np.zeros(len(t))
    f0 = mtof(m)
    tau0 = 2.8 * (1.25 - (m - 36) / 70)
    for h in range(1, 10):
        fh = f0 * h * np.sqrt(1 + 0.00018 * h * h)
        if fh > 7000:
            break
        s += (1 / h ** 1.25) * np.sin(2 * np.pi * fh * t) * np.exp(-t / (tau0 / (1 + 0.55 * (h - 1))))
    s *= np.minimum(1, t / 0.005)
    w = int(2 + (1 - vel) * 6)
    return np.convolve(s, np.ones(w) / w, mode='same') * vel


def marimba(m, vel=0.5):
    t = tt(0.9)
    f = mtof(m)
    s = np.sin(2 * np.pi * f * t) * np.exp(-t / 0.28) + 0.35 * np.sin(2 * np.pi * f * 3.93 * t) * np.exp(-t / 0.05)
    return s * np.minimum(1, t / 0.002) * vel


def pluck_bass(m, vel=0.5):
    t = tt(0.7)
    f = mtof(m)
    s = sum(np.sin(2 * np.pi * f * h * t) / h ** 1.5 * np.exp(-t * (3 + h)) for h in range(1, 5))
    return s * np.minimum(1, t / 0.004) * vel


def shaker(vel=0.2):
    n = rng.randn(int(0.09 * SR))
    n = hipass(n, 5000)
    e = np.exp(-np.arange(len(n)) / (0.02 * SR))
    return n * e * vel


# ---------------- music plan ----------------
mus = Bus()
B_A = at('s04')  # night section ends
B_C = at('s06')  # playful building
Q = at('s08')  # thins out
Q2 = at('s09')  # question: stop
RIGHT = at('s10', 'right')
REB = at('s11', 'kept')  # rebuild
G0 = at('s16')
WAIT = at('s17', 'Oh')
TEND = at('s17', 'Late')
END0 = at('s20')
TITLE = at('s22')

Am = [45, 52, 57, 60, 64]
F = [41, 53, 57, 60, 65]
C = [48, 55, 60, 64, 67]
G = [43, 55, 59, 62, 67]
Em = [40, 52, 55, 59, 64]
Dm = [38, 50, 57, 60, 65]


def chords(t0, t1, prog, length, gain=0.06, pno=True, pvel=0.35, arp=None):
    t = t0
    i = 0
    while t < t1 - 0.3:
        d = min(length, t1 - t)
        ch = prog[i % len(prog)]
        for k, m in enumerate(ch):
            mus.add(t, pad(m, d + 0.3), (k - 2) * 0.15, gain if m < 50 else gain * 0.75)
        if pno:
            mus.add(t + 0.02, piano(ch[0] - 12 if ch[0] > 40 else ch[0], 0.45, 6), -0.1, 0.32)
            notes = arp or [2, 3, 4, 3]
            for j, idx in enumerate(notes):
                tj = t + 0.5 + j * d / (len(notes) + 0.5)
                if tj < t1 - 0.2 and rng.rand() > 0.12:
                    mus.add(tj + rng.rand() * 0.04, piano(ch[idx] + 12, pvel + rng.rand() * 0.08), (rng.rand() - 0.5) * 0.6, 0.26)
        t += length
        i += 1


# A: night, sparse and uneasy
chords(0.0, B_A + 0.2, [Am, F], 5.3, gain=0.05, pno=True, pvel=0.28, arp=[3, 4])
# B: warmth arrives
chords(B_A - 0.1, B_C + 0.1, [C, G], 3.8, gain=0.055, pno=True, pvel=0.36)
# C: playful build (marimba 8ths at 112 bpm + plucked bass + shaker)
bpm = 112
e8 = 60 / bpm / 2
prog = [C, F, G, C]
t = B_C
i = 0
while t < Q:
    bar = int((t - B_C) / (e8 * 8))
    ch = prog[bar % 4]
    pat = [0, 2, 1, 3, 2, 4, 3, 2]
    p = i % 8
    up = 1 + 0.5 * min(1, (t - B_C) / 8)  # energy grows
    mus.add(t, marimba(ch[pat[p] if pat[p] < 5 else 4] + 12, 0.35 * up), (p % 2 - 0.5) * 0.4, 0.5)
    if p in (0, 3, 6):
        mus.add(t, pluck_bass(ch[0] - 12 if ch[0] > 40 else ch[0], 0.6), 0, 0.55)
    if p % 2 == 1:
        mus.add(t, shaker(0.25), 0.3, 0.5)
    t += e8
    i += 1
chords(B_C, Q, [C, F, G, C], e8 * 8, gain=0.035, pno=False)
# D: thins: just a held low note under the question, then silence until collapse
mus.add(Q, pad(45, Q2 - Q + 1.5, att=0.4, rel=1.2), 0, 0.06)
mus.add(Q2, pad(33, RIGHT - Q2 + 0.8, att=0.6, rel=0.8), 0, 0.09)
# rebuild: hopeful
chords(REB - 0.3, G0, [F, C, G, Am], 4.4, gain=0.05, pno=True, pvel=0.38)
# F language part: add light marimba sparkle
for k in range(int((G0 - at('s15')) / 0.54)):
    tk = at('s15') + k * 0.54
    if rng.rand() > 0.3:
        mus.add(tk, marimba([72, 74, 76, 79, 81][k % 5], 0.18), (rng.rand() - 0.5) * 0.8, 0.5)
# G: night callback, warm rise
chords(G0, WAIT, [Am, F, C], 1.6, gain=0.05, pno=True, pvel=0.3, arp=[3])
# H: after the scratch, tender solo piano
chords(TEND - 0.2, END0, [F, C, Dm, G], 2.6, gain=0.028, pno=True, pvel=0.34, arp=[2, 4])
# I: full warm swell, resolve on title
chords(END0, TITLE, [F, G, Em, Am], 1.6, gain=0.06, pno=True, pvel=0.42)
for k, m in enumerate(C + [72, 76]):
    mus.add(TITLE - 0.1, pad(m, TOTAL - TITLE + 0.5, att=1.0, rel=2.0), (k - 3) * 0.12, 0.07 if m < 50 else 0.055)
mus.add(TITLE, piano(36, 0.6, 7), 0, 0.4)
for j, m in enumerate([60, 64, 67, 72, 76]):
    mus.add(TITLE + 0.15 + j * 0.22, piano(m + 12, 0.42, 5), (j - 2) * 0.2, 0.3)

# global section envelope (duck the question, stop for scratch)
tg = np.arange(N) / SR
env = np.ones(N)
env *= np.where((tg > Q2) & (tg < RIGHT), 0.55, 1)
env *= np.where((tg > WAIT) & (tg < TEND - 0.2), 0.0, 1)
env = np.convolve(env, np.ones(int(0.08 * SR)) / int(0.08 * SR), mode='same')
fade = np.minimum(1, tg / 1.5) * np.clip((TOTAL + 0.4 - tg) / 2.5, 0, 1) ** 1.5
mus.L *= env * fade
mus.R *= env * fade


# ---------------- sound effects ----------------
fx = Bus()


def whoosh(d=0.7, f0=400, f1=1800, vel=1.0):
    t = tt(d)
    n = rng.randn(len(t))
    out = np.zeros(len(t))
    seg = 1024
    for i in range(0, len(t), seg):
        u = i / len(t)
        fc = f0 + (f1 - f0) * np.sin(np.pi * u)
        out[i:i + seg] = lowpass(n[i:i + seg], fc)[:len(n[i:i + seg])] if len(n[i:i + seg]) > 8 else 0
    return out * np.sin(np.pi * t / d) ** 2 * vel


def pop_(f=520, vel=1.0):
    t = tt(0.16)
    fr = f * (1 + 0.8 * np.exp(-t / 0.02))
    return np.sin(2 * np.pi * np.cumsum(fr) / SR) * np.exp(-t / 0.04) * vel


def thunk(f=110, vel=1.0):
    t = tt(0.35)
    fr = f * (1 + 1.5 * np.exp(-t / 0.03))
    s = np.sin(2 * np.pi * np.cumsum(fr) / SR) * np.exp(-t / 0.09)
    s += lowpass(rng.randn(len(t)), 900) * np.exp(-t / 0.02) * 0.6
    return s * vel


def tick(f=2400, vel=1.0):
    t = tt(0.05)
    return np.sin(2 * np.pi * f * t) * np.exp(-t / 0.008) * vel


def chime(m=84, vel=1.0, d=2.5):
    t = tt(d)
    f = mtof(m)
    s = sum(np.sin(2 * np.pi * f * r * t) * np.exp(-t / (1.2 / r)) * a for r, a in ((1, 1), (2.76, 0.4), (5.4, 0.2)))
    return s * np.minimum(1, t / 0.003) * vel


def beep(f=1046, vel=1.0):
    t = tt(0.16)
    return np.sin(2 * np.pi * f * t) * np.minimum(1, t / 0.005) * np.exp(-t / 0.06) * vel


def scratch():
    t = tt(0.45)
    rate = np.where(t < 0.15, 1 - t / 0.15 * 1.8, -0.8 + (t - 0.15) / 0.3 * 0.8)
    n = rng.randn(len(t))
    ph = np.cumsum(220 * (1 + rate)) / SR
    s = np.sin(2 * np.pi * ph) * 0.5 + lowpass(n, 2500) * 0.6
    return s * np.sin(np.pi * t / 0.45) ** 0.5


def rumble(d=1.4):
    t = tt(d)
    s = lowpass(rng.randn(len(t)), 160) * 4 * np.exp(-t / 0.5)
    for k in range(9):
        tk = rng.rand() * d * 0.7
        i = int(tk * SR)
        th = thunk(90 + rng.rand() * 120, 0.6 + rng.rand() * 0.4)
        s[i:i + len(th)] += th[:len(s) - i]
    return s


def creak(d=0.8):
    t = tt(d)
    f = 140 + 40 * np.sin(2 * np.pi * 1.3 * t)
    s = np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR)) * 0.3
    s = lowpass(s, 1500)
    return s * np.sin(np.pi * t / d) * (0.6 + 0.4 * np.sin(2 * np.pi * 9 * t))


def page(vel=1.0):
    return hipass(whoosh(0.28, 1500, 5000), 1200) * vel


def footsteps(t0, n, gap=0.42, vel=0.6):
    for k in range(n):
        fx.add(t0 + k * gap, thunk(70, vel) * 0.7, 0.3 - k * 0.1, 1)


def S(t, sig, gain=1.0, pan=0.0):
    fx.add(t, sig, pan, gain)


# A
for k in range(10):
    S(0.15 + k * 1.05, beep(988, 0.35), 1, 0.4)  # monitor
S(0.0, tick(1800, 0.6)); S(0.5, tick(1500, 0.6)); S(1.0, tick(1800, 0.5))
S(at('s01', 'An') - 0.25, whoosh(0.9, 250, 900, 0.8))
footsteps(at('s02', 'doctor') - 0.1, 3)
S(at('s03', 'hardest') - 0.05, pop_(500, 0.7)); S(at('s03', 'conversation'), pop_(620, 0.7))
S(at('s03', 'all') - 0.1, whoosh(1.0, 300, 1500, 0.7))
# B
S(at('s04', 'Sahara') - 0.05, chime(88, 0.7))
S(at('s05', 'August') - 0.05, page(0.9))
S(at('s05', 'patients'), pop_(560, 0.8))
S(at('s05', 'none') - 0.1, whoosh(1.3, 600, 3000, 0.7))
# C
S(at('s06', 'built') - 0.05, pop_(700, 0.7))
for w in ('Syringe', 'Spinal', 'Sedation'):
    S(at('s07', w) - 0.03, thunk(160, 0.9))
t0 = at('s07', 'Sedation') + 0.6
for k in range(9):
    S(t0 + k * 0.13, thunk(170 + k * 12, 0.55), 1, (k % 2 - 0.5) * 0.6)
S(at('s07', 'Twenty'), chime(91, 0.5, 1.2))
S(at('s07', 'week'), pop_(800, 0.7))
# D
S(at('s08', 'medical') - 0.05, pop_(600, 0.8))
S(at('s09', 'Why') - 0.05, pop_(450, 0.6))
S(at('s09', 'install'), creak(0.9), 0.8)
S(at('s10', 'right') - 0.05, rumble(1.6), 1.0)
S(at('s10', 'not') - 0.05, page(0.8))
S(at('s11', 'cut') - 0.05, whoosh(0.8, 300, 2000, 1.0))
S(at('s11', 'thing') - 0.1, chime(84, 0.6))
S(at('s11', 'talk') - 0.1, whoosh(0.9, 400, 2500, 0.8))
# E
S(at('s12', 'records') - 0.03, beep(1318, 0.6))
t0 = at('s12', 'thirteen')
for k in range(13):
    S(t0 + k * 0.1, tick(2600 + k * 60, 0.5), 1, (k % 3 - 1) * 0.4)
S(at('s13', 'no') - 0.03, thunk(120, 1.0), 1)
S(at('s13', 'leads') - 0.05, pop_(520, 0.6))
S(at('s14', 'reads'), tick(2200, 0.7)); S(at('s14', 'edits'), tick(2400, 0.7)); S(at('s14', 'signs'), whoosh(0.5, 2000, 4000, 0.5))
S(at('s14', 'signs') + 0.55, tick(1200, 1.0)); S(at('s14', 'signs') + 0.6, thunk(300, 0.4))
# F
for w in ('English', 'Hindi', 'Telugu', 'Urdu', 'Kannada'):
    S(at('s15', w) - 0.03, pop_(480 + 60 * len(w), 0.7), 1, rng.rand() - 0.5)
S(at('s15', 'mixed') - 0.03, pop_(900, 0.7))
# G
S(at('s16', 'Back') - 0.1, whoosh(0.8, 300, 1200, 0.6))
S(at('s16', 'scan'), beep(1568, 0.7)); S(at('s16', 'scan') + 0.12, beep(2093, 0.5))
S(at('s16', 'knows'), chime(84, 0.7))
# H
S(at('s17', 'Oh') - 0.08, scratch(), 1.0)
S(at('s17', 'hit') - 0.03, chime(96, 0.6, 1.5))
S(at('s18', 'Nobody') - 0.2, whoosh(1.0, 300, 1100, 0.6))
S(at('s19', 'check') - 0.03, pop_(560, 0.6))
S(at('s19', 'someone') - 0.03, pop_(660, 0.7))
# I
for k, w in enumerate(('Three', 'months')):
    S(at('s20', w), page(0.7))
t0 = at('s20', 'Six')
for k in range(16):
    S(t0 + k * 0.06, tick(2000 + k * 40, 0.35), 1, (k % 2 - 0.5) * 0.5)
S(at('s21', 'Play') - 0.03, pop_(700, 0.8))
S(at('s22') - 0.05, chime(84, 0.8, 3.5)); S(at('s22') - 0.05, chime(91, 0.4, 3.5))

def write(bus, name):
    L, R = bus.L[:int(TOTAL * SR)], bus.R[:int(TOTAL * SR)]
    L, R = hipass(L, 35), hipass(R, 35)
    pk = max(np.abs(L).max(), np.abs(R).max()) or 1
    L, R = L / pk * 0.89, R / pk * 0.89
    pcm = np.empty(len(L) * 2, dtype=np.int16)
    pcm[0::2] = np.int16(L * 32767)
    pcm[1::2] = np.int16(R * 32767)
    with wave.open(os.path.join(HERE, name), 'wb') as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
    print('wrote', name)


# light reverb on music
def make_ir(seed, length=2.4):
    r = np.random.RandomState(seed)
    t = tt(length)
    nz = r.randn(len(t))
    ir = nz * np.exp(-t / 0.5) * 0.3 + np.convolve(nz, np.ones(24) / 24, mode='same') * np.exp(-t / 1.0) * 1.5
    return ir / np.sqrt(np.sum(ir ** 2))


def conv(x, ir):
    n = 1
    while n < len(x) + len(ir):
        n <<= 1
    return np.fft.irfft(np.fft.rfft(x, n) * np.fft.rfft(ir, n), n)[:len(x)]


mus.L = lowpass(mus.L, 6000); mus.R = lowpass(mus.R, 6000)
mus.L += conv(mus.L, make_ir(1)) * 0.45
mus.R += conv(mus.R, make_ir(2)) * 0.45
fx.L += conv(fx.L, make_ir(3)) * 0.15
fx.R += conv(fx.R, make_ir(4)) * 0.15
write(mus, 'music.wav')
write(fx, 'sfx.wav')
