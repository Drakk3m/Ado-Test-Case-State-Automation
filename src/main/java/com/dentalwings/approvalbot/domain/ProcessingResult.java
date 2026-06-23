package com.dentalwings.approvalbot.domain;

public enum ProcessingResult {
    SKIPPED,
    COMPLETED,
    COMPLETED_WITH_WARNING,
    FAILED_RETRYABLE,
    FAILED_NON_RETRYABLE
}
