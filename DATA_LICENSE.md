# Bundled data licences

## Approximate centre locations: `android/app/src/main/assets/india-geo.json`

Map positions for palliative centres that the directory lists without coordinates were derived offline by
`scripts/geocode_centres.py` from GeoNames data:

| File | URL | SHA-256 of the copy used (27 Oct 2026) |
|---|---|---|
| Gazetteer, India | https://download.geonames.org/export/dump/IN.zip | c2fec07f9f2bdd86f631ca3be14601170e0883667623760fa568963ab8e2606b |
| Postal codes, India | https://download.geonames.org/export/zip/IN.zip | a0273982f23ade44cd11e70780d502572531fc52fa5a2683bd77d2137d3c77bd |
| State codes | https://download.geonames.org/export/dump/admin1CodesASCII.txt | 2e4867f5397acb056ea26377601b57af37cb4c81399770225018e9e439e91ad3 |

- Licence: Creative Commons Attribution 4.0 (https://creativecommons.org/licenses/by/4.0/), as stated in the
  readme.txt shipped with both downloads. Credit: GeoNames, https://www.geonames.org. GeoNames provides the data
  "as is" without warranty of accuracy.
- Changes: only per-centre results are bundled ([latitude, longitude, precision] for each centre id). The
  GeoNames files themselves are not in the app or the repo. Positions marked `pincode`, `city` or `district` are
  the centre of that area, not the building, and the app labels them "Approximate location".
- GeoNames updates these files often. To rebuild with the same result, use files with the hashes above; with newer
  files, rerun the script and review the coverage line it prints.
- The Government of India "All India Pincode Directory" (data.gov.in, Open Government Data licence) was the
  first choice but could not be downloaded from this environment (data.gov.in reset the connection / returned 503).

Rebuild:

```
python3 -I scripts/geocode_centres.py --gazetteer dump/IN.zip --postal zip/IN.zip --admin1 admin1CodesASCII.txt
python3 -I scripts/geocode_centres.py ... --check   # fails if india-geo.json is out of date
```

The app shows the credit "Approximate locations © GeoNames (CC BY 4.0)" under the Nearby map.
