package com.dentalwings.approvalbot.ui;

public record ConfigSelectorOption(
        String value,
        String displayName,
        String description,
        String source
) {
    public ConfigSelectorOption {
        value = value == null ? "" : value;
        displayName = displayName == null || displayName.isBlank() ? value : displayName;
        description = description == null ? "" : description;
        source = source == null || source.isBlank() ? "ADO" : source;
    }

    public static ConfigSelectorOption ado(String value) {
        return new ConfigSelectorOption(value, value, "", "ADO");
    }
}
