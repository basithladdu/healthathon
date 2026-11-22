'use client';
import { useMemo } from 'react';
import s from './admin.module.css';
import { AdminLock, useAdminData } from './admin-access';
import { DIRECTORY_FACTS, SUPPORTED_LANGUAGES, type ImpactTile } from './impact-facts';

type Data = { tiles: ImpactTile[]; setup: string[]; generatedAt: string };
const fmt = (n: number) => n.toLocaleString('en-IN');
function pick(value: Record<string, unknown>): Data | null {
  return Array.isArray(value.tiles) && typeof value.generatedAt === 'string' ? { tiles: value.tiles as ImpactTile[], setup: Array.isArray(value.setup) ? value.setup.filter((m): m is string => typeof m === 'string') : [], generatedAt: value.generatedAt } : null;
}

export function ImpactAdmin() {
  const admin = useAdminData<Data>('/api/admin-impact', pick, 'Could not load the impact numbers.');
  const groups = useMemo(() => {
    const map = new Map<string, ImpactTile[]>();
    (admin.data?.tiles ?? []).forEach(t => map.set(t.group, [...(map.get(t.group) ?? []), t]));
    return [...map.entries()];
  }, [admin.data]);

  const header = <div className={s.toolbarTop}>
    <div><h1 className={s.title}>Impact</h1><p className={s.lead}>Live counts from the Sahara app’s database, plus fixed facts from the app itself. Counts include the team’s own test accounts unless noted, and only data people chose to sync to the cloud.</p></div>
    {admin.phase === 'ready' && <div className={s.actions}><button type="button" className={s.secondary} disabled={admin.busy} onClick={() => void admin.reload()}>{admin.busy ? 'Refreshing…' : 'Refresh'}</button></div>}
  </div>;
  const facts = <section className={s.summary} aria-labelledby="impact-facts">
    <h2 id="impact-facts" className={s.subtitle}>Built into the app</h2>
    <div className={s.stats}>
      <div className={s.stat}><strong>{fmt(DIRECTORY_FACTS.centres)}</strong><span>Palliative care centres in the app’s directory</span></div>
      <div className={s.stat}><strong>{fmt(DIRECTORY_FACTS.states)}</strong><span>States and union territories covered</span></div>
      <div className={s.stat}><strong>{fmt(DIRECTORY_FACTS.morphineListed)}</strong><span>Centres whose listing says oral morphine is available</span></div>
      <div className={s.stat}><strong>{SUPPORTED_LANGUAGES.length}</strong><span>Languages: {SUPPORTED_LANGUAGES.join(', ')}</span></div>
    </div>
    <p className={s.small}>Directory source: {DIRECTORY_FACTS.sources}.</p>
  </section>;

  if (admin.phase === 'locked') return <>{header}<AdminLock id="impact-password" password={admin.password} setPassword={v => { admin.setPassword(v); admin.setMessage(''); }} message={admin.message} busy={admin.busy} onOpen={() => void admin.load(admin.password)} />{facts}</>;
  if (admin.phase === 'loading') return <>{header}<p className={s.status} role="status">Counting…</p>{facts}</>;
  if (admin.phase === 'failed') return <>{header}<p className={s.notice} role="alert">{admin.message}</p><button type="button" className={s.secondary} disabled={admin.busy} onClick={() => void admin.reload()}>Try again</button>{facts}</>;

  return <>
    {header}
    {admin.message && <p className={s.notice} role="alert">{admin.message}</p>}
    {admin.data?.setup.map(m => <p key={m} className={`${s.notice} ${s.setupNote}`}>{m}</p>)}
    {groups.map(([group, tiles]) => <section key={group} className={s.summary} aria-labelledby={`impact-${group.replace(/\W+/g, '-')}`}>
      <h2 id={`impact-${group.replace(/\W+/g, '-')}`} className={s.subtitle}>{group}</h2>
      <div className={s.stats}>{tiles.map(t => <div key={t.key} className={`${s.stat} ${t.total === null ? s.statOff : ''}`}>
        <strong>{t.total === null ? 'Not available' : fmt(t.total)}</strong>
        <span>{t.label}</span>
        {t.total !== null && t.last30 !== null && <span className={s.last30}>{fmt(t.last30)} in the last 30 days</span>}
        {t.unavailable && <span className={s.small}>{t.unavailable}</span>}
        {t.note && t.total !== null && <span className={s.small}>{t.note}</span>}
      </div>)}</div>
    </section>)}
    {facts}
    {admin.data && <p className={s.count}>Counted {new Date(admin.data.generatedAt).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' })} IST.</p>}
  </>;
}
