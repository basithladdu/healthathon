"""Archive public OHC pages and selected CARE documentation without executing site code.

Website HTML and complete derived text are preserved separately from review notes.
Only same-domain public content links are followed. No accounts, forms, APIs or media.
"""
from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import re
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "output/research/care-website-20261023"
UA = "Mozilla/5.0 (compatible; Sahara-Public-Resource-Review/1.0)"
DOCS = [
    "https://docs.ohc.network/contributing/plugins/",
    "https://docs.ohc.network/deployments/palliative/",
    "https://docs.ohc.network/deployments/palliative/category/playbooks/",
    "https://docs.ohc.network/deployments/palliative/playbooks/patient-enrollment/",
    "https://docs.ohc.network/concepts/clinical/consent/",
    "https://docs.ohc.network/flows/clinical/consent/record-a-consent/",
    "https://docs.ohc.network/flows/clinical/consent/view-consents/",
    "https://docs.ohc.network/flows/clinical/consent/update-a-consent/",
    "https://docs.ohc.network/flows/clinical/consent/verify-a-consent/",
    "https://docs.ohc.network/references/clinical/consent/",
    "https://docs.ohc.network/concepts/clinical/notes/",
    "https://docs.ohc.network/concepts/clinical/questionnaire-response/",
]


def stamp():
    return datetime.now(timezone.utc).isoformat()


def get(url):
    for attempt in range(2):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.read(), r.status, r.geturl(), r.headers.get("Content-Type", "")
        except Exception:
            if attempt:
                raise
            time.sleep(0.5)


def normal(url):
    p = urllib.parse.urlsplit(url)
    if p.scheme not in {"http", "https"} or p.hostname != "ohc.network" or p.query:
        return None
    if re.search(r"\.[A-Za-z0-9]{1,6}$", p.path) or p.path.startswith(("/api/", "/_", "/admin/")):
        return None
    return "https://ohc.network" + (p.path.rstrip("/") or "")


def capture(url):
    try:
        data, status, final, content_type = get(url)
        if "text/html" not in content_type:
            return {"url": url, "status": status, "contentType": content_type, "readCoverage": "not-html"}, []
        soup = BeautifulSoup(data, "html.parser")
        links = sorted({v for a in soup.select("a[href]") if (v := normal(urllib.parse.urljoin(final, a["href"])))})
        title = soup.title.get_text(" ", strip=True) if soup.title else url
        for node in soup.select("script, style, noscript, svg"):
            node.decompose()
        full_text = soup.get_text("\n", strip=True)
        main = soup.find("main") or soup.find("article") or soup.body or soup
        for node in main.select("nav, footer, header"):
            node.decompose()
        text = main.get_text("\n", strip=True)
        slug = (urllib.parse.urlsplit(url).netloc + urllib.parse.urlsplit(url).path).strip("/").replace("/", "__")
        slug = re.sub(r"[^A-Za-z0-9_.-]", "_", slug)
        raw_path = DEST / "html" / (slug + ".html")
        text_path = DEST / "text" / (slug + ".txt")
        full_path = DEST / "text" / (slug + ".full.txt")
        for p, value in [(raw_path, data), (text_path, text.encode()), (full_path, full_text.encode())]:
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes(value)
        return {"url": url, "finalUrl": final, "status": status, "title": title,
                "retrievedUtc": stamp(), "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(),
                "htmlPath": raw_path.relative_to(DEST).as_posix(), "textPath": text_path.relative_to(DEST).as_posix(),
                "fullTextPath": full_path.relative_to(DEST).as_posix(), "mainTextWords": len(text.split()),
                "textSha256": hashlib.sha256(text.encode()).hexdigest(),
                "fullTextSha256": hashlib.sha256(full_text.encode()).hexdigest(),
                "readCoverage": "archived-and-text-extracted; semantic review recorded separately"}, links
    except Exception as e:
        return {"url": url, "status": "error", "error": str(e)}, []


def main():
    global DEST
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=DEST,
                        help="New, empty snapshot directory. Existing research is never overwritten.")
    args = parser.parse_args()
    DEST = args.output_dir.resolve()
    if DEST.exists() and any(DEST.iterdir()):
        raise SystemExit("Snapshot directory is not empty. Preserve it and choose a new --output-dir.")
    DEST.mkdir(parents=True, exist_ok=True)
    maps = {}
    for host in ["ohc.network", "docs.ohc.network"]:
        data, status, final, kind = get(f"https://{host}/sitemap.xml")
        (DEST / f"{host}-sitemap.xml").write_bytes(data)
        maps[host] = [node.text for node in ET.fromstring(data).iter() if node.tag.endswith("}loc")]
    queue = {normal(u) for u in maps["ohc.network"]} - {None}
    visited = set()
    rows = []
    while queue:
        batch = sorted(queue - visited)
        if not batch:
            break
        if len(visited) + len(batch) > 250:
            raise RuntimeError("Site exceeded 250 public pages; inspect scope before expanding")
        visited.update(batch)
        queue = set()
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            for row, links in pool.map(capture, batch):
                rows.append(row)
                queue.update(set(links) - visited)
        print(json.dumps({"stage": "main-site", "attempted": len(rows), "discoveredPending": len(queue)}), flush=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        for row, links in pool.map(capture, DOCS):
            rows.append(row)
    rows.sort(key=lambda r: r["url"])
    manifest = {"createdUtc": stamp(), "scope": "All sitemap-listed and recursively linked public HTML content on ohc.network, plus explicitly listed relevant CARE documentation. No media, subdomain-wide docs crawl or code execution.",
                "sitemapCounts": {host: len(urls) for host, urls in maps.items()},
                "publicMainPagesAttempted": len(visited), "selectedDocumentationPagesAttempted": len(DOCS),
                "rows": rows, "failures": [r for r in rows if r["status"] != 200],
                "interpretation": "Downloading/extracting is not semantic review, feature testing, independent deployment audit or clinical validation."}
    (DEST / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    corpus = []
    for row in rows:
        if row.get("textPath"):
            corpus.append("\n\n===== " + row["url"] + " =====\n" + (DEST / row["textPath"]).read_text(encoding="utf-8"))
    (DEST / "ALL_PUBLIC_PAGE_TEXT.txt").write_text("".join(corpus), encoding="utf-8")
    print(json.dumps({"complete": True, "pages": len(rows), "failures": len(manifest["failures"]), "mainTextWords": sum(r.get("mainTextWords", 0) for r in rows)}))


if __name__ == "__main__":
    main()
