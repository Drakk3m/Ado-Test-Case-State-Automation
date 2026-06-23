package com.dentalwings.approvalbot.ado.http;

public class AdoClientRetryableException extends AdoClientException {

    public AdoClientRetryableException(String message) {
        super(message);
    }

    public AdoClientRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
