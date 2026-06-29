package com.dentalwings.approvalbot.idempotency;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.dentalwings.approvalbot.domain.ProcessingResult;
import com.dentalwings.approvalbot.processing.WorkItemProcessingResult;

public class InMemoryProcessedEventStore implements ProcessedEventStore
{

    private final ConcurrentHashMap<ProcessedEventKey, ProcessedEventRecord> records = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final int maxRecords;
    private final Clock clock;

    public InMemoryProcessedEventStore(Duration ttl, int maxRecords)
    {
        this(ttl, maxRecords, Clock.systemUTC());
    }

    public InMemoryProcessedEventStore(Duration ttl, int maxRecords, Clock clock)
    {
        if (ttl.isNegative() || ttl.isZero())
        {
            throw new IllegalArgumentException("ttl must be positive");
        }
        if (maxRecords < 1)
        {
            throw new IllegalArgumentException("maxRecords must be positive");
        }
        this.ttl = ttl;
        this.maxRecords = maxRecords;
        this.clock = clock;
    }

    @Override
    public boolean alreadyProcessed(ProcessedEventKey key)
    {
        return records.containsKey(key);
    }

    @Override
    public Optional<ProcessedEventRecord> find(ProcessedEventKey key)
    {
        return Optional.ofNullable(records.get(key));
    }

    @Override
    public void markProcessed(ProcessedEventKey key, WorkItemProcessingResult result)
    {
        var now = Instant.now(clock);
        records.put(key,
                new ProcessedEventRecord(key, result.result(), retryCount(result.result()),
                        nextRetryAt(result.result(), now), now, completedAt(result.result(), now),
                        errorCategory(result.result()), result.reason()));
    }

    @Override
    public void cleanupExpired()
    {
        var cutoff = Instant.now(clock).minus(ttl);
        records.entrySet().removeIf(entry -> entry.getValue().receivedAt().isBefore(cutoff));

        while (records.size() > maxRecords)
        {
            records.entrySet().stream().min(Comparator.comparing(entry -> entry.getValue().receivedAt()))
                    .ifPresent(entry -> records.remove(entry.getKey()));
        }
    }

    private int retryCount(ProcessingResult result)
    {
        return result == ProcessingResult.FAILED_RETRYABLE ? 1 : 0;
    }

    private Instant nextRetryAt(ProcessingResult result, Instant now)
    {
        return result == ProcessingResult.FAILED_RETRYABLE ? now.plus(Duration.ofSeconds(30)) : null;
    }

    private Instant completedAt(ProcessingResult result, Instant now)
    {
        return result == ProcessingResult.FAILED_RETRYABLE ? null : now;
    }

    private String errorCategory(ProcessingResult result)
    {
        return switch (result)
        {
            case FAILED_RETRYABLE -> "retryable";
            case FAILED_NON_RETRYABLE -> "non_retryable";
            default -> null;
        };
    }
}
