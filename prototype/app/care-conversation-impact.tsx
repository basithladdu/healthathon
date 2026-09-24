'use client';

import type { CareConversationRequest } from './care-conversation-request-state.ts';
import {
  careConversationImpactCsv,
  careConversationImpactEpisodes,
  summarizeCareConversationImpact,
  type CareConversationImpactEpisode,
} from './care-conversation-impact.ts';
import './care-conversation-impact.css';

export function CareConversationImpact({ episodes, requests, loading = false }: {
  episodes: CareConversationImpactEpisode[];
  requests: CareConversationRequest[];
  loading?: boolean;
}) {
  const activity = careConversationImpactEpisodes(episodes, requests);
  const summary = summarizeCareConversationImpact(activity);

  function downloadCsv() {
    const url = URL.createObjectURL(new Blob([careConversationImpactCsv(activity)], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'saathi-care-conversation-activity.csv';
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 0);
  }

  return <section className="care-impact-panel" aria-labelledby="care-impact-title">
    <header>
      <h2 id="care-impact-title">Conversation activity</h2>
      {activity.length > 0 && <button type="button" onClick={downloadCsv}>Download CSV</button>}
    </header>
    {loading ? <p role="status">Loading recorded conversation activity…</p> : activity.length === 0 ? <p>No conversation activity with a known starting point has been recorded here yet. Older notes without a recorded origin are not counted.</p> : <>
      <dl>
        <div><dt>Patient requests</dt><dd>{summary.patientRequests}</dd></div>
        <div><dt>Family requests</dt><dd>{summary.familyRequests}</dd></div>
        <div><dt>Doctor started</dt><dd>{summary.doctorStarted}</dd></div>
        <div><dt>Conversation records saved</dt><dd>{summary.completed}</dd></div>
        <div><dt>Care Notes documented</dt><dd>{summary.documented}</dd></div>
        <div><dt>Doctor signed</dt><dd>{summary.signed}</dd></div>
      </dl>
      <p>Each episode is counted once. A request, a saved conversation record, a Care Note and a doctor’s signature are separate steps. Counts include activity recorded in this browser only.</p>
    </>}
  </section>;
}
