# Candidate migration operational rollback

Migration V18 is additive and requires a database backup before deployment. If validation fails before new writes are accepted, roll back the application, remove the `fk_interviews_candidate` constraint and `candidate_id` column, then drop `candidate_timeline_events`, `candidate_vacancies`, and `candidates` in that order. Remove V18 from `flyway_schema_history` only after confirming that the schema matches V17.

After candidate writes begin, do not drop data. Disable writes, export the three candidate tables, restore the pre-deployment backup, and replay interviews created after the backup through the supported API. The deterministic backfill key is company ID plus normalized candidate name, so rerunning V18 recreates the same legacy grouping without adding PII.
