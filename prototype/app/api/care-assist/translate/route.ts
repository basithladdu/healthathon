import { createCareAssistHandler } from '../../../care-assist-server.ts';
export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const maxDuration = 300;
export const POST = createCareAssistHandler('translate');
