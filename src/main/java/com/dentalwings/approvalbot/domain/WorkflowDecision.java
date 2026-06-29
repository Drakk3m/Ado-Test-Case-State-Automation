package com.dentalwings.approvalbot.domain;

import java.util.List;

public record WorkflowDecision(ProcessingResult result, boolean patchRequired, List<PatchOperation> patchOperations,
                               String comment, String reason)
{

    public static WorkflowDecision skipped(String reason)
    {
        return new WorkflowDecision(ProcessingResult.SKIPPED, false, List.of(), null, reason);
    }

    public static WorkflowDecision completed(List<PatchOperation> patchOperations, String comment, String reason)
    {
        return new WorkflowDecision(ProcessingResult.COMPLETED, !patchOperations.isEmpty(),
                List.copyOf(patchOperations), comment, reason);
    }
}
