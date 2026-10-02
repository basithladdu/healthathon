// Deploy with verify_jwt=true. The Next server also supplies a private proxy secret.
import { REPORT_DIRECTORY } from './directory.ts';

declare const Deno: { env: { get(name: string): string | undefined }; serve(handler: (request: Request) => Promise<Response>): void };

const MAX_FILE = 4 * 1024 * 1024;
const BUCKET = 'saanthvana-bills';
const DISTRICTS = new Set(['Bagalkote', 'Ballari', 'Belagavi', 'Bengaluru Rural', 'Bengaluru Urban', 'Bidar', 'Chamarajanagar', 'Chikkaballapur', 'Chikkamagaluru', 'Chitradurga', 'Dakshina Kannada', 'Davanagere', 'Dharwad', 'Gadag', 'Hassan', 'Haveri', 'Kalaburagi', 'Kodagu', 'Kolar', 'Koppal', 'Mandya', 'Mysuru', 'Raichur', 'Ramanagara', 'Shivamogga', 'Tumakuru', 'Udupi', 'Uttara Kannada', 'Vijayapura', 'Vijayanagara', 'Yadgir', 'Bengaluru South']);
const DISTRICT_ALIASES: Record<string, string> = { 'Belagavi (Belgaum)': 'Belagavi', Belgaum: 'Belagavi', Tumkur: 'Tumakuru', Bangalore: 'Bengaluru Urban', Mysore: 'Mysuru', Shimoga: 'Shivamogga', Gulbarga: 'Kalaburagi', Bellary: 'Ballari', Bijapur: 'Vijayapura', Bagalkot: 'Bagalkote' };
const json = (value: unknown, status = 200) => Response.json(value, { status, headers: { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' } });
const fail = (error: string, status = 400) => json({ error }, status);
const digest = async (value: string | Uint8Array) => Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', typeof value === 'string' ? new TextEncoder().encode(value) : value as BufferSource)), (byte) => byte.toString(16).padStart(2, '0')).join('');
const safeEqual = (a: string, b: string) => { let mismatch = a.length ^ b.length; for (let i = 0; i < Math.max(a.length, b.length); i++) mismatch |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0); return mismatch === 0; };

type Row = {
  id: string; session_hash: string; centre_id: string; hospital_name: string; district: string; address: string;
  latitude: number | null; longitude: number | null; visit_date: string; outcome: string; oral_morphine: string;
  reporter_role: string; receipt_path: string | null; created_at: string;
};
function publicReport(row: Row) {
  return { id: row.id, centreId: row.centre_id, hospitalName: row.hospital_name, centreName: row.hospital_name, district: row.district, address: row.address,
    latitude: row.latitude, longitude: row.longitude, visitDate: row.visit_date, outcome: row.outcome, oralMorphine: row.oral_morphine,
    reporterRole: row.reporter_role, doctorStatus: row.reporter_role === 'doctor' ? 'self-reported' : 'never-verified', createdAt: row.created_at,
    receiptStatus: row.receipt_path ? 'attached' : 'not_attached' };
}

export async function handleReportRequest(request: Request) {
  const url = Deno.env.get('SUPABASE_URL');
  const key = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  if (!url || !key) return fail('Shared reports are not configured.', 503);
  const api = (path: string, init: RequestInit = {}) => fetch(`${url}${path}`, { ...init, headers: { Authorization: `Bearer ${key}`, apikey: key, ...init.headers }, redirect: 'error', signal: AbortSignal.timeout(15_000) });
  const removeBill = async (path: string) => {
    const result = await api(`/storage/v1/object/${BUCKET}`, { method: 'DELETE', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ prefixes: [path] }) });
    if (!result.ok) throw new Error('Private bill deletion failed');
  };
  try {
    const suppliedSecret = request.headers.get('X-Reports-Proxy-Secret') || '';
    if (suppliedSecret.length < 32 || suppliedSecret.length > 256) return fail('Unauthorized.', 401);
    const envSecret = Deno.env.get('SAANTHVANA_REPORTS_PROXY_SECRET');
    let expectedHash = envSecret ? await digest(envSecret) : '';
    if (!expectedHash) {
      const settings = await api('/rest/v1/saanthvana_report_settings?select=proxy_secret_hash&id=eq.true');
      if (!settings.ok) return fail('Shared reports are not configured.', 503);
      const settingsRows = await settings.json() as { proxy_secret_hash: string }[];
      expectedHash = settingsRows[0]?.proxy_secret_hash || '';
    }
    if (!/^[a-f0-9]{64}$/.test(expectedHash) || !safeEqual(await digest(suppliedSecret), expectedHash)) return fail('Unauthorized.', 401);
    const session = request.headers.get('X-Report-Session') || '';
    if (!/^[a-f0-9]{64}$/.test(session)) return fail('Reload the care app before reporting.', 401);
    const sessionHash = await digest(`session:${suppliedSecret}:${session}`);

    if (request.method === 'GET') {
      const response = await api('/rest/v1/saanthvana_centre_reports?published=eq.true&order=created_at.desc&limit=1000&select=id,session_hash,centre_id,hospital_name,district,address,latitude,longitude,visit_date,outcome,oral_morphine,reporter_role,receipt_path,created_at');
      if (!response.ok) return fail('Shared reports are temporarily unavailable.', 503);
      const rows: Row[] = await response.json();
      return json({ reports: rows.map(publicReport), ownedIds: rows.filter((row) => row.session_hash === sessionHash).map((row) => row.id), truncated: rows.length === 1000 });
    }

    if (request.method === 'DELETE') {
      const id = new URL(request.url).searchParams.get('id') || '';
      if (!/^[a-f0-9-]{36}$/.test(id)) return fail('Invalid report.');
      const filter = `id=eq.${id}&session_hash=eq.${sessionHash}`;
      const found = await api(`/rest/v1/saanthvana_centre_reports?${filter}&select=id,receipt_path`);
      if (!found.ok) return fail('The report could not be withdrawn. Try again.', 503);
      const rows = await found.json() as Pick<Row, 'id' | 'receipt_path'>[];
      const row = rows[0];
      if (!row) return fail('This report does not belong to this browser.', 404);
      // Hide first. If private-file deletion fails, retain its path for a retry.
      const hidden = await api(`/rest/v1/saanthvana_centre_reports?${filter}`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ published: false }) });
      if (!hidden.ok) return fail('The report could not be withdrawn. Try again.', 503);
      if (row.receipt_path) await removeBill(row.receipt_path);
      const removed = await api(`/rest/v1/saanthvana_centre_reports?${filter}`, { method: 'DELETE' });
      if (!removed.ok) return fail('The report is hidden; retry to finish withdrawal.', 503);
      return json({ removed: true });
    }
    if (request.method !== 'POST') return fail('Method not allowed.', 405);

    const length = request.headers.get('content-length');
    if (length && Number(length) > MAX_FILE + 24 * 1024) return fail('The bill must be no larger than 4 MB.', 413);
    // The authenticated Next proxy has already bounded the incoming stream.
    const form = await request.formData();
    for (const field of new Set(form.keys())) if (form.getAll(field).length !== 1) return fail('Duplicate form fields are not accepted.');
    const field = (name: string) => { const value = form.get(name); return typeof value === 'string' ? value.trim().replace(/\s+/g, ' ') : ''; };
    let hospitalName = field('hospitalName');
    let district = DISTRICT_ALIASES[field('district')] || field('district');
    let address = field('address');
    let latitude = field('latitude') ? Number(field('latitude')) : null;
    let longitude = field('longitude') ? Number(field('longitude')) : null;
    const suppliedCentre = field('centreId');
    if (suppliedCentre) {
      const canonical = REPORT_DIRECTORY.find((centre) => centre.id === suppliedCentre);
      if (canonical) {
        hospitalName = canonical.hospitalName;
        district = DISTRICT_ALIASES[canonical.district] || canonical.district;
        address = canonical.address;
        latitude = canonical.latitude;
        longitude = canonical.longitude;
      } else {
        if (!/^community-[a-f0-9]{24}$/.test(suppliedCentre)) return fail('Choose a listed centre or add a new hospital.');
        const existing = await api(`/rest/v1/saanthvana_centre_reports?centre_id=eq.${suppliedCentre}&published=eq.true&order=created_at.asc&limit=1&select=hospital_name,district,address,latitude,longitude`);
        if (!existing.ok) return fail('The hospital could not be checked. Try again.', 503);
        const centres = await existing.json() as Pick<Row, 'hospital_name' | 'district' | 'address' | 'latitude' | 'longitude'>[];
        if (!centres[0]) return fail('This community hospital is no longer listed. Add it as a new hospital.');
        hospitalName = centres[0].hospital_name;
        district = centres[0].district;
        address = centres[0].address;
        latitude = centres[0].latitude;
        longitude = centres[0].longitude;
      }
    }
    const visitDate = field('visitDate');
    const outcome = field('outcome');
    const oralMorphine = field('oralMorphine') || 'unknown';
    const reporterRole = field('reporterRole');
    if (hospitalName.length < 2 || hospitalName.length > 160 || address.length < 5 || address.length > 400 || /[\x00-\x1f<>]/.test(hospitalName + address)) return fail('Enter a hospital name and address without personal patient details.');
    if (!DISTRICTS.has(district)) return fail('Choose a Karnataka district.');
    const today = new Date(Date.now() + 330 * 60_000).toISOString().slice(0, 10);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(visitDate) || !Number.isFinite(Date.parse(`${visitDate}T00:00:00Z`)) || new Date(`${visitDate}T00:00:00Z`).toISOString().slice(0, 10) !== visitDate || visitDate > today || visitDate < '2000-01-01') return fail('Enter a valid visit date, no later than today.');
    if (!['received', 'unavailable'].includes(outcome) || !['available', 'unavailable', 'unknown'].includes(oralMorphine) || !['patient', 'caregiver', 'doctor'].includes(reporterRole)) return fail('Choose a visit outcome, medicine status and reporter role.');
    if (field('consent') !== 'true') return fail('Consent is required before sharing.');
    if ((latitude === null) !== (longitude === null) || (latitude !== null && (!Number.isFinite(latitude) || !Number.isFinite(longitude) || latitude < 11 || latitude > 19 || longitude! < 74 || longitude! > 79))) return fail('Enter both coordinates within Karnataka, or leave both blank.');
    const file = form.get('bill');
    let bytes: Uint8Array | null = null;
    let extension = '';
    if (file !== null && (!(file instanceof File) || file.size === 0)) return fail('Choose a valid bill file.');
    if (file instanceof File) {
      if (file.size > MAX_FILE) return fail('The bill must be no larger than 4 MB.', 413);
      bytes = new Uint8Array(await file.arrayBuffer());
      const start = Array.from(bytes.slice(0, 12));
      const ascii = (from: number, to: number) => String.fromCharCode(...start.slice(from, to));
      if (file.type === 'application/pdf' && ascii(0, 5) === '%PDF-') extension = 'pdf';
      if (file.type === 'image/jpeg' && start[0] === 255 && start[1] === 216 && start[2] === 255) extension = 'jpg';
      if (file.type === 'image/png' && start.slice(0, 8).join(',') === '137,80,78,71,13,10,26,10') extension = 'png';
      if (file.type === 'image/webp' && ascii(0, 4) === 'RIFF' && ascii(8, 12) === 'WEBP') extension = 'webp';
      if (!extension) return fail('Use a JPEG, PNG, WebP or PDF bill with the correct file type.');
    }
    const id = crypto.randomUUID();
    const normalized = (value: string) => value.normalize('NFKC').toLocaleLowerCase('en-IN').replace(/[^\p{L}\p{N}]/gu, '');
    const centreKey = await digest([hospitalName, district, address].map(normalized).join('|'));
    const path = bytes ? `${id}/receipt.${extension}` : null;
    const row = { id, session_hash: sessionHash, centre_key: centreKey, centre_id: suppliedCentre || `community-${centreKey.slice(0, 24)}`, hospital_name: hospitalName, district, address, latitude, longitude, visit_date: visitDate, outcome, oral_morphine: oralMorphine, reporter_role: reporterRole, receipt_path: path, receipt_hash: bytes ? await digest(bytes) : null };
    const ip = request.headers.get('X-Report-Client-IP');
    const reserved = await api('/rest/v1/rpc/saanthvana_reserve_report', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ p_report: row, p_ip_hash: ip ? await digest(`ip:${suppliedSecret}:${ip}`) : null }) });
    if (!reserved.ok) {
      const error = await reserved.json() as { code?: string; message?: string };
      if (error.code === '23505') return fail('This visit or bill has already been reported.', 409);
      if (error.message === 'REPORT_RATE_LIMIT') return fail('The daily reporting limit has been reached. Try again tomorrow.', 429);
      return fail('The report could not be saved. Try again.', 503);
    }
    try {
      if (bytes && path && file instanceof File) {
        const upload = await api(`/storage/v1/object/${BUCKET}/${path}`, { method: 'POST', headers: { 'Content-Type': file.type, 'x-upsert': 'false' }, body: bytes as BodyInit });
        if (!upload.ok) throw new Error('Upload failed');
      }
      const published = await api(`/rest/v1/saanthvana_centre_reports?id=eq.${id}`, { method: 'PATCH', headers: { 'Content-Type': 'application/json', Prefer: 'return=representation' }, body: JSON.stringify({ published: true }) });
      if (!published.ok) throw new Error('Publish failed');
      const saved: Row[] = await published.json();
      if (saved.length !== 1) throw new Error('Missing saved report');
      return json({ report: publicReport(saved[0]) }, 201);
    } catch {
      // Keep a hidden row with its receipt path if cleanup fails; never expose it.
      const hidden = await api(`/rest/v1/saanthvana_centre_reports?id=eq.${id}`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ published: false }) });
      if (hidden.ok) {
        // A timed-out upload may still have reached Storage.
        if (path) await removeBill(path);
        await api(`/rest/v1/saanthvana_centre_reports?id=eq.${id}`, { method: 'DELETE' });
      }
      return fail('The report could not be saved. Try again.', 503);
    }
  } catch { return fail('Shared reports are temporarily unavailable. Try again.', 503); }
}

if (typeof Deno !== 'undefined') Deno.serve(handleReportRequest);
