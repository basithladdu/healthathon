#!/usr/bin/env python3
"""Build the bundled offline medicine-name list from OHC's copy of NRCeS CDCI.

Usage (run with -I; the CDCI checkout is untrusted data, read only):
  git clone --depth 1 https://github.com/ohcnetwork/cdci /tmp/cdci
  python3 -I android/scripts/build-cdci-medicine-names.py /tmp/cdci \
      android/app/src/main/assets/medicine-names/cdci-names.tsv

Output: one medicine (ingredient) name per line, tab, SNOMED CT substance code
when CDCI's SubstanceMaster has the same name, else empty. Names only: every
strength, dose form and brand is left out, so the list can never suggest a dose.

Sources:
- data/source/SubstanceMaster.txt  (3,274 substance names with codes)
- data/source/GenericMaster.txt    (ingredient names before the first number,
  e.g. "Diclofenac potassium 50 mg and paracetamol 500 mg oral tablet" gives
  "Diclofenac potassium" and "Paracetamol"; this brings in the Indian names
  such as paracetamol that SNOMED's substance list spells acetaminophen)
BrandMaster/ProductMaster (brand names, 13.6 MB + 2 MB) are not used.
"""
import re
import subprocess
import sys
from pathlib import Path


def rows(path: Path):
    with path.open(encoding="utf-8-sig", newline="") as handle:
        next(handle)
        for line in handle:
            yield line.rstrip("\r\n").split("\t")


def clean(name: str) -> str:
    name = re.sub(r"\s+", " ", name).strip(" ,;/-")
    return name[:1].upper() + name[1:] if name else ""


def main(source: str, target: str) -> None:
    root = Path(source)
    data = root / "data" / "source"
    names: dict[str, tuple[str, str]] = {}
    for cols in rows(data / "SubstanceMaster.txt"):
        if len(cols) >= 2 and cols[0].strip().isdigit():
            name = clean(cols[1])
            if 2 <= len(name) <= 120 and not name[0].isdigit():
                names.setdefault(name.lower(), (name, cols[0].strip()))
    for cols in rows(data / "GenericMaster.txt"):
        if len(cols) < 2:
            continue
        for part in re.split(r"\s+and\s+", cols[1]):
            match = re.match(r"^([A-Za-z][A-Za-z ()',.-]*?)\s+\d", part.strip())
            if not match:
                continue
            # "Morphine (as morphine sulfate)" -> "Morphine"; both salt and base names are already listed.
            name = clean(match.group(1).split(" (as ")[0])
            if 2 <= len(name) <= 120 and "(" not in name:
                names.setdefault(name.lower(), (name, ""))
    commit = subprocess.run(["git", "-C", str(root), "rev-parse", "HEAD"], capture_output=True, text=True, check=True).stdout.strip()
    out = Path(target)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(f"# Medicine names from Common Drug Codes for India (CDCI), NRCeS / C-DAC Pune, CC BY 4.0, via github.com/ohcnetwork/cdci@{commit}. Names only; no strengths, forms or brands. See NOTICE.txt.\n")
        for _, (name, code) in sorted(names.items()):
            handle.write(f"{name}\t{code}\n")
    print(f"{len(names)} names -> {out} ({out.stat().st_size} bytes)")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
