package com.dentalwings.approvalbot.webhook.spring;

import com.dentalwings.approvalbot.config.spring.ProjectApprovalConfigResolver;
import com.dentalwings.approvalbot.processing.pipeline.WebhookEventProcessingPipeline;
import com.dentalwings.approvalbot.processing.pipeline.WebhookProcessingResult;
import com.dentalwings.approvalbot.processing.pipeline.WebhookProcessingStatus;
import com.dentalwings.approvalbot.webhook.spring.dto.AdoServiceHookWorkItemUpdatedRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ado/webhooks")
public class AdoWebhookController {

    private final ObjectProvider<WebhookEventProcessingPipeline> pipelineProvider;
    private final AdoWebhookEventMapper mapper;
    private final ProjectApprovalConfigResolver configResolver;

    public AdoWebhookController(
            ObjectProvider<WebhookEventProcessingPipeline> pipelineProvider,
            AdoWebhookEventMapper mapper,
            ProjectApprovalConfigResolver configResolver
    ) {
        this.pipelineProvider = pipelineProvider;
        this.mapper = mapper;
        this.configResolver = configResolver;
    }

    @PostMapping("/work-item-updated")
    public ResponseEntity<WebhookResponse> workItemUpdated(
            @RequestBody AdoServiceHookWorkItemUpdatedRequest request
    ) {
        var pipeline = pipelineProvider.getIfAvailable();
        if (pipeline == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new WebhookResponse("UNAVAILABLE", "Webhook processing pipeline is not configured."));
        }

        var event = mapper.toWebhookEvent(request);
        var projectConfig = configResolver.findByProjectName(event.resource().project()).orElse(null);
        var result = pipeline.process(event, projectConfig);
        return ResponseEntity.status(status(result.status()))
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
