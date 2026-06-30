package com.dentalwings.approvalbot.config.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.dentalwings.approvalbot.ApprovalBotApplication;
import com.dentalwings.approvalbot.ado.AdoClient;
import com.dentalwings.approvalbot.ado.AdoWorkItemKey;
import com.dentalwings.approvalbot.ado.DryRunAdoClient;
import com.dentalwings.approvalbot.ado.RetryingAdoClient;
import com.dentalwings.approvalbot.ado.http.AzureDevOpsHttpClient;
import com.dentalwings.approvalbot.idempotency.IdempotentWorkItemProcessor;
import com.dentalwings.approvalbot.idempotency.InMemoryProcessedEventStore;
import com.dentalwings.approvalbot.idempotency.ProcessedEventStore;
import com.dentalwings.approvalbot.idempotency.sqlite.SqliteProcessedEventStore;
import com.dentalwings.approvalbot.processing.WorkItemProcessingService;
import com.dentalwings.approvalbot.processing.pipeline.WebhookEventProcessingPipeline;
import com.dentalwings.approvalbot.queue.QueuedWorkItemProcessor;
import com.dentalwings.approvalbot.webhook.spring.AdoWebhookController;

class ApprovalBotBeanWiringTest
{

    @TempDir
    private Path tempDir;

    @Test
    void springApplicationContextLoadsWithInMemoryIdempotencyAndMockedAdoClient()
    {
        contextRunner("in-memory", null).withBean(AdoClient.class, () -> mock(AdoClient.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(InMemoryProcessedEventStore.class);
            assertThat(context).hasSingleBean(WebhookEventProcessingPipeline.class);
            assertThat(context).hasSingleBean(AdoWebhookController.class);
        });
    }

    @Test
    void springApplicationContextLoadsWithSqliteIdempotencyUsingTempPath()
    {
        var sqlitePath = tempDir.resolve("approval-bot.sqlite");

        contextRunner("sqlite", sqlitePath).withBean(AdoClient.class, () -> mock(AdoClient.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SqliteProcessedEventStore.class);
            assertThat(sqlitePath).exists();
        });
    }

    @Test
    void processedEventStoreBeanIsSqliteWhenConfiguredAsSqlite()
    {
        contextRunner("sqlite", tempDir.resolve("events.sqlite")).withBean(AdoClient.class, () -> mock(AdoClient.class))
                .run(context -> assertThat(context.getBean(ProcessedEventStore.class))
                        .isInstanceOf(SqliteProcessedEventStore.class));
    }

    @Test
    void processedEventStoreBeanIsInMemoryWhenConfiguredAsInMemory()
    {
        contextRunner("in-memory", null).withBean(AdoClient.class, () -> mock(AdoClient.class))
                .run(context -> assertThat(context.getBean(ProcessedEventStore.class))
                        .isInstanceOf(InMemoryProcessedEventStore.class));
    }

    @Test
    void webhookEventProcessingPipelineBeanIsCreated()
    {
        contextRunner("in-memory", null).withBean(AdoClient.class, () -> mock(AdoClient.class))
                .run(context -> assertThat(context).hasSingleBean(WebhookEventProcessingPipeline.class));
    }

    @Test
    void queuedWorkItemProcessorBeanIsCreated()
    {
        contextRunner("in-memory", null).withBean(AdoClient.class, () -> mock(AdoClient.class))
                .run(context -> assertThat(context).hasSingleBean(QueuedWorkItemProcessor.class));
    }

    @Test
    void idempotentWorkItemProcessorBeanIsCreated()
    {
        contextRunner("in-memory", null).withBean(AdoClient.class, () -> mock(AdoClient.class))
                .run(context -> assertThat(context).hasSingleBean(IdempotentWorkItemProcessor.class));
    }

    @Test
    void workItemProcessingServiceBeanIsCreated()
    {
        contextRunner("in-memory", null).withBean(AdoClient.class, () -> mock(AdoClient.class))
                .run(context -> assertThat(context).hasSingleBean(WorkItemProcessingService.class));
    }

    @Test
    void controllerReceivesWiredPipeline()
    {
        contextRunner("in-memory", null).withBean(AdoClient.class, () -> mock(AdoClient.class)).run(context -> {
            assertThat(context).hasSingleBean(AdoWebhookController.class);
            assertThat(context).hasSingleBean(WebhookEventProcessingPipeline.class);
        });
    }

    @Test
    void projectConfigResolverResolvesConfiguredProject()
    {
        contextRunner("in-memory", null).withBean(AdoClient.class, () -> mock(AdoClient.class))
                .run(context -> assertThat(
                        context.getBean(ProjectApprovalConfigResolver.class).findByProjectName(" projecta "))
                        .hasValueSatisfying(config -> assertThat(config.projectName()).isEqualTo("ProjectA")));
    }

    @Test
    void projectConfigResolverHandlesUnknownAndDisabledProjectConsistently()
    {
        contextRunner("in-memory", null).withPropertyValues("ado.projects.DisabledProject.enabled=false")
                .withBean(AdoClient.class, () -> mock(AdoClient.class)).run(context -> {
                    var resolver = context.getBean(ProjectApprovalConfigResolver.class);
                    assertThat(resolver.findByProjectName("Unknown")).isEmpty();
                    assertThat(resolver.findByProjectName("DisabledProject"))
                            .hasValueSatisfying(config -> assertThat(config.enabled()).isFalse());
                });
    }

    @Test
    void placeholderAdoClientThrowsClearUnsupportedExceptionWhenNoRealBeanIsProvided()
    {
        contextRunner("in-memory", null).run(context -> {
            var adoClient = context.getBean(AdoClient.class);
            assertThat(adoClient).isInstanceOf(UnsupportedAdoClient.class);
            assertThatThrownBy(() -> adoClient.fetchWorkItem(new AdoWorkItemKey("org", "ProjectA", 123)))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("Real Azure DevOps client is not implemented yet.");
        });
    }

    @Test
    void dryRunAdoClientWrapsAzureDevOpsHttpClientWhenHttpEnabledAndDryRunDefaultsToTrue()
    {
        contextRunner("in-memory", null).withPropertyValues("ado.http-client-enabled=true").run(context -> {
            var adoClient = context.getBean(AdoClient.class);

            assertThat(adoClient).isInstanceOf(RetryingAdoClient.class);
            var dryRunClient = ((RetryingAdoClient) adoClient).delegate();
            assertThat(dryRunClient).isInstanceOf(DryRunAdoClient.class);
            assertThat(((DryRunAdoClient) dryRunClient).delegate()).isInstanceOf(AzureDevOpsHttpClient.class);
        });
    }

    @Test
    void azureDevOpsHttpClientIsUsedWhenHttpEnabledAndDryRunDisabled()
    {
        contextRunner("in-memory", null).withPropertyValues("ado.http-client-enabled=true", "ado.dry-run=false")
                .run(context -> {
                    var adoClient = context.getBean(AdoClient.class);
                    assertThat(adoClient).isInstanceOf(RetryingAdoClient.class);
                    assertThat(((RetryingAdoClient) adoClient).delegate()).isInstanceOf(AzureDevOpsHttpClient.class);
                });
    }

    @Test
    void unsupportedAdoClientIsStillUsedWhenHttpClientIsDisabledEvenIfDryRunIsTrue()
    {
        contextRunner("in-memory", null).withPropertyValues("ado.dry-run=true")
                .run(context -> assertThat(context.getBean(AdoClient.class)).isInstanceOf(UnsupportedAdoClient.class));
    }

    @Test
    void noWebClientOrRestTemplateDependencyIsIntroduced()
    {
        assertNoForbiddenTypeReferences("WebClient", ApprovalBotBeanConfiguration.class,
                IdempotencyBeanConfiguration.class, ProcessingPipelineBeanConfiguration.class,
                UnsupportedAdoClient.class);
        assertNoForbiddenTypeReferences("RestTemplate", ApprovalBotBeanConfiguration.class,
                IdempotencyBeanConfiguration.class, ProcessingPipelineBeanConfiguration.class,
                UnsupportedAdoClient.class);
    }

    private ApplicationContextRunner contextRunner(String idempotencyType, Path sqlitePath)
    {
        var runner = new ApplicationContextRunner().withUserConfiguration(ApprovalBotApplication.class)
                .withPropertyValues("ado.organization=my-org", "ado.personal-access-token=test-token",
                        "ado.projects.ProjectA.enabled=true",
                        "ado.projects.ProjectA.supported-work-item-types[0]=Test Case",
                        "ado.projects.ProjectA.fields.approved-by-sme=Custom.ApprovedBySME",
                        "ado.projects.ProjectA.fields.approved-by-sqa=Custom.ApprovedBySQA",
                        "ado.projects.ProjectA.fields.reversible-business-fields[0]=System.Title",
                        "ado.projects.ProjectA.approvals.sme-users[0]=ana.perez@company.com",
                        "ado.projects.ProjectA.approvals.sqa-users[0]=carlos.gomez@company.com",
                        "bot.identity-email=ado-approval-bot@company.com",
                        "webhook.shared-secret.value=test-webhook-secret", "idempotency.type=" + idempotencyType,
                        "idempotency.ttl-hours=24", "idempotency.max-records=10000");
        if (sqlitePath == null)
        {
            return runner;
        }
        return runner.withPropertyValues("idempotency.sqlite-path=" + sqlitePath);
    }

    private void assertNoForbiddenTypeReferences(String forbiddenText, Class<?>... classes)
    {
        for (Class<?> type : classes)
        {
            assertThat(type.getName()).doesNotContain(forbiddenText);
            for (var method : type.getDeclaredMethods())
            {
                assertThat(method.toGenericString()).doesNotContain(forbiddenText);
            }
            for (var constructor : type.getDeclaredConstructors())
            {
                assertThat(constructor.toGenericString()).doesNotContain(forbiddenText);
            }
            for (var field : type.getDeclaredFields())
            {
                assertThat(field.toGenericString()).doesNotContain(forbiddenText);
            }
        }
    }
}
