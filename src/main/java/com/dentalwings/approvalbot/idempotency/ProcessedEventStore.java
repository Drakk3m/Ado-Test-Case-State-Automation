package com.dentalwings.approvalbot.idempotency;

import com.dentalwings.approvalbot.processing.WorkItemProcessingResult;
import java.util.Optional;

public interface ProcessedEventStore {

    boolean alreadyProcessed(ProcessedEventKey key);

    Optional<ProcessedEventRecord> find(ProcessedEventKey key);

    void markProcessed(ProcessedEventKey key, WorkItemProcessingResult result);

    void cleanupExpired();
}
