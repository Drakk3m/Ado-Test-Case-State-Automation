package com.dentalwings.approvalbot.queue;

import com.dentalwings.approvalbot.idempotency.IdempotentWorkItemProcessor;
import com.dentalwings.approvalbot.processing.ProcessWorkItemCommand;
import com.dentalwings.approvalbot.processing.WorkItemProcessingResult;
import com.dentalwings.approvalbot.processing.WorkItemProcessingService;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueuedWorkItemProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueuedWorkItemProcessor.class);

    private final WorkItemQueue queue;
    private final Function<ProcessWorkItemCommand, WorkItemProcessingResult> delegate;

    public QueuedWorkItemProcessor(WorkItemQueue queue, IdempotentWorkItemProcessor delegate) {
        this(queue, delegate::process);
    }

    public QueuedWorkItemProcessor(WorkItemQueue queue, WorkItemProcessingService delegate) {
        this(queue, delegate::process);
    }

    public QueuedWorkItemProcessor(
            WorkItemQueue queue,
            Function<ProcessWorkItemCommand, WorkItemProcessingResult> delegate
    ) {
        this.queue = queue;
        this.delegate = delegate;
    }

    public WorkItemProcessingResult process(QueuedWorkItemEvent event) {
        var command = event.command();
        LOGGER.debug("Processing queued work item event project={} workItemId={} revision={}",
                command.workItemKey().project(),
                command.workItemKey().workItemId(),
                command.revision());
        var result = queue.process(event, queuedEvent -> delegate.apply(queuedEvent.command()));
        LOGGER.debug("Queued work item event completed project={} workItemId={} revision={} result={}",
                command.workItemKey().project(),
                command.workItemKey().workItemId(),
                command.revision(),
                result.result());
        return result;
    }
}
