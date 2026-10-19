begin;
create index if not exists sahara_assist_user on healthathon_private.assist_reservations(user_id);
create index if not exists sahara_clinician_review_user on healthathon_private.clinician_reviews(user_id);
create index if not exists sahara_clinician_review_reviewer on healthathon_private.clinician_reviews(reviewer_id);
create index if not exists sahara_staff_grantor on healthathon_private.staff_roles(granted_by);
create index if not exists sahara_draft_author on public.healthathon_care_drafts(author_id);
create index if not exists sahara_clinician_request_reviewer on public.healthathon_clinician_requests(reviewed_by);
create index if not exists sahara_task_author on public.healthathon_team_tasks(author_id);
create index if not exists sahara_task_recipient on public.healthathon_team_tasks(recipient_id);
create index if not exists sahara_invite_person on healthathon_private.invites(person_id);

-- This remains the service-only Sahara closure route. It never deletes the
-- shared Auth identity or another application's data. No account is closed here.
create or replace function healthathon_private.close_account(p_user uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare removed_people bigint; removed_memberships bigint; closed timestamptz;
begin
 if p_user is null then raise exception 'Choose the verified Sahara account.' using errcode='22004'; end if;
 perform pg_catalog.pg_advisory_xact_lock(731044821,pg_catalog.hashtext(p_user::text));
 if not exists(select 1 from auth.users where id=p_user) then raise exception 'The verified account was not found.' using errcode='P0002'; end if;
 insert into healthathon_private.closed_accounts(user_id) values(p_user) on conflict(user_id) do nothing;
 select closed_at into closed from healthathon_private.closed_accounts where user_id=p_user;
 delete from public.healthathon_members where user_id=p_user;
 get diagnostics removed_memberships=row_count;
 delete from healthathon_private.staff_roles where user_id=p_user;
 delete from healthathon_private.assist_reservations where user_id=p_user;
 delete from public.healthathon_clinician_requests where user_id=p_user;
 delete from healthathon_private.clinician_reviews r where r.user_id=p_user and not exists(
 select 1 from public.healthathon_records n join public.healthathon_people p on p.id=n.person_id
 where n.kind='note' and n.data->>'reviewerId'=p_user::text and n.data->>'reviewerVerification'='verified' and p.owner_id<>p_user);
 -- Unreviewed drafts authored in somebody else's record are not needed as
 -- immutable review evidence. Already reviewed, patient-owned sources remain.
 delete from public.healthathon_care_drafts where author_id=p_user and status<>'reviewed';
 -- Do not claim that a closed recipient completed pending work.
 update public.healthathon_team_tasks set status='cancelled',revision=revision+1,updated_at=now()
 where recipient_id=p_user and status in ('open','accepted');
 delete from public.healthathon_people where owner_id=p_user;
 get diagnostics removed_people=row_count;
 return jsonb_build_object('closed_at',closed,'removed_people',removed_people,'removed_memberships',removed_memberships,
 'retained_scope','Closed-account marker; immutable reviewed notes/source and task history in other patient-owned records; verification decision audit supporting other people''s recorded reviews or professional approvals. Copies already exported cannot be recalled.');
end $$;
revoke all on function healthathon_private.close_account(uuid) from public,anon,authenticated;
grant execute on function healthathon_private.close_account(uuid) to service_role;
commit;
