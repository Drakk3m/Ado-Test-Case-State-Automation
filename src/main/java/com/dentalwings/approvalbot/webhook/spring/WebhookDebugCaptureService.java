package com.dentalwings.approvalbot.webhook.spring;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dentalwings.approvalbot.config.spring.ApprovalBotProperties;
import com.dentalwings.approvalbot.webhook.spring.dto.AdoServiceHookWorkItemUpdatedRequest;

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

    public void capture(String rawRequestBody, AdoServiceHookWorkItemUpdatedRequest request)
    {
        if (!enabled)
        {
            return;
        }
        latest.set(new CapturedWebhookEvent(Instant.now(), value(rawRequestBody),
                value(request == null ? null : request.eventType()), project(request), workItemId(request), revision(request)));
    }

    public Optional<CapturedWebhookEvent> latestCapture()
    {
        return Optional.ofNullable(latest.get());
    }

    private String project(AdoServiceHookWorkItemUpdatedRequest request)
    {
        if (request == null || request.resource() == null)
        {
            return null;
        }
        if (request.resource().project() != null && !request.resource().project().isBlank())
        {
            return request.resource().project();
        }
        var revision = request.resource().revision();
        if (revision == null || revision.fields() == null)
        {
            return null;
        }
        var value = revision.fields().get("System.TeamProject");
        return value == null ? null : value.toString();
    }

    private Integer workItemId(AdoServiceHookWorkItemUpdatedRequest request)
    {
        if (request == null || request.resource() == null || request.resource().id() == null)
        {
            return null;
        }
        return Math.toIntExact(request.resource().id());
    }

    private Integer revision(AdoServiceHookWorkItemUpdatedRequest request)
    {
        if (request == null || request.resource() == null)
        {
            return null;
        }
        if (request.resource().rev() != null)
        {
            return request.resource().rev();
        }
        return request.resource().revision() == null ? null : request.resource().revision().rev();
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


