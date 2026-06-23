package com.dentalwings.approvalbot.processing.pipeline;

import com.dentalwings.approvalbot.ado.AdoWorkItemKey;
import com.dentalwings.approvalbot.config.ProjectApprovalConfig;
import com.dentalwings.approvalbot.domain.ProcessingResult;
import com.dentalwings.approvalbot.idempotency.IdempotentWorkItemProcessor;
import com.dentalwings.approvalbot.idempotency.InMemoryProcessedEventStore;
import com.dentalwings.approvalbot.processing.ProcessWorkItemCommand;
import com.dentalwings.approvalbot.processing.WorkItemProcessingResult;
import com.dentalwings.approvalbot.processing.WorkItemProcessingService;
import com.dentalwings.approvalbot.queue.InMemoryWorkItemQueue;
import com.dentalwings.approvalbot.queue.QueuedWorkItemEvent;
import com.dentalwings.approvalbot.queue.QueuedWorkItemProcessor;
import com.dentalwings.approvalbot.webhook.AdoWebhookEvent;
import com.dentalwings.approvalbot.webhook.EventClassifier;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookEventProcessingPipelineTest {

    private static final Instant NOW = Instant.parse("2026-06-23T00:00:00Z");

    @Test
    void processableEventIsSentThroughQueueProcessor() {
        var queueProcessor = new RecordingQueueProcessor(completed());
        var pipeline = pipeline(queueProcessor);

        pipeline.process(validEvent(), config(true, "Test Case"));

        assertThat(queueProcessor.calls).isOne();
        assertThat(queueProcessor.lastEvent).isNotNull();
        assertThat(queueProcessor.lastEvent.revision()).isEqualTo(27);
    }

    @Test
    void processableEventReachesIdempotentProcessingPath() {
        var store = new InMemoryProcessedEventStore(Duration.ofHours(1), 10);
        var delegate = new StubProcessingService(completed());
        var idempotentProcessor = new IdempotentWorkItemProcessor(store, delegate);
        var queueProcessor = new QueuedWorkItemProcessor(new InMemoryWorkItemQueue(), idempotentProcessor);
        var pipeline = pipeline(queueProcessor);

        pipeline.process(validEvent(), config(true, "Test Case"));

        assertThat(delegate.calls).isOne();
        assertThat(store.alreadyProcessed(new com.dentalwings.approvalbot.idempotency.ProcessedEventKey("ProjectA", 123, 27)))
                .isTrue();
    }

    @Test
    void disabledProjectEventIsSkippedAndNotQueued() {
        var queueProcessor = new RecordingQueueProcessor(completed());
        var result = pipeline(queueProcessor).process(validEvent(), config(false, "Test Case"));

        assertThat(result.status()).isEqualTo(WebhookProcessingStatus.SKIPPED);
        assertThat(queueProcessor.calls).isZero();
    }

    @Test
    void unsupportedWorkItemTypeEventIsSkippedAndNotQueued() {
        var queueProcessor = new RecordingQueueProcessor(completed());
        var event = event("ProjectA", 123L, "Bug", 27, "Human User", "user@example.com");

        var result = pipeline(queueProcessor).process(event, config(true, "Test Case"));

        assertThat(result.status()).isEqualTo(WebhookProcessingStatus.SKIPPED);
        assertThat(queueProcessor.calls).isZero();
    }

    @Test
    void botGeneratedEventIsSkippedAndNotQueued() {
        var queueProcessor = new RecordingQueueProcessor(completed());
        var event = event("ProjectA", 123L, "Test Case", 27, "Any Name", "bot@example.com");

        var result = pipeline(queueProcessor).process(event, config(true, "Test Case"));

        assertThat(result.status()).isEqualTo(WebhookProcessingStatus.SKIPPED);
        assertThat(queueProcessor.calls).isZero();
    }

    @Test
    void malformedEventIsFailedAndNotQueued() {
        var queueProcessor = new RecordingQueueProcessor(completed());
        var event = event(" ", 123L, "Test Case", 27, "Human User", "user@example.com");

        var result = pipeline(queueProcessor).process(event, config(true, "Test Case"));

        assertThat(result.status()).isEqualTo(WebhookProcessingStatus.FAILED_MALFORMED_EVENT);
        assertThat(queueProcessor.calls).isZero();
    }

    @Test
    void missingChangedByEmailDoesNotPreventProcessableEvent() {
        var queueProcessor = new RecordingQueueProcessor(completed());
        var event = event("ProjectA", 123L, "Test Case", 27, "Human User", null);

        var result = pipeline(queueProcessor).process(event, config(true, "Test Case"));

        assertThat(result.status()).isEqualTo(WebhookProcessingStatus.COMPLETED);
        assertThat(queueProcessor.calls).isOne();
    }

    @Test
    void displayNameMatchingBotWithoutEmailDoesNotSkipAsBot() {
        var queueProcessor = new RecordingQueueProcessor(completed());
        var event = event("ProjectA", 123L, "Test Case", 27, "Approval Bot", null);

        var result = pipeline(queueProcessor).process(event, config(true, "Test Case"));

        assertThat(result.status()).isEqualTo(WebhookProcessingStatus.COMPLETED);
        assertThat(queueProcessor.calls).isOne();
    }

    @Test
    void pipelineResultPreservesCompletedStatusFromProcessing() {
        var result = pipeline(new RecordingQueueProcessor(completed())).process(validEvent(), config(true, "Test Case"));

        assertThat(result.status()).isEqualTo(WebhookProcessingStatus.COMPLETED);
        assertThat(result.maybeWorkItemProcessingResult()).hasValueSatisfying(workItemResult ->
                assertThat(workItemResult.result()).isEqualTo(ProcessingResult.COMPLETED));
    }

    @Test
    void pipelineResultPreservesCompletedWithWarningStatusFromProcessing() {
        var result = pipeline(new RecordingQueueProcessor(WorkItemProcessingResult.completedWithWarning("comment failed", null)))
                .process(validEvent(), config(true, "Test Case"));

        assertThat(result.status()).isEqualTo(WebhookProcessingStatus.COMPLETED_WITH_WARNING);
    }

    @Test
    void pipelineResultPreservesRetryableFailureFromProcessing() {
        var result = pipeline(new RecordingQueueProcessor(WorkItemProcessingResult.failedRetryable("timeout", null)))
                .process(validEvent(), config(true, "Test Case"));

        assertThat(result.status()).isEqualTo(WebhookProcessingStatus.FAILED_RETRYABLE);
    }

    @Test
    void pipelineResultPreservesNonRetryableFailureFromProcessing() {
        var result = pipeline(new RecordingQueueProcessor(WorkItemProcessingResult.failedNonRetryable("bad payload", null)))
                .process(validEvent(), config(true, "Test Case"));

        assertThat(result.status()).isEqualTo(WebhookProcessingStatus.FAILED_NON_RETRYABLE);
    }

    @Test
    void duplicateEventBehaviorIsDelegatedToIdempotentProcessor() {
        var store = new InMemoryProcessedEventStore(Duration.ofHours(1), 10);
        var delegate = new StubProcessingService(completed());
        var idempotentProcessor = new IdempotentWorkItemProcessor(store, delegate);
        var queueProcessor = new QueuedWorkItemProcessor(new InMemoryWorkItemQueue(), idempotentProcessor);
        var pipeline = pipeline(queueProcessor);

        var first = pipeline.process(validEvent(), config(true, "Test Case"));
        var duplicate = pipeline.process(validEvent(), config(true, "Test Case"));

        assertThat(first.status()).isEqualTo(WebhookProcessingStatus.COMPLETED);
        assertThat(duplicate.status()).isEqualTo(WebhookProcessingStatus.SKIPPED);
        assertThat(delegate.calls).isOne();
    }

    @Test
    void pipelineDoesNotCallAdoClientDirectly() {
        assertNoForbiddenTypeReferences("AdoClient", WebhookEventProcessingPipeline.class);
    }

    @Test
    void pipelineDoesNotDependOnSpringMvcOrControllerClasses() {
        assertNoForbiddenTypeReferences("org.springframework.web", WebhookEventProcessingPipeline.class);
        assertNoForbiddenTypeReferences("Controller", WebhookEventProcessingPipeline.class);
    }

    @Test
    void pipelineDoesNotDependOnWebClientRestTemplateOrResponseEntity() {
        assertNoForbiddenTypeReferences("WebClient", WebhookEventProcessingPipeline.class);
        assertNoForbiddenTypeReferences("RestTemplate", WebhookEventProcessingPipeline.class);
        assertNoForbiddenTypeReferences("ResponseEntity", WebhookEventProcessingPipeline.class);
    }

    @Test
    void pipelineDoesNotParseJson() {
        assertNoForbiddenTypeReferences("ObjectMapper", WebhookEventProcessingPipeline.class);
        assertNoForbiddenTypeReferences("Json", WebhookEventProcessingPipeline.class);
    }

    private WebhookEventProcessingPipeline pipeline(QueuedWorkItemProcessor queueProcessor) {
        return new WebhookEventProcessingPipeline(new EventClassifier(), queueProcessor, new FixedClock(NOW));
    }

    private AdoWebhookEvent validEvent() {
        return event("ProjectA", 123L, "Test Case", 27, "Human User", "user@example.com");
    }

    private AdoWebhookEvent event(
            String project,
            Long workItemId,
            String workItemType,
            Integer revision,
            String displayName,
            String email
    ) {
        return AdoWebhookEvent.workItemUpdated("org", project, workItemId, workItemType, revision, displayName, email, Set.of());
    }

    private ProjectApprovalConfig config(boolean enabled, String... supportedWorkItemTypes) {
        return new ProjectApprovalConfig(
                "ProjectA",
                enabled,
                Set.of(supportedWorkItemTypes),
                "Custom.ApprovedBySME",
                "Custom.ApprovedBySQA",
                Set.of("System.Title"),
                Set.of("sme@example.com"),
                Set.of("sqa@example.com"),
                "bot@example.com"
        );
    }

    private WorkItemProcessingResult completed() {
        return WorkItemProcessingResult.completed("done", null);
    }

    private void assertNoForbiddenTypeReferences(String forbiddenText, Class<?>... classes) {
        for (Class<?> type : classes) {
            assertThat(type.getName()).doesNotContain(forbiddenText);
            for (Method method : type.getDeclaredMethods()) {
                assertThat(method.toGenericString()).doesNotContain(forbiddenText);
            }
            for (var constructor : type.getDeclaredConstructors()) {
                assertThat(constructor.toGenericString()).doesNotContain(forbiddenText);
            }
            for (var field : type.getDeclaredFields()) {
                assertThat(field.toGenericString()).doesNotContain(forbiddenText);
            }
        }
    }

    private static class RecordingQueueProcessor extends QueuedWorkItemProcessor {

        private final WorkItemProcessingResult result;
        private int calls;
        private QueuedWorkItemEvent lastEvent;

        private RecordingQueueProcessor(WorkItemProcessingResult result) {
            super(new InMemoryWorkItemQueue(), command -> result);
            this.result = result;
        }

        @Override
        public WorkItemProcessingResult process(QueuedWorkItemEvent event) {
            calls++;
            lastEvent = event;
            return result;
        }
    }

    private static class StubProcessingService extends WorkItemProcessingService {

        private final WorkItemProcessingResult result;
        private int calls;

        private StubProcessingService(WorkItemProcessingResult result) {
            super(null, null, null, null);
            this.result = result;
        }

        @Override
        public WorkItemProcessingResult process(ProcessWorkItemCommand command) {
            calls++;
            return result;
        }
    }

    private static class FixedClock extends Clock {

        private final Instant instant;

        private FixedClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
