package com.dentalwings.approvalbot.ui;

import java.util.List;

public record ConfigLookupResult<T>(
        ConfigValidationStatus status,
        String message,
        List<T> values,
        int optionCount
) {

    public ConfigLookupResult(ConfigValidationStatus status, String message, List<T> values) {
        this(status, message, values, values == null ? 0 : values.size());
    }

    public ConfigLookupResult {
        values = values == null ? List.of() : List.copyOf(values);
        optionCount = values.size();
    }

    public static <T> ConfigLookupResult<T> valid(List<T> values) {
        if (values == null || values.isEmpty()) {
            return warning("ADO discovery returned no options.");
        }
        return new ConfigLookupResult<>(ConfigValidationStatus.VALID, "", values);
    }

    public static <T> ConfigLookupResult<T> notChecked(String message) {
        return new ConfigLookupResult<>(ConfigValidationStatus.NOT_CHECKED, message, List.of());
    }

    public static <T> ConfigLookupResult<T> warning(String message) {
        return new ConfigLookupResult<>(ConfigValidationStatus.WARNING, message, List.of());
    }

    public static <T> ConfigLookupResult<T> error(String message) {
        return new ConfigLookupResult<>(ConfigValidationStatus.ERROR, message, List.of());
    }
}
