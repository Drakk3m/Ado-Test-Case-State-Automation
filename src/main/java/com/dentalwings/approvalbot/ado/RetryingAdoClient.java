package com.dentalwings.approvalbot.ado;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.dentalwings.approvalbot.ado.http.AdoClientRetryableException;
import com.dentalwings.approvalbot.domain.PatchOperation;

/**
 * Retries only failures already classified as transient by the ADO boundary. Comment retries are intentionally
 * deferred because {@link AdoCommentResult} does not currently expose retryability.
 */
public class RetryingAdoClient implements AdoClient
{

    static final int MAX_ATTEMPTS = 3;
    static final long BASE_DELAY_MILLIS = 100;

    private static final Logger LOGGER = LoggerFactory.getLogger(RetryingAdoClient.class);

    private final AdoClient delegate;
    private final Sleeper sleeper;
    private final Jitter jitter;

    public RetryingAdoClient(AdoClient delegate)
    {
        this(delegate, Thread::sleep, upperBound -> ThreadLocalRandom.current().nextLong(upperBound));
    }

    RetryingAdoClient(AdoClient delegate, Sleeper sleeper, Jitter jitter)
    {
        this.delegate = delegate;
        this.sleeper = sleeper;
        this.jitter = jitter;
    }

    public AdoClient delegate()
    {
        return delegate;
    }

    @Override
    public AdoWorkItem fetchWorkItem(AdoWorkItemKey key)
    {
        return executeRead("fetchWorkItem", key, null, () -> delegate.fetchWorkItem(key));
    }

    @Override
    public AdoWorkItemRevision fetchWorkItemRevision(AdoWorkItemKey key, int revision)
    {
        return executeRead("fetchWorkItemRevision", key, revision,
                () -> delegate.fetchWorkItemRevision(key, revision));
    }

    @Override
    public AdoPatchResult patchWorkItem(AdoWorkItemKey key, List<PatchOperation> patchOperations)
    {
        for (var attempt = 1; attempt <= MAX_ATTEMPTS; attempt++)
        {
            logAttempt("patchWorkItem", key, null, attempt);
            var result = delegate.patchWorkItem(key, patchOperations);
            if (result.successful())
            {
                logRecovered("patchWorkItem", key, null, attempt);
                return result;
            }
            if (!result.retryable())
            {
                LOGGER.debug(
                        "ADO retry operation completed operation=patchWorkItem attempt={} maxAttempts={} project={} workItemId={} outcome=FAILED_NON_RETRYABLE",
                        attempt, MAX_ATTEMPTS, key.project(), key.workItemId());
                return result;
            }
            if (attempt == MAX_ATTEMPTS)
            {
                logExhausted("patchWorkItem", key, null, attempt);
                return result;
            }
            pauseBeforeRetry("patchWorkItem", key, null, attempt);
        }
        throw new IllegalStateException("ADO PATCH retry loop completed without a result.");
    }

    @Override
    public AdoCommentResult createWorkItemComment(AdoWorkItemKey key, String commentText)
    {
        return delegate.createWorkItemComment(key, commentText);
    }

    private <T> T executeRead(String operation, AdoWorkItemKey key, Integer revision, Supplier<T> action)
    {
        for (var attempt = 1; attempt <= MAX_ATTEMPTS; attempt++)
        {
            logAttempt(operation, key, revision, attempt);
            try
            {
                var result = action.get();
                logRecovered(operation, key, revision, attempt);
                return result;
            }
            catch (AdoClientRetryableException exception)
            {
                if (attempt == MAX_ATTEMPTS)
                {
                    logExhausted(operation, key, revision, attempt);
                    throw exception;
                }
                pauseBeforeRetry(operation, key, revision, attempt);
            }
        }
        throw new IllegalStateException("ADO read retry loop completed without a result.");
    }

    private void pauseBeforeRetry(String operation, AdoWorkItemKey key, Integer revision, int attempt)
    {
        var delay = BASE_DELAY_MILLIS * (1L << (attempt - 1));
        var jitterBound = Math.max(1, delay / 2 + 1);
        var backoffMillis = delay + jitter.next(jitterBound);
        logRetry(operation, key, revision, attempt, backoffMillis);
        try
        {
            sleeper.sleep(backoffMillis);
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            throw new AdoClientRetryableException("Azure DevOps retry interrupted.", exception);
        }
    }

    private void logAttempt(String operation, AdoWorkItemKey key, Integer revision, int attempt)
    {
        LOGGER.debug(
                "ADO retry operation attempt operation={} attempt={} maxAttempts={} project={} workItemId={} revision={}",
                operation, attempt, MAX_ATTEMPTS, key.project(), key.workItemId(), revisionLabel(revision));
    }

    private void logRetry(String operation, AdoWorkItemKey key, Integer revision, int attempt, long backoffMillis)
    {
        LOGGER.warn(
                "ADO retry scheduled operation={} attempt={} maxAttempts={} project={} workItemId={} revision={} backoffMillis={} outcome=RETRYING",
                operation, attempt, MAX_ATTEMPTS, key.project(), key.workItemId(), revisionLabel(revision),
                backoffMillis);
    }

    private void logRecovered(String operation, AdoWorkItemKey key, Integer revision, int attempt)
    {
        if (attempt == 1)
        {
            return;
        }
        LOGGER.info(
                "ADO retry operation completed operation={} attempt={} maxAttempts={} project={} workItemId={} revision={} outcome=SUCCEEDED",
                operation, attempt, MAX_ATTEMPTS, key.project(), key.workItemId(), revisionLabel(revision));
    }

    private void logExhausted(String operation, AdoWorkItemKey key, Integer revision, int attempt)
    {
        LOGGER.warn(
                "ADO retry operation completed operation={} attempt={} maxAttempts={} project={} workItemId={} revision={} outcome=FAILED_RETRYABLE",
                operation, attempt, MAX_ATTEMPTS, key.project(), key.workItemId(), revisionLabel(revision));
    }

    private Object revisionLabel(Integer revision)
    {
        return revision == null ? "n/a" : revision;
    }

    @FunctionalInterface
    interface Sleeper
    {
        void sleep(long millis) throws InterruptedException;
    }

    @FunctionalInterface
    interface Jitter
    {
        long next(long upperBound);
    }
}
