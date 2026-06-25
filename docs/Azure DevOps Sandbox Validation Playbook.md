# Azure DevOps Sandbox Validation Playbook

## A. Purpose

This playbook is for Azure DevOps sandbox validation only. It is not a production rollout guide.

Use it to validate the current V1 behavior against a sandbox project before any production hardening work. The first validation with real Azure DevOps HTTP access must use:

```yaml
ado:
  organization: sandbox-org
  http-client-enabled: true
  dry-run: true
```

`ado.organization` is required when the HTTP client is enabled. Dry-run mode allows real webhook processing and real Azure DevOps reads. It suppresses Work Item PATCH requests and Work Item comment creation.

The successful write-enabled sandbox evidence, ADO discovery commands, and real integration findings are captured in [Azure DevOps Sandbox Validation Evidence](Azure%20DevOps%20Sandbox%20Validation%20Evidence.md).

## B. Pre-flight Safety Checklist

Complete every item before starting the app against Azure DevOps:

* Working tree is clean.
* `mvn test` passes locally.
* Application config uses only a sandbox organization and sandbox project.
* `ado.organization` is populated when `ado.http-client-enabled=true`.
* No production project is enabled in `ado.projects`.
* The `ado.projects` key matches the webhook project name exactly.
* Project names with spaces, dots, or special characters use Spring Boot YAML bracket notation.
* `ado.http-client-enabled=true`.
* `ado.dry-run=true`.
* PAT is sandbox-only.
* PAT is provided through `ADO_PERSONAL_ACCESS_TOKEN` or another local secret source, not committed config.
* Webhook shared secret is provided through `ADO_WEBHOOK_SHARED_SECRET` or another local secret source, not committed config.
* `webhook.shared-secret.enabled=true`.
* Sender or tunnel can provide the configured `X-ADO-Webhook-Secret` header.
* Custom fields exist in the sandbox process/template.
* Custom field reference names match config exactly.
* Bot identity email matches the account used by the PAT.
* Work Item Updated service hook points to a local tunnel or sandbox-hosted service.
* SQLite path points to a local sandbox database.
* Logs do not print PAT values, `Authorization` headers, full webhook payloads, raw field values, or full comment text.

## C. Required Sandbox ADO Setup

Prepare a disposable sandbox project with:

* `Test Case` Work Item type.
* Workflow states:
  * `Design`
  * `In Review`
  * Final approval state, for example `Approved` or `Approval`
* Custom fields:
  * Approved by SME
  * Approved by SQA
* Reversible business fields configured intentionally. Start narrow, then add only fields that should be protected.
* SME test user.
* SQA test user.
* Bot or service account for the PAT.
* Work Item Updated service hook scoped only to the sandbox project.

## D. Recommended Local Config

Use fake names in committed examples and real sandbox values only in local, uncommitted config.

The `ado.projects` key must match the Azure DevOps project name exactly. Use Spring Boot YAML bracket notation for project names with spaces, dots, or special characters:

```yaml
projects:
  "[Project Name With Spaces]":
    enabled: true
```

```yaml
ado:
  organization: sandbox-org
  personal-access-token: ${ADO_PERSONAL_ACCESS_TOKEN:}
  http-client-enabled: true
  dry-run: true
  projects:
    "[Example Sandbox Project 2.0]":
      enabled: true
      supported-work-item-types:
        - Test Case
      states:
        design: Design
        in-review: In Review
        approved: Approval
      fields:
        approved-by-sme: Custom.ApproverTech
        approved-by-sqa: Custom.ApproverTest
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

retry:
  max-attempts: 3
  default-backoff-seconds: 30
  respect-retry-after: true

idempotency:
  type: sqlite
  sqlite-path: ./data/sandbox-approval-bot.sqlite
  ttl-hours: 24
  max-records: 10000
```

## E. Local Run Commands

Run tests first:

```bash
mvn test
```

Set the PAT in Windows PowerShell:

```powershell
$env:ADO_PERSONAL_ACCESS_TOKEN = "<sandbox-token>"
$env:ADO_WEBHOOK_SHARED_SECRET = "<sandbox-shared-secret>"
```

Run the application:

```bash
mvn spring-boot:run
```

If the Azure DevOps service hook must reach your local machine, start a local tunnel and point the sandbox service hook to:

```text
POST /api/ado/webhooks/work-item-updated
```

Use a tunnel URL only for the sandbox hook.

When the sender or tunnel supports custom headers, send:

```text
X-ADO-Webhook-Secret: <sandbox-shared-secret>
```

Azure DevOps Service Hooks may not support arbitrary headers in every configuration. If your setup cannot send the header, keep the endpoint private, use tunnel access controls, or temporarily set `webhook.shared-secret.enabled=false` only for controlled local sandbox validation. Never use that fallback for production exposure.

## F. Dry-run Validation Scenarios

For all scenarios, expected no real ADO writes means no PATCH request and no Work Item comment creation should reach Azure DevOps. Dry-run write suppression logs come from `DryRunAdoClient` and look like:

```text
Dry-run would PATCH Work Item; suppressed ADO write ...
Dry-run would create comment; suppressed ADO write ...
```

| # | Scenario | Setup/action | Expected dry-run logs | Expected no real ADO writes |
|---|---|---|---|---|
| 1 | Bot-generated event is skipped | Update a sandbox Test Case using the bot/service account identity. | Classification/result shows `SKIPPED_BOT_EVENT` or reason `Event was generated by the bot.` No `would PATCH` or `would create comment`. | No PATCH. No comment. |
| 2 | Unsupported work item type is skipped | Send or trigger an update for a non-`Test Case` Work Item in the sandbox project. | Classification/result shows `SKIPPED_UNSUPPORTED_WORK_ITEM_TYPE` or reason `Work item type is unsupported.` No `would PATCH` or `would create comment`. | No PATCH. No comment. |
| 3 | Comment-only event does not patch/comment | Add a normal user comment without changing protected fields or approval state. | Classification is processable if the hook is valid, then processing result is skipped or patch skipped because the decision requires no visible action. No `would PATCH` or `would create comment`. | No PATCH. No comment. |
| 4 | Design -> In Review clears stale approvals | Put stale SME/SQA approval values on a Test Case, then move from `Design` to `In Review`. | Workflow decision indicates Design state clears approval fields. `would PATCH` appears with paths for `/rev` and the configured approval fields. Usually no `would create comment` for this cleanup. | No PATCH. No comment. |
| 5 | Design -> Approved returns to In Review and clears approvals | Move a Test Case directly from `Design` to `Approved`. | Workflow decision indicates invalid Approved state. `would PATCH` appears with `/fields/System.State` and configured approval field paths. `would create comment` appears if the decision includes a comment. | No PATCH. No comment. |
| 6 | SME review records SME approval | Have the configured SME user perform the approval action while SQA is not complete. | `would PATCH` appears with the configured SME approval field path. `would create comment` appears only if the decision includes a comment. | No PATCH. No comment. |
| 7 | SQA review after SME moves Test Case to Approved | Start from In Review with valid SME approval, then have the configured SQA user approve. | `would PATCH` appears with `/fields/System.State` and the configured SQA approval field path. `would create comment` appears if the decision includes a comment. | No PATCH. No comment. |
| 8 | Same user cannot complete SME and SQA | Configure or trigger an approval attempt where the same identity would satisfy both roles. | Workflow decision prevents dual-role completion. `would PATCH` appears only for the corrective fields required by the decision, and comment suppression appears if a comment would be created. | No PATCH. No comment. |
| 9 | Non-SME/SQA reversible content change would be reverted | Have a non-approved reviewer change a configured reversible business field. | Workflow decision indicates unauthorized reversible content changes were reverted. `would PATCH` appears with `/rev` and the changed reversible field paths. Operation paths are logged, raw field values are not. | No PATCH. No comment. |
| 10 | SME/SQA content change clears prior approvals and records current reviewer | Have an SME or SQA change a configured reversible business field after prior approvals exist. | Workflow decision indicates prior approvals are cleared and current reviewer is recorded. `would PATCH` appears with approval field paths and relevant reversible field handling. | No PATCH. No comment. |
| 11 | Invalid Approved state would return to In Review | Create an Approved Test Case with missing or invalid SME/SQA approval values, then trigger an update. | Workflow decision indicates invalid Approved state. `would PATCH` appears with `/fields/System.State` and approval field correction paths. Comment suppression appears if a comment would be created. | No PATCH. No comment. |
| 12 | Duplicate same project/workItemId/revision is idempotently skipped | Send the same webhook event twice for the same project, Work Item id, and revision. | First event processes normally. Second event logs duplicate detection with result `SKIPPED` and reason `Event was already processed.` | No extra PATCH. No extra comment. |
| 13 | Retryable ADO read failure is controlled | Temporarily trigger a sandbox read failure such as ADO 429, 5xx, or transport outage. | Processing result is `FAILED_RETRYABLE`; the event can be retried if it was not marked processed. | No PATCH. No comment. |

For every scenario, inspect:

* Webhook classification and processing result.
* Project, Work Item id, and revision.
* ADO fetch and revision fetch logs.
* Dry-run `would PATCH` operation count and operation paths when a patch is expected.
* Dry-run `would create comment` only when a comment is expected.
* Absence of PAT values, `Authorization` headers, full webhook payloads, full comment text, and raw field values.
* Absence of webhook shared-secret values.
* ADO paths show encoded spaces as `%20`, never double-encoded `%2520`.

## G. Dry-run Go/No-go Criteria

Go only if:

* All scenarios behave as expected.
* No production project was touched.
* Logs show expected classification and processing result.
* Dry-run logs show expected operation paths.
* No PAT, `Authorization` header, comment text, raw field value, or full webhook payload leaked.
* No webhook shared-secret value leaked.
* Idempotency duplicate behavior works.
* No unexpected PATCH or comment HTTP request reached Azure DevOps.

No-go if:

* Any scenario produces an unexpected state decision.
* Dry-run suppresses the wrong operations.
* Missing or extra field paths appear.
* A real write is detected during dry-run.
* Configured field names do not match Azure DevOps.
* Any secret appears in logs.
* The tunnel or sender cannot protect the public endpoint with either the shared-secret header or another access control.

## H. Controlled Write-enabled Sandbox Validation

Proceed only after dry-run passes.

Use the dedicated [Azure DevOps Write Enabled Sandbox Validation](Azure%20DevOps%20Write%20Enabled%20Sandbox%20Validation.md) playbook for the first controlled `ado.dry-run=false` run.

Keep the same sandbox organization, project, users, service hook, and local database unless there is a deliberate reason to reset idempotency. Change only:

```yaml
ado:
  dry-run: false
```

Do not add production projects. Start with one disposable Test Case. Run one scenario at a time and confirm PATCH/comment behavior in the Azure DevOps UI after each scenario. Re-enable dry-run immediately if unexpected behavior appears:

```yaml
ado:
  dry-run: true
```

## I. Rollback/Safety Response

If anything unexpected happens:

* Stop the app.
* Disable the sandbox service hook.
* Set `ado.dry-run=true`.
* Rotate the sandbox webhook shared secret if it may have been exposed.
* Inspect or clear the local sandbox SQLite idempotency database if necessary.
* Manually restore the sandbox Test Case if needed.
* Do not retry blindly.

## J. Idempotency Notes

The idempotency key is `project + workItemId + revision`. After an event reaches `COMPLETED` or `SKIPPED`, repeating the same project, Work Item id, and revision is skipped. To retest locally, create a new Azure DevOps revision or remove the local SQLite file configured by `idempotency.sqlite-path`.

## K. Known Limitations

V1 sandbox validation still has these limitations:

* No retry scheduler yet.
* No production hardening yet.
* No webhook signature verification yet.
* No Azure DevOps Graph group lookup.
* No attachments, links, or relations handling.
* No V2 workflows.
