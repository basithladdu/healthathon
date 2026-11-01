import { createGocExtractHandler } from '../../goc-extract-server.ts';
export const runtime = 'nodejs';

// Optional second reading of a reviewed goals-of-care transcript, after separate consent. GET = whether any service is set up.
export const dynamic = 'force-dynamic';
export const maxDuration = 60;
const handler = createGocExtractHandler();
export const GET = handler;
export const POST = handler;
