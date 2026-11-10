"""Align script.json tokens to whisper word times (eleven_words.json) -> src/journey/words.json; vo.wav from eleven.mp3."""
import difflib, json, os, re, subprocess
HERE=os.path.dirname(os.path.abspath(__file__))
script=json.load(open(os.path.join(HERE,'script.json'),encoding='utf-8'))
ww=json.load(open(os.path.join(HERE,'eleven_words.json')))
NUM={'98%':'ninetyeightpercent','13':'thirteen','600':'sixhundred'}
def norm(s):
    s=s.lower().strip()
    return NUM.get(s, re.sub(r'[^a-z0-9]','',s))
toks=[]
for sen in script:
    for tk in sen['text'].split():
        toks.append((sen['id'],tk))
A=[norm(t) for _,t in toks]; B=[norm(w['w']) for w in ww]
# split hyphen/number joins: compare on char level by expanding whisper words
sm=difflib.SequenceMatcher(a=A,b=B,autojunk=False)
times=[None]*len(toks)
for blk in sm.get_matching_blocks():
    for k in range(blk.size):
        times[blk.a+k]=(ww[blk.b+k]['s'],ww[blk.b+k]['e'])
# fuzzy for replaced regions
for tag,i1,i2,j1,j2 in sm.get_opcodes():
    if tag=='replace':
        n=i2-i1; s0=ww[j1]['s']; e0=ww[j2-1]['e']
        for k in range(n):
            times[i1+k]=(s0+(e0-s0)*k/n, s0+(e0-s0)*(k+1)/n)
# interpolate missing
for k in range(len(times)):
    if times[k] is None:
        p=next((times[j] for j in range(k-1,-1,-1) if times[j]),(0,0)); q=next((times[j] for j in range(k+1,len(times)) if times[j]),None)
        if q is None: q=(p[1]+0.3,p[1]+0.6)
        # count run
        j=k
        while j<len(times) and times[j] is None: j+=1
        n=j-k+1
        for m in range(k,j):
            f=(m-k+1)/n
            times[m]=(p[1]+(q[0]-p[1])*f*0.98, p[1]+(q[0]-p[1])*f)
miss=sum(1 for b in sm.get_opcodes() if b[0]!='equal')
total=round(float(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration','-of','csv=p=0',os.path.join(HERE,'eleven.mp3')]))+1.6,3)
out=[{'id':i,'w':re.sub(r"[^\w'%-]","",t) or t,'s':round(s,3),'e':round(e,3)} for (i,t),(s,e) in zip(toks,times)]
json.dump({'voice':'ElevenLabs Max','total':total,'words':out},open(os.path.join(HERE,'..','src','journey','words.json'),'w'),indent=0)
subprocess.check_call(['ffmpeg','-v','error','-y','-i',os.path.join(HERE,'eleven.mp3'),'-af','apad=pad_dur=1.6','-ar','48000','-ac','1',os.path.join(HERE,'vo.wav')])
for o in sm.get_opcodes():
    if o[0]!='equal': print(o[0],[t for _,t in toks[o[1]:o[2]]],'<->',[w['w'] for w in ww[o[3]:o[4]]])
print('total',total)
