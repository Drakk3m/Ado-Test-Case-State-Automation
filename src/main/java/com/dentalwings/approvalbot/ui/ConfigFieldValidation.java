package com.dentalwings.approvalbot.ui;

public record ConfigFieldValidation(
        String field,
        ConfigValidationStatus status,
        String message
) {
}
