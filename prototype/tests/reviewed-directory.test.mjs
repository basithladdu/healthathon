import test from 'node:test';
import assert from 'node:assert/strict';
import { readReviewedDirectory } from '../app/care-reviewed-directory.ts';
const now=Date.parse('2026-10-19T12:00:00Z');
const valid=()=>({id:'99999999-9999-4999-8999-999999999999',reviewedAt:'2026-10-19T10:00:00Z',reportedAt:'2026-10-18T12:00:00Z',expiresAt:'2026-11-18T12:00:00Z',listing:{name:'Synthetic centre',city:'Synthetic city',state:'Karnataka',address:'Synthetic address',phone:'',latitude:12,longitude:77,services:['opd'],morphine:'unknown',notes:'Call for current services.',sourceUrl:'https://example.invalid/facility'}});
test('public projection retains dates and original source but discards private extras',()=>{const row={...valid(),full_name:'Private',email:'private@example.invalid'};row.listing.registration='PRIVATE';const out=readReviewedDirectory([row],now);assert.equal(out[0].reportedAt,row.reportedAt);assert.equal(out[0].listing.sourceUrl,row.listing.sourceUrl);assert.ok(!JSON.stringify(out).includes('PRIVATE'));assert.ok(!JSON.stringify(out).includes('private@example.invalid'));});
test('empty reviewed directory is valid without fabricating facilities',()=>assert.deepEqual(readReviewedDirectory([],now),[]));
test('expired or malformed date fails closed',()=>{for(const patch of [{expiresAt:'2026-10-18T12:00:00Z'},{reviewedAt:'not a date'},{id:'------------------------------------'}])assert.throws(()=>readReviewedDirectory([{...valid(),...patch}],now));});
test('unreviewed URL/coordinates/status are rejected',()=>{for(const patch of [{sourceUrl:'javascript:alert(1)'},{latitude:100},{longitude:null},{morphine:'live_stock'},{services:['unknown']}]){const row=valid();row.listing={...row.listing,...patch};assert.throws(()=>readReviewedDirectory([row],now));}});
