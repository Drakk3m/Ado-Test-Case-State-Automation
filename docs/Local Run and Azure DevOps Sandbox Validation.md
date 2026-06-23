# Local Run and Azure DevOps Sandbox Validation

This project is sandbox validation ready. It is not production ready.

Use this guide to run the service locally and validate it only against an Azure DevOps sandbox organization or non-production project. Do not enable the HTTP client against a production Azure DevOps project first.

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
* Webhook authentication or signature verification.

## Required Local Prerequisites

Install or prepare:

* Java 21.
* Maven.
* Access to an Azure DevOps sandbox organization and sandbox project.
* A dedicated sandbox PAT with the minimum required Work Items permissions for read, update, and comments.
* Custom fields created in the sandbox process/template:
  * Approved by SME.
  * Approved by SQA.
* A service hook configured only against the sandbox project.
* A local tunnel only when testing real Azure DevOps service hooks against your local machine.

## Required Environment Variables

Use placeholders only in committed files. Set real values locally through your shell or secret manager.

```powershell
$env:ADO_PERSONAL_ACCESS_TOKEN = "<sandbox-token>"
```

For cmd.exe:

```bat
set ADO_PERSONAL_ACCESS_TOKEN=<sandbox-token>
```

For bash:

```bash
export ADO_PERSONAL_ACCESS_TOKEN="<sandbox-token>"
```

Never commit a real PAT.

## application.yml Example

The repository includes a sanitized sample at [sample-application-sandbox.yml](sample-application-sandbox.yml).

```yaml
ado:
  organization: sandbox-org
  personal-access-token: ${ADO_PERSONAL_ACCESS_TOKEN:}
  http-client-enabled: false
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

idempotency:
  type: sqlite
  sqlite-path: ./data/sandbox-approval-bot.sqlite
  ttl-hours: 24
  max-records: 10000
```

Keep `ado.http-client-enabled=false` until you are ready for sandbox HTTP validation. Enable it only after configuration, custom fields, service hook, and PAT scope have been checked.

## Local Run Commands

Run all tests:

```bash
mvn test
```

Run the Spring Boot app locally:

```bash
mvn spring-boot:run
```

On Windows PowerShell, set the PAT in the same terminal before starting the app:

```powershell
$env:ADO_PERSONAL_ACCESS_TOKEN = "<sandbox-token>"
mvn spring-boot:run
```

## Webhook Endpoint

Azure DevOps Work Item Updated service hooks should call:

```text
POST /api/ado/webhooks/work-item-updated
```

The controller is intentionally thin. It maps the incoming payload, resolves project configuration, and delegates to the processing pipeline.

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
* Confirm the PAT belongs to the bot/service account, not a human production account.
* Configure a Work Item Updated service hook only for the sandbox project.
* Point the service hook only to a local tunnel or sandbox-hosted service.
* Confirm no production project is enabled in `ado.projects`.
* Verify `ado.http-client-enabled` is still `false` until final sandbox readiness checks pass.

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
* Use a dedicated sandbox PAT.
* Use a dedicated sandbox project.
* Verify configured reversible fields carefully because those fields may be reverted.
* Verify approval custom field reference names before running.
* Do not include production projects in enabled config.
* Do not commit real secrets, real PAT values, private URLs, or credentials.

## What To Look For In Logs During Sandbox Validation

Logs are intended to make sandbox validation debuggable without exposing sensitive content. Look for:

* Webhook classification result, such as processable, skipped, or malformed.
* Skip or malformed reason.
* Project, Work Item id, and revision.
* Idempotency duplicate detection for repeated webhook revisions.
* ADO operation type, such as fetch, revision fetch, PATCH, or comment creation.
* PATCH result, including retryable versus non-retryable failure mapping.
* Comment result after a successful PATCH.
* `COMPLETED_WITH_WARNING` when PATCH succeeds but comment creation fails.

The logs should not contain:

* PAT values.
* `Authorization` headers.
* Full webhook payloads.
* Full comment text.
* Full raw field values.

## Troubleshooting

Startup fails due to missing PAT:

* Confirm `ADO_PERSONAL_ACCESS_TOKEN` is set in the same shell running the app.
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
* Manual scenarios were tested in sandbox.
* Logs were reviewed for no PAT leakage.
