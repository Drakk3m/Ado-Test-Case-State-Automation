package com.dentalwings.approvalbot.config.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.dentalwings.approvalbot.ado.AdoClient;
import com.dentalwings.approvalbot.ado.DryRunAdoClient;
import com.dentalwings.approvalbot.ado.RetryingAdoClient;
import com.dentalwings.approvalbot.ado.RuntimeAdoCredentialService;
import com.dentalwings.approvalbot.ado.http.AzureDevOpsHttpClient;
import com.dentalwings.approvalbot.idempotency.IdempotentWorkItemProcessor;
import com.dentalwings.approvalbot.idempotency.ProcessedEventStore;
import com.dentalwings.approvalbot.processing.WorkItemProcessingService;
import com.dentalwings.approvalbot.processing.pipeline.WebhookEventProcessingPipeline;
import com.dentalwings.approvalbot.queue.InMemoryWorkItemQueue;
import com.dentalwings.approvalbot.queue.QueuedWorkItemProcessor;
import com.dentalwings.approvalbot.queue.WorkItemQueue;
import com.dentalwings.approvalbot.webhook.EventClassifier;
import com.dentalwings.approvalbot.workflow.WorkflowEngine;
import com.dentalwings.approvalbot.workflow.comment.CommentBuilder;
import com.dentalwings.approvalbot.workflow.patch.PatchBuilder;

@Configuration
public class ProcessingPipelineBeanConfiguration
{

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingPipelineBeanConfiguration.class);

    @Bean
    @ConditionalOnProperty(name = "ado.http-client-enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public AdoClient azureDevOpsHttpClient(ApprovalBotProperties properties, RuntimeAdoCredentialService credentialService)
    {
        if (properties.getAdo().getPersonalAccessToken() == null
                || properties.getAdo().getPersonalAccessToken().isBlank())
        {
            LOGGER.warn("ADO HTTP client enabled without PAT; startup continues in local-only mode and webhook ADO calls will fail as non-retryable until PAT is configured.");
            return new RetryingAdoClient(new MissingPatAdoClient(properties.getAdo(), credentialService));
        }
        var httpClient = AzureDevOpsHttpClient.fromProperties(properties.getAdo());
        if (!properties.getAdo().isDryRun())
        {
            LOGGER.info(
                    "Azure DevOps HTTP client enabled with dry-run disabled; write operations will be sent to ADO.");
            return new RetryingAdoClient(httpClient);
        }

        LOGGER.info("Azure DevOps dry-run mode is enabled; write operations will be suppressed.");
        return new RetryingAdoClient(new DryRunAdoClient(httpClient));
    }

    @Bean
    @ConditionalOnMissingBean
    public AdoClient unsupportedAdoClient()
    {
        return new UnsupportedAdoClient();
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkItemProcessingService workItemProcessingService(AdoClient adoClient, WorkflowEngine workflowEngine,
            PatchBuilder patchBuilder, CommentBuilder commentBuilder)
    {
        return new WorkItemProcessingService(adoClient, workflowEngine, patchBuilder, commentBuilder);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentWorkItemProcessor idempotentWorkItemProcessor(ProcessedEventStore processedEventStore,
            WorkItemProcessingService workItemProcessingService)
    {
        return new IdempotentWorkItemProcessor(processedEventStore, workItemProcessingService);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkItemQueue workItemQueue()
    {
        return new InMemoryWorkItemQueue();
    }

    @Bean
    @ConditionalOnMissingBean
    public QueuedWorkItemProcessor queuedWorkItemProcessor(WorkItemQueue workItemQueue,
            IdempotentWorkItemProcessor idempotentWorkItemProcessor)
    {
        return new QueuedWorkItemProcessor(workItemQueue, idempotentWorkItemProcessor);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebhookEventProcessingPipeline webhookEventProcessingPipeline(EventClassifier eventClassifier,
            QueuedWorkItemProcessor queuedWorkItemProcessor)
    {
        return new WebhookEventProcessingPipeline(eventClassifier, queuedWorkItemProcessor);
    }
}
