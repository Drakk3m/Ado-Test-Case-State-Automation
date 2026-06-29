package com.dentalwings.approvalbot.processing;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.dentalwings.approvalbot.ado.AdoClient;
import com.dentalwings.approvalbot.ado.AdoIdentity;
import com.dentalwings.approvalbot.ado.AdoPatchResult;
import com.dentalwings.approvalbot.ado.AdoWorkItem;
import com.dentalwings.approvalbot.ado.AdoWorkItemRevision;
import com.dentalwings.approvalbot.ado.http.AdoClientNonRetryableException;
import com.dentalwings.approvalbot.ado.http.AdoClientRetryableException;
import com.dentalwings.approvalbot.domain.Identity;
import com.dentalwings.approvalbot.domain.PatchOperation;
import com.dentalwings.approvalbot.domain.WorkflowDecision;
import com.dentalwings.approvalbot.domain.WorkflowInput;
import com.dentalwings.approvalbot.workflow.WorkflowEngine;
import com.dentalwings.approvalbot.workflow.comment.CommentBuilder;
import com.dentalwings.approvalbot.workflow.patch.PatchBuilder;

public class WorkItemProcessingService
{

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkItemProcessingService.class);

    private final AdoClient adoClient;
    private final WorkflowEngine workflowEngine;
    private final PatchBuilder patchBuilder;
    private final CommentBuilder commentBuilder;

    public WorkItemProcessingService(AdoClient adoClient, WorkflowEngine workflowEngine, PatchBuilder patchBuilder,
            CommentBuilder commentBuilder)
    {
        this.adoClient = adoClient;
        this.workflowEngine = workflowEngine;
        this.patchBuilder = patchBuilder;
        this.commentBuilder = commentBuilder;
    }

    public WorkItemProcessingResult process(ProcessWorkItemCommand command)
    {
        LOGGER.info("Fetching ADO source of truth project={} workItemId={} currentRevision={} previousRevision={}",
                command.workItemKey().project(), command.workItemKey().workItemId(), command.revision(),
                command.revision() - 1);
        AdoWorkItem currentWorkItem;
        AdoWorkItemRevision previousRevision;
        try
        {
            currentWorkItem = adoClient.fetchWorkItem(command.workItemKey());
            previousRevision = adoClient.fetchWorkItemRevision(command.workItemKey(), command.revision() - 1);
        }
        catch (AdoClientRetryableException exception)
        {
            LOGGER.warn("ADO source fetch failed project={} workItemId={} revision={} retryable={} message={}",
                    command.workItemKey().project(), command.workItemKey().workItemId(), command.revision(), true,
                    exception.getMessage());
            return WorkItemProcessingResult.failedRetryable("ADO read failed with retryable error.", null);
        }
        catch (AdoClientNonRetryableException exception)
        {
            LOGGER.warn("ADO source fetch failed project={} workItemId={} revision={} retryable={} message={}",
                    command.workItemKey().project(), command.workItemKey().workItemId(), command.revision(), false,
                    exception.getMessage());
            return WorkItemProcessingResult.failedNonRetryable("ADO read failed with non-retryable error.", null);
        }
        LOGGER.debug("Fetched ADO source of truth project={} workItemId={} currentRevision={} previousRevision={}",
                command.workItemKey().project(), command.workItemKey().workItemId(), currentWorkItem.revision(),
                previousRevision.revision());
        var workflowDecision = workflowEngine.decide(toWorkflowInput(command, currentWorkItem, previousRevision));
        LOGGER.info("Workflow decision project={} workItemId={} revision={} result={} patchRequired={} reason={}",
                command.workItemKey().project(), command.workItemKey().workItemId(), command.revision(),
                workflowDecision.result(), workflowDecision.patchRequired(), workflowDecision.reason());

        if (!workflowDecision.patchRequired())
        {
            LOGGER.info("Patch skipped project={} workItemId={} revision={} reason={}", command.workItemKey().project(),
                    command.workItemKey().workItemId(), command.revision(),
                    "Workflow decision does not require visible action.");
            return WorkItemProcessingResult.skipped("Workflow decision does not require visible action.",
                    workflowDecision);
        }

        var patchOperations = patchBuilder.build(currentWorkItem.revision(), workflowDecision);
        if (hasNoFieldOperations(patchOperations))
        {
            LOGGER.info("Patch skipped project={} workItemId={} revision={} reason={}", command.workItemKey().project(),
                    command.workItemKey().workItemId(), command.revision(), "Patch contains no field operations.");
            return WorkItemProcessingResult.skipped("Patch contains no field operations.", workflowDecision);
        }

        LOGGER.info("Patch attempted project={} workItemId={} revision={} operationCount={}",
                command.workItemKey().project(), command.workItemKey().workItemId(), command.revision(),
                patchOperations.size());
        var patchResult = adoClient.patchWorkItem(command.workItemKey(), patchOperations);
        if (!patchResult.successful())
        {
            LOGGER.warn("Patch failed project={} workItemId={} revision={} retryable={} message={}",
                    command.workItemKey().project(), command.workItemKey().workItemId(), command.revision(),
                    patchResult.retryable(), patchResult.message());
            return failedPatchResult(patchResult, workflowDecision);
        }
        LOGGER.info("Patch succeeded project={} workItemId={} revision={} resultingRevision={}",
                command.workItemKey().project(), command.workItemKey().workItemId(), command.revision(),
                patchResult.revision());

        var comment = commentBuilder.fromDecision(workflowDecision);
        if (comment.isEmpty())
        {
            LOGGER.info("Comment skipped project={} workItemId={} revision={} reason={}",
                    command.workItemKey().project(), command.workItemKey().workItemId(), command.revision(),
                    "Workflow decision has no comment.");
            return WorkItemProcessingResult.completed("Patch completed without comment.", workflowDecision);
        }

        LOGGER.info("Comment attempted project={} workItemId={} revision={}", command.workItemKey().project(),
                command.workItemKey().workItemId(), command.revision());
        var commentResult = adoClient.createWorkItemComment(command.workItemKey(), comment.get());
        if (!commentResult.successful())
        {
            LOGGER.warn(
                    "Comment failed after successful patch project={} workItemId={} revision={} message={} outcome={}",
                    command.workItemKey().project(), command.workItemKey().workItemId(), command.revision(),
                    commentResult.message(), "COMPLETED_WITH_WARNING");
            return WorkItemProcessingResult.completedWithWarning("Patch completed but comment creation failed.",
                    workflowDecision);
        }
        LOGGER.info("Comment succeeded project={} workItemId={} revision={} commentId={}",
                command.workItemKey().project(), command.workItemKey().workItemId(), command.revision(),
                commentResult.commentId());

        return WorkItemProcessingResult.completed("Patch and comment completed.", workflowDecision);
    }

    private WorkflowInput toWorkflowInput(ProcessWorkItemCommand command, AdoWorkItem currentWorkItem,
            AdoWorkItemRevision previousRevision)
    {
        return new WorkflowInput(currentWorkItem.project(), currentWorkItem.id(), currentWorkItem.revision(),
                currentWorkItem.workItemType(), previousState(previousRevision.fields(), currentWorkItem.state()),
                currentWorkItem.state(), toIdentity(previousRevision.changedBy()), previousRevision.fields(),
                currentWorkItem.fields(), command.projectConfig());
    }

    private Identity toIdentity(AdoIdentity identity)
    {
        if (identity == null)
        {
            return null;
        }
        return new Identity(identity.displayName(), identity.emailOrLogin());
    }

    private String previousState(Map<String, Object> previousFields, String currentState)
    {
        var previousState = previousFields.get(WorkflowEngine.SYSTEM_STATE);
        if (previousState == null)
        {
            return currentState;
        }
        return previousState.toString();
    }

    private boolean hasNoFieldOperations(List<PatchOperation> patchOperations)
    {
        return patchOperations.stream().noneMatch(operation -> operation.path().startsWith("/fields/"));
    }

    private WorkItemProcessingResult failedPatchResult(AdoPatchResult patchResult, WorkflowDecision workflowDecision)
    {
        if (patchResult.retryable())
        {
            LOGGER.warn("Patch failure mapped to processing result result={}", "FAILED_RETRYABLE");
            return WorkItemProcessingResult.failedRetryable("Patch failed with retryable error.", workflowDecision);
        }
        LOGGER.warn("Patch failure mapped to processing result result={}", "FAILED_NON_RETRYABLE");
        return WorkItemProcessingResult.failedNonRetryable("Patch failed with non-retryable error.", workflowDecision);
    }
}
