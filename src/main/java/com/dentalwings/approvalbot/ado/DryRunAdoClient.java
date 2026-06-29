package com.dentalwings.approvalbot.ado;

import com.dentalwings.approvalbot.domain.PatchOperation;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DryRunAdoClient implements AdoClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(DryRunAdoClient.class);
    private static final String DRY_RUN_COMMENT_ID = "dry-run";

    private final AdoClient delegate;

    public DryRunAdoClient(AdoClient delegate) {
        this.delegate = delegate;
    }

    public AdoClient delegate() {
        return delegate;
    }

    @Override
    public AdoWorkItem fetchWorkItem(AdoWorkItemKey key) {
        return delegate.fetchWorkItem(key);
    }

    @Override
    public AdoWorkItemRevision fetchWorkItemRevision(AdoWorkItemKey key, int revision) {
        return delegate.fetchWorkItemRevision(key, revision);
    }

    @Override
    public AdoPatchResult patchWorkItem(AdoWorkItemKey key, List<PatchOperation> patchOperations) {
        var operations = patchOperations == null ? List.<PatchOperation> of() : patchOperations;
        var paths = operations.stream().map(PatchOperation::path).toList();
        var revision = revisionFrom(operations);

        LOGGER.info(
                "Dry-run would PATCH Work Item; suppressed ADO write project={} workItemId={} revision={} operationCount={} operationPaths={}",
                key.project(), key.workItemId(), revision, operations.size(), paths);

        return AdoPatchResult.success(revision);
    }

    @Override
    public AdoCommentResult createWorkItemComment(AdoWorkItemKey key, String commentText) {
        LOGGER.info("Dry-run would create comment; suppressed ADO write project={} workItemId={}", key.project(),
                key.workItemId());

        return AdoCommentResult.success(DRY_RUN_COMMENT_ID);
    }

    private int revisionFrom(List<PatchOperation> patchOperations) {
        if (patchOperations.isEmpty()) {
            return -1;
        }
        var firstOperation = patchOperations.getFirst();
        if (!"test".equals(firstOperation.op()) || !"/rev".equals(firstOperation.path())) {
            return -1;
        }
        return firstOperation.value() instanceof Number revision ? revision.intValue() : -1;
    }
}
