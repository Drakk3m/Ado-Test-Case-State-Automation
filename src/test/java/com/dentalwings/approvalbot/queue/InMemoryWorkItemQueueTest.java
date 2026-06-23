package com.dentalwings.approvalbot.queue;

import com.dentalwings.approvalbot.ado.AdoWorkItemKey;
import com.dentalwings.approvalbot.config.ProjectApprovalConfig;
import com.dentalwings.approvalbot.domain.ProcessingResult;
import com.dentalwings.approvalbot.processing.ProcessWorkItemCommand;
import com.dentalwings.approvalbot.processing.WorkItemProcessingResult;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryWorkItemQueueTest {

    @Test
    void sameProjectAndWorkItemIdEventsDoNotRunConcurrently() throws Exception {
        var queue = new InMemoryWorkItemQueue();
        var active = new AtomicInteger();
        var maxActive = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(() -> queue.process(event("ProjectA", 10, 1), event -> {
                trackActive(active, maxActive);
                sleep(DurationMillis.SHORT);
                active.decrementAndGet();
                return completed();
            }));
            var second = executor.submit(() -> queue.process(event(" ProjectA ", 10, 2), event -> {
                trackActive(active, maxActive);
                sleep(DurationMillis.SHORT);
                active.decrementAndGet();
                return completed();
            }));

            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(maxActive).hasValue(1);
    }

    @Test
    void sameProjectAndWorkItemIdEventsAreProcessedByRevisionWhenQueuedOutOfOrder() {
        var queue = new InMemoryWorkItemQueue();
        var key = new WorkItemQueueKey("ProjectA", 10);
        var processedRevisions = new ArrayList<Integer>();

        queue.enqueue(event("ProjectA", 10, 3));
        queue.enqueue(event("ProjectA", 10, 1));
        queue.enqueue(event("ProjectA", 10, 2));

        queue.drainNext(key, event -> recordRevision(event, processedRevisions));
        queue.drainNext(key, event -> recordRevision(event, processedRevisions));
        queue.drainNext(key, event -> recordRevision(event, processedRevisions));

        assertThat(processedRevisions).containsExactly(1, 2, 3);
    }

    @Test
    void differentWorkItemIdsMayProcessIndependently() throws Exception {
        var queue = new InMemoryWorkItemQueue();
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(() -> queue.process(event("ProjectA", 10, 1), event -> waitTogether(entered, release)));
            var second = executor.submit(() -> queue.process(event("ProjectA", 11, 1), event -> waitTogether(entered, release)));

            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void differentProjectsWithSameWorkItemIdUseDifferentQueueKeys() throws Exception {
        var queue = new InMemoryWorkItemQueue();
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(() -> queue.process(event("ProjectA", 10, 1), event -> waitTogether(entered, release)));
            var second = executor.submit(() -> queue.process(event("ProjectB", 10, 1), event -> waitTogether(entered, release)));

            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void queueKeyEqualityNormalizesProjectConsistently() {
        assertThat(new WorkItemQueueKey(" ProjectA ", 10))
                .isEqualTo(new WorkItemQueueKey("projecta", 10));
    }

    @Test
    void revisionIsNotPartOfWorkItemQueueKeyEquality() {
        assertThat(WorkItemQueueKey.from(command("ProjectA", 10, 1)))
                .isEqualTo(WorkItemQueueKey.from(command("ProjectA", 10, 2)));
    }

    @Test
    void queuedEventPreservesRevision() {
        var event = QueuedWorkItemEvent.from(command("ProjectA", 10, 27), Instant.parse("2026-06-23T00:00:00Z"));

        assertThat(event.revision()).isEqualTo(27);
    }

    @Test
    void queuedEventPreservesProcessWorkItemCommandPayload() {
        var command = command("ProjectA", 10, 27);
        var event = QueuedWorkItemEvent.from(command, Instant.parse("2026-06-23T00:00:00Z"));

        assertThat(event.command()).isSameAs(command);
    }

    @Test
    void queueDoesNotDropEventsSilently() {
        var queue = new InMemoryWorkItemQueue();
        var key = new WorkItemQueueKey("ProjectA", 10);
        var processedRevisions = new ArrayList<Integer>();

        queue.enqueue(event("ProjectA", 10, 1));
        queue.enqueue(event("ProjectA", 10, 2));
        queue.enqueue(event("ProjectA", 10, 3));

        assertThat(queue.pendingCount(key)).isEqualTo(3);
        assertThat(queue.drainNext(key, event -> recordRevision(event, processedRevisions))).isPresent();
        assertThat(queue.drainNext(key, event -> recordRevision(event, processedRevisions))).isPresent();
        assertThat(queue.drainNext(key, event -> recordRevision(event, processedRevisions))).isPresent();
        assertThat(queue.drainNext(key, event -> recordRevision(event, processedRevisions))).isEmpty();
        assertThat(processedRevisions).containsExactly(1, 2, 3);
    }

    @Test
    void queueDoesNotMarkIdempotencyItself() {
        assertNoForbiddenTypeReferences(
                "ProcessedEventStore",
                WorkItemQueue.class,
                WorkItemQueueKey.class,
                QueuedWorkItemEvent.class,
                InMemoryWorkItemQueue.class
        );
        assertNoForbiddenTypeReferences("markProcessed", InMemoryWorkItemQueue.class);
    }

    @Test
    void queueImplementationDoesNotDependOnSpringMvcOrControllerClasses() {
        assertNoForbiddenTypeReferences("org.springframework.web", InMemoryWorkItemQueue.class, WorkItemQueue.class);
        assertNoForbiddenTypeReferences("Controller", InMemoryWorkItemQueue.class, WorkItemQueue.class);
    }

    @Test
    void queueImplementationDoesNotDependOnWebClientRestTemplateOrResponseEntity() {
        assertNoForbiddenTypeReferences("WebClient", InMemoryWorkItemQueue.class, WorkItemQueue.class);
        assertNoForbiddenTypeReferences("RestTemplate", InMemoryWorkItemQueue.class, WorkItemQueue.class);
        assertNoForbiddenTypeReferences("ResponseEntity", InMemoryWorkItemQueue.class, WorkItemQueue.class);
    }

    @Test
    void queueImplementationDoesNotDependOnSqliteOrDatabaseClasses() {
        assertNoForbiddenTypeReferences("sqlite", InMemoryWorkItemQueue.class, WorkItemQueue.class);
        assertNoForbiddenTypeReferences("jdbc", InMemoryWorkItemQueue.class, WorkItemQueue.class);
        assertNoForbiddenTypeReferences("DriverManager", InMemoryWorkItemQueue.class, WorkItemQueue.class);
    }

    @Test
    void queuedWorkItemProcessorDelegatesExactlyOncePerEvent() {
        var calls = new AtomicInteger();
        var processor = new QueuedWorkItemProcessor(new InMemoryWorkItemQueue(), command -> {
            calls.incrementAndGet();
            return WorkItemProcessingResult.completed("done", null);
        });

        var result = processor.process(event("ProjectA", 10, 1));

        assertThat(calls).hasValue(1);
        assertThat(result.result()).isEqualTo(ProcessingResult.COMPLETED);
    }

    @Test
    void queuedWorkItemProcessorSerializesSameWorkItemProcessing() throws Exception {
        var active = new AtomicInteger();
        var maxActive = new AtomicInteger();
        var processor = new QueuedWorkItemProcessor(new InMemoryWorkItemQueue(), command -> {
            trackActive(active, maxActive);
            sleep(DurationMillis.SHORT);
            active.decrementAndGet();
            return completed();
        });
        var executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(() -> processor.process(event("ProjectA", 10, 1)));
            var second = executor.submit(() -> processor.process(event("projecta", 10, 2)));

            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(maxActive).hasValue(1);
    }

    @Test
    void queuedWorkItemProcessorAllowsIndependentWorkItemProcessing() throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var processor = new QueuedWorkItemProcessor(new InMemoryWorkItemQueue(), command -> waitTogether(entered, release));
        var executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(() -> processor.process(event("ProjectA", 10, 1)));
            var second = executor.submit(() -> processor.process(event("ProjectA", 11, 1)));

            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private QueuedWorkItemEvent event(String project, long workItemId, int revision) {
        return QueuedWorkItemEvent.from(command(project, workItemId, revision), Instant.parse("2026-06-23T00:00:00Z"));
    }

    private ProcessWorkItemCommand command(String project, long workItemId, int revision) {
        return new ProcessWorkItemCommand(
                new AdoWorkItemKey("org", project, workItemId),
                revision,
                new ProjectApprovalConfig(
                        project,
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

    private WorkItemProcessingResult recordRevision(QueuedWorkItemEvent event, ArrayList<Integer> processedRevisions) {
        processedRevisions.add(event.revision());
        return completed();
    }

    private WorkItemProcessingResult waitTogether(CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        try {
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
        return completed();
    }

    private WorkItemProcessingResult completed() {
        return WorkItemProcessingResult.completed("done", null);
    }

    private void trackActive(AtomicInteger active, AtomicInteger maxActive) {
        var current = active.incrementAndGet();
        maxActive.accumulateAndGet(current, Math::max);
    }

    private void sleep(DurationMillis duration) {
        try {
            TimeUnit.MILLISECONDS.sleep(duration.millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
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

    private enum DurationMillis {
        SHORT(100);

        private final long millis;

        DurationMillis(long millis) {
            this.millis = millis;
        }
    }
}
