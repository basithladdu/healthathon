"""Run downloaded Sahara packages on an empty disposable CI emulator, without rebuilding.

No login credentials, private signing material, real microphone or AI-provider calls are used.
The optimized APK gets an ephemeral CI signer; that is explicitly not the store upload signer.
All fixtures and exported preview PDFs are fictional. UI evidence is saved even on failure.
"""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import traceback
import xml.etree.ElementTree as ET

ROOT = Path.cwd()
ART = ROOT / "runtime-artifacts"
OUT = ROOT / "runtime-report"
SDK = Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", "")))
ADB = SDK / "platform-tools/adb"
TOOLS = SDK / "build-tools/36.1.0"
SERIAL = "emulator-" + os.environ.get("EMULATOR_PORT", "5554")
DEBUG = "in.wedevit.sahara.development"
RELEASE = "in.wedevit.sahara"
CLASSES = ["DailyCareFlowsTest", "CareAcknowledgementMigrationTest", "CareReportTest", "CareFlowTest",
           "CombinedCareMigrationTest", "NativeMergeComponentsTest", "PatientDoctorUiTest", "DoctorLocalPreviewConsentTest"]


def utc():
    return dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z")


def write(name, value):
    path = OUT / name
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(value, bytes): path.write_bytes(value)
    elif isinstance(value, str): path.write_text(value, encoding="utf-8")
    else: path.write_text(json.dumps(value, indent=2), encoding="utf-8")


def cmd(*args, timeout=90, check=True):
    p = subprocess.run([str(a) for a in args], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)
    if check and p.returncode:
        raise RuntimeError(f"Command failed ({p.returncode}): {args[0]}\n" + p.stdout.decode(errors="replace")[-4000:])
    return p.stdout


def adb(*args, **kwargs):
    return cmd(ADB, "-s", SERIAL, *args, **kwargs)


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest().upper()


def check_run(run_id):
    data = json.loads((OUT / "build-run.json").read_text())
    assert str(data["id"]) == str(run_id)
    assert data["repository"]["full_name"] == os.environ["GITHUB_REPOSITORY"]
    assert data["head_repository"]["full_name"] == os.environ["GITHUB_REPOSITORY"]
    assert data["path"] == ".github/workflows/android-build.yml"
    assert data["event"] == "workflow_dispatch"
    assert data["status"] == "completed" and data["conclusion"] == "success"
    assert re.fullmatch(r"[a-f0-9]{40}", data["head_sha"])
    return data


def unique(pattern):
    files = list(ART.rglob(pattern))
    assert len(files) == 1, (pattern, len(files))
    return files[0]


def prepare(run_id):
    data = check_run(run_id)
    receipt = unique("build-source.txt")
    assert receipt.read_text().strip() == data["head_sha"], "API head and build receipt differ"
    manifest = unique("build-sha256.txt")
    verified = []
    for line in manifest.read_text().splitlines():
        expected, relative = line.split(maxsplit=1)
        relative = relative.lstrip("*")
        path = (manifest.parent / relative).resolve()
        assert path.is_relative_to(ART.resolve()), "Artifact hash path escaped download directory"
        assert sha(path) == expected.upper(), f"Artifact hash mismatch: {relative}"
        verified.append({"path": relative, "sha256": expected.upper(), "bytes": path.stat().st_size})
    debug = unique("app-debug.apk")
    test = unique("app-debug-androidTest.apk")
    releases = [p for p in ART.rglob("*.apk") if "/release/" in p.as_posix()]
    assert len(releases) == 1
    release = releases[0]
    for path, package in [(debug, DEBUG), (release, RELEASE)]:
        badging = cmd(TOOLS / "aapt2", "dump", "badging", path).decode(errors="replace")
        assert f"package: name='{package}'" in badging
        assert "versionCode='6'" in badging and "versionName='0.5.0'" in badging
        write("package-" + package + ".txt", badging)
    # Runner-only signing key; never uploaded and never substituted for the private store signer.
    signing = Path(os.environ["RUNNER_TEMP"]) / "sahara-synthetic-signing"
    signing.mkdir(exist_ok=True)
    key = signing / "runtime.jks"
    cmd("keytool", "-genkeypair", "-noprompt", "-keystore", key, "-alias", "runtime", "-storepass", "android", "-keypass", "android",
        "-dname", "CN=Sahara Synthetic CI", "-validity", "2", "-keyalg", "RSA", "-keysize", "2048")
    optimized = signing / "sahara-optimized-ci.apk"
    cmd(TOOLS / "apksigner", "sign", "--ks", key, "--ks-key-alias", "runtime", "--ks-pass", "pass:android", "--key-pass", "pass:android", "--out", optimized, release)
    cert = cmd(TOOLS / "apksigner", "verify", "--verbose", "--print-certs", optimized).decode(errors="replace")
    write("optimized-ci-signature.txt", cert)
    info = {"sourceCommit": data["head_sha"], "buildRunId": run_id, "buildUrl": data["html_url"],
            "harnessCommit": os.environ["GITHUB_SHA"], "verifiedArtifacts": verified,
            "debug": str(debug), "test": str(test), "unsignedRelease": str(release), "optimizedCiApk": str(optimized),
            "debugSha256": sha(debug), "testSha256": sha(test), "unsignedReleaseSha256": sha(release),
            "optimizedCiSha256": sha(optimized), "optimizedSignature": "Ephemeral CI key; differs from the private store upload key. Code and R8 output unchanged.",
            "preparedUtc": utc()}
    write("artifacts.json", info)


def instrument(name, classes, expected):
    started = time.monotonic()
    output = adb("shell", "am", "instrument", "-w", "-r", "-e", "class", classes,
                 DEBUG + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=2100, check=False).decode(errors="replace")
    write(name + ".txt", output)
    cases, current = [], {}
    for line in output.splitlines():
        m = re.match(r"INSTRUMENTATION_STATUS: (class|test)=(.*)", line)
        if m: current[m[1]] = m[2]
        m = re.match(r"INSTRUMENTATION_STATUS_CODE: (-?\d+)", line)
        if m and int(m[1]) != 1 and "test" in current:
            status = int(m[1])
            cases.append(dict(current, status=status, result={0:"pass", -4:"skipped", -3:"ignored", -2:"failure", -1:"error"}.get(status, "unknown")))
            current = {}
    counts = {key:sum(t["result"] == key for t in cases) for key in ("pass", "skipped", "ignored", "failure", "error", "unknown")}
    complete = len(cases) == expected and not any(counts[k] for k in ("failure", "error", "unknown")) and f"OK ({expected} test" in output
    if expected == 36:
        skipped = [case["test"] for case in cases if case["result"] == "skipped"]
        complete = complete and counts["pass"] == 35 and skipped == ["signInLoadsAccountRecordsOnToday"] and counts["ignored"] == 0
    else:
        complete = complete and counts["pass"] == expected and not counts["skipped"] and not counts["ignored"]
    result = {"tests":cases, "counts":counts, "expected":expected, "matrixComplete":complete,
              "durationSeconds":round(time.monotonic()-started, 3), "finishedUtc":utc()}
    write(name + ".json", result)
    return result


class Ui:
    """Targets only text, descriptions and fields present in the current UIAutomator tree."""
    def __init__(self, prefix, package, screenshots=False):
        self.prefix, self.package, self.screenshots = prefix, package, screenshots
        self.number = 0
        self.actions = []
    def dump(self, label="state"):
        self.number += 1
        stem = f"{self.prefix}/{self.number:03}-{label}"
        for attempt in range(3):
            try:
                adb("shell", "uiautomator", "dump", "/sdcard/sahara-runtime.xml")
                raw = adb("exec-out", "cat", "/sdcard/sahara-runtime.xml")
                tree = ET.fromstring(raw)
                break
            except Exception:
                if attempt == 2: raise
                time.sleep(1)
        write(stem + ".xml", raw)
        if self.screenshots: write(stem + ".png", adb("exec-out", "screencap", "-p"))
        words = " ".join(n.get("text", "") for n in tree.iter("node"))
        assert "isn't responding" not in words and "is not responding" not in words, "OS ANR overlay obscures UI"
        return tree
    @staticmethod
    def bounds(node):
        a = [int(v) for v in re.findall(r"\d+", node.get("bounds", ""))]
        assert len(a) == 4 and a[2] > a[0] and a[3] > a[1], "Node is not visibly bounded"
        return a
    def ime(self):
        text = adb("shell", "dumpsys", "window").decode(errors="replace")
        for line in text.splitlines():
            if ("mType=ime" in line or "type=ime" in line) and ("mVisible=true" in line or "visible=true" in line):
                m = re.search(r"(?:mFrame|frame)=\[(\d+),(\d+)\]\[(\d+),(\d+)\]", line)
                if m and int(m[4]) > int(m[2]): return [int(v) for v in m.groups()]
        return None
    def swipe(self, tree, up=True):
        viewport = self.bounds(next(tree.iter("node")))
        ime = self.ime()
        bottom = min(viewport[3] - 180, (ime[1] - 35) if ime else viewport[3] - 180)
        top = max(220, viewport[1] + 190)
        assert bottom > top + 100
        x = (viewport[0] + viewport[2]) // 2
        start, end = (bottom, top) if up else (top, bottom)
        adb("shell", "input", "swipe", str(x), str(start), str(x), str(end), "420")
        time.sleep(.4)
    def find(self, value, desc=False, scroll=True, reverse=False):
        key = "content-desc" if desc else "text"
        # Long doctor pages can leave the step chips above the viewport. Search down,
        # then back up through the actual scroll surface rather than assuming coordinates.
        for attempt in range(31 if scroll else 1):
            tree = self.dump("find")
            found = [n for n in tree.iter("node") if n.get(key) == value and n.get("enabled") == "true"]
            if found: return found[-1] if reverse else found[0]
            if scroll and attempt < 30: self.swipe(tree, up=attempt < 10)
        raise AssertionError("Visible target not found: " + value)
    def tap(self, value, desc=False, scroll=True, reverse=False):
        node = self.find(value, desc, scroll, reverse)
        a = self.bounds(node)
        adb("shell", "input", "tap", str((a[0]+a[2])//2), str((a[1]+a[3])//2))
        self.actions.append({"action":"tap", "label":value, "bounds":a, "atUtc":utc()})
        time.sleep(.4)
    def field(self, label, value):
        for attempt in range(11):
            tree = self.dump("field")
            found = [n for n in tree.iter("node") if n.get("class") == "android.widget.EditText" and
                     (n.get("hint") == label or any(d.get("text") == label for d in n.iter("node")))]
            if found: break
            if attempt == 10: raise AssertionError("Visible field not found: " + label)
            self.swipe(tree)
        a = self.bounds(found[0])
        adb("shell", "input", "tap", str((a[0]+a[2])//2), str((a[1]+a[3])//2))
        value.encode("ascii")
        adb("shell", "input", "text", value.replace(" ", "%s"))
        time.sleep(.6)
        self.actions.append({"action":"fill", "label":label, "syntheticValue":value, "atUtc":utc()})
    def expect(self, value, desc=False):
        return self.find(value, desc)
    def absent(self, values):
        tree = self.dump("absence")
        present = {n.get("text") for n in tree.iter("node")} | {n.get("content-desc") for n in tree.iter("node")}
        assert not set(values) & present, "Unexpected cloud action: " + str(set(values) & present)
    def checkbox(self, label):
        # These rows expose a separate Checkbox; tapping their plain label is not an action.
        self.find(label)
        tree = self.dump("checkbox")
        parents = {child: parent for parent in tree.iter() for child in parent}
        text = next(n for n in tree.iter("node") if n.get("text") == label)
        parent = parents.get(text)
        target = None
        while parent is not None and target is None:
            candidates = [n for n in parent.iter("node") if n.get("checkable") == "true"]
            if len(candidates) == 1: target = candidates[0]
            elif len(candidates) > 1:
                # Adjacent checkbox in the same horizontal row, derived from its actual bounds.
                a = self.bounds(text)
                aligned = [n for n in candidates if abs((self.bounds(n)[1]+self.bounds(n)[3])//2 - (a[1]+a[3])//2) < 80]
                if len(aligned) == 1: target = aligned[0]
            parent = parents.get(parent)
        assert target is not None, "Observed checkbox not found for label: " + label
        assert target.get("enabled") == "true"
        if target.get("checked") != "true":
            a = self.bounds(target)
            adb("shell", "input", "tap", str((a[0]+a[2])//2), str((a[1]+a[3])//2))
            self.actions.append({"action":"check", "label":label, "bounds":a, "atUtc":utc()})
            time.sleep(.4)
    def disabled(self, label):
        tree = self.dump("disabled-control")
        parents = {child: parent for parent in tree.iter() for child in parent}
        node = next(n for n in tree.iter("node") if n.get("text") == label)
        current = node
        while current is not None:
            if current.get("enabled") == "false": return
            current = parents.get(current)
        raise AssertionError("Control unexpectedly enabled: " + label)
    def key(self, key="KEYCODE_BACK"):
        adb("shell", "input", "keyevent", key); time.sleep(.4)
    def restart(self):
        adb("shell", "am", "force-stop", self.package)
        adb("shell", "am", "start", "-W", "-n", self.package + "/in.wedevit.care.MainActivity")
        time.sleep(1)
    def save_with_ime(self, text, label):
        before = self.ime()
        assert before is not None, "Real soft keyboard did not open"
        node = self.find(text, reverse=True)
        after = self.ime()
        assert after is not None, "Save reachability must be checked while IME remains open"
        bounds = self.bounds(node)
        # Scroll the ordinary editor content only; never drag the modal sheet handle.
        for _ in range(7):
            if bounds[3] <= after[1]: break
            self.swipe(self.dump("keyboard-scroll"))
            node = self.find(text, reverse=True); bounds = self.bounds(node); after = self.ime()
            assert after is not None
        assert bounds[3] <= after[1], "Save remains behind keyboard"
        write(self.prefix + "/" + label + "-ime.txt", adb("shell", "dumpsys", "input_method"))
        write(self.prefix + "/" + label + "-window.txt", adb("shell", "dumpsys", "window"))
        self.actions.append({"action":"verified-save-above-ime", "label":label, "saveBounds":bounds, "imeBounds":after})
        self.tap(text, reverse=True)
    def today(self):
        self.tap("Today", scroll=False)


def optimized_smoke(info):
    ui = Ui("optimized-ui", RELEASE)
    result = {"startedUtc":utc(), "sourceCommit":info["sourceCommit"], "apkSha256":info["optimizedCiSha256"],
              "signerQualification":info["optimizedSignature"], "screenshotsTaken":False, "flagSecurePreserved":True, "checks":[]}
    try:
        adb("install", "-r", info["optimizedCiApk"], timeout=180)
        ui.restart()
        ui.tap("Add a person")
        ui.field("Name", "CI Release Fictional Person")
        ui.save_with_ime("Continue", "person")
        ui.expect("CI Release Fictional Person")
        result["checks"].append("Person saved with actual IME open and Continue above keyboard")
        ui.tap("Calendar")
        ui.tap("Add to calendar", desc=True)
        ui.field("What is planned?", "CI fictional follow-up")
        ui.save_with_ime("Save to calendar", "calendar")
        ui.expect("CI fictional follow-up")
        ui.restart(); ui.tap("Calendar"); ui.expect("CI fictional follow-up")
        ui.tap("Edit CI fictional follow-up", desc=True)
        ui.expect("CI fictional follow-up"); ui.key()
        result["checks"].append("Calendar saved with IME open; value survives process restart and reopens for editing")
        ui.key(); ui.tap("Care team")
        ui.tap("Add task")
        ui.field("What needs doing?", "CI fictional report collection")
        ui.field("Who is this for?", "Fictional helper")
        ui.save_with_ime("Add task", "task")
        ui.expect("CI fictional report collection")
        ui.restart(); ui.tap("Care team"); ui.expect("CI fictional report collection")
        result["checks"].append("Named care task saved with IME open and survives process restart")
        ui.key(); ui.tap("Lab history")
        ui.tap("Add a lab result", desc=True)
        ui.field("Test name on report", "Synthetic value")
        ui.field("Value", "12.4")
        ui.field("Printed unit", "g/dL")
        ui.save_with_ime("Save result", "labs")
        ui.expect("12.4 g/dL")
        ui.restart(); ui.tap("Lab history"); ui.expect("12.4 g/dL")
        result["checks"].append("Exact numeric lab value/unit saved with IME open and survives process restart")
        ui.key()
        ui.tap("Change view, Family", desc=True, scroll=False); ui.tap("Doctor", scroll=False)
        ui.tap("Talk", scroll=False); ui.tap("Record conversation")
        ui.expect("Before we record")
        ui.expect("We understand the recording stays privately on this device.")
        ui.absent(["Optional free-demo transcription", "Transcribe recording",
                   "Everyone agrees to upload this fictional demo recording to Supabase and Google Gemini. It contains no real patient information, personal details or confidential information."])
        ui.disabled("Start recording")
        ui.tap("Cancel")
        result["checks"].append("Main care recording consent is local-only in optimized release; start is disabled without consent, no cloud-processing option shown, microphone never opened")
        ui.key(); ui.tap("Change view, Doctor", desc=True, scroll=False); ui.tap("Family", scroll=False)
        ui.tap("More", scroll=False); ui.tap("Doctor login")
        ui.expect("Preview account prefilled")
        ui.tap("Open workspace")
        ui.tap("Patients", scroll=False); ui.tap("Asha")
        ui.expect("Sample record · not connected to a patient account")
        ui.tap("Conversations")
        ui.tap("Open conversation workspace")
        ui.tap("2 Document")
        ui.expect("Record conversation")
        ui.absent(["Record & transcribe", "Sign in for AI", "Sahara AI sign-in", "Transcribe & draft Care Note"])
        ui.tap("Record conversation")
        ui.expect("Before recording")
        ui.absent(["Separately agrees to cloud processing"])
        ui.tap("Cancel")
        result["checks"].append("Doctor preview uses local consent; no upload/authentication action or microphone invoked")
        ui.tap("Load sample AI draft")
        ui.tap("3 Review")
        ui.checkbox("I checked the original source text")
        ui.checkbox("I reviewed every section and unresolved point")
        ui.tap("Publish preview version")
        ui.expect("Care Note · Version 1")
        ui.tap("Create preview PDF")
        ui.expect("Preview PDF created on this device")
        result["checks"].append("Explicitly fictional preview version and actual PDF created in optimized UI")
        ui.key(); ui.key(); ui.key()
        # Return through actual workspace navigation without altering the main care person.
        ui.tap("Account", scroll=False); ui.tap("Back to Sahara")
        ui.expect("CI Release Fictional Person")
        result["checks"].append("Doctor workspace exit returns to the original main care person")
        result["passed"] = True
    except Exception as error:
        result["passed"] = False; result["failure"] = str(error)
        write("optimized-ui/failure-trace.txt", traceback.format_exc())
        try: ui.dump("failure")
        except Exception: pass
    finally:
        result["actions"] = ui.actions
        result["finishedUtc"] = utc()
        result["limitations"] = ["Fresh CI installation; local Windows emulator databases were not touched.", "Ephemeral signer differs from final store signer.", "No physical-device, real speech, AI-provider or clinical validation.", "FLAG_SECURE respected; UIAutomator XML and actual keyboard/window evidence only."]
        write("optimized-release-smoke.json", result)
    return result


def run():
    info = json.loads((OUT / "artifacts.json").read_text())
    summary = {"sourceCommit":info["sourceCommit"], "harnessCommit":info["harnessCommit"], "startedUtc":utc(),
               "freshDisposableEmulator":True, "physicalDevice":False, "credentialsInjected":False,
               "aiProviderCalls":False, "microphoneOpened":False, "windowsEmulatorDataTouched":False}
    for key in ["window_animation_scale", "transition_animation_scale", "animator_duration_scale"]:
        adb("shell", "settings", "put", "global", key, "0")
    adb("shell", "wm", "size", "720x1600"); adb("shell", "wm", "density", "300")
    adb("shell", "settings", "put", "secure", "show_ime_with_hard_keyboard", "1")
    summary["environment"] = {key:adb("shell", *args).decode(errors="replace").strip() for key,args in {
        "size":["wm","size"], "density":["wm","density"], "pageSize":["getconf","PAGESIZE"],
        "api":["getprop","ro.build.version.sdk"], "deviceMemory":["cat","/proc/meminfo"]}.items()}
    summary["sixteenKbRuntimeTest"] = summary["environment"]["pageSize"] == "16384"
    write("host-memory.txt", cmd("free", "-m"))
    adb("install", "-r", info["debug"], timeout=180); adb("install", "-r", info["test"], timeout=180)
    adb("logcat", "-c")
    summary["matrix"] = instrument("matrix-36", ",".join("in.wedevit.care."+c for c in CLASSES), 36)
    summary["pdfSupplemental"] = instrument("pdf-pagination", "in.wedevit.care.DoctorWorkflowFlowTest#longPublishedPreviewExportsToMultipleReadablePages", 1)
    adb("pull", "/sdcard/Android/data/"+DEBUG+"/files/verification", str(OUT / "debug-captures"), check=False)
    # New doctor surfaces are also captured in debug mode. FLAG_SECURE on optimized
    # release remains intact; these images must never be labelled release screenshots.
    try:
        ui=Ui("debug-ui", DEBUG, screenshots=True); ui.restart(); ui.dump("entry")
        ui.tap("Doctor login"); ui.dump("doctor-preview-login")
        ui.tap("Open workspace"); ui.dump("doctor-workspace")
        ui.tap("Patients", scroll=False); ui.tap("Asha"); ui.dump("fictional-patient-hub")
        ui.tap("Conversations"); ui.tap("Open conversation workspace"); ui.tap("2 Document")
        ui.expect("Record conversation"); ui.dump("doctor-local-capture")
        ui.tap("Record conversation"); ui.expect("Before recording"); ui.dump("doctor-consent-unchecked")
        ui.tap("Cancel")
        summary["debugCapture"] = "Actual current-source debug doctor images captured; root must inspect readability, insets and absence of overlays. Original matrix screenshots are preserved separately."
    except Exception as error: summary["debugCapture"] = "Capture failed: " + str(error)
    summary["optimizedRelease"] = optimized_smoke(info)
    write("logcat.txt", adb("logcat", "-d", "-v", "brief", check=False))
    summary["finishedUtc"] = utc()
    summary["passed"] = summary["matrix"]["matrixComplete"] and summary["pdfSupplemental"]["matrixComplete"] and summary["optimizedRelease"]["passed"]
    summary["qualification"] = "CI emulator only; 36-test matrix and separate PDF check are distinguished. Component fixtures do not establish real doctor assignment or patient delivery. No local Room upgrade claimed."
    write("runtime-summary.json", summary)
    print(json.dumps({"matrix":summary["matrix"]["counts"], "pdf":summary["pdfSupplemental"]["counts"],
                      "optimizedPassed":summary["optimizedRelease"]["passed"], "passed":summary["passed"]}, indent=2))
    return 0 if summary["passed"] else 1


if __name__ == "__main__":
    parser=argparse.ArgumentParser(); parser.add_argument("mode", choices=["check-run", "prepare", "run"]); parser.add_argument("--run-id")
    args=parser.parse_args(); OUT.mkdir(parents=True,exist_ok=True)
    try:
        if args.mode=="check-run": check_run(args.run_id)
        elif args.mode=="prepare": prepare(args.run_id)
        else: sys.exit(run())
    except Exception:
        write("harness-failure.txt", traceback.format_exc()); raise
