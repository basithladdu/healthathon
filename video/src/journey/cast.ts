/* The cast. Spread these into <Person {...CAST.erDoctor} t={t} x={..} y={..} />. Never change a character's look between scenes. */
import {P, PersonProps} from './kit';

type Look = Partial<PersonProps>;
export const CAST: Record<string, Look> = {
  // the emergency-room doctor at 3 a.m. (scenes A, G)
  erDoctor: {skin: P.skin3, hair: P.hair, hairStyle: 'short', coat: true, top: P.sky, stetho: true, bottom: '#2F3A4F', seed: 3},
  // the patient: older woman, grey hair, in a hospital bed (A, G, H)
  patient: {skin: P.skin2, hairStyle: 'grey', top: '#9FB8C9', dupatta: '#C9D8E2', bottom: '#5E6E80', seed: 7},
  // her daughter, the caregiver (A, G, H)
  daughter: {skin: P.skin1, hair: P.hair, hairStyle: 'long', top: P.rose, dupatta: P.marigold, bottom: '#3D3550', seed: 11},
  // Dr Sujay, the doctor on our team (B, E, I)
  teamDoctor: {skin: P.skin4, hair: P.hair, hairStyle: 'short', glasses: true, coat: true, top: P.sage, stetho: false, bottom: '#2F3A4F', seed: 5},
  // Dr Sharada, the second doctor on our team (B, E, I): shoulder-length hair, stethoscope, coat
  sharada: {skin: P.skin2, hair: P.hair, hairStyle: 'long', coat: true, top: '#8A9BB0', stetho: true, bottom: '#3A3F55', seed: 29},
  // Shirin, the medical student on our team (B, D, H, I)
  medStudent: {skin: P.skin3, hair: P.hair, hairStyle: 'bun', top: P.plum, stetho: true, bottom: '#3A3F55', seed: 13},
  // engineers on the team: engA = Basith (curly hair), engB = Awaiz. engC is NOT a real team member: never use it.
  engA: {skin: P.skin1, hair: P.hair, hairStyle: 'curly', top: P.marigold, bottom: '#34405A', seed: 17},
  engB: {skin: P.skin2, hair: P.hair, hairStyle: 'short', top: P.indigo, bottom: '#2A2F40', glasses: true, seed: 19},
  engC: {skin: P.skin3, hair: P.hair, hairStyle: 'braid', top: P.sage, bottom: '#3A3F55', seed: 23},
};
