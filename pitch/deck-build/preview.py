"""Rough slide preview: pictures, filled shapes, wrapped text. For layout QA only."""
import io, sys
from PIL import Image, ImageDraw, ImageFont
from pptx import Presentation
from pptx.util import Emu

SRC = sys.argv[1]; OUT = sys.argv[2]
SC = 96  # px per inch
p = Presentation(SRC)
W, H = int(p.slide_width / 914400 * SC), int(p.slide_height / 914400 * SC)
FONT = r"C:\Windows\Fonts\calibri.ttf"; FONTB = r"C:\Windows\Fonts\calibrib.ttf"
SYM = r"C:\Windows\Fonts\seguisym.ttf"


def px(v): return int(v / 914400 * SC)


def col(sh):
    try:
        if sh.fill.type == 1: return '#%s' % sh.fill.fore_color.rgb
    except Exception: pass
    return None


def wrap(d, text, font, width):
    lines = []
    for para in text.split("\n"):
        cur = ""
        for w in para.split(" "):
            t = (cur + " " + w).strip()
            if d.textlength(t, font=font) <= width or not cur: cur = t
            else: lines.append(cur); cur = w
        lines.append(cur)
    return lines


thumbs = []
for i, s in enumerate(p.slides, 1):
    im = Image.new("RGB", (W, H), "#FBF8F1"); d = ImageDraw.Draw(im)
    def walk(shapes):
        for sh in shapes:
            if sh.shape_type == 6:  # group
                walk(sh.shapes); continue
            x, y, w, h = px(sh.left or 0), px(sh.top or 0), px(sh.width or 0), px(sh.height or 0)
            if sh.shape_type == 13:
                try:
                    pic = Image.open(io.BytesIO(sh.image.blob)).convert("RGBA").resize((max(w, 1), max(h, 1)))
                    im.paste(pic, (x, y), pic)
                except Exception: d.rectangle([x, y, x + w, y + h], outline="red")
                continue
            c = col(sh)
            if c: d.rounded_rectangle([x, y, x + w, y + h], radius=8, fill=c)
            if getattr(sh, "has_table", False) and sh.has_table:
                tb = sh.table; rh = h / max(len(tb.rows), 1); cw = [px(cc.width) for cc in tb.columns]
                for r in range(len(tb.rows)):
                    cx = x
                    for cidx in range(len(tb.columns)):
                        d.rectangle([cx, y + r * rh, cx + cw[cidx], y + (r + 1) * rh], outline="#bbb")
                        f = ImageFont.truetype(FONT, 13)
                        d.text((cx + 4, y + r * rh + 4), tb.cell(r, cidx).text[:60], font=f, fill="#222")
                        cx += cw[cidx]
                continue
            if sh.has_text_frame and sh.text_frame.text.strip():
                ty = y + 3
                for para in sh.text_frame.paragraphs:
                    t = "".join(r.text for r in para.runs) or para.text
                    if not t.strip(): ty += 8; continue
                    r0 = para.runs[0] if para.runs else None
                    size = (r0.font.size.pt if r0 is not None and r0.font.size else 14) * SC / 72
                    bold = bool(r0 is not None and r0.font.bold)
                    try: color = '#%s' % r0.font.color.rgb
                    except Exception: color = "#222"
                    f = ImageFont.truetype(FONTB if bold else FONT, max(int(size), 6))
                    for ln in wrap(d, t, f, max(w - 8, 10)):
                        lw = d.textlength(ln, font=f)
                        al = para.alignment
                        tx = x + 4 if al not in (2,) else x + (w - lw) / 2
                        d.text((tx, ty), ln, font=f, fill=color)
                        ty += size * 1.2
                if ty > y + h + 6:
                    d.rectangle([x, y, x + w, y + h], outline="red", width=2)  # overflow marker
    walk(s.shapes)
    d.text((W - 40, H - 22), str(i), font=ImageFont.truetype(FONTB, 16), fill="red")
    thumbs.append(im)
cols = 3; tw, th = W // 2, H // 2
g = Image.new("RGB", (tw * cols, th * ((len(thumbs) + cols - 1) // cols)), "white")
for k, t in enumerate(thumbs):
    g.paste(t.resize((tw, th)), ((k % cols) * tw, (k // cols) * th))
    t.save(OUT.replace(".jpg", f"-{k+1:02d}.jpg"))
g.save(OUT)
print(len(thumbs))
