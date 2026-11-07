// Builds a sample Vidhi-format advance care plan PDF from synthetic data.
// Usage (from prototype/): node --experimental-strip-types scripts/amd-sample-pdf.mjs [en|hi|te] [outDir]
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { amdContentHash, emptyAmdContent, markGenerated, newAmdDraft } from '../app/amd-rules.ts';
import { buildAmdPdf } from '../app/amd-pdf.ts';

const here = dirname(fileURLToPath(import.meta.url));
const lang = process.argv[2] ?? 'en';
const outDir = resolve(process.argv[3] ?? join(here, '..', '..', 'output', 'amd-sample'));
const now = new Date('2026-10-29T10:00:00+05:30');

const content = emptyAmdContent('Lakshmi Devi Reddy');
Object.assign(content.executant, {
  gender: 'Female', dob: '1958-03-14', idType: 'AADHAAR', idLast4: '4821',
  permanentAddress: '12-4-56, Gandhi Nagar, Vijayawada, Andhra Pradesh 520003',
  currentAddress: '', localBody: 'Vijayawada Municipal Corporation',
});
Object.assign(content.declarations, { capacity: true, consideredCarefully: true, voluntary: true, understandsNotAppliedIfUnclear: true });
content.values = 'I have lived a full life. When I can no longer recognise my family, I do not want machines to keep me alive. I want to be at home, free of pain, with my grandchildren near me. (मैं अपने घर पर रहना चाहती हूँ।) (నేను ఇంట్లోనే ఉండాలనుకుంటున్నాను.)';
content.conditions = ['TERMINAL', 'END_STAGE', 'VEGETATIVE'];
content.anticipatedConditions = 'Advanced ovarian cancer that has spread and no longer responds to treatment.';
Object.assign(content.treatments, { CPR: 'REFUSE', VENTILATION: 'REFUSE', DIALYSIS: 'REFUSE', TUBE_FEEDING: 'REFUSE', IV_FLUIDS_ANTIBIOTICS: 'ACCEPT', CHEMOTHERAPY: 'REFUSE', SURGERY: 'REFUSE' });
Object.assign(content.wishes, { placeOfCare: 'HOME', physician: 'Dr K. Srinivas, palliative physician, Government General Hospital, Vijayawada', focus: 'relief of breathlessness and confusion, so that I can spend meaningful time with my family' });
content.dhrs = [
  { id: 'dhr-1', name: 'Ravi Kumar Reddy', relation: 'Son', dob: '1984-07-02', idType: 'VOTER_ID', idLast4: '7390', phone: '+91 98480 12345', phoneAlt: '', email: 'ravi.reddy@example.org', permanentAddress: '12-4-56, Gandhi Nagar, Vijayawada 520003', currentAddress: '', agreed: true },
  { id: 'dhr-2', name: 'Padmavathi Rao', relation: 'Daughter', dob: '1987-11-19', idType: 'AADHAAR', idLast4: '1066', phone: '+91 99890 54321', phoneAlt: '', email: '', permanentAddress: 'Flat 302, Sai Residency, Kukatpally, Hyderabad 500072', currentAddress: '', agreed: true },
];
content.familyPhysician = { name: 'Dr Anitha Prasad', registrationNo: 'APMC 45678', phone: '+91 866 2470000', address: 'Benz Circle, Vijayawada' };
content.custodianBody = 'Vijayawada Municipal Corporation';
content.afterDeath.lastRites = 'Hindu rites at Krishnalanka; cremation.';

const draft = newAmdDraft('sample-patient', 'sample-script', now, content);
const contentSha256 = await amdContentHash(draft);
const fontDir = join(here, '..', 'public', 'fonts');
const fonts = { devanagari: new Uint8Array(await readFile(join(fontDir, 'NotoSansDevanagari.ttf'))), telugu: new Uint8Array(await readFile(join(fontDir, 'NotoSansTelugu.ttf'))) };
const generated = markGenerated([draft], draft.id, { pdfSha256: 'pending', contentSha256, lang }, now);
if (!generated.ok) throw new Error(generated.issues.map((issue) => issue.message).join('\n'));
const { bytes, sha256, pages } = await buildAmdPdf({ version: generated.version, lang, contentSha256, printedAt: now }, fonts);
const again = await buildAmdPdf({ version: generated.version, lang, contentSha256, printedAt: now }, fonts);
await mkdir(outDir, { recursive: true });
const file = join(outDir, `sample-amd-${lang}.pdf`);
await writeFile(file, bytes);
console.log(JSON.stringify({ file, pages, bytes: bytes.length, sha256, contentSha256, deterministic: again.sha256 === sha256 }, null, 2));
