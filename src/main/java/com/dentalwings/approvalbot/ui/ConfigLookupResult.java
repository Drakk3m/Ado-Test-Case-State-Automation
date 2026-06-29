package com.dentalwings.approvalbot.ui;

import java.util.List;
import java.util.Map;

public record ConfigLookupResult<T>(ConfigValidationStatus status, String message, List<T> values, int optionCount,
                                    Map<String, Object> diagnostics)
{

    public ConfigLookupResult(ConfigValidationStatus status, String message, List<T> values)
    {
        this(status, message, values, values == null ? 0 : values.size(), Map.of());
    }

    public ConfigLookupResult(ConfigValidationStatus status, String message, List<T> values, int optionCount)
    {
        this(status, message, values, optionCount, Map.of());
    }

    public ConfigLookupResult
    {
        values = values == null ? List.of() : List.copyOf(values);
        optionCount = values.size();
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
    }

    public ConfigLookupResult<T> withDiagnostics(Map<String, Object> safeDiagnostics)
    {
        return new ConfigLookupResult<>(status, message, values, optionCount, safeDiagnostics);
    }

    public static <T> ConfigLookupResult<T> valid(List<T> values)
    {
        if (values == null || values.isEmpty())
        {
            return warning("ADO discovery returned no options.");
        }
        return new ConfigLookupResult<>(ConfigValidationStatus.VALID, "", values);
    }

    public static <T> ConfigLookupResult<T> notChecked(String message)
    {
        return new ConfigLookupResult<>(ConfigValidationStatus.NOT_CHECKED, message, List.of());
    }

    public static <T> ConfigLookupResult<T> warning(String message)
    {
        return new ConfigLookupResult<>(ConfigValidationStatus.WARNING, message, List.of());
    }

    public static <T> ConfigLookupResult<T> error(String message)
    {
        return new ConfigLookupResult<>(ConfigValidationStatus.ERROR, message, List.of());
    }

    public static <T> ConfigLookupResult<T> notConfigured(String message)
    {
        return new ConfigLookupResult<>(ConfigValidationStatus.NOT_CONFIGURED, message, List.of());
    }
}
