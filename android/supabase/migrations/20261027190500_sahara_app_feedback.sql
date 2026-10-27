-- OWNER APPLIES. Not applied by the app lane (27 Oct 2026).
-- App-experience feedback from signed-in Android users ("How was your Sahara experience?").
-- Until this is applied the app keeps each feedback queued on the phone and says it was not sent.
-- No care record, person or report is read or changed. Rows cascade when the auth user is deleted.
begin;

create table public.sahara_android_feedback (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  surface text not null check (surface in ('patient','doctor-preview','doctor-patient')),
  score real not null check (score >= 0 and score <= 1),
  note text not null default '' check (length(note) <= 500),
  saved_at timestamptz not null,
  created_at timestamptz not null default now()
);
create index sahara_android_feedback_user_time on public.sahara_android_feedback(user_id, created_at);
alter table public.sahara_android_feedback enable row level security;
revoke all on public.sahara_android_feedback from public, anon, authenticated;
grant select on public.sahara_android_feedback to service_role;

-- Idempotent: resending the same id returns the existing receipt, so a retry never doubles a row.
create function public.sahara_submit_app_feedback(p_id uuid, p_surface text, p_score real, p_note text, p_saved_ms bigint)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare caller uuid := auth.uid(); existing public.sahara_android_feedback%rowtype;
begin
  if caller is null or not coalesce(healthathon_private.require_open_account(), false) then
    raise exception 'Sign in to send feedback.' using errcode = '42501';
  end if;
  if p_id is null or p_saved_ms is null or p_saved_ms <= 0 then raise exception 'Feedback is incomplete.' using errcode = 'P0001'; end if;
  select * into existing from public.sahara_android_feedback where id = p_id;
  if found then
    if existing.user_id <> caller then raise exception 'Feedback could not be sent.' using errcode = '42501'; end if;
    return jsonb_build_object('id', p_id, 'status', 'received');
  end if;
  if (select count(*) from public.sahara_android_feedback where user_id = caller and created_at >= now() - interval '24 hours') >= 30 then
    raise exception 'Too much feedback today. It stays on this phone; try again tomorrow.' using errcode = 'P0001';
  end if;
  insert into public.sahara_android_feedback(id, user_id, surface, score, note, saved_at)
  values (p_id, caller, p_surface, p_score, btrim(coalesce(p_note, '')), to_timestamp(p_saved_ms / 1000.0));
  return jsonb_build_object('id', p_id, 'status', 'received');
end; $$;
revoke all on function public.sahara_submit_app_feedback(uuid, text, real, text, bigint) from public, anon;
grant execute on function public.sahara_submit_app_feedback(uuid, text, real, text, bigint) to authenticated;

commit;
