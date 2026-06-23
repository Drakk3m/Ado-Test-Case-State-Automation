package com.dentalwings.approvalbot.processing;

import com.dentalwings.approvalbot.ado.AdoClient;
import com.dentalwings.approvalbot.ado.AdoIdentity;
import com.dentalwings.approvalbot.ado.AdoPatchResult;
import com.dentalwings.approvalbot.ado.AdoWorkItem;
import com.dentalwings.approvalbot.ado.AdoWorkItemRevision;
import com.dentalwings.approvalbot.domain.Identity;
import com.dentalwings.approvalbot.domain.PatchOperation;
import com.dentalwings.approvalbot.domain.WorkflowDecision;
import com.dentalwings.approvalbot.domain.WorkflowInput;
import com.dentalwings.approvalbot.workflow.WorkflowEngine;
import com.dentalwings.approvalbot.workflow.comment.CommentBuilder;
import com.dentalwings.approvalbot.workflow.patch.PatchBuilder;
import java.util.List;
import java.util.Map;

public class WorkItemProcessingService {

    private final AdoClient adoClient;
    private final WorkflowEngine workflowEngine;
    private final PatchBuilder patchBuilder;
    private final CommentBuilder commentBuilder;

    public WorkItemProcessingService(
            AdoClient adoClient,
            WorkflowEngine workflowEngine,
            PatchBuilder patchBuilder,
            CommentBuilder commentBuilder
    ) {
        this.adoClient = adoClient;
        this.workflowEngine = workflowEngine;
        this.patchBuilder = patchBuilder;
        this.commentBuilder = commentBuilder;
    }

    public WorkItemProcessingResult process(ProcessWorkItemCommand command) {
        var currentWorkItem = adoClient.fetchWorkItem(command.workItemKey());
        var previousRevision = adoClient.fetchWorkItemRevision(command.workItemKey(), command.revision() - 1);
        var workflowDecision = workflowEngine.decide(toWorkflowInput(command, currentWorkItem, previousRevision));

        if (!workflowDecision.patchRequired()) {
            return WorkItemProcessingResult.skipped("Workflow decision does not require visible action.", workflowDecision);
        }

        var patchOperations = patchBuilder.build(currentWorkItem.revision(), workflowDecision);
        if (hasNoFieldOperations(patchOperations)) {
            return WorkItemProcessingResult.skipped("Patch contains no field operations.", workflowDecision);
        }

        var patchResult = adoClient.patchWorkItem(command.workItemKey(), patchOperations);
        if (!patchResult.successful()) {
            return failedPatchResult(patchResult, workflowDecision);
        }

        var comment = commentBuilder.fromDecision(workflowDecision);
        if (comment.isEmpty()) {
            return WorkItemProcessingResult.completed("Patch completed without comment.", workflowDecision);
        }

        var commentResult = adoClient.createWorkItemComment(command.workItemKey(), comment.get());
        if (!commentResult.successful()) {
            return WorkItemProcessingResult.completedWithWarning("Patch completed but comment creation failed.", workflowDecision);
        }

        return WorkItemProcessingResult.completed("Patch and comment completed.", workflowDecision);
    }

    private WorkflowInput toWorkflowInput(
            ProcessWorkItemCommand command,
            AdoWorkItem currentWorkItem,
            AdoWorkItemRevision previousRevision
    ) {
        return new WorkflowInput(
                currentWorkItem.project(),
                currentWorkItem.id(),
                currentWorkItem.revision(),
                currentWorkItem.workItemType(),
                previousState(previousRevision.fields(), currentWorkItem.state()),
                currentWorkItem.state(),
                toIdentity(previousRevision.changedBy()),
                previousRevision.fields(),
                currentWorkItem.fields(),
                command.projectConfig()
        );
    }

    private Identity toIdentity(AdoIdentity identity) {
        if (identity == null) {
            return null;
        }
        return new Identity(identity.displayName(), identity.emailOrLogin());
    }

    private String previousState(Map<String, Object> previousFields, String currentState) {
        var previousState = previousFields.get(WorkflowEngine.SYSTEM_STATE);
        if (previousState == null) {
            return currentState;
        }
        return previousState.toString();
    }

    private boolean hasNoFieldOperations(List<PatchOperation> patchOperations) {
        return patchOperations.stream().noneMatch(operation -> operation.path().startsWith("/fields/"));
    }

    private WorkItemProcessingResult failedPatchResult(AdoPatchResult patchResult, WorkflowDecision workflowDecision) {
        if (patchResult.retryable()) {
            return WorkItemProcessingResult.failedRetryable("Patch failed with retryable error.", workflowDecision);
        }
        return WorkItemProcessingResult.failedNonRetryable("Patch failed with non-retryable error.", workflowDecision);
    }
}
