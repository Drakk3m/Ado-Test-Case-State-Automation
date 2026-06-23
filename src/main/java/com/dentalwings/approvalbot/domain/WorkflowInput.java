package com.dentalwings.approvalbot.domain;

import com.dentalwings.approvalbot.config.ProjectApprovalConfig;
import java.util.Map;

public record WorkflowInput(
        String projectName,
        long workItemId,
        int revision,
        String workItemType,
        String previousState,
        String currentState,
        Identity changedBy,
        Map<String, Object> previousFields,
        Map<String, Object> currentFields,
        ProjectApprovalConfig projectConfig
) {
}
