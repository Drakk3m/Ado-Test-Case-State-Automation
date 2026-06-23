package com.dentalwings.approvalbot.idempotency;

import com.dentalwings.approvalbot.domain.ProcessingResult;
import com.dentalwings.approvalbot.processing.ProcessWorkItemCommand;
import com.dentalwings.approvalbot.processing.WorkItemProcessingResult;
import com.dentalwings.approvalbot.processing.WorkItemProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdempotentWorkItemProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdempotentWorkItemProcessor.class);

    private final ProcessedEventStore processedEventStore;
    private final WorkItemProcessingService delegate;

    public IdempotentWorkItemProcessor(ProcessedEventStore processedEventStore, WorkItemProcessingService delegate) {
        this.processedEventStore = processedEventStore;
        this.delegate = delegate;
    }

    public WorkItemProcessingResult process(ProcessWorkItemCommand command) {
        var key = ProcessedEventKey.from(command);
        if (processedEventStore.alreadyProcessed(key)) {
            LOGGER.info("Duplicate work item event skipped project={} workItemId={} revision={}",
                    key.project(),
                    key.workItemId(),
                    key.revision());
            return WorkItemProcessingResult.skipped("Event was already processed.", null);
        }

        var result = delegate.process(command);
        if (shouldMarkProcessed(result)) {
            processedEventStore.markProcessed(key, result);
            LOGGER.info("Work item event marked processed project={} workItemId={} revision={} result={}",
                    key.project(),
                    key.workItemId(),
                    key.revision(),
                    result.result());
        }
        return result;
    }

    private boolean shouldMarkProcessed(WorkItemProcessingResult result) {
        return result.result() != ProcessingResult.FAILED_RETRYABLE;
    }
}
