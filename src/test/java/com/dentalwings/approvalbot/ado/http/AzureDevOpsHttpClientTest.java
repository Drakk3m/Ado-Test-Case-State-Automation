package com.dentalwings.approvalbot.ado.http;

import com.dentalwings.approvalbot.ado.AdoWorkItemKey;
import com.dentalwings.approvalbot.domain.PatchOperation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AzureDevOpsHttpClientTest {

    private static final AdoWorkItemKey KEY = new AdoWorkItemKey("my org", "Project A", 123);

    @Test
    void urlBuilderCreatesCorrectFetchWorkItemUrl() {
        var url = new AzureDevOpsUrlBuilder().workItemUrl(KEY);

        assertThat(url).isEqualTo("https://dev.azure.com/my%20org/Project%20A/_apis/wit/workitems/123?api-version=7.1");
    }

    @Test
    void urlBuilderCreatesCorrectFetchRevisionUrl() {
        var url = new AzureDevOpsUrlBuilder().workItemRevisionUrl(KEY, 27);

        assertThat(url).isEqualTo("https://dev.azure.com/my%20org/Project%20A/_apis/wit/workItems/123/revisions/27?api-version=7.1");
    }

    @Test
    void basicAuthHeaderIsBuiltFromPatUsingBlankUsername() {
        var header = new AzureDevOpsAuth().basicAuthHeader("secret-pat");

        assertThat(header).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString(":secret-pat".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void patValueIsNotExposedByHelperExceptionMessages() {
        assertThatThrownBy(() -> new AzureDevOpsAuth().basicAuthHeader(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("personalAccessToken must not be blank")
                .hasMessageNotContaining("secret-pat");
        assertThat(new AzureDevOpsAuth().toString()).doesNotContain("secret-pat");
    }

    @Test
    void workItemJsonResponseMapsToAdoWorkItem() {
        var client = clientReturning(workItemJson());

        var workItem = client.fetchWorkItem(KEY);

        assertThat(workItem.id()).isEqualTo(123);
        assertThat(workItem.revision()).isEqualTo(27);
        assertThat(workItem.workItemType()).isEqualTo("Test Case");
        assertThat(workItem.state()).isEqualTo("In Review");
        assertThat(workItem.project()).isEqualTo("Project A");
    }

    @Test
    void workItemResponsePreservesRawFieldValues() {
        var client = clientReturning(workItemJson());

        var workItem = client.fetchWorkItem(KEY);

        assertThat(workItem.fields())
                .containsEntry("Custom.ApprovedBySME", null)
                .containsEntry("System.Title", "Raw title");
    }

    @Test
    void revisionJsonResponseMapsToAdoWorkItemRevision() {
        var client = clientReturning(revisionJson("human.user@example.com"));

        var revision = client.fetchWorkItemRevision(KEY, 26);

        assertThat(revision.workItemId()).isEqualTo(123);
        assertThat(revision.revision()).isEqualTo(26);
        assertThat(revision.fields()).containsEntry("System.State", "Draft");
    }

    @Test
    void revisionResponseMapsChangedByDisplayName() {
        var client = clientReturning(revisionJson("human.user@example.com"));

        var revision = client.fetchWorkItemRevision(KEY, 26);

        assertThat(revision.changedBy().displayName()).isEqualTo("Human User");
    }

    @Test
    void revisionResponseMapsChangedByEmailOrLoginWhenPresent() {
        var client = clientReturning(revisionJson("human.user@example.com"));

        var revision = client.fetchWorkItemRevision(KEY, 26);

        assertThat(revision.changedBy().emailOrLogin()).isEqualTo("human.user@example.com");
    }

    @Test
    void revisionResponseToleratesMissingChangedByEmailOrLogin() {
        var client = clientReturning(revisionJson(null));

        var revision = client.fetchWorkItemRevision(KEY, 26);

        assertThat(revision.changedBy().displayName()).isEqualTo("Human User");
        assertThat(revision.changedBy().emailOrLogin()).isNull();
    }

    @Test
    void revisionResponsePreservesRawFieldValues() {
        var client = clientReturning(revisionJson("human.user@example.com"));

        var revision = client.fetchWorkItemRevision(KEY, 26);

        assertThat(revision.fields())
                .containsEntry("Custom.Nullable", null)
                .containsEntry("System.State", "Draft");
    }

    @Test
    void fetchRequestSendsAuthorizationHeaderWithoutExposingItInUrl() {
        var exchange = new RecordingExchangeFunction(workItemJson(), HttpStatus.OK);
        var client = AzureDevOpsHttpClient.forExchangeFunction(exchange, "secret-pat");

        client.fetchWorkItem(KEY);

        assertThat(exchange.requests).hasSize(1);
        assertThat(exchange.requests.getFirst().url().toString()).doesNotContain("secret-pat");
        assertThat(exchange.requests.getFirst().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo(new AzureDevOpsAuth().basicAuthHeader("secret-pat"));
    }

    @Test
    void notFoundThrowsNonRetryableException() {
        var client = clientReturning("{}", HttpStatus.NOT_FOUND);

        assertThatThrownBy(() -> client.fetchWorkItem(KEY))
                .isInstanceOf(AdoClientNonRetryableException.class)
                .hasMessage("Azure DevOps resource was not found.")
                .hasMessageNotContaining("secret-pat");
    }

    @Test
    void unauthorizedThrowsAuthorizationException() {
        var client = clientReturning("{}", HttpStatus.UNAUTHORIZED);

        assertThatThrownBy(() -> client.fetchWorkItem(KEY))
                .isInstanceOf(AdoClientNonRetryableException.class)
                .hasMessage("Azure DevOps authorization failed.");
    }

    @Test
    void tooManyRequestsThrowsRetryableException() {
        var client = clientReturning("{}", HttpStatus.TOO_MANY_REQUESTS);

        assertThatThrownBy(() -> client.fetchWorkItem(KEY))
                .isInstanceOf(AdoClientRetryableException.class)
                .hasMessage("Azure DevOps read request failed with retryable status 429.");
    }

    @Test
    void patchWorkItemThrowsUnsupportedOperation() {
        var client = clientReturning(workItemJson());

        assertThatThrownBy(() -> client.patchWorkItem(KEY, List.of(new PatchOperation("test", "/rev", 1))))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Azure DevOps PATCH is not implemented yet.");
    }

    @Test
    void createWorkItemCommentThrowsUnsupportedOperation() {
        var client = clientReturning(workItemJson());

        assertThatThrownBy(() -> client.createWorkItemComment(KEY, "comment"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Azure DevOps comments API is not implemented yet.");
    }

    @Test
    void httpClientImplementationDoesNotDependOnWorkflowClasses() {
        assertNoForbiddenTypeReferences("workflow", AzureDevOpsHttpClient.class, AzureDevOpsResponseMapper.class);
        assertNoForbiddenTypeReferences("WorkflowEngine", AzureDevOpsHttpClient.class, AzureDevOpsResponseMapper.class);
    }

    @Test
    void httpClientImplementationDoesNotBuildPatchesOrComments() {
        assertNoForbiddenTypeReferences("PatchBuilder", AzureDevOpsHttpClient.class, AzureDevOpsResponseMapper.class);
        assertNoForbiddenTypeReferences("CommentBuilder", AzureDevOpsHttpClient.class, AzureDevOpsResponseMapper.class);
        assertNoForbiddenTypeReferences("System.History", AzureDevOpsHttpClient.class, AzureDevOpsResponseMapper.class);
    }

    private AzureDevOpsHttpClient clientReturning(String body) {
        return clientReturning(body, HttpStatus.OK);
    }

    private AzureDevOpsHttpClient clientReturning(String body, HttpStatus status) {
        return AzureDevOpsHttpClient.forExchangeFunction(new RecordingExchangeFunction(body, status), "secret-pat");
    }

    private String workItemJson() {
        return """
                {
                  "id": 123,
                  "rev": 27,
                  "fields": {
                    "System.WorkItemType": "Test Case",
                    "System.State": "In Review",
                    "System.Title": "Raw title",
                    "Custom.ApprovedBySME": null
                  }
                }
                """;
    }

    private String revisionJson(String email) {
        var emailProperty = email == null ? "" : ", \"uniqueName\": \"" + email + "\"";
        return """
                {
                  "rev": 26,
                  "fields": {
                    "System.State": "Draft",
                    "System.ChangedBy": {
                      "displayName": "Human User"%s
                    },
                    "Custom.Nullable": null
                  }
                }
                """.formatted(emailProperty);
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

    private static class RecordingExchangeFunction implements ExchangeFunction {

        private final String body;
        private final HttpStatus status;
        private final ArrayList<ClientRequest> requests = new ArrayList<>();

        private RecordingExchangeFunction(String body, HttpStatus status) {
            this.body = body;
            this.status = status;
        }

        @Override
        public Mono<ClientResponse> exchange(ClientRequest request) {
            requests.add(request);
            return Mono.just(ClientResponse.create(status)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(body)
                    .build());
        }
    }
}
