package com.dentalwings.approvalbot.ado.http;

public class AdoClientException extends RuntimeException {

    public AdoClientException(String message) {
        super(message);
    }

    public AdoClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
