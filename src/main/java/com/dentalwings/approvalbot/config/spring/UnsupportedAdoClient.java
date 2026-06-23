package com.dentalwings.approvalbot.config.spring;

import com.dentalwings.approvalbot.ado.AdoClient;
import com.dentalwings.approvalbot.ado.AdoCommentResult;
import com.dentalwings.approvalbot.ado.AdoPatchResult;
import com.dentalwings.approvalbot.ado.AdoWorkItem;
import com.dentalwings.approvalbot.ado.AdoWorkItemKey;
import com.dentalwings.approvalbot.ado.AdoWorkItemRevision;
import com.dentalwings.approvalbot.domain.PatchOperation;
import java.util.List;

public class UnsupportedAdoClient implements AdoClient {

    private static final String MESSAGE = "Real Azure DevOps client is not implemented yet.";

    @Override
    public AdoWorkItem fetchWorkItem(AdoWorkItemKey key) {
        throw unsupported();
    }

    @Override
    public AdoWorkItemRevision fetchWorkItemRevision(AdoWorkItemKey key, int revision) {
        throw unsupported();
    }

    @Override
    public AdoPatchResult patchWorkItem(AdoWorkItemKey key, List<PatchOperation> patchOperations) {
        throw unsupported();
    }

    @Override
    public AdoCommentResult createWorkItemComment(AdoWorkItemKey key, String commentText) {
        throw unsupported();
    }

    private UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException(MESSAGE);
    }
}
