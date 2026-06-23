package com.dentalwings.approvalbot.ado;

import com.dentalwings.approvalbot.domain.PatchOperation;
import java.util.List;

/**
 * Boundary for Azure DevOps source-of-truth operations.
 * Implementations fetch ADO data, execute already-built JSON Patch operations,
 * and send already-built comment text; they do not own workflow decisions.
 */
public interface AdoClient {

    /** Fetches the current Work Item from Azure DevOps, which is the source of truth. */
    AdoWorkItem fetchWorkItem(AdoWorkItemKey key);

    /** Fetches a specific source-of-truth Work Item revision from Azure DevOps. */
    AdoWorkItemRevision fetchWorkItemRevision(AdoWorkItemKey key, int revision);

    /** Executes JSON Patch operations already built by workflow integration code. */
    AdoPatchResult patchWorkItem(AdoWorkItemKey key, List<PatchOperation> patchOperations);

    /** Sends comment text already built by CommentBuilder or application orchestration code. */
    AdoCommentResult createWorkItemComment(AdoWorkItemKey key, String commentText);
}
