'use client';

import React, { useState, useEffect, useRef, useCallback, useSyncExternalStore } from 'react';
import { IconVolume2, IconPlay, IconPause, IconSquare } from './icons';
import { careText, type CareLang } from './care-i18n';
import { pickVoice, speechChunks, speechLocale } from './tts-voice';

export type TtsSpeed = 0.85 | 1.0 | 1.25;

const noSubscribe = () => () => {};
const speechAvailable = () => 'speechSynthesis' in window;
const notOnServer = () => false;

/** `lang` picks the voice and the speech locale; a sentence not translated yet is read in English (see tts-voice.ts). */
export function useSpeechSynthesis(lang: CareLang = 'en') {
  const isSupported = useSyncExternalStore(noSubscribe, speechAvailable, notOnServer);
  const [noVoice, setNoVoice] = useState(false);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isPaused, setIsPaused] = useState(false);
  const [rate, setRate] = useState<TtsSpeed>(1.0);
  // Keeps the utterances alive while speaking (some browsers drop their events otherwise), and marks which run is current.
  const utteranceRef = useRef<SpeechSynthesisUtterance[]>([]);
  const runRef = useRef(0);

  const cancel = useCallback(() => {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      runRef.current += 1;
      window.speechSynthesis.cancel();
      setIsPlaying(false);
      setIsPaused(false);
    }
  }, []);

  const speak = useCallback(
    (text: string, customRate?: TtsSpeed) => {
      if (typeof window === 'undefined' || !('speechSynthesis' in window)) return;

      runRef.current += 1;
      const run = runRef.current;
      window.speechSynthesis.cancel();

      const chunks = speechChunks(text, lang);
      if (!chunks.length) return;

      const voices = window.speechSynthesis.getVoices();
      // An empty voice list means the browser has not loaded them yet, not that the language is missing.
      setNoVoice(voices.length > 0 && chunks.some((chunk) => !pickVoice(voices, chunk.lang)));

      const finished = () => {
        if (runRef.current !== run) return;
        setIsPlaying(false);
        setIsPaused(false);
      };

      utteranceRef.current = chunks.map((chunk, index) => {
        const utterance = new SpeechSynthesisUtterance(chunk.text);
        utterance.lang = speechLocale(chunk.lang);
        utterance.rate = customRate ?? rate;
        utterance.pitch = 1.0;
        const voice = pickVoice(voices, chunk.lang);
        if (voice) utterance.voice = voice;
        utterance.onstart = () => {
          if (runRef.current !== run) return;
          setIsPlaying(true);
          setIsPaused(false);
        };
        if (index === chunks.length - 1) utterance.onend = finished;
        utterance.onerror = finished;
        return utterance;
      });
      for (const utterance of utteranceRef.current) window.speechSynthesis.speak(utterance);
      // Some browsers and phones have no voices at all, and then nothing starts and no error comes. Say so rather than stay silent.
      window.setTimeout(() => {
        if (runRef.current === run && !window.speechSynthesis.speaking && !window.speechSynthesis.getVoices().length) setNoVoice(true);
      }, 2000);
    },
    [rate, lang]
  );

  const pause = useCallback(() => {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window && isPlaying && !isPaused) {
      window.speechSynthesis.pause();
      setIsPaused(true);
    }
  }, [isPlaying, isPaused]);

  const resume = useCallback(() => {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window && isPlaying && isPaused) {
      window.speechSynthesis.resume();
      setIsPaused(false);
    }
  }, [isPlaying, isPaused]);

  useEffect(() => {
    return () => {
      if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
        runRef.current += 1;
        window.speechSynthesis.cancel();
      }
    };
  }, []);

  return {
    isSupported,
    isPlaying,
    isPaused,
    /** The device has no voice for the language being read. */
    noVoice,
    rate,
    setRate,
    speak,
    pause,
    resume,
    cancel,
  };
}

/** The words on screen, in order, for read-aloud. Skips buttons and hidden parts; says which options are chosen and what was typed. */
export function readableText(root: Element, selectedWord: string): string {
  const BLOCK = /^(ADDRESS|ARTICLE|ASIDE|DD|DIV|DL|DT|FIELDSET|FIGURE|H[1-6]|HEADER|LABEL|LEGEND|LI|OL|P|SECTION|SMALL|STRONG|SUMMARY|UL)$/;
  const parts: string[] = [];
  const walk = (node: Node) => {
    if (node.nodeType === Node.TEXT_NODE) { parts.push(node.textContent ?? ''); return; }
    if (node.nodeType !== Node.ELEMENT_NODE) return;
    const el = node as HTMLElement;
    if (el.matches('button, svg, script, style, [hidden], [aria-hidden="true"], [data-no-read]')) return;
    if (el instanceof HTMLInputElement) {
      if (el.type === 'radio' || el.type === 'checkbox') { if (el.checked) parts.push(`${selectedWord}: `); }
      else if (el.type !== 'file' && el.type !== 'hidden' && el.value) parts.push(` ${el.value}\n`);
      return;
    }
    if (el instanceof HTMLTextAreaElement) { if (el.value) parts.push(` ${el.value}\n`); return; }
    if (el instanceof HTMLSelectElement) { if (el.value) parts.push(` ${el.selectedOptions[0]?.text ?? ''}\n`); return; }
    const closed = el instanceof HTMLDetailsElement && !el.open;
    const block = BLOCK.test(el.tagName);
    if (block) parts.push('\n');
    else if (el.tagName === 'SPAN') parts.push(' ');
    for (const child of Array.from(el.childNodes)) {
      if (closed && !(child instanceof HTMLElement && child.tagName === 'SUMMARY')) continue;
      walk(child);
    }
    if (block) parts.push('\n');
    else if (el.tagName === 'SPAN') parts.push(' ');
  };
  walk(root);
  return parts.join('');
}

/**
 * Small read-aloud button (tech spec §7) in the reader's care language. Give it `text`, or `target` to read what is on screen
 * when pressed, so what is heard matches what is shown, including choices already made.
 */
export function ReadAloudButton(props: { lang: CareLang; text?: string | (() => string); target?: React.RefObject<Element | null>; label?: string; className?: string }) {
  const { lang, text, target, label, className = '' } = props;
  const { isSupported, isPlaying, noVoice, speak, cancel } = useSpeechSynthesis(lang);
  if (!isSupported) return null;
  const say = () => {
    if (isPlaying) { cancel(); return; }
    const words = target?.current ? readableText(target.current, careText(lang, 'selected', 'चुना गया')) : typeof text === 'function' ? text() : text ?? '';
    speak(words);
  };
  return (
    <span className={`read-aloud ${className}`} data-no-read>
      <button type="button" className="read-aloud-btn" onClick={say} aria-pressed={isPlaying}>
        {isPlaying ? <IconSquare className="w-3.5 h-3.5" /> : <IconVolume2 className="w-4 h-4" />}
        <span>{isPlaying ? careText(lang, 'Stop reading', 'पढ़ना रोकें') : label ?? careText(lang, 'Read aloud', 'पढ़कर सुनाएँ')}</span>
      </button>
      {noVoice ? <small className="read-aloud-note" role="status">{careText(lang, 'This device has no voice for this language, so it may not read clearly. You can add one in the phone’s language settings.', 'इस डिवाइस पर इस भाषा की आवाज़ नहीं है, इसलिए शायद साफ़ न पढ़ पाए। फ़ोन की भाषा सेटिंग में आवाज़ जोड़ सकते हैं।')}</small> : null}
    </span>
  );
}

export function AudioTtsPlayer(props: {
  text: string;
  title: string;
  subtitle?: string;
  variant?: 'patient' | 'emergency' | 'clinical';
  autoLabel?: string;
  className?: string;
  lang?: CareLang;
}) {
  const { text, title, subtitle, variant = 'patient', className = '', lang = 'en' } = props;
  const { isSupported, isPlaying, isPaused, noVoice, rate, setRate, speak, pause, resume, cancel } = useSpeechSynthesis(lang);

  if (!isSupported) {
    return null;
  }

  const handleTogglePlay = () => {
    if (isPlaying) {
      if (isPaused) {
        resume();
      } else {
        pause();
      }
    } else {
      speak(text, rate);
    }
  };

  const handleStop = () => {
    cancel();
  };

  const cycleRate = () => {
    const nextRate: TtsSpeed = rate === 0.85 ? 1.0 : rate === 1.0 ? 1.25 : 0.85;
    setRate(nextRate);
    if (isPlaying) {
      cancel();
      speak(text, nextRate);
    }
  };

  const rateLabel = rate === 0.85 ? careText(lang, '0.85x Gentle', '0.85x धीरे') : rate === 1.0 ? careText(lang, '1.0x Normal', '1.0x सामान्य') : careText(lang, '1.25x Fast', '1.25x तेज़');

  return (
    <div className={`tts-player-card tts-player--${variant} ${className}`}>
      <div className="tts-player-header">
        <div className="tts-player-badge">
          <IconVolume2 className="w-4 h-4 text-emerald-700" />
          <span>{careText(lang, 'Voice audio', 'आवाज़ में सुनें')}</span>
        </div>
        {isPlaying && (
          <div className="tts-equalizer" aria-hidden="true">
            <span className={`tts-eq-bar bar-1 ${isPaused ? 'paused' : ''}`} />
            <span className={`tts-eq-bar bar-2 ${isPaused ? 'paused' : ''}`} />
            <span className={`tts-eq-bar bar-3 ${isPaused ? 'paused' : ''}`} />
            <span className={`tts-eq-bar bar-4 ${isPaused ? 'paused' : ''}`} />
          </div>
        )}
      </div>

      <div className="tts-player-info">
        <h4 className="tts-player-title">{title}</h4>
        {subtitle && <p className="tts-player-subtitle">{subtitle}</p>}
      </div>

      <div className="tts-player-controls">
        <button
          type="button"
          onClick={handleTogglePlay}
          className={`tts-btn tts-btn-primary ${isPlaying && !isPaused ? 'is-playing' : ''}`}
          aria-label={isPlaying ? (isPaused ? careText(lang, 'Resume reading', 'फिर पढ़ें') : careText(lang, 'Pause reading', 'रोकें')) : careText(lang, 'Read aloud', 'पढ़कर सुनाएँ')}
        >
          {isPlaying ? (
            isPaused ? (
              <>
                <IconPlay className="w-4 h-4" />
                <span>{careText(lang, 'Resume', 'फिर चलाएँ')}</span>
              </>
            ) : (
              <>
                <IconPause className="w-4 h-4" />
                <span>{careText(lang, 'Pause', 'रोकें')}</span>
              </>
            )
          ) : (
            <>
              <IconPlay className="w-4 h-4" />
              <span>{careText(lang, 'Read aloud', 'पढ़कर सुनाएँ')}</span>
            </>
          )}
        </button>

        {isPlaying && (
          <button
            type="button"
            onClick={handleStop}
            className="tts-btn tts-btn-stop"
            aria-label={careText(lang, 'Stop reading', 'पढ़ना रोकें')}
            title={careText(lang, 'Stop reading', 'पढ़ना रोकें')}
          >
            <IconSquare className="w-3.5 h-3.5" />
            <span>{careText(lang, 'Stop', 'बंद करें')}</span>
          </button>
        )}

        <button
          type="button"
          onClick={cycleRate}
          className="tts-btn tts-btn-speed"
          title={careText(lang, 'Adjust reading speed', 'पढ़ने की गति बदलें')}
        >
          {rateLabel}
        </button>
      </div>

      {isPlaying && (
        <div className="tts-reading-status" aria-live="polite">
          {isPaused ? careText(lang, 'Reading paused', 'पढ़ना रुका है') : careText(lang, 'Reading aloud now…', 'पढ़कर सुना रहे हैं…')}
        </div>
      )}
      {noVoice && <p className="read-aloud-note" role="status">{careText(lang, 'This device has no voice for this language, so it may not read clearly. You can add one in the phone’s language settings.', 'इस डिवाइस पर इस भाषा की आवाज़ नहीं है, इसलिए शायद साफ़ न पढ़ पाए। फ़ोन की भाषा सेटिंग में आवाज़ जोड़ सकते हैं।')}</p>}
    </div>
  );
}
