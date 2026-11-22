/* Inserted shots: features and facts the narration names that the original scenes never showed.
   They run on FILM time (the recorded narration), cued with nat(). Real app recordings and screenshots only; the rest is drawn. */
import React from 'react';
import {Freeze, Img, OffthreadVideo, staticFile} from 'remotion';
import {Blob, FONT_BODY, FONT_HEAD, FPS, P, Rise, clamp, easeOutCubic, lerp, nat, pop, ramp, segAt, useFilmT} from './kit';
import {Face, ORDER} from './team';

type IP = {t: number; lt: number; dur: number};

/* ---------- shared pieces ---------- */
const Stage: React.FC<{lt: number; dark?: boolean; children: React.ReactNode}> = ({lt, dark, children}) => {
  const z = 1.035 - 0.035 * easeOutCubic(ramp(lt, 0, 0.6)) + 0.0035 * lt;
  return (
    <div style={{position: 'absolute', inset: 0, overflow: 'hidden', background: dark ? `linear-gradient(160deg, ${P.night1}, ${P.night0})` : `linear-gradient(160deg, ${P.cream}, ${P.creamWarm})`}}>
      <Blob x={260 + Math.sin(lt * 0.5) * 40} y={180} r={320} color={dark ? 'rgba(95,227,192,0.10)' : 'rgba(159,209,184,0.42)'} blur={90} />
      <Blob x={1660 + Math.cos(lt * 0.4) * 40} y={880} r={360} color={dark ? 'rgba(242,179,94,0.10)' : 'rgba(224,160,64,0.24)'} blur={100} />
      <Blob x={1500} y={120 + Math.sin(lt * 0.6) * 30} r={220} color={dark ? 'rgba(127,179,217,0.08)' : 'rgba(196,106,132,0.14)'} blur={90} />
      <div style={{position: 'absolute', inset: 0, transform: `scale(${z})`, opacity: ramp(lt, 0, 0.18)}}>{children}</div>
    </div>
  );
};

/** phone frame showing a real recording (clip + vt seconds) or a real screenshot */
const Phone: React.FC<{x: number; y: number; h?: number; rot?: number; clip?: number; vt?: number; still?: string}> = ({x, y, h = 800, rot = 0, clip, vt = 0, still}) => {
  const w = h * 0.47;
  const bez = h * 0.018;
  return (
    <div style={{position: 'absolute', left: x - w / 2, top: y - h / 2, width: w, height: h, transform: `rotate(${rot}deg)`, borderRadius: h * 0.07, background: '#1B1A1F', padding: bez, boxSizing: 'border-box', boxShadow: '0 40px 80px rgba(10,20,30,0.30), 0 8px 20px rgba(10,20,30,0.22)'}}>
      <div style={{width: '100%', height: '100%', borderRadius: h * 0.055, overflow: 'hidden', background: P.cream, position: 'relative'}}>
        {still ? (
          <Img src={staticFile(`stills/${still}`)} style={{width: '100%', height: '100%', objectFit: 'cover', objectPosition: 'top'}} />
        ) : (
          <Freeze frame={Math.round(Math.max(0, vt) * FPS)}>
            <OffthreadVideo src={staticFile(`cut/f${String(clip).padStart(2, '0')}.mp4`)} muted style={{width: '100%', height: '100%', objectFit: 'cover'}} />
          </Freeze>
        )}
      </div>
    </div>
  );
};
/** phone that springs up into place at t0 */
const PhoneIn: React.FC<{t: number; t0: number; x: number; y: number; h?: number; rot?: number; clip?: number; still?: string; rate?: number; from?: number; max?: number}> = ({t, t0, x, y, h, rot = 0, clip, still, rate = 1, from = 0, max = 9.5}) => {
  const p = pop(t, t0, 11, 0.6);
  if (p <= 0) return null;
  return <Phone x={x} y={y + (1 - p) * 500} h={h} rot={rot + Math.sin(t * 0.9) * 0.6} clip={clip} still={still} vt={Math.min(max, from + Math.max(0, t - t0 - 0.3) * rate)} />;
};

const Pop: React.FC<{t: number; t0: number; x: number; y: number; children: React.ReactNode}> = ({t, t0, x, y, children}) => {
  const s = pop(t, t0);
  if (s <= 0) return null;
  return <div style={{position: 'absolute', left: x, top: y, transform: `translate(-50%,-50%) scale(${s})`}}>{children}</div>;
};

const Title: React.FC<{t: number; t0: number; x: number; y: number; text: string; size?: number; color?: string; w?: number; align?: 'left' | 'center'; weight?: number}> = ({t, t0, x, y, text, size = 84, color = P.ink, w = 900, align = 'left', weight = 700}) => (
  <div style={{position: 'absolute', left: align === 'center' ? x - w / 2 : x, top: y, width: w}}>
    <Rise t={t} t0={t0} text={text} size={size} color={color} weight={weight} align={align} />
  </div>
);

/* icons: 64 box, stroke style */
const Ico: React.FC<{k: string; s?: number; c?: string}> = ({k, s = 64, c = P.white}) => {
  const st = {stroke: c, strokeWidth: 4.5, fill: 'none', strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const};
  const body: Record<string, React.ReactNode> = {
    calendar: (<><rect x={10} y={14} width={44} height={40} rx={7} {...st} /><path d="M10 26 H54 M22 8 V18 M42 8 V18" {...st} /><circle cx={32} cy={40} r={5} fill={c} /></>),
    clipboard: (<><rect x={14} y={12} width={36} height={44} rx={6} {...st} /><rect x={24} y={7} width={16} height={10} rx={4} fill={c} /><path d="M23 36 L30 43 L42 29" {...st} /></>),
    hospital: (<><path d="M10 56 V20 L32 8 L54 20 V56 Z" {...st} /><path d="M32 24 V40 M24 32 H40" {...st} /></>),
    code: (<><path d="M24 18 L8 32 L24 46 M40 18 L56 32 L40 46 M36 12 L28 52" {...st} /></>),
    cloud: (<><path d="M18 48 H46 A12 12 0 0 0 47 24 A16 16 0 0 0 17 28 A10 10 0 0 0 18 48 Z" {...st} /></>),
    scales: (<><path d="M32 10 V54 M20 54 H44 M12 18 H52" {...st} /><path d="M12 18 L5 36 A7.5 6 0 0 0 19 36 Z M52 18 L45 36 A7.5 6 0 0 0 59 36 Z" {...st} /></>),
    people: (<><circle cx={22} cy={22} r={8} {...st} /><circle cx={44} cy={24} r={7} {...st} /><path d="M6 52 A16 16 0 0 1 38 52 M34 50 A13 13 0 0 1 59 52" {...st} /></>),
    spark: (<><path d="M32 6 L37 26 L58 32 L37 38 L32 58 L27 38 L6 32 L27 26 Z" fill={c} /></>),
    check: (<><path d="M14 33 L27 46 L50 20" {...st} strokeWidth={7} /></>),
    video: (<><rect x={6} y={18} width={36} height={28} rx={6} {...st} /><path d="M42 28 L58 20 V44 L42 36 Z" {...st} /></>),
    doc: (<><path d="M16 8 H40 L50 18 V56 H16 Z" {...st} /><path d="M24 28 H42 M24 38 H42 M24 48 H36" {...st} /></>),
    heart: (<><path d="M32 54 C8 38 8 14 22 14 C28 14 32 20 32 20 C32 20 36 14 42 14 C56 14 56 38 32 54 Z" fill={c} /></>),
    key: (<><circle cx={22} cy={32} r={11} {...st} /><path d="M33 32 H58 M48 32 V42 M56 32 V39" {...st} /></>),
  };
  return (
    <svg width={s} height={s} viewBox="0 0 64 64" style={{display: 'block', overflow: 'visible', flexShrink: 0}}>
      {body[k]}
    </svg>
  );
};
const Badge: React.FC<{k: string; d?: number; bg?: string}> = ({k, d = 120, bg = P.sage}) => (
  <div style={{width: d, height: d, flexShrink: 0, borderRadius: d * 0.3, background: bg, display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: '0 12px 28px rgba(20,30,40,0.18)'}}>
    <Ico k={k} s={d * 0.56} />
  </div>
);
/** white card tile: icon, title, sub. centred at (x,y) */
const Tile: React.FC<{t: number; t0: number; x: number; y: number; w?: number; h?: number; k: string; bg?: string; title: string; sub?: string; children?: React.ReactNode}> = ({t, t0, x, y, w = 520, h = 440, k, bg, title, sub, children}) => (
  <Pop t={t} t0={t0} x={x} y={y + Math.sin(t * 1.3 + x) * 5}>
    <div style={{width: w, height: h, borderRadius: 44, background: P.white, boxShadow: '0 30px 70px rgba(30,40,50,0.16), 0 6px 16px rgba(30,40,50,0.10)', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 22, padding: '0 36px', boxSizing: 'border-box', textAlign: 'center', position: 'relative'}}>
      <Badge k={k} bg={bg} d={140} />
      <div style={{fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 56, lineHeight: 1.12, color: P.ink}}>{title}</div>
      {sub && <div style={{fontFamily: FONT_BODY, fontWeight: 500, fontSize: 37, lineHeight: 1.25, color: P.inkSub}}>{sub}</div>}
      {children}
    </div>
  </Pop>
);
const Chip: React.FC<{children: React.ReactNode; bg?: string; color?: string; size?: number}> = ({children, bg = P.sageLight, color = P.sageDark, size = 34}) => (
  <div style={{display: 'inline-flex', alignItems: 'center', gap: size * 0.35, padding: `${size * 0.34}px ${size * 0.75}px`, borderRadius: 999, background: bg, color, fontFamily: FONT_HEAD, fontWeight: 700, fontSize: size, whiteSpace: 'nowrap', boxShadow: '0 8px 20px rgba(20,30,40,0.10)'}}>{children}</div>
);

/* ---------- the shots ---------- */
const Evidence: React.FC<IP> = ({t, lt}) => {
  const t98 = nat('m04', 'ninety-eight');
  const tYes = nat('m04', 'Yes');
  const t2 = nat('m04', 'ninety-eight', 1);
  const n = Math.round(98 * easeOutCubic(ramp(t, t98 - 0.1, t98 + 1.0)));
  const bump = (t0: number, a: number) => (t > t0 ? a * Math.exp(-(t - t0) * 4.5) * Math.cos((t - t0) * 9) : 0);
  const sc = pop(t, t98 - 0.15, 12, 0.6) * (1 + bump(tYes, 0.05) + bump(t2, 0.16));
  const hot = t > t2 ? clamp(1 - (t - t2) / 1.6) : 0;
  const tDots = nat('m04', 'people');
  return (
    <Stage lt={lt} dark>
      <Title t={t} t0={nat('m04') - 0.1} x={150} y={96} text="In India" size={64} color={P.mint} />
      <div style={{position: 'absolute', left: 150, top: 150, fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 400, lineHeight: 1, color: `rgb(${Math.round(lerp(224, 255, hot))},${Math.round(lerp(160, 226, hot))},${Math.round(lerp(64, 160, hot))})`, transform: `scale(${sc})`, transformOrigin: '30% 60%', textShadow: `0 0 ${60 + hot * 80}px rgba(224,160,64,${0.25 + hot * 0.4})`, letterSpacing: -8, whiteSpace: 'nowrap'}}>
        {n}
        <span style={{fontSize: 220, marginLeft: 8}}>%</span>
      </div>
      {t > t2 && <div style={{position: 'absolute', left: 430, top: 370, width: 10, height: 10, borderRadius: '50%', boxShadow: `0 0 0 ${(t - t2) * 520}px rgba(242,179,94,${clamp(0.16 - (t - t2) * 0.12)})`}} />}
      <Title t={t} t0={nat('m04', 'of') - 0.1} x={1000} y={210} w={800} text="of people with advanced cancer" size={62} color={P.white} />
      <Title t={t} t0={nat('m04', "don't") - 0.1} x={1000} y={372} w={820} text="don't get the palliative care they need." size={62} color={P.lampWarm} />
      {/* 100 people: 98 go without */}
      <div style={{position: 'absolute', left: 150, top: 640}}>
        {Array.from({length: 100}).map((_, i) => {
          const c = i % 25;
          const r = Math.floor(i / 25);
          const a = pop(t, tDots + i * 0.022, 16, 0.6);
          const lit = i >= 98;
          const k = !lit && t - t2 > c * 0.012 ? 1 + 0.25 * Math.exp(-(t - t2 - c * 0.012) * 5) : 1;
          return <div key={i} style={{position: 'absolute', left: c * 65, top: r * 62, width: 40, height: 40, borderRadius: '50%', background: lit ? P.monitor : P.coral, opacity: lit ? 1 : 0.85, transform: `scale(${a * k})`, boxShadow: lit ? '0 0 18px rgba(95,227,192,0.7)' : undefined}} />;
        })}
      </div>
      <div style={{position: 'absolute', right: 110, top: 70, fontFamily: FONT_BODY, fontSize: 26, color: 'rgba(255,255,255,0.62)', opacity: ramp(t, t98 + 0.6, t98 + 1.2)}}>Source: Patil et al., BMJ Supportive &amp; Palliative Care, 2024</div>
    </Stage>
  );
};

const Intro: React.FC<IP> = ({t, lt}) => {
  const t0 = nat('m05', "It's");
  return (
    <Stage lt={lt}>
      <Pop t={t} t0={t0 - 0.25} x={330} y={330}>
        <div style={{width: 260, height: 260, borderRadius: 64, background: P.white, boxShadow: '0 24px 60px rgba(30,40,50,0.16)', display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
          <Img src={staticFile('brand/sahara-logo.png')} style={{width: 220, height: 220, objectFit: 'contain'}} />
        </div>
      </Pop>
      <Title t={t} t0={t0 - 0.2} x={500} y={250} text="Sahara" size={150} color={P.sageDark} weight={800} />
      <Title t={t} t0={nat('m05', 'app') - 0.1} x={200} y={520} w={1000} text="An app for people with cancer" size={70} />
      <Title t={t} t0={nat('m05', 'families') - 0.25} x={200} y={620} w={1050} text="and the families who look after them." size={70} color={P.sage} />
      <PhoneIn t={t} t0={t0 - 0.1} x={1520} y={500} h={800} rot={3} still="a00-welcome.png" />
    </Stage>
  );
};

const Sarvam: React.FC<IP> = ({t, lt}) => {
  const t0 = nat('m12a');
  const langs = ['Hindi', 'Telugu', 'Urdu', 'Kannada'];
  return (
    <Stage lt={lt}>
      <Title t={t} t0={t0 - 0.15} x={960} y={120} w={1500} align="center" text="For Indian languages" size={76} />
      <Pop t={t} t0={nat('m12a', 'Sarvam') - 0.35} x={960} y={470}>
        <div style={{display: 'flex', alignItems: 'center', gap: 44, padding: '40px 70px 40px 44px', borderRadius: 60, background: P.white, boxShadow: '0 30px 70px rgba(30,40,50,0.18)'}}>
          <Img src={staticFile('brand/sarvam.jpeg')} style={{width: 220, height: 220, borderRadius: 48, objectFit: 'cover'}} />
          <div>
            <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 130, color: P.ink, lineHeight: 1, whiteSpace: 'nowrap'}}>Sarvam AI</div>
            <div style={{fontFamily: FONT_BODY, fontWeight: 500, fontSize: 38, color: P.inkSub, marginTop: 14, whiteSpace: 'nowrap'}}>speech to text, built for Indian languages</div>
          </div>
        </div>
      </Pop>
      {langs.map((l, i) => (
        <Pop key={l} t={t} t0={t0 + 0.5 + i * 0.14} x={510 + i * 300} y={760 + Math.sin(t * 1.6 + i) * 6}>
          <Chip size={44} bg={[P.marigold, P.sage, P.rose, P.indigo][i]} color={P.white}>{l}</Chip>
        </Pop>
      ))}
    </Stage>
  );
};

const Guidelines: React.FC<IP> = ({t, lt}) => {
  const t0 = nat('m12b', 'taken');
  return (
    <Stage lt={lt}>
      <Pop t={t} t0={t0 - 0.2} x={400} y={470}>
        <div style={{width: 440, height: 560, borderRadius: 40, background: P.white, boxShadow: '0 30px 70px rgba(30,40,50,0.18)', transform: 'rotate(-4deg)', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center'}}>
          <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 250, lineHeight: 0.95, color: P.sage}}>13</div>
          <div style={{fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 60, color: P.ink}}>topics</div>
          <div style={{display: 'grid', gridTemplateColumns: 'repeat(7, 34px)', gap: 12, marginTop: 30}}>
            {Array.from({length: 13}).map((_, i) => (
              <div key={i} style={{width: 34, height: 34, borderRadius: 10, background: P.sage, transform: `scale(${pop(t, t0 + 0.3 + i * 0.07)})`, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
                <Ico k="check" s={24} />
              </div>
            ))}
          </div>
        </div>
      </Pop>
      <Title t={t} t0={t0 - 0.05} x={760} y={170} w={1050} text="taken from India's end-of-life care guidelines" size={72} />
      <Pop t={t} t0={nat('m12b', 'critical') - 0.3} x={1230} y={560}>
        <Chip size={40} bg={P.white} color={P.ink}><Badge k="hospital" d={64} bg={P.indigo} /> Indian Society of Critical Care Medicine</Chip>
      </Pop>
      <Pop t={t} t0={nat('m12b', 'palliative') - 0.3} x={1230} y={700}>
        <Chip size={40} bg={P.white} color={P.ink}><Badge k="heart" d={64} bg={P.rose} /> Indian Association of Palliative Care</Chip>
      </Pop>
    </Stage>
  );
};

const AiDoc: React.FC<IP> = ({t, lt}) => {
  const rows: [string, number][] = [
    ['CPR', nat('m13', 'CPR')],
    ['A ventilator', nat('m13', 'ventilator')],
    ['Intensive care', nat('m13', 'Intensive')],
    ['Where she wants to be cared for', nat('m13', 'where')],
  ];
  const t0 = nat('m13');
  const tc = nat('m13', "That's");
  return (
    <Stage lt={lt}>
      <PhoneIn t={t} t0={t0 - 0.2} x={400} y={500} h={800} rot={-3} clip={2} rate={0.72} />
      <Pop t={t} t0={nat('m13', 'goals') - 0.5} x={1230} y={490}>
        <div style={{width: 1040, height: 780, borderRadius: 40, background: P.white, boxShadow: '0 30px 70px rgba(30,40,50,0.18)', padding: '44px 56px', boxSizing: 'border-box', position: 'relative'}}>
          <div style={{display: 'flex', alignItems: 'center', gap: 20}}>
            <Chip bg={P.marigold} color={P.white} size={32}><Ico k="spark" s={30} /> Written by AI</Chip>
            <div style={{opacity: ramp(t, tc, tc + 0.3)}}><Chip size={32}>A clinical record of her wishes</Chip></div>
          </div>
          <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 68, color: P.ink, marginTop: 26}}>Goals of care document</div>
          <div style={{height: 4, background: P.sageLight, borderRadius: 2, margin: '22px 0 8px'}} />
          {rows.map(([label, tr], i) => {
            const a = pop(t, tr - 0.1, 13, 0.55);
            return (
              <div key={i} style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between', height: 112, borderBottom: i < 3 ? `2px solid ${P.well}` : undefined, opacity: clamp(a * 2), transform: `translateX(${(1 - a) * 60}px)`}}>
                <div style={{fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 48, color: P.ink}}>{label}</div>
                <div style={{transform: `scale(${pop(t, tr + 0.55)})`}}><Chip size={30} bg={P.sage} color={P.white}><Ico k="check" s={28} /> Recorded</Chip></div>
              </div>
            );
          })}
        </div>
      </Pop>
    </Stage>
  );
};

const Legal: React.FC<IP> = ({t, lt}) => {
  const t0 = nat('mLegal');
  return (
    <Stage lt={lt}>
      <Pop t={t} t0={t0 - 0.2} x={480} y={480}>
        <div style={{position: 'relative', width: 420, height: 540, borderRadius: 36, background: P.white, boxShadow: '0 30px 70px rgba(30,40,50,0.18)', transform: 'rotate(-5deg)', padding: 50, boxSizing: 'border-box'}}>
          {[0, 1, 2, 3, 4, 5].map((i) => (
            <div key={i} style={{height: 18, borderRadius: 9, background: i === 0 ? P.sage : P.well, width: `${[62, 100, 92, 100, 78, 55][i] * ramp(t, t0 + 0.2 + i * 0.1, t0 + 0.6 + i * 0.1)}%`, marginBottom: 34}} />
          ))}
          <div style={{position: 'absolute', right: -70, bottom: -50, transform: `rotate(5deg) scale(${pop(t, nat('mLegal', 'legal') - 0.1)})`}}>
            <Badge k="scales" d={210} bg={P.indigo} />
          </div>
        </div>
      </Pop>
      <Title t={t} t0={nat('mLegal', 'legal') - 0.25} x={880} y={270} text="Legal advice" size={140} weight={800} color={P.indigo} />
      <Title t={t} t0={nat('mLegal', 'wording') - 0.3} x={880} y={470} w={900} text="on the wording and the format of the document." size={62} />
    </Stage>
  );
};

const Thermo: React.FC<IP> = ({t, lt}) => {
  const t0 = nat('m16a');
  const lvl = 6 * easeOutCubic(ramp(t, t0 + 0.3, t0 + 1.7));
  const H = 520;
  return (
    <Stage lt={lt}>
      <Pop t={t} t0={t0 - 0.25} x={520} y={480}>
        <div style={{width: 420, height: 760, borderRadius: 44, background: P.white, boxShadow: '0 30px 70px rgba(30,40,50,0.18)', position: 'relative'}}>
          <svg width={420} height={760} viewBox="0 0 420 760">
            <defs>
              <linearGradient id="thg" x1="0" y1="1" x2="0" y2="0">
                <stop offset="0" stopColor="#6FBF8E" /><stop offset="0.5" stopColor={P.marigold} /><stop offset="1" stopColor="#D9534F" />
              </linearGradient>
              <clipPath id="thc"><rect x={0} y={60 + H * (1 - lvl / 10)} width={420} height={800} /></clipPath>
            </defs>
            <rect x={150} y={40} width={70} height={H + 60} rx={35} fill={P.well} />
            <circle cx={185} cy={H + 120} r={66} fill={P.well} />
            <g clipPath="url(#thc)">
              <rect x={162} y={52} width={46} height={H + 60} rx={23} fill="url(#thg)" />
            </g>
            <circle cx={185} cy={H + 120} r={52} fill="#6FBF8E" />
            {Array.from({length: 11}).map((_, i) => (
              <g key={i}>
                <rect x={236} y={60 + H * (1 - i / 10) - 2} width={i % 5 === 0 ? 34 : 20} height={4} rx={2} fill={P.inkSub} />
                <text x={286} y={60 + H * (1 - i / 10) + 11} fontFamily={FONT_HEAD} fontWeight={700} fontSize={32} fill={Math.round(lvl) === i ? P.ink : '#B3A89F'}>{i}</text>
              </g>
            ))}
          </svg>
        </div>
      </Pop>
      <Title t={t} t0={nat('m16a', 'distress') - 0.5} x={860} y={300} w={980} text="Distress thermometer" size={90} weight={800} color={P.sageDark} />
      <Title t={t} t0={nat('m16a', 'distress') - 0.2} x={860} y={440} w={900} text="How are you feeling today? 0 to 10." size={56} color={P.inkSub} weight={600} />
    </Stage>
  );
};

const Journal: React.FC<IP> = ({t, lt}) => {
  const t0 = nat('m17');
  const faces = ['#6FBF8E', '#A9CF7A', P.marigold, '#E8926A', '#D9665F'];
  const pick = 3;
  return (
    <Stage lt={lt}>
      <Pop t={t} t0={t0 - 0.25} x={560} y={480}>
        <div style={{width: 760, height: 700, borderRadius: 44, background: P.white, boxShadow: '0 30px 70px rgba(30,40,50,0.18)', padding: '50px 56px', boxSizing: 'border-box', transform: 'rotate(-2deg)'}}>
          <div style={{fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 62, color: P.plum}}>Mood journal</div>
          <div style={{display: 'flex', gap: 22, marginTop: 34}}>
            {faces.map((c, i) => {
              const sel = i === pick ? pop(t, t0 + 0.9) : 0;
              const m = [10, 5, 0, -5, -10][i];
              return (
                <div key={i} style={{width: 108, height: 108, borderRadius: '50%', background: c, transform: `scale(${pop(t, t0 + 0.15 + i * 0.07) * (1 + sel * 0.16)})`, boxShadow: sel ? `0 0 0 ${8 * sel}px ${P.plum}` : undefined}}>
                  <svg width={108} height={108} viewBox="-54 -54 108 108">
                    <circle cx={-18} cy={-10} r={6.5} fill="#3A2B26" /><circle cx={18} cy={-10} r={6.5} fill="#3A2B26" />
                    <path d={`M-20 ${18 - m / 3} Q0 ${18 + m * 1.6} 20 ${18 - m / 3}`} stroke="#3A2B26" strokeWidth={6} fill="none" strokeLinecap="round" />
                  </svg>
                </div>
              );
            })}
          </div>
          <svg width={650} height={300} style={{marginTop: 40}}>
            {[0, 1, 2].map((i) => {
              const d = `M4 ${40 + i * 90} ` + Array.from({length: 12}).map((_, k) => `q${22 + ((k * 7 + i * 3) % 9)} ${k % 2 ? 22 : -22} ${44 + ((k * 5 + i) % 7)} 0`).join(' ');
              const p = ramp(t, t0 + 1.1 + i * 0.5, t0 + 1.9 + i * 0.5);
              return <path key={i} d={d} stroke={P.inkSub} strokeWidth={6} fill="none" strokeLinecap="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - p * (i === 2 ? 0.6 : 1)} opacity={0.55} />;
            })}
          </svg>
        </div>
      </Pop>
      <Title t={t} t0={nat('m17', 'for') - 0.25} x={1040} y={330} w={780} text="for the days that are hard to put into words." size={78} />
    </Stage>
  );
};

const Visit: React.FC<IP> = ({t, lt}) => {
  const t0 = nat('m20');
  return (
    <Stage lt={lt}>
      <PhoneIn t={t} t0={t0 - 0.25} x={1400} y={500} h={800} rot={3} clip={13} rate={1.5} />
      <Pop t={t} t0={t0 - 0.1} x={270} y={300}><Badge k="calendar" d={170} bg={P.marigold} /></Pop>
      <Title t={t} t0={t0} x={185} y={430} w={1000} text="Next hospital visit" size={112} weight={800} color={P.sageDark} />
      <Title t={t} t0={nat('m20', 'scheduled') - 0.2} x={185} y={590} w={900} text="scheduled right in the app." size={66} color={P.inkSub} weight={600} />
    </Stage>
  );
};

const Family: React.FC<IP> = ({t, lt}) => {
  const t0 = nat('m21');
  const ts = nat('m21', 'so');
  return (
    <Stage lt={lt}>
      <Title t={t} t0={t0 - 0.1} x={960} y={44} w={1700} align="center" text="Family members get their own login" size={70} weight={800} color={P.sageDark} />
      <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
        <path d="M800 540 H1120" stroke={P.sage} strokeWidth={10} strokeDasharray="4 26" strokeLinecap="round" opacity={ramp(t, ts - 0.2, ts + 0.6)} />
      </svg>
      <PhoneIn t={t} t0={t0 - 0.2} x={640} y={545} h={700} rot={-2} still="f14-patient-today.png" />
      <PhoneIn t={t} t0={t0 + 0.15} x={1280} y={545} h={700} rot={2} still="f14-family-today.png" />
      <Pop t={t} t0={t0 + 0.3} x={270} y={540}><Chip size={46} bg={P.sage} color={P.white}><Ico k="key" s={44} /> Patient</Chip></Pop>
      <Pop t={t} t0={t0 + 0.6} x={1660} y={540}><Chip size={46} bg={P.rose} color={P.white}><Ico k="key" s={44} /> Family</Chip></Pop>
      <Pop t={t} t0={ts + 0.1} x={960} y={540}><Badge k="heart" d={110} bg={P.coral} /></Pop>
    </Stage>
  );
};

const Sos: React.FC<IP> = ({t, lt}) => {
  const t0 = nat('m22');
  const tq = nat('m22', 'And');
  const beat = 1 + 0.05 * Math.sin((t - t0) * 7);
  const chips: [string, string, number, string][] = [
    ['Her wishes', 'doc', nat('m22', 'wishes'), P.sage],
    ['Contacts', 'people', nat('m22', 'contacts'), P.indigo],
    ['Medicines', 'clipboard', nat('m22', 'medicines'), P.marigoldDeep],
  ];
  return (
    <Stage lt={lt}>
      {[0, 1, 2].map((i) => {
        const ph = ((t - t0) * 0.8 + i / 3) % 1;
        return t > t0 ? <div key={i} style={{position: 'absolute', left: 400 - 160 - ph * 170, top: 420 - 160 - ph * 170, width: 320 + ph * 340, height: 320 + ph * 340, borderRadius: '50%', border: '6px solid #D9534F', boxSizing: 'border-box', opacity: (1 - ph) * 0.45}} /> : null;
      })}
      <Pop t={t} t0={t0 - 0.2} x={400} y={420}>
        <div style={{width: 320, height: 320, borderRadius: '50%', background: 'radial-gradient(circle at 35% 30%, #F0786F, #C9372F)', boxShadow: '0 26px 60px rgba(201,55,47,0.45), inset 0 -12px 24px rgba(0,0,0,0.18)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT_HEAD, fontWeight: 800, fontSize: 112, color: P.white, letterSpacing: 4, transform: `scale(${beat})`}}>SOS</div>
      </Pop>
      <Title t={t} t0={t0 + 0.1} x={400} y={690} w={700} align="center" text="Emergency SOS button" size={56} weight={800} color="#B5332B" />
      <PhoneIn t={t} t0={tq - 0.3} x={1060} y={500} h={800} rot={2} clip={7} rate={1.1} />
      <Title t={t} t0={nat('m22', 'QR') - 0.2} x={1340} y={150} w={520} text="QR card" size={100} weight={800} color={P.sageDark} />
      {chips.map(([label, k, tc, bg], i) => (
        <Pop key={label} t={t} t0={tc - 0.1} x={1560} y={370 + i * 150 + Math.sin(t * 1.4 + i) * 5}>
          <div style={{width: 440, display: 'flex', alignItems: 'center', gap: 24, padding: '18px 26px', borderRadius: 32, background: P.white, boxShadow: '0 16px 36px rgba(30,40,50,0.14)', boxSizing: 'border-box'}}>
            <Badge k={k} d={84} bg={bg} />
            <div style={{fontFamily: FONT_HEAD, fontWeight: 700, fontSize: 48, color: P.ink}}>{label}</div>
          </div>
        </Pop>
      ))}
    </Stage>
  );
};

const Community: React.FC<IP> = ({t, lt}) => {
  const t0 = nat('m24');
  return (
    <Stage lt={lt}>
      <PhoneIn t={t} t0={t0 - 0.25} x={470} y={500} h={800} rot={-3} clip={15} rate={1.4} />
      <Pop t={t} t0={t0 - 0.05} x={960} y={280}><Badge k="people" d={160} bg={P.plum} /></Pop>
      <Title t={t} t0={nat('m24', 'community') - 0.3} x={880} y={400} w={960} text="Community" size={150} weight={800} color={P.plum} />
      <Title t={t} t0={nat('m24', 'so') - 0.2} x={880} y={600} w={920} text="so no family goes through this alone." size={68} />
    </Stage>
  );
};

const Work: React.FC<IP> = ({t, lt}) => {
  const a = nat('m30a');
  const b = nat('m30a', 'We', 1);
  const c = nat('m30a', 'And');
  return (
    <Stage lt={lt}>
      <Pop t={t} t0={a - 0.3} x={960} y={110}><Chip size={50} bg={P.sageDark} color={P.white}>Three months of work</Chip></Pop>
      <Tile t={t} t0={a - 0.1} x={360} y={530} h={520} k="video" bg={P.sage} title="A lot of meetings" sub="with our doctors">
        <div style={{position: 'relative', width: 380, height: 84}}>
          {ORDER.map((w, i) => <Face key={w} who={w} x={38 + i * 76} y={42} d={84} s={pop(t, a + 0.35 + i * 0.09)} />)}
        </div>
      </Tile>
      <Tile t={t} t0={b - 0.15} x={960} y={530} h={520} k="clipboard" bg={P.marigold} title="A mentor review" sub="to check our direction" />
      <Tile t={t} t0={c - 0.15} x={1560} y={530} h={520} k="hospital" bg={P.indigo} title="A survey" sub="of doctors and hospitals" />
    </Stage>
  );
};

const Commit: React.FC<IP> = ({t, lt}) => {
  const t0 = nat('m31', 'Each');
  const t1 = nat('m31', "It's");
  const N = 14;
  const labels = ['Add body map', 'Fix reminder time', 'Telugu voice', 'Family login', 'QR card'];
  return (
    <Stage lt={lt}>
      <Title t={t} t0={t0 - 0.15} x={960} y={130} w={1600} align="center" text="1 commit = 1 saved improvement" size={92} weight={800} color={P.sageDark} />
      <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
        <path d="M160 560 H1760" stroke={P.sage} strokeWidth={10} strokeLinecap="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - ramp(t, t0, t0 + 1.6)} />
      </svg>
      {Array.from({length: N}).map((_, i) => {
        const x = 200 + (i * 1520) / (N - 1);
        const s = pop(t, t0 + 0.15 + i * 0.11, 16, 0.5);
        return <div key={i} style={{position: 'absolute', left: x - 22, top: 538, width: 44, height: 44, borderRadius: '50%', background: P.white, border: `9px solid ${i % 3 === 0 ? P.marigold : P.sage}`, boxSizing: 'border-box', transform: `scale(${s})`}} />;
      })}
      {labels.map((l, i) => (
        <Pop key={l} t={t} t0={t1 - 0.2 + i * 0.22} x={330 + i * 320} y={i % 2 ? 700 : 420}>
          <Chip size={42} bg={P.white} color={P.ink}><Ico k="check" s={36} c={P.sage} /> {l}</Chip>
        </Pop>
      ))}
      <Pop t={t} t0={t1 + 0.9} x={960} y={840}><Chip size={54} bg={P.ink} color={P.white}>almost 600 of them</Chip></Pop>
    </Stage>
  );
};

const Stack: React.FC<IP> = ({t, lt}) => (
  <Stage lt={lt}>
    <Tile t={t} t0={nat('m32') - 0.1} x={600} y={480} w={620} h={540} k="code" bg={P.sage} title="Free and open-source tools" sub="no licence fees" />
    <Tile t={t} t0={nat('m32', 'runs') - 0.25} x={1320} y={480} w={620} h={540} k="cloud" bg={P.sky} title="Runs on a cloud server" sub="so families can reach it any time" />
  </Stage>
);

const MAP: Record<string, React.FC<IP>> = {evidence: Evidence, intro: Intro, sarvam: Sarvam, guidelines: Guidelines, aidoc: AiDoc, legal: Legal, thermo: Thermo, journal: Journal, visit: Visit, family: Family, sos: Sos, community: Community, work: Work, commit: Commit, stack: Stack};

export const Inserts: React.FC = () => {
  const t = useFilmT();
  const s = segAt(t) as any;
  if (!s.insert) return null;
  const C = MAP[s.insert];
  if (!C) throw new Error('no insert ' + s.insert);
  return <C t={t} lt={t - s.n0} dur={s.n1 - s.n0} />;
};
export const Overlays: React.FC = () => null;
