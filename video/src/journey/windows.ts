import {BASE_TOTAL as TOTAL_SEC} from './kit';
/** scene windows on the BASE timeline (base_words.json); Journey maps them to film time with toNew(). [from, to). Boundaries sit in the pauses between sentences. */
export const WIN: Record<string, [number, number]> = {
  A: [0, 10.51], // s01-s03 emergency room at 3 a.m.
  B: [10.51, 28.42], // s04-s05 Sahara started; the five of us (photos); Dr Sujay in August
  C: [28.42, 38.24], // s06-s07 we built everything (Basith, Awaiz)
  D: [38.24, 61.06], // s08-s11c Shirin's question; the switches; back to the goals of care talk
  E: [61.06, 83.21], // s12-s14 record, 13 topics, doctor leads, review, sign, family signs, lock
  X: [83.21, 104.41], // s14a-s14e at home: symptoms, medicines, documents, centres and morphine
  F: [104.41, 113.67], // s15 languages
  G: [113.67, 118.52], // s16 back to the emergency room, one scan
  H: [118.52, 128.81], // s17-s19 oh wait: the caregiver
  I: [128.81, TOTAL_SEC], // s20-s22 three months, survey, live on Google Play, title
};
