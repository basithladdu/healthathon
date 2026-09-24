'use client';

import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import {
  CARE_CIRCLE_PERMISSIONS, activeCareCircleMember, addCareCircleMember, setCareCirclePermission,
  setCareCircleMemberActive, canWriteCareCircleUpdate, addCareCircleUpdate, visibleCareCircleUpdates,
  acknowledgeCareCircleUpdate, careCircleUpdateText, type CareCircleState, type CareCircleRole,
  type CareCirclePermission, type CareCircleUpdate, type CareCircleAudit,
} from './care-circle-state';
import './care-circle.css';

export type CareCircleProps = {
  patientId: string; patientName: string; author: string; role: CareCircleRole; hindi: boolean;
  state: CareCircleState; onChange: (state: CareCircleState) => void;
};

function CircleIcon({ kind }: { kind: 'people' | 'check' | 'copy' | 'plus' | 'note' | 'lock' }) {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {kind === 'people' ? <><circle cx="9" cy="7" r="3" /><path d="M3 21v-3a6 6 0 0 1 12 0v3M17 4a3 3 0 0 1 0 6m2 11v-3a6 6 0 0 0-2-4.5" /></>
      : kind === 'check' ? <path d="m5 12 4 4L19 6" />
        : kind === 'copy' ? <><rect x="8" y="8" width="12" height="13" rx="2" /><path d="M16 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h3" /></>
          : kind === 'plus' ? <path d="M12 5v14M5 12h14" />
            : kind === 'lock' ? <><rect x="5" y="10" width="14" height="11" rx="2" /><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 5v2" /></>
              : <><path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9Z" /><path d="M14 3v6h6M8 13h8M8 17h5" /></>}
  </svg>;
}
function initials(name: string) { return name.trim().split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toLocaleUpperCase(); }
function sameName(a: string, b: string) { return a.trim().replace(/\s+/g, ' ').toLocaleLowerCase() === b.trim().replace(/\s+/g, ' ').toLocaleLowerCase(); }
function dateTime(value: string, hindi: boolean) {
  return new Date(value).toLocaleString(hindi ? 'hi-IN' : 'en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit', second: '2-digit' });
}

function CopyUpdateDialog({ text, hindi, onClose }: { text: string; hindi: boolean; onClose: () => void }) {
  const id = useId();
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const element = dialog.current;
    element?.showModal();
    return () => element?.close();
  }, []);
  return <dialog ref={dialog} className="care-circle-copy-fallback" aria-labelledby={`${id}-heading`} onCancel={(event) => { event.preventDefault(); onClose(); }}>
    <h3 id={`${id}-heading`}>{hindi ? 'यह अपडेट कॉपी करें' : 'Copy this update'}</h3><textarea readOnly value={text} rows={6} autoFocus onFocus={(event) => event.currentTarget.select()} aria-label={hindi ? 'अपडेट का टेक्स्ट' : 'Update text'} /><button type="button" onClick={onClose}>{hindi ? 'पूरा हुआ' : 'Done'}</button>
  </dialog>;
}

export function CareCircle(props: CareCircleProps) {
  return <CareCircleForPerson key={`${props.patientId}:${props.role}:${props.author}`} {...props} />;
}

function CareCircleForPerson({ patientId, patientName, author, role, hindi, state, onChange }: CareCircleProps) {
  const id = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const patientCanEdit = role === 'patient';
  const members = state.members.filter((member) => member.patientId === patientId);
  const activeMembers = members.filter((member) => member.status === 'active');
  const myMembership = role === 'family' ? activeCareCircleMember(state, patientId, author) : undefined;
  const updates = visibleCareCircleUpdates(state, patientId, author, role);
  const canWrite = canWriteCareCircleUpdate(state, patientId, author, role);
  const [text, setText] = useState('');
  const [patientAudience, setPatientAudience] = useState(role !== 'patient');
  const [teamAudience, setTeamAudience] = useState(false);
  const [memberAudience, setMemberAudience] = useState<string[]>(role === 'patient' ? activeMembers.map((member) => member.id) : []);
  const [addingPerson, setAddingPerson] = useState(false);
  const [name, setName] = useState('');
  const [relationship, setRelationship] = useState('');
  const [personError, setPersonError] = useState('');
  const [updateError, setUpdateError] = useState('');
  const [status, setStatus] = useState('');
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [copyFallback, setCopyFallback] = useState('');
  const selectedMembers = memberAudience.filter((memberId) => activeMembers.some((member) => member.id === memberId));
  const labels: Record<CareCirclePermission, string> = {
    careNote: t('Care Note', 'देखभाल का नोट'), calendar: t('Calendar & tasks', 'कैलेंडर और काम'),
    reports: t('Reports', 'रिपोर्ट'), costs: t('Costs', 'खर्च'),
  };
  const action = () => ({ patientId, actor: author, role, at: new Date().toISOString() });

  function saveUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = addCareCircleUpdate(state, { ...action(), id: `circle-update-${crypto.randomUUID()}`, text, audience: { patient: patientAudience, careTeam: teamAudience, memberIds: selectedMembers } });
    if (next === state) { setUpdateError(t('Add an update of up to 1,200 characters.', 'अधिकतम 1,200 अक्षरों में अपडेट लिखें।')); return; }
    onChange(next); setText(''); setUpdateError(''); setStatus(t('Update saved.', 'अपडेट सेव हो गया।'));
  }
  function addPerson(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = addCareCircleMember(state, { ...action(), id: `circle-person-${crypto.randomUUID()}`, name, relationship });
    if (next === state) {
      setPersonError(members.some((member) => sameName(member.name, name)) ? t('This person is already listed below.', 'यह व्यक्ति नीचे पहले से है।') : t('Add a different person’s name and relationship.', 'दूसरे व्यक्ति का नाम और रिश्ता लिखें।'));
      return;
    }
    onChange(next); setName(''); setRelationship(''); setPersonError(''); setAddingPerson(false); setStatus(t('Person added.', 'व्यक्ति जुड़ गया।'));
  }
  function changePermission(memberId: string, permission: CareCirclePermission, allowed: boolean) {
    const next = setCareCirclePermission(state, { ...action(), memberId, permission, allowed });
    if (next !== state) { onChange(next); setStatus(t('Access updated.', 'पहुँच बदल गई।')); }
  }
  function changeAccess(memberId: string, active: boolean) {
    const next = setCareCircleMemberActive(state, { ...action(), memberId, active });
    if (next !== state) { onChange(next); setStatus(active ? t('Access restored.', 'पहुँच वापस मिल गई।') : t('Access removed.', 'पहुँच हटा दी गई।')); }
  }
  async function copyUpdate(update: CareCircleUpdate) {
    const content = careCircleUpdateText(update, patientName);
    try { await navigator.clipboard.writeText(content); setCopiedId(update.id); setStatus(t('Update copied.', 'अपडेट कॉपी हो गया।')); }
    catch { setCopyFallback(content); }
  }
  function markRead(updateId: string) {
    const next = acknowledgeCareCircleUpdate(state, { ...action(), updateId });
    if (next !== state) { onChange(next); setStatus(t('Marked as read.', 'पढ़ा हुआ चिह्नित किया।')); }
  }
  function recipients(update: CareCircleUpdate) {
    const names = [update.audience.patient ? patientName : '', update.audience.careTeam ? t('Care team', 'देखभाल की टीम') : '',
      ...update.audience.memberIds.map((memberId) => members.find((member) => member.id === memberId)?.name ?? '')].filter(Boolean);
    return names.length ? names.join(', ') : t('Only you', 'सिर्फ़ आप');
  }
  function auditLabel(entry: CareCircleAudit) {
    if (entry.action === 'member-added') return t(`${entry.targetName} added`, `${entry.targetName} को जोड़ा`);
    if (entry.action === 'member-revoked') return t(`${entry.targetName}’s access removed`, `${entry.targetName} की पहुँच हटाई`);
    if (entry.action === 'member-restored') return t(`${entry.targetName}’s access restored`, `${entry.targetName} की पहुँच लौटाई`);
    if (entry.action === 'access-changed') return `${entry.targetName} · ${labels[entry.permission!]} ${entry.allowed ? t('allowed', 'अनुमति दी') : t('removed', 'हटाया')}`;
    return entry.action === 'update-added' ? t('Update saved', 'अपडेट सेव किया') : t('Update marked as read', 'अपडेट पढ़ा हुआ चिह्नित किया');
  }
  const audit = state.audit.filter((entry) => entry.patientId === patientId && (!entry.action.startsWith('update-') || updates.some((update) => update.id === entry.targetId))).slice().reverse();

  if (role === 'family' && !myMembership) return <section className="care-circle care-circle-paused"><span className="care-circle-hero-icon"><CircleIcon kind="lock" /></span><h2>{t('Your access is paused', 'आपकी पहुँच रुकी हुई है')}</h2><p>{t(`${patientName} can add you to their care circle.`, `${patientName} आपको अपने देखभाल समूह में जोड़ सकते हैं।`)}</p></section>;

  return <section className="care-circle" aria-labelledby={`${id}-heading`}>
    <header className="care-circle-heading"><div><span className="care-circle-hero-icon"><CircleIcon kind="people" /></span><div><h2 id={`${id}-heading`}>{t('Our care circle', 'हमारा देखभाल समूह')}</h2><span>{patientName} · {activeMembers.length} {t('people helping', 'मदद करने वाले')}</span></div></div>{patientCanEdit && <button type="button" className="care-circle-add" onClick={() => setAddingPerson((value) => !value)} aria-expanded={addingPerson}><CircleIcon kind="plus" />{t('Add person', 'व्यक्ति जोड़ें')}</button>}</header>
    {status && <p className="care-circle-status" role="status"><CircleIcon kind="check" />{status}</p>}
    <div className="care-circle-layout"><div className="care-circle-updates">
      {canWrite && <form className="care-circle-composer" onSubmit={saveUpdate}><label htmlFor={`${id}-update`}>{t('One update for your people', 'अपनों के लिए एक अपडेट')}</label><textarea id={`${id}-update`} value={text} onChange={(event) => { setText(event.target.value); setUpdateError(''); }} maxLength={1200} rows={3} placeholder={t('What should they know?', 'उन्हें क्या बताना है?')} required />
        <fieldset className="care-circle-audience"><legend>{t('Who can see it?', 'कौन देख सकता है?')}</legend>
          {role !== 'patient' && <button type="button" aria-pressed={patientAudience} onClick={() => setPatientAudience((value) => !value)}>{patientAudience && <CircleIcon kind="check" />}{patientName}</button>}
          {role !== 'doctor' && <button type="button" aria-pressed={teamAudience} onClick={() => setTeamAudience((value) => !value)}>{teamAudience && <CircleIcon kind="check" />}{t('Care team', 'देखभाल की टीम')}</button>}
          {activeMembers.filter((member) => !(role === 'family' && sameName(member.name, author))).map((member) => <button key={member.id} type="button" aria-pressed={selectedMembers.includes(member.id)} onClick={() => setMemberAudience((current) => current.includes(member.id) ? current.filter((value) => value !== member.id) : [...current, member.id])}>{selectedMembers.includes(member.id) && <CircleIcon kind="check" />}{member.name}</button>)}
        </fieldset>
        {updateError && <p className="care-circle-error" role="alert">{updateError}</p>}
        <div className="care-circle-composer-footer"><span>{!patientAudience && !teamAudience && !selectedMembers.length ? t('Only you', 'सिर्फ़ आप') : `${t('From', 'की ओर से')} ${author}`}</span><button type="submit" className="care-circle-save" disabled={!text.trim()}><CircleIcon kind="note" />{t('Save update', 'अपडेट सेव करें')}</button></div>
      </form>}
      <div className="care-circle-feed-heading"><h3>{t('Updates', 'अपडेट')}</h3><span>{updates.length}</span></div>
      {updates.length ? <div className="care-circle-feed">{updates.map((update) => {
        const isOwn = update.authorRole === role && sameName(update.author, author);
        const ownRead = update.reads.find((read) => read.role === role && sameName(read.actor, author));
        return <article className="care-circle-update" key={update.id}><header><span className="care-circle-avatar" data-role={update.authorRole}>{initials(update.author)}</span><div><h4>{update.author}</h4><time dateTime={update.createdAt} title={update.createdAt}>{dateTime(update.createdAt, hindi)}</time></div></header><p className="care-circle-update-text">{update.text}</p><div className="care-circle-shared"><CircleIcon kind="people" /><span>{recipients(update)}</span></div>
          <footer><button type="button" onClick={() => copyUpdate(update)}><CircleIcon kind="copy" />{copiedId === update.id ? t('Copied', 'कॉपी हो गया') : t('Copy update', 'अपडेट कॉपी करें')}</button>{!isOwn && <button type="button" disabled={Boolean(ownRead)} onClick={() => markRead(update.id)} className={ownRead ? 'is-read' : ''}><CircleIcon kind="check" />{ownRead ? t('Read', 'पढ़ लिया') : t('I’ve read this', 'मैंने पढ़ लिया')}</button>}</footer>
          {update.reads.length > 0 && <details className="care-circle-reads"><summary><CircleIcon kind="check" />{t(`Read by ${update.reads.length}`, `${update.reads.length} ने पढ़ा`)}</summary><ul>{update.reads.map((read) => <li key={`${read.role}:${read.actor}`}><span>{read.actor}</span><time dateTime={read.at} title={read.at}>{dateTime(read.at, hindi)}</time></li>)}</ul></details>}
        </article>;
      })}</div> : <div className="care-circle-empty"><span><CircleIcon kind="note" /></span><h3>{t('Your updates will be here', 'आपके अपडेट यहाँ दिखेंगे')}</h3></div>}
    </div>
    <aside className="care-circle-people" aria-labelledby={`${id}-people-heading`}><div className="care-circle-people-heading"><h3 id={`${id}-people-heading`}>{t('People & access', 'लोग और पहुँच')}</h3><CircleIcon kind="lock" /></div>
      {addingPerson && patientCanEdit && <form className="care-circle-person-form" onSubmit={addPerson}><label>{t('Name', 'नाम')}<input value={name} onChange={(event) => { setName(event.target.value); setPersonError(''); }} maxLength={80} autoComplete="off" required /></label><label>{t('Relationship', 'रिश्ता')}<input value={relationship} onChange={(event) => setRelationship(event.target.value)} maxLength={50} placeholder={t('Daughter, partner, friend…', 'बेटी, साथी, दोस्त…')} required /></label><div className="care-circle-starting-access">{t('Starts with', 'शुरुआती पहुँच')}: {labels.careNote}, {labels.calendar}, {labels.reports}</div>{personError && <p className="care-circle-error" role="alert">{personError}</p>}<div><button type="button" onClick={() => { setAddingPerson(false); setPersonError(''); }}>{t('Cancel', 'रद्द करें')}</button><button type="submit" className="care-circle-save">{t('Add person', 'व्यक्ति जोड़ें')}</button></div></form>}
      <div className="care-circle-person care-circle-patient"><div className="care-circle-person-title"><span className="care-circle-avatar" data-role="patient">{initials(patientName)}</span><div><h4>{patientName}</h4><span>{t('Patient', 'मरीज़')}{role === 'patient' ? ` · ${t('you', 'आप')}` : ''}</span></div></div><span className="care-circle-access-owner">{t('Chooses who has access', 'तय करते हैं कि किसे पहुँच मिले')}</span></div>
      {(patientCanEdit ? members : activeMembers).map((member) => <div key={member.id} className={`care-circle-person${member.status === 'revoked' ? ' is-revoked' : ''}`}><div className="care-circle-person-title"><span className="care-circle-avatar" data-role="family">{initials(member.name)}</span><div><h4>{member.name}</h4><span>{member.relationship}{role === 'family' && sameName(member.name, author) ? ` · ${t('you', 'आप')}` : ''}</span></div></div>
        {member.status === 'active' ? <div className="care-circle-permissions" role="group" aria-label={`${member.name}: ${t('access', 'पहुँच')}`}>{CARE_CIRCLE_PERMISSIONS.map((permission) => patientCanEdit ? <button key={permission} type="button" role="switch" aria-checked={member.permissions[permission]} onClick={() => changePermission(member.id, permission, !member.permissions[permission])}><span className="care-circle-toggle" /><span>{labels[permission]}</span></button> : <span key={permission} className={member.permissions[permission] ? 'is-allowed' : ''}>{member.permissions[permission] ? <CircleIcon kind="check" /> : <CircleIcon kind="lock" />}{labels[permission]}</span>)}</div> : <p className="care-circle-revoked-label">{t('Access removed', 'पहुँच हटाई गई')}</p>}
        {patientCanEdit && <button type="button" className="care-circle-revoke" onClick={() => changeAccess(member.id, member.status !== 'active')}>{member.status === 'active' ? t('Remove access', 'पहुँच हटाएँ') : t('Restore access', 'पहुँच लौटाएँ')}</button>}
      </div>)}
      {patientCanEdit && audit.length > 0 && <details className="care-circle-audit"><summary>{t('Activity', 'गतिविधि')} <span>{audit.length}</span></summary><ol>{audit.map((entry) => <li key={entry.id}><strong>{auditLabel(entry)}</strong><span>{entry.actor}</span><time dateTime={entry.at} title={entry.at}>{dateTime(entry.at, hindi)}</time></li>)}</ol></details>}
    </aside></div>
    {copyFallback && <CopyUpdateDialog text={copyFallback} hindi={hindi} onClose={() => setCopyFallback('')} />}
  </section>;
}
