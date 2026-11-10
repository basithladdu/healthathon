import {TOTAL_SEC} from './kit';
/** scene windows in film seconds [from, to). Boundaries sit in the pauses between sentences. */
export const WIN: Record<string, [number, number]> = {
  A: [0, 10.6], // s01-s03 emergency room at 3 a.m.
  B: [10.6, 18.1], // s04-s05 where it started, a doctor on our team
  C: [18.1, 28.1], // s06-s07 we built everything
  D: [28.1, 41.0], // s08-s11 the question, collapse, keep one thing
  E: [41.0, 58.8], // s12-s14 record, doctor leads, review and sign
  F: [58.8, 68.1], // s15 languages
  G: [68.1, 72.8], // s16 back to the emergency room, one scan
  H: [72.8, 83.1], // s17-s19 oh wait: the caregiver
  I: [83.1, TOTAL_SEC], // s20-s22 three months, live on Play, title
};
