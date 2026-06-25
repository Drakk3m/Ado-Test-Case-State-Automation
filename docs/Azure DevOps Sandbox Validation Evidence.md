# Azure DevOps Sandbox Validation Evidence

## Purpose

This document captures the successful Azure DevOps sandbox validation evidence for the V1 approval automation workflow and the real integration findings discovered during write-enabled testing.

It is documentation only. It does not define new workflow behavior, HTTP behavior, UI behavior, or runtime configuration reload behavior.

## Validated In Sandbox

The following facts were validated against the Azure DevOps sandbox:

* Organization: `STMN-Group`.
* Project: `ADOnis 2.0 Test Project`.
* Work Item type: `Test Case`.
* Visible approval fields used by the ADO UI:
  * `Custom.ApproverTech`.
  * `Custom.ApproverTest`.
* Earlier assumed fields were not the correct visible approval fields for this sandbox:
  * `Custom.Dev_SME`.
  * `Custom.SQA_SME`.
* Approval identity/person fields rejected JSON Patch `replace` with `null`.
* Approval identity/person field clearing succeeded with JSON Patch `replace` and an empty string value.
* The sandbox final approved state is `Approval`.
* PATCH succeeded for the final state transition.
* Comment creation succeeded after switching the Comments API to `api-version=7.1-preview`.
* Final end-to-end processing result was `COMPLETED`.

The successful end-to-end flow observed was:

* Webhook event received.
* Event classified as processable.
* Current Work Item loaded from ADO.
* Previous revision loaded from ADO.
* Approval fields validated.
* Invalid approval fields cleaned.
* `System.State` patched successfully.
* Work Item comment created successfully.
* Idempotency marked the event processed.
* Final processing result `COMPLETED`.

Successful partial behavior was also observed:

* PATCH succeeds but comment creation fails.
* The result maps to `COMPLETED_WITH_WARNING`.
* No rollback is attempted.

## Required By V1 Specification

V1 requires:

* The webhook is a trigger; ADO REST reads are the source of truth.
* JSON Patch starts with a `/rev` test.
* JSON Patch uses `replace`; `remove` is not used.
* Work Item GET, revision GET, and PATCH use `api-version=7.1`.
* Comments API uses `api-version=7.1-preview`.
* Idempotency key is `project + workItemId + revision`.
* Repeating the same revision after `COMPLETED` or `SKIPPED` is skipped.
* PATCH failure prevents comment creation.
* Comment failure after PATCH success maps to `COMPLETED_WITH_WARNING`.

## Configurable Per Project

State names are configurable per project:

* `design`.
* `in-review`.
* `approved`.

Default V1 state names remain:

```yaml
states:
  design: Design
  in-review: In Review
  approved: Approved
```

The validated sandbox override is:

```yaml
states:
  design: Design
  in-review: In Review
  approved: Approval
```

## Sandbox Configuration

The validated sandbox shape is:

```yaml
ado:
  organization: STMN-Group
  personal-access-token: ${ADO_PERSONAL_ACCESS_TOKEN:}
  http-client-enabled: true
  dry-run: true
  projects:
    "[ADOnis 2.0 Test Project]":
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

idempotency:
  type: sqlite
  sqlite-path: ./data/sandbox-approval-bot.sqlite
```

Use `ado.dry-run=true` for real reads with suppressed writes. Use `ado.dry-run=false` only for the controlled write-enabled sandbox test.

Secrets must come from local environment variables or a local secret manager:

* `ADO_PERSONAL_ACCESS_TOKEN`.
* `ADO_WEBHOOK_SHARED_SECRET`.

Do not commit `application-local.yml`, `.env`, PAT values, webhook secrets, or SQLite sandbox database files.

## How To Verify This Against ADO

These PowerShell snippets are intended for local operator verification. They do not print PAT values, webhook secrets, or authorization headers.

Set local variables:

```powershell
$Organization = "STMN-Group"
$Project = "ADOnis 2.0 Test Project"
$WorkItemId = 12345
$WorkItemType = "Test Case"
```

Check secret presence without printing values:

```powershell
@{
  ADO_PERSONAL_ACCESS_TOKEN = -not [string]::IsNullOrWhiteSpace($env:ADO_PERSONAL_ACCESS_TOKEN)
  ADO_WEBHOOK_SHARED_SECRET = -not [string]::IsNullOrWhiteSpace($env:ADO_WEBHOOK_SHARED_SECRET)
}
```

Create a local credential object without printing the PAT:

```powershell
$SecurePat = ConvertTo-SecureString $env:ADO_PERSONAL_ACCESS_TOKEN -AsPlainText -Force
$Credential = [pscredential]::new("ado", $SecurePat)
```

List Test Case field reference names:

```powershell
$ProjectSegment = [uri]::EscapeDataString($Project)
$TypeSegment = [uri]::EscapeDataString($WorkItemType)
$FieldsUrl = "https://dev.azure.com/$Organization/$ProjectSegment/_apis/wit/workitemtypes/$TypeSegment/fields?api-version=7.1"

$Fields = Invoke-RestMethod -Method Get -Uri $FieldsUrl -Authentication Basic -Credential $Credential
$Fields.value |
  Sort-Object referenceName |
  Select-Object name, referenceName, type
```

Find likely approval fields:

```powershell
$Fields.value |
  Where-Object {
    $_.referenceName -in @(
      "Custom.ApproverTech",
      "Custom.ApproverTest",
      "Custom.Dev_SME",
      "Custom.SQA_SME"
    ) -or $_.name -match "Approver|Approval|SME|SQA"
  } |
  Select-Object name, referenceName, type
```

Inspect current Work Item values:

```powershell
$FieldList = @(
  "System.State",
  "System.Reason",
  "System.ChangedBy",
  "Custom.ApproverTech",
  "Custom.ApproverTest",
  "Custom.Dev_SME",
  "Custom.SQA_SME"
) -join ","

$WorkItemUrl = "https://dev.azure.com/$Organization/$ProjectSegment/_apis/wit/workitems/$WorkItemId" +
  "?fields=$([uri]::EscapeDataString($FieldList))&api-version=7.1"

$WorkItem = Invoke-RestMethod -Method Get -Uri $WorkItemUrl -Authentication Basic -Credential $Credential
$WorkItem.fields.GetEnumerator() |
  Sort-Object Name |
  Select-Object Name, Value
```

Inspect recent Work Item updates and field changes:

```powershell
$UpdatesUrl = "https://dev.azure.com/$Organization/$ProjectSegment/_apis/wit/workItems/$WorkItemId/updates?api-version=7.1"
$Updates = Invoke-RestMethod -Method Get -Uri $UpdatesUrl -Authentication Basic -Credential $Credential

$Updates.value |
  Sort-Object id -Descending |
  Select-Object -First 10 |
  ForEach-Object {
    $Revision = $_.rev
    $_.fields.PSObject.Properties |
      Where-Object {
        $_.Name -in @(
          "System.State",
          "System.Reason",
          "System.ChangedBy",
          "Custom.ApproverTech",
          "Custom.ApproverTest",
          "Custom.Dev_SME",
          "Custom.SQA_SME"
        )
      } |
      ForEach-Object {
        [pscustomobject]@{
          Revision = $Revision
          Field = $_.Name
          HasOldValue = $_.Value.PSObject.Properties.Name -contains "oldValue"
          HasNewValue = $_.Value.PSObject.Properties.Name -contains "newValue"
        }
      }
  }
```

The updates query above intentionally reports whether old/new values exist without printing the values. If values are needed for local troubleshooting, inspect them only in a private terminal and do not paste them into tickets, logs, PRs, or committed docs.

Check configured state values by observing real history:

```powershell
$Updates.value |
  ForEach-Object {
    $StateChange = $_.fields."System.State"
    if ($null -ne $StateChange) {
      [pscustomobject]@{
        Revision = $_.rev
        ChangedDate = $_.revisedDate
        HasOldState = $StateChange.PSObject.Properties.Name -contains "oldValue"
        HasNewState = $StateChange.PSObject.Properties.Name -contains "newValue"
      }
    }
  }
```

Confirm exact state names in the Azure DevOps UI or in private local output before configuring `states.design`, `states.in-review`, or `states.approved`.

## Approval Field Clearing Behavior

Validated sandbox behavior:

* `replace` with `null` failed for the configured approval identity/person fields.
* ADO returned: `Value cannot be null.`
* `replace` with an empty string succeeded for the configured approval fields.

This is treated as validated sandbox behavior for the configured approval fields only:

* `Custom.ApproverTech`.
* `Custom.ApproverTest`.

It does not imply that all fields should be cleared with an empty string. Business field reversion and other field behavior must remain field-specific.

## State Transition Behavior

The V1 default final approved state remains `Approved`.

The validated sandbox final state is `Approval`. Configure it per project:

```yaml
states:
  approved: Approval
```

If ADO rejects a state with an error similar to `The field 'State' contains the value 'Approved' that is not in the list of supported values`, verify the project process state values and update the per-project `states.approved` value.

## Comments API Behavior

Validated API versions:

* Work Item GET: `api-version=7.1`.
* Work Item revision GET: `api-version=7.1`.
* Work Item PATCH: `api-version=7.1`.
* Work Item Comments API: `api-version=7.1-preview`.

ADO rejected Comments API `api-version=7.1` with a preview-version error. The Comments API must use `7.1-preview`.

## Expected Successful Logs

Sanitized successful PATCH log:

```text
ADO HTTP operation completed operation=patchWorkItem organization=STMN-Group project=ADOnis 2.0 Test Project workItemId=12345 httpStatus=200 success=true retryable=false resultingRevision=42
```

Sanitized successful comment log:

```text
ADO HTTP operation completed operation=createWorkItemComment organization=STMN-Group project=ADOnis 2.0 Test Project workItemId=12345 httpStatus=201 success=true commentId=123
```

Sanitized completed processing log:

```text
ADO webhook processing completed project=ADOnis 2.0 Test Project workItemId=12345 revision=41 result=COMPLETED httpStatus=202 reason=Approvals are valid and complete.
```

Logs may include operation paths, such as:

```text
operationPaths=[/rev, /fields/System.State]
```

Logs must not include PAT values, webhook secret values, authorization headers, request body values, raw field values, full webhook payloads, or full comment text.

## Expected Warning Logs

Patch succeeded but comment failed:

```text
Patch succeeded project=ADOnis 2.0 Test Project workItemId=12345 revision=41 resultingRevision=42
Comment failed after successful patch project=ADOnis 2.0 Test Project workItemId=12345 revision=41 message=Azure DevOps comment request failed with status 400. outcome=COMPLETED_WITH_WARNING
ADO webhook processing completed project=ADOnis 2.0 Test Project workItemId=12345 revision=41 result=COMPLETED_WITH_WARNING httpStatus=202 reason=Patch succeeded but comment creation failed.
```

This is expected V1 behavior. No rollback is attempted after a successful PATCH.

## Sandbox-Specific Behavior

During helper/manual tests, the event revision and fetched current revision may not always appear intuitive. Treat this as a sandbox/testing nuance, not as a production guarantee.

The `/rev` JSON Patch test remains the safety mechanism. If the Work Item changed between the event and the PATCH, ADO can reject the PATCH revision test instead of applying stale updates.

## Troubleshooting

`Value cannot be null.`

* The sandbox approval identity/person fields rejected `replace null`.
* For configured approval fields, clear with `replace ""`.
* Do not generalize this to all field clears.

`The field 'State' contains the value 'Approved' that is not in the list of supported values`

* The project process does not support the configured final state.
* Keep the V1 default as `Approved` for projects that use it.
* Set `states.approved: Approval` for the validated sandbox.

Comments API preview-version error

* Work Item Comments API requires `api-version=7.1-preview`.
* Do not change Work Item GET, revision GET, or PATCH from `7.1`.

Duplicate event skipped by idempotency

* Idempotency key is `project + workItemId + revision`.
* Repeating a completed or skipped event is expected to skip.
* To retest locally, create a new ADO revision or intentionally remove the local SQLite file.

YAML project key with spaces or dots not loaded

* Use Spring Boot bracket notation:

```yaml
projects:
  "[ADOnis 2.0 Test Project]":
    enabled: true
```

Config changes appear ignored

* YAML configuration is loaded at service startup.
* Restart the service after local config changes.
* Confirm the active local file is untracked and not accidentally omitted from the runtime profile.

Current revision/event revision mismatch observed during sandbox/helper testing

* Confirm the Work Item id and revision in the helper payload.
* Confirm the current Work Item revision in ADO.
* Rely on the `/rev` JSON Patch test as the write safety mechanism.

## Open Validation Items

Open items for future validation:

* Confirm additional ADO process templates with identity/person approval fields.
* Validate projects whose final approval state is `Approved`.
* Validate projects whose final approval state is another custom value.
* Confirm ADO Service Hooks header behavior in each planned deployment topology.
* Validate long-running idempotency retention and SQLite cleanup settings against expected event volume.

## Future UI Validation Work

A first UI iteration exists separately and currently uses simple text fields for all values. That creates risk of human configuration errors.

Future UI work should validate or discover user-entered values against Azure DevOps whenever possible before generating YAML:

* Organization and project availability.
* Work Item types.
* Work Item fields and field reference names.
* State values.
* Users or identities for SME/SQA lists.
* Webhook/shared-secret configuration sanity without displaying secret values.
* Dry-run/write-enabled safety settings.

The UI must not rely on display names for authorization decisions. Identity matching remains based on email/login.

## Future Configuration Reload Notes

YAML configuration is currently startup-bound. A future iteration should evaluate a safe configuration reload or hot-load mechanism so operators do not need to stop and restart the service after every config change.

Do not treat this as an implementation design yet. Risks to evaluate include:

* Partial config reload.
* Changing project config while events are processing.
* Stale idempotency or queue behavior.
* Ensuring validation passes before activating new config.
* Avoiding accidental write-enabled production configuration.
