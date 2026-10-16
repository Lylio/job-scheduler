create table scheduled_jobs (
 id uuid primary key,
 name varchar(100) not null,
 task_type varchar(40) not null,
 cron_expression varchar(100) not null,
 zone_id varchar(80) not null,
 enabled boolean not null,
 created_at timestamp with time zone not null
);
create table job_executions (
 id uuid primary key,
 job_id uuid not null references scheduled_jobs(id),
 job_name varchar(100) not null,
 status varchar(20) not null,
 source varchar(20) not null,
 started_at timestamp with time zone not null,
 finished_at timestamp with time zone,
 duration_ms bigint,
 message varchar(2000)
);
create index idx_execution_started on job_executions(started_at desc);
create index idx_execution_job on job_executions(job_id, started_at desc);
