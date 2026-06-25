package com.dentalwings.approvalbot.ui;

import java.util.List;

public record ConfigLookupResult<T>(
        ConfigValidationStatus status,
        String message,
        List<T> values
) {

    public static <T> ConfigLookupResult<T> valid(List<T> values) {
        return new ConfigLookupResult<>(ConfigValidationStatus.VALID, "", List.copyOf(values));
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
