import { createSaharaAssistHandler } from '../../../sahara-assist-server.ts';
export const runtime = 'nodejs';

// Android online assist: GET = what is set up; POST = draft (no suffix), /transcribe, /translate, /ask.
export const dynamic = 'force-dynamic';
export const maxDuration = 60;
const handler = createSaharaAssistHandler();
export const GET = handler;
export const POST = handler;
