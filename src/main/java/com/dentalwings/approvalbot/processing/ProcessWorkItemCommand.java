package com.dentalwings.approvalbot.processing;

import com.dentalwings.approvalbot.ado.AdoWorkItemKey;
import com.dentalwings.approvalbot.config.ProjectApprovalConfig;
import com.dentalwings.approvalbot.webhook.EventClassification;

public record ProcessWorkItemCommand(AdoWorkItemKey workItemKey, int revision, ProjectApprovalConfig projectConfig)
{

    public static ProcessWorkItemCommand from(EventClassification classification, ProjectApprovalConfig projectConfig)
    {
        return new ProcessWorkItemCommand(classification.maybeWorkItemKey().orElseThrow(),
                classification.maybeRevision().orElseThrow(), projectConfig);
    }
}
