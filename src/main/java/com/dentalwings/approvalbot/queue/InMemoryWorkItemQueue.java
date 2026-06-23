package com.dentalwings.approvalbot.queue;

import com.dentalwings.approvalbot.processing.WorkItemProcessingResult;
import java.util.Comparator;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class InMemoryWorkItemQueue implements WorkItemQueue {

    private final ConcurrentHashMap<WorkItemQueueKey, QueueState> queues = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public void enqueue(QueuedWorkItemEvent event) {
        var state = stateFor(event.key());
        synchronized (state) {
            state.events.add(new QueueEntry(event, sequence.incrementAndGet()));
        }
    }

    @Override
    public Optional<WorkItemProcessingResult> drainNext(
            WorkItemQueueKey key,
            Function<QueuedWorkItemEvent, WorkItemProcessingResult> processor
    ) {
        var state = queues.get(key);
        if (state == null) {
            return Optional.empty();
        }

        synchronized (state) {
            var entry = state.events.poll();
            if (entry == null) {
                return Optional.empty();
            }

            return Optional.of(processor.apply(entry.event()));
        }
    }

    @Override
    public WorkItemProcessingResult process(
            QueuedWorkItemEvent event,
            Function<QueuedWorkItemEvent, WorkItemProcessingResult> processor
    ) {
        var state = stateFor(event.key());
        synchronized (state) {
            return processor.apply(event);
        }
    }

    @Override
    public int pendingCount(WorkItemQueueKey key) {
        var state = queues.get(key);
        if (state == null) {
            return 0;
        }
        synchronized (state) {
            return state.events.size();
        }
    }

    private QueueState stateFor(WorkItemQueueKey key) {
        return queues.computeIfAbsent(key, ignored -> new QueueState());
    }

    private static class QueueState {

        private final PriorityQueue<QueueEntry> events = new PriorityQueue<>(
                Comparator.comparingInt((QueueEntry entry) -> entry.event().revision())
                        .thenComparing(QueueEntry::sequence)
        );
    }

    private record QueueEntry(QueuedWorkItemEvent event, long sequence) {
    }
}
