package com.dentalwings.approvalbot.workflow.patch;

import com.dentalwings.approvalbot.domain.PatchOperation;
import com.dentalwings.approvalbot.domain.WorkflowDecision;
import java.util.ArrayList;
import java.util.List;

public class PatchBuilder {

    public List<PatchOperation> build(int revision, WorkflowDecision decision) {
        var operations = new ArrayList<PatchOperation>();
        operations.add(PatchOperation.testRevision(revision));

        for (PatchOperation operation : decision.patchOperations()) {
            operations.add(new PatchOperation("replace", operation.path(), operation.value()));
        }

        return List.copyOf(operations);
    }
}
