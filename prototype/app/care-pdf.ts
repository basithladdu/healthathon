'use client';

import type { PDFPage, PDFName, RGB } from 'pdf-lib';
import type { SummaryRelease } from './summary-state';
import { CARE_NOTE_KIND_LABELS, careNoteReleaseId, matchingCareNoteAcknowledgements, hasChangedSignedCareNote, type CareNoteAcknowledgement } from './care-note-signing-state';

export type CarePdfSection = { heading: string; lines: string[] };
export type CarePdfOptions = {
  fileName: string; title: string; subtitle?: string; sections: CarePdfSection[];
  careNote?: { release: SummaryRelease; acknowledgements: readonly CareNoteAcknowledgement[] };
};

let fontBytes: Promise<Uint8Array> | null = null;

function loadFont() {
  if (!fontBytes) {
    fontBytes = fetch('/fonts/NotoSansDevanagari.ttf').then(async (response) => {
      if (!response.ok) throw new Error('The PDF font could not load. Try again.');
      return new Uint8Array(await response.arrayBuffer());
    }).catch((error) => { fontBytes = null; throw error; });
  }
  return fontBytes;
}

export async function downloadCarePdf({ fileName, title, subtitle, sections, careNote }: CarePdfOptions): Promise<void> {
  if (careNote && hasChangedSignedCareNote(careNote.acknowledgements, careNote.release)) throw new Error('This note no longer matches its signed version.');
  const acknowledgements = careNote ? matchingCareNoteAcknowledgements(careNote.acknowledgements, careNote.release) : [];
  const outputSections = [...sections];
  if (careNote) {
    const release = careNote.release;
    if (release.signature) outputSections.push({ heading: 'Doctor signature', lines: [release.signature.name, new Date(release.signature.signedAt).toLocaleString('en-GB'), 'Typed name'] });
    for (const acknowledgement of acknowledgements) outputSections.push({ heading: acknowledgement.signerRole === 'patient' ? 'Patient signature' : 'Family signature', lines: [acknowledgement.signerName, `${new Date(acknowledgement.signedAt).toLocaleString('en-GB')} · Version ${acknowledgement.version}`, acknowledgement.statement, 'Typed name'] });
    outputSections.push({ heading: 'Version record', lines: [
      careNoteReleaseId(release),
      ...(release.priorVersion !== undefined ? [`Started from version ${release.priorVersion}; reviewed for this conversation.`] : []),
      acknowledgements.length ? 'This version is saved with the signatures above. Changes require a new app version.' : release.signature && release.noteKind ? 'Patient/family review is pending.' : 'No separate patient/family signature is recorded.',
      'Typed names recorded in the app. This PDF has no verified digital signature or cryptographic seal.',
      'Care conversation record. Not a prescription or an advance medical directive.',
    ] });
  }
  const pdf = await import('pdf-lib');
  const { default: fontkit } = await import('@pdf-lib/fontkit');
  const bytes = await loadFont();
  const document = await pdf.PDFDocument.create();
  document.registerFontkit(fontkit);
  // Full embedding keeps fontkit's shaped glyph IDs stable, including Hindi ligatures.
  const embeddedFont = await document.embedFont(bytes, { subset: false });
  const shapingFont = fontkit.create(bytes);
  document.setTitle(title);
  document.setAuthor('Saanthvana');
  document.setCreator('Saanthvana');

  const [width, height] = pdf.PageSizes.A4;
  const margin = 44;
  const textWidth = width - margin * 2;
  const ink = pdf.rgb(0.12, 0.24, 0.21);
  const muted = pdf.rgb(0.34, 0.40, 0.38);
  const accent = pdf.rgb(0.64, 0.30, 0.21);
  const pageFonts = new Map<PDFPage, PDFName>();
  const segmenter = new Intl.Segmenter('hi', { granularity: 'grapheme' });
  let page = document.addPage([width, height]);
  let y = height - 47;

  function runs(text: string) {
    // Shape the scripts separately when a line contains both English and Hindi.
    return (text.replace(/\t/g, '    ').match(/[\u0900-\u097f\u1cd0-\u1cff\ua8e0-\ua8ff\u200c\u200d]+|[^\u0900-\u097f\u1cd0-\u1cff\ua8e0-\ua8ff\u200c\u200d]+/gu) ?? []).map((part) => shapingFont.layout(part));
  }

  function measure(text: string, size: number) {
    return runs(text).reduce((sum, run) => sum + run.positions.reduce((advance, position) => advance + position.xAdvance, 0), 0) * size / shapingFont.unitsPerEm;
  }

  function draw(text: string, x: number, baseline: number, size: number, color: RGB, target = page) {
    if (!text) return;
    for (const character of text) {
      if (!/\s|[\u200c\u200d]/u.test(character) && !shapingFont.hasGlyphForCodePoint(character.codePointAt(0)!)) {
        throw new Error('The PDF cannot show every character in this note. Use Print to keep the original wording.');
      }
    }
    let fontKey = pageFonts.get(target);
    if (!fontKey) { fontKey = target.node.newFontDictionary(embeddedFont.name, embeddedFont.ref); pageFonts.set(target, fontKey); }
    const scale = size / shapingFont.unitsPerEm;
    let penX = x;
    let penY = baseline;
    // ActualText preserves the original reading order when text is copied from the PDF.
    target.pushOperators(
      pdf.pushGraphicsState(), pdf.setFillingRgbColor(color.red, color.green, color.blue),
      pdf.PDFOperator.of(pdf.PDFOperatorNames.BeginMarkedContentSequence, [pdf.PDFName.of('Span'), document.context.obj({ ActualText: pdf.PDFHexString.fromText(text) }).toString()]),
      pdf.beginText(), pdf.setFontAndSize(fontKey, size),
    );
    for (const run of runs(text)) {
      run.glyphs.forEach((glyph, index) => {
        const position = run.positions[index];
        target.pushOperators(
          pdf.setTextMatrix(1, 0, 0, 1, penX + position.xOffset * scale, penY + position.yOffset * scale),
          pdf.showText(pdf.PDFHexString.of(glyph.id.toString(16).padStart(4, '0'))),
        );
        penX += position.xAdvance * scale;
        penY += position.yAdvance * scale;
      });
    }
    target.pushOperators(pdf.endText(), pdf.endMarkedContent(), pdf.popGraphicsState());
  }

  function wrap(text: string, size: number): string[] {
    const result: string[] = [];
    for (const paragraph of text.replace(/\r\n?/g, '\n').split('\n')) {
      if (!paragraph) { result.push(''); continue; }
      let line = '';
      for (const token of paragraph.match(/\S+\s*|\s+/gu) ?? []) {
        if (measure(line + token, size) <= textWidth) { line += token; continue; }
        if (line) { result.push(line); line = ''; }
        if (measure(token, size) <= textWidth) { line = token; continue; }
        for (const { segment } of segmenter.segment(token)) {
          if (line && measure(line + segment, size) > textWidth) { result.push(line); line = ''; }
          line += segment;
        }
      }
      if (line) result.push(line);
    }
    return result;
  }

  function newPage() {
    page = document.addPage([width, height]);
    y = height - 47;
    draw('SAANTHVANA', margin, y, 9, accent);
    y -= 28;
  }

  function ensureRoom(space: number) { if (y - space < 52) newPage(); }

  function write(text: string, size = 10.5, color = ink, leading = 17) {
    for (const line of wrap(text, size)) {
      ensureRoom(leading);
      draw(line, margin, y, size, color);
      y -= leading;
    }
  }

  draw('SAANTHVANA', margin, y, 9, accent);
  y -= 31;
  write(title, 21, ink, 30);
  if (subtitle) { y -= 2; write(subtitle, 10, muted, 16); }
  if (careNote?.release.noteKind) write(CARE_NOTE_KIND_LABELS[careNote.release.noteKind], 10, accent, 16);
  y -= 12;
  page.drawLine({ start: { x: margin, y }, end: { x: width - margin, y }, thickness: 1, color: pdf.rgb(0.82, 0.85, 0.79) });
  y -= 23;

  for (const section of outputSections) {
    ensureRoom(52);
    write(section.heading, 12, accent, 19);
    y -= 2;
    for (const line of section.lines) { write(line); y -= 4; }
    y -= 12;
  }

  const pages = document.getPages();
  pages.forEach((sheet, index) => {
    sheet.drawLine({ start: { x: margin, y: 37 }, end: { x: width - margin, y: 37 }, thickness: 0.5, color: pdf.rgb(0.87, 0.88, 0.84) });
    draw(`${index + 1} / ${pages.length}`, width - margin - 30, 23, 8, muted, sheet);
  });

  const output = await document.save();
  const url = URL.createObjectURL(new Blob([new Uint8Array(output).buffer], { type: 'application/pdf' }));
  const link = window.document.createElement('a');
  link.href = url;
  link.download = `${fileName.replace(/[/\\]/g, '-').replace(/\.[^.]+$/, '') || 'care-note'}.pdf`;
  window.document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
