'use client';

import React, { useState, useEffect, useRef, useCallback } from 'react';
import { IconVolume2, IconVolumeX, IconPlay, IconPause, IconSquare } from './icons';

export type TtsSpeed = 0.85 | 1.0 | 1.25;

export function useSpeechSynthesis() {
  const [isSupported, setIsSupported] = useState(false);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isPaused, setIsPaused] = useState(false);
  const [rate, setRate] = useState<TtsSpeed>(1.0);
  const utteranceRef = useRef<SpeechSynthesisUtterance | null>(null);

  useEffect(() => {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      setIsSupported(true);
    }
  }, []);

  const cancel = useCallback(() => {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      window.speechSynthesis.cancel();
      setIsPlaying(false);
      setIsPaused(false);
    }
  }, []);

  const speak = useCallback(
    (text: string, customRate?: TtsSpeed) => {
      if (typeof window === 'undefined' || !('speechSynthesis' in window)) return;

      window.speechSynthesis.cancel();

      const cleanText = text
        .replace(/[#*•_`~]/g, '')
        .replace(/\n+/g, '. ')
        .replace(/\s+/g, ' ')
        .trim();

      if (!cleanText) return;

      const utterance = new SpeechSynthesisUtterance(cleanText);
      utterance.rate = customRate ?? rate;
      utterance.pitch = 1.0;

      const voices = window.speechSynthesis.getVoices();
      const preferredVoice =
        voices.find((v) => v.lang.startsWith('en') && (v.name.includes('Natural') || v.name.includes('Online') || v.name.includes('Google'))) ||
        voices.find((v) => v.lang.startsWith('en')) ||
        voices[0];

      if (preferredVoice) {
        utterance.voice = preferredVoice;
      }

      utterance.onstart = () => {
        setIsPlaying(true);
        setIsPaused(false);
      };

      utterance.onend = () => {
        setIsPlaying(false);
        setIsPaused(false);
      };

      utterance.onerror = () => {
        setIsPlaying(false);
        setIsPaused(false);
      };

      utteranceRef.current = utterance;
      window.speechSynthesis.speak(utterance);
    },
    [rate]
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
        window.speechSynthesis.cancel();
      }
    };
  }, []);

  return {
    isSupported,
    isPlaying,
    isPaused,
    rate,
    setRate,
    speak,
    pause,
    resume,
    cancel,
  };
}

export function AudioTtsPlayer(props: {
  text: string;
  title: string;
  subtitle?: string;
  variant?: 'patient' | 'emergency' | 'clinical';
  autoLabel?: string;
  className?: string;
}) {
  const { text, title, subtitle, variant = 'patient', className = '' } = props;
  const { isSupported, isPlaying, isPaused, rate, setRate, speak, pause, resume, cancel } = useSpeechSynthesis();

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

  const rateLabel = rate === 0.85 ? '0.85x Gentle' : rate === 1.0 ? '1.0x Normal' : '1.25x Fast';

  return (
    <div className={`tts-player-card tts-player--${variant} ${className}`}>
      <div className="tts-player-header">
        <div className="tts-player-badge">
          <IconVolume2 className="w-4 h-4 text-emerald-700" />
          <span>Open-Source Voice Audio</span>
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
          aria-label={isPlaying ? (isPaused ? 'Resume reading' : 'Pause reading') : 'Listen aloud'}
        >
          {isPlaying ? (
            isPaused ? (
              <>
                <IconPlay className="w-4 h-4" />
                <span>Resume</span>
              </>
            ) : (
              <>
                <IconPause className="w-4 h-4" />
                <span>Pause</span>
              </>
            )
          ) : (
            <>
              <IconPlay className="w-4 h-4" />
              <span>Read Aloud (TTS)</span>
            </>
          )}
        </button>

        {isPlaying && (
          <button
            type="button"
            onClick={handleStop}
            className="tts-btn tts-btn-stop"
            aria-label="Stop reading"
            title="Stop playback"
          >
            <IconSquare className="w-3.5 h-3.5" />
            <span>Stop</span>
          </button>
        )}

        <button
          type="button"
          onClick={cycleRate}
          className="tts-btn tts-btn-speed"
          title="Adjust reading speed"
        >
          {rateLabel}
        </button>
      </div>

      {isPlaying && (
        <div className="tts-reading-status" aria-live="polite">
          {isPaused ? '⏸ Reading paused' : '🔊 Reading aloud now...'}
        </div>
      )}
    </div>
  );
}
