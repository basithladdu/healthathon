import { careAssistConfiguration, createCareAssistHandler } from '../../care-assist-server.ts';
export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const maxDuration = 90;
export function GET() {
  return Response.json(careAssistConfiguration(), { headers: { 'Cache-Control': 'private, no-store', 'X-Content-Type-Options': 'nosniff' } });
}
export const POST = createCareAssistHandler('draft');
