package com.dentalwings.approvalbot.config.spring;

import com.dentalwings.approvalbot.idempotency.InMemoryProcessedEventStore;
import com.dentalwings.approvalbot.idempotency.ProcessedEventStore;
import com.dentalwings.approvalbot.idempotency.sqlite.SqliteProcessedEventStore;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdempotencyBeanConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ProcessedEventStore processedEventStore(ApprovalBotProperties properties) {
        var idempotency = properties.getIdempotency();
        var ttl = Duration.ofHours(idempotency.getTtlHours());
        var type = normalize(idempotency.getType());
        if ("in-memory".equals(type) || "memory".equals(type)) {
            return new InMemoryProcessedEventStore(ttl, idempotency.getMaxRecords());
        }
        if ("sqlite".equals(type)) {
            return new SqliteProcessedEventStore(Path.of(idempotency.getSqlitePath()), ttl, idempotency.getMaxRecords());
        }
        throw new IllegalArgumentException("Unsupported idempotency.type: " + idempotency.getType());
    }

    private String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }
}
