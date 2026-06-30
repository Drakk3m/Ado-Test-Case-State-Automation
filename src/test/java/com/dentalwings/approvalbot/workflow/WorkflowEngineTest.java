package com.dentalwings.approvalbot.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import com.dentalwings.approvalbot.ado.AdoIdentity;
import com.dentalwings.approvalbot.config.ProjectApprovalConfig;
import com.dentalwings.approvalbot.config.WorkflowStateNames;
import com.dentalwings.approvalbot.domain.Identity;
import com.dentalwings.approvalbot.domain.PatchOperation;
import com.dentalwings.approvalbot.domain.ProcessingResult;
import com.dentalwings.approvalbot.domain.WorkflowInput;

class WorkflowEngineTest
{

    private static final String SME_FIELD = "Custom.ApprovedBySME";
    private static final String SQA_FIELD = "Custom.ApprovedBySQA";
    private static final String TITLE_FIELD = "System.Title";
    private static final String DESCRIPTION_FIELD = "System.Description";

    private final WorkflowEngine workflowEngine = new WorkflowEngine();

    @Test
    void disabledProjectIsSkipped()
    {
        var decision = workflowEngine.decide(input("In Review", config(false), sme(), Map.of(), Map.of()));

        assertThat(decision.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(decision.patchRequired()).isFalse();
    }

    @Test
    void unsupportedWorkItemTypeIsSkipped()
    {
        var input = new WorkflowInput("ProjectA", 1, 1, "Bug", "Design", "In Review", sme(), Map.of(), Map.of(),
                config(true));

        var decision = workflowEngine.decide(input);

        assertThat(decision.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(decision.patchRequired()).isFalse();
    }

    @Test
    void unknownStateIsSkipped()
    {
        var decision = workflowEngine.decide(input("Closed", config(true), sme(), Map.of(), Map.of()));

        assertThat(decision.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(decision.patchRequired()).isFalse();
    }

    @Test
    void designClearsStaleApprovals()
    {
        var current = fields(SME_FIELD, "Ana <ana@example.com>", SQA_FIELD, "Sam <sam@example.com>");

        var decision = workflowEngine.decide(input("Design", config(true), sme(), Map.of(), current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(SME_FIELD, ""),
                PatchOperation.replaceField(SQA_FIELD, ""));
        assertThat(decision.comment()).isNull();
    }

    @Test
    void designWithEmptyApprovalsIsSkipped()
    {
        var current = fields(SME_FIELD, "", SQA_FIELD, null);

        var decision = workflowEngine.decide(input("Design", config(true), sme(), Map.of(), current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(decision.patchRequired()).isFalse();
    }

    @Test
    void designToApprovedForcesInReviewAndClearsApprovals()
    {
        var input = new WorkflowInput("ProjectA", 1, 2, "Test Case", "Design", "Approved", sme(), Map.of(), Map.of(),
                config(true));

        var decision = workflowEngine.decide(input);

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField("System.State", "In Review"),
                PatchOperation.replaceField(SME_FIELD, ""), PatchOperation.replaceField(SQA_FIELD, ""));
        assertThat(decision.comment()).contains("cannot move directly from Design to Approved");
    }

    @Test
    void nonApproverChangingReversibleFieldInReviewIsReverted()
    {
        var previous = fields(TITLE_FIELD, "Old title");
        var current = fields(TITLE_FIELD, "New title");

        var decision = workflowEngine.decide(input("In Review", config(true), nonApprover(), previous, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(TITLE_FIELD, "Old title"));
        assertThat(decision.comment()).contains("Proposed changes").contains("Modifier:\nNora User")
                .contains("* System.Title:").contains("Previous:\n  Old title")
                .contains("Proposed:\n  New title").contains("Action taken:")
                .contains("Reverted the configured field to its previous value.");
    }

    @Test
    void nonApproverChangingOnlyUnconfiguredFieldIsSkipped()
    {
        var previous = fields("Custom.Unconfigured", "Old");
        var current = fields("Custom.Unconfigured", "New");

        var decision = workflowEngine.decide(input("In Review", config(true), nonApprover(), previous, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(decision.patchRequired()).isFalse();
    }

    @Test
    void smeChangingReversibleFieldInReviewClearsApprovalsAndSetsSme()
    {
        var previous = fields(TITLE_FIELD, "Old", SME_FIELD, "Other <other@example.com>", SQA_FIELD,
                "Sam <sam@example.com>");
        var current = fields(TITLE_FIELD, "New", SME_FIELD, "Other <other@example.com>", SQA_FIELD,
                "Sam <sam@example.com>");

        var decision = workflowEngine.decide(input("In Review", config(true), sme(), previous, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(SME_FIELD, ""),
                PatchOperation.replaceField(SQA_FIELD, ""), PatchOperation.replaceField(SME_FIELD, "Ana Perez"));
        assertThat(decision.comment()).isNull();
    }

    @Test
    void sqaApprovingAfterSmeMovesStateToApproved()
    {
        var current = fields(SME_FIELD, "Ana Perez <ana@example.com>");

        var decision = workflowEngine.decide(input("In Review", config(true), sqa(), current, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(SQA_FIELD, "Sam Quality"),
                PatchOperation.replaceField("System.State", "Approved"));
        assertThat(decision.comment()).contains("Test Case approved automatically");
    }

    @Test
    void sqaApprovingAfterSmeMovesStateToConfiguredApprovedState()
    {
        var config = config(true, new WorkflowStateNames("Draft", "Peer Review", "Approval"));
        var current = fields(SME_FIELD, "Ana Perez <ana@example.com>");

        var decision = workflowEngine.decide(input("Peer Review", config, sqa(), current, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(SQA_FIELD, "Sam Quality"),
                PatchOperation.replaceField("System.State", "Approval"));
    }

    @Test
    void configuredApprovedStateIsRecognizedAsFinalized()
    {
        var config = config(true, new WorkflowStateNames("Draft", "Peer Review", "Approval"));
        var current = fields(SME_FIELD, "Ana Perez <ana@example.com>", SQA_FIELD, "Sam Quality <sam@example.com>");

        var decision = workflowEngine.decide(input("Approval", config, nonApprover(), current, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(decision.patchRequired()).isFalse();
    }

    @Test
    void configuredDesignStateIsRecognized()
    {
        var config = config(true, new WorkflowStateNames("Draft", "Peer Review", "Approval"));
        var current = fields(SME_FIELD, "Ana Perez <ana@example.com>");

        var decision = workflowEngine.decide(input("Draft", config, sme(), Map.of(), current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(SME_FIELD, ""));
    }

    @Test
    void configuredInReviewStateIsRecognized()
    {
        var config = config(true, new WorkflowStateNames("Draft", "Peer Review", "Approval"));
        var current = fields(SME_FIELD, "Ana Perez <ana@example.com>");

        var decision = workflowEngine.decide(input("Peer Review", config, sqa(), current, current));

        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(SQA_FIELD, "Sam Quality"),
                PatchOperation.replaceField("System.State", "Approval"));
    }

    @Test
    void inReviewWithCurrentSmeAlreadyPresentAndValidSqaMovesStateToApproved()
    {
        var current = fields(SME_FIELD, "Ana Perez <ana@example.com>", SQA_FIELD, "Sam Quality <sam@example.com>");

        var decision = workflowEngine.decide(input("In Review", config(true), sme(), current, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField("System.State", "Approved"));
        assertThat(decision.comment()).contains("Test Case approved automatically");
    }

    @Test
    void inReviewWithCurrentSqaAlreadyPresentAndValidSmeMovesStateToApproved()
    {
        var current = fields(SME_FIELD, "Ana Perez <ana@example.com>", SQA_FIELD, "Sam Quality <sam@example.com>");

        var decision = workflowEngine.decide(input("In Review", config(true), sqa(), current, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField("System.State", "Approved"));
        assertThat(decision.comment()).contains("Test Case approved automatically");
    }

    @Test
    void inReviewWithValidSmeAndInvalidSqaClearsInvalidSqaAndRemainsInReview()
    {
        var current = fields(SME_FIELD, "Ana Perez <ana@example.com>", SQA_FIELD, "Other User <other@example.com>");

        var decision = workflowEngine.decide(input("In Review", config(true), sme(), current, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(SQA_FIELD, ""));
        assertThat(decision.patchOperations()).extracting(PatchOperation::op).doesNotContain("remove");
        assertThat(decision.comment()).isNull();
    }

    @Test
    void inReviewWithInvalidSmeAndValidSqaClearsInvalidSmeAndRemainsInReview()
    {
        var current = fields(SME_FIELD, "Other User <other@example.com>", SQA_FIELD, "Sam Quality <sam@example.com>");

        var decision = workflowEngine.decide(input("In Review", config(true), sqa(), current, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(SME_FIELD, ""));
        assertThat(decision.comment()).isNull();
    }

    @Test
    void inReviewWithOnlyValidSmeRemainsInReviewWithoutPatch()
    {
        var current = fields(SME_FIELD, "Ana Perez <ana@example.com>");

        var decision = workflowEngine.decide(input("In Review", config(true), sme(), current, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(decision.patchRequired()).isFalse();
        assertThat(decision.reason()).isEqualTo("Approval already reflects current reviewer.");
    }

    @Test
    void inReviewWithOnlyValidSqaRemainsInReviewWithoutPatch()
    {
        var current = fields(SQA_FIELD, "Sam Quality <sam@example.com>");

        var decision = workflowEngine.decide(input("In Review", config(true), sqa(), current, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(decision.patchRequired()).isFalse();
        assertThat(decision.reason()).isEqualTo("Approval already reflects current reviewer.");
    }

    @Test
    void inReviewWithSameUserInBothApprovalFieldsDoesNotApproveAndClearsSqa()
    {
        var config = config(true, Set.of("dual@example.com"), Set.of("dual@example.com"));
        var current = fields(SME_FIELD, "Dual Role <dual@example.com>", SQA_FIELD, "Dual Role <dual@example.com>");

        var decision = workflowEngine.decide(input("In Review", config, dualRole(), current, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(SQA_FIELD, ""));
        assertThat(decision.patchOperations()).doesNotContain(PatchOperation.replaceField("System.State", "Approved"));
    }

    @Test
    void approvedWithMissingApprovalsIsForcedBackToInReview()
    {
        var decision = workflowEngine.decide(input("Approved", config(true), nonApprover(), Map.of(), Map.of()));

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField("System.State", "In Review"),
                PatchOperation.replaceField(SME_FIELD, ""), PatchOperation.replaceField(SQA_FIELD, ""));
        assertThat(decision.comment()).contains("does not have valid SME and SQA approvals");
    }

    @Test
    void sameUserInSmeAndSqaApprovalFieldsIsInvalid()
    {
        var current = fields(SME_FIELD, "Ana Perez <ana@example.com>", SQA_FIELD, "Ana Perez <ana@example.com>");
        var config = config(true, Set.of("ana@example.com"), Set.of("ana@example.com"));

        var decision = workflowEngine.decide(input("Approved", config, nonApprover(), current, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField("System.State", "In Review"),
                PatchOperation.replaceField(SQA_FIELD, ""));
    }

    @Test
    void manualApprovalFieldEditByNonApproverIsRestoredAndNotTreatedAsContent()
    {
        var previous = fields(SME_FIELD, "Ana Perez <ana@example.com>");
        var current = fields(SME_FIELD, "Nora User <nora@example.com>");

        var decision = workflowEngine.decide(input("In Review", config(true), nonApprover(), previous, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations())
                .containsExactly(PatchOperation.replaceField(SME_FIELD, "Ana Perez <ana@example.com>"));
        assertThat(decision.comment()).contains("Unauthorized Test Case modifications corrected.")
                .contains("* " + SME_FIELD + ":").contains("Approval fields are bot-owned")
                .contains("Previous:\n  Ana Perez <ana@example.com>")
                .contains("Proposed:\n  Nora User <nora@example.com>").contains("Action taken:");
    }

    @Test
    void manualApprovalFieldEditByApproverIsRestoredBeforeOwnApprovalIsRecorded()
    {
        var current = fields(SME_FIELD, "Manual Value <other@example.com>");

        var decision = workflowEngine.decide(input("In Review", config(true), sme(), Map.of(), current));

        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(SME_FIELD, ""),
                PatchOperation.replaceField(SME_FIELD, "Ana Perez"));
        assertThat(decision.comment())
                .contains("Cleared the approval field because no previous bot-owned value existed.");
    }

    @Test
    void smeCannotClaimSqaApprovalByManuallySettingApproverTest()
    {
        var previous = fields(SQA_FIELD, "Previous Test <previous@example.com>");
        var current = fields(SQA_FIELD, "Sam Quality <sam@example.com>");

        var decision = workflowEngine.decide(input("In Review", config(true), sme(), previous, current));

        assertThat(decision.patchOperations()).containsExactly(
                PatchOperation.replaceField(SQA_FIELD, "Previous Test <previous@example.com>"),
                PatchOperation.replaceField(SME_FIELD, "Ana Perez"), PatchOperation.replaceField(SQA_FIELD, ""));
        assertThat(decision.patchOperations())
                .doesNotContain(PatchOperation.replaceField("System.State", "Approved"));
        assertThat(decision.comment()).contains("* " + SQA_FIELD + ":")
                .contains("Previous:\n  Previous Test <previous@example.com>")
                .contains("Proposed:\n  Sam Quality <sam@example.com>")
                .contains("Restored the previous bot-owned approval value");
    }

    @Test
    void sqaCannotClaimSmeApprovalByManuallySettingApproverTech()
    {
        var previous = fields(SME_FIELD, "Previous Tech <previous@example.com>");
        var current = fields(SME_FIELD, "Ana Perez <ana@example.com>");

        var decision = workflowEngine.decide(input("In Review", config(true), sqa(), previous, current));

        assertThat(decision.patchOperations()).containsExactly(
                PatchOperation.replaceField(SME_FIELD, "Previous Tech <previous@example.com>"),
                PatchOperation.replaceField(SQA_FIELD, "Sam Quality"), PatchOperation.replaceField(SME_FIELD, ""));
        assertThat(decision.patchOperations())
                .doesNotContain(PatchOperation.replaceField("System.State", "Approved"));
        assertThat(decision.comment()).contains("* " + SME_FIELD + ":")
                .contains("Previous:\n  Previous Tech <previous@example.com>")
                .contains("Proposed:\n  Ana Perez <ana@example.com>")
                .contains("Restored the previous bot-owned approval value");
    }

    @Test
    void authorizedUserEditingBothApprovalFieldsCannotClaimOtherRole()
    {
        var current = fields(SME_FIELD, "Claimed SME <other-sme@example.com>", SQA_FIELD,
                "Sam Quality <sam@example.com>");

        var decision = workflowEngine.decide(input("In Review", config(true), sme(), Map.of(), current));

        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(SME_FIELD, ""),
                PatchOperation.replaceField(SQA_FIELD, ""), PatchOperation.replaceField(SME_FIELD, "Ana Perez"));
        assertThat(decision.patchOperations())
                .doesNotContain(PatchOperation.replaceField("System.State", "Approved"));
    }

    @Test
    void previousTrustedOtherRoleCombinesWithCurrentUsersOwnApproval()
    {
        var previous = fields(SQA_FIELD, "Sam Quality <sam@example.com>");
        var current = fields(SME_FIELD, "Claimed SME <other-sme@example.com>", SQA_FIELD,
                "Sam Quality <sam@example.com>");

        var decision = workflowEngine.decide(input("In Review", config(true), sme(), previous, current));

        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(SME_FIELD, ""),
                PatchOperation.replaceField(SME_FIELD, "Ana Perez"),
                PatchOperation.replaceField("System.State", "Approved"));
        assertThat(decision.comment()).contains("Unauthorized Test Case modifications corrected.")
                .contains("Test Case approved automatically.");
    }

    @Test
    void directApprovedTransitionCannotUseManuallyClaimedOtherRole()
    {
        var current = fields(SQA_FIELD, "Sam Quality <sam@example.com>");

        var decision = workflowEngine.decide(input("Approved", config(true), sme(), Map.of(), current));

        assertThat(decision.patchOperations()).containsExactly(
                PatchOperation.replaceField("System.State", "In Review"),
                PatchOperation.replaceField(SQA_FIELD, ""), PatchOperation.replaceField(SME_FIELD, "Ana Perez"));
    }

    @Test
    void whitespaceOnlyManualApprovalEditRestoresExactPreviousValue()
    {
        var previous = fields(SME_FIELD, "Ana Perez <ana@example.com>");
        var current = fields(SME_FIELD, "  Ana Perez <ana@example.com>  ");

        var decision = workflowEngine.decide(input("In Review", config(true), nonApprover(), previous, current));

        assertThat(decision.patchOperations())
                .containsExactly(PatchOperation.replaceField(SME_FIELD, "Ana Perez <ana@example.com>"));
    }

    @Test
    void approverChangingContentAndApprovalFieldsClearsPreviousApprovalsAndSetsCurrentUser()
    {
        var previous = fields(TITLE_FIELD, "Old", SME_FIELD, "Old SME <old-sme@example.com>", SQA_FIELD,
                "Sam Quality <sam@example.com>");
        var current = fields(TITLE_FIELD, "New", SME_FIELD, "Manual SME <manual@example.com>", SQA_FIELD,
                "Manual SQA <manual@example.com>");

        var decision = workflowEngine.decide(input("In Review", config(true), sme(), previous, current));

        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(SME_FIELD, ""),
                PatchOperation.replaceField(SQA_FIELD, ""), PatchOperation.replaceField(SME_FIELD, "Ana Perez"));
    }

    @Test
    void nonApproverChangingContentAndApprovalFieldsRevertsContentAndRestoresApprovals()
    {
        var previous = fields(TITLE_FIELD, "Old", SME_FIELD, "Ana Perez <ana@example.com>");
        var current = fields(TITLE_FIELD, "New", SME_FIELD, "Nora User <nora@example.com>");

        var decision = workflowEngine.decide(input("In Review", config(true), nonApprover(), previous, current));

        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(TITLE_FIELD, "Old"),
                PatchOperation.replaceField(SME_FIELD, "Ana Perez <ana@example.com>"));
        assertThat(decision.comment()).containsOnlyOnce("Unauthorized Test Case modifications corrected.")
                .contains("* " + SME_FIELD + ":").contains("* " + TITLE_FIELD + ":")
                .contains("Previous:\n  Ana Perez <ana@example.com>")
                .contains("Proposed:\n  Nora User <nora@example.com>").contains("Previous:\n  Old")
                .contains("Proposed:\n  New");
    }

    @Test
    void dualRoleUserWithNoApprovalsIsRecordedAsSmeFirst()
    {
        var config = config(true, Set.of("dual@example.com"), Set.of("dual@example.com"));

        var decision = workflowEngine.decide(input("In Review", config, dualRole(), Map.of(), Map.of()));

        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(SME_FIELD, "Dual Role"));
    }

    @Test
    void smeApprovalClassificationUsesEmailLoginAndPatchValueUsesDisplayNameScalar()
    {
        var changedBy = new Identity("Yunier Perez", "U129670@EXAMPLE.COM");
        var config = config(true, Set.of("u129670@example.com"), Set.of("sam@example.com"));

        var decision = workflowEngine.decide(input("In Review", config, changedBy, Map.of(), Map.of()));

        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(SME_FIELD, "Yunier Perez"));
        assertThat(decision.patchOperations().getFirst().value()).isInstanceOf(String.class).isNotEqualTo(changedBy)
                .asString().doesNotContain("displayName").doesNotContain("u129670@example.com");
    }

    @Test
    void sqaApprovalClassificationUsesEmailLoginAndPatchValueUsesDisplayNameScalar()
    {
        var changedBy = new Identity("SQA Display", "SQA@EXAMPLE.COM");
        var config = config(true, Set.of("ana@example.com"), Set.of("sqa@example.com"));

        var decision = workflowEngine.decide(input("In Review", config, changedBy, Map.of(), Map.of()));

        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(SQA_FIELD, "SQA Display"));
        assertThat(decision.patchOperations().getFirst().value()).isInstanceOf(String.class).isNotEqualTo(changedBy)
                .asString().doesNotContain("displayName").doesNotContain("sqa@example.com");
    }

    @Test
    void displayNameDoesNotAuthorizeWhenEmailLoginDoesNotMatchConfiguredApprover()
    {
        var changedBy = new Identity("Ana Perez", "nora@example.com");

        var decision = workflowEngine.decide(input("In Review", config(true), changedBy, Map.of(), Map.of()));

        assertThat(decision.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(decision.patchRequired()).isFalse();
    }

    @Test
    void blankDisplayNameFallsBackToNormalizedEmailLoginForApprovalPatchValue()
    {
        var changedBy = new Identity("  ", " U129670@EXAMPLE.COM ");
        var config = config(true, Set.of("u129670@example.com"), Set.of("sam@example.com"));

        var decision = workflowEngine.decide(input("In Review", config, changedBy, Map.of(), Map.of()));

        assertThat(decision.patchOperations())
                .containsExactly(PatchOperation.replaceField(SME_FIELD, "u129670@example.com"));
        assertThat(decision.patchOperations().getFirst().value()).isInstanceOf(String.class);
    }

    @Test
    void dualRoleUserAlreadyInSmeMustNotFillSqaForSameVersion()
    {
        var config = config(true, Set.of("dual@example.com"), Set.of("dual@example.com"));
        var current = fields(SME_FIELD, "Dual Role <dual@example.com>");

        var decision = workflowEngine.decide(input("In Review", config, dualRole(), current, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(decision.patchRequired()).isFalse();
    }

    @Test
    void existingValidApprovalsFromDifferentUsersMakeApprovedValid()
    {
        var current = fields(SME_FIELD, "Ana Perez <ana@example.com>", SQA_FIELD, "Sam Quality <sam@example.com>");

        var decision = workflowEngine.decide(input("Approved", config(true), nonApprover(), current, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(decision.patchRequired()).isFalse();
    }

    @Test
    void existingValidBareEmailApprovalsFromDifferentUsersMakeApprovedValid()
    {
        var current = fields(SME_FIELD, "ana@example.com", SQA_FIELD, "sam@example.com");

        var decision = workflowEngine.decide(input("Approved", config(true), nonApprover(), current, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(decision.patchRequired()).isFalse();
    }

    @Test
    void existingAdoIdentityObjectApprovalsFromDifferentUsersMakeApprovedValid()
    {
        var current = fields(SME_FIELD, new AdoIdentity("Ana Perez", "ana@example.com"), SQA_FIELD,
                new AdoIdentity("Sam Quality", "sam@example.com"));

        var decision = workflowEngine.decide(input("Approved", config(true), nonApprover(), current, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(decision.patchRequired()).isFalse();
    }

    @Test
    void existingMapIdentityApprovalValuesUseEmailForValidation()
    {
        var current = fields(SME_FIELD, Map.of("displayName", "Ana Perez", "uniqueName", "ana@example.com"), SQA_FIELD,
                Map.of("displayName", "Sam Quality", "mailAddress", "sam@example.com"));

        var decision = workflowEngine.decide(input("Approved", config(true), nonApprover(), current, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(decision.patchRequired()).isFalse();
    }

    @Test
    void approvalValueWithoutExtractableEmailIsRejected()
    {
        var current = fields(SME_FIELD, "Ana Perez", SQA_FIELD, "Sam Quality <sam@example.com>");

        var decision = workflowEngine.decide(input("Approved", config(true), nonApprover(), current, current));

        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField("System.State", "In Review"),
                PatchOperation.replaceField(SME_FIELD, ""));
    }

    @Test
    void approvalEmailOutsideRoleListIsRejected()
    {
        var current = fields(SME_FIELD, "Other <other@example.com>", SQA_FIELD, "Sam Quality <sam@example.com>");

        var decision = workflowEngine.decide(input("Approved", config(true), nonApprover(), current, current));

        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField("System.State", "In Review"),
                PatchOperation.replaceField(SME_FIELD, ""));
    }

    @Test
    void botGeneratedEventIsSkippedWhenChangedByEmailMatchesConfiguredBotEmail()
    {
        var previous = fields(TITLE_FIELD, "Old");
        var current = fields(TITLE_FIELD, "New");

        var decision = workflowEngine.decide(
                input("In Review", config(true), new Identity("Approval Bot", "bot@example.com"), previous, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(decision.patchRequired()).isFalse();
    }

    @Test
    void botIdentityDoesNotMatchByDisplayNameWhenEmailIsMissing()
    {
        var previous = fields(TITLE_FIELD, "Old");
        var current = fields(TITLE_FIELD, "New");

        var decision = workflowEngine
                .decide(input("In Review", config(true), new Identity("Approval Bot", null), previous, current));

        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(TITLE_FIELD, "Old"));
    }

    @Test
    void previousRawValueWithLeadingAndTrailingSpacesIsPreservedWhenReverting()
    {
        var previous = fields(TITLE_FIELD, "  Old title  ");
        var current = fields(TITLE_FIELD, "New title");

        var decision = workflowEngine.decide(input("In Review", config(true), nonApprover(), previous, current));

        assertThat(decision.patchOperations())
                .containsExactly(PatchOperation.replaceField(TITLE_FIELD, "  Old title  "));
    }

    @Test
    void missingPreviousFieldAndCurrentNonEmptyFieldRevertsToNull()
    {
        var current = fields(TITLE_FIELD, "New title");

        var decision = workflowEngine.decide(input("In Review", config(true), nonApprover(), Map.of(), current));

        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(TITLE_FIELD, null));
    }

    @Test
    void previousNonEmptyFieldAndCurrentEmptyFieldIsDetectedAsRealChange()
    {
        var previous = fields(TITLE_FIELD, "Old title");
        var current = fields(TITLE_FIELD, "");

        var decision = workflowEngine.decide(input("In Review", config(true), nonApprover(), previous, current));

        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(TITLE_FIELD, "Old title"));
    }

    @Test
    void systemStateChangeAloneIsNotTreatedAsContentChange()
    {
        var previous = fields("System.State", "In Review");
        var current = fields("System.State", "Approved");

        var decision = workflowEngine.decide(input("In Review", config(true), nonApprover(), previous, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.SKIPPED);
    }

    @Test
    void approvalFieldChangeAloneIsNotTreatedAsContentChange()
    {
        var previous = fields(SME_FIELD, "Ana Perez <ana@example.com>");
        var current = fields(SME_FIELD, "Nora User <nora@example.com>");

        assertThat(new ChangeAnalyzer(new ValueComparator()).changedReversibleFields(previous, current, config(true)))
                .isEmpty();
    }

    @Test
    void missingChangedByEmailCannotMatchSmeOrSqa()
    {
        var previous = fields(TITLE_FIELD, "Old");
        var current = fields(TITLE_FIELD, "New");

        var decision = workflowEngine
                .decide(input("In Review", config(true), new Identity("Ana Perez", null), previous, current));

        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(TITLE_FIELD, "Old"));
    }

    @Test
    void valueComparatorTreatsNullAndEmptyAsEqual()
    {
        assertThat(new ValueComparator().equivalent(null, "")).isTrue();
    }

    @Test
    void valueComparatorTrimsLeadingAndTrailingWhitespace()
    {
        assertThat(new ValueComparator().equivalent(" value ", "value")).isTrue();
    }

    @Test
    void changeAnalyzerIgnoresUnconfiguredFields()
    {
        var analyzer = new ChangeAnalyzer(new ValueComparator());
        var previous = fields("Custom.Other", "Old");
        var current = fields("Custom.Other", "New");

        assertThat(analyzer.changedReversibleFields(previous, current, config(true))).isEmpty();
    }

    private WorkflowInput input(String currentState, ProjectApprovalConfig config, Identity changedBy,
            Map<String, Object> previousFields, Map<String, Object> currentFields)
    {
        return new WorkflowInput("ProjectA", 1, 1, "Test Case", "In Review", currentState, changedBy, previousFields,
                currentFields, config);
    }

    private ProjectApprovalConfig config(boolean enabled)
    {
        return config(enabled, Set.of("ana@example.com"), Set.of("sam@example.com"));
    }

    private ProjectApprovalConfig config(boolean enabled, WorkflowStateNames stateNames)
    {
        return config(enabled, Set.of("ana@example.com"), Set.of("sam@example.com"), stateNames);
    }

    private ProjectApprovalConfig config(boolean enabled, Set<String> smeUsers, Set<String> sqaUsers)
    {
        return config(enabled, smeUsers, sqaUsers, WorkflowStateNames.defaults());
    }

    private ProjectApprovalConfig config(boolean enabled, Set<String> smeUsers, Set<String> sqaUsers,
            WorkflowStateNames stateNames)
    {
        return new ProjectApprovalConfig("ProjectA", enabled, Set.of("Test Case"), SME_FIELD, SQA_FIELD,
                Set.of(TITLE_FIELD, DESCRIPTION_FIELD), smeUsers, sqaUsers, "bot@example.com", stateNames);
    }

    private Identity sme()
    {
        return new Identity("Ana Perez", "ana@example.com");
    }

    private Identity sqa()
    {
        return new Identity("Sam Quality", "sam@example.com");
    }

    private Identity nonApprover()
    {
        return new Identity("Nora User", "nora@example.com");
    }

    private Identity dualRole()
    {
        return new Identity("Dual Role", "dual@example.com");
    }

    private Map<String, Object> fields(Object... entries)
    {
        var fields = new HashMap<String, Object>();
        for (int i = 0; i < entries.length; i += 2)
        {
            fields.put((String) entries[i], entries[i + 1]);
        }
        return fields;
    }
}
