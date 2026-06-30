package com.dentalwings.approvalbot.webhook.spring;

import org.springframework.stereotype.Component;

import com.dentalwings.approvalbot.event.AdoWorkItemEventParser;
import com.dentalwings.approvalbot.event.NormalizedWorkItemEvent;
import com.dentalwings.approvalbot.webhook.AdoWebhookEvent;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class AdoWebhookEventMapper
{

    private final AdoWorkItemEventParser parser = new AdoWorkItemEventParser();

    public NormalizedWorkItemEvent normalize(JsonNode request)
    {
        return parser.parse(request);
    }

    public AdoWebhookEvent toWebhookEvent(NormalizedWorkItemEvent event)
    {
        var changedBy = event.changedBy();
        return AdoWebhookEvent.workItemUpdated(event.organization(), event.project(), event.workItemId(),
                event.workItemType(), event.revision(), changedBy == null ? null : changedBy.displayName(),
                changedBy == null ? null : changedBy.uniqueName(), event.changedFieldNames());
    }
}
