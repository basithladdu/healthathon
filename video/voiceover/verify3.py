import json,subprocess,re
P=json.load(open("el-placement.json"));F="E:/Healthathon/Sahara-Feature-Film-final.mp4"
def lv(s,e):
    o=subprocess.run(["ffmpeg","-nostats","-ss","%.3f"%s,"-t","%.3f"%(e-s),"-i",F,"-af","volumedetect","-f","null","-"],stderr=subprocess.PIPE,universal_newlines=True).stderr
    return float(re.search(r"mean_volume: (\S+)",o).group(1)),float(re.search(r"max_volume: (\S+)",o).group(1))
prev=0
for i,l in enumerate(P):
    s=l["speech_onset"];e=l["speech_end"]
    sm,sp=lv(s+0.05,e-0.05)
    g=lv(prev+0.1,s-0.05)[0] if s-0.05-prev-0.1>0.15 else float('nan')
    ov=i+1<len(P) and (l["line_start"]+l["duration"]>P[i+1]["line_start"])
    print("%-6s start %.2f dur %.2f atempo %.3f spare %.2f speech %.1f gap %.1f peak %.1f %s"%(l["id"],l["line_start"],l["duration"],l["atempo"],l["spare"],sm,g,sp,"OVERLAP" if ov else ""))
    prev=e
