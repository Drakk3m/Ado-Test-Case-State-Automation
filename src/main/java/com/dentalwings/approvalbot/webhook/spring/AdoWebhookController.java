package com.dentalwings.approvalbot.webhook.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.HttpServletRequest;
import com.dentalwings.approvalbot.config.spring.ProjectApprovalConfigResolver;
import com.dentalwings.approvalbot.event.InvalidAdoEventPayloadException;
import com.dentalwings.approvalbot.event.NormalizedWorkItemEvent;
import com.dentalwings.approvalbot.processing.pipeline.WebhookEventProcessingPipeline;
import com.dentalwings.approvalbot.processing.pipeline.WebhookProcessingStatus;
import com.fasterxml.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/ado/webhooks")
public class AdoWebhookController
{

    private static final Logger LOGGER = LoggerFactory.getLogger(AdoWebhookController.class);

    private final ObjectProvider<WebhookEventProcessingPipeline> pipelineProvider;
    private final AdoWebhookEventMapper mapper;
    private final ProjectApprovalConfigResolver configResolver;
    private final WebhookSharedSecretValidator sharedSecretValidator;
    private final WebhookDebugCaptureService debugCaptureService;

    public AdoWebhookController(ObjectProvider<WebhookEventProcessingPipeline> pipelineProvider,
            AdoWebhookEventMapper mapper, ProjectApprovalConfigResolver configResolver,
            WebhookSharedSecretValidator sharedSecretValidator, WebhookDebugCaptureService debugCaptureService)
    {
        this.pipelineProvider = pipelineProvider;
        this.mapper = mapper;
        this.configResolver = configResolver;
        this.sharedSecretValidator = sharedSecretValidator;
        this.debugCaptureService = debugCaptureService;
    }

    @PostMapping("/work-item-updated")
    public ResponseEntity<WebhookResponse> workItemUpdated(@RequestHeader HttpHeaders headers,
            @RequestBody JsonNode request, HttpServletRequest servletRequest)
    {
        var sharedSecretResult = sharedSecretValidator.validate(headers.getFirst(sharedSecretValidator.headerName()));
        if (!sharedSecretResult.valid())
        {
            LOGGER.warn("ADO webhook shared-secret validation failed reason={}",
                    sharedSecretResult.failureReason().logValue());
            if (sharedSecretResult.failureReason() == WebhookSharedSecretValidator.FailureReason.NOT_CONFIGURED)
            {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new WebhookResponse("NOT_CONFIGURED",
                                "Webhook shared secret is required but not configured."));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new WebhookResponse("UNAUTHORIZED", "Webhook shared-secret validation failed."));
        }

        NormalizedWorkItemEvent normalizedEvent;
        try
        {
            normalizedEvent = mapper.normalize(request);
        }
        catch (InvalidAdoEventPayloadException ex)
        {
            LOGGER.warn("ADO webhook payload validation failed errorCount={}", ex.errors().size());
            return ResponseEntity.badRequest()
                    .body(new WebhookResponse("FAILED_MALFORMED_EVENT", String.join("; ", ex.errors())));
        }

        captureDebugPayloadSafely(servletRequest, normalizedEvent);

        var pipeline = pipelineProvider.getIfAvailable();
        if (pipeline == null)
        {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new WebhookResponse("UNAVAILABLE", "Webhook processing pipeline is not configured."));
        }

        var event = mapper.toWebhookEvent(normalizedEvent);
        var resource = event.resource();
        LOGGER.info("Received ADO webhook event project={} workItemId={} revision={}",
                resource == null ? null : resource.project(), resource == null ? null : resource.workItemId(),
                resource == null ? null : resource.revision());
        var projectConfig = configResolver.findByProjectName(event.resource().project()).orElse(null);
        var result = pipeline.process(event, projectConfig);
        var httpStatus = status(result.status());
        LOGGER.info(
                "ADO webhook processing completed project={} workItemId={} revision={} result={} httpStatus={} reason={}",
                resource == null ? null : resource.project(), resource == null ? null : resource.workItemId(),
                resource == null ? null : resource.revision(), result.status(), httpStatus.value(), result.reason());
        return ResponseEntity.status(httpStatus).body(new WebhookResponse(result.status().name(), result.reason()));
    }

    private void captureDebugPayloadSafely(HttpServletRequest servletRequest,
            NormalizedWorkItemEvent event)
    {
        try
        {
            debugCaptureService.capture(extractRawBody(servletRequest), event);
        }
        catch (RuntimeException ex)
        {
            LOGGER.warn("ADO webhook debug capture failed cause={}", ex.getClass().getSimpleName());
        }
    }

    private String extractRawBody(HttpServletRequest request)
    {
        var wrapper = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (wrapper == null)
        {
            return "";
        }
        var bytes = wrapper.getContentAsByteArray();
        if (bytes.length == 0)
        {
            return "";
        }
        return new String(bytes, wrapper.getCharacterEncoding() == null ? java.nio.charset.StandardCharsets.UTF_8
                : java.nio.charset.Charset.forName(wrapper.getCharacterEncoding()));
    }

    private HttpStatus status(WebhookProcessingStatus status)
    {
        return switch (status)
        {
            case SKIPPED, COMPLETED, COMPLETED_WITH_WARNING -> HttpStatus.ACCEPTED;
            case FAILED_MALFORMED_EVENT, FAILED_NON_RETRYABLE -> HttpStatus.BAD_REQUEST;
            case FAILED_RETRYABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    public record WebhookResponse(String status, String reason)
    {
    }
}
