-- Requires 20261019151322_shared_care_clinician_review (trusted staff ACL).
-- Intake stays immutable/pending_review. A separate revisioned review controls publication.
begin;
create table healthathon_private.directory_reviews (
 submission_id uuid primary key references public.sahara_hospital_submissions(id),
 listing_id uuid not null unique default gen_random_uuid(),
 revision integer not null default 1,
 decision text not null check(decision in ('approved','needs_clarification','rejected','withdrawn')),
 listing jsonb not null default '{}', evidence_reference text not null,
 review_note text not null, reviewer_id uuid not null references auth.users(id),
 reviewed_at timestamptz not null default now(), expires_at timestamptz
);
create table healthathon_private.directory_review_history (
 submission_id uuid not null references public.sahara_hospital_submissions(id), revision integer not null,
 decision text not null, listing jsonb not null, evidence_reference text not null,
 review_note text not null, reviewer_id uuid not null references auth.users(id),
 reviewed_at timestamptz not null, expires_at timestamptz,
 primary key(submission_id,revision)
);
alter table healthathon_private.directory_reviews enable row level security;
alter table healthathon_private.directory_review_history enable row level security;
revoke all on healthathon_private.directory_reviews,healthathon_private.directory_review_history from public,anon,authenticated;

create function healthathon_private.valid_directory_listing(p jsonb) returns boolean
language plpgsql immutable set search_path='' as $$
begin
 if p is null or jsonb_typeof(p)<>'object' or (select count(*) from jsonb_object_keys(p))<>11
 or not(p ?& array['name','city','state','address','phone','latitude','longitude','services','morphine','notes','sourceUrl'])
 then return false; end if;
 if exists(select 1 from jsonb_each(p) x where x.key=any(array['name','city','state','address','phone','morphine','notes','sourceUrl']) and jsonb_typeof(x.value)<>'string')
 or length(btrim(p->>'name')) not between 2 and 160 or length(btrim(p->>'city')) not between 2 and 80
 or length(btrim(p->>'state')) not between 2 and 80 or length(btrim(p->>'address')) not between 5 and 400
 or length(p->>'notes')>1000 or length(p->>'phone')>40
 or (p->>'phone'<>'' and p->>'phone' !~ '^\+?[0-9 ()-]{7,40}$')
 or p->>'sourceUrl' !~ '^https://[A-Za-z0-9][A-Za-z0-9.-]+(:443)?(/[^[:space:]]*)?$'
 or length(p->>'sourceUrl')>1000 or p->>'morphine' not in ('reported_yes','reported_no','unknown')
 or jsonb_typeof(p->'latitude')<>'number' or jsonb_typeof(p->'longitude')<>'number'
 or (p->>'latitude')::numeric not between 6 and 38 or (p->>'longitude')::numeric not between 68 and 98
 or jsonb_typeof(p->'services')<>'array' then return false; end if;
 return jsonb_array_length(p->'services') between 0 and 3
 and not exists(select 1 from jsonb_array_elements(p->'services') x where jsonb_typeof(x)<>'string' or x#>>'{}' not in ('opd','ipd','home'))
 and (select count(distinct x) from jsonb_array_elements(p->'services') x)=jsonb_array_length(p->'services');
exception when others then return false;
end $$;
revoke all on function healthathon_private.valid_directory_listing(jsonb) from public,anon,authenticated;

create function healthathon_private.directory_queue(p_offset integer default 0) returns jsonb
language plpgsql security definer set search_path='' as $$
declare result jsonb;
begin
 if not healthathon_private.has_staff_role('directory_reviewer') then raise exception 'Directory reviewer access required.' using errcode='42501'; end if;
 if p_offset is null or p_offset<0 or p_offset>100000 then raise exception 'Invalid page.'; end if;
 select coalesce(jsonb_agg(row),'[]') into result from (
 select s.id,s.facility,s.created_at,c.full_name,c.respondent_role,c.council,c.registration_state,c.registration_number,c.mobile,c.email,
 coalesce(r.revision,0) revision,coalesce(r.decision,'pending_review') decision,r.listing,r.review_note,r.evidence_reference,r.reviewed_at,r.expires_at
 from public.sahara_hospital_submissions s join public.sahara_hospital_contributors c on c.submission_id=s.id
 left join healthathon_private.directory_reviews r on r.submission_id=s.id
 order by case when r.decision is null then 0 when r.expires_at<now() then 1 else 2 end,s.created_at,s.id limit 50 offset p_offset
 ) row;
 return result;
end $$;
revoke all on function healthathon_private.directory_queue(integer) from public,anon;
grant execute on function healthathon_private.directory_queue(integer) to authenticated;
create function public.sahara_directory_queue(p_offset integer default 0) returns jsonb
language sql security invoker set search_path='' as $$select healthathon_private.directory_queue(p_offset)$$;
revoke all on function public.sahara_directory_queue(integer) from public,anon;
grant execute on function public.sahara_directory_queue(integer) to authenticated;

create function healthathon_private.review_directory(p_id uuid,p_revision integer,p_decision text,p_listing jsonb,p_evidence text,p_note text,p_expires timestamptz) returns jsonb
language plpgsql security definer set search_path='' as $$
declare saved healthathon_private.directory_reviews; previous integer;
begin
 if not healthathon_private.has_staff_role('directory_reviewer') then raise exception 'Directory reviewer access required.' using errcode='42501'; end if;
 if p_id is null or p_revision is null or p_decision is null or p_decision not in ('approved','needs_clarification','rejected','withdrawn')
 or p_note is null or length(btrim(p_note)) not between 5 and 2000
 or p_evidence is null or length(btrim(p_evidence)) not between 5 and 2000 then raise exception 'Record your checks and evidence.' using errcode='22023'; end if;
 -- Lock the immutable parent even for a first review, so concurrent first decisions conflict.
 perform 1 from public.sahara_hospital_submissions where id=p_id for update;
 if not found then raise exception 'Submission not found.'; end if;
 select revision into previous from healthathon_private.directory_reviews where submission_id=p_id;
 if coalesce(previous,0)<>p_revision then raise exception 'This submission changed. Refresh and compare before saving.' using errcode='40001'; end if;
 if p_decision='approved' and (healthathon_private.valid_directory_listing(p_listing) is distinct from true
 or p_expires is null or p_expires<=now() or p_expires>now()+interval '90 days') then raise exception 'Check the public listing and a review expiry within 90 days.' using errcode='22023'; end if;
 insert into healthathon_private.directory_reviews(submission_id,revision,decision,listing,evidence_reference,review_note,reviewer_id,expires_at)
 values(p_id,p_revision+1,p_decision,case when p_decision='approved' then p_listing else '{}'::jsonb end,btrim(p_evidence),btrim(p_note),auth.uid(),case when p_decision='approved' then p_expires else null end)
 on conflict(submission_id) do update set revision=excluded.revision,decision=excluded.decision,listing=excluded.listing,evidence_reference=excluded.evidence_reference,review_note=excluded.review_note,reviewer_id=excluded.reviewer_id,reviewed_at=clock_timestamp(),expires_at=excluded.expires_at
 returning * into saved;
 insert into healthathon_private.directory_review_history values(saved.submission_id,saved.revision,saved.decision,saved.listing,saved.evidence_reference,saved.review_note,saved.reviewer_id,saved.reviewed_at,saved.expires_at);
 return jsonb_build_object('id',saved.submission_id,'revision',saved.revision,'decision',saved.decision,'reviewedAt',saved.reviewed_at);
end $$;
revoke all on function healthathon_private.review_directory(uuid,integer,text,jsonb,text,text,timestamptz) from public,anon;
grant execute on function healthathon_private.review_directory(uuid,integer,text,jsonb,text,text,timestamptz) to authenticated;
create function public.sahara_review_directory(p_id uuid,p_revision integer,p_decision text,p_listing jsonb,p_evidence text,p_note text,p_expires timestamptz) returns jsonb
language sql security invoker set search_path='' as $$select healthathon_private.review_directory(p_id,p_revision,p_decision,p_listing,p_evidence,p_note,p_expires)$$;
revoke all on function public.sahara_review_directory(uuid,integer,text,jsonb,text,text,timestamptz) from public,anon;
grant execute on function public.sahara_review_directory(uuid,integer,text,jsonb,text,text,timestamptz) to authenticated;

create function healthathon_private.directory_history(p_id uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
begin
 if not healthathon_private.has_staff_role('directory_reviewer') then raise exception 'Directory reviewer access required.' using errcode='42501'; end if;
 return (select coalesce(jsonb_agg(to_jsonb(h) order by revision desc),'[]') from healthathon_private.directory_review_history h where submission_id=p_id);
end $$;
revoke all on function healthathon_private.directory_history(uuid) from public,anon;
grant execute on function healthathon_private.directory_history(uuid) to authenticated;
create function public.sahara_directory_history(p_id uuid) returns jsonb
language sql security invoker set search_path='' as $$select healthathon_private.directory_history(p_id)$$;
revoke all on function public.sahara_directory_history(uuid) from public,anon;
grant execute on function public.sahara_directory_history(uuid) to authenticated;

-- Narrow public projection needs a definer to read private reviews. It exposes
-- no contributor/contact/registration/receipt/reviewer/internal evidence fields.
create function public.sahara_directory_list() returns jsonb language sql stable security definer set search_path='' as $$
 select coalesce(jsonb_agg(jsonb_build_object('id',r.listing_id,'listing',r.listing,'reviewedAt',r.reviewed_at,'expiresAt',r.expires_at,'reportedAt',s.created_at) order by r.listing->>'name'),'[]')
 from healthathon_private.directory_reviews r join public.sahara_hospital_submissions s on s.id=r.submission_id
 where r.decision='approved' and r.expires_at>now()
$$;
revoke all on function public.sahara_directory_list() from public;
grant execute on function public.sahara_directory_list() to anon,authenticated;
commit;
