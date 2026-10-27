# Data licence: offline medicine names

`cdci-names.tsv` in this folder is a derivative of the **Common Drug Codes for India (CDCI)
Flat File Package** by NRCeS / C-DAC, Pune, licensed **CC BY 4.0**, taken from OHC Network's
public repository [ohcnetwork/cdci](https://github.com/ohcnetwork/cdci) at commit
`c6c800b9f00e3371a1fcac2f918551c7a8a7f383`. The original `License.txt` is kept here as
`CDCI-License.txt`; it governs. Attribution text: [`NOTICE.txt`](NOTICE.txt), also shown in
the app under More → About.

Key terms (paraphrased):
- **CC BY 4.0**: share and adapt with attribution to C-DAC / NRCeS; say what was changed
  (only names and SNOMED CT substance codes were kept).
- **SNOMED CT content** is governed by the SNOMED CT Affiliate License. SNOMED CT® is a
  trademark of SNOMED International. India is a SNOMED International member territory.
- The package may be **redistributed, and derivatives made, within India**. Sahara is
  built for Indian palliative care; distributing the app outside India needs a check of
  the CDCI licence first.
- No brand or trade names are included.

Rebuild: `python3 -I android/scripts/build-cdci-medicine-names.py <cdci checkout> android/app/src/main/assets/medicine-names/cdci-names.tsv`.
