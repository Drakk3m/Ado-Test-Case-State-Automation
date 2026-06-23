package com.dentalwings.approvalbot.config.validation;

import java.util.List;

public record ConfigValidationResult(List<ConfigValidationIssue> issues) {

    public ConfigValidationResult {
        issues = List.copyOf(issues);
    }

    public boolean hasFatalErrors() {
        return issues.stream().anyMatch(issue -> issue.severity() == ConfigValidationSeverity.FATAL);
    }

    public List<ConfigValidationIssue> fatalErrors() {
        return issues.stream()
                .filter(issue -> issue.severity() == ConfigValidationSeverity.FATAL)
                .toList();
    }

    public List<ConfigValidationIssue> warnings() {
        return issues.stream()
                .filter(issue -> issue.severity() == ConfigValidationSeverity.WARNING)
                .toList();
    }
}
