# GitHub `repository_dispatch` Payload Contract

This document defines the canonical Azure DevOps event payload expected by the approval bot when a GitHub workflow is triggered via `repository_dispatch`.

## Purpose

- The GitHub App dispatch event wakes the workflow only.
- Spring webhook mode and the Java one-shot runner normalize the same ADO `workitem.updated` event shape.
- The Java runner still fetches Azure DevOps (ADO) source of truth before making decisions.
- Processing eligibility is still controlled by YAML config (project enablement, supported work item type, workflow settings).

## Event Shape

Expected event type in GitHub:

```text
repository_dispatch
```

Expected payload location in workflow context:

```text
github.event.client_payload
```

## Canonical `client_payload`

New integrations must place the unmodified ADO event under `ado_event`:

```json
{
  "ado_event": {
    "eventType": "workitem.updated",
    "id": "delivery-1",
    "subscriptionId": "subscription-1",
    "resource": {
      "workItemId": 12345,
      "rev": 17,
      "id": 17,
      "url": "https://dev.azure.com/example/project/_apis/wit/workItems/12345/updates/17",
      "revisedBy": {
        "displayName": "Example User",
        "uniqueName": "user@example.com"
      },
      "revision": {
        "id": 12345,
        "rev": 17,
        "fields": {
          "System.TeamProject": "Project Name",
          "System.WorkItemType": "Test Case",
          "System.State": "Design"
        }
      }
    },
    "resourceContainers": {
      "account": {
        "baseUrl": "https://dev.azure.com/ExampleOrg/"
      }
    }
  }
}
```

`resource.workItemId` is the Work Item ID, with `resource.revision.id` as its fallback. `resource.id` is the update/revision ID and is never interpreted as a Work Item ID. Revision comes from `resource.rev`, falling back to `resource.revision.rev`.

The parser derives organization from `resourceContainers.account.baseUrl` and project from `resource.revision.fields["System.TeamProject"]`. Only `workitem.updated` is accepted.

## Legacy Flat Input

The flat payload remains available for manual/backward-compatible runner invocation. New repository dispatch integrations should use `ado_event`.

### Required Fields

All required fields must be present in `client_payload`.

| Field | Type | Description |
| --- | --- | --- |
| `source` | `string` | Source system identifier, for example `ado-service-hook`. |
| `organization` | `string` | ADO organization name. |
| `project` | `string` | ADO project name from the event context. |
| `workItemId` | `number` | ADO work item ID. |
| `revision` | `number` | ADO revision number tied to the triggering change. |
| `eventType` | `string` | Event classification, for example `workitem.updated`. |

### Optional Fields

These fields are accepted when available.

| Field | Type | Description |
| --- | --- | --- |
| `changedBy.displayName` | `string` | Display name of the user who performed the change. |
| `changedBy.uniqueName` | `string` | Unique identity (typically email/UPN) of the user who performed the change. |
| `resourceUrl` | `string` | URL to the ADO resource associated with the event. |
| `subscriptionId` | `string` | ADO Service Hook subscription identifier. |
| `deliveryId` | `string` | Delivery identifier for tracing a single webhook delivery. |

### Flat Example

```json
{
  "source": "ado-service-hook",
  "organization": "STMN-Group",
  "project": "ADOnis 2.0 Test Project",
  "workItemId": 12345,
  "revision": 17,
  "eventType": "workitem.updated",
  "changedBy": {
    "displayName": "Jane Doe",
    "uniqueName": "jane.doe@example.com"
  },
  "resourceUrl": "https://dev.azure.com/STMN-Group/ADOnis%202.0%20Test%20Project/_apis/wit/workItems/12345",
  "subscriptionId": "11111111-2222-3333-4444-555555555555",
  "deliveryId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
}
```

## Processing Notes

- Even with a valid dispatch payload, the runner can still skip processing based on YAML configuration.
- The payload is treated as a trigger and locator; business decisions are based on fresh ADO reads.
- Missing required fields should fail fast before runner execution.

## Security Requirements

- Secrets must never be printed.
- Installation tokens and private keys must not be committed or logged.
- Do not include PATs, shared secrets, private keys, or token values in dispatch payloads.
- Keep secret material in GitHub Secrets, environment variables, or a secret manager.

