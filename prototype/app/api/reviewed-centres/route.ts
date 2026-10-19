import { CARE_PUBLISHABLE_KEY, CARE_SUPABASE_URL } from '../../care-shared-client';
import { readReviewedDirectory } from '../../care-reviewed-directory';
export const dynamic = 'force-dynamic';
export async function GET(): Promise<Response> {
  const headers = { 'Cache-Control':'no-store', 'X-Content-Type-Options':'nosniff' };
  try {
    const response = await fetch(`${CARE_SUPABASE_URL}/rest/v1/rpc/sahara_directory_list`, {method:'POST',headers:{apikey:CARE_PUBLISHABLE_KEY,'Content-Type':'application/json'},body:'{}',cache:'no-store',redirect:'error',signal:AbortSignal.timeout(10000)});
    if (!response.ok) throw new Error('Unavailable');
    return Response.json({centres:readReviewedDirectory(await response.json())},{headers});
  } catch { return Response.json({error:'Reviewed hospital updates could not load. The existing directory is still available.'},{status:503,headers}); }
}
