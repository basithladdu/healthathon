'use client';
// The topic guide shown before and during the talk (twin of CareGocGuideUi.kt). A mode picker (Guide me / Free flow), then either one topic
// card with Decide / Try saying / answer chips and "Done · Next", or a section grid where the doctor taps a topic to start it and again to finish it.
// The page keeps the state and ticks the checklist from it. No AI is involved.
import { GOC_CHECKLIST_ROWS, gocChecklistTitle } from './goc-checklist';
import {
  GOC_ESSENTIAL_COUNT, GOC_GUIDE_TEXT, GOC_ROW_TITLES, GOC_SECTION_TITLES, gocEssentialsDone, gocGuideDiscussed, gocGuideNext, gocIsEssential, gocMmss, type GocGuideState,
} from './goc-guide';
import type { CareLang } from './care-i18n';

type T = (en: string, hi: string) => string;

export function GocRunModePicker({ t, guideMe, onChange }: { t: T; guideMe: boolean; onChange: (guideMe: boolean) => void }) {
  return <div className="goc-guide-mode" role="radiogroup" aria-label={t('How do you want to run it?', 'आप इसे कैसे चलाना चाहेंगे?')}>
    <h3>{t('How do you want to run it?', 'आप इसे कैसे चलाना चाहेंगे?')}</h3>
    <div className="goc-mode-cards">
      <button type="button" role="radio" aria-checked={guideMe} data-selected={guideMe || undefined} data-testid="goc-mode-guide" onClick={() => onChange(true)}>
        <strong>{t('Guide me', 'मुझे राह दिखाएँ')}</strong><span>{t('One topic at a time, with prompts', 'एक बार में एक विषय, सुझावों के साथ')}</span>
      </button>
      <button type="button" role="radio" aria-checked={!guideMe} data-selected={!guideMe || undefined} data-testid="goc-mode-free" onClick={() => onChange(false)}>
        <strong>{t('Free flow', 'अपने क्रम में')}</strong><span>{t('Any order; tap topics as you go', 'किसी भी क्रम में; बात करते हुए विषय छुएँ')}</span>
      </button>
    </div>
  </div>;
}

/** [cur]: the topic the Guide-me card shows. [seconds]: the recording time (-1 when nothing records). [warnTitle]: the out-of-order warning. */
export function GocTopicPanel({ t, lang, guideMe, state, cur, warnTitle, onTap, onDone, onAllTopics, onDismissWarning }: {
  t: T; lang: CareLang; guideMe: boolean; state: GocGuideState; cur: number; warnTitle: string | null;
  onTap: (index: number) => void; onDone: () => void; onAllTopics: () => void; onDismissWarning: () => void;
}) {
  const rows = GOC_CHECKLIST_ROWS;
  const discussed = gocGuideDiscussed(state);
  const hindi = t('en', 'hi') === 'hi';
  const title = (i: number) => gocChecklistTitle(rows[i], lang);
  if (guideMe) {
    const idx = Math.min(Math.max(cur, 0), rows.length - 1);
    const text = GOC_GUIDE_TEXT[idx];
    const next = gocGuideNext(new Set([...discussed, idx]), idx, rows.length);
    const pick = ([en, hi]: [string, string]) => (hindi ? hi : en);
    return <div className="goc-guide-card" data-testid="goc-guide-card">
      <div className="goc-guide-bar" aria-hidden="true">{rows.map((_, i) => <span key={i} data-state={i === idx ? 'now' : discussed.has(i) ? 'done' : undefined} />)}</div>
      <p className="goc-guide-count">{t('Topic', 'विषय')} {idx + 1}/{rows.length}</p>
      <div className="goc-guide-body">
        <div className="goc-guide-tags">
          <span className="goc-sec">{rows[idx].section}</span>
          {discussed.has(idx) ? <span className="goc-guide-done-tag">✓ {t('Discussed', 'बात हुई')} {gocMmss(state.stamps[idx] ?? -1)}</span> : null}
        </div>
        <h4>{title(idx)}</h4>
        <p><strong>{t('Decide:', 'तय करना है:')}</strong> {pick(text.decide)}</p>
        <p className="goc-guide-label">{t('TRY SAYING', 'ऐसे कह सकते हैं')}</p>
        {text.say.map((line) => <blockquote key={line[0]}>“{pick(line)}”</blockquote>)}
        <p className="goc-guide-label">{t("ANSWER YOU'LL CONFIRM LATER", 'जिसकी पुष्टि आप बाद में करेंगे')}</p>
        <ul className="goc-guide-chips">{text.answers.map((answer) => <li key={answer[0]}>{pick(answer)}</li>)}</ul>
      </div>
      <div className="goc-guide-actions">
        <button type="button" className="goc-guide-all" data-testid="goc-guide-all" onClick={onAllTopics}>{t('All topics', 'सभी विषय')}</button>
        <button type="button" className="ep-btn goc-guide-donebtn" data-testid="goc-guide-done" onClick={onDone}>
          {next === null ? t('Done', 'हो गया') : `${t('Done', 'हो गया')} · ${t('Next', 'अगला')}: ${title(next)}`}
        </button>
      </div>
    </div>;
  }
  const essentials = gocEssentialsDone(discussed, GOC_ROW_TITLES);
  return <div className="goc-topic-grid" data-testid="goc-topic-grid">
    <div><h3>{t('Topics', 'विषय')}</h3><p className="goc-muted">{t('Tap a topic when you start it, and again when you finish', 'विषय शुरू करते समय उसे छुएँ, और ख़त्म होने पर फिर से')}</p></div>
    {warnTitle ? <div className="goc-guide-warning" role="status" data-testid="goc-guide-warning">
      <span>{warnTitle} {t('started before What the patient knows. Consider covering it first.', '“मरीज़ क्या जानते हैं” से पहले शुरू हुआ। इसे पहले करने पर विचार करें।')}</span>
      <button type="button" className="goc-link" onClick={onDismissWarning}>{t('Dismiss', 'बंद करें')}</button>
    </div> : null}
    {(['I', 'II', 'III'] as const).map((section) => <div key={section} className="goc-topic-section">
      <p className="goc-guide-label">{t(...GOC_SECTION_TITLES[section])}</p>
      <div className="goc-topic-tiles">{rows.map((row, i) => row.section !== section ? null : (() => {
        const now = i === state.now; const done = discussed.has(i); const start = state.stamps[i] ?? -1; const essential = gocIsEssential(row.en);
        return <button key={row.id} type="button" className="goc-topic-tile" data-state={done ? 'done' : now ? 'now' : undefined} data-testid={`goc-topic-${i}`} onClick={() => onTap(i)}>
          <strong>{title(i)}</strong>
          <small>{done ? `✓ ${t('Discussed', 'बात हुई')} ${gocMmss(start)}` : now ? `${t('Now discussing', 'अभी बात हो रही है')} · ${gocMmss(start)}` : essential ? `${t('Not yet', 'अभी नहीं')} · ${t('Essential', 'ज़रूरी')}` : t('Not yet', 'अभी नहीं')}</small>
        </button>;
      })())}</div>
    </div>)}
    <div className="goc-essentials" data-testid="goc-guide-essentials">
      <strong>{t('Essentials', 'ज़रूरी विषय')} {essentials}/{GOC_ESSENTIAL_COUNT}</strong>
      <div className="ep-progress-track" role="progressbar" aria-valuemin={0} aria-valuemax={GOC_ESSENTIAL_COUNT} aria-valuenow={essentials}><div className="ep-progress-fill" style={{ width: `${(essentials / GOC_ESSENTIAL_COUNT) * 100}%` }} /></div>
    </div>
  </div>;
}
