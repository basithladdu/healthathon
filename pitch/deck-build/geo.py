import sys
from pptx import Presentation
from PIL import Image, ImageDraw
p = Presentation(sys.argv[1]); S = 96/914400
for n in map(int, sys.argv[2].split(',')):
    s = list(p.slides)[n-1]
    im = Image.open('pv2/p-%02d.jpg' % n).convert('RGB').resize((1280, 720)); d = ImageDraw.Draw(im, 'RGBA')
    for sh in s.shapes:
        if sh.shape_type == 1:  # autoshape
            try:
                c = sh.fill.fore_color.rgb if sh.fill.type == 1 else None
            except Exception: c = None
            box = [sh.left*S, sh.top*S, (sh.left+sh.width)*S, (sh.top+sh.height)*S]
            if c is not None: d.rectangle(box, fill=(c[0], c[1], c[2], 110))
            else: d.rectangle(box, outline=(120, 120, 120, 255))
    im.save('pv2/g-%02d.jpg' % n)
