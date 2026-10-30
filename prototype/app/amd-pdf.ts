// Vidhi-format living will (AMD) PDF. Same sections, order and wording style as the Vidhi Centre for Legal Policy /
// ACP India model AMD, filled with the person's answers, with BLANK executor signature, two witness blocks and a
// notary / gazetted-officer attestation block. Every page carries the version, "page x of y" and the content SHA-256.
// Pure builder (runs in the browser and in node); downloadAmdPdf() is the browser wrapper.
// Latin text uses Times (as the Vidhi sample); Hindi and Telugu are shaped with fontkit and Noto Sans fonts.

import type { PDFDocument as PdfDocument, PDFFont, PDFName, PDFPage, RGB } from 'pdf-lib';
import type { AmdDhr, AmdVersion, IdType } from './ectpr-model.ts';
import { AMD_TREATMENTS, AMD_TREATMENT_PDF_TEXT, ID_TYPE_LABELS, numberInWords, sha256Hex } from './amd-rules.ts';

export type AmdPdfLang = 'en' | 'hi' | 'te';
export type AmdPdfFonts = { devanagari: Uint8Array; telugu?: Uint8Array };
export type AmdPdfInput = { version: AmdVersion; lang: AmdPdfLang; contentSha256: string; printedAt: Date };

/** Section headings in Hindi and Telugu, printed under the English heading. The English text is the operative text. */
const HEADINGS: Record<string, { hi: string; te: string }> = {
  'ADVANCE MEDICAL DIRECTIVE': { hi: 'अग्रिम चिकित्सा निर्देश (लिविंग विल)', te: 'ముందస్తు వైద్య నిర్దేశం (లివింగ్ విల్)' },
  'Section 1: Personal details': { hi: 'खंड 1: व्यक्तिगत विवरण', te: 'విభాగం 1: వ్యక్తిగత వివరాలు' },
  'Section 2: Directions relating to life-sustaining treatment': { hi: 'खंड 2: जीवन-रक्षक उपचार से जुड़े निर्देश', te: 'విభాగం 2: ప్రాణాధార చికిత్సకు సంబంధించిన ఆదేశాలు' },
  'Section 3 (Optional): Wishes or desires during end-of-life care': { hi: 'खंड 3 (वैकल्पिक): जीवन के अंतिम समय की देखभाल में इच्छाएँ', te: 'విభాగం 3 (ఐచ్ఛికం): జీవిత చివరి దశ సంరక్షణలో కోరికలు' },
  'Section 4: Authorisation of the Designated Healthcare Representative (DHR)': { hi: 'खंड 4: नामित स्वास्थ्य देखभाल प्रतिनिधि (DHR) का प्राधिकार', te: 'విభాగం 4: నియమిత ఆరోగ్య సంరక్షణ ప్రతినిధి (DHR) అధికారం' },
  'Section 5 (Optional): Directions to be followed upon my death': { hi: 'खंड 5 (वैकल्पिक): मेरी मृत्यु के बाद पालन किए जाने वाले निर्देश', te: 'విభాగం 5 (ఐచ్ఛికం): నా మరణం తర్వాత పాటించవలసిన ఆదేశాలు' },
  Declaration: { hi: 'घोषणा', te: 'ప్రకటన' },
  'Validity of directive': { hi: 'निर्देश की वैधता', te: 'నిర్దేశం చెల్లుబాటు' },
  'Signature of the executor': { hi: 'निर्देश बनाने वाले के हस्ताक्षर', te: 'నిర్దేశం చేసిన వ్యక్తి సంతకం' },
  'Signature of witnesses': { hi: 'गवाहों के हस्ताक्षर', te: 'సాక్షుల సంతకాలు' },
  'Notarisation / Attestation': { hi: 'नोटरी / राजपत्रित अधिकारी द्वारा सत्यापन', te: 'నోటరీ / గెజిటెడ్ అధికారి ధృవీకరణ' },
};
const TRANSLATION_NOTE = {
  hi: 'अंग्रेज़ी पाठ ही मान्य है। हिंदी शीर्षक केवल समझने के लिए हैं।',
  te: 'ఆంగ్ల పాఠమే చెల్లుబాటు అవుతుంది. తెలుగు శీర్షికలు అర్థం చేసుకోవడానికి మాత్రమే.',
};

const DEVANAGARI = /[ऀ-ॿ᳐-᳿꣠-ꣿ]/u;
const TELUGU = /[ఀ-౿]/u;
const JOINERS = /[‌‍]/u;

export function amdPdfNeedsTelugu(version: AmdVersion, lang: AmdPdfLang) {
  return lang === 'te' || TELUGU.test(JSON.stringify(version.content));
}

const formatDate = (iso: string) => {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  return match ? `${match[3]}/${match[2]}/${match[1]}` : '';
};

const idLine = (type: IdType | null, last4: string) => [
  `1. Document name: ${type ? ID_TYPE_LABELS[type].en : ''}`,
  `2. ID no.: ${last4 ? `__________________ (ends ${last4})` : ''}`,
].join('\n');

type Kit = import('@pdf-lib/fontkit').Font;

/**
 * Noto Sans Telugu has mark records with NULL anchors (OpenType: "this mark does not attach here").
 * @pdf-lib/fontkit 1.1.1 crashes on them; skip the attachment as HarfBuzz does.
 */
function tolerateNullAnchors(kit: Kit): Kit {
  kit.layout('a');
  const engine = (kit as unknown as { _layoutEngine?: { engine?: { GPOSProcessor?: object | null } } })._layoutEngine?.engine;
  const processor = engine?.GPOSProcessor;
  if (processor) {
    const proto = Object.getPrototypeOf(processor) as { applyAnchor: (mark: { markAnchor?: unknown } | null, base: unknown, index: number) => void; saharaNullAnchors?: true };
    if (!proto.saharaNullAnchors) {
      const original = proto.applyAnchor;
      proto.applyAnchor = function applyAnchor(this: unknown, mark, base, index) {
        if (!mark?.markAnchor || !base) return;
        original.call(this, mark, base, index);
      };
      proto.saharaNullAnchors = true;
    }
  }
  return kit;
}

type Style = { size: number; bold?: boolean; italic?: boolean; color?: RGB };
type Run = { text: string; font: 'times' | 'deva' | 'telugu' };

export async function buildAmdPdf(input: AmdPdfInput, fonts: AmdPdfFonts): Promise<{ bytes: Uint8Array; sha256: string; pages: number }> {
  const { version, lang, contentSha256, printedAt } = input;
  const content = version.content;
  const pdf = await import('pdf-lib');
  // fontkit's Indic shaper (Devanagari, Telugu) calls a global `regeneratorRuntime`; the package sets it on load.
  // @ts-expect-error regenerator-runtime ships no type declarations.
  if (!('regeneratorRuntime' in globalThis)) await import('regenerator-runtime');
  const { default: fontkit } = await import('@pdf-lib/fontkit');
  const doc: PdfDocument = await pdf.PDFDocument.create();
  doc.registerFontkit(fontkit);
  const times = await doc.embedFont(pdf.StandardFonts.TimesRoman);
  const timesBold = await doc.embedFont(pdf.StandardFonts.TimesRomanBold);
  const timesItalic = await doc.embedFont(pdf.StandardFonts.TimesRomanItalic);
  const timesBoldItalic = await doc.embedFont(pdf.StandardFonts.TimesRomanBoldItalic);
  const winAnsi = new Set(times.getCharacterSet());
  // Full embedding keeps fontkit's shaped glyph IDs valid (Hindi and Telugu conjuncts).
  const shaped = {
    deva: { pdf: await doc.embedFont(fonts.devanagari, { subset: false }), kit: tolerateNullAnchors(fontkit.create(fonts.devanagari)) },
    telugu: fonts.telugu ? { pdf: await doc.embedFont(fonts.telugu, { subset: false }), kit: tolerateNullAnchors(fontkit.create(fonts.telugu)) } : null,
  };
  const when = new Date(version.generatedAt ?? printedAt.toISOString());
  doc.setTitle(`Advance Medical Directive — ${content.executant.name} — version ${version.version}`);
  doc.setAuthor(content.executant.name || 'Executor');
  doc.setSubject('Advance medical directive (living will) in the Vidhi Centre / ACP India model format');
  doc.setCreator('Sahara - A Palliative Care Companion');
  doc.setProducer('Sahara (pdf-lib)');
  doc.setKeywords([`version ${version.version}`, version.formVersion, `content-sha256 ${contentSha256}`]);
  doc.setCreationDate(when);
  doc.setModificationDate(when);

  const [width, height] = pdf.PageSizes.A4;
  const left = 72;
  const right = width - 72;
  const textWidth = right - left;
  const top = height - 70;
  const bottom = 70;
  const ink = pdf.rgb(0, 0, 0);
  const grey = pdf.rgb(0.35, 0.35, 0.35);
  const line = pdf.rgb(0.2, 0.2, 0.2);
  const pageFonts = new Map<string, PDFName>();
  let page: PDFPage = doc.addPage([width, height]);
  let y = top;
  let pageBottom = bottom + 46; // page 1 keeps room for the Vidhi attribution footnote

  const latinFont = (style: Style): PDFFont => (style.bold ? (style.italic ? timesBoldItalic : timesBold) : style.italic ? timesItalic : times);

  function runs(text: string): Run[] {
    const result: Run[] = [];
    let previous: Run['font'] = 'times';
    for (const char of text) {
      let font: Run['font'];
      if (TELUGU.test(char)) font = 'telugu';
      else if (DEVANAGARI.test(char)) font = 'deva';
      else if (JOINERS.test(char) || /[।॥]/u.test(char)) font = previous === 'telugu' ? 'telugu' : 'deva';
      else if (winAnsi.has(char.codePointAt(0)!)) font = 'times';
      else if (shaped.deva.kit.hasGlyphForCodePoint(char.codePointAt(0)!)) font = 'deva';
      else throw new Error(`The PDF cannot print the character “${char}”. Please remove or replace it.`);
      if (font === 'telugu' && !shaped.telugu) throw new Error('The Telugu font is missing. Try again when online.');
      const last = result.at(-1);
      if (last && last.font === font) last.text += char; else result.push({ text: char, font });
      previous = font;
    }
    return result;
  }

  function measure(text: string, style: Style): number {
    return runs(text).reduce((sum, run) => {
      if (run.font === 'times') return sum + latinFont(style).widthOfTextAtSize(run.text, style.size);
      const kit = (run.font === 'telugu' ? shaped.telugu! : shaped.deva).kit;
      const layout = kit.layout(run.text);
      return sum + layout.positions.reduce((advance, position) => advance + position.xAdvance, 0) * style.size / kit.unitsPerEm;
    }, 0);
  }

  function drawShaped(target: PDFPage, run: Run, x: number, baseline: number, style: Style): number {
    const face = run.font === 'telugu' ? shaped.telugu! : shaped.deva;
    const key = `${target.ref.toString()}:${run.font}`;
    let fontKey = pageFonts.get(key);
    if (!fontKey) { fontKey = target.node.newFontDictionary(face.pdf.name, face.pdf.ref); pageFonts.set(key, fontKey); }
    const color = style.color ?? ink;
    const scale = style.size / face.kit.unitsPerEm;
    const layout = face.kit.layout(run.text);
    let penX = x;
    let penY = baseline;
    target.pushOperators(
      pdf.pushGraphicsState(), pdf.setFillingRgbColor(color.red, color.green, color.blue),
      // ActualText keeps the original characters when the text is copied or searched.
      pdf.PDFOperator.of(pdf.PDFOperatorNames.BeginMarkedContentSequence, [pdf.PDFName.of('Span'), doc.context.obj({ ActualText: pdf.PDFHexString.fromText(run.text) }).toString()]),
      pdf.beginText(), pdf.setFontAndSize(fontKey, style.size),
    );
    layout.glyphs.forEach((glyph, index) => {
      const position = layout.positions[index];
      target.pushOperators(
        pdf.setTextMatrix(1, 0, 0, 1, penX + position.xOffset * scale, penY + position.yOffset * scale),
        pdf.showText(pdf.PDFHexString.of(glyph.id.toString(16).padStart(4, '0'))),
      );
      penX += position.xAdvance * scale;
      penY += position.yAdvance * scale;
    });
    target.pushOperators(pdf.endText(), pdf.endMarkedContent(), pdf.popGraphicsState());
    return penX - x;
  }

  function draw(text: string, x: number, baseline: number, style: Style, target = page) {
    let penX = x;
    for (const run of runs(text)) {
      if (run.font === 'times') {
        const font = latinFont(style);
        target.drawText(run.text, { x: penX, y: baseline, size: style.size, font, color: style.color ?? ink });
        penX += font.widthOfTextAtSize(run.text, style.size);
      } else penX += drawShaped(target, run, penX, baseline, style);
    }
    return penX - x;
  }

  function wrap(text: string, style: Style, maxWidth: number): string[] {
    const out: string[] = [];
    for (const paragraph of text.replace(/\r\n?/g, '\n').replace(/\t/g, '    ').split('\n')) {
      if (!paragraph.trim()) { out.push(''); continue; }
      let current = '';
      for (const token of paragraph.match(/\S+\s*/gu) ?? []) {
        if (measure((current + token).trimEnd(), style) <= maxWidth) { current += token; continue; }
        if (current) out.push(current.trimEnd());
        current = '';
        if (measure(token.trimEnd(), style) <= maxWidth) { current = token; continue; }
        for (const char of token) {
          if (current && measure(current + char, style) > maxWidth) { out.push(current); current = ''; }
          current += char;
        }
      }
      if (current.trim()) out.push(current.trimEnd());
    }
    return out;
  }

  const leading = (style: Style) => style.size * 1.38;

  function newPage() {
    page = doc.addPage([width, height]);
    y = top;
    pageBottom = bottom;
  }
  function ensure(space: number) { if (y - space < pageBottom) newPage(); }

  function paragraph(text: string, style: Style = { size: 11 }, indent = 0, after = 8, prefix = '') {
    const lines = wrap(text, style, textWidth - indent - (prefix ? 18 : 0));
    lines.forEach((value, index) => {
      ensure(leading(style));
      if (prefix && index === 0) draw(prefix, left + indent, y - style.size, style);
      draw(value, left + indent + (prefix ? 18 : 0), y - style.size, style);
      y -= leading(style);
    });
    y -= after;
  }
  const bullet = (text: string, style: Style = { size: 11 }, indent = 18) => paragraph(text, style, indent, 4, '•');
  const numbered = (items: string[], style: Style = { size: 11 }) => {
    items.forEach((text, index) => paragraph(text, style, 18, 3, `${index + 1}.`));
    y -= 5;
  };

  function heading(text: string, size = 13) {
    const translated = lang !== 'en' ? HEADINGS[text]?.[lang] : undefined;
    ensure(size * 1.4 + (translated ? 16 : 0) + 40);
    y -= 6;
    draw(text, left, y - size, { size, bold: true });
    y -= size * 1.5;
    if (translated) { draw(translated, left, y - 10, { size: 10, color: grey }); y -= 16; }
    y -= 4;
  }

  /** Bordered table like the Vidhi sample. Empty cells stay blank for handwriting. */
  function table(rows: string[][], widths: number[], options: { header?: string[]; minRow?: number } = {}) {
    const style: Style = { size: 10.5 };
    const pad = 5;
    const all = options.header ? [options.header, ...rows] : rows;
    all.forEach((row, rowIndex) => {
      const isHeader = Boolean(options.header) && rowIndex === 0;
      const cellStyle = (): Style => ({ ...style, bold: isHeader });
      const cellLines = row.map((cell, col) => wrap(cell, cellStyle(), widths[col] - pad * 2));
      const rowHeight = Math.max(options.minRow ?? 0, ...cellLines.map((lines) => lines.length * leading(style))) + pad * 2;
      ensure(rowHeight);
      let x = left;
      row.forEach((_, col) => {
        page.drawRectangle({ x, y: y - rowHeight, width: widths[col], height: rowHeight, borderColor: line, borderWidth: 0.6 });
        cellLines[col].forEach((value, lineIndex) => draw(value, x + pad, y - pad - style.size - lineIndex * leading(style) + 1, cellStyle()));
        x += widths[col];
      });
      y -= rowHeight;
    });
    y -= 12;
  }

  const blankLine = (label: string, value = '') => {
    ensure(22);
    const used = draw(label, left, y - 11, { size: 11 });
    if (value) draw(value, left + used + 4, y - 11, { size: 11 });
    else page.drawLine({ start: { x: left + used + 6, y: y - 13 }, end: { x: Math.min(right, Math.max(left + 260, left + used + 160)), y: y - 13 }, thickness: 0.5, color: grey });
    y -= 22;
  };

  // ---------------------------------------------------------------------------
  // Page 1: title and Section 1

  const ex = content.executant;
  const titleSize = 15;
  draw('ADVANCE MEDICAL DIRECTIVE', left, y - titleSize, { size: titleSize, bold: true });
  draw('1', left + measure('ADVANCE MEDICAL DIRECTIVE', { size: titleSize, bold: true }) + 1, y - titleSize + 7, { size: 8 });
  y -= titleSize * 1.6;
  if (lang !== 'en') {
    draw(HEADINGS['ADVANCE MEDICAL DIRECTIVE'][lang], left, y - 11, { size: 11, color: grey }); y -= 17;
    paragraph(TRANSLATION_NOTE[lang], { size: 9, color: grey }, 0, 4);
  }
  y -= 6;
  heading('Section 1: Personal details');
  paragraph('This advance medical directive (living will) and the designation of healthcare representative(s) is made by me:');
  table([
    ['Full name', ex.name],
    ['Gender', ex.gender],
    ['Date of Birth', formatDate(ex.dob) || 'DD/MM/YYYY'],
    ['Government ID', idLine(ex.idType, ex.idLast4)],
    ['Full permanent residential address', ex.permanentAddress],
    ['Full current residential address', ex.currentAddress || ex.permanentAddress],
    ['Municipality/Panchayat', ex.localBody],
  ], [150, textWidth - 150], { minRow: 18 });
  paragraph('If and when the time comes that I can no longer participate in decision-making regarding my own health and medical treatment, this directive should be treated as the final expression of my wishes.', { size: 11, bold: true, italic: true });
  if (content.values.trim()) {
    paragraph('Why I am making this directive, and the beliefs and values I wish to be respected during decision-making on my behalf:', { size: 11, italic: true }, 0, 4);
    paragraph(content.values.trim(), { size: 11 }, 18);
  }
  paragraph('I request that all concerned (including my designated healthcare representative(s), my treating team, and the others involved) must treat my wishes in this document as the primary basis for any decision regarding my medical treatment, particularly decisions relating to life-sustaining treatment.');
  // Vidhi attribution footnote on page 1.
  const firstPage = doc.getPages()[0];
  firstPage.drawLine({ start: { x: left, y: bottom + 38 }, end: { x: left + 140, y: bottom + 38 }, thickness: 0.5, color: ink });
  const footnote = wrap('This directive follows the model advance medical directive drafted by Shreyashi Ray, Rituparna Padhy, Dhvani Mehta and Hardik Dua of the Vidhi Centre for Legal Policy, with inputs from Dr Smriti Khanna (P.D. Hinduja Hospital), published with ACP India. Prepared with the Sahara app.', { size: 8.5 }, textWidth - 8);
  footnote.forEach((value, index) => draw(index === 0 ? `1 ${value}` : `  ${value}`, left, bottom + 27 - index * 10.5, { size: 8.5 }, firstPage));

  // ---------------------------------------------------------------------------
  // Section 2

  newPage();
  heading('Section 2: Directions relating to life-sustaining treatment');
  const conditionNames = { TERMINAL: 'a terminal condition', END_STAGE: 'an end-stage condition²', VEGETATIVE: 'a vegetative state³' } as const;
  const chosen = (['TERMINAL', 'END_STAGE', 'VEGETATIVE'] as const).filter((c) => content.conditions.includes(c)).map((c) => conditionNames[c]);
  const conditionText = chosen.length <= 1 ? chosen.join('') : `${chosen.slice(0, -1).join(', ')} or ${chosen.at(-1)}`;
  paragraph('In situations where my treating physician or team have determined that:', { size: 11 }, 0, 4);
  bullet(`There is no reasonable medical probability of recovery from ${conditionText}, and`);
  bullet('Any further medical intervention or course of treatment would only serve the purpose of artificially prolonging the process of dying.');
  y -= 4;
  paragraph('I direct that any life-prolonging medical procedure/treatment be withheld or withdrawn, and the course of natural death be permitted.', { size: 11, bold: true });
  if (content.anticipatedConditions.trim()) {
    paragraph('Health conditions that I anticipate, and my directions for them:', { size: 11, italic: true }, 0, 4);
    paragraph(content.anticipatedConditions.trim(), { size: 11 }, 18);
  }
  const refused = AMD_TREATMENTS.filter((t) => content.treatments[t] === 'REFUSE');
  const accepted = AMD_TREATMENTS.filter((t) => content.treatments[t] === 'ACCEPT');
  if (refused.length) {
    paragraph('Specifically, I direct the withholding or withdrawal of the following medical procedures or forms of treatment (I DO NOT WANT):', { size: 11 }, 0, 4);
    numbered(refused.map((t) => AMD_TREATMENT_PDF_TEXT[t].refuse));
  }
  if (accepted.length) {
    paragraph('I direct the following medical procedures or forms of treatment to be provided or continued (I WANT):', { size: 11 }, 0, 4);
    numbered(accepted.map((t) => AMD_TREATMENT_PDF_TEXT[t].accept));
  }
  const donation = content.afterDeath.organDonor || content.afterDeath.bodyDonation;
  paragraph(`Further, even when life-sustaining treatment is withheld/withdrawn, I direct the administration of medication or the performance of medical procedures for providing me with comfort care or to alleviate suffering in the form of pain, distress, or mental confusion${donation ? ' (or to facilitate organ or body donation)' : ''}.`);
  paragraph('Comfort feeding by mouth, mouth care and hand feeding must always continue for as long as I can take them. Nothing in this directive asks for any act intended to end my life.');
  paragraph('I understand that if a situation is ambiguous, or was not anticipated by me in this directive, this directive will not be applied to it, and decisions will be taken as per the guidelines of the Hon’ble Supreme Court for persons without a directive.');
  if (content.conditions.includes('END_STAGE') || content.conditions.includes('VEGETATIVE')) {
    ensure(70);
    page.drawLine({ start: { x: left, y: y - 2 }, end: { x: left + 140, y: y - 2 }, thickness: 0.5, color: ink });
    y -= 8;
    if (content.conditions.includes('END_STAGE')) paragraph('² ‘End-stage condition’ means an irreversible condition that is caused by injury, disease, or illness which has resulted in progressively severe and permanent deterioration, and which, to a reasonable degree of medical probability, treatment of the condition would be ineffective.', { size: 8.5 }, 0, 3);
    if (content.conditions.includes('VEGETATIVE')) paragraph('³ ‘Vegetative state’ is one where a person is awake but does not show any signs of awareness. A person is unlikely to recover when they have been in a vegetative state for more than 6 months (if caused by a non-traumatic brain injury) or more than 12 months (if caused by a traumatic brain injury). (Source: The National Health Services, UK)', { size: 8.5 }, 0, 3);
    y -= 6;
  }

  // ---------------------------------------------------------------------------
  // Section 3 (optional)

  heading('Section 3 (Optional): Wishes or desires during end-of-life care');
  const w = content.wishes;
  const wishLines: string[] = [];
  if (w.placeOfCare === 'NAMED_INSTITUTION' && w.institution.trim()) wishLines.push(`Subject to feasibility as determined by my “designated healthcare representative(s)”, I wish to stay in ${w.institution.trim()} for the duration of such palliative/comfort care.`);
  else if (w.placeOfCare) wishLines.push(`For the duration of end-of-life care, I wish to stay ${({ HOSPITAL: 'in a hospital or appropriate medical facility', HOSPICE: 'in a hospice or similar institution', HOME: 'at home', NAMED_INSTITUTION: 'in an institution chosen by my designated healthcare representative(s)' } as const)[w.placeOfCare]} for the duration of such palliative/comfort care.`);
  if (w.physician.trim()) wishLines.push(`I would like ${w.physician.trim()} to take care of my healthcare at this stage, subject to availability and feasibility.`);
  if (w.focus.trim()) wishLines.push(`In the course of end-of-life care, I wish for special attention towards: ${w.focus.trim()}`);
  if (w.preferredName.trim() || w.genderPronouns.trim()) wishLines.push(`Through the course of treatment, I would like my name${w.preferredName.trim() ? ` (${w.preferredName.trim()})` : ''} and gender${w.genderPronouns.trim() ? ` (${w.genderPronouns.trim()})` : ''} to be respected by all health workers, caregivers, support staff, and others, including in all documentation and communication.`);
  if (w.partnerName.trim()) wishLines.push(`I would like ${w.partnerName.trim()} to be referred to as ${w.partnerTerm.trim() || 'my partner'} in all communication by health workers, caregivers, support staff, and others.`);
  if (wishLines.length) wishLines.forEach((text) => bullet(text)); else paragraph('[No additional wishes recorded.]', { size: 11, italic: true });
  y -= 6;

  // ---------------------------------------------------------------------------
  // Section 4: DHRs

  heading('Section 4: Authorisation of the Designated Healthcare Representative (DHR)');
  const dhrs = content.dhrs.filter((dhr) => dhr.name.trim());
  paragraph(`In order to ensure that my wishes here are the primary basis for taking any treatment decisions, and to make decisions on my behalf for my medical treatment as may be needed from time to time, I have nominated ${numberInWords(dhrs.length)} (${dhrs.length}) designated healthcare representative(s) (DHRs) in order of preference below.${content.dhrsActJointly && dhrs.length > 1 ? ' I would like all listed persons to make decisions jointly.' : ''}`);
  paragraph('I have had extensive discussions with those named regarding my wishes, their responsibilities, and the situations which they may be expected to take decisions in. They have expressed that they are willing to be nominated as my “designated healthcare representative(s)”.');
  paragraph('I authorise my DHRs to exercise the following powers:', { size: 11 }, 0, 4);
  paragraph('They can obtain medical information from the treating team regarding my treatment, my health records, and any communications with the treating team;', { size: 11 }, 18, 4, '-');
  paragraph('They can take decisions and act on my behalf regarding my wishes in this document as necessary. These decisions would include, but are not limited to, providing consent, refusing, or withdrawing consent to any care, treatment, service, or procedure, guided primarily by my wishes as expressed in this directive.', { size: 11 }, 18, 8, '-');
  paragraph('The decisions taken by my DHRs should be respected regardless of any contrary views that any other person may hold.');
  const dhrTable = (dhr: AmdDhr, header: string) => table([
    ['a', 'Full Name', dhr.name],
    ['b', 'Relationship with the executor', dhr.relation],
    ['c', 'Date of Birth', formatDate(dhr.dob) || 'DD/MM/YYYY'],
    ['d', 'Government ID', idLine(dhr.idType, dhr.idLast4)],
    ['e', 'Mobile Number(s)', `1. Primary: ${dhr.phone}\n2. Any other: ${dhr.phoneAlt}`],
    ['f', 'Email ID(s)', `1. Primary: ${dhr.email}`],
    ['g', 'Permanent Residential Address', dhr.permanentAddress],
    ['h', 'Residential Address on the date of signing this document', dhr.currentAddress || dhr.permanentAddress],
  ], [46, 150, textWidth - 196], { header: ['Sr No', 'Particulars', header], minRow: 16 });
  if (dhrs[0]) {
    ensure(140);
    draw('I authorise the following person as my ', left, y - 11, { size: 11 });
    draw('primary DHR', left + measure('I authorise the following person as my ', { size: 11 }), y - 11, { size: 11, bold: true });
    y -= 22;
    dhrTable(dhrs[0], 'Information');
  }
  if (dhrs.length > 1) {
    paragraph('If my primary DHR is not available, or unable or unwilling to take a decision, then I authorise the following persons to act as my designated healthcare representative(s) in the stated order of preference:');
    dhrs.slice(1).forEach((dhr, index) => { ensure(150); paragraph(`${index + 1}.`, { size: 11 }, 18, 4); dhrTable(dhr, 'Details'); });
  }

  // ---------------------------------------------------------------------------
  // Section 5 (optional), declaration, validity

  heading('Section 5 (Optional): Directions to be followed upon my death');
  const after = content.afterDeath;
  const afterLines: string[] = [];
  if (after.organDonor) afterLines.push('I am a registered organ donor in accordance with the Transplantation of Human Organs and Tissues Act, 1994. My loved ones, including my designated healthcare representative(s), have been informed of my wishes regarding organ donation.');
  if (after.bodyDonation) afterLines.push(`I wish for my body to be donated for the purpose of medical research and education.${after.bodyDonationInstitution.trim() ? ` In this regard, I have communicated my wish to ${after.bodyDonationInstitution.trim()}.` : ''} My loved ones, including my designated healthcare representative(s), have been informed of my wishes regarding body donation.`);
  if (after.lastRites.trim()) afterLines.push(`My wishes about last rites: ${after.lastRites.trim()}`);
  if (afterLines.length) afterLines.forEach((text) => bullet(text)); else paragraph('[No directions recorded.]', { size: 11, italic: true });
  y -= 6;

  heading('Declaration');
  paragraph('I declare that at the time of signing and executing this document,', { size: 11 }, 0, 4);
  if (ex.readOutToMe) bullet('This document has been drafted as per my directions, and its contents have been read out and explained to me.');
  bullet(`I have the capacity and competence to understand the meaning and implications of everything mentioned in this document${content.declarations.capacityCertificateAttached ? '. A certificate of capacity and competence from a registered medical practitioner is attached' : ''};`);
  bullet('I have given careful thought and consideration to everything mentioned in this document;');
  bullet('I have willingly and voluntarily articulated and consented to everything mentioned in this document, without any coercion, duress, or undue influence from any person or entity.');
  y -= 4;
  const custodian = content.custodianBody.trim() || '______________________ (panchayat, municipality, district administration, municipal corporation, etc.)';
  const copyList = () => {
    bullet(`designated healthcare representative(s) (DHRs) mentioned in this directive${dhrs.length ? `: ${dhrs.map((dhr) => dhr.name.trim()).join(', ')}` : ''}`);
    if (content.familyPhysician.name.trim()) bullet(`my family/chosen physician: ${content.familyPhysician.name.trim()}${content.familyPhysician.registrationNo.trim() ? ` (Reg. No. ${content.familyPhysician.registrationNo.trim()})` : ''}`);
    bullet(`the Competent officer/custodian of ${custodian}`);
  };
  paragraph('This directive is executed in accordance with the order dated January 24, 2023, of the Hon’ble Supreme Court of India in MA No. 1699/2019 in WP (C) No. 215/2005 (SC Order). Further, a copy of this directive and healthcare power of attorney has been forwarded to:', { size: 11 }, 0, 4);
  copyList();
  y -= 6;
  heading('Validity of directive');
  paragraph('I declare that this directive, and my authorisations for DHRs, shall be valid and remain in force throughout my lifetime, unless I modify or revoke them.');
  paragraph('In any case, I reserve the right to execute a revised version of this document, which makes changes to my directions/wishes, or revoke this document in its entirety. I shall also share copies of the revised version with:', { size: 11 }, 0, 4);
  copyList();
  y -= 6;

  // ---------------------------------------------------------------------------
  // Signatures (always blank: the app never signs or validates an AMD)

  ensure(170);
  heading('Signature of the executor');
  y -= 44;
  paragraph(`[Full signature or Thumb impression]`, { size: 11 }, 0, 10);
  blankLine('Full name:', ex.name);
  blankLine('Date:');
  blankLine('Place:');
  y -= 6;

  heading('Signature of witnesses');
  paragraph('[Witnesses should preferably be independent, i.e. not a designated healthcare representative named above.]', { size: 10, italic: true }, 0, 6);
  for (const number of [1, 2]) {
    ensure(150);
    paragraph('I, as a witness, record my satisfaction that the document has been executed voluntarily and without any coercion, duress, inducement, or compulsion.', { size: 11 }, 18, 6, `${number}.`);
    table([[`Name:\nAddress:\n\nGovernment ID:\nEmail ID:\nMobile No.:`, '']], [textWidth / 2, textWidth / 2], { header: [`Witness ${number}`, 'Signature'] });
  }
  blankLine('Date:');
  blankLine('Place:');

  newPage();
  heading('Notarisation / Attestation');
  paragraph(`This directive and authorisation of designated healthcare representative(s) has been signed in the presence of the undersigned by ${ex.name || '______________________'} (Declarant) and I record my satisfaction that the document has been executed voluntarily and without any coercion or inducement or compulsion.`);
  y -= 8;
  paragraph('SIGNED BEFORE ME', { size: 11, bold: true }, 0, 50);
  paragraph('(Full name of notary / Gazetted Officer)', { size: 11 }, 0, 12);
  blankLine('Notary registration no. / Designation and office:');
  y -= 10;
  ensure(110);
  paragraph('APPROPRIATE AUTHORITY, STAMP', { size: 11, bold: true }, 0, 6);
  page.drawRectangle({ x: left, y: y - 90, width: 200, height: 90, borderColor: grey, borderWidth: 0.6, borderDashArray: [3, 3] });
  y -= 104;
  blankLine('Date:');
  blankLine('Place:');
  y -= 16;

  ensure(110);
  page.drawLine({ start: { x: left, y }, end: { x: right, y }, thickness: 0.5, color: grey });
  y -= 10;
  const record: Style = { size: 8.5, color: grey };
  paragraph(`Document record — Sahara AMD version ${version.version} · ${version.formVersion} · printed ${formatDate(when.toISOString())}`, record, 0, 2);
  paragraph(`Content SHA-256: ${contentSha256}`, record, 0, 2);
  paragraph('This paper is valid only when signed by hand by the executor before two witnesses and attested by a notary or gazetted officer. Sahara does not sign, e-sign or validate advance medical directives. If any part of this paper is changed by hand, make a new version instead.', record, 0, 2);

  // ---------------------------------------------------------------------------
  // Header and footer on every page: name, version, page x of y, content hash.

  const pages = doc.getPages();
  pages.forEach((sheet, index) => {
    const small: Style = { size: 8, color: grey };
    draw(`Advance Medical Directive of ${ex.name || '—'} · Version ${version.version}`, left, height - 40, small, sheet);
    const formLabel = version.formVersion.split(' (')[0];
    draw(formLabel, right - measure(formLabel, small), height - 40, small, sheet);
    sheet.drawLine({ start: { x: left, y: height - 46 }, end: { x: right, y: height - 46 }, thickness: 0.4, color: pdf.rgb(0.75, 0.75, 0.75) });
    const pageLabel = `Page ${index + 1} of ${pages.length}`;
    draw(pageLabel, (width - measure(pageLabel, { size: 9 })) / 2, 40, { size: 9 }, sheet);
    draw(`Content SHA-256 ${contentSha256.slice(0, 16)}…`, left, 26, small, sheet);
    const initials = 'Executor’s initials: ________';
    draw(initials, right - measure(initials, small), 26, small, sheet);
  });

  const bytes = await doc.save();
  return { bytes, sha256: await sha256Hex(bytes), pages: pages.length };
}

// ---------------------------------------------------------------------------
// Browser wrapper

const fontCache = new Map<string, Promise<Uint8Array>>();
function fetchFont(path: string): Promise<Uint8Array> {
  let pending = fontCache.get(path);
  if (!pending) {
    pending = fetch(path).then(async (response) => {
      if (!response.ok) throw new Error('The PDF font could not load. Check the connection and try again.');
      return new Uint8Array(await response.arrayBuffer());
    }).catch((error) => { fontCache.delete(path); throw error; });
    fontCache.set(path, pending);
  }
  return pending;
}

export async function downloadAmdPdf(input: AmdPdfInput): Promise<{ sha256: string; pages: number }> {
  const [devanagari, telugu] = await Promise.all([
    fetchFont('/fonts/NotoSansDevanagari.ttf'),
    amdPdfNeedsTelugu(input.version, input.lang) ? fetchFont('/fonts/NotoSansTelugu.ttf') : Promise.resolve(undefined),
  ]);
  const { bytes, sha256, pages } = await buildAmdPdf(input, { devanagari, telugu });
  const url = URL.createObjectURL(new Blob([new Uint8Array(bytes).buffer], { type: 'application/pdf' }));
  const link = window.document.createElement('a');
  const safeName = (input.version.content.executant.name || 'living-will').replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-|-$/g, '');
  link.href = url;
  link.download = `Advance-Medical-Directive-${safeName}-v${input.version.version}.pdf`;
  window.document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
  return { sha256, pages };
}

