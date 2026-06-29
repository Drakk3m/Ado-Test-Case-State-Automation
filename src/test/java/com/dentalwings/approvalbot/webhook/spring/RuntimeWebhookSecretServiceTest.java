package com.dentalwings.approvalbot.webhook.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.dentalwings.approvalbot.config.spring.ApprovalBotProperties;

class RuntimeWebhookSecretServiceTest
{

    @Test
    void missingConfiguredSecretStartsNotConfigured()
    {
        var service = service("");

        assertThat(service.isConfigured()).isFalse();
        assertThat(service.currentSecret()).isEmpty();
    }

    @Test
    void submittedSecretIsKeptForTheRunningProcess()
    {
        var service = service("");

        service.submitSecret(" runtime-secret ");

        assertThat(service.isConfigured()).isTrue();
        assertThat(service.currentSecret()).isEqualTo(" runtime-secret ");
    }

    @Test
    void blankRuntimeSecretIsRejectedWithoutReplacingConfiguredSecret()
    {
        var service = service("configured-secret");

        assertThatThrownBy(() -> service.submitSecret("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Webhook shared secret value is required.");
        assertThat(service.currentSecret()).isEqualTo("configured-secret");
    }

    private RuntimeWebhookSecretService service(String configuredSecret)
    {
        var properties = new ApprovalBotProperties();
        properties.getWebhook().getSharedSecret().setValue(configuredSecret);
        return new RuntimeWebhookSecretService(properties);
    }
}
