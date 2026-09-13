'use client';

import { useMemo, useState } from 'react';

export type HistoryKind =
  | 'appointment'
  | 'call'
  | 'conversation'
  | 'draft'
  | 'version'
  | 'retrieval'
  | 'consent'
  | 'card'
  | 'review'
  | 'details'
  | 'enrolment'
  | 'other';

export type HistoryEvent = {
  id: string;
  sortKey: number;
  dateLabel: string;
  kind: HistoryKind;
  title: string;
  detail: string;
  actor: string;
  status?: string;
};

const KIND_LABELS: Record<HistoryKind, string> = {
  appointment: 'Appointments',
  call: 'Calls',
  conversation: 'Conversations',
  draft: 'Drafts',
  version: 'Care plan versions',
  retrieval: 'Record access',
  consent: 'Consent',
  card: 'Emergency card',
  review: 'Review requests',
  details: 'Details updates',
  enrolment: 'Enrolment',
  other: 'Other',
};

const KIND_ORDER: HistoryKind[] = [
  'appointment',
  'call',
  'conversation',
  'draft',
  'version',
  'retrieval',
  'consent',
  'card',
  'review',
  'details',
  'enrolment',
  'other',
];

const MONTH_NAMES = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
];

function monthHeading(sortKey: number): string {
  const d = new Date(sortKey);
  return `${MONTH_NAMES[d.getUTCMonth()]} ${d.getUTCFullYear()}`;
}

export function PatientHistory(props: {
  patientName: string;
  events: HistoryEvent[];
  currentPerson?: string;
}): React.JSX.Element {
  const { patientName, events, currentPerson } = props;

  const [kindFilter, setKindFilter] = useState<HistoryKind | 'all'>('all');
  const [personFilter, setPersonFilter] = useState<string>('all');
  const [seenByMe, setSeenByMe] = useState(false);

  const kindCounts = useMemo(() => {
    const counts = new Map<HistoryKind, number>();
    for (const e of events) {
      counts.set(e.kind, (counts.get(e.kind) ?? 0) + 1);
    }
    return counts;
  }, [events]);

  const presentKinds = useMemo(
    () => KIND_ORDER.filter((k) => (kindCounts.get(k) ?? 0) > 0),
    [kindCounts]
  );

  const actors = useMemo(() => {
    const set = new Set<string>();
    for (const e of events) set.add(e.actor);
    return Array.from(set).sort((a, b) => a.localeCompare(b));
  }, [events]);

  const currentPersonIsActor = !!currentPerson && actors.includes(currentPerson);

  const lastContact = useMemo(() => {
    let best: HistoryEvent | undefined;
    for (const e of events) {
      if (e.kind === 'appointment' || e.kind === 'call') {
        if (!best || e.sortKey > best.sortKey) best = e;
      }
    }
    return best;
  }, [events]);

  const effectivePersonFilter = seenByMe && currentPerson ? currentPerson : personFilter;

  const filtered = useMemo(() => {
    return events.filter((e) => {
      if (kindFilter !== 'all' && e.kind !== kindFilter) return false;
      if (effectivePersonFilter !== 'all' && e.actor !== effectivePersonFilter) return false;
      return true;
    });
  }, [events, kindFilter, effectivePersonFilter]);

  const sorted = useMemo(
    () => [...filtered].sort((a, b) => b.sortKey - a.sortKey),
    [filtered]
  );

  type Group = { heading: string; items: HistoryEvent[] };
  const groups: Group[] = useMemo(() => {
    const out: Group[] = [];
    for (const e of sorted) {
      const heading = monthHeading(e.sortKey);
      const last = out[out.length - 1];
      if (last && last.heading === heading) {
        last.items.push(e);
      } else {
        out.push({ heading, items: [e] });
      }
    }
    return out;
  }, [sorted]);

  const hasActiveFilter = kindFilter !== 'all' || effectivePersonFilter !== 'all';

  function clearFilters() {
    setKindFilter('all');
    setPersonFilter('all');
    setSeenByMe(false);
  }

  function onSelectPerson(value: string) {
    setSeenByMe(false);
    setPersonFilter(value);
  }

  function toggleSeenByMe() {
    if (seenByMe) {
      setSeenByMe(false);
      setPersonFilter('all');
    } else {
      setSeenByMe(true);
      setPersonFilter('all');
    }
  }

  return (
    <section className="ph-card">
      <div className="ph-wrap">
        <p className="ph-eyebrow">Encounters</p>
        <h2 className="ph-heading">Patient history</h2>
        <p className="ph-sub">
          Every appointment, call, conversation, version, record access, consent and emergency
          card for {patientName}, newest first.
        </p>

        <div className="ph-stats">
          <div className="ph-stat">
            <span className="ph-stat-value">{events.length} events</span>
          </div>
          <div className="ph-stat">
            <span className="ph-stat-value">
              {lastContact ? `Last contact ${lastContact.dateLabel}` : 'No contact recorded'}
            </span>
          </div>
          <div className="ph-stat">
            <span className="ph-stat-value">{actors.length} people involved</span>
          </div>
        </div>

        <div className="ph-filters">
          <div className="ph-chips" role="group" aria-label="Filter by kind">
            <button
              type="button"
              className="ph-chip"
              aria-pressed={kindFilter === 'all'}
              onClick={() => setKindFilter('all')}
            >
              All ({events.length})
            </button>
            {presentKinds.map((k) => (
              <button
                key={k}
                type="button"
                className="ph-chip"
                aria-pressed={kindFilter === k}
                onClick={() => setKindFilter(k)}
              >
                {KIND_LABELS[k]} ({kindCounts.get(k) ?? 0})
              </button>
            ))}
          </div>

          <div className="ph-filter-row">
            <label className="ph-select-label">
              Person
              <select
                className="ph-select"
                value={effectivePersonFilter}
                onChange={(e) => onSelectPerson(e.target.value)}
              >
                <option value="all">Everyone</option>
                {actors.map((a) => (
                  <option key={a} value={a}>
                    {a}
                  </option>
                ))}
              </select>
            </label>

            {currentPersonIsActor && (
              <button
                type="button"
                className="ph-chip ph-seen-by-me"
                aria-pressed={seenByMe}
                onClick={toggleSeenByMe}
              >
                Seen by me
              </button>
            )}

            {hasActiveFilter && (
              <button type="button" className="ph-clear" onClick={clearFilters}>
                Clear filters
              </button>
            )}
          </div>
        </div>

        {events.length === 0 ? (
          <p className="ph-empty">Nothing has been recorded for this patient yet.</p>
        ) : sorted.length === 0 ? (
          <p className="ph-empty">No events match these filters.</p>
        ) : (
          <ol className="ph-list">
            {groups.map((g) => (
              <li key={g.heading} className="ph-group">
                <h3 className="ph-month">{g.heading}</h3>
                <ol className="ph-rows">
                  {g.items.map((e) => (
                    <li key={e.id} className="ph-row">
                      <span className="ph-row-date">{e.dateLabel}</span>
                      <span className="ph-kind-chip">{KIND_LABELS[e.kind]}</span>
                      <span className="ph-row-main">
                        <span className="ph-row-title">{e.title}</span>
                        <span className="ph-row-detail">{e.detail}</span>
                        <span className="ph-row-actor">by {e.actor}</span>
                      </span>
                      {e.status && <span className="ph-status-pill">{e.status}</span>}
                    </li>
                  ))}
                </ol>
              </li>
            ))}
          </ol>
        )}
      </div>
    </section>
  );
}
