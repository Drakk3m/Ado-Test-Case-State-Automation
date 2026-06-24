# Azure DevOps Write Enabled Sandbox Validation

## A. Purpose

Use this playbook only after the dry-run sandbox validation has passed. This is the first controlled Azure DevOps sandbox test with:

```yaml
ado:
  http-client-enabled: true
  dry-run: false
```

Write-enabled mode sends real Azure DevOps PATCH requests and may create real Work Item comments. Run it only against a sandbox Azure DevOps project and only with disposable Test Cases.

## B. Safety Warnings

Do not use this playbook against production.

Before starting:

* Use only a sandbox Azure DevOps organization and sandbox project.
* Use only disposable Test Cases that can be manually repaired or deleted.
* Confirm the configured project name exactly matches the webhook project name.
* Use Spring Boot YAML bracket notation for project names with spaces, dots, or special characters:

```yaml
ado:
  projects:
    "[Project Name With Spaces]":
      enabled: true
```

* Confirm approval field reference names before running.
* Confirm reversible business field reference names before running.
* Confirm `ado.dry-run=false` is never used accidentally in production config.
* Confirm `src/main/resources/application-local.yml` is untracked and ignored.
* Never commit `application-local.yml`, PAT values, webhook secrets, private tunnel URLs, or local company config.

## C. Pre-flight Checklist

Complete every item before enabling writes:

* `main` contains the latest merged fixes.
* Working branch was created from latest `main`.
* `mvn test` passes.
* `src/main/resources/application-local.yml` is untracked.
* `src/main/resources/application-local.yml` is ignored by `.gitignore`.
* `ADO_PERSONAL_ACCESS_TOKEN` is set in the environment, not committed.
* `ADO_WEBHOOK_SHARED_SECRET` is set in the environment, not committed.
* `ado.organization` is configured.
* `ado.http-client-enabled=true`.
* `ado.dry-run=false` is set only for this controlled write test.
* `ado.projects` contains only the sandbox project.
* Project key uses bracket notation if the sandbox project name needs it.
* Configured project key exactly matches the Azure DevOps webhook project name.
* `bot.identity-email` is not the same as any SME or SQA test user.
* The selected Test Case has at least revision 2, so current and previous revisions can be fetched.
* Local SQLite file/path is understood before testing idempotency.
* Service hook points only to a local tunnel or sandbox-hosted service.
* Service hook sends the configured shared-secret header, or the endpoint is otherwise protected for local sandbox validation.
* Local manually submitted webhook tests use [Invoke-SandboxWebhook.ps1](../tools/Invoke-SandboxWebhook.ps1), not hand-written JSON.

Recommended local write-enabled override:

```yaml
ado:
  organization: sandbox-org
  personal-access-token: ${ADO_PERSONAL_ACCESS_TOKEN:}
  http-client-enabled: true
  dry-run: false
  projects:
    "[Example Sandbox Project 2.0]":
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
          - Microsoft.VSTS.TCM.LocalDataSource
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
```

## D. Evidence To Capture

For every scenario, capture:

* ADO field values before the test.
* Logs from `Received ADO webhook event` through `processing completed`.
* ADO field values after the test.
* Processing result, such as `COMPLETED`, `SKIPPED`, `FAILED_RETRYABLE`, or `FAILED_NON_RETRYABLE`.
* PATCH operation paths from logs when a PATCH is expected.
* Work Item comment result if a comment is expected.

Verify logs do not contain:

* PAT values.
* `Authorization` headers.
* Webhook shared-secret values.
* Full webhook payloads.
* Raw field values.
* Full comment text.

## E. Local Webhook Helper

Use [Invoke-SandboxWebhook.ps1](../tools/Invoke-SandboxWebhook.ps1) to submit local webhook test payloads. The helper posts to `http://localhost:8080/api/ado/webhooks/work-item-updated`, sets `X-ADO-Webhook-Secret`, prints the HTTP response status/body, and does not read PATs or modify application config.

Dry-run SME example:

```powershell
.\tools\Invoke-SandboxWebhook.ps1 `
  -ProjectName "Project Name With Spaces" `
  -Organization "ExampleOrg" `
  -WorkItemId 12345 `
  -Revision 4 `
  -OldState "In Review" `
  -NewState "In Review" `
  -ChangedByEmail "sme@example.com" `
  -ChangedByDisplayName "SME Sandbox" `
  -SharedSecret $env:ADO_WEBHOOK_SHARED_SECRET
```

Dry-run SQA example:

```powershell
.\tools\Invoke-SandboxWebhook.ps1 `
  -ProjectName "Project Name With Spaces" `
  -Organization "ExampleOrg" `
  -WorkItemId 12345 `
  -Revision 5 `
  -OldState "In Review" `
  -NewState "In Review" `
  -ChangedByEmail "sqa@example.com" `
  -ChangedByDisplayName "SQA Sandbox" `
  -SharedSecret $env:ADO_WEBHOOK_SHARED_SECRET
```

Write-enabled warning example:

```powershell
# Run only after dry-run validation passed and application-local.yml intentionally has ado.dry-run=false.
.\tools\Invoke-SandboxWebhook.ps1 `
  -ProjectName "Project Name With Spaces" `
  -Organization "ExampleOrg" `
  -WorkItemId 12345 `
  -Revision 6 `
  -OldState "In Review" `
  -NewState "In Review" `
  -ChangedByEmail "sme@example.com" `
  -ChangedByDisplayName "SME Sandbox" `
  -SharedSecret $env:ADO_WEBHOOK_SHARED_SECRET
```

Duplicate revision example:

```powershell
# Re-run the same command after a completed or skipped result to validate idempotency.
.\tools\Invoke-SandboxWebhook.ps1 `
  -ProjectName "Project Name With Spaces" `
  -Organization "ExampleOrg" `
  -WorkItemId 12345 `
  -Revision 6 `
  -OldState "In Review" `
  -NewState "In Review" `
  -ChangedByEmail "sme@example.com" `
  -ChangedByDisplayName "SME Sandbox" `
  -SharedSecret $env:ADO_WEBHOOK_SHARED_SECRET
```

## F. Manual Test Scenarios

Run one scenario at a time. After each scenario, inspect Azure DevOps UI before continuing.

### A. SME Approval Write

Setup:

* Work Item type is `Test Case`.
* Work Item state is `In Review`.
* Current revision is at least 2.
* `ChangedBy` is the configured SME user.
* The configured SME approval field is blank or has a known disposable value.

Expected:

* Webhook is accepted.
* Shared secret is validated.
* Classification is processable.
* Current Work Item and previous revision are fetched.
* PATCH paths include `/rev`.
* PATCH paths include the configured SME approval field path, for example `/fields/Custom.ApprovedBySME`.
* Azure DevOps shows the SME approval field changed.
* Logs do not include raw field values or full comment text.

### B. SQA Approval Write

Setup:

* Work Item type is `Test Case`.
* Work Item state is `In Review`.
* Current revision is at least 2.
* `ChangedBy` is the configured SQA user.
* The configured SQA approval field is blank or has a known disposable value.

Expected:

* Webhook is accepted.
* Shared secret is validated.
* Classification is processable.
* Current Work Item and previous revision are fetched.
* PATCH paths include `/rev`.
* PATCH paths include the configured SQA approval field path, for example `/fields/Custom.ApprovedBySQA`.
* Azure DevOps shows the SQA approval field changed.
* Logs do not include raw field values or full comment text.

### C. Both Approvals Valid And Different Users

Setup:

* Work Item type is `Test Case`.
* Work Item state is `In Review`.
* SME and SQA users are different configured users.
* Existing approval field values are either blank or set to the expected configured users for the scenario.
* Trigger the second approval event using the role that is still missing.

Expected:

* Workflow accepts different SME and SQA users.
* PATCH paths include `/rev`.
* PATCH paths include the missing approval field.
* If the current workflow rules determine both approvals are valid, PATCH paths include `/fields/System.State` and Azure DevOps state transitions to `Approved`.
* If the current workflow rules require another corrective action, Azure DevOps reflects that decision exactly.
* No unexpected reversible business field writes occur.

### D. Bot-generated Event

Setup:

* `ChangedBy` equals `bot.identity-email`.
* Work Item belongs to the configured sandbox project.

Expected:

* Classification result is `SKIPPED_BOT_EVENT`.
* No PATCH request is sent.
* No Work Item comment is created.
* No write loop occurs from bot-authored changes.

### E. Duplicate Revision

Setup:

* Complete or skip one event for a known `project + workItemId + revision`.
* Re-send the same webhook payload for the same project, Work Item id, and revision.

Expected:

* Duplicate delivery is idempotently skipped.
* Processing result is `SKIPPED`.
* Reason indicates the event was already processed.
* No extra PATCH request is sent.
* No extra Work Item comment is created.

To retest the same workflow locally, create a new Azure DevOps revision or intentionally remove the local SQLite file configured by `idempotency.sqlite-path`.

### F. ADO Read Retryable Failure

Setup:

* Temporarily trigger a retryable ADO read failure, such as ADO 429, ADO 5xx, or a transport failure.
* Use a payload that has not already been marked processed.

Expected:

* Processing result is `FAILED_RETRYABLE`.
* Event is not marked processed.
* No PATCH request is sent.
* No Work Item comment is created.
* Re-sending the same payload is valid after the read path is healthy.

## G. Rollback And Cleanup

After the write-enabled test:

* Set `ado.dry-run=true` immediately.
* Stop the app if any unexpected write occurs.
* Disable the sandbox service hook or local tunnel if needed.
* Manually revert fields in the Azure DevOps sandbox if necessary.
* Remove the local SQLite file only when intentionally retesting the same revisions locally.
* Keep `src/main/resources/application-local.yml` untracked.
* Never commit `application-local.yml`, PAT values, webhook secrets, private tunnel URLs, or local company config.

## H. Go / No-go

Go only if:

* Dry-run validation already passed.
* The selected Test Cases are disposable.
* Expected PATCH paths match the workflow decision.
* Azure DevOps field values change only as expected.
* Logs contain no PAT, `Authorization` header, webhook secret, raw field values, full payloads, or full comment text.

No-go if:

* Any production project or non-disposable Work Item is configured.
* `ado.dry-run=false` appears in committed config.
* Project key does not exactly match the webhook project name.
* Field reference names are uncertain.
* A bot-generated event causes a write loop.
