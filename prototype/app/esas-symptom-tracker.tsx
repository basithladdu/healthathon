'use client';

import React, { useState, useMemo } from 'react';
import {
  IconHeartPulse,
  IconLungs,
  IconShieldAlert,
  IconShieldCheck,
  IconCheckCircle,
  IconSparkles,
} from './icons';

export interface EsasDomain {
  id: string;
  label: string;
  hindiLabel: string;
  category: 'physical' | 'emotional';
  lowAnchor: string;
  highAnchor: string;
}

export const ESAS_DOMAINS: EsasDomain[] = [
  { id: 'pain', label: 'Pain', hindiLabel: 'दर्द (Pain)', category: 'physical', lowAnchor: 'No Pain', highAnchor: 'Worst Pain' },
  { id: 'tiredness', label: 'Tiredness (Fatigue)', hindiLabel: 'थकान (Fatigue)', category: 'physical', lowAnchor: 'No Tiredness', highAnchor: 'Worst Tiredness' },
  { id: 'drowsiness', label: 'Drowsiness', hindiLabel: 'उनींदापन (Drowsiness)', category: 'physical', lowAnchor: 'No Drowsiness', highAnchor: 'Worst Drowsiness' },
  { id: 'nausea', label: 'Nausea', hindiLabel: 'जी मिचलाना (Nausea)', category: 'physical', lowAnchor: 'No Nausea', highAnchor: 'Worst Nausea' },
  { id: 'appetite', label: 'Lack of Appetite', hindiLabel: 'भूख न लगना (Appetite)', category: 'physical', lowAnchor: 'Normal Appetite', highAnchor: 'Worst Appetite' },
  { id: 'shortness_of_breath', label: 'Shortness of Breath', hindiLabel: 'सांस फूलना (Dyspnea)', category: 'physical', lowAnchor: 'No Shortness of Breath', highAnchor: 'Worst Shortness of Breath' },
  { id: 'depression', label: 'Depression / Sadness', hindiLabel: 'उदासी (Sadness)', category: 'emotional', lowAnchor: 'Not Depressed', highAnchor: 'Worst Depression' },
  { id: 'anxiety', label: 'Anxiety / Nervousness', hindiLabel: 'घबराहट (Anxiety)', category: 'emotional', lowAnchor: 'Not Anxious', highAnchor: 'Worst Anxiety' },
  { id: 'wellbeing', label: 'Overall Wellbeing', hindiLabel: 'समग्र स्वास्थ्य (Wellbeing)', category: 'physical', lowAnchor: 'Best Feeling', highAnchor: 'Worst Feeling' },
];

export interface EsasTrackerProps {
  patientId: string;
  patientName: string;
  onClose?: () => void;
  onSave?: (scores: Record<string, number>, distressTotal: number) => void;
}

export function EsasSymptomTracker({
  patientId,
  patientName,
  onClose,
  onSave,
}: EsasTrackerProps) {
  const [scores, setScores] = useState<Record<string, number>>({
    pain: 6,
    tiredness: 7,
    drowsiness: 4,
    nausea: 2,
    appetite: 5,
    shortness_of_breath: 6,
    depression: 3,
    anxiety: 4,
    wellbeing: 6,
  });

  // Baseline comparison scores for spider chart
  const baselineScores: Record<string, number> = {
    pain: 8,
    tiredness: 8,
    drowsiness: 5,
    nausea: 6,
    appetite: 7,
    shortness_of_breath: 7,
    depression: 5,
    anxiety: 6,
    wellbeing: 8,
  };

  const [savedToast, setSavedToast] = useState(false);

  const totalScore = useMemo(() => {
    return Object.values(scores).reduce((sum, val) => sum + val, 0);
  }, [scores]);

  const baselineTotal = useMemo(() => {
    return Object.values(baselineScores).reduce((sum, val) => sum + val, 0);
  }, []);

  const severeSymptoms = useMemo(() => {
    return ESAS_DOMAINS.filter((d) => (scores[d.id] ?? 0) >= 7);
  }, [scores]);

  const handleScoreChange = (domainId: string, value: number) => {
    setScores((prev) => ({ ...prev, [domainId]: value }));
  };

  const handleSave = () => {
    if (onSave) {
      onSave(scores, totalScore);
    }
    setSavedToast(true);
    setTimeout(() => {
      setSavedToast(false);
      if (onClose) onClose();
    }, 1200);
  };

  // Generate SVG Radar Polygon Points
  const radarData = useMemo(() => {
    const size = 260;
    const center = size / 2;
    const radius = 95;
    const count = ESAS_DOMAINS.length;

    const currentPoints: string[] = [];
    const baselinePoints: string[] = [];
    const axisLines: Array<{ x: number; y: number; label: string }> = [];

    ESAS_DOMAINS.forEach((domain, index) => {
      const angle = (Math.PI * 2 * index) / count - Math.PI / 2;

      // Outer axis
      const axisX = center + radius * Math.cos(angle);
      const axisY = center + radius * Math.sin(angle);
      axisLines.push({ x: axisX, y: axisY, label: domain.label });

      // Current score point (0 to 10 normalized)
      const currentR = radius * ((scores[domain.id] ?? 0) / 10);
      const curX = center + currentR * Math.cos(angle);
      const curY = center + currentR * Math.sin(angle);
      currentPoints.push(`${curX.toFixed(1)},${curY.toFixed(1)}`);

      // Baseline score point
      const baseR = radius * ((baselineScores[domain.id] ?? 0) / 10);
      const baseX = center + baseR * Math.cos(angle);
      const baseY = center + baseR * Math.sin(angle);
      baselinePoints.push(`${baseX.toFixed(1)},${baseY.toFixed(1)}`);
    });

    return {
      size,
      center,
      currentPoints: currentPoints.join(' '),
      baselinePoints: baselinePoints.join(' '),
      axisLines,
    };
  }, [scores]);

  return (
    <div className="esas-container">
      {/* Header */}
      <div className="esas-header">
        <div className="esas-title-group">
          <div className="flex items-center gap-2">
            <span className="esas-kicker">Clinical Triage &amp; Outcomes</span>
            <span className="esas-badge">Edmonton Symptom Assessment System (ESAS-r)</span>
          </div>
          <h2 className="esas-title">Palliative Symptom Tracker &amp; Visual Radar</h2>
          <p className="esas-subtitle">
            Quantitative physical and emotional symptom burden monitoring for <strong>{patientName}</strong> ({patientId}).
          </p>
        </div>
        {onClose && (
          <button type="button" className="close-btn" onClick={onClose} aria-label="Close Tracker">
            ✕
          </button>
        )}
      </div>

      <div className="esas-body">
        {/* Radar Chart & Summary Card */}
        <div className="esas-radar-summary-grid">
          {/* Visual SVG Radar Spider Chart */}
          <div className="esas-radar-card">
            <div className="esas-card-head">
              <span className="text-xs font-bold text-slate-700 uppercase tracking-wide">
                🕸️ Multi-Axis Symptom Radar
              </span>
              <div className="flex items-center gap-3 text-xs">
                <span className="flex items-center gap-1.5 font-semibold text-rose-700">
                  <span className="w-2.5 h-2.5 rounded-full bg-rose-500 inline-block" /> Current
                </span>
                <span className="flex items-center gap-1.5 font-semibold text-slate-400">
                  <span className="w-2.5 h-2.5 rounded-full bg-slate-300 inline-block" /> Discharge Baseline
                </span>
              </div>
            </div>

            <div className="esas-svg-wrap">
              <svg width={radarData.size} height={radarData.size} className="esas-radar-svg">
                {/* Background concentric circles */}
                {[0.25, 0.5, 0.75, 1.0].map((ratio) => (
                  <circle
                    key={ratio}
                    cx={radarData.center}
                    cy={radarData.center}
                    r={95 * ratio}
                    fill="none"
                    stroke="#e2e8f0"
                    strokeDasharray={ratio === 1.0 ? 'none' : '3 3'}
                    strokeWidth="1"
                  />
                ))}

                {/* Axis radiating lines */}
                {radarData.axisLines.map((axis, i) => (
                  <line
                    key={i}
                    x1={radarData.center}
                    y1={radarData.center}
                    x2={axis.x}
                    y2={axis.y}
                    stroke="#cbd5e1"
                    strokeWidth="1"
                  />
                ))}

                {/* Baseline polygon (dashed grey) */}
                <polygon
                  points={radarData.baselinePoints}
                  fill="rgba(148, 163, 184, 0.15)"
                  stroke="#94a3b8"
                  strokeWidth="1.5"
                  strokeDasharray="4 2"
                />

                {/* Current polygon (solid colored rose/emerald) */}
                <polygon
                  points={radarData.currentPoints}
                  fill={totalScore > 40 ? 'rgba(239, 68, 68, 0.25)' : 'rgba(16, 185, 129, 0.25)'}
                  stroke={totalScore > 40 ? '#ef4444' : '#10b981'}
                  strokeWidth="2.5"
                />
              </svg>
            </div>
          </div>

          {/* Distress Summary Metrics */}
          <div className="esas-metrics-card">
            <div className="esas-score-callout">
              <div>
                <span className="text-xs font-bold text-slate-500 uppercase">Symptom Distress Score</span>
                <div className="esas-total-val">
                  {totalScore} <span className="text-sm font-normal text-slate-500">/ 90</span>
                </div>
              </div>
              <div className="esas-delta-chip">
                {totalScore < baselineTotal ? (
                  <span className="text-emerald-700 font-bold">
                    ↓ Improved by {baselineTotal - totalScore} pts vs Baseline
                  </span>
                ) : (
                  <span className="text-rose-700 font-bold">
                    ↑ Increased by {totalScore - baselineTotal} pts
                  </span>
                )}
              </div>
            </div>

            {/* Severe Symptom Alerts */}
            {severeSymptoms.length > 0 ? (
              <div className="esas-alert-box">
                <div className="flex items-center gap-2 text-rose-800 font-bold text-xs">
                  <IconShieldAlert className="w-4 h-4 text-rose-600 flex-shrink-0" />
                  <span>Severe Symptom Threshold (Score ≥ 7) Triggered:</span>
                </div>
                <ul className="esas-severe-list">
                  {severeSymptoms.map((s) => (
                    <li key={s.id}>
                      <strong>{s.label}:</strong> {scores[s.id]} / 10 · Action: Titrate breakthrough PRN analgesia
                    </li>
                  ))}
                </ul>
              </div>
            ) : (
              <div className="esas-safe-box">
                <IconShieldCheck className="w-4 h-4 text-emerald-600 flex-shrink-0" />
                <span>All symptoms currently stabilized below acute intervention threshold (&lt;7/10).</span>
              </div>
            )}
          </div>
        </div>

        {/* Interactive Sliders for all 9 Domains */}
        <div className="esas-sliders-section">
          <h3 className="esas-section-title">Record Individual Symptom Ratings (0 = None, 10 = Worst Possible)</h3>
          <div className="esas-slider-grid">
            {ESAS_DOMAINS.map((domain) => {
              const val = scores[domain.id] ?? 0;
              const severityColor =
                val >= 7 ? 'text-rose-600 bg-rose-50 border-rose-200' : val >= 4 ? 'text-amber-600 bg-amber-50 border-amber-200' : 'text-emerald-600 bg-emerald-50 border-emerald-200';

              return (
                <div key={domain.id} className="esas-slider-card">
                  <div className="flex items-center justify-between mb-1.5">
                    <div>
                      <strong className="text-xs text-slate-800">{domain.label}</strong>
                      <span className="block text-[11px] text-slate-400">{domain.hindiLabel}</span>
                    </div>
                    <span className={`esas-val-pill ${severityColor}`}>
                      {val} / 10
                    </span>
                  </div>

                  <input
                    type="range"
                    min={0}
                    max={10}
                    step={1}
                    value={val}
                    onChange={(e) => handleScoreChange(domain.id, Number(e.target.value))}
                    className="esas-range-slider"
                  />

                  <div className="flex items-center justify-between text-[10px] text-slate-400 mt-1">
                    <span>{domain.lowAnchor} (0)</span>
                    <span>{domain.highAnchor} (10)</span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Footer */}
      <div className="esas-footer">
        <div className="flex items-center gap-2 text-xs text-slate-500">
          <IconShieldCheck className="w-4 h-4 text-emerald-600" />
          <span>Edmonton Symptom Assessment System (ESAS-r) · Validated Palliative Outcome Measure</span>
        </div>

        <div className="flex items-center gap-2">
          {onClose && (
            <button type="button" className="secondary-button" onClick={onClose}>
              Cancel
            </button>
          )}
          <button
            type="button"
            className="primary-button flex items-center gap-2"
            onClick={handleSave}
            disabled={savedToast}
          >
            {savedToast ? (
              <>
                <IconCheckCircle className="w-4 h-4 text-white" />
                <span>Saved &amp; Updated!</span>
              </>
            ) : (
              <>
                <IconSparkles className="w-4 h-4" />
                <span>Save Symptom Assessment</span>
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
