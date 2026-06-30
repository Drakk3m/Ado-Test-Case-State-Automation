package com.dentalwings.approvalbot.workflow.comment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import com.dentalwings.approvalbot.domain.Identity;
import com.dentalwings.approvalbot.domain.PatchOperation;
import com.dentalwings.approvalbot.domain.WorkflowDecision;
import com.dentalwings.approvalbot.workflow.comment.CommentBuilder.ChangedField;
import com.dentalwings.approvalbot.workflow.comment.CommentBuilder.RejectedChange;

class CommentBuilderTest
{

    private final CommentBuilder commentBuilder = new CommentBuilder();

    @Test
    void approvedAutomaticallyCommentIncludesSmeAndSqaApprovalValues()
    {
        var comment = commentBuilder.approvedAutomatically("Ana Perez <ana.perez@company.com>",
                "Carlos Gomez <carlos.gomez@company.com>");

        assertThat(comment).contains("Test Case approved automatically.");
        assertThat(comment).contains("Approved by SME:\nAna Perez <ana.perez@company.com>");
        assertThat(comment).contains("Approved by SQA:\nCarlos Gomez <carlos.gomez@company.com>");
    }

    @Test
    void proposedChangesCommentIncludesVerifiedDisplayNameAndEmailWhenEmailExists()
    {
        var comment = commentBuilder.proposedChanges(new Identity("John Doe", "john.doe@company.com"),
                List.of(new ChangedField("System.Title", "Old", "New")));

        assertThat(comment)
                .contains("Proposed changes by John Doe [john.doe@company.com](mailto:john.doe@company.com).");
        assertThat(comment).contains("this user is not an SME/SQA approver");
    }

    @Test
    void proposedChangesCommentOmitsEmailAndUsesUnverifiedWordingWhenEmailIsMissing()
    {
        var comment = commentBuilder.proposedChanges(new Identity("John Doe", null),
                List.of(new ChangedField("System.Title", "Old", "New")));

        assertThat(comment).contains("Proposed changes by John Doe.");
        assertThat(comment).doesNotContain("mailto:");
        assertThat(comment).contains("this user could not be verified as an SME/SQA approver");
    }

    @Test
    void proposedChangesCommentListsChangedFieldsDeterministically()
    {
        var comment = commentBuilder.proposedChanges(new Identity("John Doe", "john.doe@company.com"),
                List.of(new ChangedField("System.Title", "Old title", "New title"),
                        new ChangedField("Microsoft.VSTS.TCM.Steps", "Old steps", "New steps")));

        assertThat(comment).containsSubsequence("* Microsoft.VSTS.TCM.Steps:", "* System.Title:");
    }

    @Test
    void proposedChangesCommentPreservesRawPreviousAndProposedValues()
    {
        var comment = commentBuilder.proposedChanges(new Identity("John Doe", "john.doe@company.com"),
                List.of(new ChangedField("System.Title", "  Old raw value  ", "  New raw value  ")));

        assertThat(comment).contains("  Old raw value  ");
        assertThat(comment).contains("  New raw value  ");
    }

    @Test
    void proposedChangesCommentHandlesNullPreviousAndProposedValuesClearly()
    {
        var comment = commentBuilder.proposedChanges(new Identity("John Doe", "john.doe@company.com"),
                List.of(new ChangedField("System.Title", null, null)));

        assertThat(comment).contains("Previous:\n  (null)");
        assertThat(comment).contains("Proposed:\n  (null)");
    }

    @Test
    void unauthorizedModificationCommentIncludesDeterministicReasonsValuesAndActions()
    {
        var comment = commentBuilder.unauthorizedModifications(new Identity("John Doe", "john.doe@company.com"),
                List.of(
                        new RejectedChange("System.Title", "Old title", "New title", "Protected field.",
                                "Reverted to the previous value."),
                        new RejectedChange("Custom.ApproverTest", null, "Claimed User", "Bot-owned field.",
                                "Cleared the field.")));

        assertThat(comment).contains("Unauthorized Test Case modifications corrected.")
                .contains("Modifier:\nJohn Doe [john.doe@company.com](mailto:john.doe@company.com)")
                .containsSubsequence("* Custom.ApproverTest:", "* System.Title:")
                .contains("Reason:\n  Bot-owned field.").contains("Previous:\n  (null)")
                .contains("Proposed:\n  Claimed User").contains("Action taken:\n  Cleared the field.");
    }

    @Test
    void manualApprovalFieldCorrectionCommentMatchesV1Wording()
    {
        assertThat(commentBuilder.manualApprovalFieldEditCorrected()).isEqualTo(
                """
                        Manual approval field edit corrected.
                        
                        Approval fields are controlled by the approval automation.
                        The manually entered approval value was ignored and approval fields were recalculated from the actual reviewer identity.""");
    }

    @Test
    void unauthorizedApprovalActionRejectedCommentMatchesV1Wording()
    {
        assertThat(commentBuilder.unauthorizedApprovalActionRejected()).isEqualTo("""
                The approval action was rejected because the user identity could not be verified as an SME/SQA approver.
                
                The Test Case was returned to In Review because approval requires verified SME and SQA approvers.""");
    }

    @Test
    void invalidApprovedCorrectionCommentMatchesV1Wording()
    {
        assertThat(commentBuilder.invalidApprovedCorrection()).isEqualTo(
                "The Test Case was returned to In Review because it does not have valid SME and SQA approvals from different users.");
    }

    @Test
    void skippedOrNoOpDecisionReturnsNoComment()
    {
        assertThat(commentBuilder.fromDecision(WorkflowDecision.skipped("No action"))).isEmpty();
        assertThat(commentBuilder
                .fromDecision(WorkflowDecision.completed(List.of(), "Comment should not be emitted", "No patch")))
                .isEmpty();
    }

    @Test
    void commentBuilderDoesNotDependOnHttpOrAdoClientClasses()
    {
        assertThat(CommentBuilder.class.getDeclaredFields()).isEmpty();
        assertThat(CommentBuilder.class.getName()).doesNotContain("client");
        assertThat(commentBuilder.fromDecision(
                WorkflowDecision.completed(List.of(PatchOperation.replaceField("System.State", "Approved")),
                        "Existing workflow comment", "Completed"))).contains("Existing workflow comment");
    }
}
