import {continueRender, delayRender, staticFile} from 'remotion';

const faces: [string, string, string][] = [
  // [family, file, weight]
  ['SaharaHead', 'fonts/sahara_anek_semibold.ttf', '600'],
  ['SaharaSans', 'fonts/noto-sans-latin-400-normal.woff2', '400'],
  ['SaharaSans', 'fonts/noto-sans-latin-600-normal.woff2', '600'],
  ['SaharaSans', 'fonts/noto-sans-devanagari-devanagari-400-normal.woff2', '400'],
  ['SaharaSans', 'fonts/noto-sans-devanagari-devanagari-600-normal.woff2', '600'],
  ['SaharaSans', 'fonts/noto-sans-telugu-telugu-400-normal.woff2', '400'],
  ['SaharaSans', 'fonts/noto-sans-telugu-telugu-600-normal.woff2', '600'],
  ['SaharaSans', 'fonts/noto-sans-kannada-kannada-400-normal.woff2', '400'],
  ['SaharaSans', 'fonts/noto-sans-kannada-kannada-600-normal.woff2', '600'],
  ['NotoUrdu', 'fonts/noto-nastaliq-urdu-arabic-400-normal.woff2', '400'],
  ['NotoUrdu', 'fonts/noto-nastaliq-urdu-arabic-600-normal.woff2', '600'],
];

let started = false;
export const loadFonts = () => {
  if (started || typeof document === 'undefined') return;
  started = true;
  const h = delayRender('fonts');
  Promise.all(
    faces.map(async ([family, file, weight]) => {
      const ff = new FontFace(family, `url(${staticFile(file)})`, {weight});
      await ff.load();
      document.fonts.add(ff);
    }),
  )
    .catch((e) => console.error('font load failed', e))
    .finally(() => continueRender(h));
};
