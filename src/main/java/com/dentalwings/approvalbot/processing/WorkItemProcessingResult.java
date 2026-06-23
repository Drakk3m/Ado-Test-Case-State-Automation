package com.dentalwings.approvalbot.processing;

import com.dentalwings.approvalbot.domain.ProcessingResult;
import com.dentalwings.approvalbot.domain.WorkflowDecision;
import java.util.Optional;

public record WorkItemProcessingResult(
        ProcessingResult result,
        String reason,
        WorkflowDecision workflowDecision
) {

    public static WorkItemProcessingResult skipped(String reason, WorkflowDecision workflowDecision) {
        return new WorkItemProcessingResult(ProcessingResult.SKIPPED, reason, workflowDecision);
    }

    public static WorkItemProcessingResult completed(String reason, WorkflowDecision workflowDecision) {
        return new WorkItemProcessingResult(ProcessingResult.COMPLETED, reason, workflowDecision);
    }

    public static WorkItemProcessingResult completedWithWarning(String reason, WorkflowDecision workflowDecision) {
        return new WorkItemProcessingResult(ProcessingResult.COMPLETED_WITH_WARNING, reason, workflowDecision);
    }

    public static WorkItemProcessingResult failedRetryable(String reason, WorkflowDecision workflowDecision) {
        return new WorkItemProcessingResult(ProcessingResult.FAILED_RETRYABLE, reason, workflowDecision);
    }

    public static WorkItemProcessingResult failedNonRetryable(String reason, WorkflowDecision workflowDecision) {
        return new WorkItemProcessingResult(ProcessingResult.FAILED_NON_RETRYABLE, reason, workflowDecision);
    }

    public Optional<WorkflowDecision> maybeWorkflowDecision() {
        return Optional.ofNullable(workflowDecision);
    }
}
