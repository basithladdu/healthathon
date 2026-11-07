'use client';
// ECTPR PDFs (tech spec §8 "ECTPR PDF + QR wallet card"):
//  - makeEctprPdf: one A4 page for the emergency department (more pages only if the plan is long), V8 footer on
//    every page, V9 always-provided care, the "If in doubt, resuscitate" box, and a QR to the live card.
//  - makeWalletCardPdf: Appendix E wallet card (front + back, cut and fold) on an A4 sheet, with both QR codes.
// Both return the SHA-256 of the finished file so the caller can show it and write it to the audit log.
// pdf-lib, fontkit, qrcode and the fonts load only when a PDF is made, so the ED view itself stays light.
import type { PDFDocument, PDFFont, PDFPage, PDFName, RGB } from 'pdf-lib';
import { careText, type CareLang } from './care-i18n';
import { AMD_ED_CAVEAT, AMD_ED_CAVEAT_HI } from './amd-rules';
import type { EctprRecord } from './ectpr-model';
import { edInterpretation, pageFooter } from './ectpr-rules';
import { capacityGuide, homeCareLines, withdrawalLines,
  alwaysProvidedCare, amdCaveat, ambulanceLabel, edAmdLines, authorityLabel, ceilingDetails, ceilingLabel, ceilingShort, cprDecidedBy, cprLabel,
  cprShort, deviceLabel, dialysisLabel, dyingPhasePrompt, edBanner, edFlag, formatDay, goalLabel, hydrationLabel, icuLabel, ifInDoubtAfter,
  ifInDoubtConditions, ifInDoubtTitle, ngLabel, notRecorded, pegLabel, textFor, transferLabel, transferShort,
  treatingConsultant, vasopressorLabel, walletCardLine,
} from './ectpr-labels.ts';
import { sha256Hex } from './ed-card-snapshot';

export type PdfResult = { bytes: Uint8Array; sha256: string; fileName: string; pages: number };

type FontPair = { devanagari: Uint8Array; telugu: Uint8Array | null };
const fontCache = new Map<string, Promise<Uint8Array>>();
function loadFont(path: string): Promise<Uint8Array> {
  let pending = fontCache.get(path);
  if (!pending) {
    pending = fetch(path).then(async (r) => {
      if (!r.ok) throw new Error('The PDF font could not load. Check the connection and try again.');
      return new Uint8Array(await r.arrayBuffer());
    }).catch((e) => { fontCache.delete(path); throw e; });
    fontCache.set(path, pending);
  }
  return pending;
}
const TELUGU = /[ఀ-౿]/u;
async function loadFonts(texts: string[]): Promise<FontPair> {
  const needsTelugu = texts.some((t) => TELUGU.test(t));
  const [devanagari, telugu] = await Promise.all([loadFont('/fonts/NotoSansDevanagari.ttf'), needsTelugu ? loadFont('/fonts/NotoSansTelugu.ttf') : Promise.resolve(null)]);
  return { devanagari, telugu };
}

// Characters the Noto fonts lack, replaced with plain equivalents.
const FALLBACK: Record<string, string> = { '₂': '2', '≤': '<=', '≥': '>=', '☐': '[ ]', '✓': 'v', '→': '->' };
const clean = (text: string) => text.replace(/[₂≤≥☐✓→]/gu, (c) => FALLBACK[c] ?? '?');

type Glyph = { id: number };
type Pos = { xAdvance: number; yAdvance: number; xOffset: number; yOffset: number };
type ShapingFont = { unitsPerEm: number; layout(text: string): { glyphs: Glyph[]; positions: Pos[] }; hasGlyphForCodePoint(cp: number): boolean };

/** A tiny text engine over pdf-lib + fontkit: shaped Devanagari/Telugu, wrapping, per-page font dictionaries. */
class Writer {
  readonly fonts: { embedded: PDFFont; shaping: ShapingFont; telugu: boolean }[];
  private pageFonts = new Map<PDFPage, Map<number, PDFName>>();
  private segmenter = new Intl.Segmenter('hi', { granularity: 'grapheme' });
  constructor(private pdf: typeof import('pdf-lib'), private doc: PDFDocument, fonts: { embedded: PDFFont; shaping: ShapingFont; telugu: boolean }[]) {
    this.fonts = fonts;
  }
  private fontIndexFor(run: string): number {
    if (TELUGU.test(run)) { const i = this.fonts.findIndex((f) => f.telugu); if (i >= 0) return i; }
    return 0;
  }
  private runs(text: string): { font: number; text: string }[] {
    const parts = clean(text).replace(/\t/g, '    ').match(/[ఀ-౿‌‍]+|[^ఀ-౿‌‍]+/gu) ?? [];
    return parts.map((p) => ({ font: this.fontIndexFor(p), text: p }));
  }
  measure(text: string, size: number): number {
    return this.runs(text).reduce((sum, run) => {
      const f = this.fonts[run.font].shaping;
      return sum + f.layout(run.text).positions.reduce((a, p) => a + p.xAdvance, 0) * size / f.unitsPerEm;
    }, 0);
  }
  wrap(text: string, size: number, width: number): string[] {
    const out: string[] = [];
    for (const paragraph of clean(text).replace(/\r\n?/g, '\n').split('\n')) {
      if (!paragraph) { out.push(''); continue; }
      let line = '';
      for (const token of paragraph.match(/\S+\s*|\s+/gu) ?? []) {
        if (this.measure(line + token, size) <= width) { line += token; continue; }
        if (line) { out.push(line.trimEnd()); line = ''; }
        if (this.measure(token, size) <= width) { line = token; continue; }
        for (const { segment } of this.segmenter.segment(token)) {
          if (line && this.measure(line + segment, size) > width) { out.push(line); line = ''; }
          line += segment;
        }
      }
      if (line) out.push(line.trimEnd());
    }
    return out;
  }
  draw(page: PDFPage, text: string, x: number, baseline: number, size: number, color: RGB): void {
    if (!text) return;
    const pdf = this.pdf;
    let dict = this.pageFonts.get(page);
    if (!dict) { dict = new Map(); this.pageFonts.set(page, dict); }
    let penX = x;
    page.pushOperators(
      pdf.pushGraphicsState(), pdf.setFillingRgbColor(color.red, color.green, color.blue),
      pdf.PDFOperator.of(pdf.PDFOperatorNames.BeginMarkedContentSequence, [pdf.PDFName.of('Span'), this.doc.context.obj({ ActualText: pdf.PDFHexString.fromText(text) }).toString()]),
      pdf.beginText(),
    );
    for (const run of this.runs(text)) {
      const font = this.fonts[run.font];
      let key = dict.get(run.font);
      if (!key) { key = page.node.newFontDictionary(font.embedded.name, font.embedded.ref); dict.set(run.font, key); }
      page.pushOperators(pdf.setFontAndSize(key, size));
      const scale = size / font.shaping.unitsPerEm;
      const shaped = font.shaping.layout(run.text);
      shaped.glyphs.forEach((glyph, i) => {
        const p = shaped.positions[i];
        page.pushOperators(pdf.setTextMatrix(1, 0, 0, 1, penX + p.xOffset * scale, baseline + p.yOffset * scale), pdf.showText(pdf.PDFHexString.of(glyph.id.toString(16).padStart(4, '0'))));
        penX += p.xAdvance * scale;
      });
    }
    page.pushOperators(pdf.endText(), pdf.endMarkedContent(), pdf.popGraphicsState());
  }
}

async function setup(title: string, texts: string[]) {
  const pdf = await import('pdf-lib');
  const { default: fontkit } = await import('@pdf-lib/fontkit');
  // fontkit's Indic shaper (Devanagari, Telugu) calls a global `regeneratorRuntime`; the package sets it on load.
  // @ts-expect-error regenerator-runtime ships no type declarations.
  if (!('regeneratorRuntime' in globalThis)) await import('regenerator-runtime');
  const bytes = await loadFonts(texts);
  const doc = await pdf.PDFDocument.create();
  doc.registerFontkit(fontkit);
  const fonts = [{ embedded: await doc.embedFont(bytes.devanagari, { subset: false }), shaping: fontkit.create(bytes.devanagari) as unknown as ShapingFont, telugu: false }];
  if (bytes.telugu) fonts.push({ embedded: await doc.embedFont(bytes.telugu, { subset: false }), shaping: fontkit.create(bytes.telugu) as unknown as ShapingFont, telugu: true });
  doc.setTitle(title);
  doc.setAuthor('Sahara - A Palliative Care Companion');
  doc.setCreator('Sahara - A Palliative Care Companion');
  doc.setProducer('Sahara');
  // Fixed dates keep the file reproducible for the same record and print day.
  return { pdf, doc, writer: new Writer(pdf, doc, fonts) };
}

async function qrPng(doc: PDFDocument, text: string) {
  const QRCode = (await import('qrcode')).default;
  const dataUrl = await QRCode.toDataURL(text, { errorCorrectionLevel: 'M', margin: 1, width: 360 });
  const bytes = Uint8Array.from(atob(dataUrl.split(',')[1]), (c) => c.charCodeAt(0));
  return doc.embedPng(bytes);
}

function localFooter(record: EctprRecord, page: number, pages: number, printDate: string, lang: CareLang): string {
  if (lang === 'en') return pageFooter(record, page, pages, printDate);
  const t = textFor(lang);
  return `${record.patient.name || notRecorded(lang)} · UHID ${record.patient.uhid || notRecorded(lang)} · ${t('Version', 'संस्करण')} ${record.version} · ${record.formVersion} · ${t('Printed', 'छपा')} ${printDate} · ${t('Page', 'पृष्ठ')} ${page} / ${pages}`;
}

function todayIso(date = new Date()): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}`;
}

function safeName(text: string): string {
  return text.replace(/[^A-Za-z0-9_-]+/g, '-').replace(/^-+|-+$/g, '') || 'patient';
}

/** Plain-text emergency summary used for the offline QR code (readable by any phone camera, no internet). */
export function offlineCardText(record: EctprRecord, lang: CareLang = 'en', now: Date = new Date()): string {
  const t = textFor(lang);
  const interp = edInterpretation(record, now);
  const consultant = treatingConsultant(record);
  const dm = record.decisionMakers.primary;
  const lines = [
    `SAHARA ECTPR v${record.version} · ${record.formVersion}`,
    interp.state === 'ACTIVE' ? t('Signed emergency care plan', 'हस्ताक्षरित आपातकालीन योजना') : edBanner(interp, record, lang),
    `${record.patient.name} · UHID ${record.patient.uhid}`,
    `${t('Diagnosis', 'निदान')}: ${record.clinical.diagnosis || notRecorded(lang)}`,
    `${t('Transfer', 'अस्पताल ले जाना')}: ${transferShort(record.choices.hospitalTransfer, lang)}`,
    `CPR: ${cprShort(record, lang)}`,
    `${t('Breathing ceiling', 'साँस सहायता सीमा')}: ${ceilingShort(record.choices.respCeiling, lang)}`,
    `ICU: ${icuLabel(record.choices.icu, lang)}`,
    `${t('Call first', 'पहले फ़ोन')}: ${record.careTeam.palliativePhysician || notRecorded(lang)} ${record.careTeam.palliativePhone24h}`.trim(),
    `${t('Decision-maker', 'निर्णयकर्ता')}: ${dm ? `${dm.name} (${dm.relation}) ${dm.phone24h}`.trim() : notRecorded(lang)}`,
    `${t('Signed', 'हस्ताक्षर')}: ${consultant ? `${consultant.name} ${formatDay(consultant.signedAt, lang)}` : t('NOT SIGNED', 'हस्ताक्षर नहीं')} · ${t('Review due', 'समीक्षा')} ${formatDay(record.reviewDue, lang)}`,
    ...(record.amdAtIssue ? [edAmdLines(record.existingDirective, record.amdAtIssue, lang).lines.join(' '), careText(lang, AMD_ED_CAVEAT, AMD_ED_CAVEAT_HI)] : []),
    walletCardLine(lang),
  ];
  return lines.join('\n');
}

/**
 * One-page A4 ECTPR for the emergency department. `liveUrl` (optional) is the /ed/<token> link for the QR code.
 */
export async function makeEctprPdf(record: EctprRecord, opts: { lang?: CareLang; liveUrl?: string | null; now?: Date } = {}): Promise<PdfResult> {
  const lang = opts.lang ?? 'en';
  const now = opts.now ?? new Date();
  const t = textFor(lang);
  const interp = edInterpretation(record, now);
  const instruction = interp.state === 'ACTIVE';
  const c = record.choices;
  const consultant = treatingConsultant(record);
  const dm = record.decisionMakers.primary;
  const alt = record.decisionMakers.alternate;
  const pending = instruction ? '' : `${t('[Sign-off pending]', '[स्वीकृति बाकी]')} `;

  const rows: [string, string[]][] = [
    [t('Hospital transfer', 'अस्पताल ले जाना'), [
      transferLabel(c.hospitalTransfer, lang),
      ...(c.callBeforeAmbulance.filter(Boolean).length ? [`${t('Call before an ambulance', 'एम्बुलेंस से पहले फ़ोन करें')}: ${c.callBeforeAmbulance.filter(Boolean).join('; ')}`] : []),
      `${t('Ambulance crew', 'एम्बुलेंस टीम')}: ${ambulanceLabel(c.ambulance, lang)}`,
    ]],
    ['CPR', [cprLabel(record, lang), ...(cprDecidedBy(record, lang) ? [cprDecidedBy(record, lang)!] : [])]],
    [t('Breathing support ceiling', 'साँस सहायता की सीमा'), [ceilingLabel(c.respCeiling, lang), ...ceilingDetails(record, lang)]],
    ['ICU', [icuLabel(c.icu, lang)]],
    [t('Goal of care', 'देखभाल का लक्ष्य'), [goalLabel(c.goalOfCare, lang)]],
    [t('Vasopressors / dialysis', 'वैसोप्रेसर / डायलिसिस'), [`${t('Vasopressors', 'वैसोप्रेसर')}: ${vasopressorLabel(c.vasopressors, lang)}`, `${t('Dialysis', 'डायलिसिस')}: ${dialysisLabel(c.dialysis, lang)}`]],
    [t('Feeding and fluids', 'खाना और तरल'), [
      `${t('Comfort feeding by mouth always continues.', 'मुँह से आराम के लिए खिलाना हमेशा जारी रहेगा।')} NG: ${ngLabel(c.feeding.ng, lang)} · PEG: ${pegLabel(c.feeding.peg, lang)}`,
      `${t('Hydration', 'तरल')}: ${hydrationLabel(c.hydration, lang)}`,
      ...(c.feeding.removeExistingTube ? [t('Wish to remove an existing tube is recorded: this is withdrawal and needs the medical board pathway — not actionable from this plan.', 'लगी ट्यूब हटाने की इच्छा दर्ज है: यह इलाज रोकना है और मेडिकल बोर्ड प्रक्रिया चाहिए — इस योजना से अमल नहीं।')] : []),
    ]],
    [t('Allergies', 'एलर्जी'), [record.clinical.allergies || notRecorded(lang)]],
    [t('Implanted device', 'लगा हुआ उपकरण'), [deviceLabel(record, lang)]],
    [t('Key medicines', 'मुख्य दवाएँ'), [record.clinical.keyMedicines || notRecorded(lang)]],
    [t('Decision-maker (24 h)', 'निर्णयकर्ता (24 घंटे)'), [
      dm ? `${dm.name} · ${dm.relation} · ${dm.phone24h || notRecorded(lang)}${authorityLabel(dm.authorityBasis, lang) ? ` · ${authorityLabel(dm.authorityBasis, lang)}` : ''}` : notRecorded(lang),
      ...(alt ? [`${t('Secondary', 'द्वितीय')}: ${alt.name} · ${alt.relation} · ${alt.phone24h || notRecorded(lang)}`] : []),
    ]],
    [t('Treating team', 'इलाज करने वाली टीम'), [
      `${t('Treating consultant', 'मुख्य डॉक्टर')}: ${consultant ? `${consultant.name} (${t('Reg.', 'पंजी.')} ${consultant.registrationNo})` : record.careTeam.oncologist || notRecorded(lang)}`,
      `${t('Palliative care', 'पैलिएटिव केयर')}: ${record.careTeam.palliativePhysician || notRecorded(lang)} · ${record.careTeam.palliativePhone24h || notRecorded(lang)}`,
    ]],
    ...(homeCareLines(record, lang).length ? [[t('Home care', 'घर पर देखभाल'), homeCareLines(record, lang)] as [string, string[]]] : []),
    ...(withdrawalLines(record, lang).length ? [[t('Withdrawal or withholding (medical boards)', 'इलाज रोकना या शुरू न करना (मेडिकल बोर्ड)'), withdrawalLines(record, lang)] as [string, string[]]] : []),
    ...((record.decisionMakers.presentAtDiscussion ?? []).length ? [[t('Present for the discussion', 'बातचीत में मौजूद'), record.decisionMakers.presentAtDiscussion!] as [string, string[]]] : []),
    ...((record.decisionMakers.attemptedContact ?? []).length ? [[t('Could not be reached', 'संपर्क नहीं हो सका'), record.decisionMakers.attemptedContact!] as [string, string[]]] : []),
    [t('Capacity', 'निर्णय क्षमता'), [
      record.capacity.status === 'FLUCTUATES' ? t('Capacity fluctuates — reassess', 'निर्णय क्षमता बदलती है — दोबारा जाँचें')
        : record.capacity.status === 'HAS' ? t('Has capacity', 'निर्णय क्षमता है') : record.capacity.status === 'HAS_DELEGATED' ? t('Has capacity; has delegated decisions', 'क्षमता है; निर्णय सौंपे हैं')
        : record.capacity.status === 'LACKS' ? t('Lacks capacity for these decisions', 'इन निर्णयों की क्षमता नहीं') : notRecorded(lang),
      t('A patient with capacity can change any part of this plan at any time; what they say now overrides it.', 'क्षमता वाला मरीज़ कभी भी योजना बदल सकता है; उसकी अभी की बात ही मान्य है।'),
    ]],
    [t('Advance care planning (AMD)', 'अग्रिम देखभाल योजना (AMD)'), [...edAmdLines(record.existingDirective, record.amdAtIssue, lang).lines, amdCaveat(lang)]],
  ];

  const guide = capacityGuide(lang);
  const allTexts = [guide.title, guide.note, ...guide.points, record.patient.gender ?? '', record.patient.name, record.clinical.diagnosis, ...rows.flatMap(([a, b]) => [a, ...b]), ...alwaysProvidedCare(lang), ...ifInDoubtConditions(lang), edBanner(interp, record, lang)];
  const { pdf, doc, writer } = await setup(`ECTPR · ${record.patient.name} · v${record.version}`, allTexts);
  const fixed = new Date(`${todayIso(now)}T00:00:00Z`);
  doc.setCreationDate(fixed); doc.setModificationDate(fixed);

  const [W, H] = pdf.PageSizes.A4;
  const M = 30;
  const ink = pdf.rgb(0.07, 0.13, 0.11);
  const muted = pdf.rgb(0.36, 0.40, 0.38);
  const grey = pdf.rgb(0.55, 0.57, 0.56);
  const accent = pdf.rgb(0.64, 0.30, 0.21);
  const red = pdf.rgb(0.66, 0.10, 0.10);
  const line = pdf.rgb(0.82, 0.84, 0.80);
  const printDate = todayIso(now);
  let page = doc.addPage([W, H]);
  let y = H - M;
  const pages: PDFPage[] = [page];
  const bottom = 40;

  function newPage() {
    page = doc.addPage([W, H]);
    pages.push(page);
    y = H - M;
    writer.draw(page, `SAHARA · ECTPR · ${record.patient.name} · UHID ${record.patient.uhid} · ${t('continued', 'जारी')}`, M, y - 8, 8, accent);
    y -= 22;
  }
  function room(h: number) { if (y - h < bottom) newPage(); }
  function text(str: string, size: number, color: RGB, x = M, width = W - 2 * M, leading = size * 1.32) {
    for (const l of writer.wrap(str, size, width)) { room(leading); writer.draw(page, l, x, y - size, size, color); y -= leading; }
  }

  // Header + QR
  const qrSize = 74;
  const qrText = opts.liveUrl ?? offlineCardText(record, lang, now);
  const qr = await qrPng(doc, qrText);
  page.drawImage(qr, { x: W - M - qrSize, y: H - M - qrSize, width: qrSize, height: qrSize });
  const headerWidth = W - 2 * M - qrSize - 10;
  writer.draw(page, 'SAHARA', M, y - 9, 9, accent);
  y -= 14;
  text(t('Emergency Care and Treatment Plan (ECTPR)', 'आपातकालीन देखभाल और इलाज योजना (ECTPR)'), 15, ink, M, headerWidth, 19);
  text(`${record.patient.name || notRecorded(lang)} · UHID ${record.patient.uhid || notRecorded(lang)}${record.patient.dob ? ` · ${t('DOB', 'जन्म')} ${formatDay(record.patient.dob, lang)}` : ''}${record.patient.sex ? ` · ${record.patient.sex}` : ''}${record.patient.gender ? ` · ${record.patient.gender}` : ''}`, 10, ink, M, headerWidth, 13);
  text(`${t('Diagnosis', 'निदान')}: ${record.clinical.diagnosis || notRecorded(lang)}${record.clinical.stage ? ` · ${record.clinical.stage}` : ''}${record.clinical.ecog !== null ? ` · ECOG ${record.clinical.ecog}` : ''}`, 9, ink, M, headerWidth, 12);
  text(`${t('Version', 'संस्करण')} ${record.version} · ${record.formVersion} · ${t('Signed', 'हस्ताक्षर')} ${consultant ? `${formatDay(consultant.signedAt, lang)} ${t('by', 'द्वारा')} ${consultant.name}` : t('NOT SIGNED', 'हस्ताक्षर नहीं')} · ${t('Review due', 'समीक्षा')} ${formatDay(record.reviewDue, lang)}`, 8.5, muted, M, headerWidth, 11);
  writer.draw(page, opts.liveUrl ? t('Scan: live card (internet)', 'स्कैन: लाइव कार्ड (इंटरनेट)') : t('Scan: offline summary', 'स्कैन: ऑफ़लाइन सारांश'), W - M - qrSize, H - M - qrSize - 9, 6.5, muted);
  y = Math.min(y, H - M - qrSize - 14);

  // Status banner
  const banner = edBanner(interp, record, lang);
  const bannerLines = writer.wrap(banner, 9.5, W - 2 * M - 16);
  const bannerH = bannerLines.length * 12.5 + 10;
  page.drawRectangle({ x: M, y: y - bannerH, width: W - 2 * M, height: bannerH, color: instruction ? pdf.rgb(0.91, 0.96, 0.93) : pdf.rgb(1, 0.93, 0.90), borderColor: instruction ? pdf.rgb(0.55, 0.75, 0.62) : red, borderWidth: 1 });
  bannerLines.forEach((l, i) => writer.draw(page, l, M + 8, y - 15 - i * 12.5, 9.5, instruction ? ink : red));
  y -= bannerH + 6;
  for (const flag of interp.flags) text(`! ${edFlag(flag, lang)}`, 9, red);

  // If in doubt box (always)
  const doubt = [...ifInDoubtConditions(lang).map((s) => `• ${s}`), ifInDoubtAfter(lang)];
  const doubtLines = doubt.flatMap((s) => writer.wrap(s, 8.5, W - 2 * M - 16));
  const doubtH = 16 + doubtLines.length * 11 + 6;
  page.drawRectangle({ x: M, y: y - doubtH, width: W - 2 * M, height: doubtH, borderColor: red, borderWidth: 1.6 });
  writer.draw(page, ifInDoubtTitle(lang), M + 8, y - 13, 10.5, red);
  doubtLines.forEach((l, i) => writer.draw(page, l, M + 8, y - 26 - i * 11, 8.5, ink));
  y -= doubtH + 8;

  // Decision rows, in ED Quick View order
  const labelW = 120;
  const valueX = M + labelW + 6;
  const valueW = W - M - valueX;
  for (const [label, values] of rows) {
    const valueLines = values.flatMap((v, i) => writer.wrap(`${i === 0 ? pending : ''}${v}`, 8.5, valueW));
    const labelLines = writer.wrap(label, 8.5, labelW);
    const h = Math.max(valueLines.length, labelLines.length) * 10.8 + 4;
    room(h);
    labelLines.forEach((l, i) => writer.draw(page, l, M, y - 9 - i * 10.8, 8.5, accent));
    valueLines.forEach((l, i) => writer.draw(page, l, valueX, y - 9 - i * 10.8, 8.5, instruction ? ink : grey));
    y -= h;
    page.drawLine({ start: { x: M, y: y + 1 }, end: { x: W - M, y: y + 1 }, thickness: 0.4, color: line });
  }
  y -= 4;

  // V9 always-provided care
  const care = alwaysProvidedCare(lang);
  const careLines = care.flatMap((s) => writer.wrap(`• ${s}`, 8, (W - 2 * M - 16) / 2 - 6));
  const half = Math.ceil(careLines.length / 2);
  const careH = 16 + half * 10 + 6;
  room(careH);
  page.drawRectangle({ x: M, y: y - careH, width: W - 2 * M, height: careH, color: pdf.rgb(0.96, 0.97, 0.93), borderColor: pdf.rgb(0.70, 0.78, 0.62), borderWidth: 0.8 });
  writer.draw(page, t('Care that always continues, whatever else is recorded', 'जो देखभाल हमेशा जारी रहती है, चाहे कुछ भी दर्ज हो'), M + 8, y - 12, 9.5, pdf.rgb(0.27, 0.37, 0.27));
  careLines.forEach((l, i) => writer.draw(page, l, i < half ? M + 8 : M + 8 + (W - 2 * M) / 2, y - 24 - (i % half) * 10, 8, ink));
  y -= careH + 6;

  text(dyingPhasePrompt(lang), 7.8, muted);
  y -= 2;
  // Signatures
  const sigs = record.signatures.map((s) => `${s.role.replace(/_/g, ' ').toLowerCase()}${s.surrogateRank ? ` (${s.surrogateRank.toLowerCase()})` : ''}: ${s.name}${s.registrationNo ? ` (${t('Reg.', 'पंजी.')} ${s.registrationNo})` : ''} · ${formatDay(s.signedAt, lang)}${s.thumbImpression ? ` · ${t('thumb impression', 'अंगूठे का निशान')}` : ''}`);
  text(`${t('Signatures', 'हस्ताक्षर')}: ${sigs.length ? sigs.join(' | ') : t('none', 'कोई नहीं')}`, 7.8, muted);
  y -= 4;
  text(guide.title, 8.5, accent);
  text(guide.note, 7.5, muted);
  guide.points.forEach((point, index) => text(`${index + 1}. ${point}`, 7.5, muted));
  text(t('Typed signatures recorded in Sahara. Synthetic patient data for the healthathon. The SHA-256 of this file is shown in the app and kept in the audit log.', 'हस्ताक्षर Sahara में टाइप किए गए। हैकाथॉन के लिए काल्पनिक मरीज़। इस फ़ाइल का SHA-256 ऐप और ऑडिट लॉग में है।'), 7, grey);

  // V8 footer on every page
  pages.forEach((p, i) => {
    p.drawLine({ start: { x: M, y: 30 }, end: { x: W - M, y: 30 }, thickness: 0.5, color: line });
    writer.draw(p, localFooter(record, i + 1, pages.length, printDate, lang), M, 19, 7.5, muted);
  });

  const bytes = await doc.save();
  return { bytes, sha256: await sha256Hex(bytes), fileName: `ECTPR-${safeName(record.patient.uhid)}-v${record.version}-${printDate}.pdf`, pages: pages.length };
}

/** Appendix E wallet card: front (fields) and back (QR codes), credit-card size, printed on A4 to cut and fold. */
export async function makeWalletCardPdf(record: EctprRecord, opts: { lang?: CareLang; liveUrl?: string | null; now?: Date } = {}): Promise<PdfResult> {
  const lang = opts.lang ?? 'en';
  const now = opts.now ?? new Date();
  const t = textFor(lang);
  const interp = edInterpretation(record, now);
  const consultant = treatingConsultant(record);
  const dm = record.decisionMakers.primary;
  const fields: [string, string][] = [
    [t('Patient', 'मरीज़'), `${record.patient.name} · UHID ${record.patient.uhid}`],
    [t('Diagnosis', 'निदान'), record.clinical.diagnosis || notRecorded(lang)],
    ['CPR', cprShort(record, lang)],
    [t('Breathing ceiling', 'साँस सीमा'), ceilingShort(record.choices.respCeiling, lang)],
    [t('Transfer', 'अस्पताल'), transferShort(record.choices.hospitalTransfer, lang)],
    [t('Call first', 'पहले फ़ोन'), `${record.careTeam.palliativePhysician || notRecorded(lang)} ${record.careTeam.palliativePhone24h}`.trim()],
    [t('Decision-maker', 'निर्णयकर्ता'), dm ? `${dm.name} (${dm.relation}) ${dm.phone24h}`.trim() : notRecorded(lang)],
    [t('Full record', 'पूरा रिकॉर्ड'), `${record.careTeam.palliativePhysician ? `${t('Palliative team', 'पैलिएटिव टीम')} · ` : ''}${t('scan the QR (break-glass)', 'QR स्कैन करें (ब्रेक-ग्लास)')}`],
    [t('Signed', 'हस्ताक्षर'), consultant ? `${consultant.name} · ${formatDay(consultant.signedAt, lang)}` : t('NOT SIGNED — not a plan', 'हस्ताक्षर नहीं — योजना नहीं')],
  ];
  const line = walletCardLine(lang);
  const { pdf, doc, writer } = await setup(`Emergency card · ${record.patient.name}`, [...fields.flat(), line, edBanner(interp, record, lang)]);
  const fixed = new Date(`${todayIso(now)}T00:00:00Z`);
  doc.setCreationDate(fixed); doc.setModificationDate(fixed);
  const [W, H] = pdf.PageSizes.A4;
  const page = doc.addPage([W, H]);
  const mm = 72 / 25.4;
  const cw = 85.6 * mm, ch = 54 * mm;
  const x0 = (W - cw * 2) / 2, y0 = H - 60 - ch;
  const ink = pdf.rgb(0.07, 0.13, 0.11), muted = pdf.rgb(0.36, 0.40, 0.38), red = pdf.rgb(0.66, 0.10, 0.10), accent = pdf.rgb(0.64, 0.30, 0.21);
  const active = interp.state === 'ACTIVE';

  writer.draw(page, t('Sahara emergency card — cut along the outline, fold down the middle, keep it with the patient.', 'Sahara आपातकालीन कार्ड — किनारे से काटें, बीच से मोड़ें, मरीज़ के पास रखें।'), x0, H - 40, 9, muted);
  for (const x of [x0, x0 + cw]) page.drawRectangle({ x, y: y0, width: cw, height: ch, borderColor: pdf.rgb(0.5, 0.5, 0.5), borderWidth: 0.6, borderDashArray: [3, 2] });

  // Front
  page.drawRectangle({ x: x0, y: y0 + ch - 13, width: cw, height: 13, color: active ? pdf.rgb(0.73, 0.28, 0.17) : red });
  writer.draw(page, active ? t('EMERGENCY CARE PLAN · SAHARA', 'आपातकालीन देखभाल योजना · SAHARA') : t('NOT A VALID PLAN — GIVE STANDARD CARE', 'मान्य योजना नहीं — सामान्य इलाज दें'), x0 + 5, y0 + ch - 9.5, 6.8, pdf.rgb(1, 1, 1));
  const ruleLines = writer.wrap(line, 5.8, cw - 10);
  const floor = y0 + 4 + ruleLines.length * 6.6 + 2;
  let y = y0 + ch - 20;
  for (const [label, value] of fields) {
    const lines = writer.wrap(`${label}: ${value}`, 5.6, cw - 10).slice(0, 2);
    for (const l of lines) { if (y < floor) break; writer.draw(page, l, x0 + 5, y, 5.6, ink); y -= 6.6; }
  }
  ruleLines.forEach((l, i) => writer.draw(page, l, x0 + 5, y0 + 4 + (ruleLines.length - 1 - i) * 6.6, 5.8, red));

  // Back: QR codes
  const bx = x0 + cw;
  const q = 30 * mm;
  const live = opts.liveUrl ? await qrPng(doc, opts.liveUrl) : null;
  const offline = await qrPng(doc, offlineCardText(record, lang, now));
  if (live) {
    page.drawImage(live, { x: bx + 6, y: y0 + ch - 8 - q, width: q, height: q });
    writer.draw(page, t('Live card (internet)', 'लाइव कार्ड (इंटरनेट)'), bx + 6, y0 + ch - 14 - q, 5.6, muted);
  }
  page.drawImage(offline, { x: bx + cw - 6 - q, y: y0 + ch - 8 - q, width: q, height: q });
  writer.draw(page, t('Offline summary (any camera)', 'ऑफ़लाइन सारांश (कोई भी कैमरा)'), bx + cw - 6 - q, y0 + ch - 14 - q, 5.6, muted);
  const backText = [
    `${t('Version', 'संस्करण')} ${record.version} · ${record.formVersion} · ${t('review due', 'समीक्षा')} ${formatDay(record.reviewDue, lang)}`,
    t('IF IN DOUBT, RESUSCITATE. What the patient says now overrides this card.', 'संदेह हो तो CPR दें। मरीज़ की अभी की बात इस कार्ड से ऊपर है।'),
  ];
  let by = y0 + 16;
  backText.forEach((s, index) => { for (const l of writer.wrap(s, 5.6, cw - 12)) { writer.draw(page, l, bx + 6, by, 5.6, index === 1 ? red : ink); by -= 6.6; } });
  writer.draw(page, `SAHARA`, bx + 6, y0 + ch - 6, 5.6, accent);

  // V8 footer
  page.drawLine({ start: { x: 30, y: 30 }, end: { x: W - 30, y: 30 }, thickness: 0.5, color: pdf.rgb(0.82, 0.84, 0.80) });
  writer.draw(page, localFooter(record, 1, 1, todayIso(now), lang), 30, 19, 7.5, muted);

  const bytes = await doc.save();
  return { bytes, sha256: await sha256Hex(bytes), fileName: `Emergency-card-${safeName(record.patient.uhid)}-v${record.version}.pdf`, pages: 1 };
}

export function downloadPdf(result: PdfResult): void {
  const url = URL.createObjectURL(new Blob([new Uint8Array(result.bytes).buffer], { type: 'application/pdf' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = result.fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
