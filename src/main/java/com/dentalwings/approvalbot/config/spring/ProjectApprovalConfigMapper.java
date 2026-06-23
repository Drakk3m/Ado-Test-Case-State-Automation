package com.dentalwings.approvalbot.config.spring;

import com.dentalwings.approvalbot.config.ProjectApprovalConfig;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProjectApprovalConfigMapper {

    public Map<String, ProjectApprovalConfig> toProjectConfigs(ApprovalBotProperties properties) {
        var mapped = new LinkedHashMap<String, ProjectApprovalConfig>();
        properties.getAdo().getProjects().forEach((projectName, projectProperties) ->
                mapped.put(projectName, toProjectConfig(projectName, projectProperties, properties.getBot()))
        );
        return mapped;
    }

    public ProjectApprovalConfig toProjectConfig(
            String projectName,
            ProjectApprovalProperties projectProperties,
            BotProperties botProperties
    ) {
        var fields = projectProperties.getFields();
        var approvals = projectProperties.getApprovals();
        return new ProjectApprovalConfig(
                projectName,
                projectProperties.isEnabled(),
                projectProperties.getSupportedWorkItemTypes(),
                fields.getApprovedBySme(),
                fields.getApprovedBySqa(),
                fields.getReversibleBusinessFields(),
                approvals.getSmeUsers(),
                approvals.getSqaUsers(),
                botProperties.getIdentityEmail()
        );
    }
}
