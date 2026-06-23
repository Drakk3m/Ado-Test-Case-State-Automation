package com.dentalwings.approvalbot.webhook.spring;

import com.dentalwings.approvalbot.config.ProjectApprovalConfig;
import com.dentalwings.approvalbot.config.spring.ProjectApprovalConfigResolver;
import com.dentalwings.approvalbot.processing.WorkItemProcessingResult;
import com.dentalwings.approvalbot.processing.pipeline.WebhookEventProcessingPipeline;
import com.dentalwings.approvalbot.processing.pipeline.WebhookProcessingResult;
import com.dentalwings.approvalbot.webhook.AdoWebhookEvent;
import com.dentalwings.approvalbot.webhook.EventClassification;
import com.dentalwings.approvalbot.webhook.EventClassificationStatus;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdoWebhookController.class)
@Import(AdoWebhookEventMapper.class)
class AdoWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookEventProcessingPipeline pipeline;

    @MockBean
    private ProjectApprovalConfigResolver configResolver;

    @Test
    void controllerAcceptsMinimalValidWorkItemUpdatedPayloadAndDelegatesToPipeline() throws Exception {
        when(configResolver.findByProjectName("ProjectA")).thenReturn(Optional.of(config()));
        when(pipeline.process(any(), any())).thenReturn(completedResult());

        mockMvc.perform(post("/api/ado/webhooks/work-item-updated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalPayload()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        var eventCaptor = ArgumentCaptor.forClass(AdoWebhookEvent.class);
        var configCaptor = ArgumentCaptor.forClass(ProjectApprovalConfig.class);
        verify(pipeline).process(eventCaptor.capture(), configCaptor.capture());
        assertThat(eventCaptor.getValue().resource().project()).isEqualTo("ProjectA");
        assertThat(configCaptor.getValue().projectName()).isEqualTo("ProjectA");
    }

    @Test
    void controllerMapsCompletedPipelineResultToAccepted() throws Exception {
        when(configResolver.findByProjectName("ProjectA")).thenReturn(Optional.of(config()));
        when(pipeline.process(any(), any())).thenReturn(completedResult());

        mockMvc.perform(post("/api/ado/webhooks/work-item-updated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalPayload()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void controllerMapsSkippedPipelineResultToAccepted() throws Exception {
        when(configResolver.findByProjectName("ProjectA")).thenReturn(Optional.of(config()));
        when(pipeline.process(any(), any())).thenReturn(WebhookProcessingResult.skipped("Project is disabled.", skippedClassification()));

        mockMvc.perform(post("/api/ado/webhooks/work-item-updated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalPayload()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SKIPPED"));
    }

    @Test
    void controllerMapsMalformedPipelineResultToBadRequest() throws Exception {
        when(configResolver.findByProjectName("ProjectA")).thenReturn(Optional.of(config()));
        when(pipeline.process(any(), any())).thenReturn(WebhookProcessingResult.malformed("Project is missing.", malformedClassification()));

        mockMvc.perform(post("/api/ado/webhooks/work-item-updated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalPayload()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAILED_MALFORMED_EVENT"));
    }

    @Test
    void controllerMapsRetryableFailureToServiceUnavailable() throws Exception {
        when(configResolver.findByProjectName("ProjectA")).thenReturn(Optional.of(config()));
        when(pipeline.process(any(), any())).thenReturn(WebhookProcessingResult.fromWorkItemResult(
                WorkItemProcessingResult.failedRetryable("timeout", null),
                EventClassification.processable(new com.dentalwings.approvalbot.ado.AdoWorkItemKey("org", "ProjectA", 123), 27)
        ));

        mockMvc.perform(post("/api/ado/webhooks/work-item-updated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalPayload()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("FAILED_RETRYABLE"));
    }

    @Test
    void controllerMapsNonRetryableFailureToBadRequest() throws Exception {
        when(configResolver.findByProjectName("ProjectA")).thenReturn(Optional.of(config()));
        when(pipeline.process(any(), any())).thenReturn(WebhookProcessingResult.fromWorkItemResult(
                WorkItemProcessingResult.failedNonRetryable("bad payload", null),
                EventClassification.processable(new com.dentalwings.approvalbot.ado.AdoWorkItemKey("org", "ProjectA", 123), 27)
        ));

        mockMvc.perform(post("/api/ado/webhooks/work-item-updated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalPayload()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAILED_NON_RETRYABLE"));
    }

    @Test
    void controllerDoesNotCallAdoClientDirectly() {
        assertNoForbiddenTypeReferences("AdoClient", AdoWebhookController.class);
    }

    @Test
    void controllerDoesNotCallWorkflowEngineDirectly() {
        assertNoForbiddenTypeReferences("WorkflowEngine", AdoWebhookController.class);
    }

    @Test
    void controllerDoesNotDependOnWebClientOrRestTemplate() {
        assertNoForbiddenTypeReferences("WebClient", AdoWebhookController.class);
        assertNoForbiddenTypeReferences("RestTemplate", AdoWebhookController.class);
    }

    private WebhookProcessingResult completedResult() {
        return WebhookProcessingResult.fromWorkItemResult(
                WorkItemProcessingResult.completed("done", null),
                EventClassification.processable(new com.dentalwings.approvalbot.ado.AdoWorkItemKey("org", "ProjectA", 123), 27)
        );
    }

    private EventClassification skippedClassification() {
        return EventClassification.skipped(EventClassificationStatus.SKIPPED_DISABLED_PROJECT, "Project is disabled.");
    }

    private EventClassification malformedClassification() {
        return EventClassification.malformed("Project is missing.");
    }

    private ProjectApprovalConfig config() {
        return new ProjectApprovalConfig(
                "ProjectA",
                true,
                Set.of("Test Case"),
                "Custom.ApprovedBySME",
                "Custom.ApprovedBySQA",
                Set.of("System.Title"),
                Set.of("sme@example.com"),
                Set.of("sqa@example.com"),
                "bot@example.com"
        );
    }

    private String minimalPayload() {
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
                      }
                    }
                  }
                }
                """;
    }

    private void assertNoForbiddenTypeReferences(String forbiddenText, Class<?>... classes) {
        for (Class<?> type : classes) {
            assertThat(type.getName()).doesNotContain(forbiddenText);
            for (Method method : type.getDeclaredMethods()) {
                assertThat(method.toGenericString()).doesNotContain(forbiddenText);
            }
            for (var constructor : type.getDeclaredConstructors()) {
                assertThat(constructor.toGenericString()).doesNotContain(forbiddenText);
            }
            for (var field : type.getDeclaredFields()) {
                assertThat(field.toGenericString()).doesNotContain(forbiddenText);
            }
        }
    }
}
