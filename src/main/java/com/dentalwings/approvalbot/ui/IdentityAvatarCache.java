package com.dentalwings.approvalbot.ui;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

final class IdentityAvatarCache
{

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);
    private static final Duration DEFAULT_FAILURE_TTL = Duration.ofMinutes(2);
    private static final int DEFAULT_MAX_ENTRIES = 200;
    private final Duration successTtl;
    private final Duration failureTtl;
    private final int maxEntries;
    private final Clock clock;
    private final Map<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);

    IdentityAvatarCache()
    {
        this(DEFAULT_TTL, DEFAULT_FAILURE_TTL, DEFAULT_MAX_ENTRIES, Clock.systemUTC());
    }

    IdentityAvatarCache(Duration ttl, int maxEntries, Clock clock)
    {
        this(ttl, DEFAULT_FAILURE_TTL, maxEntries, clock);
    }

    IdentityAvatarCache(Duration successTtl, Duration failureTtl, int maxEntries, Clock clock)
    {
        this.successTtl = successTtl;
        this.failureTtl = failureTtl;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    synchronized Optional<CachedAvatar> get(String organization, String descriptor)
    {
        var key = key(organization, descriptor);
        var entry = entries.get(key);
        if (entry == null || !entry.createdAt().plus(entry.avatar().available() ? successTtl : failureTtl)
                .isAfter(clock.instant()))
        {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.avatar().copy());
    }

    synchronized void putSuccess(String organization, String descriptor, byte[] bytes, String contentType)
    {
        put(organization, descriptor, new CachedAvatar(true, bytes, contentType));
    }

    synchronized void putFailure(String organization, String descriptor)
    {
        put(organization, descriptor, new CachedAvatar(false, new byte[0], ""));
    }

    private void put(String organization, String descriptor, CachedAvatar avatar)
    {
        entries.put(key(organization, descriptor), new Entry(clock.instant(), avatar.copy()));
        while (entries.size() > maxEntries)
        {
            entries.remove(entries.keySet().iterator().next());
        }
    }

    private Key key(String organization, String descriptor)
    {
        return new Key(normalized(organization), normalized(descriptor));
    }

    private String normalized(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record Key(String organization, String descriptor)
    {
    }

    record CachedAvatar(boolean available, byte[] bytes, String contentType)
    {
        CachedAvatar
        {
            bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
            contentType = contentType == null ? "" : contentType;
        }

        CachedAvatar copy()
        {
            return new CachedAvatar(available, bytes, contentType);
        }
    }

    private record Entry(Instant createdAt, CachedAvatar avatar)
    {
    }
}
