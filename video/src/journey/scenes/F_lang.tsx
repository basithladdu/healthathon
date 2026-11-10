/* Scene F 58.8-68.1: families don't talk in one language. Five languages, then a mixed sentence.
   Real app footage f06 (language switch). Sarvam AI credit. */
import React from 'react';
import {AbsoluteFill, Img, staticFile} from 'remotion';
import {at, camAt, clamp, easeInCubic, easeOutQuint, FONT_BODY, FONT_HEAD, mv, P, Person, pop, ramp, useGT, World} from '../kit';
import {CAST} from '../cast';
import {FgLeaves, Motes, PhoneSeq, Plant, toScreen} from './E_parts';
import {WIN} from '../windows';

const SON = {skin: P.skin2, hair: P.hair, hairStyle: 'short' as const, top: '#4E9A8A', bottom: '#3A3F55', seed: 31};
const START = WIN.F[0];
const END = WIN.F[1];

const T = {
  families: at('s15', 'families'),
  talk: at('s15', 'talk'),
  language: at('s15', 'language'),
  so: at('s15', 'So'),
  listens: at('s15', 'listens'),
  english: at('s15', 'English'),
  hindi: at('s15', 'Hindi'),
  telugu: at('s15', 'Telugu'),
  urdu: at('s15', 'Urdu'),
  kannada: at('s15', 'Kannada'),
  even: at('s15', 'even'),
  mixed: at('s15', 'mixed'),
  sentence: at('s15', 'sentence'),
};

type Who = 'patient' | 'son' | 'daughter';
const SPK: Record<Who, [number, number]> = {patient: [520, 440], son: [830, 440], daughter: [1130, 400]};

const LANGS: {k: string; text: string; t: number; who: Who; cx: number; cy: number; bg: string; size: number; font?: string}[] = [
  {k: 'English', text: 'Is she in pain?', t: T.english, who: 'daughter', cx: 1010, cy: 320, bg: P.indigo, size: 46},
  {k: 'Hindi', text: 'माँ घर जाना चाहती हैं', t: T.hindi, who: 'patient', cx: 400, cy: 320, bg: P.marigoldDeep, size: 44},
  {k: 'Telugu', text: 'అమ్మకు నొప్పిగా ఉంది', t: T.telugu, who: 'son', cx: 690, cy: 150, bg: P.rose, size: 40},
  {k: 'Urdu', text: 'امی کو آرام چاہیے', t: T.urdu, who: 'patient', cx: 240, cy: 150, bg: P.plum, size: 38, font: "'NotoUrdu'"},
  {k: 'Kannada', text: 'ಅಮ್ಮ ಮನೆಗೆ ಹೋಗಬೇಕು', t: T.kannada, who: 'daughter', cx: 1135, cy: 150, bg: P.sage, size: 40},
];

const Bubble: React.FC<{cx: number; cy: number; s: number; bg: string; tailTo: [number, number]; children: React.ReactNode; op?: number}> = ({cx, cy, s, bg, tailTo, children, op = 1}) => {
  // tail drawn from bubble bottom toward the speaker (short)
  const dx = tailTo[0] - cx;
  const tx = Math.max(-120, Math.min(120, dx * 0.5));
  return (
    <div style={{position: 'absolute', left: cx, top: cy, transform: `translate(-50%,-50%) scale(${s})`, transformOrigin: `${50 + (tx / 4)}% 120%`, opacity: op}}>
      <svg style={{position: 'absolute', left: '50%', top: '100%', overflow: 'visible'}} width={1} height={1}>
        <path d={`M${tx * 0.3 - 22} -6 L${tx * 0.3 + 22} -6 L${tx} 46 Z`} fill={bg} />
      </svg>
      <div style={{position: 'relative', padding: '18px 34px 20px', borderRadius: 34, background: bg, whiteSpace: 'nowrap', boxShadow: '0 12px 26px rgba(60,40,20,0.14)'}}>{children}</div>
    </div>
  );
};

const Room: React.FC<{t: number}> = ({t}) => (
  <>
    <div style={{position: 'absolute', left: -800, top: -600, width: 3600, height: 1500, background: P.creamWarm}} />
    {/* wallpaper motif */}
    <svg style={{position: 'absolute', left: -800, top: -600, overflow: 'visible'}} width={3600} height={1240}>
      {Array.from({length: 30}).map((_, i) =>
        Array.from({length: 14}).map((__, j) => (
          <path key={i + '-' + j} d={`M${i * 120 + (j % 2) * 60} ${j * 90 + 20} l10 14 l-10 14 l-10 -14 Z`} fill="rgba(224,160,64,0.13)" />
        )),
      )}
    </svg>
    <div style={{position: 'absolute', left: -800, top: 640, width: 3600, height: 300, background: P.sand}} />
    <div style={{position: 'absolute', left: -800, top: 630, width: 3600, height: 14, background: '#E4CDAE'}} />
    {/* window, evening */}
    <svg style={{position: 'absolute', left: 1380, top: 120, overflow: 'visible'}} width={360} height={420}>
      <defs>
        <linearGradient id="fsky" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#F4C58A" />
          <stop offset="1" stopColor="#F7E2C4" />
        </linearGradient>
      </defs>
      <rect x={-16} y={-16} width={392} height={452} rx={12} fill="#E9D3B4" />
      <rect width={360} height={420} fill="url(#fsky)" />
      <circle cx={250} cy={270} r={46} fill="#FFF1D6" />
      <path d="M0 420 L0 330 L40 330 L40 290 L90 290 L90 340 L140 340 L140 260 L190 260 L190 350 L260 350 L260 310 L300 310 L300 420 Z" fill="#E6B585" />
      <rect x={174} width={12} height={420} fill="#E9D3B4" />
      <path d={`M-40 -30 L70 -30 Q${60 + Math.sin(t) * 5} 200 80 450 L-40 450 Z`} fill={P.rose} />
      <path d={`M400 -30 L290 -30 Q${300 - Math.sin(t) * 5} 200 280 450 L400 450 Z`} fill={P.rose} />
      <rect x={-60} y={-44} width={480} height={14} rx={7} fill={P.plum} />
    </svg>
    {/* marigold garland across the wall */}
    <svg style={{position: 'absolute', left: -300, top: 0, overflow: 'visible'}} width={10} height={10}>
      {[0, 1, 2, 3].map((k) => {
        const x0 = k * 420;
        const pts = Array.from({length: 13}).map((_, i) => {
          const u = i / 12;
          return [x0 + u * 420, 20 + Math.sin(u * Math.PI) * 70] as const;
        });
        return (
          <g key={k}>
            <path d={`M${x0} 20 Q${x0 + 210} 160 ${x0 + 420} 20`} stroke="#7A5A2E" strokeWidth={3} fill="none" />
            {pts.map(([x, y], i) => (
              <circle key={i} cx={x} cy={y} r={13} fill={i % 3 === 2 ? P.coral : P.marigold} />
            ))}
            <path d={`M${x0 + 210} 92 q-14 30 0 56 q14 -26 0 -56 Z`} fill="#5E8F4A" />
          </g>
        );
      })}
    </svg>
  </>
);

const Floor: React.FC = () => (
  <>
    <div style={{position: 'absolute', left: -800, top: 880, width: 3600, height: 600, background: '#D9B48C'}} />
    <div style={{position: 'absolute', left: -800, top: 880, width: 3600, height: 12, background: '#C79F76'}} />
    {Array.from({length: 26}).map((_, i) => (
      <div key={i} style={{position: 'absolute', left: -800 + i * 150, top: 892, width: 4, height: 400, background: 'rgba(110,70,40,0.1)', transform: 'skewX(-28deg)'}} />
    ))}
    {/* striped rug */}
    <svg style={{position: 'absolute', left: 200, top: 880, overflow: 'visible'}} width={1000} height={140}>
      <defs>
        <clipPath id="rugc">
          <ellipse cx={500} cy={64} rx={520} ry={62} />
        </clipPath>
      </defs>
      <g clipPath="url(#rugc)">
        <rect x={-40} y={0} width={1100} height={140} fill={P.rose} />
        {[0, 1, 2, 3, 4, 5, 6].map((i) => (
          <rect key={i} x={-40 + i * 160} y={0} width={60} height={140} fill={[P.marigold, P.plum, P.coral][i % 3]} opacity={0.75} />
        ))}
      </g>
    </svg>
  </>
);

const SofaBack: React.FC = () => (
  <svg style={{position: 'absolute', left: 290, top: 540, overflow: 'visible'}} width={760} height={340}>
    <rect x={20} y={0} width={720} height={260} rx={60} fill={P.marigold} />
    <rect x={460} y={0} width={280} height={260} rx={60} fill="rgba(120,70,20,0.12)" />
    <rect x={70} y={70} width={150} height={120} rx={36} fill={P.rose} transform="rotate(-8 145 130)" />
    <rect x={560} y={74} width={140} height={116} rx={34} fill={P.plum} transform="rotate(7 630 130)" />
  </svg>
);
const SofaFront: React.FC = () => (
  <svg style={{position: 'absolute', left: 290, top: 540, overflow: 'visible'}} width={760} height={340}>
    <rect x={30} y={226} width={700} height={84} rx={28} fill="#D2912F" />
    <rect x={-20} y={150} width={90} height={190} rx={40} fill="#E8AE55" />
    <rect x={690} y={150} width={90} height={190} rx={40} fill="#E8AE55" />
    <rect x={60} y={300} width={20} height={44} rx={6} fill={P.marigoldDeep} />
    <rect x={680} y={300} width={20} height={44} rx={6} fill={P.marigoldDeep} />
  </svg>
);

const Lamp: React.FC<{t: number}> = ({t}) => (
  <svg style={{position: 'absolute', left: 1290, top: 600, overflow: 'visible'}} width={10} height={10}>
    <ellipse cx={0} cy={60} rx={170} ry={120} fill={`rgba(242,179,94,${0.16 + 0.03 * Math.sin(t * 3)})`} />
    <rect x={-70} y={170} width={140} height={20} rx={8} fill={P.plum} />
    <rect x={-56} y={190} width={14} height={146} fill="#6E4468" />
    <rect x={42} y={190} width={14} height={146} fill="#6E4468" />
    <rect x={-8} y={90} width={16} height={84} fill={P.ink} />
    <path d="M-62 92 L-40 10 L40 10 L62 92 Z" fill={P.marigold} />
    <path d="M10 10 L40 10 L62 92 L22 92 Z" fill="rgba(120,70,20,0.15)" />
  </svg>
);

export const Scene: React.FC = () => {
  const t = useGT();
  const cam = camAt(
    [
      {t: START, x: 690, y: 520, z: 1.2},
      {t: T.so - 0.2, x: 720, y: 520, z: 1.14},
      {t: T.listens + 0.35, x: 960, y: 530, z: 1.0},
      {t: T.even - 0.1, x: 960, y: 530, z: 1.02},
      {t: T.mixed + 0.4, x: 900, y: 500, z: 1.07},
    ],
    t,
    0.003,
  );
  const talking = (who: Who) => LANGS.some((l) => l.who === who && t > l.t && t < l.t + 0.65) || (who === 'son' && t > T.even && t < T.sentence + 0.5) || (t > T.families && t < T.language + 0.3 && ((who === 'patient' && Math.sin(t * 3) > 0) || (who === 'daughter' && Math.sin(t * 3) < 0)));

  /* chatter bubbles before the languages */
  const chatter: {who: Who; t: number}[] = [
    {who: 'daughter', t: T.families},
    {who: 'patient', t: T.talk - 0.1},
    {who: 'son', t: T.talk + 0.25},
  ];
  const chatOut = ramp(t, T.so - 0.1, T.so + 0.2);

  const phIn = easeOutQuint(ramp(t, T.so - 0.1, T.listens + 0.4));
  const phX = 1560 + (1 - phIn) * 700;
  const phRot = 3 - 3 * phIn + Math.sin(t * 0.8) * 0.6;

  const mixIn = pop(t, T.even - 0.05, 12, 0.5);
  const others = (i: number) => 1 - easeInCubic(ramp(t, T.even - 0.25 + i * 0.03, T.even + 0.05 + i * 0.03));

  const irisIn = mv(t, START, START + 0.6);
  const irisR = 30 + irisIn * 1500;
  const [phSX, phSY] = toScreen(cam, phX, 520);
  const exit = easeInCubic(ramp(t, END - 0.42, END - 0.02));

  const credit = easeOutQuint(ramp(t, T.listens, T.listens + 0.6));

  const mixWords: {w: string; dev?: boolean}[] = [{w: 'Amma'}, {w: 'ko'}, {w: 'दर्द', dev: true}, {w: 'hai,'}, {w: 'but'}, {w: 'she'}, {w: 'wants'}, {w: 'घर', dev: true}];
  const mixSpan = T.sentence - 0.1 - T.even;

  return (
    <AbsoluteFill style={{background: P.creamWarm, overflow: 'hidden'}}>
      <World cam={cam} depth={0.85}>
        <Room t={t} />
      </World>
      <World cam={cam} depth={0.93}>
        <Motes t={t} n={30} x0={-300} w={2600} h={880} color="rgba(224,160,64,0.35)" seed={7} />
      </World>
      <World cam={cam}>
        <Floor />
        <Plant x={120} y={905} s={1.15} t={t} pot={P.plum} />
        <Lamp t={t} />
        <SofaBack />
        <Person {...CAST.patient} t={t} x={520} y={800} s={1.1} sitting look={0.4} expr={t > T.even ? 'happy' : 'neutral'} talk={talking('patient')} />
        <Person {...SON} t={t} x={830} y={800} s={1.1} sitting look={-0.4} flip expr="happy" talk={talking('son')} armR={t > T.even && t < T.sentence + 0.6 ? 50 : 6} elbowR={t > T.even && t < T.sentence + 0.6 ? 60 : 0} />
        <SofaFront />
        <Person {...CAST.daughter} t={t} x={1135} y={935} s={1.15} flip look={0.8} expr={t > T.kannada ? 'happy' : 'neutral'} talk={talking('daughter')} armR={talking('daughter') ? 34 : 10} elbowR={talking('daughter') ? 40 : 0} />

        {/* chatter */}
        {chatter.map((c, i) => {
          const s = pop(t, c.t, 13, 0.45) * (1 - chatOut);
          if (s <= 0.01) return null;
          const [sx, sy] = SPK[c.who];
          return (
            <Bubble key={'c' + i} cx={sx + (c.who === 'daughter' ? -40 : 0)} cy={sy - 110} s={s} bg={P.white} tailTo={[sx, sy]}>
              <div style={{display: 'flex', gap: 12, padding: '8px 4px'}}>
                {[0, 1, 2].map((k) => (
                  <div key={k} style={{width: 18, height: 18, borderRadius: '50%', background: P.inkSub, transform: `translateY(${Math.sin(t * 9 - k * 0.9) * 5}px)`, opacity: 0.6}} />
                ))}
              </div>
            </Bubble>
          );
        })}

        {/* five languages */}
        {LANGS.map((l, i) => {
          const s = pop(t, l.t, 13, 0.42) * others(i);
          if (s <= 0.01) return null;
          const sp = SPK[l.who];
          return (
            <Bubble key={l.k} cx={l.cx} cy={l.cy + Math.sin(t * 1.4 + i) * 4} s={s} bg={l.bg} tailTo={sp}>
              <div style={{position: 'absolute', left: 22, top: -20, padding: '3px 14px', borderRadius: 999, background: P.white, color: l.bg, fontFamily: FONT_BODY, fontWeight: 600, fontSize: 22, boxShadow: '0 4px 10px rgba(60,40,20,0.15)'}}>{l.k}</div>
              <div style={{fontFamily: l.font ?? FONT_BODY, fontWeight: 600, fontSize: l.size, color: P.white, lineHeight: l.font ? 1.9 : 1.35, paddingTop: 4, direction: l.font ? 'rtl' : 'ltr'}}>{l.text}</div>
            </Bubble>
          );
        })}

        {/* the mixed sentence */}
        {mixIn > 0.01 && (
          <Bubble cx={720} cy={230} s={mixIn} bg={P.sageDark} tailTo={[830, 440]}>
            <div style={{position: 'absolute', left: 22, top: -22, padding: '3px 16px', borderRadius: 999, background: P.white, color: P.sageDark, fontFamily: FONT_BODY, fontWeight: 600, fontSize: 22}}>Mixed, one sentence</div>
            <div style={{fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 58, color: P.cream, lineHeight: 1.3, paddingTop: 6}}>
              {mixWords.map((m, i) => {
                const w0 = T.even + (i / mixWords.length) * mixSpan;
                const p = easeOutQuint(ramp(t, w0, w0 + 0.32));
                return (
                  <span key={i} style={{display: 'inline-block', overflow: 'hidden', verticalAlign: 'top', marginRight: i < mixWords.length - 1 ? '0.26em' : 0, paddingBottom: '0.1em'}}>
                    <span style={{display: 'inline-block', transform: `translateY(${(1 - p) * 130}%)`, color: m.dev ? P.marigold : P.cream}}>{m.w}</span>
                  </span>
                );
              })}
            </div>
          </Bubble>
        )}

        {/* listening rings from the family to the phone */}
        {t > T.so && (
          <svg style={{position: 'absolute', left: phX - 200, top: 520, overflow: 'visible', opacity: phIn * (1 - ramp(t, T.even, T.even + 0.4) * 0.6)}} width={1} height={1}>
            {[0, 1, 2, 3].map((k) => {
              const ph = (t * 0.9 + k * 0.25) % 1;
              return <path key={k} d={`M${60 - ph * 110} ${-110 - ph * 70} Q${20 - ph * 160} 0 ${60 - ph * 110} ${110 + ph * 70}`} stroke={P.sage} strokeWidth={8} strokeLinecap="round" fill="none" opacity={(1 - ph) * 0.7} />;
            })}
          </svg>
        )}
        <PhoneSeq t={t} segs={[{clip: 6, at: T.so, clipFrom: 0, rate: 1.15}]} x={phX} y={520} h={800} rot={phRot} />
        <div style={{position: 'absolute', left: phX - 92, top: 62, transform: `rotate(${phRot}deg) scale(${pop(t, T.listens, 13, 0.45)})`}}>
          <div style={{display: 'flex', alignItems: 'center', gap: 12, padding: '8px 22px', borderRadius: 999, background: P.sage, fontFamily: FONT_HEAD, fontWeight: 600, fontSize: 30, color: P.cream, whiteSpace: 'nowrap'}}>
            <svg width={20} height={28} viewBox="0 0 20 28">
              <rect x={5} y={0} width={10} height={17} rx={5} fill={P.cream} />
              <path d="M1 12 Q1 22 10 22 Q19 22 19 12" stroke={P.cream} strokeWidth={3} fill="none" />
              <rect x={9} y={21} width={2} height={6} fill={P.cream} />
            </svg>
            Listening
          </div>
        </div>
        {/* coffee table with chai */}
        <svg style={{position: 'absolute', left: 520, top: 940, overflow: 'visible'}} width={10} height={10}>
          <rect x={0} y={0} width={420} height={30} rx={14} fill="#8A5A3C" />
          <rect x={30} y={30} width={22} height={90} fill="#6E4630" />
          <rect x={368} y={30} width={22} height={90} fill="#6E4630" />
          {[90, 170, 300].map((x, i) => (
            <g key={i}>
              <rect x={x} y={-38} width={40} height={40} rx={8} fill={[P.cream, P.roseLight, P.sageLight][i]} />
              <path d={`M${x + 12} -48 q6 -14 0 -26`} stroke="rgba(255,255,255,0.7)" strokeWidth={4} fill="none" opacity={0.5 + 0.5 * Math.sin(t * 2 + i)} />
            </g>
          ))}
        </svg>
      </World>
      <World cam={cam} depth={1.25}>
        <FgLeaves x={-160} y={1220} s={1.1} t={t} color="#2E604D" color2="#3E7A62" />
      </World>

      {/* Sarvam AI credit */}
      <div style={{position: 'absolute', left: 36, bottom: 34, opacity: credit, transform: `translateY(${(1 - credit) * 20}px)`, display: 'flex', alignItems: 'center', gap: 12, padding: '6px 10px 6px 20px', borderRadius: 999, background: 'rgba(255,255,255,0.88)'}}>
        <div style={{fontFamily: FONT_BODY, fontSize: 21, color: 'rgba(56,46,41,0.75)', whiteSpace: 'nowrap'}}>Indian-language speech by Sarvam AI</div>
        <div style={{width: 112, height: 34, overflow: 'hidden', position: 'relative', borderRadius: 8}}>
          <Img src={staticFile('brand/sarvam.jpeg')} style={{position: 'absolute', left: -22, top: -42, width: 160}} />
        </div>
      </div>

      {/* entry: cream opens as an iris */}
      {irisIn < 1 && <div style={{position: 'absolute', left: 760 - irisR, top: 520 - irisR, width: irisR * 2, height: irisR * 2, borderRadius: '50%', boxShadow: `0 0 0 3000px ${P.cream}`}} />}
      {/* exit: night navy grows from the phone */}
      {exit > 0 && <div style={{position: 'absolute', left: phSX - 2300 * exit, top: phSY - 2300 * exit, width: 4600 * exit, height: 4600 * exit, borderRadius: '50%', background: P.night0}} />}
    </AbsoluteFill>
  );
};
