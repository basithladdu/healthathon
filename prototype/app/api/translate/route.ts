import { createTranslateHandler } from '../../translate-server.ts';
export const runtime = 'nodejs';

// Machine translation of a person's own words (en/hi/te/kn/ur): Bhashini first, then the free LLM chain. GET = what is set up.
export const dynamic = 'force-dynamic';
export const maxDuration = 60;
const handler = createTranslateHandler();
export const GET = handler;
export const POST = handler;
