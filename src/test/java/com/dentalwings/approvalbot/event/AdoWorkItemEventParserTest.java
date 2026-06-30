package com.dentalwings.approvalbot.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class AdoWorkItemEventParserTest
{

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdoWorkItemEventParser parser = new AdoWorkItemEventParser();

    @Test
    void parsesCanonicalAdoEventPayload()
    {
        var event = parser.parse(canonicalPayload());

        assertThat(event.source()).isEqualTo("ado-service-hook");
        assertThat(event.organization()).isEqualTo("STMN-Group");
        assertThat(event.project()).isEqualTo("ADOnis 2.0 Test Project");
        assertThat(event.workItemId()).isEqualTo(25691L);
        assertThat(event.revision()).isEqualTo(7);
        assertThat(event.eventType()).isEqualTo("workitem.updated");
        assertThat(event.changedBy().displayName()).isEqualTo("Example User");
        assertThat(event.changedBy().uniqueName()).isEqualTo("user@example.com");
        assertThat(event.resourceUrl()).isEqualTo("https://example.invalid/workitems/25691/updates/7");
        assertThat(event.subscriptionId()).isEqualTo("subscription-1");
        assertThat(event.deliveryId()).isEqualTo("delivery-1");
        assertThat(event.workItemType()).isEqualTo("Test Case");
        assertThat(event.changedFieldNames()).containsExactly("System.Title");
    }

    @Test
    void usesResourceWorkItemIdInsteadOfResourceUpdateId()
    {
        var event = parser.parse(canonicalPayload());

        assertThat(event.workItemId()).isEqualTo(25691L).isNotEqualTo(7L);
    }

    @Test
    void fallsBackToRevisionIdWhenWorkItemIdIsMissing()
    {
        var payload = canonicalPayload();
        resource(payload).remove("workItemId");

        assertThat(parser.parse(payload).workItemId()).isEqualTo(25691L);
    }

    @Test
    void extractsRevisionFromResourceRevAndFallsBackToRevisionRev()
    {
        var primary = canonicalPayload();
        revision(primary).put("rev", 99);
        assertThat(parser.parse(primary).revision()).isEqualTo(7);

        var fallback = canonicalPayload();
        resource(fallback).remove("rev");
        assertThat(parser.parse(fallback).revision()).isEqualTo(7);
    }

    @Test
    void parsesDirectAdoWebhookBodyThroughTheSamePath()
    {
        var event = parser.parse(canonicalPayload().path("ado_event"));

        assertThat(event.project()).isEqualTo("ADOnis 2.0 Test Project");
        assertThat(event.organization()).isEqualTo("STMN-Group");
    }

    @Test
    void rejectsNonWorkItemUpdatedEvent()
    {
        var payload = canonicalPayload();
        event(payload).put("eventType", "workitem.created");

        assertInvalid(payload, "'eventType' must be 'workitem.updated'");
    }

    @Test
    void rejectsMissingProject()
    {
        var payload = canonicalPayload();
        revisionFields(payload).remove("System.TeamProject");

        assertInvalid(payload, "'project' is required");
    }

    @Test
    void rejectsMissingOrganization()
    {
        var payload = canonicalPayload();
        event(payload).remove("resourceContainers");

        assertInvalid(payload, "'organization' is required");
    }

    @Test
    void rejectsMissingWorkItemIdWithoutRevisionFallback()
    {
        var payload = canonicalPayload();
        resource(payload).remove("workItemId");
        revision(payload).remove("id");

        assertInvalid(payload, "'workItemId' is required");
    }

    @Test
    void rejectsMissingRevisionWithoutFallback()
    {
        var payload = canonicalPayload();
        resource(payload).remove("rev");
        revision(payload).remove("rev");

        assertInvalid(payload, "'revision' is required");
    }

    @Test
    void rejectsNonPositiveWorkItemIdAndRevision()
    {
        var payload = canonicalPayload();
        resource(payload).put("workItemId", 0);
        resource(payload).put("rev", -1);

        assertThatThrownBy(() -> parser.parse(payload)).isInstanceOf(InvalidAdoEventPayloadException.class)
                .satisfies(ex -> assertThat(((InvalidAdoEventPayloadException) ex).errors())
                        .contains("'workItemId' must be greater than 0", "'revision' must be greater than 0"));
    }

    private void assertInvalid(JsonNode payload, String error)
    {
        assertThatThrownBy(() -> parser.parse(payload)).isInstanceOf(InvalidAdoEventPayloadException.class)
                .satisfies(ex -> assertThat(((InvalidAdoEventPayloadException) ex).errors()).contains(error));
    }

    private ObjectNode canonicalPayload()
    {
        try
        {
            return (ObjectNode) objectMapper.readTree("""
                    {
                      "ignored": "safe",
                      "ado_event": {
                        "eventType": "workitem.updated",
                        "id": "delivery-1",
                        "subscriptionId": "subscription-1",
                        "resource": {
                          "workItemId": 25691,
                          "rev": 7,
                          "id": 7,
                          "url": "https://example.invalid/workitems/25691/updates/7",
                          "revisedBy": {
                            "displayName": "Example User",
                            "uniqueName": "user@example.com"
                          },
                          "revision": {
                            "id": 25691,
                            "rev": 7,
                            "fields": {
                              "System.TeamProject": "ADOnis 2.0 Test Project",
                              "System.WorkItemType": "Test Case",
                              "System.State": "Design"
                            }
                          },
                          "fields": {
                            "System.Title": {
                              "oldValue": "Old",
                              "newValue": "New"
                            }
                          }
                        },
                        "resourceContainers": {
                          "account": {
                            "baseUrl": "https://dev.azure.com/STMN-Group/"
                          }
                        }
                      }
                    }
                    """);
        }
        catch (Exception ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    private ObjectNode event(JsonNode payload)
    {
        return (ObjectNode) payload.path("ado_event");
    }

    private ObjectNode resource(JsonNode payload)
    {
        return (ObjectNode) event(payload).path("resource");
    }

    private ObjectNode revision(JsonNode payload)
    {
        return (ObjectNode) resource(payload).path("revision");
    }

    private ObjectNode revisionFields(JsonNode payload)
    {
        return (ObjectNode) revision(payload).path("fields");
    }
}
