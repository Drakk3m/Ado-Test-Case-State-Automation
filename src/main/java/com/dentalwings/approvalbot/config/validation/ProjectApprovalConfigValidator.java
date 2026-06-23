package com.dentalwings.approvalbot.config.validation;

import com.dentalwings.approvalbot.config.ProjectApprovalConfig;
import com.dentalwings.approvalbot.identity.EmailNormalizer;
import com.dentalwings.approvalbot.workflow.WorkflowEngine;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProjectApprovalConfigValidator {

    private static final Pattern BASIC_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final EmailNormalizer emailNormalizer = new EmailNormalizer();

    public ConfigValidationResult validate(ProjectApprovalConfig config) {
        var issues = new ArrayList<ConfigValidationIssue>();
        if (config == null || !config.enabled()) {
            return new ConfigValidationResult(issues);
        }

        validateRequiredText(config.approvedBySmeField(), "Missing SME approval field config.", issues);
        validateRequiredText(config.approvedBySqaField(), "Missing SQA approval field config.", issues);
        validateRequiredText(config.botIdentityEmail(), "Missing bot identity email.", issues);
        validateEmail(config.botIdentityEmail(), "Invalid bot identity email.", issues);

        validateSupportedWorkItemTypes(config, issues);
        validateReversibleBusinessFields(config, issues);
        validateUsers(config.smeUsers(), "SME", issues);
        validateUsers(config.sqaUsers(), "SQA", issues);
        validateCrossRoleWarnings(config, issues);

        return new ConfigValidationResult(issues);
    }

    private void validateRequiredText(String value, String message, ArrayList<ConfigValidationIssue> issues) {
        if (value == null || value.isBlank()) {
            issues.add(ConfigValidationIssue.fatal(message));
        }
    }

    private void validateSupportedWorkItemTypes(ProjectApprovalConfig config, ArrayList<ConfigValidationIssue> issues) {
        if (config.supportedWorkItemTypes() == null || config.supportedWorkItemTypes().isEmpty()) {
            issues.add(ConfigValidationIssue.fatal("Supported work item types are missing or empty."));
            return;
        }
        if (config.supportedWorkItemTypes().stream().noneMatch(WorkflowEngine.WORK_ITEM_TYPE_TEST_CASE::equals)) {
            issues.add(ConfigValidationIssue.fatal("Enabled V1 project must support Test Case work item type."));
        }
    }

    private void validateReversibleBusinessFields(ProjectApprovalConfig config, ArrayList<ConfigValidationIssue> issues) {
        if (config.reversibleBusinessFields() == null || config.reversibleBusinessFields().isEmpty()) {
            issues.add(ConfigValidationIssue.fatal("Reversible business fields list is missing or empty."));
            return;
        }

        var reversibleFields = config.reversibleBusinessFields().stream()
                .map(this::normalizeField)
                .collect(Collectors.toUnmodifiableSet());
        if (reversibleFields.contains(normalizeField(config.approvedBySmeField()))) {
            issues.add(ConfigValidationIssue.fatal("SME approval field appears in reversible business fields."));
        }
        if (reversibleFields.contains(normalizeField(config.approvedBySqaField()))) {
            issues.add(ConfigValidationIssue.fatal("SQA approval field appears in reversible business fields."));
        }
        if (reversibleFields.contains(WorkflowEngine.SYSTEM_STATE)) {
            issues.add(ConfigValidationIssue.fatal("System.State appears in reversible business fields."));
        }
    }

    private void validateUsers(Set<String> users, String role, ArrayList<ConfigValidationIssue> issues) {
        if (users == null || users.isEmpty()) {
            issues.add(ConfigValidationIssue.fatal("Missing " + role + " users."));
            return;
        }

        var seen = new HashSet<String>();
        for (String user : users) {
            var normalized = emailNormalizer.normalize(user);
            if (normalized.isEmpty() || !isValidEmail(normalized.get())) {
                issues.add(ConfigValidationIssue.fatal("Invalid " + role + " email entry."));
                continue;
            }
            if (!seen.add(normalized.get())) {
                issues.add(ConfigValidationIssue.warning("Duplicate email within " + role + " list."));
            }
        }
    }

    private void validateCrossRoleWarnings(ProjectApprovalConfig config, ArrayList<ConfigValidationIssue> issues) {
        var smeEmails = validNormalizedEmails(config.smeUsers());
        var sqaEmails = validNormalizedEmails(config.sqaUsers());

        smeEmails.stream()
                .filter(sqaEmails::contains)
                .findAny()
                .ifPresent(email -> issues.add(ConfigValidationIssue.warning("Same email appears in both SME and SQA lists.")));
    }

    private Set<String> validNormalizedEmails(Set<String> users) {
        if (users == null) {
            return Set.of();
        }
        return users.stream()
                .flatMap(user -> emailNormalizer.normalize(user).stream())
                .filter(this::isValidEmail)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void validateEmail(String value, String message, ArrayList<ConfigValidationIssue> issues) {
        var normalized = emailNormalizer.normalize(value);
        if (normalized.isPresent() && !isValidEmail(normalized.get())) {
            issues.add(ConfigValidationIssue.fatal(message));
        }
    }

    private boolean isValidEmail(String email) {
        return BASIC_EMAIL.matcher(email).matches();
    }

    private String normalizeField(String field) {
        return field == null ? "" : field.trim();
    }
}
