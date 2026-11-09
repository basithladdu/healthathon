import json,subprocess,sys
P=json.load(open("el-placement.json"))
thr,ratio,pre=sys.argv[1],sys.argv[2],sys.argv[3]
fc="[0:a]volume=%sdB[mu];[1:a]anull[k];[mu][k]sidechaincompress=threshold=%s:ratio=%s:attack=60:release=600:makeup=1:knee=4[du]"%(pre,thr,ratio)
subprocess.check_call(["ffmpeg","-v","error","-y","-i","music3.wav","-i","voice3.wav","-filter_complex",fc,"-map","[du]","-c:a","pcm_s16le","ducked3.wav"])
sel="+".join("between(t,%.3f,%.3f)"%(l["speech_onset"]+0.15,l["speech_end"]-0.15) for l in P)
def lufs(f):
    o=subprocess.run(["ffmpeg","-nostats","-i",f,"-af","aselect='%s',asetpts=N/SR/TB,ebur128"%sel,"-f","null","-"],stderr=subprocess.PIPE,universal_newlines=True).stderr
    return [x for x in o.split("Summary")[1].splitlines() if " I:" in x][0].split()[1]
print("voice",lufs("voice3.wav"),"music-ducked",lufs("ducked3.wav"))
