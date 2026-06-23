package com.dentalwings.approvalbot.webhook;

import java.util.Map;
import java.util.Set;

public record AdoWebhookEvent(
        String eventType,
        String organization,
        AdoWebhookResource resource,
        Map<String, Object> rawResourceValues
) {

    public AdoWebhookEvent {
        rawResourceValues = rawResourceValues == null ? Map.of() : Map.copyOf(rawResourceValues);
    }

    public static AdoWebhookEvent workItemUpdated(
            String organization,
            String project,
            Long workItemId,
            String workItemType,
            Integer revision,
            String changedByDisplayName,
            String changedByEmailOrLogin,
            Set<String> changedFieldNames
    ) {
        return new AdoWebhookEvent(
                "workitem.updated",
                organization,
                new AdoWebhookResource(
                        project,
                        workItemId,
                        workItemType,
                        revision,
                        changedByDisplayName,
                        changedByEmailOrLogin,
                        changedFieldNames
                ),
                Map.of()
        );
    }
}
