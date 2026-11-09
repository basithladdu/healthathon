import json,re,difflib
W=json.load(open("words.json"))
txt=open("../elevenlabs-script.txt",encoding="utf-8").read()
blocks=[b.strip() for b in re.split(r"\n\s*\n",txt.strip()) if b.strip()]
norm=lambda s:re.sub(r"[^a-z0-9]","",s.lower())
sw=[];owner=[]
for bi,b in enumerate(blocks):
    clean=re.sub(r"\[[^\]]*\]","",b)
    for t in clean.replace("-"," ").split():
        n=norm(t)
        if n: sw.append(n); owner.append(bi)
ww=[]
for w in W:
    for t in w["w"].replace("-"," ").split():
        n=norm(t)
        if n: ww.append((n,w["s"],w["e"]))
a=sw;b=[x[0] for x in ww]
sm=difflib.SequenceMatcher(None,a,b,autojunk=False)
m2w={}
print("blocks",len(blocks),"script words",len(a),"whisper words",len(b))
for tag,i1,i2,j1,j2 in sm.get_opcodes():
    if tag=="equal":
        for k in range(i2-i1): m2w[i1+k]=j1+k
    else:
        print(tag,"block",owner[i1]+1 if i1<len(owner) else "end","script:",a[i1:i2],"whisper:",b[j1:j2],"t=%.2f"%ww[j1][1] if j1<len(ww) else "")
for t in ("softly","serious","warmly","excited","cheerfully","confident"):
    print("tag word in whisper:",t,[ "%.1f"%x[1] for x in ww if x[0]==t])
# per block first/last matched
res=[]
for bi in range(len(blocks)):
    idx=[i for i in range(len(a)) if owner[i]==bi and i in m2w]
    f,l=m2w[idx[0]],m2w[idx[-1]]
    res.append(dict(block=bi+1,first=f,last=l,fs=ww[f][1],le=ww[l][2],text=blocks[bi]))
    print(bi+1,"%.2f-%.2f"%(ww[f][1],ww[l][2]),blocks[bi][:50])
json.dump(res,open("blocks.json","w"))
json.dump(ww,open("ww.json","w"))
