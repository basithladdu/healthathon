-- Cover only new synthetic-workspace foreign keys flagged by post-deploy advisors.
begin;
create index doctor_notes_patient_dataset on public.sahara_doctor_notes(patient_id,dataset_id);
create index doctor_notes_author on public.sahara_doctor_notes(author_id);
create index doctor_doses_patient_dataset on public.sahara_doctor_doses(patient_id,dataset_id);
create index doctor_doses_reporter on public.sahara_doctor_doses(reporter_id);
create index doctor_files_patient_dataset on public.sahara_doctor_files(patient_id,dataset_id);
create index doctor_files_uploader on public.sahara_doctor_files(uploader_id);
commit;
