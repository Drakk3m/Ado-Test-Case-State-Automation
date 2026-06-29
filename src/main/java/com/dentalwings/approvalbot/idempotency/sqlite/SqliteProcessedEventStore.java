package com.dentalwings.approvalbot.idempotency.sqlite;

import java.util.Optional;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.dentalwings.approvalbot.domain.ProcessingResult;
import com.dentalwings.approvalbot.idempotency.ProcessedEventKey;
import com.dentalwings.approvalbot.idempotency.ProcessedEventRecord;
import com.dentalwings.approvalbot.idempotency.ProcessedEventStore;
import com.dentalwings.approvalbot.processing.WorkItemProcessingResult;

public class SqliteProcessedEventStore implements ProcessedEventStore
{

    public static final Duration DEFAULT_TTL = Duration.ofHours(24);
    public static final int DEFAULT_MAX_RECORDS = 10_000;

    private final String jdbcUrl;
    private final Duration ttl;
    private final int maxRecords;
    private final Clock clock;

    public SqliteProcessedEventStore(Path sqlitePath)
    {
        this(sqlitePath, DEFAULT_TTL, DEFAULT_MAX_RECORDS);
    }

    public SqliteProcessedEventStore(Path sqlitePath, Duration ttl, int maxRecords)
    {
        this(sqlitePath.toString(), ttl, maxRecords);
    }

    public SqliteProcessedEventStore(Path sqlitePath, Duration ttl, int maxRecords, Clock clock)
    {
        this(sqlitePath.toString(), ttl, maxRecords, clock);
    }

    public SqliteProcessedEventStore(String sqlitePathOrJdbcUrl, Duration ttl, int maxRecords)
    {
        this(sqlitePathOrJdbcUrl, ttl, maxRecords, Clock.systemUTC());
    }

    public SqliteProcessedEventStore(String sqlitePathOrJdbcUrl, Duration ttl, int maxRecords, Clock clock)
    {
        if (ttl.isNegative() || ttl.isZero())
        {
            throw new IllegalArgumentException("ttl must be positive");
        }
        if (maxRecords < 1)
        {
            throw new IllegalArgumentException("maxRecords must be positive");
        }
        this.jdbcUrl = jdbcUrl(sqlitePathOrJdbcUrl);
        this.ttl = ttl;
        this.maxRecords = maxRecords;
        this.clock = clock;
        initializeSchema();
    }

    @Override
    public boolean alreadyProcessed(ProcessedEventKey key)
    {
        try (var connection = connection(); var statement = connection.prepareStatement("""
                SELECT 1
                FROM processed_events
                WHERE project = ? AND work_item_id = ? AND revision = ?
                LIMIT 1
                """))
        {
            bindKey(statement, key);
            try (var resultSet = statement.executeQuery())
            {
                return resultSet.next();
            }
        }
        catch (SQLException exception)
        {
            throw persistenceFailure("Failed to check processed event", exception);
        }
    }

    @Override
    public Optional<ProcessedEventRecord> find(ProcessedEventKey key)
    {
        try (var connection = connection(); var statement = connection.prepareStatement("""
                SELECT project, work_item_id, revision, result, retry_count, next_retry_at,
                       received_at, completed_at, error_category, error_message
                FROM processed_events
                WHERE project = ? AND work_item_id = ? AND revision = ?
                """))
        {
            bindKey(statement, key);
            try (var resultSet = statement.executeQuery())
            {
                if (!resultSet.next())
                {
                    return Optional.empty();
                }
                return Optional.of(record(resultSet));
            }
        }
        catch (SQLException exception)
        {
            throw persistenceFailure("Failed to find processed event", exception);
        }
    }

    @Override
    public void markProcessed(ProcessedEventKey key, WorkItemProcessingResult result)
    {
        var now = Instant.now(clock);
        var processingResult = result.result();
        var record = new ProcessedEventRecord(key, processingResult, retryCount(processingResult),
                nextRetryAt(processingResult, now), now, completedAt(processingResult, now),
                errorCategory(processingResult), result.reason());

        try (var connection = connection(); var statement = connection.prepareStatement("""
                INSERT INTO processed_events (
                    project, work_item_id, revision, result, retry_count, next_retry_at,
                    received_at, completed_at, error_category, error_message
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(project, work_item_id, revision) DO UPDATE SET
                    result = excluded.result,
                    retry_count = excluded.retry_count,
                    next_retry_at = excluded.next_retry_at,
                    received_at = excluded.received_at,
                    completed_at = excluded.completed_at,
                    error_category = excluded.error_category,
                    error_message = excluded.error_message
                """))
        {
            bindRecord(statement, record);
            statement.executeUpdate();
        }
        catch (SQLException exception)
        {
            throw persistenceFailure("Failed to mark processed event", exception);
        }
    }

    @Override
    public void cleanupExpired()
    {
        var cutoff = Instant.now(clock).minus(ttl).toString();
        try (var connection = connection())
        {
            try (var deleteExpired = connection.prepareStatement("""
                    DELETE FROM processed_events
                    WHERE received_at < ?
                    """))
            {
                deleteExpired.setString(1, cutoff);
                deleteExpired.executeUpdate();
            }

            try (var trimOldest = connection.prepareStatement("""
                    DELETE FROM processed_events
                    WHERE rowid IN (
                        SELECT rowid
                        FROM processed_events
                        ORDER BY received_at ASC, project ASC, work_item_id ASC, revision ASC
                        LIMIT CASE
                            WHEN (SELECT COUNT(*) FROM processed_events) > ?
                            THEN (SELECT COUNT(*) FROM processed_events) - ?
                            ELSE 0
                        END
                    )
                    """))
            {
                trimOldest.setInt(1, maxRecords);
                trimOldest.setInt(2, maxRecords);
                trimOldest.executeUpdate();
            }
        }
        catch (SQLException exception)
        {
            throw persistenceFailure("Failed to cleanup processed events", exception);
        }
    }

    private void initializeSchema()
    {
        try (var connection = connection(); var statement = connection.createStatement())
        {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS processed_events (
                        project TEXT NOT NULL,
                        work_item_id INTEGER NOT NULL,
                        revision INTEGER NOT NULL,
                        result TEXT NOT NULL,
                        retry_count INTEGER NOT NULL,
                        next_retry_at TEXT NULL,
                        received_at TEXT NOT NULL,
                        completed_at TEXT NULL,
                        error_category TEXT NULL,
                        error_message TEXT NULL,
                        PRIMARY KEY (project, work_item_id, revision)
                    )
                    """);
        }
        catch (SQLException exception)
        {
            throw persistenceFailure("Failed to initialize processed events schema", exception);
        }
    }

    private Connection connection() throws SQLException
    {
        return DriverManager.getConnection(jdbcUrl);
    }

    private static String jdbcUrl(String sqlitePathOrJdbcUrl)
    {
        if (sqlitePathOrJdbcUrl == null || sqlitePathOrJdbcUrl.isBlank())
        {
            throw new IllegalArgumentException("sqlitePathOrJdbcUrl must not be blank");
        }
        if (sqlitePathOrJdbcUrl.startsWith("jdbc:"))
        {
            return sqlitePathOrJdbcUrl;
        }
        return "jdbc:sqlite:" + sqlitePathOrJdbcUrl;
    }

    private static void bindKey(PreparedStatement statement, ProcessedEventKey key) throws SQLException
    {
        statement.setString(1, key.project());
        statement.setLong(2, key.workItemId());
        statement.setInt(3, key.revision());
    }

    private static void bindRecord(PreparedStatement statement, ProcessedEventRecord record) throws SQLException
    {
        bindKey(statement, record.key());
        statement.setString(4, record.result().name());
        statement.setInt(5, record.retryCount());
        statement.setString(6, instantString(record.nextRetryAt()));
        statement.setString(7, record.receivedAt().toString());
        statement.setString(8, instantString(record.completedAt()));
        statement.setString(9, record.errorCategory());
        statement.setString(10, record.errorMessage());
    }

    private static ProcessedEventRecord record(ResultSet resultSet) throws SQLException
    {
        return new ProcessedEventRecord(
                new ProcessedEventKey(resultSet.getString("project"), resultSet.getLong("work_item_id"),
                        resultSet.getInt("revision")),
                ProcessingResult.valueOf(resultSet.getString("result")), resultSet.getInt("retry_count"),
                instant(resultSet.getString("next_retry_at")), Instant.parse(resultSet.getString("received_at")),
                instant(resultSet.getString("completed_at")), resultSet.getString("error_category"),
                resultSet.getString("error_message"));
    }

    private static String instantString(Instant instant)
    {
        return instant == null ? null : instant.toString();
    }

    private static Instant instant(String value)
    {
        return value == null ? null : Instant.parse(value);
    }

    private static int retryCount(ProcessingResult result)
    {
        return result == ProcessingResult.FAILED_RETRYABLE ? 1 : 0;
    }

    private static Instant nextRetryAt(ProcessingResult result, Instant now)
    {
        return result == ProcessingResult.FAILED_RETRYABLE ? now.plus(Duration.ofSeconds(30)) : null;
    }

    private static Instant completedAt(ProcessingResult result, Instant now)
    {
        return result == ProcessingResult.FAILED_RETRYABLE ? null : now;
    }

    private static String errorCategory(ProcessingResult result)
    {
        return switch (result)
        {
            case FAILED_RETRYABLE -> "retryable";
            case FAILED_NON_RETRYABLE -> "non_retryable";
            default -> null;
        };
    }

    private static SqliteProcessedEventStoreException persistenceFailure(String message, SQLException exception)
    {
        return new SqliteProcessedEventStoreException(message, exception);
    }

    public static class SqliteProcessedEventStoreException extends RuntimeException
    {

        public SqliteProcessedEventStoreException(String message, Throwable cause)
        {
            super(message, cause);
        }
    }
}
