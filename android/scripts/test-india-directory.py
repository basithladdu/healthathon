"""Directory source/data contracts; run with the scraper's Python dependencies."""
import hashlib
import importlib.util
import json
import sys
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("india_scraper", HERE / "scrape-india-directory.py")
scraper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scraper)
ASSETS = HERE.parent / "app/src/main/assets"


class DirectoryContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.records = json.loads((ASSETS / "india.json").read_text(encoding="utf-8"))
        cls.manifest = json.loads((ASSETS / "india-directory-sources.json").read_text(encoding="utf-8"))
        cls.original = json.loads((ASSETS / "karnataka.json").read_text(encoding="utf-8-sig"))

    def test_all_states_have_source_listings_with_unique_identity(self):
        self.assertEqual(set(scraper.STATES), {r["state"] for r in self.records})
        self.assertEqual(len(self.records), len({r["id"] for r in self.records}))
        self.assertEqual(len(self.records), self.manifest["totalCentres"])

    def test_original_karnataka_unchanged_and_still_addressable(self):
        self.assertEqual(hashlib.sha256((ASSETS / "karnataka.json").read_bytes()).hexdigest(), self.manifest["originalKarnataka"]["sha256Unchanged"])
        by_id = {r["id"]: r for r in self.records}
        for original in self.original:
            self.assertIn(original["id"], by_id)
            for key in ["latitude", "longitude", "coordinateSource", "coordinateCheckedOn"]:
                if key in original:
                    self.assertEqual(original[key], by_id[original["id"]][key])

    def test_no_claim_of_independent_verification_or_live_medicine_stock(self):
        self.assertTrue(all(r["verification"] == "directory_listed" and r["independentlyVerified"] is False for r in self.records))
        self.assertTrue(all(r["morphine"] == "unknown" for r in self.records))

    def test_every_record_has_public_source_and_retrieval_date(self):
        for record in self.records:
            self.assertTrue(record["name"] and record["address"] and record["sources"])
            self.assertRegex(record["checkedOn"], r"^\d{4}-\d{2}-\d{2}$")
            self.assertIn(scraper.urlparse(record["sourceURL"]).hostname, ["palliumindia.org", "www.palliativecare.in"])
            self.assertTrue(all(source["url"] and source["retrievedOn"] for source in record["sources"]))

    def test_retrieval_snapshots_match_recorded_hashes(self):
        for source in self.manifest["sources"]:
            if "rawPath" in source:
                file = scraper.ROOT / source["rawPath"]
                self.assertEqual(source["sha256"], hashlib.sha256(file.read_bytes()).hexdigest())

    def test_public_call_numbers_do_not_contain_fax_or_email_fields(self):
        for record in self.records:
            for phone in record["phoneNumbers"]:
                self.assertNotIn("@", phone)
                self.assertNotIn("fax", phone.lower())
                self.assertTrue(7 <= len(scraper.normal(phone)) <= 13)

    def test_table_district_header_is_not_a_centre(self):
        html = """<table><tr><td>Sl No</td><td>Name and Address</td><td>Services</td><td>Contact Details</td></tr>
        <tr><td></td><td><strong>KURNOOL</strong></td><td></td><td></td></tr>
        <tr><td>1</td><td><strong>Example Hospital</strong><br>Kurnool</td><td>OP / Home care. Morphine: available</td>
        <td>Phone: 99999 11111<br>Fax: 99999 22222</td></tr></table>"""
        records, rejected = scraper.parse_page(html, "Andhra Pradesh", "https://palliumindia.org/clinics/andhra", "Pallium India", "2026-10-20")
        self.assertEqual([], rejected)
        self.assertEqual(1, len(records))
        self.assertEqual("Kurnool", records[0]["district"])
        self.assertEqual(["99999 11111"], records[0]["phoneNumbers"])
        self.assertEqual("unknown", records[0]["morphine"])

    def test_heading_rows_inside_tables_are_not_centres(self):
        html = """<table><tr><td>Sl No</td><td>Name and Address</td><td>Services</td><td>Contact Details</td></tr>
        <tr><td></td><td>Referral &amp; Research Institutions:</td><td></td><td></td></tr>
        <tr><td></td><td>District | Name | Address</td><td>Services</td><td></td></tr>
        <tr><td>1</td><td><strong>Example Hospital</strong><br>Guwahati</td><td>OP</td><td>Phone: 99999 11111</td></tr></table>"""
        records, rejected = scraper.parse_page(html, "Assam", "https://palliumindia.org/clinics/assam", "Pallium India", "2026-10-20")
        self.assertEqual(["Example Hospital"], [r["name"] for r in records])
        self.assertEqual(["heading-row", "heading-row"], [r["reason"] for r in rejected])
        self.assertFalse(any(r["name"].endswith(":") or "|" in r["name"] for r in self.records))

    def test_vasai_listings_sit_in_palghar_district(self):
        vasai = [r for r in self.records if r["state"] == "Maharashtra" and "vasai" in r["address"].lower() and r["district"]]
        self.assertTrue(vasai)
        self.assertEqual({"Palghar"}, {r["district"] for r in vasai})

    def test_map_viewport_is_not_a_place_coordinate(self):
        html = """<table><tr><th>Name &amp; Address</th><th>Contact Details</th><th>Other Details</th></tr>
        <tr><td>Example Hospital<br>Shillong</td><td>Phone: 99999 11111</td>
        <td><a href='https://www.google.com/maps/@25.123,91.456,12z'>Map</a></td></tr></table>"""
        records, _ = scraper.parse_page(html, "Meghalaya", "https://www.palliativecare.in/palliative-care-centres-meghalaya/", "IAPC", "2026-10-20")
        self.assertEqual(1, len(records))
        self.assertNotIn("latitude", records[0])


if __name__ == "__main__":
    unittest.main(verbosity=2)
