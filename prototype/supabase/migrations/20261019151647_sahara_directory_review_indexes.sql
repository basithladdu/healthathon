-- Cover account-reference checks and review provenance lookup without changing access.
begin;
create index directory_reviews_reviewer on healthathon_private.directory_reviews(reviewer_id);
create index directory_review_history_reviewer on healthathon_private.directory_review_history(reviewer_id);
commit;
