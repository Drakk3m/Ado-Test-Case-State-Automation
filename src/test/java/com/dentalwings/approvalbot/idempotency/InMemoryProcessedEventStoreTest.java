package com.dentalwings.approvalbot.idempotency;

import com.dentalwings.approvalbot.ado.AdoWorkItemKey;
import com.dentalwings.approvalbot.config.ProjectApprovalConfig;
import com.dentalwings.approvalbot.domain.ProcessingResult;
import com.dentalwings.approvalbot.processing.ProcessWorkItemCommand;
import com.dentalwings.approvalbot.processing.WorkItemProcessingResult;
import com.dentalwings.approvalbot.processing.WorkItemProcessingService;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryProcessedEventStoreTest {

    private static final ProcessedEventKey KEY = new ProcessedEventKey("ProjectA", 10, 3);

    @Test
    void sameProjectWorkItemIdAndRevisionIsAlreadyProcessedAfterMarkProcessed() {
        var store = store();

        store.markProcessed(KEY, completed());

        assertThat(store.alreadyProcessed(new ProcessedEventKey("ProjectA", 10, 3))).isTrue();
    }

    @Test
    void differentRevisionIsNotConsideredAlreadyProcessed() {
        var store = store();

        store.markProcessed(KEY, completed());

        assertThat(store.alreadyProcessed(new ProcessedEventKey("ProjectA", 10, 4))).isFalse();
    }

    @Test
    void differentWorkItemIdIsNotConsideredAlreadyProcessed() {
        var store = store();

        store.markProcessed(KEY, completed());

        assertThat(store.alreadyProcessed(new ProcessedEventKey("ProjectA", 11, 3))).isFalse();
    }

    @Test
    void differentProjectIsNotConsideredAlreadyProcessed() {
        var store = store();

        store.markProcessed(KEY, completed());

        assertThat(store.alreadyProcessed(new ProcessedEventKey("ProjectB", 10, 3))).isFalse();
    }

    @Test
    void keyEqualityUsesNormalizedProjectConsistently() {
        assertThat(new ProcessedEventKey(" ProjectA ", 10, 3))
                .isEqualTo(new ProcessedEventKey("projecta", 10, 3));
    }

    @Test
    void storedRecordPreservesProcessingResultStatus() {
        var store = store();

        store.markProcessed(KEY, WorkItemProcessingResult.completedWithWarning("comment failed", null));

        assertThat(store.find(KEY)).hasValueSatisfying(record ->
                assertThat(record.result()).isEqualTo(ProcessingResult.COMPLETED_WITH_WARNING));
    }

    @Test
    void storedRecordPreservesRetryMetadata() {
        var clock = new MutableClock(Instant.parse("2026-06-23T00:00:00Z"));
        var store = new InMemoryProcessedEventStore(Duration.ofHours(1), 10, clock);

        store.markProcessed(KEY, WorkItemProcessingResult.failedRetryable("timeout", null));

        assertThat(store.find(KEY)).hasValueSatisfying(record -> {
            assertThat(record.retryCount()).isEqualTo(1);
            assertThat(record.maybeNextRetryAt()).contains(Instant.parse("2026-06-23T00:00:30Z"));
            assertThat(record.maybeCompletedAt()).isEmpty();
            assertThat(record.maybeErrorCategory()).contains("retryable");
            assertThat(record.maybeErrorMessage()).contains("timeout");
        });
    }

    @Test
    void cleanupExpiredRemovesRecordsOlderThanTtl() {
        var clock = new MutableClock(Instant.parse("2026-06-23T00:00:00Z"));
        var store = new InMemoryProcessedEventStore(Duration.ofMinutes(10), 10, clock);
        store.markProcessed(KEY, completed());

        clock.advance(Duration.ofMinutes(11));
        store.cleanupExpired();

        assertThat(store.alreadyProcessed(KEY)).isFalse();
    }

    @Test
    void cleanupExpiredKeepsRecordsWithinTtl() {
        var clock = new MutableClock(Instant.parse("2026-06-23T00:00:00Z"));
        var store = new InMemoryProcessedEventStore(Duration.ofMinutes(10), 10, clock);
        store.markProcessed(KEY, completed());

        clock.advance(Duration.ofMinutes(9));
        store.cleanupExpired();

        assertThat(store.alreadyProcessed(KEY)).isTrue();
    }

    @Test
    void cleanupExpiredTrimsOldestRecordsWhenMaxRecordsIsExceeded() {
        var clock = new MutableClock(Instant.parse("2026-06-23T00:00:00Z"));
        var store = new InMemoryProcessedEventStore(Duration.ofHours(1), 2, clock);
        var first = new ProcessedEventKey("ProjectA", 10, 1);
        var second = new ProcessedEventKey("ProjectA", 10, 2);
        var third = new ProcessedEventKey("ProjectA", 10, 3);

        store.markProcessed(first, completed());
        clock.advance(Duration.ofSeconds(1));
        store.markProcessed(second, completed());
        clock.advance(Duration.ofSeconds(1));
        store.markProcessed(third, completed());
        store.cleanupExpired();

        assertThat(store.alreadyProcessed(first)).isFalse();
        assertThat(store.alreadyProcessed(second)).isTrue();
        assertThat(store.alreadyProcessed(third)).isTrue();
    }

    @Test
    void inMemoryStoreIsIndependentFromSqliteDatabaseClasses() {
        assertNoForbiddenTypeReferences("sqlite", InMemoryProcessedEventStore.class, ProcessedEventStore.class);
        assertNoForbiddenTypeReferences("jdbc", InMemoryProcessedEventStore.class, ProcessedEventStore.class);
    }

    @Test
    void inMemoryStoreIsIndependentFromSpringData() {
        assertNoForbiddenTypeReferences("org.springframework.data", InMemoryProcessedEventStore.class, ProcessedEventStore.class);
        assertNoForbiddenTypeReferences("Repository", InMemoryProcessedEventStore.class, ProcessedEventStore.class);
    }

    @Test
    void duplicateEventDoesNotCallDelegate() {
        var store = store();
        var delegate = new StubProcessingService(WorkItemProcessingResult.completed("done", null));
        var processor = new IdempotentWorkItemProcessor(store, delegate);
        var command = command();

        processor.process(command);
        var duplicateResult = processor.process(command);

        assertThat(delegate.calls).isOne();
        assertThat(duplicateResult.result()).isEqualTo(ProcessingResult.SKIPPED);
    }

    @Test
    void firstEventCallsDelegateAndMarksProcessed() {
        var store = store();
        var delegate = new StubProcessingService(WorkItemProcessingResult.completed("done", null));
        var processor = new IdempotentWorkItemProcessor(store, delegate);
        var command = command();

        var result = processor.process(command);

        assertThat(delegate.calls).isOne();
        assertThat(result.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(store.alreadyProcessed(ProcessedEventKey.from(command))).isTrue();
    }

    @Test
    void retryableFailureIsNotMarkedAsProcessed() {
        var store = store();
        var delegate = new StubProcessingService(WorkItemProcessingResult.failedRetryable("timeout", null));
        var processor = new IdempotentWorkItemProcessor(store, delegate);
        var command = command();

        var result = processor.process(command);

        assertThat(result.result()).isEqualTo(ProcessingResult.FAILED_RETRYABLE);
        assertThat(store.alreadyProcessed(ProcessedEventKey.from(command))).isFalse();
    }

    private InMemoryProcessedEventStore store() {
        return new InMemoryProcessedEventStore(Duration.ofHours(1), 100);
    }

    private WorkItemProcessingResult completed() {
        return WorkItemProcessingResult.completed("done", null);
    }

    private ProcessWorkItemCommand command() {
        return new ProcessWorkItemCommand(
                new AdoWorkItemKey("org", "ProjectA", 10),
                3,
                new ProjectApprovalConfig(
                        "ProjectA",
                        true,
                        Set.of("Test Case"),
                        "Custom.ApprovedBySME",
                        "Custom.ApprovedBySQA",
                        Set.of("System.Title"),
                        Set.of("sme@example.com"),
                        Set.of("sqa@example.com"),
                        "bot@example.com"
                )
        );
    }

    private void assertNoForbiddenTypeReferences(String forbiddenText, Class<?>... classes) {
        for (Class<?> type : classes) {
            assertThat(type.getName().toLowerCase()).doesNotContain(forbiddenText.toLowerCase());
            for (Method method : type.getDeclaredMethods()) {
                assertThat(method.toGenericString().toLowerCase()).doesNotContain(forbiddenText.toLowerCase());
            }
            for (var constructor : type.getDeclaredConstructors()) {
                assertThat(constructor.toGenericString().toLowerCase()).doesNotContain(forbiddenText.toLowerCase());
            }
            for (var field : type.getDeclaredFields()) {
                assertThat(field.toGenericString().toLowerCase()).doesNotContain(forbiddenText.toLowerCase());
            }
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

    private static class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
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
