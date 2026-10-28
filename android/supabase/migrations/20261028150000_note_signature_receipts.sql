-- OWNER APPLIES. Not applied by the sign-off lane (28 Oct 2026). The app does not call this table yet.
-- Goals-of-care sign-off receipts for shared care. The signature IMAGE stays on the signing phone;
-- only its SHA-256 and the binding to one confirmation of one exact note version are shared, so
-- family and clinicians on the care team can see "Signed by <name> · <role> · <time>".
-- This is an acknowledgement, not a legal signature. No government ID is stored.
-- Append-only: no update or delete policy. Rows cascade with the person.
begin;

create table public.healthathon_note_signatures (
  id uuid primary key,
  person_id uuid not null references public.healthathon_people(id) on delete cascade,
  ack_id uuid not null,
  version_id uuid not null,
  content_hash text not null check (content_hash ~ '^[0-9a-f]{64}$'),
  signer_name text not null check (length(btrim(signer_name)) between 1 and 100),
  relationship text not null check (relationship in ('Patient','Family / carer')),
  kind text not null check (kind in ('drawn','photo')),
  image_sha256 text not null check (image_sha256 ~ '^[0-9a-f]{64}$'),
  binding_sha256 text not null check (binding_sha256 ~ '^[0-9a-f]{64}$'),
  scheme text not null check (scheme = 'sahara-note-signature-v1'),
  signed_at bigint not null check (signed_at > 0),
  actor_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);
create index healthathon_note_signatures_person on public.healthathon_note_signatures(person_id, version_id);

alter table public.healthathon_note_signatures enable row level security;
revoke all on public.healthathon_note_signatures from public, anon, authenticated;
grant select, insert on public.healthathon_note_signatures to authenticated;

create policy note_signatures_read on public.healthathon_note_signatures for select to authenticated
  using (healthathon_private.can_read(person_id));

-- Only the account that saved the confirmation may add its signature, and only for the same
-- version id and content hash that confirmation was made on.
create policy note_signatures_add on public.healthathon_note_signatures for insert to authenticated
  with check (
    actor_id = (select auth.uid())
    and healthathon_private.can_read(person_id)
    and exists (
      select 1 from public.healthathon_records a
      where a.kind = 'ack' and a.id = healthathon_note_signatures.ack_id and a.person_id = healthathon_note_signatures.person_id
        and coalesce(nullif(a.data->>'versionId',''), a.id::text) = healthathon_note_signatures.version_id::text
        and a.data->>'contentHash' = healthathon_note_signatures.content_hash
        and a.data->>'relationship' = healthathon_note_signatures.relationship
        and lower(btrim(a.data->>'name')) = lower(btrim(healthathon_note_signatures.signer_name))
        and a.data->>'actorId' = (select auth.uid())::text
    )
  );

commit;
