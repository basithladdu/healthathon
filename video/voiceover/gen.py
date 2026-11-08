import asyncio, json, subprocess, sys, os
import edge_tts
V="en-IN-NeerjaNeural"
lines=json.load(open("../narration.json",encoding="utf-8"))
os.makedirs("lines",exist_ok=True)
def dur(p):
    return float(subprocess.check_output(["ffprobe","-v","error","-show_entries","format=duration","-of","csv=p=0",p]).decode().strip())
async def synth(text,rate,out):
  for a in range(6):
    try:
      await edge_tts.Communicate(text,V,rate=rate).save(out); return
    except Exception as e:
      print("retry",a,type(e).__name__,flush=True); await asyncio.sleep(2+2*a)
  raise SystemExit("edge-tts unreachable")
async def _old(text,rate,out):
    await edge_tts.Communicate(text,V,rate=rate).save(out)
res=[]
for l in lines:
    win=l["end"]-l["start"]; used=None
    for r in (-3,0,3,6,9,12):
        rs="%+d%%"%r
        raw="lines/%s_raw.mp3"%l["id"]; out="lines/%s.wav"%l["id"]
        asyncio.get_event_loop().run_until_complete(synth(l["text"],rs,raw))
        subprocess.check_call(["ffmpeg","-v","error","-y","-i",raw,"-ar","48000","-ac","1","-af",
          "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.03,areverse,silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.03,areverse",out])
        d=dur(out); used=(r,d)
        if d<=win-0.05: break
    res.append(dict(id=l["id"],start=l["start"],end=l["end"],rate=used[0],dur=used[1],fits=used[1]<=win-0.05))
    print(res[-1],flush=True)
json.dump(res,open("lines.json","w"),indent=1)
