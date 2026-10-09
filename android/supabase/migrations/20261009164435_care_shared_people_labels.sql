create function healthathon_private.list_members(p_person uuid)
returns table(user_id uuid,can_edit boolean,email text)
language plpgsql stable security definer set search_path='' as $$
begin
 if auth.uid() is null or not healthathon_private.is_owner(p_person) then raise exception 'Only the owner can see the sharing list.' using errcode='42501'; end if;
 return query select m.user_id,m.can_edit,u.email::text from public.healthathon_members m join auth.users u on u.id=m.user_id where m.person_id=p_person order by u.email;
end $$;
revoke all on function healthathon_private.list_members(uuid) from public,anon;
grant execute on function healthathon_private.list_members(uuid) to authenticated;
create function public.healthathon_members_list(p_person uuid)
returns table(user_id uuid,can_edit boolean,email text)
language sql security invoker set search_path='' as $$ select * from healthathon_private.list_members(p_person) $$;
revoke all on function public.healthathon_members_list(uuid) from public,anon;
grant execute on function public.healthathon_members_list(uuid) to authenticated;
