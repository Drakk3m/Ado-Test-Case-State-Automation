package com.dentalwings.approvalbot.ui;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class IdentityAvatarCache {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);
    private static final int DEFAULT_MAX_ENTRIES = 200;
    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;
    private final Map<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);

    IdentityAvatarCache() {
        this(DEFAULT_TTL, DEFAULT_MAX_ENTRIES, Clock.systemUTC());
    }

    IdentityAvatarCache(Duration ttl, int maxEntries, Clock clock) {
        this.ttl = ttl;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    synchronized Optional<byte[]> get(String organization, String descriptor) {
        var key = key(organization, descriptor);
        var entry = entries.get(key);
        if (entry == null || !entry.createdAt().plus(ttl).isAfter(clock.instant())) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(Arrays.copyOf(entry.bytes(), entry.bytes().length));
    }

    synchronized void put(String organization, String descriptor, byte[] bytes) {
        entries.put(key(organization, descriptor), new Entry(clock.instant(), Arrays.copyOf(bytes, bytes.length)));
        while (entries.size() > maxEntries) {
            entries.remove(entries.keySet().iterator().next());
        }
    }

    private Key key(String organization, String descriptor) {
        return new Key(normalized(organization), normalized(descriptor));
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record Key(String organization, String descriptor) {
    }

    private record Entry(Instant createdAt, byte[] bytes) {
    }
}
