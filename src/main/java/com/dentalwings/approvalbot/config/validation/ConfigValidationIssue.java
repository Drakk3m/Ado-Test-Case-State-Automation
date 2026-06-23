package com.dentalwings.approvalbot.config.validation;

public record ConfigValidationIssue(ConfigValidationSeverity severity, String message) {

    public static ConfigValidationIssue fatal(String message) {
        return new ConfigValidationIssue(ConfigValidationSeverity.FATAL, message);
    }

    public static ConfigValidationIssue warning(String message) {
        return new ConfigValidationIssue(ConfigValidationSeverity.WARNING, message);
    }
}
