// A Word (.docx) file of the goals-of-care form, made in the browser with no library: a stored (uncompressed) zip of three XML parts.
// Word, LibreOffice and Google Docs open it. Headings and two-column tables follow the legal form's Sections I to IV.
import type { GocFormDocument, GocFormRow, GocFormTable } from './goc-form.ts';

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; table[n] = c >>> 0; }
  return table;
})();
export function crc32(bytes: Uint8Array): number {
  let c = 0xffffffff;
  for (let i = 0; i < bytes.length; i++) c = CRC_TABLE[(c ^ bytes[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

/** A zip with every file stored, not compressed. Enough for a few kilobytes of XML. */
export function storedZip(files: Array<{ name: string; data: Uint8Array }>): Uint8Array {
  const enc = new TextEncoder();
  const parts: Uint8Array[] = []; const central: Uint8Array[] = [];
  let offset = 0;
  for (const file of files) {
    const name = enc.encode(file.name); const crc = crc32(file.data); const size = file.data.length;
    const local = new DataView(new ArrayBuffer(30));
    local.setUint32(0, 0x04034b50, true); local.setUint16(4, 20, true); local.setUint16(6, 0x0800, true); local.setUint16(8, 0, true);
    local.setUint16(10, 0, true); local.setUint16(12, 0x21, true); local.setUint32(14, crc, true); local.setUint32(18, size, true); local.setUint32(22, size, true);
    local.setUint16(26, name.length, true); local.setUint16(28, 0, true);
    parts.push(new Uint8Array(local.buffer), name, file.data);
    const dir = new DataView(new ArrayBuffer(46));
    dir.setUint32(0, 0x02014b50, true); dir.setUint16(4, 20, true); dir.setUint16(6, 20, true); dir.setUint16(8, 0x0800, true); dir.setUint16(10, 0, true);
    dir.setUint16(12, 0, true); dir.setUint16(14, 0x21, true); dir.setUint32(16, crc, true); dir.setUint32(20, size, true); dir.setUint32(24, size, true);
    dir.setUint16(28, name.length, true); dir.setUint32(42, offset, true);
    central.push(new Uint8Array(dir.buffer), name);
    offset += 30 + name.length + size;
  }
  const centralSize = central.reduce((sum, part) => sum + part.length, 0);
  const end = new DataView(new ArrayBuffer(22));
  end.setUint32(0, 0x06054b50, true); end.setUint16(8, files.length, true); end.setUint16(10, files.length, true);
  end.setUint32(12, centralSize, true); end.setUint32(16, offset, true);
  const all = [...parts, ...central, new Uint8Array(end.buffer)];
  const out = new Uint8Array(all.reduce((sum, part) => sum + part.length, 0));
  let at = 0; for (const part of all) { out.set(part, at); at += part.length; }
  return out;
}

const xml = (value: string) => value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const run = (text: string, props = '') => `<w:r>${props ? `<w:rPr>${props}</w:rPr>` : ''}<w:t xml:space="preserve">${xml(text)}</w:t></w:r>`;
const para = (text: string, { size = 22, bold = false, rtl = false, shade = '' } = {}) =>
  `<w:p><w:pPr>${shade ? `<w:shd w:val="clear" w:color="auto" w:fill="${shade}"/>` : ''}${rtl ? '<w:bidi/>' : ''}<w:spacing w:after="80"/></w:pPr>${text.split('\n').map((line, i) => (i ? '<w:r><w:br/></w:r>' : '') + run(line, `${bold ? '<w:b/>' : ''}<w:sz w:val="${size}"/>${rtl ? '<w:rtl/>' : ''}`)).join('')}</w:p>`;
const cell = (text: string, width: number, rtl: boolean, bold = false) =>
  `<w:tc><w:tcPr><w:tcW w:w="${width}" w:type="dxa"/>${bold ? '<w:shd w:val="clear" w:color="auto" w:fill="F4F7F5"/>' : ''}</w:tcPr>${para(text, { size: 20, bold, rtl })}</w:tc>`;

/** The value, bold when filled and grey when not discussed, then each tick box of the form, ticked ones bold, like the paper. */
const valueCell = (row: GocFormRow, rtl: boolean, width = 5760, span = 1) => {
  const p = (text: string, props: string) => `<w:p><w:pPr>${rtl ? '<w:bidi/>' : ''}<w:spacing w:after="40"/></w:pPr>${run(text, `${props}<w:sz w:val="20"/>${rtl ? '<w:rtl/>' : ''}`)}</w:p>`;
  const value = row.value ? p(row.value, row.open ? '<w:i/><w:color w:val="8D7F74"/>' : '<w:b/>') : '';
  const ticks = (row.ticks ?? []).map((tick) => p(`${tick.on ? '☑' : '☐'} ${tick.text}${tick.detail ? ` ${tick.detail}` : ''}`, tick.on ? '<w:b/>' : '<w:color w:val="6D6058"/>')).join('');
  return `<w:tc><w:tcPr><w:tcW w:w="${width}" w:type="dxa"/>${span > 1 ? `<w:gridSpan w:val="${span}"/>` : ''}</w:tcPr>${value || ticks ? `${value}${ticks}` : p('', '')}</w:tc>`;
};

/** The form's own tables (decision-makers, oxygen LEVEL / WHAT IT MEANS / CEILING, Section 19), header row shaded like the docx. */
const gridTable = (table: GocFormTable, rtl: boolean, border: string) => {
  const n = table.head.length;
  const widths = n === 3 && table.head[0].length <= 8 ? [1100, 6860, 1400] : n === 3 ? [2800, 3280, 3280] : n === 5 ? [2560, 2000, 1800, 1600, 1400] : Array.from({ length: n }, () => Math.floor(9360 / n));
  const lines = (text: string, head: boolean) => text.split('\n').map((line) => {
    const on = line.startsWith('☑');
    return `<w:p><w:pPr>${rtl ? '<w:bidi/>' : ''}<w:spacing w:after="20"/></w:pPr>${run(line, `<w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/>${head || on ? '<w:b/>' : ''}${line.trim() === 'Not discussed' ? '<w:i/><w:color w:val="8D7F74"/>' : ''}<w:sz w:val="${head ? 18 : 20}"/>${rtl ? '<w:rtl/>' : ''}`)}</w:p>`;
  }).join('');
  const tc = (text: string, i: number, head: boolean) => `<w:tc><w:tcPr><w:tcW w:w="${widths[i]}" w:type="dxa"/>${head ? '<w:shd w:val="clear" w:color="auto" w:fill="E4EDE8"/>' : ''}</w:tcPr>${lines(text, head)}</w:tc>`;
  return `<w:tbl><w:tblPr><w:tblW w:w="9360" w:type="dxa"/>${rtl ? '<w:bidiVisual/>' : ''}${border}</w:tblPr><w:tblGrid>${widths.map((x) => `<w:gridCol w:w="${x}"/>`).join('')}</w:tblGrid>`
    + `<w:tr>${table.head.map((text, i) => tc(text, i, true)).join('')}</w:tr>${table.rows.map((cells) => `<w:tr>${cells.map((text, i) => tc(text, i, false)).join('')}</w:tr>`).join('')}</w:tbl>`;
};

export function gocFormDocumentXml(doc: GocFormDocument, rtl = false, note = ''): string {
  const border = '<w:tblBorders>' + ['top', 'left', 'bottom', 'right', 'insideH', 'insideV'].map((side) => `<w:${side} w:val="single" w:sz="4" w:color="999999"/>`).join('') + '</w:tblBorders>';
  const body = [
    para(doc.title, { size: 32, bold: true, rtl }),
    ...doc.lead.map((line) => para(line, { size: 20, rtl })),
    ...doc.sections.flatMap((section) => [
      para(section.title, { size: 26, bold: true, rtl, shade: 'E4EDE8' }),
      ...section.groups.flatMap((group) => [
        ...(group.title ? [para(group.title, { size: 22, bold: true, rtl })] : []),
        ...(group.rows.length ? [`<w:tbl><w:tblPr><w:tblW w:w="9360" w:type="dxa"/>${rtl ? '<w:bidiVisual/>' : ''}${border}</w:tblPr><w:tblGrid><w:gridCol w:w="3600"/><w:gridCol w:w="5760"/></w:tblGrid>${group.rows.map((row) => (row.label
          ? `<w:tr>${cell(row.label, 3600, rtl, true)}${valueCell(row, rtl)}</w:tr>`
          : `<w:tr>${valueCell(row, rtl, 9360, 2)}</w:tr>`)).join('')}</w:tbl>`] : []),
        ...(group.table ? [gridTable(group.table, rtl, border)] : []),
        para(''),
      ]),
    ]),
    ...(note.trim() ? [para('Doctor’s note', { size: 24, bold: true, rtl }), para(note, { size: 20, rtl })] : []),
  ].join('');
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>${body}<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="567" w:footer="567" w:gutter="0"/></w:sectPr></w:body></w:document>`;
}

/** The .docx bytes. */
export function gocFormDocx(doc: GocFormDocument, rtl = false, note = ''): Uint8Array {
  const enc = new TextEncoder();
  return storedZip([
    { name: '[Content_Types].xml', data: enc.encode('<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>') },
    { name: '_rels/.rels', data: enc.encode('<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>') },
    { name: 'word/document.xml', data: enc.encode(gocFormDocumentXml(doc, rtl, note)) },
  ]);
}
