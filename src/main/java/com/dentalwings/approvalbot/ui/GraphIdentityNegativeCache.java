package com.dentalwings.approvalbot.ui;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class GraphIdentityNegativeCache {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final int DEFAULT_MAX_ENTRIES = 100;
    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;
    private final Map<Key, Instant> entries = new LinkedHashMap<>(16, 0.75f, true);

    GraphIdentityNegativeCache() {
        this(DEFAULT_TTL, DEFAULT_MAX_ENTRIES, Clock.systemUTC());
    }

    GraphIdentityNegativeCache(Duration ttl, int maxEntries, Clock clock) {
        this.ttl = ttl;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    synchronized boolean matches(String organization, String projectId, String query) {
        var normalizedQuery = normalized(query);
        entries.entrySet().removeIf(entry -> !entry.getValue().plus(ttl).isAfter(clock.instant()));
        return entries.keySet().stream().anyMatch(key -> key.organization().equals(normalized(organization))
                && key.projectId().equals(normalized(projectId)) && normalizedQuery.startsWith(key.query()));
    }

    synchronized void put(String organization, String projectId, String query) {
        if (normalized(projectId).isEmpty()) {
            return;
        }
        entries.put(new Key(normalized(organization), normalized(projectId), normalized(query)), clock.instant());
        while (entries.size() > maxEntries) {
            entries.remove(entries.keySet().iterator().next());
        }
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record Key(String organization, String projectId, String query) {
    }
}
