package com.dentalwings.approvalbot.config.spring;

import com.dentalwings.approvalbot.config.ProjectApprovalConfig;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProjectApprovalConfigResolver {

    private final Map<String, ProjectApprovalConfig> projectConfigs;

    public ProjectApprovalConfigResolver(ApprovalBotProperties properties) {
        this.projectConfigs = new ProjectApprovalConfigMapper().toProjectConfigs(properties);
    }

    public Optional<ProjectApprovalConfig> findByProjectName(String projectName) {
        if (projectName == null) {
            return Optional.empty();
        }
        return projectConfigs.entrySet().stream()
                .filter(entry -> normalize(entry.getKey()).equals(normalize(projectName))).map(Map.Entry::getValue)
                .findFirst();
    }

    private String normalize(String projectName) {
        return projectName == null ? "" : projectName.trim().toLowerCase(Locale.ROOT);
    }
}
