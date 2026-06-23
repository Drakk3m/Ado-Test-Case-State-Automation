package com.dentalwings.approvalbot.webhook;

public enum EventClassificationStatus {
    PROCESSABLE,
    SKIPPED_DISABLED_PROJECT,
    SKIPPED_UNSUPPORTED_WORK_ITEM_TYPE,
    SKIPPED_BOT_EVENT,
    FAILED_MALFORMED_EVENT
}
