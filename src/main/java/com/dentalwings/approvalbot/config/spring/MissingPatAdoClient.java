package com.dentalwings.approvalbot.config.spring;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.dentalwings.approvalbot.ado.AdoClient;
import com.dentalwings.approvalbot.ado.AdoCommentResult;
import com.dentalwings.approvalbot.ado.AdoPatchResult;
import com.dentalwings.approvalbot.ado.AdoWorkItem;
import com.dentalwings.approvalbot.ado.AdoWorkItemKey;
import com.dentalwings.approvalbot.ado.AdoWorkItemRevision;
import com.dentalwings.approvalbot.ado.DryRunAdoClient;
import com.dentalwings.approvalbot.ado.RuntimeAdoCredentialService;
import com.dentalwings.approvalbot.ado.http.AdoClientNonRetryableException;
import com.dentalwings.approvalbot.ado.http.AzureDevOpsHttpClient;
import com.dentalwings.approvalbot.domain.PatchOperation;

public class MissingPatAdoClient implements AdoClient
{

    private static final String MESSAGE =
            "ADO_PERSONAL_ACCESS_TOKEN is missing while ado.http-client-enabled=true; ADO calls are non-retryable until configured.";

    private final AdoProperties properties;
    private final RuntimeAdoCredentialService credentialService;
    private final AtomicReference<String> cachedPat = new AtomicReference<>("");
    private final AtomicReference<AdoClient> delegate = new AtomicReference<>();

    public MissingPatAdoClient(AdoProperties properties, RuntimeAdoCredentialService credentialService)
    {
        this.properties = properties;
        this.credentialService = credentialService;
    }

    @Override
    public AdoWorkItem fetchWorkItem(AdoWorkItemKey key)
    {
        return requireDelegate().fetchWorkItem(key);
    }

    @Override
    public AdoWorkItemRevision fetchWorkItemRevision(AdoWorkItemKey key, int revision)
    {
        return requireDelegate().fetchWorkItemRevision(key, revision);
    }

    @Override
    public AdoPatchResult patchWorkItem(AdoWorkItemKey key, List<PatchOperation> patchOperations)
    {
        return requireDelegate().patchWorkItem(key, patchOperations);
    }

    @Override
    public AdoCommentResult createWorkItemComment(AdoWorkItemKey key, String commentText)
    {
        return requireDelegate().createWorkItemComment(key, commentText);
    }

    private AdoClient requireDelegate()
    {
        var token = credentialService.currentPersonalAccessToken();
        if (token.isBlank())
        {
            throw new AdoClientNonRetryableException(MESSAGE);
        }
        var currentPat = cachedPat.get();
        var cachedDelegate = delegate.get();
        if (cachedDelegate != null && token.equals(currentPat))
        {
            return cachedDelegate;
        }
        var httpClient = AzureDevOpsHttpClient.fromProperties(properties, token);
        var runtimeDelegate = properties.isDryRun() ? new DryRunAdoClient(httpClient) : httpClient;
        cachedPat.set(token);
        delegate.set(runtimeDelegate);
        return runtimeDelegate;
    }
}


