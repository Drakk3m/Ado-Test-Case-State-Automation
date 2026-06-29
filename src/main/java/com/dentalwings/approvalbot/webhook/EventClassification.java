package com.dentalwings.approvalbot.webhook;

import java.util.Optional;

import com.dentalwings.approvalbot.ado.AdoWorkItemKey;

public record EventClassification(EventClassificationStatus status, String reason, AdoWorkItemKey workItemKey,
                                  Integer revision)
{

    public static EventClassification processable(AdoWorkItemKey workItemKey, int revision)
    {
        return new EventClassification(EventClassificationStatus.PROCESSABLE, "Event can be processed.", workItemKey,
                revision);
    }

    public static EventClassification skipped(EventClassificationStatus status, String reason)
    {
        return new EventClassification(status, reason, null, null);
    }

    public static EventClassification malformed(String reason)
    {
        return new EventClassification(EventClassificationStatus.FAILED_MALFORMED_EVENT, reason, null, null);
    }

    public Optional<AdoWorkItemKey> maybeWorkItemKey()
    {
        return Optional.ofNullable(workItemKey);
    }

    public Optional<Integer> maybeRevision()
    {
        return Optional.ofNullable(revision);
    }
}
