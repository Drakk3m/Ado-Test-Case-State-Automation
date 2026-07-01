# One-Shot Runner

`RepositoryDispatchOneShotRunner` executes one `repository_dispatch` payload without starting Spring Boot.

## What It Does

1. Parses dispatch JSON from `--payload`.
2. Loads YAML config from `--config`.
3. Gates by configured/enabled project.
4. Fetches Azure DevOps source of truth.
5. Reuses `WorkItemProcessingService` for workflow, patch, and comment behavior.
6. Prints final result to stdout.

It is a plain Java entry point: it does not start Spring Boot, use a web controller, load the Config UI, or require SQLite idempotency.

## Usage

Build classes and runtime dependencies:

```powershell
mvn -DskipTests compile dependency:copy-dependencies -DincludeScope=runtime
```

Run one-shot execution:

```powershell
java -cp "target/classes;target/dependency/*" com.dentalwings.approvalbot.dispatch.RepositoryDispatchOneShotRunner --payload .\payload.json --config .\src\main\resources\application-local.yml
```

Expected output shape:

```text
Fetched ADO source of truth project=... workItemId=... workItemType=... revision=...
project=... workItemId=... revision=... result=COMPLETED reason=...
```

## Exit Codes

| Code | Meaning |
| --- | --- |
| `0` | Completed, skipped, or completed with a comment warning. |
| `1` | Retryable processing failure after the configured ADO retry attempts. |
| `2` | Payload validation, usage, credential, or YAML configuration error. |
| `3` | Non-retryable processing failure. |

## Notes

- `ado.http-client-enabled=true` is required.
- Authentication mode defaults to legacy `pat`. PAT mode reads `ado.personal-access-token`, normally from `ADO_PERSONAL_ACCESS_TOKEN`.
- Service Principal workflows use `ado.authentication.mode: bearer` and `ado.authentication.bearer-token`, normally from `ADO_ACCESS_TOKEN`. Java consumes the token but does not acquire or refresh it.
- YAML is the source of truth. In particular, ADO calls use `ado.organization`; the payload organization remains required trigger metadata.
- If project is missing/disabled in YAML, runner exits with `SKIPPED` before ADO fetch.
- Unsupported work item types are skipped after one eligibility fetch and before PATCH/comment.
- The V1 eligibility gate fetches the current item before invoking `WorkItemProcessingService`. The service then performs its own fresh source-of-truth read. This duplicate read is intentional for V1: it keeps unsupported types outside the processing boundary without duplicating workflow logic.
- The standard `RetryingAdoClient` wraps one-shot ADO operations. Configured dry-run mode still performs reads while suppressing PATCH/comment writes.
- One-shot mode does not persist idempotency state. Dispatch delivery deduplication must be handled by the caller/workflow if required.
- Required payload fields are validated before execution.
- Output includes only project, work item ID, revision, result, reason, and safe source-of-truth metadata. It never includes PATs, bearer tokens, webhook secrets, authorization headers, patch values, or comment bodies.
- Do not print or commit secrets, PATs, installation tokens, or private keys.

