"""Refresh Sahara's public, source-attributed India palliative-care listings.

Run with Python 3.12 and beautifulsoup4/requests installed. Raw HTML is cached in
output/research/india-directory-YYYYMMDD, outside the shipped Android assets.
Only directory rows are extracted: directory membership is not service validation.
"""
from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import re
import time
import unicodedata
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urljoin, urlparse

import requests
from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "android/app/src/main/assets"
PALLIUM = "https://palliumindia.org/clinics/"
IAPC = "https://www.palliativecare.in/"
STATE_ALIASES = {"Tamilnadu": "Tamil Nadu", "New Delhi": "Delhi", "Jammu & Kashmir": "Jammu and Kashmir",
                 "Andaman & Nicobar Islands": "Andaman and Nicobar Islands",
                 "Dadra & Nagar Haveli, Daman and Diu": "Dadra and Nagar Haveli and Daman and Diu",
                 "Pudducherry": "Puducherry", "Uttaranchal": "Uttarakhand"}
STATES = ["Andaman and Nicobar Islands", "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chandigarh",
          "Chhattisgarh", "Dadra and Nagar Haveli and Daman and Diu", "Delhi", "Goa", "Gujarat", "Haryana",
          "Himachal Pradesh", "Jammu and Kashmir", "Jharkhand", "Karnataka", "Kerala", "Ladakh", "Lakshadweep",
          "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Puducherry",
          "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal"]


def clean(value):
    return re.sub(r"\s+", " ", value.replace("\ufeff", "").replace("\xa0", " ")).strip()


def normal(value):
    return re.sub(r"[^a-z0-9]", "", unicodedata.normalize("NFKD", value).lower())


def lines(cell):
    clone = BeautifulSoup(str(cell), "html.parser")
    for element in clone.find_all(["br", "p", "div"]):
        if element.name == "br":
            element.replace_with("\n")
        else:
            element.insert_before("\n")
            element.insert_after("\n")
    return [clean(x) for x in clone.get_text().splitlines() if clean(x)]


def public_phones(cell):
    result = []
    for line in lines(cell):
        # Do not turn fax numbers, email numbers or extension digits into calls.
        line = re.split(r"fax\s*:|e-?mail\s*:|https?://|www\.", line, flags=re.I)[0]
        line = re.sub(r"[\w.+-]+@[\w.-]+", "", line)
        line = re.split(r"\b(?:extn?|extension)\.?\s*:?", line, flags=re.I)[0]
        for match in re.findall(r"(?<!\d)\+?\d[\d ()–−-]{5,}\d(?!\d)", line):
            value = clean(match).strip(" .-")
            digits = re.sub(r"\D", "", value)
            if 7 <= len(digits) <= 13 and digits not in [re.sub(r"\D", "", x) for x in result]:
                result.append(value)
    return result


def name_from(address_lines):
    title = re.sub(r"^\s*\d+[.)]\s*", "", address_lines[0])
    # Preserve wrapped institution names and generic departments' host hospital.
    if len(address_lines) > 1 and (re.search(r"(?:\bof|\band|\bMedical|\bRegional|\bResearch|[–-])$", title, re.I)
                                  or re.fullmatch(r"(?:Department of )?(?:Pain (?:&|and) )?Palliative (?:Care|Medicine)(?: Services)?[, .]*", title, re.I)):
        title += " " + address_lines[1]
    return title.strip(" ,")


def parse_page(html, state, url, publisher, as_of):
    soup = BeautifulSoup(html, "html.parser")
    results, rejected = [], []
    for table_idx, table in enumerate(soup.find_all("table")):
        # Limit to actual directory tables rather than arbitrary navigation tables.
        heads = " ".join(x.get_text(" ", strip=True) for x in table.find_all("tr")[:2]).lower()
        if not ("address" in heads or "contact details" in heads):
            continue
        heading = table.find_previous(["h2", "h3", "h4", "h5"])
        district = clean(heading.get_text(" ", strip=True)).title() if heading else ""
        if normal(district) in {normal(state), "palliativecarecentres", "palliativecaredirectoryofindia"} or len(district) > 55:
            district = ""
        if publisher == "IAPC":
            match = re.search(r"/palliative-care-centres-([^/]+)-of-kerala/?$", url)
            district = match.group(1).replace("-", " ").title() if match else ""
        for row_idx, row in enumerate(table.find_all("tr")):
            cells = row.find_all(["td", "th"], recursive=False)
            if not cells or row.find("th"):
                continue
            txt = [clean(c.get_text(" ", strip=True)) for c in cells]
            if any("name and address" in t.lower() or "name & address" in t.lower() for t in txt):
                continue
            if publisher == "Pallium India":
                if len(cells) == 4:
                    address_cell, service_cell, contact_cell = cells[1:]
                elif len(cells) == 3:
                    address_cell, service_cell, contact_cell = cells
                else:
                    rejected.append({"table": table_idx, "row": row_idx, "reason": "unexpected-column-count", "columns": len(cells)})
                    continue
            else:
                if len(cells) == 4:
                    address_cell, contact_cell, service_cell = cells[1:]
                elif len(cells) == 3:
                    address_cell, contact_cell, service_cell = cells
                else:
                    rejected.append({"table": table_idx, "row": row_idx, "reason": "unexpected-column-count", "columns": len(cells)})
                    continue
            address_lines = lines(address_cell)
            if not address_lines or len(clean(address_lines[0])) < 3:
                continue
            title = name_from(address_lines)
            first_bold = address_cell.find(["strong", "b"])
            bold_text = clean(first_bold.get_text(" ", strip=True)) if first_bold else ""
            if publisher == "Pallium India" and bold_text and clean(address_cell.get_text(" ", strip=True)).startswith(bold_text) and len(bold_text) < 190:
                title = re.sub(r"^\s*\d+[.)]\s*", "", bold_text).strip(" ,")
            if title.lower().startswith(("name", "contact", "sl no", "s. no")):
                continue
            # Pallium often embeds district headings inside table rows, with the
            # address cell populated and both service/contact cells empty.
            # These are navigation, not facilities; preserve them as context.
            if (publisher == "Pallium India" and len(address_lines) == 1 and
                    title.isupper() and not clean(service_cell.get_text()) and not clean(contact_cell.get_text())):
                district = title.title()
                continue
            service_text = clean(service_cell.get_text(" ", strip=True))
            services = []
            for regex, label in [(r"\bOP(?:D)?\b|out.?patient", "Clinic visits"), (r"\bIP\b|in.?patient|hospice", "Inpatient care"),
                                 (r"home\s*care|home.?based", "Home care"), (r"physiother", "Physiotherapy"), (r"day\s*care", "Day care")]:
                if re.search(regex, service_text, re.I):
                    services.append(label)
            phones = public_phones(contact_cell)
            # City is only used when explicitly published as a district heading.
            # Complete published address is retained for search/directions elsewhere.
            city = district
            record = {
                "id": "india-" + hashlib.sha256((state + "|" + title + "|" + clean(address_cell.get_text(" ", strip=True))).encode()).hexdigest()[:16],
                "name": title, "city": city, "district": district,
                "address": ", ".join(address_lines), "state": state, "phone": ", ".join(phones), "phoneNumbers": phones,
                "services": services, "listedServices": service_text, "morphine": "unknown",
                "directoryUrl": url, "sourceURL": url, "sourceAsOf": as_of,
                "sourceName": publisher, "checkedOn": as_of, "verification": "directory_listed",
                "listingStatus": "directory-listed", "independentlyVerified": False,
                "sources": [{"publisher": publisher, "url": url, "retrievedOn": as_of, "retrievedAt": as_of, "table": table_idx + 1, "row": row_idx + 1}],
            }
            morphine = re.search(r"morphine\s*:\s*(not\s+available|available)", service_text, re.I)
            if morphine:
                record["sourceListedMorphine"] = clean(morphine.group(1)).lower()
            # Only exact place-coordinate links are eligible. Map viewport @lat,
            # lon values and addresses requiring geocoding are intentionally ignored.
            for anchor in row.find_all("a", href=True):
                href = anchor["href"]
                coordinate = re.search(r"!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)", href)
                if not coordinate:
                    coordinate = re.search(r"(?:[?&](?:q|query)=|geo:)(-?\d+\.\d+),\s*(-?\d+\.\d+)", href)
                if coordinate:
                    latitude, longitude = map(float, coordinate.groups())
                    if -90 <= latitude <= 90 and -180 <= longitude <= 180:
                        record.update(latitude=latitude, longitude=longitude, coordinateSource=href, coordinateCheckedOn=as_of)
                        break
            results.append(record)
    return results, rejected


def run():
    parser = argparse.ArgumentParser()
    parser.add_argument("--date", default=datetime.now(timezone.utc).strftime("%Y-%m-%d"))
    parser.add_argument("--offline", action="store_true", help="Require cached source pages; do not fetch")
    args = parser.parse_args()
    cache = ROOT / "output/research" / ("india-directory-" + args.date.replace("-", ""))
    cache.mkdir(parents=True, exist_ok=True)
    session = requests.Session()
    session.headers["User-Agent"] = "Sahara public palliative-care directory research; low-rate factual extraction"
    ledger = []

    def get(url, label):
        path = cache / (label + ".html")
        if path.exists():
            raw = path.read_bytes()
            return raw.decode("utf-8"), {"url": url, "status": "cached", "sha256": hashlib.sha256(raw).hexdigest(), "rawPath": str(path.relative_to(ROOT)).replace("\\", "/")}
        if args.offline:
            return "", {"url": url, "status": "cache-missing"}
        time.sleep(0.3)
        try:
            response = session.get(url, timeout=60)
            if response.status_code != 200:
                return "", {"url": url, "status": response.status_code}
            response.encoding = "utf-8"
            raw = response.text.encode("utf-8")
            path.write_bytes(raw)
            return response.text, {"url": url, "resolvedUrl": response.url, "status": 200, "sha256": hashlib.sha256(raw).hexdigest(), "rawPath": str(path.relative_to(ROOT)).replace("\\", "/")}
        except requests.RequestException as error:
            return "", {"url": url, "status": "fetch-failed", "error": type(error).__name__}

    pallium_index, index_source = get(PALLIUM.rstrip("/"), "pallium-index")
    ledger.append(index_source)
    soup = BeautifulSoup(pallium_index, "html.parser")
    jobs = []
    for anchor in soup.find_all("a", href=True):
        label = clean(anchor.get_text(" ", strip=True))
        state = STATE_ALIASES.get(label, label)
        if state in STATES:
            jobs.append((state, urljoin(PALLIUM, anchor["href"]), "Pallium India", "pallium-" + normal(state)))
    iapc_index, index_source = get(IAPC + "palliative-care-directory-of-india/", "iapc-index")
    ledger.append(index_source)
    iapc_soup = BeautifulSoup(iapc_index, "html.parser")
    # Use source's published state list; successful directory pages are independently
    # checked for title and table format. A 404 remains a ledger gap, never a listing.
    for option in iapc_soup.select("select option"):
        label = clean(option.get_text(" ", strip=True))
        state = STATE_ALIASES.get(label, label)
        if state not in STATES:
            continue
        slug = label.lower().replace(" & ", "-").replace(" ", "-")
        if state == "Kerala":
            continue  # Its source landing page links district-specific directories.
        if state == "Puducherry":
            slug = "puducherry"
        jobs.append((state, IAPC + "palliative-care-centres-" + slug + "/", "IAPC", "iapc-" + normal(state)))
    # Kerala district URLs are explicitly linked from the live state page.
    kerala_html, kerala_meta = get(IAPC + "palliative-care-centres-kerala/", "iapc-kerala-index")
    ledger.append(kerala_meta)
    for anchor in BeautifulSoup(kerala_html, "html.parser").find_all("a", href=True):
        href = urljoin(IAPC, anchor["href"])
        if re.search(r"/palliative-care-centres-[^/]+-of-kerala/?$", href):
            jobs.append(("Kerala", href, "IAPC", "iapc-" + urlparse(href).path.strip("/")))
    # WordPress's public published-page index gives the actual directory URLs,
    # including historical spellings (Ernakulum/Pudducherry) used by this source.
    discovered_jobs = []
    for page_no in range(1, 11):
        discovery_url = IAPC + "wp-json/wp/v2/pages?search=palliative%20care%20centres&per_page=100&page=" + str(page_no)
        discovery_json, discovery_meta = get(discovery_url, "iapc-discovery-page-" + str(page_no))
        ledger.append(discovery_meta)
        try:
            pages = json.loads(discovery_json)
        except json.JSONDecodeError:
            break
        if not isinstance(pages, list):
            break
        for page in pages:
            href = page.get("link", "")
            match = re.search(r"/palliative-care-centres-([^/]+)/?$", href)
            if not match or match.group(1) == "kerala":
                continue
            slug = match.group(1)
            if slug.endswith("-of-kerala"):
                state = "Kerala"
            else:
                spelling = {"pudducherry": "Puducherry", "jammu-kashmir": "Jammu and Kashmir"}.get(slug, slug.replace("-", " ").title())
                state = STATE_ALIASES.get(spelling, spelling)
            if state in STATES:
                discovered_jobs.append((state, href, "IAPC", "iapc-" + ("palliative-care-centres-" + slug if state == "Kerala" else normal(state))))
        if len(pages) < 100:
            break
    # Prefer actual published-page URLs over inferred spelling variants.
    discovered_states = {job[0] for job in discovered_jobs}
    jobs = [job for job in jobs if job[2] != "IAPC" or job[0] not in discovered_states] + discovered_jobs
    jobs = list(dict.fromkeys(jobs))
    records, skipped = [], []

    def fetch(job):
        state, url, publisher, label = job
        html, meta = get(url, label)
        meta.update(state=state, publisher=publisher, retrievedOn=args.date)
        found, rejected = parse_page(html, state, meta.get("resolvedUrl", url), publisher, args.date)
        meta.update(parsedRows=len(found), rejectedRows=len(rejected))
        return found, meta, rejected

    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        for found, meta, rejected in pool.map(fetch, jobs):
            records.extend(found)
            ledger.append(meta)
            skipped.extend({"url": meta["url"], **row} for row in rejected)
            print(meta["publisher"], meta["state"], meta["status"], len(found), flush=True)

    # Preserve the original Karnataka asset byte-for-byte. Carry its curated names,
    # district labels and independently sourced coordinates into the national copy.
    original = json.loads((ASSETS / "karnataka.json").read_text(encoding="utf-8-sig"))
    original_hash = hashlib.sha256((ASSETS / "karnataka.json").read_bytes()).hexdigest()
    for old in original:
        matches = [r for r in records if r["state"] == "Karnataka" and normal(r["name"]) == normal(old["name"])]
        if not matches:
            restored = dict(old)
            restored.update(morphine="unknown", listingStatus="directory-listed", independentlyVerified=False,
                            sourceName="Pallium India", checkedOn=old["sourceAsOf"], verification="directory_listed",
                            sources=[{"publisher": "Pallium India", "url": old["directoryUrl"], "retrievedOn": old["sourceAsOf"], "retrievedAt": old["sourceAsOf"], "preservedOriginal": True}])
            restored["sourceListedMorphine"] = old.get("morphine", "unknown")
            records.append(restored)
        else:
            for record in matches:
                record["id"] = old["id"]
                for key in ["name", "city", "district", "latitude", "longitude", "coordinateSource", "coordinateCheckedOn", "phoneNote"]:
                    if key in old:
                        record[key] = old[key]

    unique, duplicates = [], []
    for record in records:
        exact = next((r for r in unique if r["state"] == record["state"] and
                      (r["id"] == record["id"] or
                       normal(r["name"]) == normal(record["name"]) and
                       (normal(r["address"]) == normal(record["address"]) or
                        set(map(normal, r["phoneNumbers"])) & set(map(normal, record["phoneNumbers"]))))), None)
        if exact:
            exact["sources"].extend(x for x in record["sources"] if x not in exact["sources"])
            # Preserve conflicting published details for review rather than
            # silently overwriting one directory's report with another's.
            variant = {k: record[k] for k in ["name", "address", "phoneNumbers", "listedServices", "sourceURL", "sourceAsOf"]}
            if any(record[k] != exact[k] for k in ["name", "address", "phoneNumbers", "listedServices"]):
                exact.setdefault("sourceVariants", []).append(variant)
            duplicates.append({"kept": exact["id"], "merged": record["id"], "reason": "preserved-original-id" if exact["id"] == record["id"] else "same-state-and-name-plus-address-or-phone"})
        else:
            unique.append(record)
    unique.sort(key=lambda r: (r["state"], r["district"], r["name"]))
    # Stable unique IDs even when the same historical ID occurs in two sources.
    ids = Counter(r["id"] for r in unique)
    for r in unique:
        if ids[r["id"]] > 1:
            r["id"] += "-" + hashlib.sha256(r["address"].encode()).hexdigest()[:6]
    counts = dict(sorted(Counter(r["state"] for r in unique).items()))
    result = {
        "retrievedOn": args.date, "totalCentres": len(unique), "stateAndUtCoverage": len(counts),
        "stateCounts": counts, "statesWithoutSourceListings": [s for s in STATES if s not in counts],
        "originalKarnataka": {"records": len(original), "sha256Unchanged": original_hash},
        "totalParsedRowsBeforeDedupe": len(records), "duplicatesMerged": len(duplicates),
        "withCoordinates": sum("latitude" in r and "longitude" in r for r in unique),
        "withPhone": sum(bool(r["phoneNumbers"]) for r in unique),
        "independentlyVerifiedCentres": 0,
        "caveats": ["Public directory-listed centres, not independently verified or an exhaustive census.",
                    "Retrieval date is not the source's last centre verification date.",
                    "Published contacts and services may be outdated; call before travel.",
                    "Morphine source text is historical listing information; no live stock claim is made.",
                    "No inferred coordinates, city centroids or geocoded guesses are used.",
                    "Exact same-state/name plus matching address/phone, or the same preserved Karnataka identity, is deduplicated; possible name/address variants remain."],
        "sources": ledger, "deduplication": duplicates, "parserSkippedRows": skipped,
    }
    assert len(unique) > len(original), "A national refresh cannot silently become a smaller Karnataka-only list"
    assert len({r["id"] for r in unique}) == len(unique)
    assert {r["id"] for r in original} <= {r["id"] for r in unique}, "Original Karnataka identities must remain addressable"
    assert all(r["name"] and r["state"] and r["address"] and r["sourceURL"] for r in unique)
    assert hashlib.sha256((ASSETS / "karnataka.json").read_bytes()).hexdigest() == original_hash
    (ASSETS / "india.json").write_text(json.dumps(unique, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    (ASSETS / "india-directory-sources.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items() if k not in {"sources", "deduplication", "parserSkippedRows"}}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    run()
