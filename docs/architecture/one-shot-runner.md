# One-Shot Runner

`RepositoryDispatchOneShotRunner` executes one `repository_dispatch` payload without starting Spring Boot.

## What It Does

1. Parses dispatch JSON from `--payload`.
2. Loads YAML config from `--config`.
3. Gates by configured/enabled project.
4. Fetches Azure DevOps source of truth.
5. Reuses `WorkItemProcessingService` for workflow, patch, and comment behavior.
6. Prints final result to stdout.

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
Final result: COMPLETED (...)
```

## Notes

- `ado.http-client-enabled=true` is required.
- A valid PAT must be available via config/env expansion.
- If project is missing/disabled in YAML, runner exits with `SKIPPED` before ADO fetch.
- Required payload fields are validated before execution.
- Do not print or commit secrets, PATs, installation tokens, or private keys.

