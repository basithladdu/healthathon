"""Caption lines from script.json + word timings. Writes src/journey/captions.json and journey/Sahara-Journey.srt.
Each line is at most ~42 characters, breaks after punctuation where possible, never crosses a sentence."""
import json, os, re

HERE = os.path.dirname(os.path.abspath(__file__))
script = json.load(open(os.path.join(HERE, 'script.json'), encoding='utf-8'))
words = json.load(open(os.path.join(HERE, '..', 'src', 'journey', 'words.json'), encoding='utf-8'))['words']
MAX = 42
norm = lambda s: re.sub(r'[^a-z0-9]', '', s.lower())

lines = []
for sen in script:
    toks = sen['text'].split()
    ws = [w for w in words if w['id'] == sen['id']]
    # map every script token to a time span
    spans = []
    if len(ws) == len(toks):
        spans = [(w['s'], w['e']) for w in ws]
    else:
        j = 0
        for k, tok in enumerate(toks):
            nt = norm(tok)
            # advance through edge words whose text is part of this token
            s = ws[min(j, len(ws) - 1)]['s']
            e = ws[min(j, len(ws) - 1)]['e']
            acc = ''
            while j < len(ws) and (acc == '' or not acc.startswith(nt) and nt.startswith(acc)):
                acc += norm(ws[j]['w']); e = ws[j]['e']; j += 1
                if acc == nt or len(acc) >= len(nt):
                    break
            spans.append((s, e))
    # 1) split into sentences on . ? ! ; 2) balanced recursive split of anything longer than MAX
    parts, cur = [], []
    for k, tok in enumerate(toks):
        cur.append(k)
        if tok[-1] in '.?!' and len(' '.join(toks[i] for i in cur)) >= 12:
            parts.append(cur); cur = []
    if cur:
        if parts and len(' '.join(toks[i] for i in cur)) < 12:
            parts[-1] += cur
        else:
            parts.append(cur)
    def L(ix):
        return len(' '.join(toks[i] for i in ix))
    def split(ix):
        if L(ix) <= MAX or len(ix) < 2:
            return [ix]
        best, bk = 1e9, 1
        for k in range(1, len(ix)):
            l, r = ix[:k], ix[k:]
            cost = max(L(l), L(r)) + (abs(L(l) - L(r)) * 0.15)
            if toks[l[-1]][-1] in ',:;':
                cost -= 9
            if len(l) == 1 or len(r) == 1:
                cost += 12
            if cost < best:
                best, bk = cost, k
        return split(ix[:bk]) + split(ix[bk:])
    for part in parts:
        for ix in split(part):
            lines.append({'text': ' '.join(toks[i] for i in ix), 's': spans[ix[0]][0], 'e': spans[ix[-1]][1]})

# hold each line a little after its last word, but never into the next line
for i, l in enumerate(lines):
    nxt = lines[i + 1]['s'] if i + 1 < len(lines) else l['e'] + 2
    l['s'] = round(max(0, l['s'] - 0.08), 3)
    l['e'] = round(min(l['e'] + 0.45, nxt - 0.1), 3)
    l['text'] = l['text'].rstrip('.,:')  # sparse look: no trailing full stops/commas

json.dump(lines, open(os.path.join(HERE, '..', 'src', 'journey', 'captions.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=0)

def ts(x):
    h, r = divmod(x, 3600); m, s = divmod(r, 60)
    return '%02d:%02d:%02d,%03d' % (h, m, int(s), round((s - int(s)) * 1000) % 1000)

with open(os.path.join(HERE, 'Sahara-Journey.srt'), 'w', encoding='utf-8') as f:
    for i, l in enumerate(lines, 1):
        f.write('%d\n%s --> %s\n%s\n\n' % (i, ts(l['s']), ts(l['e']), l['text']))
print(len(lines), 'caption lines; longest', max(len(l['text']) for l in lines))
for l in lines:
    print('%6.2f %6.2f  %s' % (l['s'], l['e'], l['text']))
