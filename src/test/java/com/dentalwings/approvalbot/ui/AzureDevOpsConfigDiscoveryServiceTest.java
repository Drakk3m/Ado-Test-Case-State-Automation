package com.dentalwings.approvalbot.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.dentalwings.approvalbot.ado.http.AzureDevOpsAuth;
import com.dentalwings.approvalbot.ado.http.AzureDevOpsUrlBuilder;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

class AzureDevOpsConfigDiscoveryServiceTest {

    @Test
    void validateProjectUsesReadOnlyProjectEndpointAndBasicAuthWithoutExposingPatInUrl() {
        var exchange = new RecordingExchangeFunction("""
                {"name":"ADOnis 2.0 Test Project"}
                """, HttpStatus.OK);
        var service = discovery(exchange);

        var result = service.validateProject("STMN-Group", "ADOnis 2.0 Test Project");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.VALID);
        assertThat(result.values()).containsExactly("ADOnis 2.0 Test Project");
        assertThat(exchange.requests.getFirst().url().toString())
                .isEqualTo("https://dev.azure.com/STMN-Group/_apis/projects/ADOnis%202.0%20Test%20Project?api-version=7.1")
                .doesNotContain("secret-pat");
        assertThat(exchange.requests.getFirst().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo(new AzureDevOpsAuth().basicAuthHeader("secret-pat"));
    }

    @Test
    void listWorkItemTypesReturnsNamesFromAdoResponse() {
        var exchange = new RecordingExchangeFunction("""
                {"count":2,"value":[{"name":"Bug"},{"name":"Test Case"}]}
                """, HttpStatus.OK);
        var service = discovery(exchange);

        var result = service.listWorkItemTypes("STMN-Group", "ADOnis 2.0 Test Project");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.VALID);
        assertThat(result.values()).containsExactly("Bug", "Test Case");
        assertThat(exchange.requests.getFirst().url().toString())
                .isEqualTo("https://dev.azure.com/STMN-Group/ADOnis%202.0%20Test%20Project/_apis/wit/workitemtypes?$expand=None&api-version=7.1")
                .contains("$expand=None")
                .contains("ADOnis%202.0%20Test%20Project")
                .doesNotContain("%24expand", "%2520");
    }

    @Test
    void listWorkItemTypeOptionsMapsAdoValueArrayToSelectorOptions() {
        var exchange = new RecordingExchangeFunction("""
                {"count":1,"values":[{"name":"Wrong"}],"value":[{"name":"Test Case"}]}
                """, HttpStatus.OK);
        var service = discovery(exchange);

        var result = service.listWorkItemTypeOptions("STMN-Group", "ADOnis 2.0 Test Project");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.VALID);
        assertThat(result.optionCount()).isEqualTo(1);
        assertThat(result.values()).singleElement()
                .satisfies(option -> {
                    assertThat(option.value()).isEqualTo("Test Case");
                    assertThat(option.displayName()).isEqualTo("Test Case");
                    assertThat(option.source()).isEqualTo("ADO");
                });
        assertThat(exchange.requests).hasSize(1);
        assertThat(exchange.requests.getFirst().url().toString())
                .contains("/_apis/wit/workitemtypes?$expand=None&api-version=7.1")
                .doesNotContain("/fields");
    }

    @Test
    void emptyAdoDiscoveryResponseReturnsWarningWithOptionCountZero() {
        var service = discovery(new RecordingExchangeFunction("""
                {"value":[]}
                """, HttpStatus.OK));

        var result = service.listWorkItemTypes("STMN-Group", "Sandbox");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.WARNING);
        assertThat(result.optionCount()).isZero();
        assertThat(result.message()).contains("no options");
    }

    @Test
    void listFieldReferenceNamesReturnsReferenceNamesFromAdoResponse() {
        var service = discovery(new RecordingExchangeFunction("""
                {"value":[{"referenceName":"System.Title"},{"referenceName":"Custom.ApproverTech"}]}
                """, HttpStatus.OK));

        var result = service.listFieldReferenceNames("STMN-Group", "Sandbox", "Test Case");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.VALID);
        assertThat(result.values()).containsExactly("System.Title", "Custom.ApproverTech");
    }

    @Test
    void listFieldOptionsUsesReferenceNameAsValueAndFriendlyNameAsDisplayOnly() {
        var service = discovery(new RecordingExchangeFunction("""
                {"value":[{"name":"Approver Tech","referenceName":"Custom.ApproverTech","type":"identity"}]}
                """, HttpStatus.OK));

        var result = service.listFieldOptions("STMN-Group", "Sandbox", "Test Case");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.VALID);
        assertThat(result.values()).singleElement()
                .satisfies(option -> {
                    assertThat(option.value()).isEqualTo("Custom.ApproverTech");
                    assertThat(option.displayName()).isEqualTo("Approver Tech");
                    assertThat(option.description()).isEqualTo("identity");
                });
    }

    @Test
    void listObservedStateNamesReturnsStateNamesFromAdoResponse() {
        var service = discovery(new RecordingExchangeFunction("""
                {"value":[{"name":"Design"},{"name":"In Review"},{"name":"Approval"}]}
                """, HttpStatus.OK));

        var result = service.listObservedStateNames("STMN-Group", "Sandbox", "Test Case");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.VALID);
        assertThat(result.values()).containsExactly("Design", "In Review", "Approval");
    }

    @Test
    void missingPatReturnsBlockingErrorBeforeSendingRequest() {
        var exchange = new RecordingExchangeFunction("{}", HttpStatus.OK);
        var service = new AzureDevOpsConfigDiscoveryService("", exchange, new AzureDevOpsUrlBuilder());

        var result = service.listFieldReferenceNames("STMN-Group", "Sandbox", "Test Case");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.ERROR);
        assertThat(result.message()).contains("ADO_PERSONAL_ACCESS_TOKEN").doesNotContain("secret-pat");
        assertThat(exchange.requests).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "BAD_REQUEST,failed with status 400",
            "FORBIDDEN,authorization failed",
            "NOT_FOUND,not found"
    })
    void adoHttpDiscoveryFailuresMapToError(HttpStatus status, String expectedMessage) {
        var service = discovery(new RecordingExchangeFunction("""
                {"message":"The project or work item type could not be discovered."}
                """, status));

        var result = service.validateProject("STMN-Group", "ADOnis 2.0 Test Project");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.ERROR);
        assertThat(result.message()).contains(expectedMessage);
        assertThat(result.message()).contains("project or work item type").doesNotContain("secret-pat");
    }

    @Test
    void retryableAdoFailureMapsToErrorBecauseDiscoveryWasAttempted() {
        var service = discovery(new RecordingExchangeFunction("""
                {"message":"ADO is temporarily unavailable."}
                """, HttpStatus.SERVICE_UNAVAILABLE));

        var result = service.listWorkItemTypes("STMN-Group", "Sandbox");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.ERROR);
        assertThat(result.message()).contains("retryable status 503");
    }

    @Test
    void transportFailureMapsToSanitizedErrorBecauseDiscoveryWasAttempted() {
        var service = new AzureDevOpsConfigDiscoveryService(
                "secret-pat",
                request -> Mono.error(new IllegalStateException("connection failed with secret-pat")),
                new AzureDevOpsUrlBuilder()
        );

        var result = service.listWorkItemTypes("STMN-Group", "Sandbox");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.ERROR);
        assertThat(result.message())
                .contains("transport error")
                .contains("IllegalStateException")
                .doesNotContain("secret-pat");
    }

    @Test
    void dataBufferLimitFailureMapsToErrorBecauseDiscoveryWasAttempted() {
        var service = new AzureDevOpsConfigDiscoveryService(
                "secret-pat",
                request -> Mono.error(new DataBufferLimitException("Exceeded limit on max bytes to buffer : 262144")),
                new AzureDevOpsUrlBuilder()
        );

        var result = service.listWorkItemTypeOptions("STMN-Group", "ADOnis 2.0 Test Project");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.ERROR);
        assertThat(result.message())
                .contains("transport error")
                .contains("DataBufferLimitException")
                .contains("262144")
                .doesNotContain("secret-pat");
    }

    @Test
    void userResolutionIsWarningBecauseGraphLookupIsNotImplemented() {
        var service = discovery(new RecordingExchangeFunction("{}", HttpStatus.OK));

        var result = service.resolveUsers("STMN-Group", java.util.List.of("sme@example.test"));

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.WARNING);
        assertThat(result.message()).contains("email/login");
    }

    private AzureDevOpsConfigDiscoveryService discovery(RecordingExchangeFunction exchange) {
        return new AzureDevOpsConfigDiscoveryService("secret-pat", exchange, new AzureDevOpsUrlBuilder());
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
