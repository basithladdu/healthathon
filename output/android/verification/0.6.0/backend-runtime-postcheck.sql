-- READ ONLY: one JSON result for MCP; no account/patient rows or access changes.
begin read only;
select jsonb_build_object(
 'checked_at',now(),
 'rpcs',(select jsonb_agg(jsonb_build_object('signature',e.signature,'present',p.oid is not null,'public_security_definer_must_be_false',p.prosecdef,
 'authenticated_execute',case when p.oid is not null then has_function_privilege('authenticated',p.oid,'execute') end,
 'anon_execute_must_be_false',case when p.oid is not null then has_function_privilege('anon',p.oid,'execute') end,
 'arguments',pg_get_function_identity_arguments(p.oid)) order by e.signature)
 from unnest(array['public.healthathon_conversation_capabilities()','public.healthathon_conversation_candidates(uuid)','public.healthathon_conversation_scope_at(uuid,uuid)','public.healthathon_conversation_scope(uuid,uuid,bigint,uuid[],boolean)','public.healthathon_prescription_capabilities()','public.healthathon_prescription_snapshot(uuid)','public.healthathon_save_prescription(jsonb)','public.healthathon_save_draft(uuid,uuid,jsonb,bigint,boolean)','public.healthathon_review_draft(uuid,bigint,boolean)','public.healthathon_save_record(uuid,uuid,text,jsonb,bigint)']::text[]) e(signature) left join pg_proc p on p.oid=to_regprocedure(e.signature)),
 'tables',(select jsonb_agg(jsonb_build_object('name',e.name,'present',c.oid is not null,'rls',c.relrowsecurity,
 'policy_count',(select count(*) from pg_policy p where p.polrelid=c.oid),
 'anon_select_must_be_false',case when c.oid is not null then has_table_privilege('anon',c.oid,'select') end,
 'direct_client_write_must_be_false',case when c.oid is not null then has_table_privilege('authenticated',c.oid,'insert,update,delete') end) order by e.name)
 from unnest(array['healthathon_private.conversation_scopes','healthathon_private.conversation_audience','public.healthathon_prescriptions','public.healthathon_prescription_state','public.healthathon_prescription_medicines','public.healthathon_prescription_mutations']::text[]) e(name) left join pg_class c on c.oid=to_regclass(e.name)),
 'policies',(select jsonb_agg(to_jsonb(p) order by p.tablename,p.policyname) from pg_policies p where
 (p.schemaname='public' and p.tablename in ('healthathon_prescriptions','healthathon_prescription_state','healthathon_prescription_medicines','healthathon_prescription_mutations'))
 or (p.schemaname='public' and p.tablename='healthathon_records' and p.policyname in ('records_conversation_scope','records_read','records_remove'))),
 'triggers',(select jsonb_agg(jsonb_build_object('table',c.relname,'name',t.tgname,'enabled',t.tgenabled) order by t.tgname)
 from pg_trigger t join pg_class c on c.oid=t.tgrelid where not t.tgisinternal and t.tgname in ('require_new_note_context','prescription_entry_serialization','prescription_course_serialization')),
 'legacy_helpers',(select jsonb_agg(jsonb_build_object('signature',e.signature,'present',p.oid is not null,
 'authenticated_execute_must_be_false',case when p.oid is not null then has_function_privilege('authenticated',p.oid,'execute') end,
 'anon_execute_must_be_false',case when p.oid is not null then has_function_privilege('anon',p.oid,'execute') end) order by e.signature)
 from unnest(array['healthathon_private.valid_draft_before_context(jsonb)','healthathon_private.save_draft_before_context(uuid,uuid,jsonb,bigint,boolean)','healthathon_private.can_read_record_before_context(uuid,text,jsonb)','healthathon_private.save_record_before_context(uuid,uuid,text,jsonb,bigint)','healthathon_private.review_draft_before_context(uuid,bigint,boolean)','healthathon_private.review_receipts_before_context()']::text[]) e(signature) left join pg_proc p on p.oid=to_regprocedure(e.signature)),
 'partial_context_rejected',not coalesce(healthathon_private.valid_draft('{"source":"Synthetic compatibility check","contextJson":"{}"}'::jsonb),false),
 'legacy_six_field_draft_valid',healthathon_private.valid_draft('{"source":"Synthetic compatibility check","priorities":"","participants":"","topics":"","questions":"","nextSteps":""}'::jsonb)
) as postcheck;
rollback;
