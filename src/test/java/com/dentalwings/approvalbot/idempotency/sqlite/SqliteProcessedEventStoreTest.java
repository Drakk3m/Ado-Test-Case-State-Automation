package com.dentalwings.approvalbot.idempotency.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.dentalwings.approvalbot.domain.ProcessingResult;
import com.dentalwings.approvalbot.idempotency.ProcessedEventKey;
import com.dentalwings.approvalbot.idempotency.ProcessedEventStore;
import com.dentalwings.approvalbot.processing.WorkItemProcessingResult;

class SqliteProcessedEventStoreTest
{

    private static final ProcessedEventKey KEY = new ProcessedEventKey("ProjectA", 10, 3);

    @TempDir
    private Path tempDir;

    @Test
    void storeInitializesSchemaAutomatically() throws Exception
    {
        var database = databasePath();

        store(database);

        try (var connection = DriverManager.getConnection(jdbcUrl(database));
             var statement = connection.prepareStatement("""
                     SELECT name
                     FROM sqlite_master
                     WHERE type = 'table' AND name = 'processed_events'
                     """); var resultSet = statement.executeQuery())
        {
            assertThat(resultSet.next()).isTrue();
        }
    }

    @Test
    void markProcessedPersistsRecord()
    {
        var store = store(databasePath());

        store.markProcessed(KEY, completed());

        assertThat(store.find(KEY)).isPresent();
    }

    @Test
    void alreadyProcessedReturnsTrueAfterInsert()
    {
        var store = store(databasePath());

        store.markProcessed(KEY, completed());

        assertThat(store.alreadyProcessed(KEY)).isTrue();
    }

    @Test
    void findReturnsStoredResultStatus()
    {
        var store = store(databasePath());

        store.markProcessed(KEY, WorkItemProcessingResult.completedWithWarning("comment failed", null));

        assertThat(store.find(KEY)).hasValueSatisfying(
                record -> assertThat(record.result()).isEqualTo(ProcessingResult.COMPLETED_WITH_WARNING));
    }

    @Test
    void sameKeyInsertReplacesExistingRecord()
    {
        var store = store(databasePath());

        store.markProcessed(KEY, WorkItemProcessingResult.completed("done", null));
        store.markProcessed(KEY, WorkItemProcessingResult.failedNonRetryable("bad payload", null));

        assertThat(store.find(KEY)).hasValueSatisfying(record -> {
            assertThat(record.result()).isEqualTo(ProcessingResult.FAILED_NON_RETRYABLE);
            assertThat(record.maybeErrorCategory()).contains("non_retryable");
            assertThat(record.maybeErrorMessage()).contains("bad payload");
        });
    }

    @Test
    void differentRevisionIsStoredIndependently()
    {
        var store = store(databasePath());
        var otherRevision = new ProcessedEventKey("ProjectA", 10, 4);

        store.markProcessed(KEY, completed());
        store.markProcessed(otherRevision, WorkItemProcessingResult.completedWithWarning("warning", null));

        assertThat(store.find(KEY))
                .hasValueSatisfying(record -> assertThat(record.result()).isEqualTo(ProcessingResult.COMPLETED));
        assertThat(store.find(otherRevision)).hasValueSatisfying(
                record -> assertThat(record.result()).isEqualTo(ProcessingResult.COMPLETED_WITH_WARNING));
    }

    @Test
    void differentWorkItemIdIsStoredIndependently()
    {
        var store = store(databasePath());
        var otherWorkItem = new ProcessedEventKey("ProjectA", 11, 3);

        store.markProcessed(KEY, completed());
        store.markProcessed(otherWorkItem, WorkItemProcessingResult.failedNonRetryable("invalid", null));

        assertThat(store.find(KEY))
                .hasValueSatisfying(record -> assertThat(record.result()).isEqualTo(ProcessingResult.COMPLETED));
        assertThat(store.find(otherWorkItem)).hasValueSatisfying(
                record -> assertThat(record.result()).isEqualTo(ProcessingResult.FAILED_NON_RETRYABLE));
    }

    @Test
    void differentProjectIsStoredIndependently()
    {
        var store = store(databasePath());
        var otherProject = new ProcessedEventKey("ProjectB", 10, 3);

        store.markProcessed(KEY, completed());
        store.markProcessed(otherProject, WorkItemProcessingResult.completedWithWarning("warning", null));

        assertThat(store.find(KEY))
                .hasValueSatisfying(record -> assertThat(record.result()).isEqualTo(ProcessingResult.COMPLETED));
        assertThat(store.find(otherProject)).hasValueSatisfying(
                record -> assertThat(record.result()).isEqualTo(ProcessingResult.COMPLETED_WITH_WARNING));
    }

    @Test
    void rawErrorCategoryAndMessageArePreserved()
    {
        var store = store(databasePath());

        store.markProcessed(KEY, WorkItemProcessingResult.failedRetryable(" timeout contacting ado ", null));

        assertThat(store.find(KEY)).hasValueSatisfying(record -> {
            assertThat(record.maybeErrorCategory()).contains("retryable");
            assertThat(record.maybeErrorMessage()).contains(" timeout contacting ado ");
        });
    }

    @Test
    void retryCountIsPreservedForRetryableResult()
    {
        var clock = new MutableClock(Instant.parse("2026-06-23T00:00:00Z"));
        var store = new SqliteProcessedEventStore(databasePath(), Duration.ofHours(1), 10, clock);

        store.markProcessed(KEY, WorkItemProcessingResult.failedRetryable("timeout", null));

        assertThat(store.find(KEY)).hasValueSatisfying(record -> {
            assertThat(record.retryCount()).isEqualTo(1);
            assertThat(record.maybeNextRetryAt()).contains(Instant.parse("2026-06-23T00:00:30Z"));
            assertThat(record.maybeCompletedAt()).isEmpty();
        });
    }

    @Test
    void cleanupExpiredRemovesRecordsOlderThanTtl()
    {
        var clock = new MutableClock(Instant.parse("2026-06-23T00:00:00Z"));
        var store = new SqliteProcessedEventStore(databasePath(), Duration.ofMinutes(10), 10, clock);
        store.markProcessed(KEY, completed());

        clock.advance(Duration.ofMinutes(11));
        store.cleanupExpired();

        assertThat(store.alreadyProcessed(KEY)).isFalse();
    }

    @Test
    void cleanupExpiredKeepsRecordsWithinTtl()
    {
        var clock = new MutableClock(Instant.parse("2026-06-23T00:00:00Z"));
        var store = new SqliteProcessedEventStore(databasePath(), Duration.ofMinutes(10), 10, clock);
        store.markProcessed(KEY, completed());

        clock.advance(Duration.ofMinutes(9));
        store.cleanupExpired();

        assertThat(store.alreadyProcessed(KEY)).isTrue();
    }

    @Test
    void cleanupExpiredTrimsOldestRecordsWhenMaxRecordsIsExceeded()
    {
        var clock = new MutableClock(Instant.parse("2026-06-23T00:00:00Z"));
        var store = new SqliteProcessedEventStore(databasePath(), Duration.ofHours(1), 2, clock);
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
    void dataPersistsAfterRecreatingStoreForSameFile()
    {
        var database = databasePath();
        var firstStore = store(database);
        firstStore.markProcessed(KEY, WorkItemProcessingResult.failedNonRetryable("invalid", null));

        var secondStore = store(database);

        assertThat(secondStore.find(KEY)).hasValueSatisfying(record -> {
            assertThat(record.result()).isEqualTo(ProcessingResult.FAILED_NON_RETRYABLE);
            assertThat(record.maybeErrorMessage()).contains("invalid");
        });
    }

    @Test
    void sqliteImplementationSatisfiesProcessedEventStoreContract()
    {
        ProcessedEventStore store = store(databasePath());

        store.markProcessed(KEY, completed());

        assertThat(store.alreadyProcessed(KEY)).isTrue();
        assertThat(store.find(KEY)).isPresent();
    }

    @Test
    void sqliteImplementationDoesNotDependOnSpringDataJpaOrHibernate()
    {
        assertNoForbiddenTypeReferences("org.springframework.data", SqliteProcessedEventStore.class);
        assertNoForbiddenTypeReferences("Jpa", SqliteProcessedEventStore.class);
        assertNoForbiddenTypeReferences("Hibernate", SqliteProcessedEventStore.class);
    }

    private SqliteProcessedEventStore store(Path database)
    {
        return new SqliteProcessedEventStore(database, Duration.ofHours(1), 100);
    }

    private Path databasePath()
    {
        return tempDir.resolve("processed-events.sqlite");
    }

    private String jdbcUrl(Path database)
    {
        return "jdbc:sqlite:" + database;
    }

    private WorkItemProcessingResult completed()
    {
        return WorkItemProcessingResult.completed("done", null);
    }

    private void assertNoForbiddenTypeReferences(String forbiddenText, Class<?>... classes)
    {
        for (Class<?> type : classes)
        {
            assertThat(type.getName().toLowerCase()).doesNotContain(forbiddenText.toLowerCase());
            for (Method method : type.getDeclaredMethods())
            {
                assertThat(method.toGenericString().toLowerCase()).doesNotContain(forbiddenText.toLowerCase());
            }
            for (var constructor : type.getDeclaredConstructors())
            {
                assertThat(constructor.toGenericString().toLowerCase()).doesNotContain(forbiddenText.toLowerCase());
            }
            for (var field : type.getDeclaredFields())
            {
                assertThat(field.toGenericString().toLowerCase()).doesNotContain(forbiddenText.toLowerCase());
            }
        }
    }

    private static class MutableClock extends Clock
    {

        private Instant instant;

        private MutableClock(Instant instant)
        {
            this.instant = instant;
        }

        private void advance(Duration duration)
        {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone()
        {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone)
        {
            return this;
        }

        @Override
        public Instant instant()
        {
            return instant;
        }
    }
}
