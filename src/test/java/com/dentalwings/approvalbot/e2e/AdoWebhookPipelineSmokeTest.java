package com.dentalwings.approvalbot.e2e;

import com.dentalwings.approvalbot.ado.AdoClient;
import com.dentalwings.approvalbot.ado.http.AzureDevOpsHttpClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "ado.organization=org",
        "ado.personal-access-token=test-token",
        "ado.projects.ProjectA.enabled=true",
        "ado.projects.ProjectA.supported-work-item-types[0]=Test Case",
        "ado.projects.ProjectA.fields.approved-by-sme=Custom.ApprovedBySME",
        "ado.projects.ProjectA.fields.approved-by-sqa=Custom.ApprovedBySQA",
        "ado.projects.ProjectA.fields.reversible-business-fields[0]=System.Title",
        "ado.projects.ProjectA.approvals.sme-users[0]=sme@example.com",
        "ado.projects.ProjectA.approvals.sqa-users[0]=sqa@example.com",
        "bot.identity-email=bot@example.com",
        "webhook.shared-secret.value=test-webhook-secret",
        "idempotency.type=in-memory",
        "idempotency.ttl-hours=24",
        "idempotency.max-records=10000"
})
@AutoConfigureMockMvc
@ActiveProfiles("e2e-smoke")
class AdoWebhookPipelineSmokeTest {

    private static final String WEBHOOK_PATH = "/api/ado/webhooks/work-item-updated";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingAdoExchange exchange;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetExchange() {
        exchange.reset();
    }

    @Test
    void happyPathProcessesWebhookThroughAdoFetchPatchAndComment() throws Exception {
        exchange.enqueueJson(HttpStatus.OK, currentWorkItemJson(701, 31));
        exchange.enqueueJson(HttpStatus.OK, previousRevisionJson(30));
        exchange.enqueueJson(HttpStatus.OK, currentWorkItemJson(701, 32));
        exchange.enqueueJson(HttpStatus.CREATED, commentJson("comment-701"));

        mockMvc.perform(webhookPost()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload(701, 31, "Human User", "human@example.com")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(exchange.requests).hasSize(4);
        assertFetchPatchCommentOrder();

        var patchBody = patchBody(exchange.patchRequests().getFirst());
        assertThat(patchBody.getFirst())
                .containsEntry("op", "test")
                .containsEntry("path", "/rev")
                .containsEntry("value", 31);
        assertThat(patchBody).anySatisfy(operation -> assertThat(operation)
                .containsEntry("op", "replace")
                .containsEntry("path", "/fields/System.State")
                .containsEntry("value", "In Review"));
        assertThat(patchBody).anySatisfy(operation -> assertThat(operation)
                .containsEntry("op", "replace")
                .containsEntry("path", "/fields/Custom.ApprovedBySME")
                .containsEntry("value", ""));
        assertThat(patchBody).anySatisfy(operation -> assertThat(operation)
                .containsEntry("op", "replace")
                .containsEntry("path", "/fields/Custom.ApprovedBySQA")
                .containsEntry("value", ""));
        assertThat(exchange.patchRequests().getFirst().body()).doesNotContain("System.History");

        var commentBody = commentBody(exchange.commentRequests().getFirst());
        assertThat(commentBody).containsOnlyKeys("text");
        assertThat(commentBody.get("text").toString())
                .contains("returned to In Review")
                .doesNotContain("System.History");
    }

    @Test
    void patchFailureDoesNotCreateCommentOrRetryPatch() throws Exception {
        exchange.enqueueJson(HttpStatus.OK, currentWorkItemJson(702, 41));
        exchange.enqueueJson(HttpStatus.OK, previousRevisionJson(40));
        exchange.enqueueJson(HttpStatus.CONFLICT, "{}");

        mockMvc.perform(webhookPost()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload(702, 41, "Human User", "human@example.com")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("FAILED_RETRYABLE"));

        assertThat(exchange.fetchRequests()).hasSize(2);
        assertThat(exchange.patchRequests()).hasSize(1);
        assertThat(exchange.commentRequests()).isEmpty();
        assertThat(exchange.requests).hasSize(3);
    }

    @Test
    void commentFailureAfterSuccessfulPatchCompletesWithWarningWithoutRetriesOrRollback() throws Exception {
        exchange.enqueueJson(HttpStatus.OK, currentWorkItemJson(703, 51));
        exchange.enqueueJson(HttpStatus.OK, previousRevisionJson(50));
        exchange.enqueueJson(HttpStatus.OK, currentWorkItemJson(703, 52));
        exchange.enqueueJson(HttpStatus.INTERNAL_SERVER_ERROR, "{}");

        mockMvc.perform(webhookPost()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload(703, 51, "Human User", "human@example.com")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMPLETED_WITH_WARNING"));

        assertThat(exchange.fetchRequests()).hasSize(2);
        assertThat(exchange.patchRequests()).hasSize(1);
        assertThat(exchange.commentRequests()).hasSize(1);
        assertThat(exchange.requests).hasSize(4);
    }

    @Test
    void skippedBotGeneratedEventDoesNotCallAdo() throws Exception {
        mockMvc.perform(webhookPost()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload(704, 61, "Approval Bot", "bot@example.com")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SKIPPED"));

        assertThat(exchange.requests).isEmpty();
    }

    @Test
    void duplicateWebhookIsIdempotentlySkippedWithoutSecondAdoCall() throws Exception {
        exchange.enqueueJson(HttpStatus.OK, currentWorkItemJson(705, 71));
        exchange.enqueueJson(HttpStatus.OK, previousRevisionJson(70));
        exchange.enqueueJson(HttpStatus.OK, currentWorkItemJson(705, 72));
        exchange.enqueueJson(HttpStatus.CREATED, commentJson("comment-705"));

        var payload = webhookPayload(705, 71, "Human User", "human@example.com");
        mockMvc.perform(webhookPost()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        var requestCountAfterFirstPost = exchange.requests.size();

        mockMvc.perform(webhookPost()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SKIPPED"));

        assertThat(requestCountAfterFirstPost).isEqualTo(4);
        assertThat(exchange.requests).hasSize(requestCountAfterFirstPost);
        assertThat(exchange.remainingResponses()).isZero();
    }

    private void assertFetchPatchCommentOrder() {
        assertThat(exchange.requests.get(0).method()).isEqualTo(HttpMethod.GET);
        assertThat(exchange.requests.get(0).url()).contains("/_apis/wit/workitems/701?");
        assertThat(exchange.requests.get(1).method()).isEqualTo(HttpMethod.GET);
        assertThat(exchange.requests.get(1).url()).contains("/revisions/30?");
        assertThat(exchange.requests.get(2).method()).isEqualTo(HttpMethod.PATCH);
        assertThat(exchange.requests.get(2).url()).contains("/_apis/wit/workitems/701?");
        assertThat(exchange.requests.get(3).method()).isEqualTo(HttpMethod.POST);
        assertThat(exchange.requests.get(3).url()).contains("/_apis/wit/workItems/701/comments?");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder webhookPost() {
        return post(WEBHOOK_PATH).header("X-ADO-Webhook-Secret", "test-webhook-secret");
    }

    private List<Map<String, Object>> patchBody(RecordedRequest request) throws Exception {
        return objectMapper.readValue(request.body(), new TypeReference<>() {
        });
    }

    private Map<String, Object> commentBody(RecordedRequest request) throws Exception {
        return objectMapper.readValue(request.body(), new TypeReference<>() {
        });
    }

    private String webhookPayload(long workItemId, int revision, String displayName, String email) {
        return """
                {
                  "eventType": "workitem.updated",
                  "organization": "org",
                  "resource": {
                    "id": %d,
                    "rev": %d,
                    "revisedBy": {
                      "displayName": "%s",
                      "uniqueName": "%s"
                    },
                    "revision": {
                      "rev": %d,
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
                """.formatted(workItemId, revision, displayName, email, revision);
    }

    private String currentWorkItemJson(long workItemId, int revision) {
        return """
                {
                  "id": %d,
                  "rev": %d,
                  "fields": {
                    "System.WorkItemType": "Test Case",
                    "System.State": "Approved",
                    "System.Title": "New title",
                    "Custom.ApprovedBySME": null,
                    "Custom.ApprovedBySQA": null
                  }
                }
                """.formatted(workItemId, revision);
    }

    private String previousRevisionJson(int revision) {
        return """
                {
                  "rev": %d,
                  "fields": {
                    "System.State": "In Review",
                    "System.ChangedBy": {
                      "displayName": "Human User",
                      "uniqueName": "human@example.com"
                    },
                    "System.Title": "Old title"
                  }
                }
                """.formatted(revision);
    }

    private String commentJson(String id) {
        return """
                {
                  "id": "%s"
                }
                """.formatted(id);
    }

    @TestConfiguration
    @Profile("e2e-smoke")
    static class SmokeTestConfiguration {

        @Bean
        RecordingAdoExchange recordingAdoExchange() {
            return new RecordingAdoExchange();
        }

        @Bean
        AdoClient adoClient(RecordingAdoExchange exchange) {
            return AzureDevOpsHttpClient.forExchangeFunction(exchange, "test-token");
        }
    }

    static class RecordingAdoExchange implements ExchangeFunction {

        private final ArrayDeque<MockAdoResponse> responses = new ArrayDeque<>();
        private final ArrayList<RecordedRequest> requests = new ArrayList<>();

        void reset() {
            responses.clear();
            requests.clear();
        }

        void enqueueJson(HttpStatus status, String body) {
            responses.addLast(new MockAdoResponse(status, body));
        }

        int remainingResponses() {
            return responses.size();
        }

        List<RecordedRequest> fetchRequests() {
            return requests.stream()
                    .filter(request -> request.method() == HttpMethod.GET)
                    .toList();
        }

        List<RecordedRequest> patchRequests() {
            return requests.stream()
                    .filter(request -> request.method() == HttpMethod.PATCH)
                    .toList();
        }

        List<RecordedRequest> commentRequests() {
            return requests.stream()
                    .filter(request -> request.method() == HttpMethod.POST)
                    .toList();
        }

        @Override
        public Mono<ClientResponse> exchange(ClientRequest request) {
            requests.add(new RecordedRequest(
                    request.method(),
                    request.url().toString(),
                    request.headers(),
                    captureBody(request)
            ));
            var response = responses.pollFirst();
            if (response == null) {
                return Mono.error(new AssertionError("Unexpected ADO HTTP call: " + request.method() + " " + request.url()));
            }
            return Mono.just(ClientResponse.create(response.status())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(response.body())
                    .build());
        }

        private String captureBody(ClientRequest request) {
            var mockRequest = new MockClientHttpRequest(request.method(), request.url());
            mockRequest.getHeaders().putAll(request.headers());
            request.body().insert(mockRequest, new BodyInserterContext()).block();
            return mockRequest.getBodyAsString().block();
        }
    }

    record MockAdoResponse(HttpStatus status, String body) {
    }

    record RecordedRequest(HttpMethod method, String url, HttpHeaders headers, String body) {
    }

    static class BodyInserterContext implements BodyInserter.Context {

        @Override
        public List<HttpMessageWriter<?>> messageWriters() {
            return ExchangeStrategies.withDefaults().messageWriters();
        }

        @Override
        public Optional<ServerHttpRequest> serverRequest() {
            return Optional.empty();
        }

        @Override
        public Map<String, Object> hints() {
            return Map.of();
        }
    }
}
