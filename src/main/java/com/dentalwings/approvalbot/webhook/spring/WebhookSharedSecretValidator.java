package com.dentalwings.approvalbot.webhook.spring;

import com.dentalwings.approvalbot.config.spring.ApprovalBotProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

@Component
public class WebhookSharedSecretValidator {

    private final ApprovalBotProperties properties;

    public WebhookSharedSecretValidator(ApprovalBotProperties properties) {
        this.properties = properties;
    }

    public String headerName() {
        return properties.getWebhook().getSharedSecret().getHeaderName();
    }

    public ValidationResult validate(String receivedSecret) {
        var sharedSecret = properties.getWebhook().getSharedSecret();
        if (!sharedSecret.isEnabled()) {
            return ValidationResult.accepted();
        }
        if (receivedSecret == null || receivedSecret.isEmpty()) {
            return ValidationResult.invalid(FailureReason.MISSING_HEADER);
        }
        if (!constantTimeEquals(sharedSecret.getValue(), receivedSecret)) {
            return ValidationResult.invalid(FailureReason.INVALID_HEADER);
        }
        return ValidationResult.accepted();
    }

    private boolean constantTimeEquals(String expectedSecret, String receivedSecret) {
        var expectedBytes = bytes(expectedSecret);
        var receivedBytes = bytes(receivedSecret);
        return MessageDigest.isEqual(expectedBytes, receivedBytes);
    }

    private byte[] bytes(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }

    public enum FailureReason {
        MISSING_HEADER("missing header"),
        INVALID_HEADER("invalid header");

        private final String logValue;

        FailureReason(String logValue) {
            this.logValue = logValue;
        }

        public String logValue() {
            return logValue;
        }
    }

    public record ValidationResult(boolean valid, FailureReason failureReason) {

        public static ValidationResult accepted() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(FailureReason failureReason) {
            return new ValidationResult(false, failureReason);
        }
    }
}
