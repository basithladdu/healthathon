"""CPU-only model smoke check. No app build/device claim; no patient input or upload.

Point --models at the checksum-verified Whisper small int8 files. --hindi must be
an explicitly supplied non-patient PCM WAV. --english should be a locally
synthesised 16 kHz PCM WAV saying the expectation below. The mixed fixture is
assembled locally; no TTS or ASR provider is called. Install sherpa-onnx==1.13.8
and numpy into an isolated environment, or provide --python-deps.
"""
import argparse
import hashlib
import json
import pathlib
import re
import sys
import time
import wave


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--models", type=pathlib.Path, required=True)
    parser.add_argument("--hindi", type=pathlib.Path, required=True)
    parser.add_argument("--english", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    parser.add_argument("--python-deps", type=pathlib.Path)
    args = parser.parse_args()
    if args.python_deps:
        sys.path.insert(0, str(args.python_deps))
    import numpy as np
    import sherpa_onnx

    # One manifest: use the exact files and hashes compiled into the Android source.
    source = pathlib.Path(__file__).parents[1] / "app/src/main/java/in/wedevit/care/OfflineSpeechRules.kt"
    specs = re.findall(r'OfflineModelFile\("([^"]+)",\s*([\d_]+),\s*"([a-f0-9]{64})"\)', source.read_text(encoding="utf-8"))
    if len(specs) != 3:
        raise RuntimeError("Model source manifest could not be verified")
    model_hashes = {}
    for name, size, expected in specs:
        path = args.models / name
        digest = hashlib.file_digest(path.open("rb"), "sha256").hexdigest()
        if path.stat().st_size != int(size.replace("_", "")) or digest != expected:
            raise RuntimeError("Model checksum mismatch: " + name)
        model_hashes[name] = digest

    def read(path):
        with wave.open(str(path), "rb") as audio:
            if (audio.getframerate(), audio.getnchannels(), audio.getsampwidth()) != (16000, 1, 2):
                raise RuntimeError("Fixtures must be 16 kHz mono PCM16")
            return np.frombuffer(audio.readframes(audio.getnframes()), dtype="<i2").astype(np.float32) / 32768

    hindi, english = read(args.hindi), read(args.english)
    mixed = np.concatenate([hindi, np.zeros(4800, dtype=np.float32), english])
    if len(mixed) > 24 * 16000:
        raise RuntimeError("Keep the mixed smoke fixture below one app processing turn")
    recognizer = sherpa_onnx.OfflineRecognizer.from_whisper(
        encoder=str(args.models / specs[0][0]), decoder=str(args.models / specs[1][0]),
        tokens=str(args.models / specs[2][0]), language="", task="transcribe", num_threads=1,
        provider="cpu", debug=False,
    )
    rows = []
    for name, samples in (("hindi-public", hindi), ("english-synthetic", english), ("hindi-english-concatenated", mixed)):
        started = time.monotonic()
        stream = recognizer.create_stream(); stream.accept_waveform(16000, samples)
        recognizer.decode_stream(stream)
        text = stream.result.text.strip()
        rows.append({"fixture": name, "audioSeconds": len(samples) / 16000,
                     "inferenceSeconds": round(time.monotonic() - started, 3), "text": text,
                     "nonempty": bool(text), "containsHindiScript": bool(re.search(r"[\u0900-\u097f]", text)),
                     "containsLatinScript": bool(re.search("[A-Za-z]", text)),
                     "englishNegationRetained": bool(re.search(r"\b(?:not|don't|dont)\b", text, re.I))})
    result = {"evidence": "desktop CPU inference with the same pinned ONNX files and runtime version; no Android runtime or clinical accuracy claim",
              "runtime": getattr(sherpa_onnx, "__version__", "1.13.8"), "modelSha256": model_hashes,
              "englishExpected": "Please write these words. I do not want to decide today. We can talk again on Monday.",
              "patientDataUsed": False, "fixturesUploaded": False, "results": rows}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"report": str(args.output), "nonempty": [row["nonempty"] for row in rows],
                      "englishNegationRetained": rows[1]["englishNegationRetained"],
                      "mixedHindiAndLatin": rows[2]["containsHindiScript"] and rows[2]["containsLatinScript"]}))


if __name__ == "__main__":
    main()
