package com.dentalwings.approvalbot.processing.pipeline;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.dentalwings.approvalbot.config.ProjectApprovalConfig;
import com.dentalwings.approvalbot.processing.ProcessWorkItemCommand;
import com.dentalwings.approvalbot.queue.QueuedWorkItemEvent;
import com.dentalwings.approvalbot.queue.QueuedWorkItemProcessor;
import com.dentalwings.approvalbot.webhook.AdoWebhookEvent;
import com.dentalwings.approvalbot.webhook.EventClassificationStatus;
import com.dentalwings.approvalbot.webhook.EventClassifier;

public class WebhookEventProcessingPipeline
{

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookEventProcessingPipeline.class);

    private final EventClassifier eventClassifier;
    private final QueuedWorkItemProcessor queuedWorkItemProcessor;
    private final Clock clock;

    public WebhookEventProcessingPipeline(EventClassifier eventClassifier,
            QueuedWorkItemProcessor queuedWorkItemProcessor)
    {
        this(eventClassifier, queuedWorkItemProcessor, Clock.systemUTC());
    }

    public WebhookEventProcessingPipeline(EventClassifier eventClassifier,
            QueuedWorkItemProcessor queuedWorkItemProcessor, Clock clock)
    {
        this.eventClassifier = eventClassifier;
        this.queuedWorkItemProcessor = queuedWorkItemProcessor;
        this.clock = clock;
    }

    public WebhookProcessingResult process(AdoWebhookEvent event, ProjectApprovalConfig projectConfig)
    {
        var classification = eventClassifier.classify(event, projectConfig);
        var resource = event == null ? null : event.resource();
        LOGGER.info("Webhook event classified status={} project={} workItemId={} revision={} reason={}",
                classification.status(), resource == null ? null : resource.project(),
                resource == null ? null : resource.workItemId(), resource == null ? null : resource.revision(),
                classification.reason());
        if (classification.status() == EventClassificationStatus.FAILED_MALFORMED_EVENT)
        {
            return WebhookProcessingResult.malformed(classification.reason(), classification);
        }
        if (classification.status() != EventClassificationStatus.PROCESSABLE)
        {
            return WebhookProcessingResult.skipped(classification.reason(), classification);
        }

        var command = ProcessWorkItemCommand.from(classification, projectConfig);
        var queuedEvent = QueuedWorkItemEvent.from(command, Instant.now(clock));
        LOGGER.debug("Submitting work item event to queue project={} workItemId={} revision={}",
                command.workItemKey().project(), command.workItemKey().workItemId(), command.revision());
        var result = queuedWorkItemProcessor.process(queuedEvent);
        LOGGER.info("Webhook event processing result project={} workItemId={} revision={} result={} reason={}",
                command.workItemKey().project(), command.workItemKey().workItemId(), command.revision(),
                result.result(), result.reason());
        return WebhookProcessingResult.fromWorkItemResult(result, classification);
    }
}
