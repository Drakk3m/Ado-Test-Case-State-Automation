package com.dentalwings.approvalbot.processing.pipeline;

import com.dentalwings.approvalbot.config.ProjectApprovalConfig;
import com.dentalwings.approvalbot.processing.ProcessWorkItemCommand;
import com.dentalwings.approvalbot.queue.QueuedWorkItemEvent;
import com.dentalwings.approvalbot.queue.QueuedWorkItemProcessor;
import com.dentalwings.approvalbot.webhook.AdoWebhookEvent;
import com.dentalwings.approvalbot.webhook.EventClassificationStatus;
import com.dentalwings.approvalbot.webhook.EventClassifier;
import java.time.Clock;
import java.time.Instant;

public class WebhookEventProcessingPipeline {

    private final EventClassifier eventClassifier;
    private final QueuedWorkItemProcessor queuedWorkItemProcessor;
    private final Clock clock;

    public WebhookEventProcessingPipeline(
            EventClassifier eventClassifier,
            QueuedWorkItemProcessor queuedWorkItemProcessor
    ) {
        this(eventClassifier, queuedWorkItemProcessor, Clock.systemUTC());
    }

    public WebhookEventProcessingPipeline(
            EventClassifier eventClassifier,
            QueuedWorkItemProcessor queuedWorkItemProcessor,
            Clock clock
    ) {
        this.eventClassifier = eventClassifier;
        this.queuedWorkItemProcessor = queuedWorkItemProcessor;
        this.clock = clock;
    }

    public WebhookProcessingResult process(AdoWebhookEvent event, ProjectApprovalConfig projectConfig) {
        var classification = eventClassifier.classify(event, projectConfig);
        if (classification.status() == EventClassificationStatus.FAILED_MALFORMED_EVENT) {
            return WebhookProcessingResult.malformed(classification.reason(), classification);
        }
        if (classification.status() != EventClassificationStatus.PROCESSABLE) {
            return WebhookProcessingResult.skipped(classification.reason(), classification);
        }

        var command = ProcessWorkItemCommand.from(classification, projectConfig);
        var queuedEvent = QueuedWorkItemEvent.from(command, Instant.now(clock));
        var result = queuedWorkItemProcessor.process(queuedEvent);
        return WebhookProcessingResult.fromWorkItemResult(result, classification);
    }
}
