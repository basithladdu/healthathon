"""CPU-only model smoke check. No app build/device claim; no patient input or upload.

Point --models at the checksum-verified Qwen3-ASR0.6B int8 files. --hindi must be
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
    parser.add_argument("--hinglish", type=pathlib.Path, help="Optional real non-patient Hindi/English code-switching fixture")
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
    if len(specs) != 6:
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
    recognizer = sherpa_onnx.OfflineRecognizer.from_qwen3_asr(
        conv_frontend=str(args.models / "conv_frontend.onnx"),
        encoder=str(args.models / "encoder.int8.onnx"), decoder=str(args.models / "decoder.int8.onnx"),
        tokenizer=str(args.models / "tokenizer"), max_total_len=256, max_new_tokens=128,
        num_threads=1, provider="cpu", debug=False,
    )
    def windows(samples):
        # Match the phone's signal-based planner. All input offsets, including quiet tail, are retained.
        pending, quiet, end, fresh = [], 0, 0, 0
        for pos in range(0, len(samples), 320):
            frame = samples[pos:pos + 320]
            pending.extend(frame.tolist()); end += len(frame); fresh += len(frame)
            quiet = quiet + len(frame) if np.sqrt(np.mean(frame.astype(np.float64) ** 2)) < .003 and np.max(np.abs(frame)) < .03 else 0
            pause = len(pending) >= 16000 and quiet >= 4000
            if pause or len(pending) >= 192000:
                yield end - len(pending), end, np.asarray(pending, dtype=np.float32)
                pending = [] if pause else pending[-4800:]
                quiet, fresh = 0, 0
        if fresh:
            yield end - len(pending), end, np.asarray(pending, dtype=np.float32)
    rows = []
    fixtures = [("hindi-public", hindi), ("english-synthetic", english), ("hindi-english-concatenated", mixed)]
    if args.hinglish:
        fixtures.append(("hinglish-public", read(args.hinglish)))
    for name, samples in fixtures:
        started = time.monotonic()
        segments, covered = [], 0
        for start, end, chunk in windows(samples):
            if start > covered:
                raise RuntimeError("Audio coverage gap")
            covered = max(covered, end)
            text = ""
            if not (np.sqrt(np.mean(chunk.astype(np.float64) ** 2)) < .0003 and np.max(np.abs(chunk)) < .002):
                stream = recognizer.create_stream(); stream.accept_waveform(16000, chunk)
                recognizer.decode_stream(stream)
                text = stream.result.text.strip()
                if not text or len(stream.result.tokens) >= 124:
                    raise RuntimeError("Empty or token-limited speech turn; do not report completion")
            segments.append({"startMs": start / 16, "endMs": end / 16, "text": text})
        if covered != len(samples):
            raise RuntimeError("Audio tail not covered")
        text = "\n".join(segment["text"] for segment in segments if segment["text"])
        rows.append({"fixture": name, "audioSeconds": len(samples) / 16000,
                     "inferenceSeconds": round(time.monotonic() - started, 3), "text": text,
                     "segments": segments, "allInputSamplesCovered": True,
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
    # These are narrow retention gates, not an accuracy score or clinical validation.
    if not all(row["nonempty"] for row in rows) or not rows[1]["englishNegationRetained"] or not rows[2]["englishNegationRetained"] or not rows[0]["containsHindiScript"]:
        raise SystemExit("Speech retention checks failed; inspect the recorded output")


if __name__ == "__main__":
    main()
