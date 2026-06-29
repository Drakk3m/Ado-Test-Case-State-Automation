package com.dentalwings.approvalbot.webhook.spring;

import com.dentalwings.approvalbot.config.spring.ApprovalBotProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSharedSecretValidatorTest {

    @Test
    void disabledValidationAllowsMissingHeader() {
        var properties = properties(false, "X-ADO-Webhook-Secret", "");
        var validator = new WebhookSharedSecretValidator(properties);

        var result = validator.validate(null);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void enabledValidationRejectsMissingHeader() {
        var validator = new WebhookSharedSecretValidator(properties(true, "X-ADO-Webhook-Secret", "expected"));

        var result = validator.validate(null);

        assertThat(result.valid()).isFalse();
        assertThat(result.failureReason()).isEqualTo(WebhookSharedSecretValidator.FailureReason.MISSING_HEADER);
    }

    @Test
    void enabledValidationReturnsNotConfiguredWhenSecretIsMissing()
    {
        var validator = new WebhookSharedSecretValidator(properties(true, "X-ADO-Webhook-Secret", ""));

        var result = validator.validate("anything");

        assertThat(result.valid()).isFalse();
        assertThat(result.failureReason()).isEqualTo(WebhookSharedSecretValidator.FailureReason.NOT_CONFIGURED);
    }

    @Test
    void runtimeSubmittedSecretEnablesValidation()
    {
        var properties = properties(true, "X-ADO-Webhook-Secret", "");
        var secretService = new RuntimeWebhookSecretService(properties);
        var validator = new WebhookSharedSecretValidator(properties, secretService);

        secretService.submitSecret("runtime-secret");

        assertThat(validator.validate("runtime-secret").valid()).isTrue();
        assertThat(validator.validate("wrong").failureReason())
                .isEqualTo(WebhookSharedSecretValidator.FailureReason.INVALID_HEADER);
    }

    @Test
    void enabledValidationRejectsWrongHeader() {
        var validator = new WebhookSharedSecretValidator(properties(true, "X-ADO-Webhook-Secret", "expected"));

        var result = validator.validate("wrong");

        assertThat(result.valid()).isFalse();
        assertThat(result.failureReason()).isEqualTo(WebhookSharedSecretValidator.FailureReason.INVALID_HEADER);
    }

    @Test
    void enabledValidationAcceptsExactHeader() {
        var validator = new WebhookSharedSecretValidator(properties(true, "X-ADO-Webhook-Secret", "expected"));

        var result = validator.validate("expected");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void enabledValidationDoesNotTrimHeaderValue() {
        var validator = new WebhookSharedSecretValidator(properties(true, "X-ADO-Webhook-Secret", "expected"));

        var result = validator.validate(" expected ");

        assertThat(result.valid()).isFalse();
        assertThat(result.failureReason()).isEqualTo(WebhookSharedSecretValidator.FailureReason.INVALID_HEADER);
    }

    @Test
    void exposesConfiguredHeaderName() {
        var validator = new WebhookSharedSecretValidator(properties(true, "X-Custom-Webhook-Secret", "expected"));

        assertThat(validator.headerName()).isEqualTo("X-Custom-Webhook-Secret");
    }

    private ApprovalBotProperties properties(boolean enabled, String headerName, String value) {
        var properties = new ApprovalBotProperties();
        properties.getWebhook().getSharedSecret().setEnabled(enabled);
        properties.getWebhook().getSharedSecret().setHeaderName(headerName);
        properties.getWebhook().getSharedSecret().setValue(value);
        return properties;
    }
}
