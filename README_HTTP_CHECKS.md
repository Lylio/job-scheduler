# Milestone 2 — real HTTP health checks

This update adds `HTTP_HEALTH_CHECK` alongside the existing demo tasks. It sends a real GET request, compares the returned status with the configured expected status, and records response time to headers. It does not read, store, or validate response bodies.

## Apply to your existing project

The update ZIP contains only changed/new application files. It intentionally omits your root README.md, compose.yml, Git history, screenshots, and database files. Extract its contents into the existing `~/workspace/job-scheduler` directory, allowing the supplied source files to replace their earlier versions.

For a ZIP downloaded to `~/Downloads`:

```bash
unzip -o ~/Downloads/job-scheduler-http-checks-update.zip -d ~/workspace/job-scheduler
cd ~/workspace/job-scheduler
docker compose up --build -d
```

No database reset or `down -v` is needed. Flyway applies the additive V2 migration automatically. Existing job definitions and execution history remain. Your working Compose configuration stays in place:

| Service | Host port |
|---|---|
| Frontend | 23473 |
| Backend | 28473 |
| PostgreSQL | 25473 |

Open http://localhost:23473. Refresh the browser if it has retained an older page.

## First real check

1. Click **Create job**. The default type is now **HTTP health check**.
2. Name: `Scheduler backend health`.
3. Target URL: `http://backend:8080/actuator/health`.
4. Expected HTTP status: `200`; timeout: `5` seconds.
5. Select **Every minute**, then create the job.
6. Click **Run now** and open its execution details. You should see the actual HTTP status and response time.

This URL reaches the running backend through Docker's service network, so it checks a real endpoint without relying on internet access. You can then add your website's final HTTPS URL, or another reachable service endpoint.

Checks originate from the backend container, not your browser. `localhost` in a check URL refers to that container. Use Docker service names for services in the same Compose network; use a network address reachable from the container for other services.

## Behaviour

- Default expectation: HTTP 200. An exact match is SUCCESS; any other returned status is FAILED with error category `HTTP_STATUS`.
- Timeout: 1–30 seconds; default 5. It bounds waiting for a connection and response headers.
- Redirects are not followed. A 301/302 is recorded as the actual status, so configure the final URL or explicitly expect a redirect status.
- Response time uses a monotonic clock and measures time until response headers are available. Full job duration is recorded separately.
- DNS, connection, TLS, timeout, and other network failures have distinct error categories. When there is no HTTP response, status and response time remain null; execution duration still shows time spent attempting the check.
- Each execution snapshots its target URL and expected status. Existing demo executions retain null HTTP fields.
- URLs must use HTTP/HTTPS and may not include embedded credentials or fragments. Private/local targets are allowed for local service monitoring. Use this unauthenticated development app only in the existing trusted local environment.
- Requests use GET, standard TLS verification, no cookies, no credentials, and no configurable authentication headers. There are no automatic retries in this milestone.
- Existing pause/resume, cron scheduling, manual execution, history, and same-job serialization apply to HTTP jobs too.

## API example

```bash
curl -i -X POST http://localhost:28473/api/jobs \
  -H 'Content-Type: application/json' \
  -d '{"name":"Scheduler backend health","taskType":"HTTP_HEALTH_CHECK","cronExpression":"0 * * * * ?","zoneId":"Europe/London","targetUrl":"http://backend:8080/actuator/health","expectedStatus":200,"timeoutSeconds":5}'
```

`targetUrl` is required for HTTP jobs. `expectedStatus` and `timeoutSeconds` can be omitted to use defaults. The original demo-job API payloads remain valid.

Job responses include target configuration. Execution responses now include `targetUrl`, `expectedStatus`, `httpStatus`, `responseTimeMs`, and `errorCategory`.

## Verification

```bash
mvn -B -f backend/pom.xml verify
npm ci --prefix frontend
npm run build --prefix frontend
```

The backend tests run a real local HTTP server and cover success, unexpected status, custom expected status, timeout, connection refusal, redirects, and a stalled response body. They also verify API validation/defaults, HTTP result persistence, migration of existing rows from V1, and all original scheduler behaviours. They require no public internet endpoints.

The scheduler still runs as a single backend instance and rebuilds its Quartz RAM schedules from PostgreSQL at startup. This milestone does not introduce clustered scheduling or durable Quartz queues.

Suggested commit message:

```text
feat: add configurable HTTP health checks and response metrics
```
