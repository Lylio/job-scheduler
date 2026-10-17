alter table scheduled_jobs add column target_url varchar(1000);
alter table scheduled_jobs add column expected_status integer;
alter table scheduled_jobs add column timeout_seconds integer;
alter table job_executions add column target_url varchar(1000);
alter table job_executions add column expected_status integer;
alter table job_executions add column http_status integer;
alter table job_executions add column response_time_ms bigint;
alter table job_executions add column error_category varchar(40);
