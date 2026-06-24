# Pre-Sandbox Readiness Checklist

Use this before the first real Azure DevOps sandbox dry-run.

## Git And Local Files

- Branch is based on latest `main`.
- Working tree is clean before starting validation.
- No `.env`, tunnel config, PAT, webhook secret, `.db`, `.sqlite`, journal, WAL, or SHM files are tracked.
- `.gitignore` covers local runtime and SQLite files.

## Configuration

- `ado.http-client-enabled=true` only for sandbox HTTP validation.
- `ado.dry-run=true` for the first sandbox run.
- `ADO_PERSONAL_ACCESS_TOKEN` is set only when the HTTP client is enabled.
- `webhook.shared-secret.enabled=true`.
- `ADO_WEBHOOK_SHARED_SECRET` is set when shared-secret validation is enabled.
- `ado.projects` contains only the sandbox project.
- No production organization, project, PAT, webhook secret, or private tunnel URL is committed.

## Webhook Boundary

- The sender or tunnel includes `X-ADO-Webhook-Secret`.
- Missing or wrong shared-secret headers return `401 Unauthorized`.
- Rejected webhook requests do not reach the processing pipeline.
- Logs show only validation failure categories, not secret values.

## Dry-Run Safety

- Dry-run delegates ADO reads.
- Dry-run suppresses Work Item PATCH requests.
- Dry-run suppresses Work Item comment creation.
- Dry-run logs operation paths only, not raw field values.
- Dry-run logs comment suppression without comment bodies.

## ADO Write Safety

- PATCH uses `application/json-patch+json`.
- PATCH starts with `/rev` test.
- PATCH uses `replace` with `null` for clears.
- PATCH rejects `remove`.
- Comments use the Work Item Comments API, not `System.History`.
- Comment creation is skipped if PATCH fails.
- Comment failure after PATCH success is treated as completed with warning.

## Go / No-Go

- Go only if `mvn test` passes and logs contain no PAT, `Authorization`, webhook secret, raw field value, full payload, or full comment text.
- No-go if any production project is enabled, any real secret is committed, the public endpoint cannot be protected, or dry-run shows unexpected write intent.
- Roll back by stopping the app, disabling the service hook or tunnel, rotating any exposed sandbox secrets, and keeping `ado.dry-run=true`.
