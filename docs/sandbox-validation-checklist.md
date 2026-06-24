# Sandbox Validation Checklist

Use this checklist with the full [Azure DevOps Sandbox Validation Playbook](Azure%20DevOps%20Sandbox%20Validation%20Playbook.md).

## Before Dry-run

- [ ] Working tree is clean.
- [ ] `mvn test` passes.
- [ ] Config points only to a sandbox organization and sandbox project.
- [ ] No production project is enabled.
- [ ] `ado.http-client-enabled=true`.
- [ ] `ado.dry-run=true`.
- [ ] PAT is sandbox-only and provided through `ADO_PERSONAL_ACCESS_TOKEN`.
- [ ] Webhook shared secret is provided through `ADO_WEBHOOK_SHARED_SECRET`.
- [ ] `webhook.shared-secret.enabled=true`.
- [ ] Sender or tunnel sends `X-ADO-Webhook-Secret`.
- [ ] Custom fields exist in the sandbox.
- [ ] Custom field reference names match config.
- [ ] Bot identity email matches the PAT account.
- [ ] Work Item Updated service hook points only to local tunnel or sandbox service.
- [ ] SQLite path points to a local sandbox database.
- [ ] Logs do not print PAT, webhook shared secret, `Authorization`, full payloads, raw field values, or full comment text.

## Dry-run Scenarios

- [ ] Bot-generated event is skipped.
- [ ] Unsupported Work Item type is skipped.
- [ ] Comment-only event does not patch/comment.
- [ ] Design -> In Review clears stale approvals.
- [ ] Design -> Approved returns to In Review and clears approvals.
- [ ] SME review records SME approval.
- [ ] SQA review after SME moves Test Case to Approved.
- [ ] Same user cannot complete SME and SQA.
- [ ] Non-SME/SQA reversible content change would be reverted.
- [ ] SME/SQA content change clears prior approvals and records current reviewer.
- [ ] Invalid Approved state would return to In Review.
- [ ] Duplicate same project/workItemId/revision is idempotently skipped.

## Dry-run Go/No-go

- [ ] Expected classification/result appears for every scenario.
- [ ] Expected `would PATCH` paths appear only when PATCH is expected.
- [ ] Expected `would create comment` appears only when comment creation is expected.
- [ ] No real PATCH/comment request reached Azure DevOps.
- [ ] No secret or raw content leaked in logs.

## Controlled Write-enabled Sandbox Test

- [ ] Dry-run passed.
- [ ] Same sandbox project remains configured.
- [ ] Only `ado.dry-run=false` changed.
- [ ] One disposable Test Case selected.
- [ ] One scenario run at a time.
- [ ] Azure DevOps UI confirms expected PATCH/comment behavior.
- [ ] Dry-run is re-enabled immediately if behavior is unexpected.
