import re
p='finale.py'; L=open(p,encoding='utf-8').read().split('\n')
def line(prefix, new):
    idx=[i for i,l in enumerate(L) if l.lstrip().startswith(prefix)]
    assert len(idx)==1,(prefix,idx); L[idx[0]]=new
def block(start_prefix, end_prefix, new):
    a=[i for i,l in enumerate(L) if l.lstrip().startswith(start_prefix)][0]
    b=[i for i,l in enumerate(L) if i>=a and l.rstrip().endswith(end_prefix)][0]
    L[a:b+1]=new.split('\n')
line('rows = [(124, 125, 126, 127)', '''rows = [(124, 125, 126, 127), (128, 129, 130, 131), (132, 133, 134, 135)]
NEW = [("<4%", ["of the 7\u201310 million Indians who need palliative care get it.", "Sharma, Thakkar et al., ecancer 2025"]),
       ("79%", ["of cancer hospitalisations push families into catastrophic spending.", "Kastor & Mohanty, PLoS One 2018"]),
       ("63%", ["of end-stage patients: code status not found within 15 minutes of reaching the ER.", "Russell, West J Emerg Med 2021"])]
for (o_, ic_, num_, txt_), (big, lines) in zip(rows, NEW):
    set_pars(shape(ev, num_), [big]); set_pars(shape(ev, txt_), lines)''')
s='\n'.join(L)
s=s.replace('("3", "Checks 13 topics", "Anything not discussed is flagged, never guessed.")','("3", "Covers 13 topics", "Anything not discussed is shown as not discussed.")')
s=s.replace('''    [("Also in India", {"size": 14, "bold": True, "color": TEAL})],''','''    [("Also checked (55 apps)", {"size": 14, "bold": True, "color": TEAL})],
    [("London UCP, Oregon POLST, Taiwan NHI: plans reach the ER, but no home layer for families.", {"size": 11, "space": 5})],
    [("Kerala Care grid: home visits and care plans.", {"size": 11, "space": 5})],''')
s=s.replace('PalaSH (AIIMS): triage for the palliative team; no signed goals of care.','PalaSh, AIIMS Bathinda: clinician records; no patient plan or ER card. A likely partner.')
s=s.replace('BandhuCare: 8 languages.','BandhuCare, CMC Vellore: 8-language companion.')
s=s.replace('"size": 12, "space": 6','"size": 11, "space": 5')
L=s.split('\n')
line('bullets(164,', 'bullets(164, ["Recording starts only after the patient agrees", "DPDP Act: consent, access, correction, erasure", "Research use only if the patient opts in"])')
line('bullets(169,', 'bullets(169, ["The AI drafts; it never recommends treatment", "Every line links to the sentence it came from", "A signed clinical record, not a living will"])')
line('171: ["Hospitals and clinics pay"', '    171: ["Hospitals and clinics pay", "Per enrolled patient, to plug Sahara into their EHR. Public roll-out through NCG or state programmes."],')
line('183: ["How it scales"', '    183: ["How it scales", "One clinic, then the National Cancer Grid\u2019s 370+ centres, on ABDM\u2019s 90 crore health IDs. London\u2019s care plan grew the same way."],')
block('rows = [["What we will measure"', ']]', '''rows = [["90-day pilot: what we measure", "Evidence it can move", "Target"],
        ["Eligible patients with a signed plan", "Chennai form: 0% to 92%", "[baseline] to [target]"],
        ["Seconds from ER arrival to the plan", "63% not found in 15 min", "Under 60 s"],
        ["Doctor minutes to a signed plan", "Measured, not promised", "By hand vs Sahara"],
        ["Care at the end matched the plan", "86% vs 30% with planning", "Chart review"],
        ["Caregiver distress at 30 days", "Less stress with planning", "Lower or no worse"],
        ["Family out-of-pocket, last 30 days", "\u20B943,897 for patients who died", "Direction only"]]''')
line('set_pars(shape(c, 186)', 'set_pars(shape(c, 186), ["No family should go into debt for treatment the patient said they didn\u2019t want."])')
line('set_pars(shape(c, 187)', 'set_pars(shape(c, 187), ["Thangasamy 2025; Russell 2021; Detering, BMJ 2010; Das & Ladusingh 2018 (NSS). Effect evidence is mostly from abroad; our pilot measures it in India."])')
open(p,'w',encoding='utf-8').write('\n'.join(L))
print('ok')
