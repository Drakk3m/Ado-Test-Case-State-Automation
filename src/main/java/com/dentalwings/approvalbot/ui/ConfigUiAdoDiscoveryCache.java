package com.dentalwings.approvalbot.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

final class ConfigUiAdoDiscoveryCache
{
    static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    static final int DEFAULT_MAX_ENTRIES = 200;

    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;
    private final Map<CacheKey, CacheEntry> entries = new LinkedHashMap<>(16, 0.75f, true);

    ConfigUiAdoDiscoveryCache()
    {
        this(DEFAULT_TTL, DEFAULT_MAX_ENTRIES, Clock.systemUTC());
    }

    ConfigUiAdoDiscoveryCache(Duration ttl, int maxEntries, Clock clock)
    {
        if (ttl == null || ttl.isZero() || ttl.isNegative())
        {
            throw new IllegalArgumentException("ADO discovery cache TTL must be positive.");
        }
        if (maxEntries < 1)
        {
            throw new IllegalArgumentException("ADO discovery cache max entries must be positive.");
        }
        this.ttl = ttl;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    synchronized Optional<ProjectMetadata> project(String organization, String project)
    {
        return get(new CacheKey("project", normalized(organization), normalized(project), ""), ProjectMetadata.class);
    }

    synchronized void putProject(String organization, String requestedProject, String projectId, String projectName)
    {
        put(new CacheKey("project", normalized(organization), normalized(requestedProject), ""),
                new ProjectMetadata(projectId, projectName));
    }

    synchronized Optional<ProcessSelection> process(String organization, String projectId)
    {
        return get(new CacheKey("process", normalized(organization), normalized(projectId), ""), ProcessSelection.class);
    }

    synchronized void putProcess(String organization, String projectId, String propertyName, String processId)
    {
        put(new CacheKey("process", normalized(organization), normalized(projectId), ""),
                new ProcessSelection(propertyName, processId));
    }

    synchronized void removeProcess(String organization, String projectId)
    {
        entries.remove(new CacheKey("process", normalized(organization), normalized(projectId), ""));
    }

    synchronized boolean processFailure(String organization, String projectId, String processId)
    {
        return get(new CacheKey("process-failure", normalized(organization), normalized(projectId),
                normalized(processId)), ProcessFailure.class).isPresent();
    }

    synchronized void putProcessFailure(String organization, String projectId, String processId)
    {
        put(new CacheKey("process-failure", normalized(organization), normalized(projectId), normalized(processId)),
                new ProcessFailure());
    }

    synchronized Optional<CachedOptions> options(String kind, String organization, String scope, String qualifier)
    {
        return get(new CacheKey(kind, normalized(organization), normalized(scope), normalized(qualifier)), CachedOptions.class);
    }

    synchronized void putOptions(String kind, String organization, String scope, String qualifier,
            ConfigLookupResult<ConfigSelectorOption> result)
    {
        if (result.status() == ConfigValidationStatus.ERROR || result.status() == ConfigValidationStatus.NOT_CHECKED
                || result.status() == ConfigValidationStatus.NOT_CONFIGURED)
        {
            return;
        }
        var sanitized = result.values().stream()
                .map(option -> new ConfigSelectorOption(option.value(), option.displayName(), option.description(),
                        option.source(), option.referenceName(), option.avatarUrl(), option.resolved()))
                .toList();
        put(new CacheKey(kind, normalized(organization), normalized(scope), normalized(qualifier)),
                new CachedOptions(result.status(), result.message(), sanitized));
    }

    private <T> Optional<T> get(CacheKey key, Class<T> type)
    {
        var entry = entries.get(key);
        if (entry == null)
        {
            return Optional.empty();
        }
        if (!entry.createdAt().plus(ttl).isAfter(clock.instant()))
        {
            entries.remove(key);
            return Optional.empty();
        }
        return type.isInstance(entry.value()) ? Optional.of(type.cast(entry.value())) : Optional.empty();
    }

    private void put(CacheKey key, Object value)
    {
        entries.put(key, new CacheEntry(clock.instant(), value));
        while (entries.size() > maxEntries)
        {
            entries.remove(entries.keySet().iterator().next());
        }
    }

    private String normalized(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    record ProjectMetadata(String projectId, String projectName)
    {
    }

    record ProcessSelection(String propertyName, String processId)
    {
    }

    private record ProcessFailure()
    {
    }

    record CachedOptions(ConfigValidationStatus status, String message, List<ConfigSelectorOption> values)
    {
        CachedOptions
        {
            message = message == null ? "" : message;
            values = values == null ? List.of() : List.copyOf(values);
        }

        ConfigLookupResult<ConfigSelectorOption> toResult()
        {
            return new ConfigLookupResult<>(status, message, values);
        }
    }

    private record CacheKey(String kind, String organization, String scope, String qualifier)
    {
    }

    private record CacheEntry(Instant createdAt, Object value)
    {
    }
}
