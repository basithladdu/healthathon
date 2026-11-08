import io, shutil
D = 'C:/Users/basit/sahara-build/deck/'
def apply(p, R):
    shutil.copy(D + p, D + p.replace('.py', '_pre_nogemini.py'))
    s = io.open(D + p, encoding='utf-8').read()
    for o, n in R:
        assert s.count(o) == 1, (p, o[:70], s.count(o))
        s = s.replace(o, n)
    io.open(D + p, 'w', encoding='utf-8').write(s)

apply('v3_sections.py', [
 ("the Gemini pipeline that transcribes", "the Sarvam AI pipeline that transcribes"),
 ("'Google’s Gemini AI types out who said what'", "'Sarvam AI types out who said what'"),
 ("(LG / 'googlegemini.png', 'Gemini + Sarvam AI', 'type out the talk, in Indian languages too, and fill the clinical document')",
  "(LG / 'sarvam.png', 'Sarvam AI', 'types out the talk, in Indian languages too, and fills the clinical document')"),
 ("    pic(tech, lg, x + 0.8, 1.55, 0.55, 0.55)",
  "    if lg.name == 'sarvam.png': pic(tech, lg, x + 0.4, 1.68, 1.35, 1.35 * 207 / 1114)\n    else: pic(tech, lg, x + 0.8, 1.55, 0.55, 0.55)"),
 ("('googlegemini', 'Google Gemini', 'AI: speech to text, fills the document'), ", ""),
 ("Gemini models: 3.1 Flash-Lite first, then other Flash models when busy; on-device Whisper; typing.",
  "AI: Sarvam AI speech to text with speaker labels and translation; on-device Whisper when offline; typing."),
 ("[('Gemini AI + backups', TEAL), ('Whisper + Gemini', TEAL)]", "[('AI + backup models', TEAL), ('Offline Whisper', TEAL)]"),
 ("Gemini fallback 5 Nov.", "AI fallback 5 Nov."),
 ("('Google Gemini AI, per 30-minute talk', '–', '≈ ₹2 a talk')", "('Sarvam AI, per 30-minute talk', '–', '≈ ₹23 a talk')"),
 ("('Cost per clinic, if 10 clinics share it', '₹5,000', '≈ ₹1,600')", "('Cost per clinic, if 10 clinics share it', '₹5,000', '≈ ₹3,700')"),
 ("Gemini ≈ $0.30 per million audio units (a 30-minute talk ≈ 58,000); 100 talks per clinic a month.",
  "Sarvam AI ₹45 per audio hour with speaker labels; 100 talks per clinic a month."),
 ("an assumed Gemini audio price; confirm before submission. US$1 = Rs 94 (Dr Sujay, 7 Nov). Servers 45 x 94 = ~4,230. Per clinic monthly = (4,230 + 10,000) / 10 + 100 x 2 = ~1,620.",
  "Sarvam AI price (Rs 45 per hour, speech to text with diarization, docs.sarvam.ai). US$1 = Rs 94 (Dr Sujay, 7 Nov). Servers 45 x 94 = ~4,230. Per clinic monthly = (4,230 + 10,000) / 10 + 100 x 22.5 = ~3,670."),
])

apply('build_brief_v5.py', [
 ("Gemini AI transcribes the audio", "Sarvam AI transcribes the audio"),
 ("documents that Gemini categorizes", "documents that Sarvam AI categorizes"),
 ("S('Google Gemini', b=True), S(' with backup models; '), S('Sarvam AI', b=True), S(': speech-to-text and translation for Indian languages",
  "S('Sarvam AI', b=True), S(': speech-to-text, speaker labels and translation for Indian languages"),
 ("goals-of-care recording with Gemini onto", "goals-of-care recording with Sarvam AI onto"),
 ("Gemini accuracy on Hindi and Telugu", "Sarvam AI accuracy on Hindi and Telugu"),
 ("Gemini is used only with that consent", "Sarvam AI is used only with that consent"),
 ("Gemini about \\u20b92 per 30-minute talk; about \\u20b91,600 per clinic", "Sarvam AI about \\u20b923 per 30-minute talk; about \\u20b93,700 per clinic"),
])
print('ok')
