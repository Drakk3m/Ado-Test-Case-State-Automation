package com.dentalwings.approvalbot.ado;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dentalwings.approvalbot.config.spring.ApprovalBotProperties;

@Service
public class RuntimeAdoCredentialService
{

    private final String configuredPersonalAccessToken;
    private final AtomicReference<String> runtimePersonalAccessToken = new AtomicReference<>("");

    @Autowired
    public RuntimeAdoCredentialService(ApprovalBotProperties properties)
    {
        this(properties.getAdo().getPersonalAccessToken());
    }

    RuntimeAdoCredentialService(String configuredPersonalAccessToken)
    {
        this.configuredPersonalAccessToken = normalize(configuredPersonalAccessToken);
    }

    public boolean isPatConfigured()
    {
        return !currentPersonalAccessToken().isBlank();
    }

    public String currentPersonalAccessToken()
    {
        var runtimeToken = normalize(runtimePersonalAccessToken.get());
        if (!runtimeToken.isBlank())
        {
            return runtimeToken;
        }
        return configuredPersonalAccessToken;
    }

    public void submitPersonalAccessToken(String personalAccessToken)
    {
        var normalizedToken = normalize(personalAccessToken);
        if (normalizedToken.isBlank())
        {
            throw new IllegalArgumentException("ADO PAT value is required.");
        }
        runtimePersonalAccessToken.set(normalizedToken);
    }

    private String normalize(String value)
    {
        return value == null ? "" : value.trim();
    }
}

