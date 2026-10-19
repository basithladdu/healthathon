export type ReviewedListing = {
  id: string; reviewedAt: string; expiresAt: string; reportedAt: string;
  listing: { name: string; city: string; state: string; address: string; phone: string; latitude: number; longitude: number; services: ('opd'|'ipd'|'home')[]; morphine: 'reported_yes'|'reported_no'|'unknown'; notes: string; sourceUrl: string };
};
export function readReviewedDirectory(value: unknown, now = Date.now()): ReviewedListing[] {
  if (!Array.isArray(value) || value.length > 5000) throw new Error('The reviewed directory could not be read.');
  return value.map((row: unknown) => {
    if (!row || typeof row !== 'object') throw new Error('Invalid directory entry.');
    const r = row as ReviewedListing, p = r.listing;
    if (typeof r.id !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(r.id) || !Number.isFinite(Date.parse(r.reviewedAt)) || !Number.isFinite(Date.parse(r.reportedAt)) || !Number.isFinite(Date.parse(r.expiresAt)) || Date.parse(r.expiresAt) <= now || !p ||
      !['name','city','state','address','phone','notes','sourceUrl'].every(k => typeof p[k as keyof typeof p] === 'string') || !p.name.trim() || p.name.length>160 || !p.city.trim() || p.city.length>80 || !p.state.trim() || p.state.length>80 || !p.address.trim() || p.address.length>400 || p.phone.length>40 || p.notes.length>1000 || p.sourceUrl.length>1000 || !/^https:\/\/[A-Za-z0-9][A-Za-z0-9.-]+(?::443)?(?:\/[^\s]*)?$/.test(p.sourceUrl) ||
      !Number.isFinite(p.latitude) || p.latitude < 6 || p.latitude > 38 || !Number.isFinite(p.longitude) || p.longitude < 68 || p.longitude > 98 || !Array.isArray(p.services) || p.services.length>3 || new Set(p.services).size!==p.services.length || p.services.some(s => !['opd','ipd','home'].includes(s)) || !['reported_yes','reported_no','unknown'].includes(p.morphine)) throw new Error('Invalid or expired directory entry.');
    // Construct the allowlisted projection rather than retaining unexpected server keys.
    return { id:r.id, reviewedAt:r.reviewedAt, reportedAt:r.reportedAt, expiresAt:r.expiresAt,
      listing:{name:p.name,city:p.city,state:p.state,address:p.address,phone:p.phone,notes:p.notes,sourceUrl:p.sourceUrl,latitude:p.latitude,longitude:p.longitude,services:p.services,morphine:p.morphine} };
  });
}
