import re, sys
sys.path.insert(0, '.')
from v3_sections import S
src = open('build_deck_v2.py', encoding='utf-8').read()
parts = re.split(r'(?m)^(# -{10,} (\d+) .*)$', src)
out = parts[0]
i = 1
while i < len(parts):
    marker, num, body = parts[i], int(parts[i+1]), parts[i+2]
    if num in S:
        tail = ''
        if num == 13:
            k = body.find('import re'); tail = body[k:]
        out += marker + '\n' + S[num].strip('\n') + '\n\n' + tail
    else:
        out += marker + body
    i += 3
out = out.replace('"""Sahara final presentation', '"""Sahara final presentation v3 (plain words, logos, calendar)', 1)
open('build_deck_v3.py', 'w', encoding='utf-8').write(out)
print('ok')
