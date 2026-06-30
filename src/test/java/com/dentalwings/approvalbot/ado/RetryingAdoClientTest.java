package com.dentalwings.approvalbot.ado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import com.dentalwings.approvalbot.ado.http.AdoClientNonRetryableException;
import com.dentalwings.approvalbot.ado.http.AdoClientRetryableException;
import com.dentalwings.approvalbot.domain.PatchOperation;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class RetryingAdoClientTest
{

    private static final AdoWorkItemKey KEY = new AdoWorkItemKey("org", "ProjectA", 123);
    private static final AdoWorkItem WORK_ITEM = new AdoWorkItem(123, "ProjectA", "Test Case", 27, "In Review",
            Map.of());

    @Test
    void readRetriesRetryableFailureAndSucceedsOnLaterAttempt()
    {
        var delegate = mock(AdoClient.class);
        when(delegate.fetchWorkItem(KEY)).thenThrow(new AdoClientRetryableException("temporary"))
                .thenReturn(WORK_ITEM);
        var delays = new ArrayList<Long>();
        var client = client(delegate, delays);

        assertThat(client.fetchWorkItem(KEY)).isSameAs(WORK_ITEM);
        assertThat(delays).containsExactly(100L);
        verify(delegate, times(2)).fetchWorkItem(KEY);
    }

    @Test
    void readStopsAfterMaximumAttemptsAndKeepsRetryableFailure()
    {
        var delegate = mock(AdoClient.class);
        when(delegate.fetchWorkItem(KEY)).thenThrow(new AdoClientRetryableException("temporary"));
        var delays = new ArrayList<Long>();
        var client = client(delegate, delays);

        assertThatThrownBy(() -> client.fetchWorkItem(KEY)).isInstanceOf(AdoClientRetryableException.class);
        assertThat(delays).containsExactly(100L, 200L);
        verify(delegate, times(3)).fetchWorkItem(KEY);
    }

    @Test
    void readDoesNotRetryNonRetryableFailure()
    {
        var delegate = mock(AdoClient.class);
        when(delegate.fetchWorkItem(KEY)).thenThrow(new AdoClientNonRetryableException("not configured"));
        var client = client(delegate, new ArrayList<>());

        assertThatThrownBy(() -> client.fetchWorkItem(KEY)).isInstanceOf(AdoClientNonRetryableException.class);
        verify(delegate).fetchWorkItem(KEY);
    }

    @Test
    void revisionReadRetriesWithRevisionContext()
    {
        var delegate = mock(AdoClient.class);
        var revision = new AdoWorkItemRevision(123, 26, null, Map.of(), null);
        when(delegate.fetchWorkItemRevision(KEY, 26)).thenThrow(new AdoClientRetryableException("temporary"))
                .thenReturn(revision);
        var client = client(delegate, new ArrayList<>());

        var logs = captureLogs(() -> assertThat(client.fetchWorkItemRevision(KEY, 26)).isSameAs(revision));

        assertThat(logs).contains("operation=fetchWorkItemRevision").contains("revision=26")
                .contains("outcome=SUCCEEDED");
        verify(delegate, times(2)).fetchWorkItemRevision(KEY, 26);
    }

    @Test
    void patchRetriesRetryableResultAndSucceeds()
    {
        var delegate = mock(AdoClient.class);
        var operations = patchOperations();
        when(delegate.patchWorkItem(KEY, operations)).thenReturn(AdoPatchResult.retryableFailure("conflict"))
                .thenReturn(AdoPatchResult.success(28));
        var client = client(delegate, new ArrayList<>());

        assertThat(client.patchWorkItem(KEY, operations)).isEqualTo(AdoPatchResult.success(28));
        verify(delegate, times(2)).patchWorkItem(KEY, operations);
    }

    @Test
    void patchReturnsRetryableFailureAfterMaximumAttempts()
    {
        var delegate = mock(AdoClient.class);
        var operations = patchOperations();
        var failure = AdoPatchResult.retryableFailure("conflict");
        when(delegate.patchWorkItem(KEY, operations)).thenReturn(failure);
        var client = client(delegate, new ArrayList<>());

        assertThat(client.patchWorkItem(KEY, operations)).isSameAs(failure);
        verify(delegate, times(3)).patchWorkItem(KEY, operations);
    }

    @Test
    void patchDoesNotRetryNonRetryableResult()
    {
        var delegate = mock(AdoClient.class);
        var operations = patchOperations();
        var failure = AdoPatchResult.nonRetryableFailure("bad request");
        when(delegate.patchWorkItem(KEY, operations)).thenReturn(failure);
        var client = client(delegate, new ArrayList<>());

        assertThat(client.patchWorkItem(KEY, operations)).isSameAs(failure);
        verify(delegate).patchWorkItem(KEY, operations);
    }

    @Test
    void commentsRemainSingleAttemptUntilRetryabilityIsExplicit()
    {
        var delegate = mock(AdoClient.class);
        var failure = AdoCommentResult.failure("temporary");
        when(delegate.createWorkItemComment(KEY, "SECRET_COMMENT_BODY")).thenReturn(failure);
        var client = client(delegate, new ArrayList<>());

        assertThat(client.createWorkItemComment(KEY, "SECRET_COMMENT_BODY")).isSameAs(failure);
        verify(delegate).createWorkItemComment(KEY, "SECRET_COMMENT_BODY");
    }

    @Test
    void retryLogsContainContextWithoutSecretsOrPayloadValues()
    {
        var delegate = mock(AdoClient.class);
        var operations = patchOperations();
        when(delegate.patchWorkItem(KEY, operations)).thenReturn(AdoPatchResult.retryableFailure("SECRET_PAT_VALUE"))
                .thenReturn(AdoPatchResult.success(28));
        var client = client(delegate, new ArrayList<>());

        var logs = captureLogs(() -> client.patchWorkItem(KEY, operations));

        assertThat(logs).contains("operation=patchWorkItem").contains("attempt=1").contains("project=ProjectA")
                .contains("workItemId=123").contains("outcome=SUCCEEDED").doesNotContain("SECRET_PAT_VALUE")
                .doesNotContain("SECRET_PATCH_VALUE");
    }

    private RetryingAdoClient client(AdoClient delegate, List<Long> delays)
    {
        return new RetryingAdoClient(delegate, delays::add, upperBound -> 0);
    }

    private List<PatchOperation> patchOperations()
    {
        return List.of(PatchOperation.testRevision(27),
                PatchOperation.replaceField("System.State", "SECRET_PATCH_VALUE"));
    }

    private String captureLogs(Runnable action)
    {
        var logger = (Logger) LoggerFactory.getLogger(RetryingAdoClient.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try
        {
            action.run();
            return appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("",
                    (left, right) -> left + "\n" + right);
        }
        finally
        {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
