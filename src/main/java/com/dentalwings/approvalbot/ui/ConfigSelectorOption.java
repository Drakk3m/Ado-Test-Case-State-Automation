package com.dentalwings.approvalbot.ui;

public record ConfigSelectorOption(
        String value,
        String displayName,
        String description,
        String source,
        String referenceName,
        String avatarUrl,
        boolean resolved
) {
    public ConfigSelectorOption(String value, String displayName, String description, String source) {
        this(value, displayName, description, source, "", "", value != null && !value.isBlank());
    }

    public ConfigSelectorOption(String value, String displayName, String description, String source, String referenceName) {
        this(value, displayName, description, source, referenceName, "", value != null && !value.isBlank());
    }

    public ConfigSelectorOption {
        value = value == null ? "" : value;
        displayName = displayName == null || displayName.isBlank() ? value : displayName;
        description = description == null ? "" : description;
        source = source == null || source.isBlank() ? "ADO" : source;
        referenceName = referenceName == null ? "" : referenceName;
        avatarUrl = avatarUrl == null ? "" : avatarUrl;
        resolved = resolved && !value.isBlank();
    }

    public static ConfigSelectorOption ado(String value) {
        return new ConfigSelectorOption(value, value, "", "ADO");
    }
}
