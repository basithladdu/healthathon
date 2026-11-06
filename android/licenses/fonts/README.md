# Bundled fonts (SIL Open Font License 1.1)

- `app/src/main/res/font/sahara_anek_semibold.ttf`: Anek Latin (Ek Type), static SemiBold (wght 600, wdth 100) instance, subset to Latin-1 and general punctuation. Display face for headings. Licence: OFL-Anek.txt.
- `app/src/main/res/font/sahara_noto_sans.ttf`: Noto Sans (Google), variable weight 400-700, subset to Latin-1 and general punctuation. Body and controls. Licence: OFL-Noto-Sans.txt.

Both were cut with fontTools (`varLib.instancer`, `subset --no-hinting`) from the woff2 files AI-FIR ships. Devanagari falls back to the phone's own Noto Sans Devanagari. Together they add about 69 KB to the app.
