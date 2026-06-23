package com.dentalwings.approvalbot.idempotency;

import com.dentalwings.approvalbot.domain.ProcessingResult;
import com.dentalwings.approvalbot.processing.ProcessWorkItemCommand;
import com.dentalwings.approvalbot.processing.WorkItemProcessingResult;
import com.dentalwings.approvalbot.processing.WorkItemProcessingService;

public class IdempotentWorkItemProcessor {

    private final ProcessedEventStore processedEventStore;
    private final WorkItemProcessingService delegate;

    public IdempotentWorkItemProcessor(ProcessedEventStore processedEventStore, WorkItemProcessingService delegate) {
        this.processedEventStore = processedEventStore;
        this.delegate = delegate;
    }

    public WorkItemProcessingResult process(ProcessWorkItemCommand command) {
        var key = ProcessedEventKey.from(command);
        if (processedEventStore.alreadyProcessed(key)) {
            return WorkItemProcessingResult.skipped("Event was already processed.", null);
        }

        var result = delegate.process(command);
        if (shouldMarkProcessed(result)) {
            processedEventStore.markProcessed(key, result);
        }
        return result;
    }

    private boolean shouldMarkProcessed(WorkItemProcessingResult result) {
        return result.result() != ProcessingResult.FAILED_RETRYABLE;
    }
}
