# Local Run and Azure DevOps Sandbox Validation

This project is sandbox validation ready. It is not production ready.

Use this guide to run the service locally and validate it only against an Azure DevOps sandbox organization or non-production project. Do not enable the HTTP client against a production Azure DevOps project first.

For the first real dry-run validation and the later controlled write-enabled sandbox test, follow the focused [Azure DevOps Sandbox Validation Playbook](Azure%20DevOps%20Sandbox%20Validation%20Playbook.md). A concise checkbox version is available in [Sandbox Validation Checklist](sandbox-validation-checklist.md).

## Current Project Status

V1 currently supports:

* Azure DevOps Test Case work items.
* Design, In Review, and Approved states.
* Configured SME and SQA approval fields.
* Configured reversible business fields only.
* JSON Patch corrections with `/rev` test operations.
* Azure DevOps Work Item Comments API comments.
* SQLite or in-memory idempotency.
* Per Work Item queue serialization.

The webhook is only a trigger. Azure DevOps REST reads are treated as the source of truth before workflow decisions are made.

## Intentionally Out Of Scope

V1 does not implement:

* Attachments, links, or relations protection.
* Ignored-fields configuration.
* Azure DevOps Graph groups.
* Advanced HTML/XML normalization.
* Retry scheduler, retry dashboard, or background backoff execution.
* V2 workflows.
* Full webhook signature verification, OAuth, JWT, or role-based authorization.

## Required Local Prerequisites

Install or prepare:

* Java 21.
* Maven.
* Access to an Azure DevOps sandbox organization and sandbox project.
* A dedicated sandbox PAT with the minimum required Work Items permissions for read, update, and comments when `ado.http-client-enabled=true`.
* Custom fields created in the sandbox process/template:
  * Approved by SME.
  * Approved by SQA.
* A service hook configured only against the sandbox project.
* A local tunnel only when testing real Azure DevOps service hooks against your local machine.

## Required Environment Variables

Use placeholders only in committed files. Set real values locally through your shell or secret manager.
`ADO_PERSONAL_ACCESS_TOKEN` is required only when `ado.http-client-enabled=true`.
`ADO_WEBHOOK_SHARED_SECRET` is required when `webhook.shared-secret.enabled=true`.

```powershell
$env:ADO_PERSONAL_ACCESS_TOKEN = "<sandbox-token>"
$env:ADO_WEBHOOK_SHARED_SECRET = "<sandbox-shared-secret>"
```

For cmd.exe:

```bat
set ADO_PERSONAL_ACCESS_TOKEN=<sandbox-token>
set ADO_WEBHOOK_SHARED_SECRET=<sandbox-shared-secret>
```

For bash:

```bash
export ADO_PERSONAL_ACCESS_TOKEN="<sandbox-token>"
export ADO_WEBHOOK_SHARED_SECRET="<sandbox-shared-secret>"
```

Never commit a real PAT.
Never commit a real webhook shared secret.

## application.yml Example

The repository includes a sanitized sample at [sample-application-sandbox.yml](sample-application-sandbox.yml).

```yaml
ado:
  organization: sandbox-org
  personal-access-token: ${ADO_PERSONAL_ACCESS_TOKEN:}
  http-client-enabled: false
  dry-run: true
  projects:
    SandboxProject:
      enabled: true
      supported-work-item-types:
        - Test Case
      fields:
        approved-by-sme: Custom.ApprovedBySME
        approved-by-sqa: Custom.ApprovedBySQA
        reversible-business-fields:
          - System.Title
          - System.Description
          - Microsoft.VSTS.TCM.Steps
      approvals:
        sme-users:
          - sme.user@example.test
        sqa-users:
          - sqa.user@example.test

bot:
  identity-email: approval-bot@example.test

webhook:
  shared-secret:
    enabled: true
    header-name: X-ADO-Webhook-Secret
    value: ${ADO_WEBHOOK_SHARED_SECRET:}

idempotency:
  type: sqlite
  sqlite-path: ./data/sandbox-approval-bot.sqlite
  ttl-hours: 24
  max-records: 10000
```

Keep `ado.http-client-enabled=false` until you are ready for sandbox HTTP validation. Enable it only after configuration, custom fields, service hook, and PAT scope have been checked.

Dry-run mode is enabled by default through `ado.dry-run=true`. When the HTTP client is enabled, dry-run still fetches Work Items and revisions from Azure DevOps, still computes workflow decisions, and still builds patch operations and comment text internally. It suppresses ADO PATCH requests and Work Item comment creation.

For the first HTTP sandbox validation step, use:

```yaml
ado:
  http-client-enabled: true
  dry-run: true
```

Only set `ado.dry-run=false` after the dry-run logs match the expected behavior for your sandbox Test Cases.

## Local Run Commands

Run all tests:

```bash
mvn test
```

Run the Spring Boot app locally:

```bash
mvn spring-boot:run
```

On Windows PowerShell, set the needed secrets in the same terminal before starting the app. The PAT is required when `ado.http-client-enabled=true`; the webhook shared secret is required when `webhook.shared-secret.enabled=true`:

```powershell
$env:ADO_PERSONAL_ACCESS_TOKEN = "<sandbox-token>"
$env:ADO_WEBHOOK_SHARED_SECRET = "<sandbox-shared-secret>"
mvn spring-boot:run
```

## Webhook Endpoint

Azure DevOps Work Item Updated service hooks should call:

```text
POST /api/ado/webhooks/work-item-updated
```

The controller is intentionally thin. It maps the incoming payload, resolves project configuration, and delegates to the processing pipeline.

When `webhook.shared-secret.enabled=true`, requests must include the configured shared-secret header before the controller maps or delegates the payload:

```text
X-ADO-Webhook-Secret: <sandbox-shared-secret>
```

Configure the same header in the tunnel/test sender when possible. Azure DevOps Service Hooks may not support arbitrary headers in every configuration; if your setup cannot send this header, keep the endpoint private, use tunnel access controls, or temporarily set `webhook.shared-secret.enabled=false` only for controlled local sandbox validation. Never disable this gate for production exposure.

## Azure DevOps Sandbox Setup Checklist

Before enabling real HTTP validation:

* Create a sandbox project or use a non-production project.
* Create or confirm the custom field reference names for:
  * Approved by SME.
  * Approved by SQA.
* Confirm the workflow states exist:
  * Design.
  * In Review.
  * Approved.
* Create at least one sandbox Test Case.
* Configure at least one SME user.
* Configure at least one SQA user.
* Configure the bot identity email.
* Create a dedicated sandbox PAT for the bot/service account.
* Create a dedicated sandbox webhook shared secret.
* Confirm the PAT belongs to the bot/service account, not a human production account.
* Configure a Work Item Updated service hook only for the sandbox project.
* Point the service hook only to a local tunnel or sandbox-hosted service.
* Confirm no production project is enabled in `ado.projects`.
* Verify `ado.http-client-enabled` is still `false` until final sandbox readiness checks pass.
* Use `ado.http-client-enabled=true` with `ado.dry-run=true` as the first ADO HTTP validation mode.
* Set `ado.dry-run=false` only after dry-run logs show the expected PATCH and comment actions.
* Verify `webhook.shared-secret.enabled=true` and the sender/tunnel includes `X-ADO-Webhook-Secret`.

## Manual Validation Scenarios

Run these with sandbox Test Cases only:

* Comment-only or non-actionable update should not PATCH or comment.
* Design to In Review clears stale SME/SQA approvals.
* Design to Approved returns the Test Case to In Review and clears approvals.
* SME approval records the SME approval field.
* SQA approval after SME approval moves the Test Case to Approved.
* The same user cannot fill both SME and SQA approvals.
* Non-SME/SQA reversible content change is reverted.
* SME/SQA reversible content change clears previous approvals and records the current reviewer.
* Approved with invalid or missing approvals returns to In Review.
* Bot-generated event is skipped.
* Duplicate webhook for the same project, Work Item id, and revision is idempotently skipped.

## Safety Warnings

* Never enable this first against a production Azure DevOps project.
* Keep `ado.http-client-enabled=false` until ready for sandbox HTTP validation.
* Keep `ado.dry-run=true` for the first sandbox HTTP validation pass.
* Use a dedicated sandbox PAT.
* Use a dedicated sandbox project.
* Verify configured reversible fields carefully because those fields may be reverted.
* Verify approval custom field reference names before running.
* Do not include production projects in enabled config.
* Do not commit real secrets, real PAT values, private URLs, or credentials.
* Do not log or share the webhook shared secret.

## What To Look For In Logs During Sandbox Validation

Logs are intended to make sandbox validation debuggable without exposing sensitive content. Look for:

* Webhook classification result, such as processable, skipped, or malformed.
* Shared-secret validation failures by category only, such as missing header or invalid header.
* Skip or malformed reason.
* Project, Work Item id, and revision.
* Idempotency duplicate detection for repeated webhook revisions.
* ADO operation type, such as fetch, revision fetch, PATCH, or comment creation.
* Dry-run suppressed write logs, such as `would PATCH`/suppressed PATCH and `would create comment`/suppressed comment creation.
* PATCH result, including retryable versus non-retryable failure mapping.
* Comment result after a successful PATCH.
* `COMPLETED_WITH_WARNING` when PATCH succeeds but comment creation fails.

The logs should not contain:

* PAT values.
* Webhook shared-secret values.
* `Authorization` headers.
* Full webhook payloads.
* Full comment text.
* Full raw field values.

When `ado.http-client-enabled=true` and `ado.dry-run=true`, expect ADO fetch logs plus suppressed write logs. You should see operation counts and patch paths, but not patch values or comment bodies.

## Troubleshooting

Startup fails due to missing PAT:

* Confirm `ADO_PERSONAL_ACCESS_TOKEN` is set in the same shell running the app.
* Confirm this is expected only when `ado.http-client-enabled=true`.
* Keep the property value as `${ADO_PERSONAL_ACCESS_TOKEN:}` in committed config.

Startup fails due to invalid project config:

* Check project names, enabled flags, supported work item types, custom field reference names, SME/SQA users, and bot identity email.

Service hook is received but skipped:

* Confirm the project is enabled.
* Confirm the Work Item type is `Test Case`.
* Confirm the event was not generated by the configured bot identity.

ADO returns 401 or 403:

* Confirm the PAT is valid, unexpired, and belongs to the sandbox bot/service account.
* Confirm PAT permissions allow Work Items read/update and comments in the sandbox project.

ADO returns 404:

* Confirm organization, project, Work Item id, and revision exist in the sandbox.
* Confirm the service hook is not sending events from a different project.

PATCH conflict or revision mismatch:

* ADO rejected the `/rev` test because the Work Item changed. V1 maps this as retryable but does not run a retry scheduler.

Comment failure produces completed-with-warning:

* PATCH succeeded, comment creation failed, and V1 does not roll back or retry the comment.

SQLite database path issues:

* Confirm the parent directory exists or can be created by the app process.
* For first sandbox runs, use a local path such as `./data/sandbox-approval-bot.sqlite`.

Local tunnel URL changed:

* Update the Azure DevOps service hook URL.
* Send a test event from the service hook configuration before manual workflow validation.

## PR Checklist

Before asking for review or running against sandbox:

* `mvn test` passes.
* No real secrets are committed.
* Config samples use placeholders only.
* Only a sandbox project is enabled.
* `ado.http-client-enabled=false` by default unless explicitly testing sandbox HTTP.
* `ado.dry-run=true` for the first HTTP sandbox validation run.
* Manual scenarios were tested in sandbox.
* Logs were reviewed for no PAT leakage.
