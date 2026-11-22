"""Map the current narration (words.json) onto the timeline the scenes were built for (base_words.json).
Anchors: every sentence's first-word start and last-word end, plus every word that matches between the two
versions of a sentence. Writes src/journey/warp.json = [[new_t, base_t], ...] (monotonic)."""
import difflib, json, os, re

HERE = os.path.dirname(os.path.abspath(__file__))
J = lambda p: json.load(open(os.path.join(HERE, '..', 'src', 'journey', p), encoding='utf-8'))
new, base = J('words.json'), J('base_words.json')
norm = lambda s: re.sub(r'[^a-z0-9]', '', s.lower())
ids = []
[ids.append(w['id']) for w in base['words'] if w['id'] not in ids]
pairs = [(0.0, 0.0, 1)]
for i in ids:
    a = [w for w in new['words'] if w['id'] == i]
    b = [w for w in base['words'] if w['id'] == i]
    if not a or not b:
        raise SystemExit('sentence missing: ' + i)
    pairs.append((a[0]['s'], b[0]['s'], 1))
    sm = difflib.SequenceMatcher(a=[norm(w['w']) for w in a], b=[norm(w['w']) for w in b], autojunk=False)
    for blk in sm.get_matching_blocks():
        for k in range(blk.size):
            wa, wb = a[blk.a + k], b[blk.b + k]
            pairs.append((wa['s'], wb['s'], 0))
    pairs.append((a[-1]['e'], b[-1]['e'], 1))
pairs.append((new['total'], base['total'], 1))
pairs.sort()
out = []
# word anchors at least GAP s apart and away from sentence anchors, so motion speed stays smooth
GAP = 0.9
hard = [p for p in pairs if p[2]]
for p in pairs:  # keep strictly increasing in both axes
    if not p[2]:
        if min(abs(p[0] - h[0]) for h in hard) < GAP / 2 or (out and p[0] - out[-1][0] < GAP):
            continue
    if not out or (p[0] > out[-1][0] + 1e-3 and p[1] > out[-1][1] + 1e-3):
        out.append([round(p[0], 3), round(p[1], 3)])
json.dump(out, open(os.path.join(HERE, '..', 'src', 'journey', 'warp.json'), 'w'))
sl = [(out[k + 1][1] - out[k][1]) / (out[k + 1][0] - out[k][0]) for k in range(len(out) - 1)]
print(len(out), 'anchors; base/new speed min %.2f max %.2f' % (min(sl), max(sl)))
