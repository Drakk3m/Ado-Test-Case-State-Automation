package com.dentalwings.approvalbot.queue;

import java.time.Instant;

import com.dentalwings.approvalbot.processing.ProcessWorkItemCommand;

public record QueuedWorkItemEvent(WorkItemQueueKey key, int revision, ProcessWorkItemCommand command,
                                  Instant receivedAt)
{

    public QueuedWorkItemEvent
    {
        if (key == null)
        {
            throw new IllegalArgumentException("key must not be null");
        }
        if (command == null)
        {
            throw new IllegalArgumentException("command must not be null");
        }
        if (receivedAt == null)
        {
            throw new IllegalArgumentException("receivedAt must not be null");
        }
    }

    public static QueuedWorkItemEvent from(ProcessWorkItemCommand command, Instant receivedAt)
    {
        return new QueuedWorkItemEvent(WorkItemQueueKey.from(command), command.revision(), command, receivedAt);
    }
}
