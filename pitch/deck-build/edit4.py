p = 'finale.py'
L = open(p, encoding='utf-8').read().split('\n')
BS = chr(92)


def setline(prefix, new):
    i = [k for k, l in enumerate(L) if l.lstrip().startswith(prefix)]
    assert len(i) == 1, prefix
    L[i[0]] = new
    return i[0]


setline('(118, 119, 120, 121, "Patient"', '    (118, 119, 120, 121, "Patient", "at home", ["Symptoms, pain and distress", "Multilingual", "Shares the care with a caregiver", "Simple Mode"]),')
setline('(122, 123, 124, 125, "Family"', '    (122, 123, 124, 125, "Caregiver", "family, at home", ["Everyone stays in the loop", "Medicine and visit reminders", "Their own wellbeing check"]),')
setline('(126, 127, 128, 129, "Their doctor"', '    (126, 127, 128, 129, "Their doctor", "clinic", ["Leads the goals-of-care talk", "Sees symptom trends", "Signs the plan"]),')
setline('(130, 131, 132, 133, "Emergency doctor"', '    (130, 131, 132, 133, "Emergency doctor", "emergency room", ["Scans the QR", "Signed wishes in seconds", "No login needed"]),')
i = setline('[[(name, {"size": 21', '         [[(name, {"size": 24, "bold": True})], [(where.upper(), {"size": 12, "bold": True, "color": SUB, "space": 2})]] +')
L[i + 1] = '         [[("' + BS + 'u2022  " + it, {"size": 18, "color": INK, "space": 14})] for it in items])'

i = setline('clean(c, "Impact We Will Measure', '''clean(c, "Impact We Will Measure: a 90-Day Pilot")
drop(c, *range(168, 184))
tshape = shape(c, 184); tshape.left = Inches(0.55); tshape.top = Inches(1.2); tshape.width = Inches(12.2); tshape.height = Inches(4.0)
pay = shape(c, 185); pay.left = Inches(0.55); pay.width = Inches(12.2); pay.top = Inches(5.45)
pt = shape(c, 186); pt.left = Inches(0.75); pt.width = Inches(11.8); pt.top = Inches(5.45)
nt = shape(c, 187); nt.left = Inches(0.55); nt.width = Inches(12.2); nt.top = Inches(6.35); nt.height = Inches(0.6)''')
# skip the old card text loop (its shapes are gone)
j = [k for k, l in enumerate(L) if l.startswith('for sid, tx in {') and k > i][0]
L[j] = 'for sid, tx in {} or {'
setline('for k_, w_ in enumerate((2.75', 'for k_, w_ in enumerate((5.0, 4.2, 3.0)): tbl.columns[k_].width = Inches(w_)')
k = [n for n, l in enumerate(L) if 'r__.font.size = Pt(12 if r_ else 13)' in l][0]
L[k] = L[k].replace('Pt(12 if r_ else 13)', 'Pt(16 if r_ else 17)')
setline('set_pars(shape(c, 186)', 'set_pars(shape(c, 186), ["A week in an ICU costs a hospital ' + BS + 'u20B942,000' + BS + 'u201375,000 to provide, and families often pay most of it. We make sure what the patient chose is known before that week starts."])')
open(p, 'w', encoding='utf-8').write('\n'.join(L))
print('ok')
