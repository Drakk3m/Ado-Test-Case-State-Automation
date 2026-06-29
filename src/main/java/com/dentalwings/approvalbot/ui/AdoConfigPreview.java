package com.dentalwings.approvalbot.ui;

public record AdoConfigPreview(String yaml, ConfigValidationResult validation, boolean draftYamlAvailable,
                               boolean finalYamlAllowed)
{
}
