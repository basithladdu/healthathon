"""Give every bundled palliative centre an approximate map position, offline and reproducibly.

Inputs (downloaded once, never bundled; see DATA_LICENSE.md, all CC BY 4.0 by GeoNames, www.geonames.org):
  * --gazetteer  https://download.geonames.org/export/dump/IN.zip               (places and districts, WGS84)
  * --postal     https://download.geonames.org/export/zip/IN.zip                (post-office names per PIN)
  * --admin1     https://download.geonames.org/export/dump/admin1CodesASCII.txt (state codes)
  * --directory  android/app/src/main/assets/india.json (the bundled directory; read only)

Output: android/app/src/main/assets/india-geo.json, {"centres": {id: [lat, lon, precision]}, ...}: per-centre results only.

The postal file's own coordinates are not used: checked against towns named in the same addresses they were a
median 15 km off, and half were further. The PIN is used only for the names of its post offices, which are then
looked up in the gazetteer.

Rules, in order (the first that applies wins):
  exact     the directory row already has latitude/longitude: kept unchanged
  pincode   the address PIN's post-office localities found in the gazetteer, within PIN_SPREAD_KM of each other and,
            when the address also names a town, within PIN_TOWN_KM of it: their median
  city      a town or locality named in the address (or the listed city) in the centre's state, inside the listed
            district when there is one: the largest town of that name, or a tight group of small places of that name
  district  the listed district, or a district named in the address: the gazetteer's district point
Anything else stays off the map with its address only. No state-level guesses.

Usage: python3 -I scripts/geocode_centres.py --gazetteer dump/IN.zip --postal zip/IN.zip --admin1 admin1CodesASCII.txt [--check]
"""
import argparse
import hashlib
import io
import json
import math
import re
import statistics
import sys
import zipfile
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "android" / "app" / "src" / "main" / "assets"
SOURCES = {"gazetteer": "https://download.geonames.org/export/dump/IN.zip",
           "postal": "https://download.geonames.org/export/zip/IN.zip",
           "admin1": "https://download.geonames.org/export/dump/admin1CodesASCII.txt"}
LICENCE = "CC BY 4.0, GeoNames (www.geonames.org)"
PIN_SPREAD_KM = 8.0
PIN_TOWN_KM = 15.0
COARSE_KM = 50.0
CITY_SPREAD_KM = 20.0
BIG_TOWN = 5000

PIN = re.compile(r"(?<!\d)([1-8]\d{2})\s?(\d{3})(?!\d)")
OFFICE_SUFFIX = re.compile(r"\s+(h\.?\s*o|s\.?\s*o|b\.?\s*o|g\.?\s*p\.?\s*o|p\.?\s*o)\.?$")

# Other spellings seen in the directory or gazetteer. key() applies this to both sides, so either spelling matches.
ALIAS = {"trivandrum": "thiruvananthapuram", "trichur": "thrissur", "calicut": "kozhikode", "kasargod": "kasaragod",
         "ahilyanagar": "ahmednagar", "ahmadnagar": "ahmednagar", "bangalore": "bengaluru", "bangaloreurban": "bengaluru",
         "bengaluruurban": "bengaluru", "bangalorerural": "bengalururural", "mysore": "mysuru", "belgaum": "belagavi",
         "gulbarga": "kalaburagi", "shimoga": "shivamogga", "tumkur": "tumakuru", "bellary": "ballari", "cochin": "kochi",
         "alleppey": "alappuzha", "quilon": "kollam", "palghat": "palakkad", "cannanore": "kannur", "bombay": "mumbai",
         "gurugram": "gurgaon", "gurugaon": "gurgaon", "madras": "chennai", "calcutta": "kolkata", "trichy": "tiruchirappalli",
         "tiruchirapalli": "tiruchirappalli", "tirupur": "tiruppur", "thoothukudi": "tuticorin", "vizag": "visakhapatnam",
         "rrd": "rangareddy", "rrdistrict": "rangareddy", "kvrangareddy": "rangareddy", "ernakulum": "ernakulam",
         "sarguja": "surguja", "bhubaneshwar": "bhubaneswar", "nilgiri": "nilgiris", "thenilgiris": "nilgiris",
         "mahbubnagar": "mahabubnagar", "jangoan": "jangaon", "pondicherry": "puducherry", "kutch": "kachchh",
         "prayagraj": "allahabad", "mussourie": "mussoorie", "dharamshala": "dharamsala",
         "andamanandnicobarislands": "andamanandnicobar"}

# Address words that name a kind of place, not a place: a locality called "Civil Hospital" or "Bellary Road" is no town.
GENERIC = re.compile(r"(hospital|college|clinic|centre|center|road|marg|street|lane|colony|campus|bazar|market|"
                     r"station|block|sector|layout|cross|main|society|trust|care|palliative|medical|institute|mission|"
                     r"school|church|temple|junction|house|building|floor|opposite|near|behind|^nagar$)")


def key(text):
    k = re.sub(r"[^a-z0-9]", "", text.lower())
    return ALIAS.get(k, k)


def median_point(points):
    return (round(statistics.median(p[0] for p in points), 4), round(statistics.median(p[1] for p in points), 4))


def km(a, b):
    lat = math.radians((a[0] + b[0]) / 2)
    return math.hypot((a[0] - b[0]) * 111.2, (a[1] - b[1]) * 111.2 * math.cos(lat))


def tight(points, limit):
    """Median of the points when every one lies within limit km of it, else None."""
    if not points:
        return None
    centre = median_point(points)
    return centre if all(km(centre, p) <= limit for p in points) else None


class Place(tuple):
    """(lat, lon) plus population, district code and whether the gazetteer marks it as a seat of government."""

    def __new__(cls, lat, lon, population=0, district="", town=False):
        place = super().__new__(cls, (lat, lon))
        place.population, place.district, place.town = population, district, town
        return place


class Gazetteer:
    """Places by (state code, name), district points by (state code, name), and post-office names by PIN."""

    def __init__(self, places, districts, offices, states):
        self.places = defaultdict(list)
        self.district_points = {}
        self.offices = defaultdict(set)
        self.coarse_points = defaultdict(list)
        self.states = states
        for code1, names, lat, lon, population, code2, feature in places:
            for n in names:
                self.places[(code1, n)].append(Place(lat, lon, population, code2, feature.startswith("PPLA") or feature == "PPLC"))
        for code1, names, lat, lon, code2 in districts:
            for n in names:
                self.district_points.setdefault((code1, n), (lat, lon, code2))
        for pin, office, code1, lat, lon in offices:
            self.offices[pin].add(key(OFFICE_SUFFIX.sub("", office.strip().lower())))
            if code1:
                self.coarse_points[(code1, pin)].append((lat, lon))

    def state(self, name):
        return self.states.get(key(name))

    def district(self, state, name):
        return self.district_points.get((state, key(name))) if name else None

    def coarse(self, state, pin):
        """The postal file's own median for a PIN in this state: too rough to map, good enough to tell same-named places apart."""
        points = self.coarse_points.get((state, pin))
        return median_point(points) if points else None

    def candidates(self, state, name, district=None, near=None):
        found = self.places.get((state, name), [])
        if district:
            found = [p for p in found if p.district == district]
        if near:
            found = [p for p in found if km(near, p) <= COARSE_KM]
        return found

    def town(self, state, name, district=None, near=None, big_only=False):
        found = self.candidates(state, name, district, near)
        big = [p for p in found if p.population >= BIG_TOWN or p.town]
        if big:
            return tuple(max(big, key=lambda p: p.population))
        return None if big_only else tight(found, CITY_SPREAD_KM)

    def pincode(self, state, pin, district=None, near=None):
        coarse = self.coarse(state, pin)
        if not coarse:
            return None
        points = []
        for name in self.offices.get(pin, ()):
            if not usable(name):
                continue
            found = self.candidates(state, name, district, coarse)
            if near:
                found = [p for p in found if km(near, p) <= PIN_TOWN_KM]
            if found and tight(found, 2.0):
                points.append(median_point(found))
        centre = tight(points, PIN_SPREAD_KM)
        return None if centre and near and km(centre, near) > PIN_TOWN_KM else centre


def _open_text(path, member):
    raw = Path(path).read_bytes()
    text = zipfile.ZipFile(io.BytesIO(raw)).read(member).decode("utf-8") if str(path).endswith(".zip") else raw.decode("utf-8")
    return text, hashlib.sha256(raw).hexdigest()


def load(gazetteer, postal, admin1):
    states, digests = {}, {}
    text, digests["admin1"] = _open_text(admin1, "admin1CodesASCII.txt")
    for line in text.splitlines():
        f = line.split("\t")
        if f[0].startswith("IN.") and len(f) > 2:
            states[key(f[1])] = states[key(f[2])] = f[0][3:]
    places, districts = [], []
    text, digests["gazetteer"] = _open_text(gazetteer, "IN.txt")
    for line in text.splitlines():
        f = line.split("\t")
        if len(f) < 15 or f[8] != "IN" or f[6] not in ("P", "A"):
            continue
        names = {key(f[1]), key(f[2])} | {key(a) for a in f[3].split(",") if a and re.fullmatch(r"[A-Za-z .'\-()]+", a)}
        names.discard("")
        if f[6] == "P":
            places.append((f[10], names, float(f[4]), float(f[5]), int(f[14] or 0), f[11], f[7]))
        elif f[7] == "ADM2":
            districts.append((f[10], names, float(f[4]), float(f[5]), f[11]))
    text, digests["postal"] = _open_text(postal, "IN.txt")
    offices = [(f[1], f[2], states.get(key(f[3])), float(f[9]), float(f[10]))
               for f in (line.split("\t") for line in text.splitlines()) if len(f) >= 11 and f[0] == "IN" and f[9] and f[10]]
    return Gazetteer(places, districts, offices, states), digests


def expand(piece):
    """A part plus its words and neighbouring pairs ("Attingal Trivandrum" -> also "Attingal", "Trivandrum").
    A part with a generic word ("Bellary Road") stays whole, so its place-like word is not read as the town."""
    words = piece.split()
    if len(words) < 2 or GENERIC.search(key(piece)):
        return [piece]
    return [piece] + [" ".join(words[i:i + 2]) for i in range(len(words) - 1)] + words


def parts(address):
    """(later parts, first part) of an address without PIN digits; the first part is usually the centre's own name."""
    pieces = [re.sub(r"\b(dist|district|po|p\.o|tahsil|taluka|taluk|india)\b\.?", " ", p, flags=re.I).strip(" .-–:")
              for p in re.split(r"[,\n]+", re.sub(r"-\d{1,3}\b", " ", PIN.sub(" ", address)))]
    pieces = [p for p in pieces if p] or [""]
    return [n for p in pieces[1:] for n in expand(p)], (expand(pieces[0]) if pieces[0] else [])


def usable(name):
    return len(name) >= 4 and not GENERIC.search(name)


def locate(centre, gaz):
    """[lat, lon, precision] for one directory row, or None when nothing reliable matches."""
    lat, lon = centre.get("latitude"), centre.get("longitude")
    if isinstance(lat, (int, float)) and isinstance(lon, (int, float)):
        return [lat, lon, "exact"]
    state = gaz.state(centre.get("state", ""))
    if not state:
        return None
    address = centre.get("address", "")
    listed = gaz.district(state, centre.get("district", ""))
    code2 = listed[2] if listed else None
    later, first = parts(address)
    names = [n for n in [key(p) for p in later] + [key(centre.get("city", ""))] if usable(n)]
    # Words of the centre's own name count only when they name a real town ("… care centre Khammam").
    names += [n for n in (key(p) for p in first)
              if usable(n) and any(p.town or p.population >= BIG_TOWN for p in gaz.places.get((state, n), []))]
    pins = [m[0] + m[1] for m in reversed(PIN.findall(address))]
    near = next((c for c in (gaz.coarse(state, pin) for pin in pins) if c), None)
    # Narrowest first. The postal median can itself be far off, and districts get split, so each filter is dropped in
    # turn. Without a district or PIN to narrow it down, only a large town counts: a lone village of that name is too
    # likely the wrong one.
    tries = [(code2, near, False), (code2, None, False), (None, near, False), (None, None, True)]
    tries = [t for i, t in enumerate(tries) if (t[0] or t[1] or t[2]) and t not in tries[:i]]
    town = next((t for district, anchor, big_only in tries for t in (gaz.town(state, n, district, anchor, big_only) for n in names) if t), None)
    for pin in pins:
        point = gaz.pincode(state, pin, code2, town)
        if point:
            return [point[0], point[1], "pincode"]
    if town:
        return [round(town[0], 4), round(town[1], 4), "city"]
    for point in [listed] + [gaz.district(state, n) for n in names]:
        if point:
            return [round(point[0], 4), round(point[1], 4), "district"]
    return None


def build(directory, gaz, digests):
    centres = {c["id"]: r for c in directory for r in [locate(c, gaz)] if r}
    return {"sources": SOURCES, "sourceSha256": digests, "licence": LICENCE,
            "note": "pincode, city and district positions are area centres, not the building. Exact ones come from the directory.",
            "centres": centres}


def coverage(directory, overlay):
    counts = defaultdict(int)
    for c in directory:
        counts[overlay["centres"].get(c["id"], [None, None, "unmapped"])[2]] += 1
    return dict(counts)


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--gazetteer", required=True, help=SOURCES["gazetteer"])
    ap.add_argument("--postal", required=True, help=SOURCES["postal"])
    ap.add_argument("--admin1", required=True, help=SOURCES["admin1"])
    ap.add_argument("--directory", default=str(ASSETS / "india.json"))
    ap.add_argument("--out", default=str(ASSETS / "india-geo.json"))
    ap.add_argument("--check", action="store_true", help="fail if the output file differs from what would be written")
    args = ap.parse_args(argv)
    directory = json.loads(Path(args.directory).read_text(encoding="utf-8"))
    gaz, digests = load(args.gazetteer, args.postal, args.admin1)
    overlay = build(directory, gaz, digests)
    text = json.dumps(overlay, ensure_ascii=False, separators=(",", ":"), sort_keys=True) + "\n"
    print(json.dumps(coverage(directory, overlay), sort_keys=True))
    if args.check:
        if Path(args.out).read_text(encoding="utf-8") != text:
            print("india-geo.json is out of date", file=sys.stderr)
            return 1
        return 0
    Path(args.out).write_text(text, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
