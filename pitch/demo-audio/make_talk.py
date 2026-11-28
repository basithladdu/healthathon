"""Two-voice goals-of-care conversation (fictional), adapted from docs/goc-mock-conversations/01-english.txt."""
import asyncio, ssl, subprocess, json
_orig = ssl.create_default_context
def _ctx(*a, **k):  # trust the environment's proxy CA bundle (verification stays on)
    k.pop("capath", None); k.pop("cadata", None); k["cafile"] = "/root/.ccr/ca-bundle.crt"
    return _orig(*a, **k)
ssl.create_default_context = _ctx
import edge_tts

D = ("en-IN-PrabhatNeural", "-4%", "+0Hz")   # doctor
P = ("en-IN-NeerjaNeural", "-12%", "-6Hz")   # patient, 64
LINES = [
 ("D", "Good morning. Before we start, is it okay if I record this conversation, so that I can write it down correctly?"),
 ("P", "Yes, that is fine. You can record."),
 ("D", "Thank you. Can you tell me your full name and how old you are?"),
 ("P", "I am Lakshmi. I am sixty-four years old."),
 ("D", "And who should we call in an emergency?"),
 ("P", "Please call my daughter, Priya. Her phone number is on the file. She will speak for me if I cannot."),
 ("D", "Thank you. Let me ask about some treatments, if your heart or breathing were to stop."),
 ("D", "If your heart stops, would you want CPR, where we press on the chest to try to restart it?"),
 ("P", "No. I do not want CPR."),
 ("D", "If your kidneys fail, would you want dialysis?"),
 ("P", "No dialysis. I have seen what it does."),
 ("D", "What about a breathing machine, a ventilator?"),
 ("P", "I do not want a ventilator."),
 ("D", "Is oxygen through the nose acceptable?"),
 ("P", "Oxygen is okay."),
 ("D", "Would you want to go to the ICU if you become very unwell?"),
 ("P", "Maybe for a few days, as a trial, if it can help."),
 ("D", "What about a feeding tube, if you cannot swallow?"),
 ("P", "No feeding tube."),
 ("D", "Should we take you to hospital if things get worse at home?"),
 ("P", "Take me to hospital only for pain or breathlessness that you cannot control at home."),
 ("D", "What matters most to you now?"),
 ("P", "What matters most is being at home with my grandchildren. I would not want to live on machines."),
 ("D", "Overall, what is the goal for your care?"),
 ("P", "Keep me comfortable. Comfort care only."),
 ("D", "Do you understand that this illness cannot be cured?"),
 ("P", "Yes. I know the cancer cannot be cured."),
 ("D", "Thank you, Lakshmi. I will write this down, and we will read it together before anything is signed."),
]

async def say(i, who, text):
    v, rate, pitch = D if who == "D" else P
    await edge_tts.Communicate(text, v, rate=rate, pitch=pitch).save(f"l{i:02d}.mp3")

async def main():
    for i, (who, text) in enumerate(LINES):
        for attempt in range(3):
            try: await say(i, who, text); break
            except Exception as e:
                if attempt == 2: raise
                await asyncio.sleep(1.5)
asyncio.run(main())

# Join with natural gaps: 0.75 s when the speaker changes, 0.35 s when the same person continues.
parts, t, cues = [], 0.0, []
for i, (who, text) in enumerate(LINES):
    gap = 0.0 if i == 0 else (0.75 if LINES[i - 1][0] != who else 0.35)
    if gap:
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "lavfi", "-i", f"anullsrc=r=24000:cl=mono", "-t", str(gap), "-c:a", "pcm_s16le", f"g{i:02d}.wav"], check=True)
        parts.append(f"g{i:02d}.wav"); t += gap
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", f"l{i:02d}.mp3", "-ar", "24000", "-ac", "1", "-c:a", "pcm_s16le", f"l{i:02d}.wav"], check=True)
    dur = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", f"l{i:02d}.wav"], capture_output=True, text=True).stdout)
    cues.append((t, "Doctor" if who == "D" else "Lakshmi (patient)", text)); t += dur
    parts.append(f"l{i:02d}.wav")
open("list.txt", "w").write("".join(f"file '{p}'\n" for p in parts))
subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "concat", "-safe", "0", "-i", "list.txt", "-af", "loudnorm=I=-16:TP=-1.5:LRA=11", "-ar", "44100", "-b:a", "192k", "Sahara-GOC-conversation-doctor-patient.mp3"], check=True)
with open("Sahara-GOC-conversation-script.txt", "w") as f:
    f.write("Goals-of-care demo conversation (FICTIONAL: no real patient or doctor). Two voices: Doctor (male), Lakshmi, 64 (female).\n")
    f.write("Adapted from the team's practice talk docs/goc-mock-conversations/01-english.txt.\n\n")
    for s, who, text in cues: f.write(f"[{int(s // 60)}:{s % 60:04.1f}] {who}: {text}\n")
print("total seconds", round(t, 1))
