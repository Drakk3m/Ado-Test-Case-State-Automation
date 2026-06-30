package com.dentalwings.approvalbot.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.dentalwings.approvalbot.ado.AdoClient;
import com.dentalwings.approvalbot.ado.AdoCommentResult;
import com.dentalwings.approvalbot.ado.AdoPatchResult;
import com.dentalwings.approvalbot.ado.AdoWorkItem;
import com.dentalwings.approvalbot.ado.AdoWorkItemKey;
import com.dentalwings.approvalbot.ado.AdoWorkItemRevision;
import com.dentalwings.approvalbot.ado.DryRunAdoClient;
import com.dentalwings.approvalbot.ado.RetryingAdoClient;
import com.dentalwings.approvalbot.config.spring.ApprovalBotProperties;
import com.dentalwings.approvalbot.domain.ProcessingResult;
import com.dentalwings.approvalbot.processing.ProcessWorkItemCommand;
import com.dentalwings.approvalbot.processing.WorkItemProcessingResult;

class RepositoryDispatchOneShotRunnerTest
{

    @TempDir
    Path tempDir;

    @Test
    void skipsWhenProjectIsNotConfigured() throws IOException
    {
        var payloadFile = writePayload("Project Missing");
        var configFile = writeConfig();
        var fakeClient = new FakeAdoClient();

        var runner = new RepositoryDispatchOneShotRunner(new RepositoryDispatchPayloadParser(),
                new ApprovalBotYamlConfigLoader(), properties -> fakeClient,
                adoClient -> command -> WorkItemProcessingResult.completed("unexpected", null));

        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = runner.run(arguments(payloadFile, configFile), new PrintStream(out), new PrintStream(err));

        assertThat(exit).isZero();
        assertThat(fakeClient.fetchCalls).isZero();
        assertThat(fakeClient.writeCalls()).isZero();
        assertThat(out.toString()).contains(
                "project=Project Missing workItemId=12345 revision=17 result=SKIPPED reason=Project is not configured.");
    }

    @Test
    void skipsDisabledProjectBeforeCreatingAdoClient() throws IOException
    {
        var payloadFile = writePayload("Project A");
        var configFile = writeConfig(false, "Test Case", "dummy");
        var factoryCalls = new int[1];

        var runner = new RepositoryDispatchOneShotRunner(new RepositoryDispatchPayloadParser(),
                new ApprovalBotYamlConfigLoader(), properties -> {
                    factoryCalls[0]++;
                    return new FakeAdoClient();
                }, adoClient -> command -> WorkItemProcessingResult.completed("unexpected", null));

        var out = new ByteArrayOutputStream();
        var exit = runner.run(arguments(payloadFile, configFile), new PrintStream(out), System.err);

        assertThat(exit).isZero();
        assertThat(factoryCalls[0]).isZero();
        assertThat(out.toString()).contains("result=SKIPPED reason=Project is disabled.");
    }

    @Test
    void skipsUnsupportedWorkItemTypeBeforeAdoWrite() throws IOException
    {
        var payloadFile = writePayload("Project A");
        var configFile = writeConfig();
        var fakeClient = new FakeAdoClient();
        fakeClient.workItemType = "Bug";
        var processorCalls = new int[1];

        var runner = new RepositoryDispatchOneShotRunner(new RepositoryDispatchPayloadParser(),
                new ApprovalBotYamlConfigLoader(), properties -> fakeClient, adoClient -> command -> {
                    processorCalls[0]++;
                    return WorkItemProcessingResult.completed("unexpected", null);
                });

        var out = new ByteArrayOutputStream();
        var exit = runner.run(arguments(payloadFile, configFile), new PrintStream(out), System.err);

        assertThat(exit).isZero();
        assertThat(fakeClient.fetchCalls).isEqualTo(1);
        assertThat(fakeClient.writeCalls()).isZero();
        assertThat(processorCalls[0]).isZero();
        assertThat(out.toString()).contains("result=SKIPPED reason=Work item type is unsupported");
    }

    @Test
    void fetchesSourceOfTruthAndCallsProcessor() throws IOException
    {
        var payloadFile = writePayload("Project A", "Payload-Organization");
        var configFile = writeConfig();
        var fakeClient = new FakeAdoClient();
        ProcessWorkItemCommand[] capturedCommand = new ProcessWorkItemCommand[1];

        var runner = new RepositoryDispatchOneShotRunner(new RepositoryDispatchPayloadParser(),
                new ApprovalBotYamlConfigLoader(), properties -> fakeClient, adoClient -> command -> {
                    capturedCommand[0] = command;
                    return new WorkItemProcessingResult(ProcessingResult.COMPLETED, "Patch and comment completed.", null);
                });

        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = runner.run(arguments(payloadFile, configFile), new PrintStream(out), new PrintStream(err));

        assertThat(exit).isZero();
        assertThat(fakeClient.fetchCalls).isEqualTo(1);
        assertThat(capturedCommand[0]).isNotNull();
        assertThat(capturedCommand[0].workItemKey().project()).isEqualTo("Project A");
        assertThat(capturedCommand[0].workItemKey().organization()).isEqualTo("STMN-Group");
        assertThat(capturedCommand[0].revision()).isEqualTo(17);
        assertThat(out.toString()).contains("Fetched ADO source of truth")
                .contains("project=Project A workItemId=12345 revision=17 result=COMPLETED "
                        + "reason=Patch and comment completed.");
    }

    @Test
    void processesCanonicalAdoEventEnvelope() throws IOException
    {
        var payloadFile = writeCanonicalPayload();
        var configFile = writeConfig();
        var fakeClient = new FakeAdoClient();
        ProcessWorkItemCommand[] capturedCommand = new ProcessWorkItemCommand[1];
        var runner = new RepositoryDispatchOneShotRunner(new RepositoryDispatchPayloadParser(),
                new ApprovalBotYamlConfigLoader(), properties -> fakeClient, adoClient -> command -> {
                    capturedCommand[0] = command;
                    return WorkItemProcessingResult.completed("done", null);
                });

        var exit = runner.run(arguments(payloadFile, configFile), System.out, System.err);

        assertThat(exit).isZero();
        assertThat(capturedCommand[0].workItemKey().project()).isEqualTo("Project A");
        assertThat(capturedCommand[0].workItemKey().workItemId()).isEqualTo(12345L);
        assertThat(capturedCommand[0].revision()).isEqualTo(17);
    }

    @Test
    void mapsRetryableAndNonRetryableProcessingResultsToDocumentedExitCodes() throws IOException
    {
        assertProcessingExit(WorkItemProcessingResult.failedRetryable("retry later", null),
                RepositoryDispatchOneShotRunner.EXIT_RETRYABLE_FAILURE, "result=FAILED_RETRYABLE");
        assertProcessingExit(WorkItemProcessingResult.failedNonRetryable("invalid request", null),
                RepositoryDispatchOneShotRunner.EXIT_NON_RETRYABLE_FAILURE, "result=FAILED_NON_RETRYABLE");
        assertProcessingExit(WorkItemProcessingResult.completedWithWarning("comment failed", null),
                RepositoryDispatchOneShotRunner.EXIT_SUCCESS, "result=COMPLETED_WITH_WARNING");
    }

    @Test
    void missingPatIsAValidationErrorWithoutLeakingConfiguredText() throws IOException
    {
        var payloadFile = writePayload("Project A");
        var configFile = writeConfig(true, "Test Case", "");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = new RepositoryDispatchOneShotRunner().run(arguments(payloadFile, configFile),
                new PrintStream(out), new PrintStream(err));

        assertThat(exit).isEqualTo(RepositoryDispatchOneShotRunner.EXIT_VALIDATION_ERROR);
        assertThat(err.toString()).contains("ADO PAT is required").doesNotContain("Authorization");
        assertThat(out.toString()).contains("project=Project A workItemId=12345 revision=17 result=VALIDATION_ERROR")
                .doesNotContain("personal-access-token");
    }

    @Test
    void defaultClientPreservesRetryAndDryRunDecorators()
    {
        var properties = new ApprovalBotProperties();
        properties.getAdo().setHttpClientEnabled(true);
        properties.getAdo().setDryRun(true);
        properties.getAdo().setOrganization("ExampleOrg");
        properties.getAdo().setPersonalAccessToken("not-a-real-secret");

        var client = RepositoryDispatchOneShotRunner.defaultAdoClient(properties);

        assertThat(client).isInstanceOf(RetryingAdoClient.class);
        var retrying = (RetryingAdoClient) client;
        assertThat(retrying.delegate()).isInstanceOf(DryRunAdoClient.class);
    }

    private void assertProcessingExit(WorkItemProcessingResult result, int expectedExit, String expectedOutput)
            throws IOException
    {
        var payloadFile = writePayload("Project A");
        var configFile = writeConfig();
        var runner = new RepositoryDispatchOneShotRunner(new RepositoryDispatchPayloadParser(),
                new ApprovalBotYamlConfigLoader(), properties -> new FakeAdoClient(), adoClient -> command -> result);
        var out = new ByteArrayOutputStream();

        var exit = runner.run(arguments(payloadFile, configFile), new PrintStream(out), System.err);

        assertThat(exit).isEqualTo(expectedExit);
        assertThat(out.toString()).contains(expectedOutput).contains("project=Project A").contains("workItemId=12345")
                .contains("revision=17").contains("reason=" + result.reason());
    }

    private String[] arguments(Path payloadFile, Path configFile)
    {
        return new String[] { "--payload", payloadFile.toString(), "--config", configFile.toString() };
    }

    private Path writePayload(String project) throws IOException
    {
        return writePayload(project, "STMN-Group");
    }

    private Path writePayload(String project, String organization) throws IOException
    {
        var file = tempDir.resolve("payload.json");
        Files.writeString(file, """
                {
                  "source": "ado-service-hook",
                  "organization": "%s",
                  "project": "%s",
                  "workItemId": 12345,
                  "revision": 17,
                  "eventType": "workitem.updated"
                }
                """.formatted(organization, project));
        return file;
    }

    private Path writeCanonicalPayload() throws IOException
    {
        var file = tempDir.resolve("canonical-payload.json");
        Files.writeString(file, """
                {
                  "ado_event": {
                    "eventType": "workitem.updated",
                    "id": "delivery-1",
                    "resource": {
                      "workItemId": 12345,
                      "rev": 17,
                      "id": 17,
                      "revision": {
                        "id": 12345,
                        "rev": 17,
                        "fields": {
                          "System.TeamProject": "Project A",
                          "System.WorkItemType": "Test Case"
                        }
                      }
                    },
                    "resourceContainers": {
                      "account": {
                        "baseUrl": "https://dev.azure.com/Payload-Organization/"
                      }
                    }
                  }
                }
                """);
        return file;
    }

    private Path writeConfig() throws IOException
    {
        return writeConfig(true, "Test Case", "dummy");
    }

    private Path writeConfig(boolean enabled, String supportedType, String pat) throws IOException
    {
        var file = tempDir.resolve("application.yml");
        Files.writeString(file, """
                ado:
                  organization: STMN-Group
                  personal-access-token: "%s"
                  http-client-enabled: true
                  dry-run: true
                  projects:
                    "[Project A]":
                      enabled: %s
                      supported-work-item-types:
                        - %s
                      fields:
                        approved-by-sme: Custom.ApproverTech
                        approved-by-sqa: Custom.ApproverTest
                        reversible-business-fields:
                          - System.Title
                      approvals:
                        sme-users:
                          - sme@example.com
                        sqa-users:
                          - sqa@example.com
                bot:
                  identity-email: bot@example.com
                """.formatted(pat, enabled, supportedType));
        return file;
    }

    private static class FakeAdoClient implements AdoClient
    {

        private int fetchCalls;
        private int patchCalls;
        private int commentCalls;
        private String workItemType = "Test Case";

        @Override
        public AdoWorkItem fetchWorkItem(AdoWorkItemKey key)
        {
            fetchCalls++;
            return new AdoWorkItem(key.workItemId(), key.project(), workItemType, 17, "In Review", Map.of());
        }

        @Override
        public AdoWorkItemRevision fetchWorkItemRevision(AdoWorkItemKey key, int revision)
        {
            return new AdoWorkItemRevision(key.workItemId(), revision, null, Map.of(), Set.of());
        }

        @Override
        public AdoPatchResult patchWorkItem(AdoWorkItemKey key,
                List<com.dentalwings.approvalbot.domain.PatchOperation> patchOperations)
        {
            patchCalls++;
            return AdoPatchResult.success(18);
        }

        @Override
        public AdoCommentResult createWorkItemComment(AdoWorkItemKey key, String commentText)
        {
            commentCalls++;
            return AdoCommentResult.success("1");
        }

        private int writeCalls()
        {
            return patchCalls + commentCalls;
        }
    }
}


