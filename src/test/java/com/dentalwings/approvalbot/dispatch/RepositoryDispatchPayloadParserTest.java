package com.dentalwings.approvalbot.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryDispatchPayloadParserTest
{

    @TempDir
    Path tempDir;

    private final RepositoryDispatchPayloadParser parser = new RepositoryDispatchPayloadParser();

    @Test
    void parsesValidPayload() throws IOException
    {
        var file = writePayload("""
                {
                  "source": "ado-service-hook",
                  "organization": "STMN-Group",
                  "project": "ADOnis 2.0 Test Project",
                  "workItemId": 12345,
                  "revision": 17,
                  "eventType": "workitem.updated",
                  "changedBy": {
                    "displayName": "Jane Doe",
                    "uniqueName": "jane.doe@example.com"
                  },
                  "resourceUrl": "https://dev.azure.com/STMN-Group/proj/_apis/wit/workItems/12345",
                  "subscriptionId": "sub-1",
                  "deliveryId": "delivery-1"
                }
                """);

        var payload = parser.parse(file);

        assertThat(payload.source()).isEqualTo("ado-service-hook");
        assertThat(payload.organization()).isEqualTo("STMN-Group");
        assertThat(payload.project()).isEqualTo("ADOnis 2.0 Test Project");
        assertThat(payload.workItemId()).isEqualTo(12345L);
        assertThat(payload.revision()).isEqualTo(17);
        assertThat(payload.eventType()).isEqualTo("workitem.updated");
        assertThat(payload.changedBy()).isNotNull();
        assertThat(payload.changedBy().displayName()).isEqualTo("Jane Doe");
        assertThat(payload.changedBy().uniqueName()).isEqualTo("jane.doe@example.com");
    }

    @Test
    void returnsAllMissingRequiredFieldErrors()
    {
        var file = writePayloadUnchecked("""
                {
                  "source": " ",
                  "organization": "",
                  "project": null,
                  "workItemId": null,
                  "revision": null,
                  "eventType": ""
                }
                """);

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(InvalidRepositoryDispatchPayloadException.class)
                .satisfies(ex -> {
                    var validation = (InvalidRepositoryDispatchPayloadException) ex;
                    assertThat(validation.errors()).containsExactly(
                            "'source' is required",
                            "'organization' is required",
                            "'project' is required",
                            "'workItemId' is required",
                            "'revision' is required",
                            "'eventType' is required");
                })
                .hasMessageContaining("Invalid repository_dispatch payload");
    }

    @Test
    void returnsValidationErrorsForNonPositiveNumbers()
    {
        var file = writePayloadUnchecked("""
                {
                  "source": "ado-service-hook",
                  "organization": "org",
                  "project": "project",
                  "workItemId": 0,
                  "revision": -1,
                  "eventType": "workitem.updated"
                }
                """);

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(InvalidRepositoryDispatchPayloadException.class)
                .satisfies(ex -> {
                    var validation = (InvalidRepositoryDispatchPayloadException) ex;
                    assertThat(validation.errors()).containsExactly(
                            "'workItemId' must be greater than 0",
                            "'revision' must be greater than 0");
                });
    }

    @Test
    void returnsClearValidationErrorForInvalidRequiredFieldType()
    {
        var file = writePayloadUnchecked("""
                {
                  "source": "ado-service-hook",
                  "organization": "org",
                  "project": "project",
                  "workItemId": "not-a-number",
                  "revision": 1,
                  "eventType": "workitem.updated"
                }
                """);

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(InvalidRepositoryDispatchPayloadException.class)
                .satisfies(ex -> assertThat(((InvalidRepositoryDispatchPayloadException) ex).errors())
                        .containsExactly("'workItemId' has an invalid value"));
    }

    private Path writePayload(String json) throws IOException
    {
        var file = tempDir.resolve("payload.json");
        Files.writeString(file, json);
        return file;
    }

    private Path writePayloadUnchecked(String json)
    {
        try
        {
            return writePayload(json);
        }
        catch (IOException ex)
        {
            throw new RuntimeException(ex);
        }
    }
}

