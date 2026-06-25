package com.dentalwings.approvalbot.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.dentalwings.approvalbot.ado.http.AzureDevOpsAuth;
import com.dentalwings.approvalbot.ado.http.AzureDevOpsUrlBuilder;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
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
        var service = discovery(new RecordingExchangeFunction("""
                {"value":[{"name":"Bug"},{"name":"Test Case"}]}
                """, HttpStatus.OK));

        var result = service.listWorkItemTypes("STMN-Group", "Sandbox");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.VALID);
        assertThat(result.values()).containsExactly("Bug", "Test Case");
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

    @Test
    void missingProjectMapsToError() {
        var service = discovery(new RecordingExchangeFunction("{}", HttpStatus.NOT_FOUND));

        var result = service.validateProject("STMN-Group", "Missing Project");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.ERROR);
        assertThat(result.message()).contains("not found");
    }

    @Test
    void retryableAdoFailureMapsToNotChecked() {
        var service = discovery(new RecordingExchangeFunction("{}", HttpStatus.SERVICE_UNAVAILABLE));

        var result = service.listWorkItemTypes("STMN-Group", "Sandbox");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.NOT_CHECKED);
        assertThat(result.message()).contains("retryable status 503");
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
