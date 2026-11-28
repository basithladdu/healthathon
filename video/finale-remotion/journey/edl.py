"""Edit list: film time (words.json, the recorded narration) -> scene timeline (base_words.json) spans or named inserts.
Writes src/journey/edl.json = [{n0,n1,b0,b1} | {n0,n1,insert,b0}]."""
import json, os, re
HERE = os.path.dirname(os.path.abspath(__file__))
J = lambda p: json.load(open(os.path.join(HERE, '..', 'src', 'journey', p), encoding='utf-8'))
new, base = J('words.json'), J('base_words.json')
norm = lambda s: re.sub(r"[^a-z0-9']", '', s.lower())
def look(d, i, w=None, nth=0):
    ws = [x for x in d['words'] if x['id'] == i]
    if not ws: raise SystemExit('no sentence ' + i)
    if w is None: return ws[0]['s']
    h = [x for x in ws if norm(x['w']) == norm(w)]
    if not h: raise SystemExit('no word %s in %s' % (w, i))
    return h[min(nth, len(h) - 1)]['s']
N = lambda i, w=None, nth=0: look(new, i, w, nth)
B = lambda i, w=None, nth=0: look(base, i, w, nth)
BE = lambda i: [x for x in base['words'] if x['id'] == i][-1]['e']
BT = base['total']
# (film cue, target)   target = (b0, b1) or 'insert name'
CUT = [
 (('m01',), (0, 2.73)), (('m02',), (2.73, 5.71)), (('m03',), (5.71, 10.51)),
 (('m04',), 'evidence'),
 (('m05',), (10.51, B('s04', 'With') - 0.1)), (('m05', "It's"), 'intro'),
 (('m06',), (B('s04', 'With') - 0.1, B('s04a') - 0.15)), (('m06a',), (B('s04a') - 0.15, B('s04b') - 0.1)),
 (('m06b',), (B('s04b') - 0.1, B('s04b', 'Dr', 1) - 0.05)), (('m06c',), (B('s04b', 'Dr', 1) - 0.05, B('s04b', 'and', 1) - 0.05)),
 (('m06d',), (B('s04b', 'and', 1) - 0.05, 22.2)),
 (('m07',), (22.2, B('s05', 'patients') - 0.1)), (('m07a',), (B('s05', 'patients') - 0.1, 28.42)),
 (('m08',), (28.42, 38.24)),
 (('m09',), (38.24, 45.0)), (('m10',), (45.0, 52.2)),
 (('m11',), (56.9, 61.06)), (('m11a',), (61.06, B('s12', 'records') - 0.1)),
 (('m12',), (B('s12', 'records') - 0.1, B('s12', 'and') - 0.05)), (('m12a',), 'sarvam'),
 (('m12b',), (B('s12', 'and') - 0.05, B('s12', 'thirteen') - 0.15)), (('m12b', 'It'), (B('s12', 'thirteen') - 0.15, BE('s12') + 0.2)), (('m12b', 'taken'), 'guidelines'),
 (('m13',), 'aidoc'),
 (('m14',), (B('s14') - 0.2, 83.21)),
 (('mLegal',), 'legal'),
 (('m15',), (83.21, B('s14b') - 0.1)), (('m16',), (B('s14b') - 0.1, B('s14b', 'marked') - 0.1)),
 (('m16a',), 'thermo'),
 (('m16b',), (B('s14b', 'marked') - 0.1, B('s14c') - 0.1)),
 (('m17',), 'journal'),
 (('m18',), (B('s14c') - 0.1, B('s14d') - 0.1)), (('m19',), (B('s14d') - 0.1, B('s14e') - 0.1)),
 (('m20',), 'visit'), (('m21',), 'family'), (('m22',), 'sos'),
 (('m23',), (B('s14e') - 0.1, 104.41)),
 (('m24',), 'community'),
 (('m25',), (104.41, 113.67)), (('m26',), (113.67, 118.52)),
 (('m27',), (118.52, B('s18') - 0.13)), (('m28',), (B('s18') - 0.13, B('s19') - 0.13)), (('m29',), (B('s19') - 0.13, 128.81)),
 (('m30',), (128.81, B('s20', 'Almost') - 0.2)),
 (('m30a',), 'work'),
 (('m31',), (B('s20', 'Almost') - 0.2, B('s20', 'A') - 0.1)),
 (('m31', 'Each'), 'commit'),
 (('m32',), 'stack'),
 (('m33',), (52.2, 56.9)),
 (('m34',), (B('s21') - 0.2, B('s22') - 0.2)), (('m35',), (B('s22') - 0.2, BT)),
]
ends = {}
for w in new['words']: ends[w['id']] = w['e']
starts, prev_end = [], 0.0
allw = new['words']
for cue, _ in CUT:
    s = N(*cue)
    before = [w['e'] for w in allw if w['e'] <= s + 1e-6]
    pe = before[-1] if before else 0.0
    starts.append(0.0 if not starts else round(s - min(0.3, (s - pe) / 2), 3))
out = []
for k, (cue, tg) in enumerate(CUT):
    n0 = starts[k]; n1 = starts[k + 1] if k + 1 < len(CUT) else new['total']
    if isinstance(tg, str):
        hold = next((o['b1'] for o in reversed(out) if 'b1' in o), 0)
        out.append({'n0': n0, 'n1': n1, 'insert': tg, 'b0': round(hold, 3)})
        print('%7.2f-%7.2f  %5.2fs  INSERT %s' % (n0, n1, n1 - n0, tg))
    else:
        out.append({'n0': n0, 'n1': n1, 'b0': round(tg[0], 3), 'b1': round(tg[1], 3)})
        print('%7.2f-%7.2f  %5.2fs  base %6.2f-%6.2f  x%.2f  %s' % (n0, n1, n1 - n0, tg[0], tg[1], (tg[1] - tg[0]) / (n1 - n0), cue[0]))
json.dump(out, open(os.path.join(HERE, '..', 'src', 'journey', 'edl.json'), 'w'), indent=0)
