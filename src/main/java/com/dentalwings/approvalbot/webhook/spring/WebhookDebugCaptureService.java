package com.dentalwings.approvalbot.webhook.spring;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dentalwings.approvalbot.config.spring.ApprovalBotProperties;
import com.dentalwings.approvalbot.event.NormalizedWorkItemEvent;

@Service
public class WebhookDebugCaptureService
{

    private final boolean enabled;
    private final AtomicReference<CapturedWebhookEvent> latest = new AtomicReference<>();

    @Autowired
    public WebhookDebugCaptureService(ApprovalBotProperties properties)
    {
        this(properties.getWebhook().isDebugCaptureEnabled());
    }

    WebhookDebugCaptureService(boolean enabled)
    {
        this.enabled = enabled;
    }

    public void capture(String rawRequestBody, NormalizedWorkItemEvent event)
    {
        if (!enabled)
        {
            return;
        }
        latest.set(new CapturedWebhookEvent(Instant.now(), value(rawRequestBody),
                value(event == null ? null : event.eventType()), event == null ? null : event.project(),
                workItemId(event), event == null ? null : event.revision()));
    }

    public Optional<CapturedWebhookEvent> latestCapture()
    {
        return Optional.ofNullable(latest.get());
    }

    private Integer workItemId(NormalizedWorkItemEvent event)
    {
        if (event == null || event.workItemId() == null)
        {
            return null;
        }
        return Math.toIntExact(event.workItemId());
    }

    private String value(String input)
    {
        return input == null ? "" : input;
    }

    public record CapturedWebhookEvent(Instant receivedAt, String rawRequestBody, String eventType, String project,
            Integer workItemId, Integer revision)
    {
    }
}


