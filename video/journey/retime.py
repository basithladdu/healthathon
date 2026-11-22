"""After voice.py: rewrite TIMINGS.txt and src/journey/windows.ts from words.json."""
import json, os
os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
d=json.load(open('src/journey/words.json',encoding='utf-8'));W=d['words']
ids=[]; [ids.append(w['id']) for w in W if w['id'] not in ids]
st=lambda i:[w for w in W if w['id']==i][0]['s']; en=lambda i:[w for w in W if w['id']==i][-1]['e']
out=[]
for i in ids:
    ws=[w for w in W if w['id']==i]
    out.append(f"{i} {ws[0]['s']:.2f}-{ws[-1]['e']:.2f}: "+' '.join(f"{w['w']}@{w['s']:.2f}" for w in ws))
open('journey/TIMINGS.txt','w',encoding='utf-8').write('\n'.join(out)+f"\nTOTAL {d['total']}\n")
def b(nxt):
    p=ids[ids.index(nxt)-1]; return round((en(p)+st(nxt))/2,2)
cuts={'A':0,'B':b('s04'),'C':b('s06'),'D':b('s08'),'E':b('s12'),'X':b('s14a'),'F':b('s15'),'G':b('s16'),'H':b('s17'),'I':b('s20')}
notes={'A':'s01-s03 emergency room at 3 a.m.','B':'s04-s05 Sahara started; the five of us (photos); Dr Sujay in August','C':'s06-s07 we built everything (Basith, Awaiz)','D':"s08-s11c Shirin's question; the switches; back to the goals of care talk",'E':'s12-s14 record, 13 topics, doctor leads, review, sign, family signs, lock','X':'s14a-s14e at home: symptoms, medicines, documents, centres and morphine','F':'s15 languages','G':'s16 back to the emergency room, one scan','H':'s17-s19 oh wait: the caregiver','I':'s20-s22 three months, survey, live on Google Play, title'}
ks=list(cuts); L=[]
for i,k in enumerate(ks):
    to='TOTAL_SEC' if i==len(ks)-1 else str(cuts[ks[i+1]])
    L.append(f"  {k}: [{cuts[k]}, {to}], // {notes[k]}")
open('src/journey/windows.ts','w').write("import {TOTAL_SEC} from './kit';\n/** scene windows in film seconds [from, to). Boundaries sit in the pauses between sentences. */\nexport const WIN: Record<string, [number, number]> = {\n"+"\n".join(L)+"\n};\n")
print('\n'.join(L))
