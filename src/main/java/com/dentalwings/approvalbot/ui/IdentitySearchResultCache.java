package com.dentalwings.approvalbot.ui;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class IdentitySearchResultCache {

    static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    static final int DEFAULT_MAX_ENTRIES = 100;

    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;
    private final Map<CacheKey, CacheEntry> entries = new LinkedHashMap<>(16, 0.75f, true);

    IdentitySearchResultCache() {
        this(DEFAULT_TTL, DEFAULT_MAX_ENTRIES, Clock.systemUTC());
    }

    IdentitySearchResultCache(Duration ttl, int maxEntries, Clock clock) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("Identity search cache TTL must be positive.");
        }
        if (maxEntries < 1) {
            throw new IllegalArgumentException("Identity search cache max entries must be positive.");
        }
        this.ttl = ttl;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    synchronized Optional<CachedIdentitySearch> get(String organization, String project, String query) {
        var key = key(organization, project, query);
        var entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.createdAt().plus(ttl).isAfter(clock.instant())) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.result());
    }

    synchronized void put(String organization, String project, String query, ConfigLookupResult<ConfigSelectorOption> result) {
        var sanitizedValues = result.values().stream()
                .map(option -> new ConfigSelectorOption(
                        option.value(),
                        option.displayName(),
                        option.description(),
                        option.source(),
                        "",
                        option.avatarUrl(),
                        option.resolved()
                ))
                .toList();
        var cached = new CachedIdentitySearch(result.status(), result.message(), sanitizedValues);
        entries.put(key(organization, project, query), new CacheEntry(clock.instant(), cached));
        while (entries.size() > maxEntries) {
            entries.remove(entries.keySet().iterator().next());
        }
    }

    private CacheKey key(String organization, String project, String query) {
        return new CacheKey(normalized(organization), normalized(project), normalized(query));
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    record CachedIdentitySearch(
            ConfigValidationStatus status,
            String message,
            List<ConfigSelectorOption> values
    ) {
        CachedIdentitySearch {
            message = message == null ? "" : message;
            values = values == null ? List.of() : List.copyOf(values);
        }

        ConfigLookupResult<ConfigSelectorOption> toResult() {
            return new ConfigLookupResult<>(status, message, values);
        }
    }

    private record CacheKey(String organization, String project, String query) {
    }

    private record CacheEntry(Instant createdAt, CachedIdentitySearch result) {
    }
}
