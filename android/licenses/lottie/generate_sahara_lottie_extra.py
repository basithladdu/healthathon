"""Hand-authored Sahara Lottie files (plain shapes, no expressions, no images). Usage: python -I gen.py OUTDIR"""
import json, math, sys

def hexc(h, a=1):
    h = h.lstrip('#'); return [round(int(h[i:i+2], 16) / 255, 4) for i in (0, 2, 4)] + [a]

E_IN = {"x": [0.33], "y": [1]}; E_OUT = {"x": [0.42], "y": [0]}
def kf(frames):
    """frames: list of (t, value_list). Animated property with eased keys."""
    ks = []
    for i, (t, v) in enumerate(frames):
        k = {"t": t, "s": v}
        if i < len(frames) - 1:
            n = len(v); k["i"] = {"x": [0.33] * n, "y": [1] * n}; k["o"] = {"x": [0.42] * n, "y": [0] * n}
        ks.append(k)
    return {"a": 1, "k": ks}
def st(v): return {"a": 0, "k": v}

def tr(): return {"ty": "tr", "p": st([0, 0]), "a": st([0, 0]), "s": st([100, 100]), "r": st(0), "o": st(100), "sk": st(0), "sa": st(0), "nm": "t"}

def layer(ind, nm, shapes, op, o=None, r=None, p=None, s=None):
    return {"ddd": 0, "ind": ind, "ty": 4, "nm": nm, "sr": 1,
            "ks": {"o": o or st(100), "r": r or st(0), "p": p or st([60, 60, 0]), "a": st([0, 0, 0]), "s": s or st([100, 100, 100])},
            "ao": 0, "shapes": shapes, "ip": 0, "op": op, "st": 0, "bm": 0}

def ellipse_group(w, h, fill=None, stroke=None, sw=0, pos=(0, 0), rot=0):
    it = [{"ty": "el", "p": st(list(pos)), "s": st([w, h]), "d": 1, "nm": "e"}]
    if fill: it.append({"ty": "fl", "c": st(fill), "o": st(100), "r": 1, "nm": "f"})
    if stroke: it.append({"ty": "st", "c": st(stroke), "o": st(100), "w": st(sw), "lc": 2, "lj": 2, "ml": 4, "nm": "s"})
    t = tr(); t["r"] = st(rot)
    it.append(t)
    return {"ty": "gr", "nm": "g", "it": it}

def comp(nm, op, layers):
    return {"v": "5.7.4", "fr": 30, "ip": 0, "op": op, "w": 120, "h": 120, "nm": nm, "ddd": 0, "assets": [], "layers": layers}

TEAL, GOLD, LEAF, TEALW, GOLDW, LEAFW = "3A7D75", "E6C168", "4F7F4B", "DDEEEA", "FAF0D2", "E0EEDC"

def bloom():
    """Check-in saved: six soft petals open from the centre and fade, with one ring spreading. Ends empty (it is an overlay)."""
    op = 45; layers = []; cols = [TEAL, GOLD, LEAF] * 2
    for i in range(6):
        a = math.radians(i * 60 - 90); d = 30
        x, y = 60 + d * math.cos(a), 60 + d * math.sin(a)
        petal = ellipse_group(14, 24, fill=hexc(cols[i]), rot=i * 60)
        layers.append(layer(i + 1, f"petal{i}", [petal], op,
            o=kf([(0, [0]), (8, [90]), (30, [80]), (44, [0])]),
            p=kf([(0, [60, 60, 0]), (22, [round(x, 2), round(y, 2), 0])]),
            s=kf([(0, [20, 20, 100]), (18, [100, 100, 100]), (44, [80, 80, 100])])))
    ring = ellipse_group(40, 40, stroke=hexc(TEAL), sw=3)
    layers.append(layer(7, "ring", [ring], op, o=kf([(0, [70]), (36, [0])]), s=kf([(0, [40, 40, 100]), (36, [260, 260, 100])])))
    core = ellipse_group(26, 26, fill=hexc(GOLDW))
    layers.append(layer(8, "core", [core], op, o=kf([(0, [0]), (6, [100]), (34, [100]), (44, [0])]), s=kf([(0, [0, 0, 100]), (12, [110, 110, 100]), (20, [100, 100, 100])])))
    return comp("bloom", op, layers)

def path_group(verts, closed, fill=None, stroke=None, sw=0, ins=None, outs=None):
    n = len(verts)
    it = [{"ty": "sh", "d": 1, "nm": "p", "ks": st({"i": ins or [[0, 0]] * n, "o": outs or [[0, 0]] * n, "v": verts, "c": closed})}]
    if fill: it.append({"ty": "fl", "c": st(fill), "o": st(100), "r": 1, "nm": "f"})
    if stroke: it.append({"ty": "st", "c": st(stroke), "o": st(100), "w": st(sw), "lc": 2, "lj": 2, "ml": 4, "nm": "s"})
    it.append(tr())
    return {"ty": "gr", "nm": "g", "it": it}

def leaf_shape(sign):
    # A leaf from the stem point (0,0) out to (sign*26,-14), curved both sides.
    tip = [sign * 26, -14]
    return path_group([[0, 0], tip], True, fill=hexc(LEAF),
                      ins=[[sign * 4, -12], [sign * -8, 8]], outs=[[sign * 12, 4], [sign * -12, -8]])

def sprout():
    """Empty states: a small sprout on a soft ground disc; it sways once left and right and returns to rest (seamless to repeat)."""
    op = 90
    ground = ellipse_group(70, 14, fill=hexc(LEAFW), pos=(0, 0))
    g = layer(1, "ground", [ground], op, p=st([60, 98, 0]))
    stem = path_group([[0, 0], [0, -40]], False, stroke=hexc(LEAF), sw=4, ins=[[0, 0], [3, 12]], outs=[[-3, -12], [0, 0]])
    leaves = [{"ty": "gr", "nm": "l", "it": [leaf_shape(-1)["it"][0], leaf_shape(-1)["it"][1], {**tr(), "p": st([0, -26])}]},
              {"ty": "gr", "nm": "r", "it": [leaf_shape(1)["it"][0], {"ty": "fl", "c": st(hexc(TEAL)), "o": st(100), "r": 1, "nm": "f"}, {**tr(), "p": st([0, -38])}]}]
    bud = ellipse_group(10, 10, fill=hexc(GOLD), pos=(0, -42))
    plant = layer(2, "plant", [bud] + leaves + [stem], op, p=st([60, 96, 0]),
                  r=kf([(0, [0]), (22, [-5]), (45, [0]), (67, [4]), (89, [0])]),
                  s=kf([(0, [100, 100, 100]), (45, [104, 104, 100]), (89, [100, 100, 100])]))
    # The layer pivots at its anchor (the stem foot), so the sway bends from the ground.
    halo = ellipse_group(92, 92, fill=hexc(GOLDW))
    h = layer(3, "halo", [halo], op, p=st([60, 58, 0]), o=kf([(0, [60]), (45, [100]), (89, [60])]))
    return comp("sprout", op, [plant, g, h])

out = sys.argv[1]
for name, data in (("lottie_bloom.json", bloom()), ("lottie_sprout.json", sprout())):
    with open(f"{out}/{name}", "w", encoding="utf-8") as f: json.dump(data, f, separators=(",", ":"))
    print(name, len(json.dumps(data, separators=(",", ":"))))
