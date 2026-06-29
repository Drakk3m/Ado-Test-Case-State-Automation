package com.dentalwings.approvalbot.webhook.spring;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dentalwings.approvalbot.config.spring.ApprovalBotProperties;

@Service
public class RuntimeWebhookSecretService
{

    private final String configuredSecret;
    private final boolean required;
    private final AtomicReference<String> runtimeSecret = new AtomicReference<>("");

    @Autowired
    public RuntimeWebhookSecretService(ApprovalBotProperties properties)
    {
        this(properties.getWebhook().getSharedSecret().getValue(),
                properties.getWebhook().getSharedSecret().isEnabled());
    }

    RuntimeWebhookSecretService(String configuredSecret)
    {
        this(configuredSecret, true);
    }

    RuntimeWebhookSecretService(String configuredSecret, boolean required)
    {
        this.configuredSecret = value(configuredSecret);
        this.required = required;
    }

    public boolean isConfigured()
    {
        return !currentSecret().isBlank();
    }

    public boolean isRequired()
    {
        return required;
    }

    public String currentSecret()
    {
        var submittedSecret = value(runtimeSecret.get());
        return submittedSecret.isBlank() ? configuredSecret : submittedSecret;
    }

    public void submitSecret(String secret)
    {
        var submittedSecret = value(secret);
        if (submittedSecret.isBlank())
        {
            throw new IllegalArgumentException("Webhook shared secret value is required.");
        }
        runtimeSecret.set(submittedSecret);
    }

    private String value(String secret)
    {
        return secret == null ? "" : secret;
    }
}
