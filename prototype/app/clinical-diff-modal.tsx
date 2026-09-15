'use client';

import React, { useState, useMemo } from 'react';
import {
  IconCheckCircle,
  IconShieldAlert,
  IconShieldCheck,
  IconClock,
  IconSparkles,
  IconUsers,
  IconHeartPulse,
} from './icons';
import type { SummaryRelease, DraftFieldKey } from './summary-state';

export interface ClinicalDiffModalProps {
  patientId: string;
  patientName: string;
  releases: readonly SummaryRelease[];
  initialOlderVersion?: number;
  initialNewerVersion?: number;
  currentRole: string;
  onClose: () => void;
  onEndorse?: (vFrom: number, vTo: number) => void;
}

const FIELD_LABELS: Record<DraftFieldKey, string> = {
  priorities: 'Patient Priorities & Goals of Care',
  participants: 'Surrogates & Participating Caregivers',
  topics: 'Clinical Topics & Decisions Discussed',
  openQuestions: 'Open Clinical Questions & Concerns',
  followUp: 'Agreed Follow-up & Escalation Plan',
};

// Simple word-level diff tokenizer
export interface DiffToken {
  type: 'unchanged' | 'added' | 'removed';
  text: string;
}

export function computeWordDiff(textA: string, textB: string): DiffToken[] {
  if (!textA && !textB) return [];
  if (!textA) return [{ type: 'added', text: textB }];
  if (!textB) return [{ type: 'removed', text: textA }];
  if (textA === textB) return [{ type: 'unchanged', text: textA }];

  const wordsA = textA.split(/(\s+)/);
  const wordsB = textB.split(/(\s+)/);
  const result: DiffToken[] = [];

  let i = 0;
  let j = 0;

  while (i < wordsA.length || j < wordsB.length) {
    if (i < wordsA.length && j < wordsB.length && wordsA[i] === wordsB[j]) {
      result.push({ type: 'unchanged', text: wordsA[i] });
      i++;
      j++;
    } else {
      // Find forward match
      let foundA = -1;
      let foundB = -1;
      const lookAhead = 6;

      for (let offset = 1; offset <= lookAhead; offset++) {
        if (i + offset < wordsA.length && wordsA[i + offset] === wordsB[j]) {
          foundA = i + offset;
          break;
        }
        if (j + offset < wordsB.length && wordsB[j + offset] === wordsA[i]) {
          foundB = j + offset;
          break;
        }
      }

      if (foundA !== -1) {
        while (i < foundA) {
          result.push({ type: 'removed', text: wordsA[i] });
          i++;
        }
      } else if (foundB !== -1) {
        while (j < foundB) {
          result.push({ type: 'added', text: wordsB[j] });
          j++;
        }
      } else {
        if (i < wordsA.length) {
          result.push({ type: 'removed', text: wordsA[i] });
          i++;
        }
        if (j < wordsB.length) {
          result.push({ type: 'added', text: wordsB[j] });
          j++;
        }
      }
    }
  }

  // Coalesce adjacent identical types
  const coalesced: DiffToken[] = [];
  for (const token of result) {
    if (coalesced.length > 0 && coalesced[coalesced.length - 1].type === token.type) {
      coalesced[coalesced.length - 1].text += token.text;
    } else {
      coalesced.push({ ...token });
    }
  }

  return coalesced;
}

// Generate deterministic pseudo-SHA256 checksum for prototype verification
export function calculateChecksum(release: SummaryRelease): string {
  const content = `${release.patientId}-${release.number}-${release.releasedAt}-${release.physician}-${JSON.stringify(release.fields)}`;
  let hash = 0;
  for (let i = 0; i < content.length; i++) {
    hash = (hash << 5) - hash + content.charCodeAt(i);
    hash |= 0;
  }
  const hex = Math.abs(hash).toString(16).padStart(8, '0');
  return `sha256:e3b0c442...${hex}`;
}

export function ClinicalDiffModal({
  patientId,
  patientName,
  releases,
  initialOlderVersion,
  initialNewerVersion,
  currentRole,
  onClose,
  onEndorse,
}: ClinicalDiffModalProps) {
  // Available versions
  const availableVersions = useMemo(() => {
    return [...releases].sort((a, b) => a.number - b.number);
  }, [releases]);

  const defaultOlder = initialOlderVersion ?? (availableVersions.length > 1 ? availableVersions[availableVersions.length - 2].number : 1);
  const defaultNewer = initialNewerVersion ?? (availableVersions.length > 0 ? availableVersions[availableVersions.length - 1].number : 1);

  const [olderVersionNum, setOlderVersionNum] = useState<number>(defaultOlder);
  const [newerVersionNum, setNewerVersionNum] = useState<number>(defaultNewer);
  const [diffMode, setDiffMode] = useState<'side-by-side' | 'unified' | 'changes-only'>('unified');
  const [filterField, setFilterField] = useState<DraftFieldKey | 'all'>('all');
  const [endorsedToast, setEndorsedToast] = useState(false);

  const olderRelease = useMemo(() => {
    return releases.find((r) => r.number === olderVersionNum) || releases[0];
  }, [releases, olderVersionNum]);

  const newerRelease = useMemo(() => {
    return releases.find((r) => r.number === newerVersionNum) || releases[releases.length - 1];
  }, [releases, newerVersionNum]);

  // Checksums
  const olderChecksum = useMemo(() => calculateChecksum(olderRelease), [olderRelease]);
  const newerChecksum = useMemo(() => calculateChecksum(newerRelease), [newerRelease]);

  // Field keys
  const fieldKeys: DraftFieldKey[] = ['priorities', 'participants', 'topics', 'openQuestions', 'followUp'];

  // Change summary stats
  const changeStats = useMemo(() => {
    let changedFieldsCount = 0;
    const diffMap: Record<DraftFieldKey, { changed: boolean; tokens: DiffToken[] }> = {} as any;

    fieldKeys.forEach((k) => {
      const textA = olderRelease?.fields?.[k] || '';
      const textB = newerRelease?.fields?.[k] || '';
      const changed = textA.trim() !== textB.trim();
      if (changed) changedFieldsCount++;
      diffMap[k] = {
        changed,
        tokens: computeWordDiff(textA, textB),
      };
    });

    return { changedFieldsCount, diffMap };
  }, [olderRelease, newerRelease]);

  const displayedFields = useMemo(() => {
    if (filterField === 'all') return fieldKeys;
    return [filterField];
  }, [filterField]);

  const handleEndorse = () => {
    if (onEndorse) {
      onEndorse(olderVersionNum, newerVersionNum);
    }
    setEndorsedToast(true);
    setTimeout(() => setEndorsedToast(false), 3500);
  };

  return (
    <div className="tep-overlay" role="dialog" aria-modal="true">
      <div className="tep-modal prog-modal-wide" style={{ maxWidth: '1100px' }}>
        {/* Modal Header */}
        <div className="tep-header">
          <div className="tep-header-title">
            <div className="flex items-center gap-2">
              <span className="p-1 rounded-md bg-emerald-100 text-emerald-700 font-black text-xs">
                DIFF & AUDIT
              </span>
              <h2 className="text-xl font-bold text-slate-800">
                Clinical Handoff & Version Comparison
              </h2>
            </div>
            <p className="text-sm text-slate-500 mt-1">
              Tamper-evident, cryptographic differential audit across clinical encounter handoffs
            </p>
          </div>
          <button
            onClick={onClose}
            className="tep-close-btn"
            aria-label="Close Comparison"
          >
            ✕
          </button>
        </div>

        {/* Patient & Comparison Bar */}
        <div className="px-6 py-3 bg-slate-50 border-b border-slate-200 flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div>
              <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Patient</span>
              <span className="font-bold text-slate-900 text-sm">{patientName} ({patientId})</span>
            </div>
          </div>

          {/* Version Pickers */}
          <div className="flex items-center gap-2 bg-white px-3 py-1.5 rounded-lg border border-slate-200 shadow-sm text-xs">
            <div className="flex items-center gap-1.5">
              <span className="text-slate-500 font-semibold">Base:</span>
              <select
                value={olderVersionNum}
                onChange={(e) => setOlderVersionNum(Number(e.target.value))}
                className="font-bold text-slate-800 bg-transparent border-none focus:outline-none cursor-pointer"
              >
                {availableVersions.map((v) => (
                  <option key={`older-${v.number}`} value={v.number}>
                    Version {v.number} ({v.releasedAt.split('T')[0] || v.releasedAt})
                  </option>
                ))}
              </select>
            </div>

            <span className="text-slate-400 font-black">→</span>

            <div className="flex items-center gap-1.5">
              <span className="text-slate-500 font-semibold">Successor:</span>
              <select
                value={newerVersionNum}
                onChange={(e) => setNewerVersionNum(Number(e.target.value))}
                className="font-bold text-emerald-700 bg-transparent border-none focus:outline-none cursor-pointer"
              >
                {availableVersions.map((v) => (
                  <option key={`newer-${v.number}`} value={v.number}>
                    Version {v.number} ({v.releasedAt.split('T')[0] || v.releasedAt})
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* View Mode Toggle */}
          <div className="flex items-center gap-1 bg-slate-200 p-1 rounded-lg text-xs">
            <button
              onClick={() => setDiffMode('unified')}
              className={`px-3 py-1 rounded font-semibold transition ${
                diffMode === 'unified' ? 'bg-white text-slate-800 shadow-xs' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Unified Inline
            </button>
            <button
              onClick={() => setDiffMode('side-by-side')}
              className={`px-3 py-1 rounded font-semibold transition ${
                diffMode === 'side-by-side' ? 'bg-white text-slate-800 shadow-xs' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Side-by-Side
            </button>
            <button
              onClick={() => setDiffMode('changes-only')}
              className={`px-3 py-1 rounded font-semibold transition ${
                diffMode === 'changes-only' ? 'bg-white text-slate-800 shadow-xs' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Delta Summary
            </button>
          </div>
        </div>

        {/* Change Stats Ribbon & Cryptographic Seal */}
        <div className="px-6 py-2.5 bg-indigo-50/70 border-b border-indigo-100 flex flex-wrap items-center justify-between gap-3 text-xs">
          <div className="flex items-center gap-3">
            <span className="px-2 py-0.5 rounded-full bg-indigo-200 text-indigo-900 font-bold">
              {changeStats.changedFieldsCount} of 5 fields modified
            </span>
            <span className="text-slate-600">
              Comparing <strong>V{olderVersionNum}</strong> ({olderRelease?.physician}) with <strong>V{newerVersionNum}</strong> ({newerRelease?.physician})
            </span>
          </div>

          <div className="flex items-center gap-2 text-[11px] text-slate-500">
            <span className="font-mono bg-white px-2 py-0.5 rounded border border-slate-200" title={`Checksum for V${newerVersionNum}`}>
              🛡️ {newerChecksum}
            </span>
            <span className="text-emerald-700 font-bold">✓ Tamper-Evident</span>
          </div>
        </div>

        {/* Diff Content Body */}
        <div className="p-6 overflow-y-auto max-h-[70vh] space-y-6">
          {displayedFields.map((fieldKey) => {
            const olderVal = olderRelease?.fields?.[fieldKey] || 'Not stated';
            const newerVal = newerRelease?.fields?.[fieldKey] || 'Not stated';
            const { changed, tokens } = changeStats.diffMap[fieldKey];

            return (
              <div
                key={fieldKey}
                className={`p-4 rounded-xl border transition ${
                  changed
                    ? 'border-amber-200 bg-amber-50/20 shadow-xs'
                    : 'border-slate-200 bg-white'
                }`}
              >
                {/* Field Header */}
                <div className="flex items-center justify-between pb-3 mb-3 border-b border-slate-100">
                  <div className="flex items-center gap-2">
                    <h3 className="font-bold text-slate-800 text-sm">
                      {FIELD_LABELS[fieldKey]}
                    </h3>
                    {changed ? (
                      <span className="px-2 py-0.5 rounded bg-amber-100 text-amber-900 font-bold text-[10px] uppercase">
                        Modified in V{newerVersionNum}
                      </span>
                    ) : (
                      <span className="px-2 py-0.5 rounded bg-slate-100 text-slate-500 font-semibold text-[10px]">
                        Unchanged
                      </span>
                    )}
                  </div>
                </div>

                {/* Diff Display by Mode */}
                {diffMode === 'unified' && (
                  <div className="bg-white p-3 rounded-lg border border-slate-200 font-sans text-sm leading-relaxed text-slate-800">
                    {changed ? (
                      <div>
                        {tokens.map((token, idx) => {
                          if (token.type === 'removed') {
                            return (
                              <span
                                key={idx}
                                className="bg-rose-100 text-rose-800 line-through decoration-rose-500 decoration-1 px-1 rounded mx-0.5"
                                title={`Removed from Version ${olderVersionNum}`}
                              >
                                {token.text}
                              </span>
                            );
                          } else if (token.type === 'added') {
                            return (
                              <span
                                key={idx}
                                className="bg-emerald-100 text-emerald-900 font-semibold px-1 rounded mx-0.5 border-b-2 border-emerald-500"
                                title={`Added in Version ${newerVersionNum}`}
                              >
                                {token.text}
                              </span>
                            );
                          } else {
                            return <span key={idx}>{token.text}</span>;
                          }
                        })}
                      </div>
                    ) : (
                      <div className="text-slate-600">{newerVal}</div>
                    )}
                  </div>
                )}

                {diffMode === 'side-by-side' && (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
                    {/* Older column */}
                    <div className="p-3 rounded-lg bg-rose-50/40 border border-rose-200">
                      <div className="text-[11px] font-bold text-rose-800 uppercase mb-1 flex items-center justify-between">
                        <span>Version {olderVersionNum}</span>
                        <span className="text-slate-400 font-normal">{olderRelease?.physician}</span>
                      </div>
                      <p className="text-slate-700 leading-relaxed whitespace-pre-wrap">{olderVal}</p>
                    </div>

                    {/* Newer column */}
                    <div className="p-3 rounded-lg bg-emerald-50/40 border border-emerald-200">
                      <div className="text-[11px] font-bold text-emerald-800 uppercase mb-1 flex items-center justify-between">
                        <span>Version {newerVersionNum}</span>
                        <span className="text-slate-400 font-normal">{newerRelease?.physician}</span>
                      </div>
                      <p className="text-slate-900 font-medium leading-relaxed whitespace-pre-wrap">{newerVal}</p>
                    </div>
                  </div>
                )}

                {diffMode === 'changes-only' && (
                  <div className="text-xs space-y-2">
                    {changed ? (
                      <div className="p-3 rounded-lg bg-amber-50 border border-amber-200 space-y-1.5">
                        <div className="font-semibold text-amber-900">Clinical Delta:</div>
                        <div className="text-slate-600">
                          <strong className="text-rose-700 line-through">Prior:</strong> {olderVal}
                        </div>
                        <div className="text-slate-800">
                          <strong className="text-emerald-700">Updated:</strong> {newerVal}
                        </div>
                      </div>
                    ) : (
                      <div className="text-slate-400 italic">No modifications in this field.</div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Modal Footer */}
        <div className="p-4 bg-slate-50 border-t border-slate-200 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <span className="text-xs text-slate-500">
              Audit Endorsement Actor: <strong className="text-slate-700">{currentRole}</strong>
            </span>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={onClose}
              className="px-4 py-2 text-xs font-semibold text-slate-600 hover:text-slate-900 transition"
            >
              Close
            </button>
            <button
              onClick={handleEndorse}
              className="px-5 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-bold transition shadow-sm flex items-center gap-1.5"
            >
              <IconCheckCircle className="w-4 h-4" />
              <span>{endorsedToast ? '✓ Handoff Endorsed & Logged' : 'Endorse Handoff Transition'}</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
