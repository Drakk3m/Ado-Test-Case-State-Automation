package com.dentalwings.approvalbot.workflow;

import com.dentalwings.approvalbot.config.ProjectApprovalConfig;
import com.dentalwings.approvalbot.domain.Identity;
import com.dentalwings.approvalbot.domain.PatchOperation;
import com.dentalwings.approvalbot.domain.ProcessingResult;
import com.dentalwings.approvalbot.domain.WorkflowInput;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowEngineTest {

    private static final String SME_FIELD = "Custom.ApprovedBySME";
    private static final String SQA_FIELD = "Custom.ApprovedBySQA";
    private static final String TITLE_FIELD = "System.Title";
    private static final String DESCRIPTION_FIELD = "System.Description";

    private final WorkflowEngine workflowEngine = new WorkflowEngine();

    @Test
    void disabledProjectIsSkipped() {
        var decision = workflowEngine.decide(input("In Review", config(false), sme(), Map.of(), Map.of()));

        assertThat(decision.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(decision.patchRequired()).isFalse();
    }

    @Test
    void unsupportedWorkItemTypeIsSkipped() {
        var input = new WorkflowInput("ProjectA", 1, 1, "Bug", "Design", "In Review", sme(), Map.of(), Map.of(), config(true));

        var decision = workflowEngine.decide(input);

        assertThat(decision.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(decision.patchRequired()).isFalse();
    }

    @Test
    void unknownStateIsSkipped() {
        var decision = workflowEngine.decide(input("Closed", config(true), sme(), Map.of(), Map.of()));

        assertThat(decision.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(decision.patchRequired()).isFalse();
    }

    @Test
    void designClearsStaleApprovals() {
        var current = fields(SME_FIELD, "Ana <ana@example.com>", SQA_FIELD, "Sam <sam@example.com>");

        var decision = workflowEngine.decide(input("Design", config(true), sme(), Map.of(), current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations()).containsExactly(
                PatchOperation.replaceField(SME_FIELD, null),
                PatchOperation.replaceField(SQA_FIELD, null)
        );
        assertThat(decision.comment()).isNull();
    }

    @Test
    void designWithEmptyApprovalsIsSkipped() {
        var current = fields(SME_FIELD, "", SQA_FIELD, null);

        var decision = workflowEngine.decide(input("Design", config(true), sme(), Map.of(), current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(decision.patchRequired()).isFalse();
    }

    @Test
    void designToApprovedForcesInReviewAndClearsApprovals() {
        var input = new WorkflowInput("ProjectA", 1, 2, "Test Case", "Design", "Approved", sme(), Map.of(), Map.of(), config(true));

        var decision = workflowEngine.decide(input);

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations()).containsExactly(
                PatchOperation.replaceField("System.State", "In Review"),
                PatchOperation.replaceField(SME_FIELD, null),
                PatchOperation.replaceField(SQA_FIELD, null)
        );
        assertThat(decision.comment()).contains("cannot move directly from Design to Approved");
    }

    @Test
    void nonApproverChangingReversibleFieldInReviewIsReverted() {
        var previous = fields(TITLE_FIELD, "Old title");
        var current = fields(TITLE_FIELD, "New title");

        var decision = workflowEngine.decide(input("In Review", config(true), nonApprover(), previous, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(TITLE_FIELD, "Old title"));
        assertThat(decision.comment()).contains("Proposed changes");
    }

    @Test
    void nonApproverChangingOnlyUnconfiguredFieldIsSkipped() {
        var previous = fields("Custom.Unconfigured", "Old");
        var current = fields("Custom.Unconfigured", "New");

        var decision = workflowEngine.decide(input("In Review", config(true), nonApprover(), previous, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(decision.patchRequired()).isFalse();
    }

    @Test
    void smeChangingReversibleFieldInReviewClearsApprovalsAndSetsSme() {
        var previous = fields(TITLE_FIELD, "Old", SME_FIELD, "Other <other@example.com>", SQA_FIELD, "Sam <sam@example.com>");
        var current = fields(TITLE_FIELD, "New", SME_FIELD, "Other <other@example.com>", SQA_FIELD, "Sam <sam@example.com>");

        var decision = workflowEngine.decide(input("In Review", config(true), sme(), previous, current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations()).containsExactly(
                PatchOperation.replaceField(SME_FIELD, null),
                PatchOperation.replaceField(SQA_FIELD, null),
                PatchOperation.replaceField(SME_FIELD, "Ana Perez <ana@example.com>")
        );
        assertThat(decision.comment()).isNull();
    }

    @Test
    void sqaApprovingAfterSmeMovesStateToApproved() {
        var current = fields(SME_FIELD, "Ana Perez <ana@example.com>");

        var decision = workflowEngine.decide(input("In Review", config(true), sqa(), Map.of(), current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations()).containsExactly(
                PatchOperation.replaceField(SQA_FIELD, "Sam Quality <sam@example.com>"),
                PatchOperation.replaceField("System.State", "Approved")
        );
        assertThat(decision.comment()).contains("Test Case approved automatically");
    }

    @Test
    void approvedWithMissingApprovalsIsForcedBackToInReview() {
        var decision = workflowEngine.decide(input("Approved", config(true), nonApprover(), Map.of(), Map.of()));

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations()).containsExactly(
                PatchOperation.replaceField("System.State", "In Review"),
                PatchOperation.replaceField(SME_FIELD, null),
                PatchOperation.replaceField(SQA_FIELD, null)
        );
        assertThat(decision.comment()).contains("does not have valid SME and SQA approvals");
    }

    @Test
    void sameUserInSmeAndSqaApprovalFieldsIsInvalid() {
        var current = fields(SME_FIELD, "Ana Perez <ana@example.com>", SQA_FIELD, "Ana Perez <ana@example.com>");
        var config = config(true, Set.of("ana@example.com"), Set.of("ana@example.com"));

        var decision = workflowEngine.decide(input("Approved", config, nonApprover(), Map.of(), current));

        assertThat(decision.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(decision.patchOperations()).containsExactly(
                PatchOperation.replaceField("System.State", "In Review"),
                PatchOperation.replaceField(SME_FIELD, null),
                PatchOperation.replaceField(SQA_FIELD, null)
        );
    }

    @Test
    void missingChangedByEmailCannotMatchSmeOrSqa() {
        var previous = fields(TITLE_FIELD, "Old");
        var current = fields(TITLE_FIELD, "New");

        var decision = workflowEngine.decide(input("In Review", config(true), new Identity("Ana Perez", null), previous, current));

        assertThat(decision.patchOperations()).containsExactly(PatchOperation.replaceField(TITLE_FIELD, "Old"));
    }

    @Test
    void valueComparatorTreatsNullAndEmptyAsEqual() {
        assertThat(new ValueComparator().equivalent(null, "")).isTrue();
    }

    @Test
    void valueComparatorTrimsLeadingAndTrailingWhitespace() {
        assertThat(new ValueComparator().equivalent(" value ", "value")).isTrue();
    }

    @Test
    void changeAnalyzerIgnoresUnconfiguredFields() {
        var analyzer = new ChangeAnalyzer(new ValueComparator());
        var previous = fields("Custom.Other", "Old");
        var current = fields("Custom.Other", "New");

        assertThat(analyzer.changedReversibleFields(previous, current, config(true))).isEmpty();
    }

    private WorkflowInput input(String currentState, ProjectApprovalConfig config, Identity changedBy, Map<String, Object> previousFields, Map<String, Object> currentFields) {
        return new WorkflowInput("ProjectA", 1, 1, "Test Case", "In Review", currentState, changedBy, previousFields, currentFields, config);
    }

    private ProjectApprovalConfig config(boolean enabled) {
        return config(enabled, Set.of("ana@example.com"), Set.of("sam@example.com"));
    }

    private ProjectApprovalConfig config(boolean enabled, Set<String> smeUsers, Set<String> sqaUsers) {
        return new ProjectApprovalConfig(
                "ProjectA",
                enabled,
                Set.of("Test Case"),
                SME_FIELD,
                SQA_FIELD,
                Set.of(TITLE_FIELD, DESCRIPTION_FIELD),
                smeUsers,
                sqaUsers
        );
    }

    private Identity sme() {
        return new Identity("Ana Perez", "ana@example.com");
    }

    private Identity sqa() {
        return new Identity("Sam Quality", "sam@example.com");
    }

    private Identity nonApprover() {
        return new Identity("Nora User", "nora@example.com");
    }

    private Map<String, Object> fields(Object... entries) {
        var fields = new HashMap<String, Object>();
        for (int i = 0; i < entries.length; i += 2) {
            fields.put((String) entries[i], entries[i + 1]);
        }
        return fields;
    }
}
