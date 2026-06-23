package com.dentalwings.approvalbot.idempotency;

import com.dentalwings.approvalbot.domain.ProcessingResult;
import java.time.Instant;
import java.util.Optional;

public record ProcessedEventRecord(
        ProcessedEventKey key,
        ProcessingResult result,
        int retryCount,
        Instant nextRetryAt,
        Instant receivedAt,
        Instant completedAt,
        String errorCategory,
        String errorMessage
) {

    public Optional<Instant> maybeNextRetryAt() {
        return Optional.ofNullable(nextRetryAt);
    }

    public Optional<Instant> maybeCompletedAt() {
        return Optional.ofNullable(completedAt);
    }

    public Optional<String> maybeErrorCategory() {
        return Optional.ofNullable(errorCategory);
    }

    public Optional<String> maybeErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }
}
