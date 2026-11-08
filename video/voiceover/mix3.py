import json,subprocess
P=json.load(open("el-placement.json"));D=192.2
ins=[];fc=[];lab=[]
for i,l in enumerate(P):
    ins+=["-i",l["file"]];ms=int(round(l["line_start"]*1000))
    fc.append("[%d:a]aresample=48000,adelay=%d|%d,apad=whole_dur=%s[a%d]"%(i,ms,ms,D,i));lab.append("[a%d]"%i)
fc.append("".join(lab)+"amix=inputs=%d:normalize=0:duration=longest,atrim=0:%s,asetpts=N/SR[m]"%(len(P),D))
fc.append("[m]highpass=f=80,acompressor=threshold=-24dB:ratio=1.6:attack=20:release=250:makeup=1[v]")
subprocess.check_call(["ffmpeg","-v","error","-y"]+ins+["-filter_complex",";".join(fc),"-map","[v]","-ar","48000","-ac","1","-c:a","pcm_s16le","voice3.wav"])
