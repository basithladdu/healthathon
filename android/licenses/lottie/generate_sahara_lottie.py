"""Generates Sahara's Lottie (bodymovin 5.7) animations as plain JSON. Original geometry, Sahara palette, CC0."""
import json, math, os, sys

OUT = sys.argv[1]
W = H = 120
FR = 30

def hexc(h, a=1):
    h = h.lstrip('#'); return [round(int(h[i:i+2], 16) / 255, 4) for i in (0, 2, 4)] + [a]

C = dict(success='#188038', successWash='#D4EFDC', teal='#00847A', tealWash='#CDF3EE', rose='#D81B60', roseWash='#FFDCE8',
         rust='#D2390B', rustWash='#FFE1D3', gold='#FFB300', goldWash='#FFF0C2', indigo='#2447D8', indigoWash='#DCE3FF',
         plum='#C2178E', plumWash='#FCDDF1', marigold='#E36D00', leaf='#28842E', ink='#382E29', surface='#FFFDF9',
         line='#E6D8CC', white='#FFFFFF', edge='#9C887B', band='#521A0C')

EASE_O = 0.33; EASE_I = 0.0  # calm ease-out / ease-in (no overshoot, no bounce)

def static(v): return {"a": 0, "k": v}

def anim(keys, dims=1):
    """keys: list of (frame, value). Values are scalars or lists. Smooth ease in/out between keys."""
    out = []
    for n, (t, v) in enumerate(keys):
        s = v if isinstance(v, list) else [v]
        k = {"t": t, "s": s}
        if n < len(keys) - 1:
            d = len(s)
            k["i"] = {"x": [0.33] * d, "y": [1] * d}
            k["o"] = {"x": [0.42] * d, "y": [0] * d}
        out.append(k)
    return {"a": 1, "k": out}

def ks(p=(60, 60), a=(0, 0), s=100, r=0, o=100):
    def wrap(v, f):
        return v if isinstance(v, dict) else static(f(v))
    return {"o": wrap(o, lambda v: v), "r": wrap(r, lambda v: v), "p": wrap(p, lambda v: [v[0], v[1], 0]),
            "a": wrap(a, lambda v: [v[0], v[1], 0]), "s": wrap(s, lambda v: [v, v, 100])}

def tr(p=(0, 0), a=(0, 0), s=100, r=0, o=100):
    def wrap(v, f):
        return v if isinstance(v, dict) else static(f(v))
    return {"ty": "tr", "p": wrap(p, list), "a": wrap(a, list), "s": wrap(s, lambda v: [v, v]), "r": wrap(r, lambda v: v),
            "o": wrap(o, lambda v: v), "sk": static(0), "sa": static(0), "nm": "t"}

def ellipse(w, h=None, p=(0, 0)): return {"ty": "el", "p": static(list(p)), "s": static([w, h or w]), "d": 1, "nm": "e"}
def rect(w, h, r=0, p=(0, 0)): return {"ty": "rc", "p": static(list(p)), "s": static([w, h]), "r": static(r), "d": 1, "nm": "r"}
def fill(c, o=100): return {"ty": "fl", "c": static(hexc(C.get(c, c))), "o": o if isinstance(o, dict) else static(o), "r": 1, "nm": "f"}
def stroke(c, w, o=100): return {"ty": "st", "c": static(hexc(C.get(c, c))), "o": static(o), "w": static(w), "lc": 2, "lj": 2, "ml": 4, "nm": "s"}
def trim(e, s=None): return {"ty": "tm", "s": s or static(0), "e": e, "o": static(0), "m": 1, "nm": "tm"}

def path(points, closed=False, ins=None, outs=None):
    n = len(points)
    return {"ty": "sh", "d": 1, "nm": "p", "ks": static({"i": ins or [[0, 0]] * n, "o": outs or [[0, 0]] * n, "v": [list(p) for p in points], "c": closed})}

def group(items, t=None): return {"ty": "gr", "nm": "g", "it": items + [t or tr()]}

def layer(ind, shapes, op, k=None, ip=0, nm="l"):
    return {"ddd": 0, "ind": ind, "ty": 4, "nm": nm, "sr": 1, "ks": k or ks(), "ao": 0, "shapes": shapes, "ip": ip, "op": op, "st": 0, "bm": 0}

def comp(name, op, layers):
    # Layers are listed top first; give them indices in that order.
    for n, l in enumerate(layers): l["ind"] = n + 1
    return {"v": "5.7.4", "fr": FR, "ip": 0, "op": op, "w": W, "h": H, "nm": name, "ddd": 0, "assets": [], "layers": layers}

def heart_path(size):
    """A symmetric heart centred on 0,0: vertices bottom, left, top dip, right; tangents relative to each vertex."""
    s = size / 2
    v = [[0, s * .95], [-s, -s * .15], [0, -s * .45], [s, -s * .15]]
    i = [[s * .45, -s * .35], [-s * .05, s * .55], [-s * .5, -s * .65], [s * .02, -s * .62]]
    o = [[-s * .45, -s * .35], [0, -s * .62], [s * .5, -s * .65], [s * .05, s * .55]]
    return path(v, True, i, o)

anims = {}

# 1. Success tick: green disc grows in, white check draws itself. Once, 1.2 s.
anims["lottie_success"] = comp("success", 40, [
    layer(0, [group([path([(-17, 1), (-5, 13), (18, -11)]), trim(anim([(10, 0), (28, 100)])), stroke("white", 9)])], 40),
    layer(0, [group([ellipse(84), fill("success")])], 40,
          ks(s=anim([(0, [0, 0, 100]), (14, [100, 100, 100])]))),
    layer(0, [group([ellipse(104), fill("successWash")])], 40, ks(s=anim([(4, [0, 0, 100]), (20, [100, 100, 100])]), o=anim([(4, 0), (14, 100)]))),
])

# 2. Breathing circle: soft rings swell for 2 s and settle for 2 s. Loops. Reads on a coloured band (white, translucent).
anims["lottie_breathe"] = comp("breathe", 120, [
    layer(0, [group([ellipse(64), fill("white", 55)])], 120, ks(s=anim([(0, [86, 86, 100]), (60, [100, 100, 100]), (120, [86, 86, 100])]))),
    layer(0, [group([ellipse(96), fill("white", 28)])], 120, ks(s=anim([(0, [80, 80, 100]), (60, [112, 112, 100]), (120, [80, 80, 100])]))),
    layer(0, [group([ellipse(116), stroke("white", 2, 40)])], 120, ks(s=anim([(0, [76, 76, 100]), (60, [100, 100, 100]), (120, [76, 76, 100])]), o=anim([(0, 30), (60, 100), (120, 30)]))),
])

# 3. Heart beat: two soft beats then a long rest. Loops (2.4 s).
anims["lottie_heart"] = comp("heart", 72, [
    layer(0, [group([heart_path(70), fill("rose")])], 72,
          ks(s=anim([(0, [100, 100, 100]), (6, [114, 114, 100]), (12, [100, 100, 100]), (18, [110, 110, 100]), (26, [100, 100, 100]), (72, [100, 100, 100])]))),
    layer(0, [group([heart_path(70), fill("roseWash")])], 72,
          ks(s=anim([(4, [100, 100, 100]), (22, [150, 150, 100]), (72, [150, 150, 100])]), o=anim([(4, 90), (24, 0), (72, 0)]))),
])

# 4. Pill bounce: a two-tone capsule hops gently, its shadow shrinking under it. Loops (2 s).
pill = [group([rect(26, 22, 11, (-13, 0)), rect(13, 22, 0, (-6.5, 0)), fill("plum")]),
        group([rect(26, 22, 11, (13, 0)), rect(13, 22, 0, (6.5, 0)), fill("goldWash")]),
        group([rect(52, 22, 11), stroke("plum", 3)]),
        group([path([(-14, -5), (-6, -5)]), stroke("white", 3, 70)])]
anims["lottie_pill"] = comp("pill", 60, [
    layer(0, pill, 60, ks(p=anim([(0, [60, 66]), (15, [60, 44]), (30, [60, 66]), (60, [60, 66])]), r=anim([(0, -28), (15, -14), (30, -28), (60, -28)]))),
    layer(0, [group([ellipse(56, 10), fill("ink", 14)])], 60, ks(p=(60, 96), s=anim([(0, [100, 100, 100]), (15, [70, 70, 100]), (30, [100, 100, 100]), (60, [100, 100, 100])]))),
])

# 5. Document fly-in: a page rises in and its lines write themselves. Once (1.3 s).
doc_lines = []
for n, (y, w) in enumerate([(-14, 30), (-4, 34), (6, 26), (16, 20)]):
    doc_lines.append(group([path([(-17, y), (-17 + w, y)]), trim(anim([(12 + n * 4, 0), (24 + n * 4, 100)])), stroke("indigo" if n == 0 else "edge", 4)]))
anims["lottie_document"] = comp("document", 40, [
    layer(0, doc_lines + [group([rect(54, 66, 6), fill("surface")]), group([rect(54, 66, 6), stroke("indigo", 3)])], 40,
          ks(p=anim([(0, [74, 92]), (14, [60, 60]), (40, [60, 60])]), r=anim([(0, 14), (14, 0), (40, 0)]), o=anim([(0, 0), (8, 100)]))),
    layer(0, [group([ellipse(96), fill("indigoWash")])], 40, ks(s=anim([(0, [60, 60, 100]), (16, [100, 100, 100])]), o=anim([(0, 0), (10, 100)]))),
])

# 6. Map pin drop: the pin falls in, lands, and a ring spreads under it. Once (1.6 s).
pin = path([(0, 30), (-20, -2), (0, -30), (20, -2)], True,
           [[8, 12], [0, 14], [-12, 0], [0, -12]], [[-8, 12], [0, -12], [12, 0], [0, 14]])
anims["lottie_pin"] = comp("pin", 50, [
    layer(0, [group([ellipse(14, p=(0, -4)), fill("white")]), group([pin, fill("rust")])], 50,
          ks(p=anim([(0, [60, 10]), (14, [60, 56]), (19, [60, 51]), (24, [60, 56]), (50, [60, 56])]), o=anim([(0, 0), (6, 100)]))),
    layer(0, [group([ellipse(30, 10), stroke("rust", 3)])], 50, ks(p=(60, 88), s=anim([(14, [40, 40, 100]), (40, [190, 190, 100])]), o=anim([(14, 100), (40, 0)]))),
    layer(0, [group([ellipse(22, 7), fill("ink", 18)])], 50, ks(p=(60, 88), s=anim([(0, [30, 30, 100]), (14, [100, 100, 100])]))),
])

# 7. Waving hand: an open palm sways from the wrist, twice, then rests. Loops (2.4 s).
hand = [group([rect(10, 30, 5, (-15, -18)), rect(10, 36, 5, (-4, -22)), rect(10, 34, 5, (7, -20)), rect(9, 26, 4.5, (17, -14)),
               rect(40, 34, 12, (1, 2)), fill("marigold")]),
        group([rect(10, 26, 5, (0, 0)), fill("marigold")], tr(p=(-20, 4), r=-40)),
        group([rect(16, 14, 3, (1, 24)), fill("rust")])]
anims["lottie_wave"] = comp("wave", 72, [
    layer(0, hand, 72, ks(p=(60, 80), a=(0, 22), r=anim([(0, 0), (9, 16), (18, -10), (27, 16), (36, -6), (44, 0), (72, 0)]))),
    layer(0, [group([ellipse(100), fill("goldWash")])], 72),
])

# 8. Loading dots: three dots rise and brighten in turn. Loops (1.2 s).
dots = []
for n, x in enumerate([-26, 0, 26]):
    d = n * 6
    dots.append(layer(0, [group([ellipse(18), fill(["rust", "marigold", "gold"][n])])], 36,
                      ks(p=anim([(d, [60 + x, 60]), (d + 9, [60 + x, 50]), (d + 18, [60 + x, 60]), (36, [60 + x, 60])]),
                         s=anim([(d, [80, 80, 100]), (d + 9, [110, 110, 100]), (d + 18, [80, 80, 100]), (36, [80, 80, 100])]))))
anims["lottie_loading"] = comp("loading", 36, dots)

# 9. Confetti: small pieces burst from the centre, drift down and fade. Once (2 s). For "all doses taken today" only.
pieces = []
colours = ["rust", "teal", "gold", "plum", "leaf", "indigo", "rose", "marigold"]
for n in range(16):
    ang = (n / 16) * 2 * math.pi + (0.2 if n % 2 else 0)
    dist = 38 + (n % 3) * 8
    ex, ey = 60 + math.cos(ang) * dist, 60 + math.sin(ang) * dist
    shape = rect(8, 5, 1) if n % 2 == 0 else ellipse(6)
    pieces.append(layer(0, [group([shape, fill(colours[n % len(colours)])])], 60,
                        ks(p=anim([(0, [60, 60]), (20, [round(ex, 1), round(ey, 1)]), (60, [round(ex, 1), round(ey + 16, 1)])]),
                           r=anim([(0, 0), (60, 200 if n % 2 else -160)]), o=anim([(0, 100), (40, 100), (60, 0)]),
                           s=anim([(0, [40, 40, 100]), (12, [120, 120, 100]), (60, [100, 100, 100])]))))
pieces.append(layer(0, [group([ellipse(30), fill("successWash")])], 60, ks(s=anim([(0, [0, 0, 100]), (18, [260, 260, 100])]), o=anim([(0, 100), (30, 0)]))))
anims["lottie_confetti"] = comp("confetti", 60, pieces)

# 10. Hug: two rounded figures lean together and a small heart rises. Once (1.6 s).
def figure(colour, x, lean):
    return layer(0, [group([ellipse(22, p=(0, -26)), fill(colour)]), group([rect(34, 40, 17, (0, 8)), fill(colour)])], 48,
                 ks(p=anim([(0, [60 + x, 92]), (16, [60 + x * .75, 92]), (48, [60 + x * .75, 92])]), a=(0, 28), r=anim([(0, 0), (16, lean), (48, lean)])))
small_heart = layer(0, [group([heart_path(20), fill("rose")])], 48,
                    ks(p=anim([(14, [60, 40]), (34, [60, 16]), (48, [60, 16])]), s=anim([(14, [0, 0, 100]), (26, [100, 100, 100])]), o=anim([(14, 0), (20, 100)])))
anims["lottie_hug"] = comp("hug", 48, [small_heart, figure("teal", -22, 8), figure("marigold", 22, -8),
                                       layer(0, [group([ellipse(104), fill("tealWash")])], 48)])

# 11. Calendar flip: the top page folds away and the new day settles in. Once (1.3 s).
def cal_page(colour, rows):
    items = []
    for n in range(rows):
        for m in range(3):
            items.append(group([rect(8, 6, 2, (-14 + m * 14, -2 + n * 12)), fill(colour)]))
    return items
anims["lottie_calendar"] = comp("calendar", 40, [
    layer(0, [group([rect(6, 14, 3, (-14, -30)), fill("ink")]), group([rect(6, 14, 3, (14, -30)), fill("ink")])], 40, ks()),
    layer(0, [group([rect(64, 20, 6), fill("rust")])], 40, ks(p=(60, 38))),
    # Old page: folds up and away.
    layer(0, cal_page("edge", 3) + [group([rect(64, 50, 6), fill("surface")])], 40,
          ks(p=(60, 52), a=(0, -25), s=anim([(0, [100, 100, 100]), (16, [100, 0, 100])]), o=anim([(0, 100), (14, 100), (16, 0)]))),
    # New page under it, with today's square highlighted.
    layer(0, [group([rect(8, 6, 2, (14, 10)), fill("rust")]), group([rect(14, 12, 3, (14, 10)), fill("rustWash")])] + cal_page("edge", 3) + [group([rect(64, 50, 6), fill("surface")])], 40,
          ks(p=(60, 77), o=anim([(8, 0), (18, 100)]))),
    layer(0, [group([rect(68, 74, 8), stroke("rust", 3)])], 40, ks(p=(60, 65))),
    layer(0, [group([ellipse(104), fill("rustWash")])], 40, ks(o=static(60))),
])

# 12. Offline cloud: a cloud floats gently; a slash draws across it once per cycle. Loops (3 s).
cloud = [group([ellipse(34, p=(-16, 6)), ellipse(42, p=(4, -4)), ellipse(30, p=(22, 8)), rect(64, 22, 11, (2, 12)), fill("indigoWash")]),
         group([ellipse(34, p=(-16, 6)), ellipse(42, p=(4, -4)), ellipse(30, p=(22, 8)), stroke("indigo", 3)])]
anims["lottie_offline"] = comp("offline", 90, [
    layer(0, [group([path([(-26, -26), (28, 28)]), trim(anim([(10, 0), (28, 100), (90, 100)])), stroke("rust", 5)])], 90, ks(p=anim([(0, [60, 62]), (45, [60, 57]), (90, [60, 62])]))),
    layer(0, cloud, 90, ks(p=anim([(0, [58, 62]), (45, [58, 57]), (90, [58, 62])]))),
])

os.makedirs(OUT, exist_ok=True)
total = 0
for name, data in anims.items():
    text = json.dumps(data, separators=(",", ":"))
    with open(os.path.join(OUT, name + ".json"), "w") as f: f.write(text)
    total += len(text); print(f"{name}.json {len(text)} B")
print("total", total)
