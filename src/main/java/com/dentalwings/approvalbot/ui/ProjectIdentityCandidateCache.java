package com.dentalwings.approvalbot.ui;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class ProjectIdentityCandidateCache {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    private static final int DEFAULT_MAX_ENTRIES = 40;
    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;
    private final Map<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);

    ProjectIdentityCandidateCache() {
        this(DEFAULT_TTL, DEFAULT_MAX_ENTRIES, Clock.systemUTC());
    }

    ProjectIdentityCandidateCache(Duration ttl, int maxEntries, Clock clock) {
        this.ttl = ttl;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    synchronized Optional<ProjectIdentityCandidates> get(String organization, String project) {
        var key = key(organization, project);
        var entry = entries.get(key);
        if (entry == null || !entry.createdAt().plus(ttl).isAfter(clock.instant())) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.candidates());
    }

    synchronized void put(String organization, String project, ProjectIdentityCandidates candidates) {
        entries.put(key(organization, project), new Entry(clock.instant(), candidates));
        while (entries.size() > maxEntries) {
            entries.remove(entries.keySet().iterator().next());
        }
    }

    private Key key(String organization, String project) {
        return new Key(normalized(organization), normalized(project));
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    record ProjectIdentityCandidates(boolean available, String scopeDescriptor, String source, List<ConfigSelectorOption> values) {
        ProjectIdentityCandidates {
            scopeDescriptor = scopeDescriptor == null ? "" : scopeDescriptor;
            source = source == null ? "" : source;
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    private record Key(String organization, String project) {
    }

    private record Entry(Instant createdAt, ProjectIdentityCandidates candidates) {
    }
}
