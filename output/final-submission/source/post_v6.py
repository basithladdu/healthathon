"""ToFEI-style polish on the generated deck builder: bold sans headings, solid icon badges. No wording changes."""
import io, json
p = r'C:\Users\basit\sahara-build\deck\build_deck_v3.py'
s = io.open(p, encoding='utf-8').read()
cols = {n: c for n, _, c in json.load(open(r'C:\Users\basit\sahara-build\icon-map.json'))}
def dark(h, f=0.68):
    r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4))
    return '%02X%02X%02X' % (int(r * f), int(g * f), int(b * f))
badge = {n: dark(c) for n, c in cols.items()}
old = "def icon(slide, n, x, y, s=0.42): return pic(slide, IC / f'image{n}.png', x, y, s, s)"
assert old in s
new = ("BADGE = " + json.dumps({str(k): v for k, v in badge.items()}) + "\n"
       "IW = B / 'assets/icons-white'\n"
       "def icon(slide, n, x, y, s=0.42):\n"
       "    c = slide.shapes.add_shape(MSO_SHAPE.OVAL, Inches(x), Inches(y), Inches(s), Inches(s))\n"
       "    c.fill.solid(); c.fill.fore_color.rgb = rgb(BADGE.get(str(n), '157B68')); c.line.fill.background(); c.shadow.inherit = False\n"
       "    k = s * 0.6\n"
       "    return pic(slide, IW / f'image{n}.png', x + (s - k) / 2, y + (s - k) / 2, k, k)")
s = s.replace(old, new)
# bold sans headings and numbers everywhere (ToFEI look)
s = s.replace("style_runs(t, font='Georgia', size=26, bold=True, color=GREEN)", "style_runs(t, font='Calibri', size=30, bold=True, color='1B2621')")
s = s.replace("font='Georgia'", "font='Calibri'")
io.open(p, 'w', encoding='utf-8').write(s)
print('ok')
