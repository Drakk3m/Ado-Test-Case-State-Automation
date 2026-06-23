package com.dentalwings.approvalbot.ado;

public record AdoPatchResult(boolean successful, int revision, String message, boolean retryable) {

    public static AdoPatchResult success(int revision) {
        return new AdoPatchResult(true, revision, null, false);
    }

    public static AdoPatchResult failure(String message) {
        return retryableFailure(message);
    }

    public static AdoPatchResult retryableFailure(String message) {
        return new AdoPatchResult(false, -1, message, true);
    }

    public static AdoPatchResult nonRetryableFailure(String message) {
        return new AdoPatchResult(false, -1, message, false);
    }
}
