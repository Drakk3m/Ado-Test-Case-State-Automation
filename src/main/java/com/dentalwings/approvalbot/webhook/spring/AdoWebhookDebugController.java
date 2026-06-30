package com.dentalwings.approvalbot.webhook.spring;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local debug endpoint for inspecting the last captured ADO webhook event.
 *
 * <p>Only available when {@code webhook.debug-capture-enabled=true}. Never exposes request headers
 * or secrets — only the raw body and safe structural metadata captured by
 * {@link WebhookDebugCaptureService}.
 */
@RestController
@RequestMapping("/debug/ado-webhook")
@ConditionalOnProperty(name = "webhook.debug-capture-enabled", havingValue = "true")
public class AdoWebhookDebugController
{

    private final WebhookDebugCaptureService captureService;

    public AdoWebhookDebugController(WebhookDebugCaptureService captureService)
    {
        this.captureService = captureService;
    }

    @GetMapping("/last-event")
    public ResponseEntity<DebugLastEventResponse> lastEvent()
    {
        return captureService.latestCapture()
                .map(DebugLastEventResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Safe, header-free representation of the last captured webhook event. Contains the raw
     * request body and structural metadata only — no secrets or request headers are ever included.
     */
    public record DebugLastEventResponse(
            Instant receivedAt,
            String rawRequestBody,
            String eventType,
            String project,
            Integer workItemId,
            Integer revision)
    {
        static DebugLastEventResponse from(WebhookDebugCaptureService.CapturedWebhookEvent event)
        {
            return new DebugLastEventResponse(
                    event.receivedAt(),
                    event.rawRequestBody(),
                    event.eventType(),
                    event.project(),
                    event.workItemId(),
                    event.revision());
        }
    }
}

