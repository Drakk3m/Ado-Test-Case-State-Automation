package com.dentalwings.approvalbot.workflow.patch;

import com.dentalwings.approvalbot.domain.PatchOperation;
import com.dentalwings.approvalbot.domain.WorkflowDecision;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatchBuilderTest {

    private static final String SME_FIELD = "Custom.ApprovedBySME";
    private static final String SQA_FIELD = "Custom.ApprovedBySQA";
    private static final String TITLE_FIELD = "System.Title";

    private final PatchBuilder patchBuilder = new PatchBuilder();

    @Test
    void generatedPatchAlwaysStartsWithRevTest() {
        var patch = patchBuilder.build(27, decision(PatchOperation.replaceField("System.State", "In Review")));

        assertThat(patch.get(0)).isEqualTo(new PatchOperation("test", "/rev", 27));
    }

    @Test
    void stateReplacementIsGeneratedCorrectly() {
        var patch = patchBuilder.build(27, decision(PatchOperation.replaceField("System.State", "In Review")));

        assertThat(patch).containsExactly(
                PatchOperation.testRevision(27),
                new PatchOperation("replace", "/fields/System.State", "In Review")
        );
    }

    @Test
    void smeApprovalFieldReplacementIsGeneratedCorrectly() {
        var patch = patchBuilder.build(27, decision(PatchOperation.replaceField(SME_FIELD, "Ana Perez <ana@example.com>")));

        assertThat(patch).containsExactly(
                PatchOperation.testRevision(27),
                new PatchOperation("replace", "/fields/" + SME_FIELD, "Ana Perez <ana@example.com>")
        );
    }

    @Test
    void sqaApprovalFieldReplacementIsGeneratedCorrectly() {
        var patch = patchBuilder.build(27, decision(PatchOperation.replaceField(SQA_FIELD, "Sam Quality <sam@example.com>")));

        assertThat(patch).containsExactly(
                PatchOperation.testRevision(27),
                new PatchOperation("replace", "/fields/" + SQA_FIELD, "Sam Quality <sam@example.com>")
        );
    }

    @Test
    void approvalFieldClearUsesReplaceWithNull() {
        var patch = patchBuilder.build(27, decision(PatchOperation.replaceField(SME_FIELD, null)));

        assertThat(patch).containsExactly(
                PatchOperation.testRevision(27),
                new PatchOperation("replace", "/fields/" + SME_FIELD, null)
        );
    }

    @Test
    void reversibleBusinessFieldRevertUsesExactPreviousRawValue() {
        var patch = patchBuilder.build(27, decision(PatchOperation.replaceField(TITLE_FIELD, "Previous title")));

        assertThat(patch.get(1).value()).isEqualTo("Previous title");
    }

    @Test
    void previousRawValueWithLeadingAndTrailingSpacesIsPreserved() {
        var patch = patchBuilder.build(27, decision(PatchOperation.replaceField(TITLE_FIELD, "  Previous title  ")));

        assertThat(patch.get(1).value()).isEqualTo("  Previous title  ");
    }

    @Test
    void missingPreviousFieldValueGeneratesReplaceWithNull() {
        var patch = patchBuilder.build(27, decision(PatchOperation.replaceField(TITLE_FIELD, null)));

        assertThat(patch).containsExactly(
                PatchOperation.testRevision(27),
                new PatchOperation("replace", "/fields/" + TITLE_FIELD, null)
        );
    }

    @Test
    void generatedOperationsNeverUseRemove() {
        var patch = patchBuilder.build(27, decision(new PatchOperation("remove", "/fields/" + TITLE_FIELD, null)));

        assertThat(patch).extracting(PatchOperation::op).doesNotContain("remove");
    }

    @Test
    void emptyWorkflowDecisionProducesNoFieldReplacements() {
        var patch = patchBuilder.build(27, WorkflowDecision.skipped("No action"));

        assertThat(patch).containsExactly(PatchOperation.testRevision(27));
    }

    @Test
    void operationOrderIsDeterministic() {
        var patch = patchBuilder.build(27, decision(
                PatchOperation.replaceField("System.State", "In Review"),
                PatchOperation.replaceField(SME_FIELD, null),
                PatchOperation.replaceField(SQA_FIELD, "Sam Quality <sam@example.com>"),
                PatchOperation.replaceField(TITLE_FIELD, "Previous title")
        ));

        assertThat(patch).containsExactly(
                PatchOperation.testRevision(27),
                new PatchOperation("replace", "/fields/System.State", "In Review"),
                new PatchOperation("replace", "/fields/" + SME_FIELD, null),
                new PatchOperation("replace", "/fields/" + SQA_FIELD, "Sam Quality <sam@example.com>"),
                new PatchOperation("replace", "/fields/" + TITLE_FIELD, "Previous title")
        );
    }

    private WorkflowDecision decision(PatchOperation... operations) {
        return WorkflowDecision.completed(List.of(operations), null, "test decision");
    }
}
