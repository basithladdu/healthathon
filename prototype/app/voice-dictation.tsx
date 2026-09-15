'use client';

import React, { useState, useEffect, useRef } from 'react';
import { IconSparkles, IconCheck, IconSquare } from './icons';

interface VoiceDictationProps {
  onTranscript: (text: string, append: boolean) => void;
  placeholder?: string;
  fieldLabel?: string;
}

export function VoiceDictationBar({
  onTranscript,
  fieldLabel = 'Conversation Notes',
}: VoiceDictationProps) {
  const [isListening, setIsListening] = useState(false);
  const [interimText, setInterimText] = useState('');
  const [supported, setSupported] = useState(true);
  const recognitionRef = useRef<any>(null);

  useEffect(() => {
    const SpeechRecognition =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;

    if (!SpeechRecognition) {
      setSupported(false);
      return;
    }

    const recognition = new SpeechRecognition();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = 'en-US';

    recognition.onstart = () => {
      setIsListening(true);
    };

    recognition.onresult = (event: any) => {
      let currentInterim = '';
      let finalSpeech = '';

      for (let i = event.resultIndex; i < event.results.length; ++i) {
        if (event.results[i].isFinal) {
          finalSpeech += event.results[i][0].transcript;
        } else {
          currentInterim += event.results[i][0].transcript;
        }
      }

      setInterimText(currentInterim);
      if (finalSpeech) {
        onTranscript(finalSpeech.trim(), true);
      }
    };

    recognition.onerror = (event: any) => {
      console.warn('Speech recognition error:', event.error);
      setIsListening(false);
    };

    recognition.onend = () => {
      setIsListening(false);
      setInterimText('');
    };

    recognitionRef.current = recognition;

    return () => {
      if (recognitionRef.current) {
        recognitionRef.current.abort();
      }
    };
  }, [onTranscript]);

  const toggleListening = () => {
    if (!recognitionRef.current) return;

    if (isListening) {
      recognitionRef.current.stop();
      setIsListening(false);
    } else {
      setInterimText('');
      try {
        recognitionRef.current.start();
      } catch (err) {
        console.warn('Recognition start exception:', err);
      }
    }
  };

  const applyTemplate = (templateText: string) => {
    onTranscript(templateText, false);
  };

  return (
    <div className="voice-dictation-bar">
      <div className="dictation-header">
        <div className="dictation-status">
          <button
            type="button"
            className={`dictate-btn ${isListening ? 'is-recording' : ''}`}
            onClick={toggleListening}
            title={isListening ? 'Stop voice recording' : 'Click to dictate notes hands-free'}
          >
            {isListening ? (
              <>
                <span className="rec-pulse-ring" />
                <IconSquare className="w-3.5 h-3.5 fill-red-600 text-red-600" />
                <span>Recording Speech...</span>
              </>
            ) : (
              <>
                <span className="mic-icon">🎙️</span>
                <span>Dictate into {fieldLabel}</span>
              </>
            )}
          </button>

          {isListening && (
            <div className="dictate-equalizer">
              <span className="dictate-bar b1" />
              <span className="dictate-bar b2" />
              <span className="dictate-bar b3" />
              <span className="dictate-bar b4" />
              <span className="dictate-bar b5" />
            </div>
          )}

          {interimText && (
            <span className="interim-preview">
              &ldquo;{interimText}&hellip;&rdquo;
            </span>
          )}
        </div>

        {/* Quick Clinical Templates */}
        <div className="dictate-quick-templates">
          <span className="template-kicker">1-Click Clinical Dictation Templates:</span>
          <button
            type="button"
            className="template-pill"
            onClick={() =>
              applyTemplate(
                'Comprehensive goals of care discussion held with patient and daughter. Patient expresses strong desire to remain at home with symptom management. DNACPR agreed and confirmed. Family supportive.'
              )
            }
          >
            + Goals of Care Conference
          </button>
          <button
            type="button"
            className="template-pill"
            onClick={() =>
              applyTemplate(
                'Emergency ceiling review completed. Due to refractory disease progression, cardiopulmonary resuscitation and invasive intubation are non-beneficial. Ward/HDU ceiling with anticipatory comfort medications instituted.'
              )
            }
          >
            + DNACPR &amp; Ceiling Review
          </button>
          <button
            type="button"
            className="template-pill"
            onClick={() =>
              applyTemplate(
                'Follow-up telephone check-in completed. Patient reports pain is well controlled on baseline analgesia. Caregiver feels confident with syringe pump. Follow-up scheduled in 2 weeks.'
              )
            }
          >
            + Coordinator Telephone Check-in
          </button>
        </div>
      </div>
    </div>
  );
}
