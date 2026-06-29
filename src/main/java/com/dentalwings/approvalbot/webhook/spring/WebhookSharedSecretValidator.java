package com.dentalwings.approvalbot.webhook.spring;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.dentalwings.approvalbot.config.spring.ApprovalBotProperties;

@Component
public class WebhookSharedSecretValidator
{

    private final ApprovalBotProperties properties;
    private final RuntimeWebhookSecretService secretService;

    @Autowired
    public WebhookSharedSecretValidator(ApprovalBotProperties properties, RuntimeWebhookSecretService secretService)
    {
        this.properties = properties;
        this.secretService = secretService;
    }

    WebhookSharedSecretValidator(ApprovalBotProperties properties)
    {
        this(properties, new RuntimeWebhookSecretService(properties));
    }

    public String headerName()
    {
        return properties.getWebhook().getSharedSecret().getHeaderName();
    }

    public ValidationResult validate(String receivedSecret)
    {
        var sharedSecret = properties.getWebhook().getSharedSecret();
        if (!sharedSecret.isEnabled())
        {
            return ValidationResult.accepted();
        }
        var expectedSecret = secretService.currentSecret();
        if (expectedSecret.isBlank())
        {
            return ValidationResult.invalid(FailureReason.NOT_CONFIGURED);
        }
        if (receivedSecret == null || receivedSecret.isEmpty())
        {
            return ValidationResult.invalid(FailureReason.MISSING_HEADER);
        }
        if (!constantTimeEquals(expectedSecret, receivedSecret))
        {
            return ValidationResult.invalid(FailureReason.INVALID_HEADER);
        }
        return ValidationResult.accepted();
    }

    private boolean constantTimeEquals(String expectedSecret, String receivedSecret)
    {
        var expectedBytes = bytes(expectedSecret);
        var receivedBytes = bytes(receivedSecret);
        return MessageDigest.isEqual(expectedBytes, receivedBytes);
    }

    private byte[] bytes(String value)
    {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }

    public enum FailureReason
    {
        NOT_CONFIGURED("secret not configured"),
        MISSING_HEADER("missing header"),
        INVALID_HEADER("invalid header");

        private final String logValue;

        FailureReason(String logValue)
        {
            this.logValue = logValue;
        }

        public String logValue()
        {
            return logValue;
        }
    }

    public record ValidationResult(boolean valid, FailureReason failureReason)
    {

        public static ValidationResult accepted()
        {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(FailureReason failureReason)
        {
            return new ValidationResult(false, failureReason);
        }
    }
}
