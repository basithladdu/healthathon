import json,subprocess,re
r=json.load(open("lines.json"))
F="E:/Healthathon/Sahara-Feature-Film-voiceover.mp4"
def lv(s,e,f=F):
    o=subprocess.run(["ffmpeg","-nostats","-ss","%.3f"%s,"-t","%.3f"%(e-s),"-i",f,"-af","volumedetect","-f","null","-"],stderr=subprocess.PIPE,universal_newlines=True).stderr
    m=re.search(r"mean_volume: (\S+) dB",o);p=re.search(r"max_volume: (\S+) dB",o)
    return float(m.group(1)),float(p.group(1))
print("id start dur rate window speechMean gapBeforeMean peakdB")
prev_end=0
for i,l in enumerate(r):
    s=l["start"];e=s+l["dur"]
    sm,sp=lv(s,e)
    g0=prev_end+0.05; g1=s-0.03
    gm=lv(g0,g1)[0] if g1-g0>0.12 else float('nan')
    ov = e>r[i+1]["start"] if i+1<len(r) else e>191.2
    print("%s %.1f %.2f %+d%% %.1f %.1f %.1f %.1f %s"%(l["id"],s,l["dur"],l["rate"],l["end"]-s,sm,gm,sp,"OVERLAP" if ov else ""))
    prev_end=e
