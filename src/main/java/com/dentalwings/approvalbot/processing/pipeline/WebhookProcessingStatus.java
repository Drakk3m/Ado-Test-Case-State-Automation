package com.dentalwings.approvalbot.processing.pipeline;

public enum WebhookProcessingStatus {
    SKIPPED,
    COMPLETED,
    COMPLETED_WITH_WARNING,
    FAILED_RETRYABLE,
    FAILED_NON_RETRYABLE,
    FAILED_MALFORMED_EVENT
}
