-- ONE-TIME OPERATOR SETUP ONLY: select Saanthvana (kjnhtakqzboayqorrlue).
-- Do not apply through the current Sahara application's migration pipeline.
-- No patient/table access, data writes, or RLS changes.
begin;
create function public.sahara_health_check()
returns boolean
language sql
stable
security invoker
set search_path = ''
as $$ select true $$;
revoke all on function public.sahara_health_check() from public, anon, authenticated, service_role;
grant execute on function public.sahara_health_check() to anon;
commit;
