package com.dentalwings.approvalbot.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.dentalwings.approvalbot.ado.http.AzureDevOpsAuth;
import com.dentalwings.approvalbot.ado.http.AzureDevOpsUrlBuilder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
                {"id":"project-id-1","name":"ADOnis 2.0 Test Project"}
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
        var exchange = processDiscoveryExchange("""
                {"count":2,"value":[{"name":"Bug","referenceName":"Microsoft.VSTS.WorkItemTypes.Bug"},{"name":"Test Case","referenceName":"Microsoft.VSTS.WorkItemTypes.TestCase"}]}
                """, HttpStatus.OK);
        var service = discovery(exchange);

        var result = service.listWorkItemTypes("STMN-Group", "ADOnis 2.0 Test Project");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.VALID);
        assertThat(result.values()).containsExactly("Bug", "Test Case");
        assertThat(exchange.requests).hasSize(3);
        assertThat(exchange.requests.get(0).url().toString())
                .isEqualTo("https://dev.azure.com/STMN-Group/_apis/projects/ADOnis%202.0%20Test%20Project?api-version=7.1")
                .contains("ADOnis%202.0%20Test%20Project")
                .doesNotContain("%24expand", "%2520");
        assertThat(exchange.requests.get(1).url().toString())
                .isEqualTo("https://dev.azure.com/STMN-Group/_apis/projects/project-id-1/properties?keys=System.ProcessTemplateType,System.CurrentProcessTemplateId,System.Process%20Template&api-version=7.1-preview.1");
        assertThat(exchange.requests.get(2).url().toString())
                .isEqualTo("https://dev.azure.com/STMN-Group/_apis/work/processes/process-id-1/workitemtypes?api-version=7.1")
                .doesNotContain("/_apis/wit/workitemtypes");
    }

    @Test
    void listWorkItemTypeOptionsMapsAdoValueArrayToSelectorOptions() {
        var exchange = processDiscoveryExchange("""
                {"count":1,"values":[{"name":"Wrong"}],"value":[{"name":"Test Case","referenceName":"Microsoft.VSTS.WorkItemTypes.TestCase","description":"Test validation case"}]}
                """, HttpStatus.OK);
        var service = discovery(exchange);

        var result = service.listWorkItemTypeOptions("STMN-Group", "ADOnis 2.0 Test Project");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.VALID);
        assertThat(result.optionCount()).isEqualTo(1);
        assertThat(result.values()).singleElement()
                .satisfies(option -> {
                    assertThat(option.value()).isEqualTo("Test Case");
                    assertThat(option.displayName()).isEqualTo("Test Case");
                    assertThat(option.referenceName()).isEqualTo("Microsoft.VSTS.WorkItemTypes.TestCase");
                    assertThat(option.description()).isEqualTo("Test validation case");
                    assertThat(option.source()).isEqualTo("ADO");
                });
        assertThat(exchange.requests).hasSize(3);
        assertThat(exchange.requests)
                .extracting(request -> request.url().toString())
                .noneMatch(url -> url.contains("/_apis/wit/workitemtypes?"))
                .noneMatch(url -> url.contains("/fields"));
    }

    @Test
    void listProjectOptionsUsesProjectNameOnlyForNormalVisibleLabel() {
        var exchange = new RecordingExchangeFunction("""
                {"value":[{"id":"project-guid-1","name":"ADOnis 2.0 Test Project"}]}
                """, HttpStatus.OK);
        var service = discovery(exchange);

        var result = service.listProjectOptions("STMN-Group");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.VALID);
        assertThat(result.values()).singleElement()
                .satisfies(option -> {
                    assertThat(option.value()).isEqualTo("ADOnis 2.0 Test Project");
                    assertThat(option.displayName()).isEqualTo("ADOnis 2.0 Test Project");
                    assertThat(option.description()).isEmpty();
                    assertThat(option.referenceName()).isEmpty();
                });
    }

    @Test
    void emptyAdoDiscoveryResponseReturnsWarningWithOptionCountZero() {
        var service = discovery(processDiscoveryExchange("""
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
        var service = discovery(processDiscoveryExchange("""
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
                request -> {
                    if (request.url().toString().contains("/_apis/work/processes/")) {
                        return Mono.error(new DataBufferLimitException("Exceeded limit on max bytes to buffer : 262144"));
                    }
                    if (request.url().toString().contains("/properties?")) {
                        return Mono.just(ClientResponse.create(HttpStatus.OK)
                                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                                .body("""
                                        {"value":[{"name":"System.CurrentProcessTemplateId","value":"process-id-1"}]}
                                        """)
                                .build());
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "application/json")
                            .body("""
                                    {"id":"project-id-1","name":"ADOnis 2.0 Test Project"}
                                    """)
                            .build());
                },
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
    void processDiscoveryTriesCandidateProcessPropertiesDeterministically() {
        var exchange = new RecordingExchangeFunction(List.of(
                new RecordedResponse("""
                        {"id":"project-id-1","name":"ADOnis 2.0 Test Project"}
                        """, HttpStatus.OK),
                new RecordedResponse("""
                        {"value":[
                            {"name":"System.CurrentProcessTemplateId","value":"bad-process-id"},
                            {"name":"System.ProcessTemplateType","value":"process-id-1"}
                        ]}
                        """, HttpStatus.OK),
                new RecordedResponse("""
                        {"message":"not a process"}
                        """, HttpStatus.NOT_FOUND),
                new RecordedResponse("""
                        {"value":[{"name":"Test Case"}]}
                        """, HttpStatus.OK)
        ));
        var service = discovery(exchange);

        var result = service.listWorkItemTypeOptions("STMN-Group", "ADOnis 2.0 Test Project");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.VALID);
        assertThat(result.values()).extracting(ConfigSelectorOption::value).containsExactly("Test Case");
        assertThat(exchange.requests.get(2).url().toString())
                .contains("/_apis/work/processes/bad-process-id/workitemtypes?api-version=7.1");
        assertThat(exchange.requests.get(3).url().toString())
                .contains("/_apis/work/processes/process-id-1/workitemtypes?api-version=7.1");
    }

    @ParameterizedTest
    @CsvSource({
            "BAD_REQUEST,failed with status 400",
            "FORBIDDEN,authorization failed",
            "NOT_FOUND,not found"
    })
    void projectPropertiesFailuresMapToError(HttpStatus status, String expectedMessage) {
        var exchange = new RecordingExchangeFunction(List.of(
                new RecordedResponse("""
                        {"id":"project-id-1","name":"ADOnis 2.0 Test Project"}
                        """, HttpStatus.OK),
                new RecordedResponse("""
                        {"message":"The process properties could not be discovered."}
                        """, status)
        ));
        var service = discovery(exchange);

        var result = service.listWorkItemTypeOptions("STMN-Group", "ADOnis 2.0 Test Project");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.ERROR);
        assertThat(result.message()).contains(expectedMessage);
        assertThat(exchange.requests.get(1).url().toString()).contains("/properties?");
    }

    @ParameterizedTest
    @CsvSource({
            "BAD_REQUEST,failed with status 400",
            "FORBIDDEN,authorization failed",
            "NOT_FOUND,not found"
    })
    void processWorkItemTypeFailuresMapToError(HttpStatus status, String expectedMessage) {
        var service = discovery(processDiscoveryExchange("""
                {"message":"The process work item types could not be discovered."}
                """, status));

        var result = service.listWorkItemTypeOptions("STMN-Group", "ADOnis 2.0 Test Project");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.ERROR);
        assertThat(result.message()).contains(expectedMessage);
    }

    @Test
    void userResolutionIsWarningBecauseGraphLookupIsNotImplemented() {
        var service = discovery(new RecordingExchangeFunction("{}", HttpStatus.OK));

        var result = service.resolveUsers("STMN-Group", java.util.List.of("sme@example.test"));

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.ERROR);
        assertThat(result.message()).contains("not resolved");
    }

    @Test
    void identitySearchMapsUniqueNameToSelectableNormalizedOption() {
        var exchange = new RecordingExchangeFunction("""
                {"count":1,"value":[{"displayName":"SME Sandbox","uniqueName":"SME@Example.Test","subjectDescriptor":"aad.user-1"}]}
                """, HttpStatus.OK);
        var service = discovery(exchange);

        var result = service.searchIdentityOptions("STMN-Group", "sme");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.VALID);
        assertThat(result.values()).singleElement()
                .satisfies(option -> {
                    assertThat(option.value()).isEqualTo("sme@example.test");
                    assertThat(option.displayName()).contains("SME Sandbox").contains("sme@example.test");
                    assertThat(option.description()).isEqualTo("sme@example.test");
                    assertThat(option.referenceName()).isEqualTo("aad.user-1");
                });
        assertThat(exchange.requests.getFirst().url().toString())
                .contains("filterValue=sme")
                .contains("vssps.dev.azure.com");
    }

    @Test
    void identitySearchFallsBackToMailPropertyAndNeverUsesDisplayNameAsValue() {
        var service = discovery(new RecordingExchangeFunction("""
                {"identities":[{"displayName":"Display Only","properties":{"Mail":{"$value":"mail@example.test"}}}]}
                """, HttpStatus.OK));

        var result = service.searchIdentityOptions("STMN-Group", "Display");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.VALID);
        assertThat(result.values()).singleElement()
                .satisfies(option -> {
                    assertThat(option.value()).isEqualTo("mail@example.test");
                    assertThat(option.displayName()).contains("Display Only");
                    assertThat(option.value()).isNotEqualTo("Display Only");
                });
    }

    @Test
    void identityWithoutEmailLoginOrUniqueNameIsNotSelectable() {
        var service = discovery(new RecordingExchangeFunction("""
                {"count":1,"value":[{"displayName":"Display Only"}]}
                """, HttpStatus.OK));

        var result = service.searchIdentityOptions("STMN-Group", "Display");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.WARNING);
        assertThat(result.optionCount()).isZero();
    }

    @Test
    void resolveUsersReturnsNormalizedEmailsOnlyWhenAdoSearchMatches() {
        var exchange = new RecordingExchangeFunction("""
                {"value":[{"displayName":"SME Sandbox","uniqueName":"SME@Example.Test"}]}
                """, HttpStatus.OK);
        var service = discovery(exchange);

        var result = service.resolveUsers("STMN-Group", java.util.List.of("SME@Example.Test"));

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.VALID);
        assertThat(result.values()).containsExactly("sme@example.test");
    }

    @Test
    void shortIdentitySearchIsNotCheckedAndDoesNotCallAdo() {
        var exchange = new RecordingExchangeFunction("{}", HttpStatus.OK);
        var service = discovery(exchange);

        var result = service.searchIdentityOptions("STMN-Group", "ab");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.NOT_CHECKED);
        assertThat(result.message()).contains("3 characters");
        assertThat(exchange.requests).isEmpty();
    }

    @Test
    void repeatedNormalizedIdentitySearchUsesBackendCacheWithoutCallingAdoAgain() {
        var exchange = new RecordingExchangeFunction("""
                {"value":[{"displayName":"Rene Example","uniqueName":"rene@example.test"}]}
                """, HttpStatus.OK);
        var service = discovery(exchange);

        var first = service.searchIdentityOptions("STMN-Group", " Rene ");
        var second = service.searchIdentityOptions("stmn-group", "RENE");

        assertThat(first.diagnostics()).containsEntry("backendCacheMiss", true);
        assertThat(second.diagnostics())
                .containsEntry("backendCacheHit", true)
                .containsEntry("adoRequestCount", 1L);
        assertThat(exchange.requests).hasSize(1);
        assertThat(second.values()).extracting(ConfigSelectorOption::value).containsExactly("rene@example.test");
    }

    @Test
    void identitySearchCacheSeparatesOrganizations() {
        var exchange = new RecordingExchangeFunction("""
                {"value":[{"displayName":"Rene Example","uniqueName":"rene@example.test"}]}
                """, HttpStatus.OK);
        var service = discovery(exchange);

        service.searchIdentityOptions("Organization-A", "rene");
        service.searchIdentityOptions("Organization-B", "rene");

        assertThat(exchange.requests).hasSize(2);
    }

    @Test
    void expiredIdentitySearchCacheCausesNewAdoRequest() {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var cache = new IdentitySearchResultCache(Duration.ofMinutes(5), 10, clock);
        var exchange = new RecordingExchangeFunction("""
                {"value":[{"displayName":"Rene Example","uniqueName":"rene@example.test"}]}
                """, HttpStatus.OK);
        var service = new AzureDevOpsConfigDiscoveryService(
                "secret-pat",
                exchange,
                new AzureDevOpsUrlBuilder(),
                cache
        );

        service.searchIdentityOptions("STMN-Group", "rene");
        clock.advance(Duration.ofMinutes(6));
        var refreshed = service.searchIdentityOptions("STMN-Group", "rene");

        assertThat(exchange.requests).hasSize(2);
        assertThat(refreshed.diagnostics()).containsEntry("backendCacheMiss", true);
    }

    @Test
    void identitySearchCacheStoresOnlyNormalizedSelectorOptions() {
        var cache = new IdentitySearchResultCache(Duration.ofMinutes(5), 10, Clock.systemUTC());
        cache.put("STMN-Group", "Sandbox", "rene", ConfigLookupResult.valid(List.of(
                new ConfigSelectorOption("rene@example.test", "Rene Example", "rene@example.test", "ADO", "aad.user-1")
        )));

        var cached = cache.get("STMN-Group", "Sandbox", "rene").orElseThrow().toResult();

        assertThat(cached.values()).containsExactly(
                new ConfigSelectorOption("rene@example.test", "Rene Example", "rene@example.test", "ADO")
        );
        assertThat(cached.values().getFirst().referenceName()).isEmpty();
        assertThat(cached.diagnostics()).isEmpty();
    }

    @Test
    void projectScopedIdentitySearchUsesContainsMatchingAndReturnsAvatarProxyUrls() {
        var exchange = projectIdentityExchange("""
                {"count":3,"value":[
                  {"descriptor":"aad.rene-1","displayName":"Rene Alpha","principalName":"rene.alpha@example.test"},
                  {"descriptor":"aad.rene-2","displayName":"Irene Beta","mailAddress":"irene.beta@example.test"},
                  {"descriptor":"aad.rene-3","displayName":"Serene Gamma","principalName":"serene.gamma@example.test"}
                ]}
                """);
        var service = discovery(exchange);

        var result = service.searchIdentityOptions("STMN-Group", "ADOnis 2.0 Test Project", "rene");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.VALID);
        assertThat(result.values()).hasSize(3).allSatisfy(option -> {
            assertThat(option.resolved()).isTrue();
            assertThat(option.value()).endsWith("@example.test");
            assertThat(option.avatarUrl())
                    .startsWith("/api/config-ui/discovery/users/avatar?organization=STMN-Group&descriptor=aad.");
        });
        assertThat(exchange.requests).hasSize(3);
        assertThat(exchange.requests.get(1).url().toString()).contains("/_apis/graph/descriptors/project-id-1");
        assertThat(exchange.requests.get(2).url().toString()).contains("scopeDescriptor=scp.project-1");
    }

    @Test
    void projectCandidateCacheSupportsNewQueriesWithoutMoreAdoCalls() {
        var exchange = projectIdentityExchange("""
                {"count":3,"value":[
                  {"descriptor":"aad.1","displayName":"Rene Example One","principalName":"rene.one@example.test"},
                  {"descriptor":"aad.2","displayName":"Rene Example Two","principalName":"rene.two@example.test"},
                  {"descriptor":"aad.3","displayName":"Rene Example Three","principalName":"rene.three@example.test"}
                ]}
                """);
        var service = discovery(exchange);

        service.searchIdentityOptions("STMN-Group", "Sandbox", "rene");
        var second = service.searchIdentityOptions("STMN-Group", "Sandbox", "example");

        assertThat(exchange.requests).hasSize(3);
        assertThat(second.diagnostics())
                .containsEntry("candidatePoolCacheHit", true)
                .containsEntry("candidatePoolSize", 3);
    }

    @Test
    void identitySearchResultCacheSeparatesProjects() {
        var cache = new IdentitySearchResultCache(Duration.ofMinutes(5), 10, Clock.systemUTC());
        cache.put("STMN-Group", "Project A", "rene", ConfigLookupResult.valid(List.of(
                new ConfigSelectorOption("rene@example.test", "Rene", "rene@example.test", "project-scope")
        )));

        assertThat(cache.get("STMN-Group", "Project A", "rene")).isPresent();
        assertThat(cache.get("STMN-Group", "Project B", "rene")).isEmpty();
    }

    @Test
    void sparseProjectCandidatesFallBackToOfficialGraphSubjectQuery() {
        var exchange = projectIdentityFallbackExchange("""
                {"count":1,"value":[
                  {"descriptor":"aad.fallback","displayName":"Rene Fallback","principalName":"RENE@Example.Test","subjectKind":"User"}
                ]}
                """);
        var service = discovery(exchange);

        var result = service.searchIdentityOptions("STMN-Group", "Sandbox", "rene");

        assertThat(result.values()).singleElement().satisfies(option -> {
            assertThat(option.value()).isEqualTo("rene@example.test");
            assertThat(option.displayName()).isEqualTo("Rene Fallback <rene@example.test>");
            assertThat(option.avatarUrl()).contains("descriptor=aad.fallback");
            assertThat(option.resolved()).isTrue();
        });
        assertThat(result.diagnostics()).containsEntry("candidatePoolSource", "project-scope+graph-query");
        assertThat(exchange.requests.getLast().method().name()).isEqualTo("POST");
        assertThat(exchange.requests.getLast().url().toString()).contains("/_apis/graph/subjectquery?api-version=7.1-preview.1");
    }

    @Test
    void graphSubjectQueryObjectRootMapsAllResolvableUsersAndFallsBackToLoginForDisplay() {
        var exchange = projectIdentityFallbackExchange("""
                {"count":4,"value":[
                  {"descriptor":"aad.one","displayName":"Rene One","mailAddress":"RENE.ONE@Example.Test","subjectKind":"User"},
                  {"descriptor":"aad.two","principalName":"rene.two@example.test","subjectKind":"User"},
                  {"descriptor":"vssgp.group","displayName":"Rene Group","principalName":"group@example.test","subjectKind":"Group"},
                  {"descriptor":"aad.unresolved","displayName":"Rene Without Login","subjectKind":"User"}
                ]}
                """);
        var service = discovery(exchange);

        var result = service.searchIdentityOptions("STMN-Group", "Sandbox", "rene");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.VALID);
        assertThat(result.values()).extracting(ConfigSelectorOption::value)
                .containsExactly("rene.one@example.test", "rene.two@example.test");
        assertThat(result.values().get(1).displayName()).isEqualTo("rene.two@example.test");
        assertThat(result.values()).noneMatch(option -> option.value().equals("group@example.test"));
    }

    @Test
    void graphSubjectQueryMapsIdentityPickerStyleCollectionAndFieldAliases() {
        var exchange = projectIdentityFallbackExchange("""
                {"identities":[
                  {
                    "subjectDescriptor":"aad.aliased-user",
                    "displayName":"Rene Alias",
                    "samAccountName":"RENE.ALIAS@Example.Test",
                    "entityType":"User"
                  }
                ]}
                """);
        var service = discovery(exchange);

        var result = service.searchIdentityOptions("STMN-Group", "Sandbox", "rene");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.VALID);
        assertThat(result.values()).singleElement().satisfies(option -> {
            assertThat(option.value()).isEqualTo("rene.alias@example.test");
            assertThat(option.displayName()).isEqualTo("Rene Alias <rene.alias@example.test>");
            assertThat(option.avatarUrl()).contains("descriptor=aad.aliased-user");
            assertThat(option.resolved()).isTrue();
        });
    }

    @Test
    void graphSubjectQueryDoesNotMakeUnresolvedOrNonUserSubjectsSelectable() {
        var exchange = projectIdentityFallbackExchange("""
                {"value":[
                  {"descriptor":"vssgp.group","displayName":"Rene Group","subjectKind":"Group"},
                  {"descriptor":"aad.unresolved","displayName":"Rene Without Login","subjectKind":"User"}
                ]}
                """);
        var service = discovery(exchange);

        var result = service.searchIdentityOptions("STMN-Group", "Sandbox", "rene");

        assertThat(result.status()).isEqualTo(ConfigValidationStatus.WARNING);
        assertThat(result.optionCount()).isZero();
    }

    @Test
    void identityAvatarIsFetchedOnceAndThenServedFromCache() {
        var exchange = new RecordingExchangeFunction(List.of(
                new RecordedResponse("png-binary", HttpStatus.OK, "image/png;api-version=7.1")
        ));
        var service = discovery(exchange);

        var first = service.loadIdentityAvatar("STMN-Group", "aad.user-1").orElseThrow();
        var second = service.loadIdentityAvatar("stmn-group", "AAD.USER-1").orElseThrow();

        assertThat(first.bytes()).isEqualTo("png-binary".getBytes(StandardCharsets.UTF_8));
        assertThat(first.contentType()).isEqualTo("image/png;api-version=7.1");
        assertThat(first.cacheHit()).isFalse();
        assertThat(second.bytes()).isEqualTo("png-binary".getBytes(StandardCharsets.UTF_8));
        assertThat(second.cacheHit()).isTrue();
        assertThat(exchange.requests).hasSize(1);
        assertThat(exchange.requests.getFirst().url().toString()).contains("/avatars?size=small&format=png&api-version=7.1");
    }

    @Test
    void failedAvatarLoadIsCachedBrieflyWithoutAffectingIdentityDiscovery() {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var avatarCache = new IdentityAvatarCache(Duration.ofMinutes(15), Duration.ofMinutes(2), 10, clock);
        var exchange = new RecordingExchangeFunction(List.of(
                new RecordedResponse("not found", HttpStatus.NOT_FOUND, "application/json")
        ));
        var service = new AzureDevOpsConfigDiscoveryService(
                "secret-pat",
                exchange,
                new AzureDevOpsUrlBuilder(),
                new IdentitySearchResultCache(),
                new ProjectIdentityCandidateCache(),
                avatarCache
        );

        assertThat(service.loadIdentityAvatar("STMN-Group", "aad.missing")).isEmpty();
        assertThat(service.loadIdentityAvatar("STMN-Group", "aad.missing")).isEmpty();
        assertThat(exchange.requests).hasSize(1);

        clock.advance(Duration.ofMinutes(3));
        assertThat(service.loadIdentityAvatar("STMN-Group", "aad.missing")).isEmpty();
        assertThat(exchange.requests).hasSize(2);
    }

    @Test
    void nonPngAvatarResponseFallsBackWithoutTryingJsonDeserialization() {
        var exchange = new RecordingExchangeFunction(List.of(
                new RecordedResponse("{\"value\":\"not-an-image\"}", HttpStatus.OK, "application/json")
        ));
        var service = discovery(exchange);

        assertThat(service.loadIdentityAvatar("STMN-Group", "aad.user-1")).isEmpty();
        assertThat(exchange.requests).hasSize(1);
    }


    private AzureDevOpsConfigDiscoveryService discovery(RecordingExchangeFunction exchange) {
        return new AzureDevOpsConfigDiscoveryService("secret-pat", exchange, new AzureDevOpsUrlBuilder());
    }

    private RecordingExchangeFunction processDiscoveryExchange(String processWorkItemTypesBody, HttpStatus processStatus) {
        return new RecordingExchangeFunction(List.of(
                new RecordedResponse("""
                        {"id":"project-id-1","name":"ADOnis 2.0 Test Project"}
                        """, HttpStatus.OK),
                new RecordedResponse("""
                        {"value":[{"name":"System.CurrentProcessTemplateId","value":"process-id-1"}]}
                        """, HttpStatus.OK),
                new RecordedResponse(processWorkItemTypesBody, processStatus)
        ));
    }

    private RecordingExchangeFunction projectIdentityExchange(String graphUsersBody) {
        return new RecordingExchangeFunction(List.of(
                new RecordedResponse("{\"id\":\"project-id-1\",\"name\":\"Sandbox\"}", HttpStatus.OK),
                new RecordedResponse("{\"value\":\"scp.project-1\"}", HttpStatus.OK),
                new RecordedResponse(graphUsersBody, HttpStatus.OK)
        ));
    }

    private RecordingExchangeFunction projectIdentityFallbackExchange(String graphQueryBody) {
        return new RecordingExchangeFunction(List.of(
                new RecordedResponse("{\"id\":\"project-id-1\",\"name\":\"Sandbox\"}", HttpStatus.OK),
                new RecordedResponse("{\"value\":\"scp.project-1\"}", HttpStatus.OK),
                new RecordedResponse("{\"count\":0,\"value\":[]}", HttpStatus.OK),
                new RecordedResponse(graphQueryBody, HttpStatus.OK)
        ));
    }

    private record RecordedResponse(String body, HttpStatus status, String contentType) {
        private RecordedResponse(String body, HttpStatus status) {
            this(body, status, "application/json");
        }
    }

    private static class RecordingExchangeFunction implements ExchangeFunction {

        private final List<RecordedResponse> responses;
        private final ArrayList<ClientRequest> requests = new ArrayList<>();

        private RecordingExchangeFunction(String body, HttpStatus status) {
            this(List.of(new RecordedResponse(body, status)));
        }

        private RecordingExchangeFunction(List<RecordedResponse> responses) {
            this.responses = responses;
        }

        @Override
        public Mono<ClientResponse> exchange(ClientRequest request) {
            requests.add(request);
            var response = responses.get(Math.min(requests.size() - 1, responses.size() - 1));
            return Mono.just(ClientResponse.create(response.status())
                    .header(HttpHeaders.CONTENT_TYPE, response.contentType())
                    .body(response.body())
                    .build());
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
