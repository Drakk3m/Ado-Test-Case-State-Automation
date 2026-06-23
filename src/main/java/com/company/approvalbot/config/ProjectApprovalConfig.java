package com.company.approvalbot.config;

import java.util.Set;

public record ProjectApprovalConfig(
        String projectName,
        boolean enabled,
        Set<String> supportedWorkItemTypes,
        String approvedBySmeField,
        String approvedBySqaField,
        Set<String> reversibleBusinessFields,
        Set<String> smeUsers,
        Set<String> sqaUsers
) {
}
