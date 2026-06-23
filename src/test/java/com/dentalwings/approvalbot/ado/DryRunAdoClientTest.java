package com.dentalwings.approvalbot.ado;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dentalwings.approvalbot.domain.PatchOperation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class DryRunAdoClientTest {

    private static final AdoWorkItemKey KEY = new AdoWorkItemKey("org", "ProjectA", 123);

    @Test
    void delegatesFetchWorkItemToUnderlyingAdoClient() {
        var delegate = new RecordingAdoClient();
        var client = new DryRunAdoClient(delegate);

        var result = client.fetchWorkItem(KEY);

        assertThat(result).isSameAs(delegate.workItem);
        assertThat(delegate.fetchWorkItemCalls).isEqualTo(1);
    }

    @Test
    void delegatesFetchWorkItemRevisionToUnderlyingAdoClient() {
        var delegate = new RecordingAdoClient();
        var client = new DryRunAdoClient(delegate);

        var result = client.fetchWorkItemRevision(KEY, 26);

        assertThat(result).isSameAs(delegate.revision);
        assertThat(delegate.fetchRevisionCalls).isEqualTo(1);
        assertThat(delegate.requestedRevision).isEqualTo(26);
    }

    @Test
    void suppressesPatchWorkItemAndDoesNotCallDelegatePatch() {
        var delegate = new RecordingAdoClient();
        var client = new DryRunAdoClient(delegate);

        var result = client.patchWorkItem(KEY, patchOperations());

        assertThat(result.successful()).isTrue();
        assertThat(result.revision()).isEqualTo(27);
        assertThat(delegate.patchCalls).isZero();
    }

    @Test
    void suppressesCreateWorkItemCommentAndDoesNotCallDelegateComment() {
        var delegate = new RecordingAdoClient();
        var client = new DryRunAdoClient(delegate);

        var result = client.createWorkItemComment(KEY, "SECRET_COMMENT_TEXT");

        assertThat(result.successful()).isTrue();
        assertThat(result.commentId()).isEqualTo("dry-run");
        assertThat(delegate.commentCalls).isZero();
    }

    @Test
    void dryRunPatchLogsOperationPathsWithoutRawFieldValues() {
        var delegate = new RecordingAdoClient();
        var client = new DryRunAdoClient(delegate);

        var logs = captureLogs(() -> client.patchWorkItem(KEY, patchOperations()));

        assertThat(logs)
                .contains("Dry-run would PATCH Work Item")
                .contains("suppressed ADO write")
                .contains("project=ProjectA")
                .contains("workItemId=123")
                .contains("revision=27")
                .contains("operationCount=3")
                .contains("/rev")
                .contains("/fields/System.State")
                .contains("/fields/Custom.ApprovedBySME")
                .doesNotContain("SECRET_FIELD_VALUE");
    }

    @Test
    void dryRunCommentLogsSuppressionWithoutFullCommentText() {
        var delegate = new RecordingAdoClient();
        var client = new DryRunAdoClient(delegate);

        var logs = captureLogs(() -> client.createWorkItemComment(KEY, "SECRET_COMMENT_TEXT"));

        assertThat(logs)
                .contains("Dry-run would create comment")
                .contains("suppressed ADO write")
                .contains("project=ProjectA")
                .contains("workItemId=123")
                .doesNotContain("SECRET_COMMENT_TEXT");
    }

    private List<PatchOperation> patchOperations() {
        return List.of(
                PatchOperation.testRevision(27),
                PatchOperation.replaceField("System.State", "SECRET_FIELD_VALUE"),
                PatchOperation.replaceField("Custom.ApprovedBySME", "SECRET_FIELD_VALUE")
        );
    }

    private String captureLogs(Runnable action) {
        var logger = (Logger) LoggerFactory.getLogger(DryRunAdoClient.class);
        var originalLevel = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        try {
            action.run();
            return appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
    }

    private static class RecordingAdoClient implements AdoClient {

        private final AdoWorkItem workItem = new AdoWorkItem(
                123,
                "ProjectA",
                "Test Case",
                27,
                "In Review",
                Map.of()
        );
        private final AdoWorkItemRevision revision = new AdoWorkItemRevision(
                123,
                26,
                null,
                Map.of(),
                java.util.Set.of()
        );

        private int fetchWorkItemCalls;
        private int fetchRevisionCalls;
        private int requestedRevision;
        private int patchCalls;
        private int commentCalls;

        @Override
        public AdoWorkItem fetchWorkItem(AdoWorkItemKey key) {
            fetchWorkItemCalls++;
            return workItem;
        }

        @Override
        public AdoWorkItemRevision fetchWorkItemRevision(AdoWorkItemKey key, int revision) {
            fetchRevisionCalls++;
            requestedRevision = revision;
            return this.revision;
        }

        @Override
        public AdoPatchResult patchWorkItem(AdoWorkItemKey key, List<PatchOperation> patchOperations) {
            patchCalls++;
            return AdoPatchResult.success(28);
        }

        @Override
        public AdoCommentResult createWorkItemComment(AdoWorkItemKey key, String commentText) {
            commentCalls++;
            return AdoCommentResult.success("real-comment");
        }
    }
}
