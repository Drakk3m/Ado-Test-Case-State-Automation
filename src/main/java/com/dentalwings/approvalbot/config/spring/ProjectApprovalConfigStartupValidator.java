package com.dentalwings.approvalbot.config.spring;

import com.dentalwings.approvalbot.config.validation.ConfigValidationIssue;
import com.dentalwings.approvalbot.config.validation.ProjectApprovalConfigValidator;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProjectApprovalConfigStartupValidator implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectApprovalConfigStartupValidator.class);

    private final ApprovalBotProperties properties;
    private final ProjectApprovalConfigMapper mapper;
    private final ProjectApprovalConfigValidator validator;

    @Autowired
    public ProjectApprovalConfigStartupValidator(ApprovalBotProperties properties) {
        this(properties, new ProjectApprovalConfigMapper(), new ProjectApprovalConfigValidator());
    }

    ProjectApprovalConfigStartupValidator(
            ApprovalBotProperties properties,
            ProjectApprovalConfigMapper mapper,
            ProjectApprovalConfigValidator validator
    ) {
        this.properties = properties;
        this.mapper = mapper;
        this.validator = validator;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate();
    }

    public StartupValidationReport validate() {
        var fatalMessages = new ArrayList<String>();
        var warningMessages = new ArrayList<String>();

        validateAdoBoundary(fatalMessages);
        validateProjects(fatalMessages, warningMessages);

        warningMessages.forEach(message -> LOGGER.warn("Approval bot configuration warning: {}", message));
        if (!fatalMessages.isEmpty()) {
            throw new ApprovalBotConfigurationException("Invalid approval bot configuration: "
                    + String.join("; ", fatalMessages));
        }

        return new StartupValidationReport(fatalMessages, warningMessages);
    }

    private void validateAdoBoundary(ArrayList<String> fatalMessages) {
        if (isBlank(properties.getAdo().getPersonalAccessToken())) {
            fatalMessages.add("ado.personal-access-token is missing.");
        }
        if (properties.getAdo().getProjects().isEmpty()) {
            fatalMessages.add("ado.projects must contain at least one project configuration.");
        }
    }

    private void validateProjects(ArrayList<String> fatalMessages, ArrayList<String> warningMessages) {
        mapper.toProjectConfigs(properties).forEach((projectName, config) -> {
            var result = validator.validate(config);
            result.fatalErrors().stream()
                    .map(issue -> format(projectName, issue))
                    .forEach(fatalMessages::add);
            result.warnings().stream()
                    .map(issue -> format(projectName, issue))
                    .forEach(warningMessages::add);
        });
    }

    private String format(String projectName, ConfigValidationIssue issue) {
        return "Project '" + projectName + "': " + issue.message();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record StartupValidationReport(List<String> fatalMessages, List<String> warningMessages) {

        public StartupValidationReport {
            fatalMessages = List.copyOf(fatalMessages);
            warningMessages = List.copyOf(warningMessages);
        }
    }
}
