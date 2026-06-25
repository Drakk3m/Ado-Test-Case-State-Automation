package com.dentalwings.approvalbot.config;

import java.util.Set;

public record ProjectApprovalConfig(
        String projectName,
        boolean enabled,
        Set<String> supportedWorkItemTypes,
        String approvedBySmeField,
        String approvedBySqaField,
        Set<String> reversibleBusinessFields,
        Set<String> smeUsers,
        Set<String> sqaUsers,
        String botIdentityEmail,
        WorkflowStateNames stateNames
) {
    public ProjectApprovalConfig {
        stateNames = stateNames == null ? WorkflowStateNames.defaults() : stateNames;
    }

    public ProjectApprovalConfig(
            String projectName,
            boolean enabled,
            Set<String> supportedWorkItemTypes,
            String approvedBySmeField,
            String approvedBySqaField,
            Set<String> reversibleBusinessFields,
            Set<String> smeUsers,
            Set<String> sqaUsers,
            String botIdentityEmail
    ) {
        this(
                projectName,
                enabled,
                supportedWorkItemTypes,
                approvedBySmeField,
                approvedBySqaField,
                reversibleBusinessFields,
                smeUsers,
                sqaUsers,
                botIdentityEmail,
                WorkflowStateNames.defaults()
        );
    }
}
