package com.dentalwings.approvalbot.ado.http;

public class AdoClientNonRetryableException extends AdoClientException {

    public AdoClientNonRetryableException(String message) {
        super(message);
    }

    public AdoClientNonRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
