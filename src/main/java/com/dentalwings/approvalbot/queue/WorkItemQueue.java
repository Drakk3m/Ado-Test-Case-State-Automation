package com.dentalwings.approvalbot.queue;

import com.dentalwings.approvalbot.processing.WorkItemProcessingResult;
import java.util.Optional;
import java.util.function.Function;

public interface WorkItemQueue {

    void enqueue(QueuedWorkItemEvent event);

    Optional<WorkItemProcessingResult> drainNext(
            WorkItemQueueKey key,
            Function<QueuedWorkItemEvent, WorkItemProcessingResult> processor
    );

    WorkItemProcessingResult process(
            QueuedWorkItemEvent event,
            Function<QueuedWorkItemEvent, WorkItemProcessingResult> processor
    );

    int pendingCount(WorkItemQueueKey key);
}
