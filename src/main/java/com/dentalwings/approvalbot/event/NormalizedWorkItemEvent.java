package com.dentalwings.approvalbot.event;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record NormalizedWorkItemEvent(String source, String organization, String project, Long workItemId,
                                      Integer revision, String eventType, ChangedBy changedBy, String resourceUrl,
                                      String subscriptionId, String deliveryId, String workItemType,
                                      Set<String> changedFieldNames)
{

    public NormalizedWorkItemEvent
    {
        changedFieldNames = changedFieldNames == null ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(changedFieldNames));
    }

    public record ChangedBy(String displayName, String uniqueName)
    {
    }
}
