# GitHub Action E2E Testing

The `ADO Work Item Updated` workflow runs `RepositoryDispatchOneShotRunner` once for each `repository_dispatch` event of type `ado-work-item-updated`. It does not start Spring Boot and does not use SQLite.

## Prerequisites

1. Add `ADO_PERSONAL_ACCESS_TOKEN` as a GitHub repository secret. Grant only the Azure DevOps scopes needed to read Work Items and, when write mode is intentionally enabled, update Work Items and create comments.
2. Review `config/application-github-action.yml` and replace every example organization, project, field, state, user, and bot identity with sandbox values. The configured organization and project name must match ADO exactly.
3. Keep `ado.dry-run: true` for the first validation.

The PAT is read only from the runner environment. Do not add it to YAML, dispatch JSON, logs, artifacts, repository variables, or workflow output.

## Dispatch Contract

The bridge sends a GitHub `repository_dispatch` request with:

```json
{
  "event_type": "ado-work-item-updated",
  "client_payload": {
    "ado_event": {
      "eventType": "workitem.updated",
      "id": "delivery-id",
      "subscriptionId": "subscription-id",
      "resource": {
        "workItemId": 12345,
        "rev": 7,
        "id": 7,
        "url": "https://dev.azure.com/ExampleOrg/ExampleProject/_apis/wit/workItems/12345/updates/7",
        "revisedBy": {
          "displayName": "Example User",
          "uniqueName": "user@example.test"
        },
        "revision": {
          "id": 12345,
          "rev": 7,
          "fields": {
            "System.TeamProject": "Example Sandbox Project",
            "System.WorkItemType": "Test Case",
            "System.State": "In Review"
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
}
```

The Azure DevOps/GitHub App bridge should forward the ADO event as `client_payload.ado_event` without flattening or remapping `resource.id`. The bridge authenticates to GitHub with an installation token obtained at runtime from its private key. Neither the installation token nor private key belongs in the dispatch payload.

For a manual sandbox dispatch, store the JSON above in `dispatch.json`, set `GH_TOKEN` from a secure installation or fine-grained token source, and run:

```powershell
gh api --method POST repos/OWNER/REPOSITORY/dispatches --input dispatch.json
```

Do not print `GH_TOKEN` or include it in command arguments, files, examples, or logs.

## Dry-Run Validation

With `ado.dry-run: true`, the runner performs real ADO reads and workflow evaluation, but suppresses PATCH and comment writes. Confirm that the Actions log shows only safe metadata and an outcome such as:

```text
Fetched ADO source of truth project=... workItemId=... workItemType=... revision=...
project=... workItemId=... revision=... result=COMPLETED reason=...
```

The workflow writes `github.event.client_payload` to a permission-restricted file under `RUNNER_TEMP`, never prints it, and deletes it in an `always()` cleanup step.

## Write-Enabled Validation

Use only a disposable Test Case in an ADO sandbox. After dry-run evidence is clean:

1. Review PAT permissions, configured field reference names, users, and project-specific state names.
2. Change `ado.dry-run` to `false` in `config/application-github-action.yml` through a reviewed commit.
3. Dispatch one new ADO revision and verify PATCH/comment results.
4. Restore `ado.dry-run: true` immediately after testing.

Never enable writes by modifying the dispatch payload. YAML remains the source of truth.

## Concurrency

The V1 concurrency group uses the canonical `resource.workItemId`, falling back to `resource.revision.id`. This prevents parallel handling of the same Work Item. It conservatively serializes equal Work Item IDs across different projects because accessing `System.TeamProject` in a portable GitHub concurrency expression is awkward; project-aware grouping can be added later if needed.

## Exit Codes

| Code | Meaning |
| --- | --- |
| `0` | Completed, skipped, or completed with warning. |
| `1` | Retryable processing failure after ADO retry attempts. |
| `2` | Payload, usage, credential, or YAML configuration error. |
| `3` | Non-retryable processing failure. |

Any nonzero code fails the Actions job. Runner output never includes the PAT, authorization headers, full payload, PATCH values, or comment body.

## Security Notes

- Store the ADO PAT only in `ADO_PERSONAL_ACCESS_TOKEN` as a GitHub secret.
- Store GitHub App private keys and installation tokens outside this repository and dispatch payload.
- Do not upload the temporary payload as an artifact.
- Do not enable shell tracing or print environment variables.
- Keep least-privilege permissions; the workflow itself needs only `contents: read`.
