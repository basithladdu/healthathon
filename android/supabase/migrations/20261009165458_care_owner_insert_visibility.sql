drop policy people_read on public.healthathon_people;
create policy people_read on public.healthathon_people for select to authenticated using (owner_id=(select auth.uid()) or healthathon_private.can_read(id));
