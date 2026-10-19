import { createCareAssistHandler } from '../../../care-assist-server.ts';
export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const maxDuration = 90;
export const POST = createCareAssistHandler('transcribe');
