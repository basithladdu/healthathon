import json,subprocess
r=json.load(open("lines.json"))
ins=[];fc=[];lab=[]
for i,l in enumerate(r):
    ins+=["-i","lines/%s.wav"%l["id"]]
    ms=int(round(l["start"]*1000))
    fc.append("[%d:a]adelay=%d|%d,apad=whole_dur=191.2[a%d]"%(i,ms,ms,i)); lab.append("[a%d]"%i)
fc.append("".join(lab)+"amix=inputs=%d:normalize=0:duration=longest,atrim=0:191.2,asetpts=N/SR_START[m]"%len(r))
fc[-1]=fc[-1].replace("SR_START","SR")
fc.append("[m]highpass=f=80,acompressor=threshold=-20dB:ratio=2.5:attack=15:release=200:makeup=2,alimiter=limit=0.9[v]")
subprocess.check_call(["ffmpeg","-v","error","-y"]+ins+["-filter_complex",";".join(fc),"-map","[v]","-ar","48000","-ac","1","-c:a","pcm_s16le","voice.wav"])
