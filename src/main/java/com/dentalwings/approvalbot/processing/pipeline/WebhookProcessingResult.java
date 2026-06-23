package com.dentalwings.approvalbot.processing.pipeline;

import com.dentalwings.approvalbot.processing.WorkItemProcessingResult;
import com.dentalwings.approvalbot.webhook.EventClassification;
import java.util.Optional;

public record WebhookProcessingResult(
        WebhookProcessingStatus status,
        String reason,
        EventClassification classification,
        WorkItemProcessingResult workItemProcessingResult
) {

    public static WebhookProcessingResult skipped(String reason, EventClassification classification) {
        return new WebhookProcessingResult(WebhookProcessingStatus.SKIPPED, reason, classification, null);
    }

    public static WebhookProcessingResult malformed(String reason, EventClassification classification) {
        return new WebhookProcessingResult(WebhookProcessingStatus.FAILED_MALFORMED_EVENT, reason, classification, null);
    }

    public static WebhookProcessingResult fromWorkItemResult(
            WorkItemProcessingResult result,
            EventClassification classification
    ) {
        return new WebhookProcessingResult(statusFrom(result), result.reason(), classification, result);
    }

    public Optional<WorkItemProcessingResult> maybeWorkItemProcessingResult() {
        return Optional.ofNullable(workItemProcessingResult);
    }

    private static WebhookProcessingStatus statusFrom(WorkItemProcessingResult result) {
        return switch (result.result()) {
            case SKIPPED -> WebhookProcessingStatus.SKIPPED;
            case COMPLETED -> WebhookProcessingStatus.COMPLETED;
            case COMPLETED_WITH_WARNING -> WebhookProcessingStatus.COMPLETED_WITH_WARNING;
            case FAILED_RETRYABLE -> WebhookProcessingStatus.FAILED_RETRYABLE;
            case FAILED_NON_RETRYABLE -> WebhookProcessingStatus.FAILED_NON_RETRYABLE;
        };
    }
}
