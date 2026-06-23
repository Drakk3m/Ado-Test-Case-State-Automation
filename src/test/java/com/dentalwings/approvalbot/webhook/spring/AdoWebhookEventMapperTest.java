package com.dentalwings.approvalbot.webhook.spring;

import com.dentalwings.approvalbot.webhook.spring.dto.AdoServiceHookWorkItemUpdatedRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdoWebhookEventMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdoWebhookEventMapper mapper = new AdoWebhookEventMapper();

    @Test
    void dtoMapperExtractsProjectName() throws Exception {
        var event = mapper.toWebhookEvent(request(payload()));

        assertThat(event.resource().project()).isEqualTo("ProjectA");
    }

    @Test
    void dtoMapperExtractsWorkItemId() throws Exception {
        var event = mapper.toWebhookEvent(request(payload()));

        assertThat(event.resource().workItemId()).isEqualTo(123L);
    }

    @Test
    void dtoMapperExtractsWorkItemType() throws Exception {
        var event = mapper.toWebhookEvent(request(payload()));

        assertThat(event.resource().workItemType()).isEqualTo("Test Case");
    }

    @Test
    void dtoMapperExtractsRevision() throws Exception {
        var event = mapper.toWebhookEvent(request(payload()));

        assertThat(event.resource().revision()).isEqualTo(27);
    }

    @Test
    void dtoMapperExtractsChangedByDisplayName() throws Exception {
        var event = mapper.toWebhookEvent(request(payload()));

        assertThat(event.resource().changedByDisplayName()).isEqualTo("Human User");
    }

    @Test
    void dtoMapperExtractsChangedByEmailOrLoginWhenPresent() throws Exception {
        var event = mapper.toWebhookEvent(request(payload()));

        assertThat(event.resource().changedByEmailOrLogin()).isEqualTo("human.user@example.com");
    }

    @Test
    void dtoMapperToleratesMissingChangedByEmailOrLogin() throws Exception {
        var event = mapper.toWebhookEvent(request(payload().replace("\"uniqueName\": \"human.user@example.com\"", "\"displayName\": \"Human User\"")));

        assertThat(event.resource().changedByDisplayName()).isEqualTo("Human User");
        assertThat(event.resource().changedByEmailOrLogin()).isNull();
    }

    @Test
    void dtoMapperExtractsChangedFieldNamesWhenPresent() throws Exception {
        var event = mapper.toWebhookEvent(request(payload()));

        assertThat(event.resource().changedFieldNames()).containsExactly("System.Title", "Custom.ApprovedBySME");
    }

    private AdoServiceHookWorkItemUpdatedRequest request(String json) throws Exception {
        return objectMapper.readValue(json, AdoServiceHookWorkItemUpdatedRequest.class);
    }

    private String payload() {
        return """
                {
                  "eventType": "workitem.updated",
                  "organization": "org",
                  "resource": {
                    "id": 123,
                    "rev": 27,
                    "revisedBy": {
                      "displayName": "Human User",
                      "uniqueName": "human.user@example.com"
                    },
                    "revision": {
                      "rev": 27,
                      "fields": {
                        "System.TeamProject": "ProjectA",
                        "System.WorkItemType": "Test Case"
                      }
                    },
                    "fields": {
                      "System.Title": {
                        "oldValue": "Old title",
                        "newValue": "New title"
                      },
                      "Custom.ApprovedBySME": {
                        "oldValue": null,
                        "newValue": "Approved"
                      }
                    }
                  }
                }
                """;
    }
}
