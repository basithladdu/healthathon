-- OWNER APPLIES. Not applied by any agent. Project: Saanthvana (kjnhtakqzboayqorrlue), the Android app's project.
-- Kept outside prototype/supabase/migrations on purpose: that folder belongs to the website's project (cghlkqeqiokjqdionrkc).
-- Copy it into android/supabase/migrations/ (or paste it into the SQL editor of kjnhtakqzboayqorrlue) to apply.
--
-- Lets the website's /admin page (shared admin password, server-side service-role key) review doctor sign-ups and
-- read impact counts from private tables. Both functions are callable by service_role only.
-- sahara_admin_review_clinician mirrors healthathon_private.review_clinician
-- (20261019151322_shared_care_clinician_review.sql): same decisions, same evidence/note limits, same revision check,
-- same update of healthathon_clinician_requests and the same clinician_reviews log row. Differences:
--  * the reviewer is the name typed on /admin (reviewer_label), not an auth.users id, so reviewed_by stays null;
--  * only these transitions are allowed: pending → approved|rejected, approved → suspended, suspended → approved.
begin;

alter table healthathon_private.clinician_reviews alter column reviewer_id drop not null;
alter table healthathon_private.clinician_reviews add column if not exists reviewer_label text not null default ''
  check (length(reviewer_label) <= 100);
alter table healthathon_private.clinician_reviews add constraint clinician_reviews_has_reviewer
  check (reviewer_id is not null or length(btrim(reviewer_label)) between 2 and 100);

create function public.sahara_admin_review_clinician(p_user uuid, p_revision bigint, p_decision text, p_evidence text, p_note text, p_reviewer text)
returns public.healthathon_clinician_requests
language plpgsql security definer set search_path='' as $$
declare saved public.healthathon_clinician_requests; current_status text;
begin
 if p_user is null or p_revision is null or p_decision is null or p_decision not in ('approved','rejected','suspended')
 or length(btrim(coalesce(p_evidence,''))) not between 8 and 1200 or length(btrim(coalesce(p_note,''))) not between 8 and 2000
 then raise exception 'Record the checked register/source and review reason.' using errcode='22023'; end if;
 if length(btrim(coalesce(p_reviewer,''))) not between 2 and 100 then raise exception 'Add the reviewer''s name.' using errcode='22023'; end if;
 select status into current_status from public.healthathon_clinician_requests where user_id=p_user and revision=p_revision for update;
 if not found then raise exception 'This request changed. Refresh before reviewing.' using errcode='40001'; end if;
 if not ((current_status='pending' and p_decision in ('approved','rejected'))
      or (current_status='approved' and p_decision='suspended')
      or (current_status='suspended' and p_decision='approved'))
 then raise exception 'A % request cannot be marked %.', current_status, p_decision using errcode='22023'; end if;
 update public.healthathon_clinician_requests set status=p_decision,reviewed_by=null,reviewed_at=now(),review_note=p_note,revision=revision+1
 where user_id=p_user and revision=p_revision returning * into saved;
 insert into healthathon_private.clinician_reviews(user_id,reviewer_id,reviewer_label,decision,evidence_reference,note,request_revision)
 values(p_user,null,btrim(p_reviewer),p_decision,p_evidence,p_note,p_revision);
 return saved;
end $$;

-- Counts for /admin → Impact from tables PostgREST does not expose. Numbers only, no rows.
-- Emergency links are counted only when 20261027190000_emergency_card_links.sql has been applied (otherwise the keys are absent).
create function public.sahara_admin_private_counts() returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare result jsonb := jsonb_build_object('clinician_reviews', (select count(*) from healthathon_private.clinician_reviews));
 links bigint; links_30d bigint; live bigint; views bigint; viewed_30d bigint;
begin
 if to_regclass('healthathon_private.emergency_links') is not null then
  execute 'select count(*), count(*) filter (where created_at > now() - interval ''30 days''),
    count(*) filter (where revoked_at is null and expires_at > now()), coalesce(sum(views),0),
    count(*) filter (where last_viewed_at > now() - interval ''30 days'') from healthathon_private.emergency_links'
   into links, links_30d, live, views, viewed_30d;
  result := result || jsonb_build_object('emergency_links', links, 'emergency_links_30d', links_30d, 'emergency_links_live', live,
   'emergency_views', views, 'emergency_viewed_30d', viewed_30d);
 end if;
 return result;
end $$;

revoke all on function public.sahara_admin_review_clinician(uuid,bigint,text,text,text,text) from public, anon, authenticated;
grant execute on function public.sahara_admin_review_clinician(uuid,bigint,text,text,text,text) to service_role;
revoke all on function public.sahara_admin_private_counts() from public, anon, authenticated;
grant execute on function public.sahara_admin_private_counts() to service_role;

commit;
