package com.dentalwings.approvalbot.queue;

import com.dentalwings.approvalbot.idempotency.IdempotentWorkItemProcessor;
import com.dentalwings.approvalbot.processing.ProcessWorkItemCommand;
import com.dentalwings.approvalbot.processing.WorkItemProcessingResult;
import com.dentalwings.approvalbot.processing.WorkItemProcessingService;
import java.util.function.Function;

public class QueuedWorkItemProcessor {

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
        return queue.process(event, queuedEvent -> delegate.apply(queuedEvent.command()));
    }
}
