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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
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
    void urlBuilderEncodesProjectWithSpacesAndDotsExactlyOnce() {
        var key = new AdoWorkItemKey("STMN-Group", "ADOnis 2.0 Test Project", 25193);

        var url = new AzureDevOpsUrlBuilder().workItemUrl(key);

        assertThat(url)
                .isEqualTo("https://dev.azure.com/STMN-Group/ADOnis%202.0%20Test%20Project/_apis/wit/workitems/25193?api-version=7.1")
                .contains("ADOnis%202.0%20Test%20Project")
                .doesNotContain("%2520");
    }

    @Test
    void urlBuilderCreatesCorrectFetchRevisionUrl() {
        var url = new AzureDevOpsUrlBuilder().workItemRevisionUrl(KEY, 27);

        assertThat(url).isEqualTo("https://dev.azure.com/my%20org/Project%20A/_apis/wit/workItems/123/revisions/27?api-version=7.1");
    }

    @Test
    void urlBuilderCreatesCorrectPatchUrl() {
        var url = new AzureDevOpsUrlBuilder().workItemPatchUrl(KEY);

        assertThat(url).isEqualTo("https://dev.azure.com/my%20org/Project%20A/_apis/wit/workitems/123?api-version=7.1");
    }

    @Test
    void urlBuilderCreatesCorrectCommentUrl() {
        var url = new AzureDevOpsUrlBuilder().workItemCommentsUrl(KEY);

        assertThat(url).isEqualTo("https://dev.azure.com/my%20org/Project%20A/_apis/wit/workItems/123/comments?api-version=7.1-preview");
    }

    @Test
    void urlBuilderCreatesCorrectProjectDiscoveryUrl() {
        var url = new AzureDevOpsUrlBuilder().projectUrl("my org", "Project A");

        assertThat(url).isEqualTo("https://dev.azure.com/my%20org/_apis/projects/Project%20A?api-version=7.1");
    }

    @Test
    void urlBuilderCreatesProjectPropertiesDiscoveryUrl() {
        var url = new AzureDevOpsUrlBuilder().projectPropertiesUrl("my org", "11111111-2222-3333-4444-555555555555");

        assertThat(url)
                .isEqualTo("https://dev.azure.com/my%20org/_apis/projects/11111111-2222-3333-4444-555555555555/properties?keys=System.ProcessTemplateType,System.CurrentProcessTemplateId,System.Process%20Template&api-version=7.1-preview.1")
                .contains("System.Process%20Template")
                .doesNotContain("%2520");
    }

    @Test
    void urlBuilderCreatesProcessWorkItemTypesDiscoveryUrl() {
        var url = new AzureDevOpsUrlBuilder().processWorkItemTypesUrl("my org", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

        assertThat(url)
                .isEqualTo("https://dev.azure.com/my%20org/_apis/work/processes/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/workitemtypes?api-version=7.1")
                .doesNotContain("/_apis/wit/workitemtypes")
                .doesNotContain("%2520");
    }

    @Test
    void urlBuilderCreatesCorrectWorkItemTypeFieldsDiscoveryUrl() {
        var url = new AzureDevOpsUrlBuilder().workItemTypeFieldsUrl("my org", "Project A", "Test Case");

        assertThat(url).isEqualTo("https://dev.azure.com/my%20org/Project%20A/_apis/wit/workitemtypes/Test%20Case/fields?api-version=7.1");
    }

    @Test
    void urlBuilderCreatesCorrectWorkItemTypeStatesDiscoveryUrl() {
        var url = new AzureDevOpsUrlBuilder().workItemTypeStatesUrl("my org", "Project A", "Test Case");

        assertThat(url).isEqualTo("https://dev.azure.com/my%20org/Project%20A/_apis/wit/workitemtypes/Test%20Case/states?api-version=7.1");
    }

    @Test
    void urlBuilderCreatesIdentitySearchUrlOnVsspsHost() {
        var url = new AzureDevOpsUrlBuilder().identitySearchUrl("my org", "SME User");

        assertThat(url)
                .isEqualTo("https://vssps.dev.azure.com/my%20org/_apis/identities?searchFilter=General&filterValue=SME%20User&queryMembership=None&api-version=7.1")
                .doesNotContain("%2520");
    }

    @Test
    void projectScopedIdentityUrlsUseOfficialGraphEndpointsAndEncodeOnce() {
        var builder = new AzureDevOpsUrlBuilder();

        assertThat(builder.graphDescriptorUrl("my org", "project id"))
                .isEqualTo("https://vssps.dev.azure.com/my%20org/_apis/graph/descriptors/project%20id?api-version=7.1-preview.1");
        assertThat(builder.scopedGraphUsersUrl("my org", "scp.project descriptor"))
                .isEqualTo("https://vssps.dev.azure.com/my%20org/_apis/graph/users?scopeDescriptor=scp.project%20descriptor&api-version=7.1-preview.1");
        assertThat(builder.graphSubjectQueryUrl("my org"))
                .isEqualTo("https://vssps.dev.azure.com/my%20org/_apis/graph/subjectquery?api-version=7.1-preview.1");
        assertThat(builder.graphAvatarUrl("my org", "aad.user descriptor"))
                .isEqualTo("https://vssps.dev.azure.com/my%20org/_apis/graph/Subjects/aad.user%20descriptor/avatars?size=small&format=png&api-version=7.1");
    }

    @Test
    void urlBuilderRejectsNullWorkItemKeyWithClearMessage() {
        assertThatThrownBy(() -> new AzureDevOpsUrlBuilder().workItemUrl(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Azure DevOps Work Item key must not be null.");
    }

    @Test
    void urlBuilderRejectsNullOrganizationWithClearMessage() {
        var key = new AdoWorkItemKey(null, "Project A", 123);

        assertThatThrownBy(() -> new AzureDevOpsUrlBuilder().workItemUrl(key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Azure DevOps organization must not be blank.");
    }

    @Test
    void urlBuilderRejectsBlankOrganizationWithClearMessage() {
        var key = new AdoWorkItemKey(" ", "Project A", 123);

        assertThatThrownBy(() -> new AzureDevOpsUrlBuilder().workItemUrl(key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Azure DevOps organization must not be blank.");
    }

    @Test
    void urlBuilderRejectsNullProjectWithClearMessage() {
        var key = new AdoWorkItemKey("my org", null, 123);

        assertThatThrownBy(() -> new AzureDevOpsUrlBuilder().workItemUrl(key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Azure DevOps project must not be blank.");
    }

    @Test
    void urlBuilderRejectsBlankProjectWithClearMessage() {
        var key = new AdoWorkItemKey("my org", " ", 123);

        assertThatThrownBy(() -> new AzureDevOpsUrlBuilder().workItemUrl(key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Azure DevOps project must not be blank.");
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
    void fetchRequestForProjectWithSpacesDoesNotDoubleEncodeUrl() {
        var exchange = new RecordingExchangeFunction(workItemJson(), HttpStatus.OK);
        var client = AzureDevOpsHttpClient.forExchangeFunction(exchange, "secret-pat");
        var key = new AdoWorkItemKey("STMN-Group", "ADOnis 2.0 Test Project", 25193);

        client.fetchWorkItem(key);

        assertThat(exchange.requests.getFirst().url().toString())
                .isEqualTo("https://dev.azure.com/STMN-Group/ADOnis%202.0%20Test%20Project/_apis/wit/workitems/25193?api-version=7.1")
                .doesNotContain("%2520");
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
    void patchRequestUsesPatchMethod() {
        var exchange = new RecordingExchangeFunction(workItemJson(), HttpStatus.OK);
        var client = AzureDevOpsHttpClient.forExchangeFunction(exchange, "secret-pat");

        client.patchWorkItem(KEY, validPatchOperations());

        assertThat(exchange.requests.getFirst().method()).isEqualTo(HttpMethod.PATCH);
    }

    @Test
    void patchRequestUsesJsonPatchContentType() {
        var exchange = new RecordingExchangeFunction(workItemJson(), HttpStatus.OK);
        var client = AzureDevOpsHttpClient.forExchangeFunction(exchange, "secret-pat");

        client.patchWorkItem(KEY, validPatchOperations());

        assertThat(exchange.requests.getFirst().headers().getContentType().toString())
                .isEqualTo("application/json-patch+json");
    }

    @Test
    void patchRequestUsesBasicAuth() {
        var exchange = new RecordingExchangeFunction(workItemJson(), HttpStatus.OK);
        var client = AzureDevOpsHttpClient.forExchangeFunction(exchange, "secret-pat");

        client.patchWorkItem(KEY, validPatchOperations());

        assertThat(exchange.requests.getFirst().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo(new AzureDevOpsAuth().basicAuthHeader("secret-pat"));
        assertThat(exchange.requests.getFirst().url().toString()).doesNotContain("secret-pat");
    }

    @Test
    void patchRequestBodyPreservesOperationOrderAndRevisionTestFirst() {
        var exchange = new RecordingExchangeFunction(workItemJson(), HttpStatus.OK);
        var client = AzureDevOpsHttpClient.forExchangeFunction(exchange, "secret-pat");

        client.patchWorkItem(KEY, validPatchOperations());

        assertThat(exchange.requestBodies.getFirst())
                .containsSubsequence(
                        "\"op\":\"test\"",
                        "\"path\":\"/rev\"",
                        "\"value\":27",
                        "\"op\":\"replace\"",
                        "\"path\":\"/fields/System.State\"",
                        "\"value\":\"In Review\"",
                        "\"op\":\"replace\"",
                        "\"path\":\"/fields/Custom.ApprovedBySME\"",
                        "\"value\":null"
                );
    }

    @Test
    void patchRequestBodyPreservesReplaceWithNullValue() {
        var exchange = new RecordingExchangeFunction(workItemJson(), HttpStatus.OK);
        var client = AzureDevOpsHttpClient.forExchangeFunction(exchange, "secret-pat");

        client.patchWorkItem(KEY, validPatchOperations());

        assertThat(exchange.requestBodies.getFirst())
                .contains("{\"op\":\"replace\",\"path\":\"/fields/Custom.ApprovedBySME\",\"value\":null}");
    }

    @Test
    void removePatchOperationIsRejectedAndNotSent() {
        var exchange = new RecordingExchangeFunction(workItemJson(), HttpStatus.OK);
        var client = AzureDevOpsHttpClient.forExchangeFunction(exchange, "secret-pat");

        var result = client.patchWorkItem(KEY, List.of(
                PatchOperation.testRevision(27),
                new PatchOperation("remove", "/fields/System.State", null)
        ));

        assertThat(result.successful()).isFalse();
        assertThat(result.retryable()).isFalse();
        assertThat(result.message()).isEqualTo("Azure DevOps PATCH remove operations are not supported.");
        assertThat(exchange.requests).isEmpty();
    }

    @Test
    void emptyPatchOperationListIsRejected() {
        var exchange = new RecordingExchangeFunction(workItemJson(), HttpStatus.OK);
        var client = AzureDevOpsHttpClient.forExchangeFunction(exchange, "secret-pat");

        var result = client.patchWorkItem(KEY, List.of());

        assertThat(result.successful()).isFalse();
        assertThat(result.retryable()).isFalse();
        assertThat(result.message()).isEqualTo("Azure DevOps PATCH requires at least one operation.");
        assertThat(exchange.requests).isEmpty();
    }

    @Test
    void missingRevisionTestFirstOperationIsRejected() {
        var exchange = new RecordingExchangeFunction(workItemJson(), HttpStatus.OK);
        var client = AzureDevOpsHttpClient.forExchangeFunction(exchange, "secret-pat");

        var result = client.patchWorkItem(KEY, List.of(PatchOperation.replaceField("System.State", "In Review")));

        assertThat(result.successful()).isFalse();
        assertThat(result.retryable()).isFalse();
        assertThat(result.message()).isEqualTo("Azure DevOps PATCH must start with a /rev test operation.");
        assertThat(exchange.requests).isEmpty();
    }

    @Test
    void blankPatchOperationPathIsRejected() {
        var exchange = new RecordingExchangeFunction(workItemJson(), HttpStatus.OK);
        var client = AzureDevOpsHttpClient.forExchangeFunction(exchange, "secret-pat");

        var result = client.patchWorkItem(KEY, List.of(PatchOperation.testRevision(27), new PatchOperation("replace", " ", "x")));

        assertThat(result.successful()).isFalse();
        assertThat(result.retryable()).isFalse();
        assertThat(result.message()).isEqualTo("Azure DevOps PATCH operation path must not be blank.");
        assertThat(exchange.requests).isEmpty();
    }

    @Test
    void okCreatedAndNoContentPatchResponsesMapToSuccess() {
        assertThat(patchResultFor(HttpStatus.OK).successful()).isTrue();
        assertThat(patchResultFor(HttpStatus.CREATED).successful()).isTrue();
        assertThat(patchResultFor(HttpStatus.NO_CONTENT).successful()).isTrue();
    }

    @Test
    void successfulPatchResponseUsesReturnedRevisionWhenPresent() {
        var result = patchResultFor(HttpStatus.OK);

        assertThat(result.revision()).isEqualTo(27);
    }

    @Test
    void badRequestPatchResponseMapsToNonRetryableFailure() {
        var result = patchResultFor(HttpStatus.BAD_REQUEST);

        assertThat(result.successful()).isFalse();
        assertThat(result.retryable()).isFalse();
        assertThat(result.message()).isEqualTo("Azure DevOps PATCH request failed with status 400.");
    }

    @Test
    void patchFailureMessageIncludesSanitizedAdoResponseBody() {
        var result = patchResultFor(
                HttpStatus.BAD_REQUEST,
                """
                        {
                          "message": "VS403654: The field Custom.ApproverTech is invalid.\\nCheck the identity value."
                        }
                        """
        );

        assertThat(result.successful()).isFalse();
        assertThat(result.retryable()).isFalse();
        assertThat(result.message())
                .contains("Azure DevOps PATCH request failed with status 400.")
                .contains("ADO response:")
                .contains("Custom.ApproverTech")
                .contains("Check the identity value.")
                .doesNotContain("\n")
                .doesNotContain("\r");
    }

    @Test
    void patchFailureMessageTruncatesLongAdoResponseBody() {
        var result = patchResultFor(HttpStatus.BAD_REQUEST, "x".repeat(1200));

        assertThat(result.message())
                .hasSizeLessThan(1100)
                .endsWith("...");
    }

    @Test
    void patchFailureMessageDoesNotExposePatchRequestValues() {
        var operations = List.of(
                PatchOperation.testRevision(27),
                PatchOperation.replaceField("Custom.ApproverTech", "SECRET_APPROVER_VALUE")
        );
        var client = clientReturning("""
                {"message":"The field Custom.ApproverTech is invalid."}
                """, HttpStatus.BAD_REQUEST);

        var result = client.patchWorkItem(KEY, operations);

        assertThat(result.message())
                .contains("Custom.ApproverTech")
                .doesNotContain("SECRET_APPROVER_VALUE")
                .doesNotContain("secret-pat");
    }

    @Test
    void authorizationPatchResponsesMapToNonRetryableFailure() {
        assertThat(patchResultFor(HttpStatus.UNAUTHORIZED).retryable()).isFalse();
        assertThat(patchResultFor(HttpStatus.FORBIDDEN).retryable()).isFalse();
    }

    @Test
    void notFoundPatchResponseMapsToNonRetryableFailure() {
        var result = patchResultFor(HttpStatus.NOT_FOUND);

        assertThat(result.successful()).isFalse();
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void conflictPatchResponsesMapToRetryableFailure() {
        assertThat(patchResultFor(HttpStatus.CONFLICT).retryable()).isTrue();
        assertThat(patchResultFor(HttpStatus.PRECONDITION_FAILED).retryable()).isTrue();
    }

    @Test
    void rateLimitedPatchResponseMapsToRetryableFailure() {
        var result = patchResultFor(HttpStatus.TOO_MANY_REQUESTS);

        assertThat(result.successful()).isFalse();
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void serverErrorPatchResponsesMapToRetryableFailure() {
        assertThat(patchResultFor(HttpStatus.INTERNAL_SERVER_ERROR).retryable()).isTrue();
        assertThat(patchResultFor(HttpStatus.BAD_GATEWAY).retryable()).isTrue();
        assertThat(patchResultFor(HttpStatus.SERVICE_UNAVAILABLE).retryable()).isTrue();
        assertThat(patchResultFor(HttpStatus.GATEWAY_TIMEOUT).retryable()).isTrue();
    }

    @Test
    void transportPatchErrorMapsToRetryableFailure() {
        var client = AzureDevOpsHttpClient.forExchangeFunction(
                request -> Mono.error(new RuntimeException("timeout secret-pat Custom.ApprovedBySME")),
                "secret-pat"
        );

        var result = client.patchWorkItem(KEY, validPatchOperations());

        assertThat(result.successful()).isFalse();
        assertThat(result.retryable()).isTrue();
        assertThat(result.message())
                .isEqualTo("Azure DevOps PATCH request failed with retryable transport error.")
                .doesNotContain("secret-pat")
                .doesNotContain("Custom.ApprovedBySME");
    }

    @Test
    void retryablePatchFailureMessageDoesNotExposePatOrPayload() {
        var result = patchResultFor(HttpStatus.CONFLICT);

        assertThat(result.message())
                .doesNotContain("secret-pat")
                .doesNotContain("Custom.ApprovedBySME")
                .doesNotContain("In Review");
    }

    @Test
    void commentRequestUsesPostMethod() {
        var exchange = new RecordingExchangeFunction(commentJson("42"), HttpStatus.CREATED);
        var client = AzureDevOpsHttpClient.forExchangeFunction(exchange, "secret-pat");

        client.createWorkItemComment(KEY, "comment");

        assertThat(exchange.requests.getFirst().method()).isEqualTo(HttpMethod.POST);
    }

    @Test
    void commentRequestUsesJsonContentType() {
        var exchange = new RecordingExchangeFunction(commentJson("42"), HttpStatus.CREATED);
        var client = AzureDevOpsHttpClient.forExchangeFunction(exchange, "secret-pat");

        client.createWorkItemComment(KEY, "comment");

        assertThat(exchange.requests.getFirst().headers().getContentType()).isEqualTo(org.springframework.http.MediaType.APPLICATION_JSON);
    }

    @Test
    void commentRequestUsesBasicAuth() {
        var exchange = new RecordingExchangeFunction(commentJson("42"), HttpStatus.CREATED);
        var client = AzureDevOpsHttpClient.forExchangeFunction(exchange, "secret-pat");

        client.createWorkItemComment(KEY, "comment");

        assertThat(exchange.requests.getFirst().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo(new AzureDevOpsAuth().basicAuthHeader("secret-pat"));
        assertThat(exchange.requests.getFirst().url().toString()).doesNotContain("secret-pat");
    }

    @Test
    void commentRequestBodyUsesCommentsApiTextPayload() {
        var exchange = new RecordingExchangeFunction(commentJson("42"), HttpStatus.CREATED);
        var client = AzureDevOpsHttpClient.forExchangeFunction(exchange, "secret-pat");

        client.createWorkItemComment(KEY, "comment body");

        assertThat(exchange.requestBodies.getFirst()).isEqualTo("{\"text\":\"comment body\"}");
    }

    @Test
    void commentRequestBodyPreservesMultilineTextExactly() {
        var exchange = new RecordingExchangeFunction(commentJson("42"), HttpStatus.CREATED);
        var client = AzureDevOpsHttpClient.forExchangeFunction(exchange, "secret-pat");
        var comment = "Line one\n\nLine two with /commands and [links](https://example.com)";

        client.createWorkItemComment(KEY, comment);

        assertThat(exchange.requestBodies.getFirst())
                .isEqualTo("{\"text\":\"Line one\\n\\nLine two with /commands and [links](https://example.com)\"}");
    }

    @Test
    void nullCommentTextIsRejectedBeforeSending() {
        var exchange = new RecordingExchangeFunction(commentJson("42"), HttpStatus.CREATED);
        var client = AzureDevOpsHttpClient.forExchangeFunction(exchange, "secret-pat");

        var result = client.createWorkItemComment(KEY, null);

        assertThat(result.successful()).isFalse();
        assertThat(result.message()).isEqualTo("Azure DevOps comment text must not be blank.");
        assertThat(exchange.requests).isEmpty();
    }

    @Test
    void blankCommentTextIsRejectedBeforeSending() {
        var exchange = new RecordingExchangeFunction(commentJson("42"), HttpStatus.CREATED);
        var client = AzureDevOpsHttpClient.forExchangeFunction(exchange, "secret-pat");

        var result = client.createWorkItemComment(KEY, " \n\t ");

        assertThat(result.successful()).isFalse();
        assertThat(result.message()).isEqualTo("Azure DevOps comment text must not be blank.");
        assertThat(exchange.requests).isEmpty();
    }

    @Test
    void commentRequestDoesNotUseJsonPatchOrSystemHistory() {
        var exchange = new RecordingExchangeFunction(commentJson("42"), HttpStatus.CREATED);
        var client = AzureDevOpsHttpClient.forExchangeFunction(exchange, "secret-pat");

        client.createWorkItemComment(KEY, "comment");

        assertThat(exchange.requests.getFirst().method()).isEqualTo(HttpMethod.POST);
        assertThat(exchange.requests.getFirst().headers().getContentType()).isEqualTo(org.springframework.http.MediaType.APPLICATION_JSON);
        assertThat(exchange.requests.getFirst().url().toString()).doesNotContain("System.History");
        assertThat(exchange.requestBodies.getFirst())
                .doesNotContain("\"op\"")
                .doesNotContain("/fields/System.History")
                .doesNotContain("System.History");
    }

    @Test
    void okAndCreatedCommentResponsesMapToSuccess() {
        assertThat(commentResultFor(HttpStatus.OK).successful()).isTrue();
        assertThat(commentResultFor(HttpStatus.CREATED).successful()).isTrue();
    }

    @Test
    void successfulCommentResponseUsesReturnedCommentId() {
        var result = commentResultFor(HttpStatus.CREATED);

        assertThat(result.commentId()).isEqualTo("42");
    }

    @Test
    void badRequestCommentResponseMapsToFailure() {
        var result = commentResultFor(HttpStatus.BAD_REQUEST);

        assertThat(result.successful()).isFalse();
        assertThat(result.message()).isEqualTo("Azure DevOps comment request failed with status 400.");
    }

    @Test
    void commentFailureMessageIncludesSanitizedAdoResponseBody() {
        var result = commentResultFor(
                HttpStatus.BAD_REQUEST,
                """
                        {
                          "message": "TF401232: The comment payload is invalid.\\nCheck api-version."
                        }
                        """
        );

        assertThat(result.successful()).isFalse();
        assertThat(result.message())
                .contains("Azure DevOps comment request failed with status 400.")
                .contains("ADO response:")
                .contains("comment payload is invalid")
                .contains("Check api-version.")
                .doesNotContain("\n")
                .doesNotContain("\r");
    }

    @Test
    void commentFailureMessageTruncatesLongAdoResponseBody() {
        var result = commentResultFor(HttpStatus.BAD_REQUEST, "x".repeat(1200));

        assertThat(result.message())
                .hasSizeLessThan(1100)
                .endsWith("...");
    }

    @Test
    void authorizationCommentResponsesMapToFailure() {
        assertThat(commentResultFor(HttpStatus.UNAUTHORIZED).successful()).isFalse();
        assertThat(commentResultFor(HttpStatus.FORBIDDEN).successful()).isFalse();
    }

    @Test
    void notFoundCommentResponseMapsToFailure() {
        assertThat(commentResultFor(HttpStatus.NOT_FOUND).successful()).isFalse();
    }

    @Test
    void conflictCommentResponsesMapToFailure() {
        assertThat(commentResultFor(HttpStatus.CONFLICT).successful()).isFalse();
        assertThat(commentResultFor(HttpStatus.PRECONDITION_FAILED).successful()).isFalse();
    }

    @Test
    void rateLimitedCommentResponseMapsToFailure() {
        assertThat(commentResultFor(HttpStatus.TOO_MANY_REQUESTS).successful()).isFalse();
    }

    @Test
    void serverErrorCommentResponsesMapToFailure() {
        assertThat(commentResultFor(HttpStatus.INTERNAL_SERVER_ERROR).successful()).isFalse();
        assertThat(commentResultFor(HttpStatus.BAD_GATEWAY).successful()).isFalse();
        assertThat(commentResultFor(HttpStatus.SERVICE_UNAVAILABLE).successful()).isFalse();
        assertThat(commentResultFor(HttpStatus.GATEWAY_TIMEOUT).successful()).isFalse();
    }

    @Test
    void transportCommentErrorMapsToFailureWithoutExposingPatOrText() {
        var client = AzureDevOpsHttpClient.forExchangeFunction(
                request -> Mono.error(new RuntimeException("timeout secret-pat private comment")),
                "secret-pat"
        );

        var result = client.createWorkItemComment(KEY, "private comment");

        assertThat(result.successful()).isFalse();
        assertThat(result.message())
                .isEqualTo("Azure DevOps comment request failed with transport error.")
                .doesNotContain("secret-pat")
                .doesNotContain("private comment");
    }

    @Test
    void commentFailureMessageDoesNotExposePatOrText() {
        var client = clientReturning("""
                {"message":"The comment request body was invalid."}
                """, HttpStatus.INTERNAL_SERVER_ERROR);

        var result = client.createWorkItemComment(KEY, "private comment body");

        assertThat(result.message())
                .contains("comment request body")
                .doesNotContain("secret-pat")
                .doesNotContain("private comment body");
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

    private List<PatchOperation> validPatchOperations() {
        return List.of(
                PatchOperation.testRevision(27),
                PatchOperation.replaceField("System.State", "In Review"),
                PatchOperation.replaceField("Custom.ApprovedBySME", null)
        );
    }

    private com.dentalwings.approvalbot.ado.AdoPatchResult patchResultFor(HttpStatus status) {
        return patchResultFor(status, status.is2xxSuccessful() ? workItemJson() : "");
    }

    private com.dentalwings.approvalbot.ado.AdoPatchResult patchResultFor(HttpStatus status, String body) {
        var client = clientReturning(body, status);

        return client.patchWorkItem(KEY, validPatchOperations());
    }

    private com.dentalwings.approvalbot.ado.AdoCommentResult commentResultFor(HttpStatus status) {
        return commentResultFor(status, status.is2xxSuccessful() ? commentJson("42") : "");
    }

    private com.dentalwings.approvalbot.ado.AdoCommentResult commentResultFor(HttpStatus status, String body) {
        var client = clientReturning(body, status);

        return client.createWorkItemComment(KEY, "comment body");
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

    private String commentJson(String id) {
        return """
                {
                  "id": "%s"
                }
                """.formatted(id);
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
        private final ArrayList<String> requestBodies = new ArrayList<>();

        private RecordingExchangeFunction(String body, HttpStatus status) {
            this.body = body;
            this.status = status;
        }

        @Override
        public Mono<ClientResponse> exchange(ClientRequest request) {
            requests.add(request);
            requestBodies.add(captureBody(request));
            return Mono.just(ClientResponse.create(status)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(body)
                    .build());
        }

        private String captureBody(ClientRequest request) {
            var mockRequest = new MockClientHttpRequest(request.method(), request.url());
            mockRequest.getHeaders().putAll(request.headers());
            request.body().insert(mockRequest, new BodyInserterContext()).block();
            return mockRequest.getBodyAsString().block();
        }
    }

    private static class BodyInserterContext implements BodyInserter.Context {

        @Override
        public List<org.springframework.http.codec.HttpMessageWriter<?>> messageWriters() {
            return ExchangeStrategies.withDefaults().messageWriters();
        }

        @Override
        public java.util.Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest() {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Map<String, Object> hints() {
            return java.util.Map.of();
        }
    }
}
