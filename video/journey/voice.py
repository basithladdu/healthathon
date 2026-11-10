"""Make the narration with edge-tts, one clip per sentence, joined with designed gaps.
Writes journey/vo.wav and src/journey/words.json (word start/end in seconds)."""
import asyncio, json, os, subprocess, sys, wave
import edge_tts

HERE = os.path.dirname(os.path.abspath(__file__))
VOICE = sys.argv[1] if len(sys.argv) > 1 else "en-IN-PrabhatNeural"
RATE = sys.argv[2] if len(sys.argv) > 2 else "+0%"
SR = 48000
lines = json.load(open(os.path.join(HERE, "script.json"), encoding="utf-8"))
# spoken respellings so the TTS says the names right; captions and words.json keep the real spelling
SAY = {}
BACK = {v: k for k, v in SAY.items()}
def say(t):
    for k, v in SAY.items():
        t = t.replace(k, v)
    return t
os.makedirs(os.path.join(HERE, "vo"), exist_ok=True)


async def synth(text, out):
    for a in range(6):
        try:
            c = edge_tts.Communicate(text, VOICE, rate=RATE, boundary="WordBoundary")
            words = []
            with open(out, "wb") as f:
                async for ch in c.stream():
                    if ch["type"] == "audio":
                        f.write(ch["data"])
                    elif ch["type"] == "WordBoundary":
                        words.append({"w": ch["text"], "s": ch["offset"] / 1e7, "e": (ch["offset"] + ch["duration"]) / 1e7})
            return words
        except Exception as e:
            print("retry", a, e, flush=True)
            await asyncio.sleep(2 + 2 * a)
    raise SystemExit("edge-tts unreachable")


def pcm(path):
    raw = subprocess.check_output(["ffmpeg", "-v", "error", "-i", path, "-ar", str(SR), "-ac", "1", "-f", "s16le", "-"])
    return raw


def lead_trail(raw, thr=500):
    import numpy as np
    a = np.frombuffer(raw, dtype=np.int16)
    idx = np.where(np.abs(a) > thr)[0]
    if len(idx) == 0:
        return 0, len(a)
    return max(0, idx[0] - int(0.02 * SR)), min(len(a), idx[-1] + int(0.06 * SR))


async def main():
    import numpy as np
    out = []
    allw = []
    t = 0.0
    for l in lines:
        mp3 = os.path.join(HERE, "vo", l["id"] + ".mp3")
        words = await synth(say(l["text"]), mp3)
        raw = pcm(mp3)
        a, b = lead_trail(raw)
        seg = np.frombuffer(raw, dtype=np.int16)[a:b]
        t += l["gap"]
        out.append(np.zeros(int(l["gap"] * SR), dtype=np.int16))
        off = a / SR
        for w in words:
            allw.append({"id": l["id"], "w": BACK.get(w["w"], w["w"]), "s": round(t + w["s"] - off, 3), "e": round(t + w["e"] - off, 3)})
        out.append(seg)
        print(l["id"], "%.2f" % t, "%.2f s" % (len(seg) / SR), flush=True)
        t += len(seg) / SR
    out.append(np.zeros(int(1.6 * SR), dtype=np.int16))
    t += 1.6
    a = np.concatenate(out)
    with wave.open(os.path.join(HERE, "vo.wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(a.tobytes())
    os.makedirs(os.path.join(HERE, "..", "src", "journey"), exist_ok=True)
    json.dump({"voice": VOICE, "total": round(t, 3), "words": allw}, open(os.path.join(HERE, "..", "src", "journey", "words.json"), "w"), indent=0)
    print("TOTAL", round(t, 2), "words", len(allw))

asyncio.run(main())
