import json,subprocess,wave,numpy as np,os
B=json.load(open("blocks.json"))
T=json.load(open("../out/timing-v3.json"))["scenes"]
os.makedirs("el-lines",exist_ok=True)
subprocess.check_call(["ffmpeg","-v","error","-y","-i","elevenlabs.mp3","-ac","1","-c:a","pcm_s16le","el_full.wav"])
w=wave.open("el_full.wav");sr=w.getframerate();x=np.frombuffer(w.readframes(w.getnframes()),dtype=np.int16).astype(float)/32768
hop=int(sr*0.005);n=len(x)//hop
env=np.sqrt((x[:n*hop].reshape(n,hop)**2).mean(1))
env=np.convolve(env,np.ones(3)/3,"same")
def cutpoint(le,fs):
    lo,hi=(le,fs) if fs>le+0.02 else (min(le,fs)-0.06,max(le,fs)+0.06)
    lo=max(lo,0);i0,i1=int(lo/0.005),int(hi/0.005)+1
    return (i0+int(np.argmin(env[i0:i1])))*0.005
# scene list: merge O4+O5
sc=[];i=0
while i<len(T):
    s=T[i]
    if s["id"]=="O4": sc.append(dict(id="O4+O5",start=s["start_s"],end=T[i+1]["start_s"]+T[i+1]["length_s"])); i+=2; continue
    sc.append(dict(id=s["id"].split(" ")[0] if s["id"].startswith("CH") else s["id"],start=s["start_s"],end=s["start_s"]+s["length_s"],ch=s["id"].startswith("CH"))); i+=1
assert len(sc)==27
dur_total=len(x)/sr
out=[]
for k,b in enumerate(B):
    prevcut=0.0 if k==0 else cutpoint(B[k-1]["le"],b["fs"])
    nextcut=dur_total if k==26 else cutpoint(b["le"],B[k+1]["fs"])
    ls=max(prevcut,b["fs"]-0.10); le=min(nextcut,b["le"]+0.18)
    off=0.2 if sc[k].get("ch") else 0.4
    speech=b["le"]-b["fs"]; room=sc[k]["end"]-0.2-(sc[k]["start"]+off)
    at=1.0
    if speech>room: at=round(speech/room+0.0005,3)
    f="el-lines/%02d_%s.wav"%(k+1,sc[k]["id"].replace("+","_"))
    d=le-ls
    af="afade=t=in:d=0.015,afade=t=out:st=%.3f:d=0.015"%(d-0.015)
    if at>1: af+=",atempo=%.3f"%min(at,1.08)
    subprocess.check_call(["ffmpeg","-v","error","-y","-ss","%.4f"%ls,"-t","%.4f"%d,"-i","el_full.wav","-af",af,"-c:a","pcm_s16le",f])
    a=min(at,1.08)
    lead=(b["fs"]-ls)/a
    pstart=sc[k]["start"]+off-lead
    sp=speech/a
    out.append(dict(id=sc[k]["id"],block=k+1,scene_start=sc[k]["start"],scene_end=round(sc[k]["end"],3),line_start=round(pstart,3),speech_onset=round(sc[k]["start"]+off,3),duration=round(d/a,3),speech_duration=round(sp,3),speech_end=round(sc[k]["start"]+off+sp,3),spare=round(sc[k]["end"]-(sc[k]["start"]+off+sp),3),atempo=round(a,3),needs_atempo=round(at,3),file=f,src_cut=[round(ls,3),round(le,3)]))
    print(out[-1]["id"],out[-1]["scene_start"],out[-1]["speech_onset"],out[-1]["speech_duration"],"spare",out[-1]["spare"],"atempo",out[-1]["needs_atempo"])
json.dump(out,open("el-placement.json","w"),indent=1)
