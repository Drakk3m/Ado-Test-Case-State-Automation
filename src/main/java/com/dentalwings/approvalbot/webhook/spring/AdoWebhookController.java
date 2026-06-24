package com.dentalwings.approvalbot.webhook.spring;

import com.dentalwings.approvalbot.config.spring.ProjectApprovalConfigResolver;
import com.dentalwings.approvalbot.processing.pipeline.WebhookEventProcessingPipeline;
import com.dentalwings.approvalbot.processing.pipeline.WebhookProcessingResult;
import com.dentalwings.approvalbot.processing.pipeline.WebhookProcessingStatus;
import com.dentalwings.approvalbot.webhook.spring.dto.AdoServiceHookWorkItemUpdatedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ado/webhooks")
public class AdoWebhookController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdoWebhookController.class);

    private final ObjectProvider<WebhookEventProcessingPipeline> pipelineProvider;
    private final AdoWebhookEventMapper mapper;
    private final ProjectApprovalConfigResolver configResolver;
    private final WebhookSharedSecretValidator sharedSecretValidator;

    public AdoWebhookController(
            ObjectProvider<WebhookEventProcessingPipeline> pipelineProvider,
            AdoWebhookEventMapper mapper,
            ProjectApprovalConfigResolver configResolver,
            WebhookSharedSecretValidator sharedSecretValidator
    ) {
        this.pipelineProvider = pipelineProvider;
        this.mapper = mapper;
        this.configResolver = configResolver;
        this.sharedSecretValidator = sharedSecretValidator;
    }

    @PostMapping("/work-item-updated")
    public ResponseEntity<WebhookResponse> workItemUpdated(
            @RequestHeader HttpHeaders headers,
            @RequestBody AdoServiceHookWorkItemUpdatedRequest request
    ) {
        var sharedSecretResult = sharedSecretValidator.validate(headers.getFirst(sharedSecretValidator.headerName()));
        if (!sharedSecretResult.valid()) {
            LOGGER.warn("ADO webhook shared-secret validation failed reason={}",
                    sharedSecretResult.failureReason().logValue());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new WebhookResponse("UNAUTHORIZED", "Webhook shared-secret validation failed."));
        }

        var pipeline = pipelineProvider.getIfAvailable();
        if (pipeline == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new WebhookResponse("UNAVAILABLE", "Webhook processing pipeline is not configured."));
        }

        var event = mapper.toWebhookEvent(request);
        var resource = event.resource();
        LOGGER.info("Received ADO webhook event project={} workItemId={} revision={}",
                resource == null ? null : resource.project(),
                resource == null ? null : resource.workItemId(),
                resource == null ? null : resource.revision());
        var projectConfig = configResolver.findByProjectName(event.resource().project()).orElse(null);
        var result = pipeline.process(event, projectConfig);
        var httpStatus = status(result.status());
        LOGGER.info("ADO webhook processing completed project={} workItemId={} revision={} result={} httpStatus={} reason={}",
                resource == null ? null : resource.project(),
                resource == null ? null : resource.workItemId(),
                resource == null ? null : resource.revision(),
                result.status(),
                httpStatus.value(),
                result.reason());
        return ResponseEntity.status(httpStatus)
                .body(new WebhookResponse(result.status().name(), result.reason()));
    }

    private HttpStatus status(WebhookProcessingStatus status) {
        return switch (status) {
            case SKIPPED, COMPLETED, COMPLETED_WITH_WARNING -> HttpStatus.ACCEPTED;
            case FAILED_MALFORMED_EVENT, FAILED_NON_RETRYABLE -> HttpStatus.BAD_REQUEST;
            case FAILED_RETRYABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    public record WebhookResponse(String status, String reason) {
    }
}
