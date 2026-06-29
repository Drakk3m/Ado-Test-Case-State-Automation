package com.dentalwings.approvalbot.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

final class ProjectIdentityCandidateCache
{

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    private static final int DEFAULT_MAX_ENTRIES = 40;
    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;
    private final Map<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<ProjectNameKey, ProjectReference> projectReferences = new LinkedHashMap<>();

    ProjectIdentityCandidateCache()
    {
        this(DEFAULT_TTL, DEFAULT_MAX_ENTRIES, Clock.systemUTC());
    }

    ProjectIdentityCandidateCache(Duration ttl, int maxEntries, Clock clock)
    {
        this.ttl = ttl;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    synchronized Optional<ProjectIdentityCandidates> get(String organization, String project)
    {
        var referenceKey = new ProjectNameKey(normalized(organization), normalized(project));
        var reference = projectReferences.get(referenceKey);
        if (reference == null || !reference.createdAt().plus(ttl).isAfter(clock.instant()))
        {
            projectReferences.remove(referenceKey);
            return Optional.empty();
        }
        var key = key(organization, reference.projectId());
        var entry = entries.get(key);
        if (entry == null || !entry.createdAt().plus(ttl).isAfter(clock.instant()))
        {
            entries.remove(key);
            projectReferences.remove(referenceKey);
            return Optional.empty();
        }
        return Optional.of(entry.candidates());
    }

    synchronized void put(String organization, String project, ProjectIdentityCandidates candidates)
    {
        if (normalized(candidates.projectId()).isEmpty())
        {
            return;
        }
        entries.put(key(organization, candidates.projectId()), new Entry(clock.instant(), candidates));
        projectReferences.put(new ProjectNameKey(normalized(organization), normalized(project)),
                new ProjectReference(candidates.projectId(), clock.instant()));
        while (projectReferences.size() > maxEntries)
        {
            projectReferences.remove(projectReferences.keySet().iterator().next());
        }
        while (entries.size() > maxEntries)
        {
            var removed = entries.keySet().iterator().next();
            entries.remove(removed);
            projectReferences.entrySet()
                    .removeIf(entry -> normalized(entry.getValue().projectId()).equals(removed.projectId()));
        }
    }

    private Key key(String organization, String projectId)
    {
        return new Key(normalized(organization), normalized(projectId));
    }

    private String normalized(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    record ProjectIdentityCandidates(boolean available, String projectId, String scopeDescriptor, String source,
                                     List<ConfigSelectorOption> values)
    {
        ProjectIdentityCandidates
        {
            projectId = projectId == null ? "" : projectId;
            scopeDescriptor = scopeDescriptor == null ? "" : scopeDescriptor;
            source = source == null ? "" : source;
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    private record Key(String organization, String projectId)
    {
    }

    private record ProjectNameKey(String organization, String project)
    {
    }

    private record ProjectReference(String projectId, Instant createdAt)
    {
    }

    private record Entry(Instant createdAt, ProjectIdentityCandidates candidates)
    {
    }
}
