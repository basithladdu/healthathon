import json,subprocess,sys
r=json.load(open("lines.json"))
thr,ratio,pre=sys.argv[1],sys.argv[2],sys.argv[3]
fc="[0:a]volume=%sdB[mu];[1:a]anull[k];[mu][k]sidechaincompress=threshold=%s:ratio=%s:attack=60:release=600:makeup=1:knee=4[du]"%(pre,thr,ratio)
subprocess.check_call(["ffmpeg","-v","error","-y","-i","music.wav","-i","voice.wav","-filter_complex",fc,"-map","[du]","-c:a","pcm_s16le","ducked.wav"])
sel="+".join("between(t,%.3f,%.3f)"%(l["start"]+0.2,l["start"]+l["dur"]-0.2) for l in r)
def lufs(f):
    o=subprocess.run(["ffmpeg","-nostats","-i",f,"-af","aselect='%s',asetpts=N/SR/TB,ebur128"%sel,"-f","null","-"],stderr=subprocess.PIPE,universal_newlines=True).stderr
    return [x for x in o.split("Summary")[1].splitlines() if " I:" in x][0].split()[1]
print("voice",lufs("voice.wav"),"music-ducked",lufs("ducked.wav"))
