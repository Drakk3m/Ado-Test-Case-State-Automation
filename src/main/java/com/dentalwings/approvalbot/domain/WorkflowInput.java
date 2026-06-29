package com.dentalwings.approvalbot.domain;

import java.util.Map;

import com.dentalwings.approvalbot.config.ProjectApprovalConfig;

public record WorkflowInput(String projectName, long workItemId, int revision, String workItemType,
                            String previousState, String currentState, Identity changedBy, Map<String, Object> previousFields,
                            Map<String, Object> currentFields, ProjectApprovalConfig projectConfig)
{
}
