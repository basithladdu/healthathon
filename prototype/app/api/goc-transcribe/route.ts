import { createGocTranscribeHandler } from '../../goc-transcribe-server.ts';
export const runtime = 'nodejs';

// Online transcription of a goals-of-care recording with Gemini (Files API upload, then one call per window).
// GET = whether a Gemini key is set. POST {action: start | transcribe | delete}; POST ?action=chunk with raw audio (relay mode only).
export const dynamic = 'force-dynamic';
// One Gemini window (up to 10 minutes of audio) can take a few minutes to answer.
export const maxDuration = 300;
const handler = createGocTranscribeHandler();
export const GET = handler;
export const POST = handler;
