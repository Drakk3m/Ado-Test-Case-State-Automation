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
        assertThat(out.toString()).contains("Final result: SKIPPED");
    }

    @Test
    void fetchesSourceOfTruthAndCallsProcessor() throws IOException
    {
        var payloadFile = writePayload("Project A");
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
        assertThat(capturedCommand[0].revision()).isEqualTo(17);
        assertThat(out.toString()).contains("Fetched ADO source of truth")
                .contains("Final result: COMPLETED (Patch and comment completed.)");
    }

    private String[] arguments(Path payloadFile, Path configFile)
    {
        return new String[] { "--payload", payloadFile.toString(), "--config", configFile.toString() };
    }

    private Path writePayload(String project) throws IOException
    {
        var file = tempDir.resolve("payload.json");
        Files.writeString(file, """
                {
                  "source": "ado-service-hook",
                  "organization": "STMN-Group",
                  "project": "%s",
                  "workItemId": 12345,
                  "revision": 17,
                  "eventType": "workitem.updated"
                }
                """.formatted(project));
        return file;
    }

    private Path writeConfig() throws IOException
    {
        var file = tempDir.resolve("application.yml");
        Files.writeString(file, """
                ado:
                  organization: STMN-Group
                  personal-access-token: "${ADO_PERSONAL_ACCESS_TOKEN:dummy}"
                  http-client-enabled: true
                  dry-run: true
                  projects:
                    "[Project A]":
                      enabled: true
                      supported-work-item-types:
                        - Test Case
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
                """);
        return file;
    }

    private static class FakeAdoClient implements AdoClient
    {

        private int fetchCalls;

        @Override
        public AdoWorkItem fetchWorkItem(AdoWorkItemKey key)
        {
            fetchCalls++;
            return new AdoWorkItem(key.workItemId(), key.project(), "Test Case", 17, "In Review", Map.of());
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
            return AdoPatchResult.success(18);
        }

        @Override
        public AdoCommentResult createWorkItemComment(AdoWorkItemKey key, String commentText)
        {
            return AdoCommentResult.success("1");
        }
    }
}


