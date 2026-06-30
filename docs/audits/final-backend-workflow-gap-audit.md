# Final Backend Workflow Gap Audit

## Scope

This audit compares the backend at commit `0dbf7ee` with the final agreed workflow contract. It audits the service behavior rather than treating the earlier TL diagram as a literal implementation specification.

The Config UI, retry policy, and idempotency key are outside this audit. No behavior changes are included in this branch.

Classification meanings:

* **implemented**: the current execution path and tests satisfy the agreed behavior.
* **partially implemented**: related behavior exists, but at least one contract path is incomplete or unsafe.
* **missing**: no backend behavior currently implements the item.
* **intentionally out of scope**: explicitly deferred and not required to block the core workflow.
* **needs confirmation**: the required product or ADO-specific behavior is not precise enough to implement safely.

## Executive Summary

| # | Audit item | Classification | Main finding |
|---|---|---|---|
| 1 | Bot-owned Approver Tech field | **partially implemented** | The configured SME field is written, cleared, and sometimes restored by the bot, but edits made by an authorized approver are not always restored to the previous bot-owned value. |
| 2 | Bot-owned Approver Test field | **partially implemented** | The configured SQA field has the same protection gap as the SME field. |
| 3 | Manual approval field modification restoration | **partially implemented** | Non-approver edits are restored, while authorized approver edits are recalculated and may allow a manually supplied value for the other role to participate in approval. |
| 4 | Revert limited to reversible-business-fields only | **implemented** | Business-field reversion iterates only configured fields and explicitly excludes state and approval fields. |
| 5 | Unauthorized modification comment content | **partially implemented** | Detailed comment formatting exists but is not connected to workflow decisions; current emitted comments omit changed-field details, and approval restoration can emit no comment. |
| 6 | Configurable design / in-review / approved states | **implemented** | All three names bind per project, default correctly, are validated, and drive state detection and writes. |
| 7 | Test Version increment when leaving approved state | **missing** | No Test Version configuration, parsing, decision, or PATCH operation exists. |
| 8 | Null/blank/non-numeric Test Version handling | **needs confirmation** | No behavior exists, and the expected fallback/error policy and ADO field type/reference name are not yet defined. |
| 9 | Bot identity skip by configured bot email | **implemented** | Classifier and workflow engine both compare normalized email/login, never display name. |
| 10 | Read-only field enforcement as best-effort/deferred | **intentionally out of scope** | No process-rule integration exists. The final contract allows this to remain deferred and non-blocking. |
| 11 | Existing tests protecting these decisions | **partially implemented** | Strong tests cover current behavior, but some tests explicitly preserve behavior that conflicts with the final bot-owned-field contract. |
| 12 | Missing tests needed before implementation | **missing** | Critical cross-role manual-edit, Test Version, comment integration, and best-effort rule tests do not exist. |

## Detailed Findings

### 1. Bot-owned Approver Tech field

**Classification: partially implemented**

The backend has no hardcoded `Custom.ApproverTech` concept. It treats `ProjectApprovalConfig.approvedBySmeField` as the technical/SME approval field, so ownership applies when YAML maps `approved-by-sme` to `Custom.ApproverTech`.

Evidence:

* `ProjectApprovalConfigMapper` maps the configured SME field into the domain configuration.
* `WorkflowEngine.currentUserApprovalOperations` writes the actual reviewer's scalar display value, falling back to normalized email/login.
* `WorkflowEngine.restoreChangedApprovalFields` can restore the exact previous raw value.
* Approval cleanup uses `replace ""`, matching the validated ADO identity-field behavior.
* `ProjectApprovalConfigValidator` prevents the approval field from also being configured as a reversible business field.

Gap:

* `WorkflowEngine.handleInReview` calls restoration for a non-approver, but an authorized approver follows `finalizeInReviewApprovals` instead. The test `manualApprovalFieldEditByApproverWithoutContentChangeCountsAsReview` explicitly expects recalculation rather than unconditional restoration.
* Therefore the field is automation-managed, but not yet strictly bot-owned under the final contract.

### 2. Bot-owned Approver Test field

**Classification: partially implemented**

The configured `approvedBySqaField` provides the Test/SQA role and supports the same write, clear, validation, and restoration operations as the SME field. It represents `Custom.ApproverTest` only when YAML maps that reference name.

The same authorized-approver gap applies. A human edit to this field is not unconditionally restored before approval validation, so strict bot ownership is incomplete.

### 3. Manual approval field modification restoration

**Classification: partially implemented**

Implemented paths:

* A non-approver changing an approval field in configured In Review state gets the exact previous value restored.
* If the previous value is absent/blank, the configured identity field is cleared with `replace ""`.
* A non-approver changing reversible content and approval fields gets both the configured business fields reverted and approval fields restored.
* An authorized approver changing reversible content causes both approvals to be cleared and the actual reviewer to be recorded.

Contract gap:

* With no reversible content change, an authorized SME/SQA edit is treated as a review action instead of first restoring every manually changed approval field.
* `finalizeInReviewApprovals` begins from `currentFields`. A manually supplied, valid value for the other role can remain in that projection and participate in `fullyApprovedByDifferentUsers()`.
* Example requiring a regression test: an SME changes only Approver Test to a configured SQA identity while both previous approval fields are empty. The engine can add the actual SME approval, accept the manually supplied SQA value, and move the item to the approved state. That violates "a user cannot claim another user approved".

This is the highest-priority workflow gap found by the audit.

### 4. Revert limited to reversible-business-fields only

**Classification: implemented**

`ChangeAnalyzer.changedReversibleFields` iterates only `config.reversibleBusinessFields()`. It does not inspect arbitrary changed fields and explicitly skips `System.State` and both configured approval fields.

Protective evidence:

* `WorkflowEngineTest.nonApproverChangingOnlyUnconfiguredFieldIsSkipped` proves an unconfigured field is not reverted.
* `WorkflowEngineTest.changeAnalyzerIgnoresUnconfiguredFields` protects the analyzer boundary directly.
* Raw previous values, including whitespace and null, are preserved when a configured field is reverted.
* `ProjectApprovalConfigValidator` rejects approval fields or `System.State` inside the reversible list.

No arbitrary business-field reversion was found outside this configuration boundary.

### 5. Unauthorized modification comment content

**Classification: partially implemented**

The desired building blocks exist in `CommentBuilder.proposedChanges`:

* TL-style wording that original values were restored because the user is not, or could not be verified as, an SME/SQA approver.
* Deterministic changed-field ordering.
* Previous and proposed values, including explicit null rendering.

The production workflow does not use those building blocks. `WorkflowEngine.proposedChangesComment` emits only the user's display name and generic restoration wording. It does not include rejected field names, previous values, proposed values, or field-specific reasons.

Additional gaps:

* A non-approver's approval-only edit is restored with `comment=null`.
* `CommentBuilder.manualApprovalFieldEditCorrected()` and `unauthorizedApprovalActionRejected()` are tested but are not wired into `WorkflowEngine` decisions.
* `WorkItemProcessingService` only calls `CommentBuilder.fromDecision`, which passes through the text already embedded in `WorkflowDecision`.

The final implementation should have one comment-construction path and must continue to keep raw values out of logs even when verbose details are intentionally placed in the ADO comment.

### 6. Configurable design / in-review / approved states

**Classification: implemented**

`WorkflowStateProperties` defaults to `Design`, `In Review`, and `Approved`. `ProjectApprovalConfigMapper` maps all three project-level values into `WorkflowStateNames`, and startup validation rejects blank values.

`WorkflowEngine` uses configured names for:

* known-state detection;
* Design handling;
* In Review handling;
* approved/final-state handling;
* direct Design-to-approved correction;
* PATCH transitions to In Review and approved.

Tests cover custom values such as `Draft`, `Peer Review`, and `Approval`, plus blank-state startup validation. State comparison is exact after configuration values are trimmed during mapping.

### 7. Test Version increment when leaving approved state

**Classification: missing**

No Test Version reference name or configuration exists in `ProjectApprovalConfig`, Spring properties, or sample backend configuration. `WorkflowEngine` has no branch for `previousState == configured approved` followed by another state, and no version increment operation is produced.

No parser, numeric conversion, overflow policy, PATCH operation, or test was found.

The future implementation should detect the transition using configured state names, not literal `Approved`, and should remain independent from unrelated field-reversion logic.

### 8. Handling null/blank/non-numeric Test Version

**Classification: needs confirmation**

The backend currently has no Test Version behavior. Before implementation, confirm:

* the exact ADO field reference name and whether it is fixed or project-configurable;
* the ADO field type and whether values arrive as integer, decimal, or string;
* whether null/blank starts at `0` and becomes `1`, starts directly at `1`, or blocks the increment;
* whether non-numeric values should be left unchanged with a warning, corrected to a baseline, or produce a controlled workflow failure;
* maximum value/overflow behavior;
* whether the version increments on every transition away from approved or only selected target states.

Until these are agreed, silently coercing malformed values would risk corrupting a business-owned field.

### 9. Bot identity skip by configured bot email

**Classification: implemented**

`EventClassifier` skips bot events before processing, and `WorkflowEngine` repeats the check as defense in depth. Both use normalized configured email/login values. Display name is not trusted.

Tests cover:

* configured bot email match;
* case and surrounding whitespace normalization;
* display-name match with missing email not being treated as a bot identity.

### 10. Read-only field enforcement as best-effort/deferred

**Classification: intentionally out of scope**

No ADO process-rule client, read-only rule inspection, or enforcement operation exists. This is consistent with treating process-rule management as deferred and best-effort rather than part of the core approval transaction.

If implemented later, the integration should be isolated from `WorkflowEngine`. Unsupported APIs, insufficient PAT permissions, and authorization failures must be logged safely and skipped without changing the workflow result. It must not become a startup requirement or roll back successful Work Item processing.

### 11. Existing tests that protect these decisions

**Classification: partially implemented**

Useful existing coverage includes:

* `WorkflowEngineTest`: configured states, approval validation, different-user requirement, non-approver restoration, configured-only business reversion, exact raw value preservation, and bot skip.
* `CommentBuilderTest`: detailed changed-field formatting, TL-style reason wording, manual approval correction wording, and null/raw value rendering.
* `PatchBuilderTest`: `/rev` first, deterministic order, exact raw revert values, replace-null support for business fields, and no `remove` operations.
* `EventClassifierTest`: bot identity matching by normalized email/login.
* `ProjectApprovalConfigValidatorTest`: required approval fields, reversible-field exclusions, state-name validation, and identity-list warnings.
* `WorkItemProcessingServiceTest`: source-of-truth reads, PATCH/comment sequencing, and `COMPLETED_WITH_WARNING` after comment failure.

Coverage conflict:

* `manualApprovalFieldEditByApproverWithoutContentChangeCountsAsReview` protects behavior that is weaker than the final strict bot-owned-field contract. It should be replaced or reframed when the implementation changes.
* Detailed `CommentBuilder` unit tests do not prove that workflow decisions actually emit those comments.
* No Test Version or process-rule tests exist.

### 12. Missing tests needed before implementation

**Classification: missing**

Add the following tests before changing behavior:

1. An SME manually setting Approver Test to a valid configured SQA identity restores the previous Test value and does not approve.
2. An SQA manually setting Approver Tech to a valid configured SME identity restores the previous Tech value and does not approve.
3. An authorized user editing both approval fields cannot claim either approval beyond the action attributable to their own identity.
4. Approval restoration preserves exact previous raw identity objects/strings; an absent previous approval clears with `replace ""`.
5. Approval-only unauthorized edits generate the agreed correction comment.
6. Reversible-field rejection emits the combined TL wording plus deterministic field names, previous/proposed values, and reasons through the real `WorkflowEngine` to `WorkItemProcessingService` path.
7. Verbose comment values never appear in application logs.
8. Leaving a custom configured approved state increments Test Version exactly once.
9. Entering approved, remaining approved, or changing between non-approved states does not increment Test Version.
10. Null, blank, numeric-string, numeric-object, non-numeric, and overflow Test Version cases follow the confirmed policy.
11. Test Version PATCH ordering remains deterministic and `/rev` remains first.
12. Best-effort process-rule success is observable, while unsupported, 401/403, and transport failures log safely and do not fail workflow processing.

## Recommended Implementation Order

1. Close the cross-role approval-field claim gap and establish strict restoration semantics for all human edits.
2. Connect workflow decisions to the detailed `CommentBuilder` path and add end-to-end comment assertions.
3. Confirm Test Version field semantics, then implement transition-aware increment and malformed-value handling.
4. Keep read-only process rules deferred unless operational need and PAT permissions justify a separate best-effort integration.

These changes should preserve the configured reversible-field boundary, configurable state names, bot email skip, existing idempotency key, and current Config UI behavior.
