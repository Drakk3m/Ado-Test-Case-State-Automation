package com.dentalwings.approvalbot.ui;

import com.dentalwings.approvalbot.ado.http.AzureDevOpsAuth;
import com.dentalwings.approvalbot.ado.http.AzureDevOpsUrlBuilder;
import com.dentalwings.approvalbot.config.spring.ApprovalBotProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AzureDevOpsConfigDiscoveryService implements AdoConfigDiscoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureDevOpsConfigDiscoveryService.class);
    private static final String MISSING_PAT_MESSAGE = "ADO_PERSONAL_ACCESS_TOKEN is required for read-only ADO discovery.";
    private static final int MAX_SAFE_DETAIL_LENGTH = 1000;
    private static final List<String> PROCESS_ID_PROPERTY_PREFERENCE = List.of(
            "System.CurrentProcessTemplateId",
            "System.ProcessTemplateType",
            "System.Process Template"
    );

    private final WebClient webClient;
    private final AzureDevOpsUrlBuilder urlBuilder;
    private final String personalAccessToken;

    @Autowired
    public AzureDevOpsConfigDiscoveryService(ApprovalBotProperties properties) {
        this(properties.getAdo().getPersonalAccessToken(), WebClient.builder().build(), new AzureDevOpsUrlBuilder());
    }

    AzureDevOpsConfigDiscoveryService(
            String personalAccessToken,
            ExchangeFunction exchangeFunction,
            AzureDevOpsUrlBuilder urlBuilder
    ) {
        this(personalAccessToken, WebClient.builder().exchangeFunction(exchangeFunction).build(), urlBuilder);
    }

    private AzureDevOpsConfigDiscoveryService(
            String personalAccessToken,
            WebClient webClient,
            AzureDevOpsUrlBuilder urlBuilder
    ) {
        this.personalAccessToken = personalAccessToken;
        this.webClient = webClient;
        this.urlBuilder = urlBuilder;
    }

    @Override
    public ConfigLookupResult<String> listProjects(String organization) {
        return readList(
                "listProjects",
                () -> urlBuilder.projectsUrl(organization),
                AdoRestProjectListResponse.class,
                response -> response.value().stream().map(AdoRestProjectResponse::name).toList()
        );
    }

    @Override
    public ConfigLookupResult<ConfigSelectorOption> listProjectOptions(String organization) {
        return readList(
                "listProjectOptions",
                () -> urlBuilder.projectsUrl(organization),
                AdoRestProjectListResponse.class,
                response -> response.value().stream()
                        .map(project -> new ConfigSelectorOption(project.name(), project.name(), "", "ADO"))
                        .toList()
        );
    }

    @Override
    public ConfigLookupResult<String> validateProject(String organization, String project) {
        if (isBlank(project)) {
            return ConfigLookupResult.error("Project name is required.");
        }
        return readOne(
                "validateProject",
                () -> urlBuilder.projectUrl(organization, project),
                AdoRestProjectResponse.class,
                response -> List.of(response.name())
        );
    }

    @Override
    public ConfigLookupResult<String> listWorkItemTypes(String organization, String project) {
        var options = listWorkItemTypeOptions(organization, project);
        return new ConfigLookupResult<>(
                options.status(),
                options.message(),
                options.values().stream().map(ConfigSelectorOption::value).toList()
        );
    }

    @Override
    public ConfigLookupResult<ConfigSelectorOption> listWorkItemTypeOptions(String organization, String project) {
        var started = System.nanoTime();
        if (isBlank(project)) {
            return ConfigLookupResult.error("Project name is required.");
        }
        var projectFetch = fetchBody(
                "resolveProjectForWorkItemTypes",
                () -> urlBuilder.projectUrl(organization, project),
                AdoRestProjectResponse.class
        );
        if (!projectFetch.successful()) {
            return failedLookup(projectFetch);
        }
        var projectId = projectFetch.body().id();
        if (isBlank(projectId)) {
            return ConfigLookupResult.error("ADO project id was not returned by project discovery.");
        }

        var propertiesFetch = fetchBody(
                "loadProjectProcessProperties",
                () -> urlBuilder.projectPropertiesUrl(organization, projectId),
                AdoRestProjectPropertiesResponse.class
        );
        if (!propertiesFetch.successful()) {
            logProcessDiscoveryFailure("listWorkItemTypeOptions", organization, project, projectId, "", "", propertiesFetch, started);
            return failedLookup(propertiesFetch);
        }

        var candidates = processIdCandidates(propertiesFetch.body());
        LOGGER.info(
                "ADO config discovery process properties loaded operation={} organization={} project={} projectId={} path={} httpStatus={} candidateCount={} durationMs={}",
                "listWorkItemTypeOptions",
                organization,
                project,
                projectId,
                propertiesFetch.safePath(),
                propertiesFetch.httpStatus(),
                candidates.size(),
                elapsedMillis(started)
        );
        if (candidates.isEmpty()) {
            LOGGER.warn(
                    "ADO config discovery failed operation={} organization={} project={} projectId={} processPropertyUsed={} processId={} path={} failureCategory={} message={} durationMs={}",
                    "listWorkItemTypeOptions",
                    organization,
                    project,
                    projectId,
                    "",
                    "",
                    propertiesFetch.safePath(),
                    "missing-process-id",
                    "No usable process id property was returned by ADO.",
                    elapsedMillis(started)
            );
            return ConfigLookupResult.error("ADO project process id could not be resolved from project properties.");
        }

        ConfigLookupResult<ConfigSelectorOption> lastFailure = ConfigLookupResult.error("ADO project process id could not be resolved.");
        for (var candidate : candidates) {
            var processFetch = fetchBody(
                    "listProcessWorkItemTypes",
                    () -> urlBuilder.processWorkItemTypesUrl(organization, candidate.processId()),
                    AdoRestWorkItemTypeListResponse.class
            );
            if (processFetch.successful()) {
                var values = processFetch.body().value().stream()
                        .map(type -> new ConfigSelectorOption(
                                type.name(),
                                type.name(),
                                type.description(),
                                "ADO",
                                type.referenceName()
                        ))
                        .toList();
                var result = ConfigLookupResult.valid(values);
                LOGGER.info(
                        "ADO config discovery completed operation={} organization={} project={} projectId={} processPropertyUsed={} processId={} path={} httpStatus={} rawAdoCount={} mappedOptionCount={} finalOptionCount={} durationMs={}",
                        "listWorkItemTypeOptions",
                        organization,
                        project,
                        projectId,
                        candidate.propertyName(),
                        candidate.processId(),
                        processFetch.safePath(),
                        processFetch.httpStatus(),
                        rawAdoCount(processFetch.body()),
                        values.size(),
                        result.optionCount(),
                        elapsedMillis(started)
                );
                return result;
            }
            logProcessDiscoveryFailure("listWorkItemTypeOptions", organization, project, projectId, candidate.propertyName(), candidate.processId(), processFetch, started);
            lastFailure = failedLookup(processFetch);
        }
        return ConfigLookupResult.error("ADO process Work Item Types could not be discovered. " + lastFailure.message());
    }

    @Override
    public ConfigLookupResult<String> listFieldReferenceNames(String organization, String project, String workItemType) {
        return readList(
                "listFieldReferenceNames",
                () -> urlBuilder.workItemTypeFieldsUrl(organization, project, workItemType),
                AdoRestFieldListResponse.class,
                response -> response.value().stream().map(AdoRestFieldResponse::referenceName).toList()
        );
    }

    @Override
    public ConfigLookupResult<ConfigSelectorOption> listFieldOptions(String organization, String project, String workItemType) {
        return readList(
                "listFieldOptions",
                () -> urlBuilder.workItemTypeFieldsUrl(organization, project, workItemType),
                AdoRestFieldListResponse.class,
                response -> response.value().stream()
                        .map(field -> new ConfigSelectorOption(
                                field.referenceName(),
                                field.name(),
                                field.type(),
                                "ADO"
                        ))
                        .toList()
        );
    }

    @Override
    public ConfigLookupResult<String> listObservedStateNames(String organization, String project, String workItemType) {
        return readList(
                "listObservedStateNames",
                () -> urlBuilder.workItemTypeStatesUrl(organization, project, workItemType),
                AdoRestStateListResponse.class,
                response -> response.value().stream().map(AdoRestStateResponse::name).toList()
        );
    }

    @Override
    public ConfigLookupResult<ConfigSelectorOption> listStateOptions(String organization, String project, String workItemType) {
        return readList(
                "listStateOptions",
                () -> urlBuilder.workItemTypeStatesUrl(organization, project, workItemType),
                AdoRestStateListResponse.class,
                response -> response.value().stream()
                        .map(state -> new ConfigSelectorOption(state.name(), state.name(), "", "ADO"))
                        .toList()
        );
    }

    @Override
    public ConfigLookupResult<String> resolveUsers(String organization, List<String> users) {
        if (users == null || users.isEmpty()) {
            return ConfigLookupResult.error("User list is required.");
        }
        var resolved = new LinkedHashSet<String>();
        var unresolved = new LinkedHashSet<String>();
        for (var user : users) {
            var normalizedUser = normalizedIdentity(user);
            if (normalizedUser.isBlank()) {
                unresolved.add("");
                continue;
            }
            var lookup = searchIdentityOptions(organization, user);
            if (lookup.status() == ConfigValidationStatus.ERROR || lookup.status() == ConfigValidationStatus.NOT_CHECKED) {
                return new ConfigLookupResult<>(lookup.status(), lookup.message(), List.of());
            }
            var matched = lookup.values().stream()
                    .map(ConfigSelectorOption::value)
                    .map(this::normalizedIdentity)
                    .filter(normalizedUser::equals)
                    .findFirst();
            if (matched.isPresent()) {
                resolved.add(matched.get());
            } else {
                unresolved.add(user);
            }
        }
        if (!unresolved.isEmpty()) {
            return ConfigLookupResult.error("User identity was not resolved in ADO: " + String.join(", ", unresolved));
        }
        return ConfigLookupResult.valid(List.copyOf(resolved));
    }

    @Override
    public ConfigLookupResult<ConfigSelectorOption> searchIdentityOptions(String organization, String query) {
        var trimmedQuery = query == null ? "" : query.trim();
        if (trimmedQuery.length() < 2) {
            return ConfigLookupResult.notChecked("Type at least 2 characters to search ADO identities.");
        }
        return readList(
                "searchIdentityOptions",
                () -> urlBuilder.identitySearchUrl(organization, trimmedQuery),
                AdoRestIdentityListResponse.class,
                this::identityOptions
        );
    }

    private List<ConfigSelectorOption> identityOptions(AdoRestIdentityListResponse response) {
        return response.items().stream()
                .map(this::identityOption)
                .filter(option -> !option.value().isBlank())
                .toList();
    }

    private ConfigSelectorOption identityOption(AdoRestIdentityResponse identity) {
        var value = normalizedIdentity(firstNonBlank(
                identity.uniqueName(),
                identity.mail(),
                identity.account(),
                identity.signInAddress()
        ));
        if (value.isBlank()) {
            return new ConfigSelectorOption("", identity.displayLabel(), "", "ADO", identity.subjectDescriptor());
        }
        var displayName = firstNonBlank(identity.displayName(), identity.providerDisplayName(), value);
        var displayLabel = displayName.equals(value) ? value : displayName + " <" + value + ">";
        return new ConfigSelectorOption(value, displayLabel, value, "ADO", identity.subjectDescriptor());
    }

    private <R, T> ConfigLookupResult<R> readOne(
            String operation,
            Supplier<String> url,
            Class<T> responseType,
            Function<T, List<R>> mapper
    ) {
        return read(operation, url, responseType, mapper);
    }

    private <R, T> ConfigLookupResult<R> readList(
            String operation,
            Supplier<String> url,
            Class<T> responseType,
            Function<T, List<R>> mapper
    ) {
        return read(operation, url, responseType, mapper);
    }

    private <R, T> ConfigLookupResult<R> read(
            String operation,
            Supplier<String> url,
            Class<T> responseType,
            Function<T, List<R>> mapper
    ) {
        if (isBlank(personalAccessToken)) {
            return ConfigLookupResult.error(MISSING_PAT_MESSAGE);
        }
        try {
            var targetUrl = url.get();
            var safePath = safePath(targetUrl);
            return webClient.get()
                    .uri(URI.create(targetUrl))
                    .header(HttpHeaders.AUTHORIZATION, new AzureDevOpsAuth().basicAuthHeader(personalAccessToken))
                    .exchangeToMono(response -> handleResponse(operation, safePath, response, responseType, mapper))
                    .onErrorResume(error -> Mono.just(handleTransportError(operation, safePath, error)))
                    .block();
        } catch (IllegalArgumentException ex) {
            return ConfigLookupResult.error(ex.getMessage());
        }
    }

    private <T> AdoFetchResult<T> fetchBody(
            String operation,
            Supplier<String> url,
            Class<T> responseType
    ) {
        if (isBlank(personalAccessToken)) {
            return AdoFetchResult.failure(ConfigLookupResult.error(MISSING_PAT_MESSAGE), 0, "");
        }
        try {
            var targetUrl = url.get();
            var safePath = safePath(targetUrl);
            return webClient.get()
                    .uri(URI.create(targetUrl))
                    .header(HttpHeaders.AUTHORIZATION, new AzureDevOpsAuth().basicAuthHeader(personalAccessToken))
                    .exchangeToMono(response -> handleFetchResponse(operation, safePath, response, responseType))
                    .onErrorResume(error -> Mono.just(AdoFetchResult.failure(
                            handleTransportError(operation, safePath, error),
                            0,
                            safePath
                    )))
                    .block();
        } catch (IllegalArgumentException ex) {
            return AdoFetchResult.failure(ConfigLookupResult.error(ex.getMessage()), 0, "");
        }
    }

    private <T> Mono<AdoFetchResult<T>> handleFetchResponse(
            String operation,
            String safePath,
            ClientResponse response,
            Class<T> responseType
    ) {
        var status = response.statusCode().value();
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(responseType)
                    .map(body -> AdoFetchResult.success(body, status, safePath))
                    .defaultIfEmpty(AdoFetchResult.failure(ConfigLookupResult.warning("ADO discovery returned no response body."), status, safePath));
        }
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> AdoFetchResult.failure(handleHttpFailure(operation, safePath, status, body), status, safePath));
    }

    private <R> ConfigLookupResult<R> failedLookup(AdoFetchResult<?> fetchResult) {
        return new ConfigLookupResult<>(
                fetchResult.failure().status(),
                fetchResult.failure().message(),
                List.of()
        );
    }

    private <R, T> Mono<ConfigLookupResult<R>> handleResponse(
            String operation,
            String safePath,
            ClientResponse response,
            Class<T> responseType,
            Function<T, List<R>> mapper
    ) {
        var status = response.statusCode().value();
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(responseType)
                    .map(body -> mapSuccessfulResponse(operation, safePath, status, body, mapper))
                    .defaultIfEmpty(ConfigLookupResult.valid(List.of()));
        }
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> handleHttpFailure(operation, safePath, status, body));
    }

    private <R, T> ConfigLookupResult<R> mapSuccessfulResponse(
            String operation,
            String safePath,
            int status,
            T body,
            Function<T, List<R>> mapper
    ) {
        var values = mapper.apply(body);
        var result = ConfigLookupResult.valid(values);
        LOGGER.info(
                "ADO config discovery completed operation={} path={} httpStatus={} rawAdoCount={} mappedOptionCount={} finalOptionCount={}",
                operation,
                safePath,
                status,
                rawAdoCount(body),
                values.size(),
                result.optionCount()
        );
        return result;
    }

    private <R> ConfigLookupResult<R> handleHttpFailure(String operation, String safePath, int status, String responseBody) {
        var safeBody = sanitize(responseBody);
        LOGGER.warn(
                "ADO config discovery failed operation={} path={} httpStatus={} adoResponse={}",
                operation,
                safePath,
                status,
                safeBody
        );
        if (status == 401 || status == 403) {
            return ConfigLookupResult.error("ADO discovery authorization failed with status " + status + detail(safeBody));
        }
        if (status == 404) {
            return ConfigLookupResult.error("ADO discovery resource was not found. status=404" + detail(safeBody));
        }
        if (status == 429 || status >= 500) {
            return ConfigLookupResult.error("ADO discovery failed with retryable status " + status + detail(safeBody));
        }
        return ConfigLookupResult.error("ADO discovery failed with status " + status + detail(safeBody));
    }

    private <R> ConfigLookupResult<R> handleTransportError(String operation, String safePath, Throwable error) {
        var safeDetail = sanitize(error.getClass().getSimpleName() + ": " + error.getMessage());
        LOGGER.warn(
                "ADO config discovery transport failure operation={} path={} detail={}",
                operation,
                safePath,
                safeDetail
        );
        return ConfigLookupResult.error("ADO discovery transport error: " + safeDetail);
    }

    private String detail(String safeBody) {
        return safeBody.isBlank() ? "." : ". ADO response: " + safeBody;
    }

    private Integer rawAdoCount(Object body) {
        if (body instanceof AdoRestProjectListResponse response) {
            return response.rawCount();
        }
        if (body instanceof AdoRestWorkItemTypeListResponse response) {
            return response.rawCount();
        }
        if (body instanceof AdoRestFieldListResponse response) {
            return response.rawCount();
        }
        if (body instanceof AdoRestStateListResponse response) {
            return response.rawCount();
        }
        if (body instanceof AdoRestIdentityListResponse response) {
            return response.rawCount();
        }
        return null;
    }

    private List<ProcessIdCandidate> processIdCandidates(AdoRestProjectPropertiesResponse response) {
        return PROCESS_ID_PROPERTY_PREFERENCE.stream()
                .flatMap(propertyName -> response.value().stream()
                        .filter(property -> propertyName.equals(property.name()))
                        .map(property -> new ProcessIdCandidate(property.name(), property.valueAsString()))
                )
                .filter(candidate -> !isBlank(candidate.processId()))
                .distinct()
                .toList();
    }

    private void logProcessDiscoveryFailure(
            String operation,
            String organization,
            String project,
            String projectId,
            String processPropertyUsed,
            String processId,
            AdoFetchResult<?> fetchResult,
            long started
    ) {
        LOGGER.warn(
                "ADO config discovery failed operation={} organization={} project={} projectId={} processPropertyUsed={} processId={} path={} httpStatus={} failureCategory={} message={} durationMs={}",
                operation,
                organization,
                project,
                projectId,
                processPropertyUsed,
                processId,
                fetchResult.safePath(),
                fetchResult.httpStatus(),
                fetchResult.failure().status(),
                sanitize(fetchResult.failure().message()),
                elapsedMillis(started)
        );
    }

    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private String safePath(String url) {
        var uri = URI.create(url);
        var query = uri.getRawQuery();
        return query == null || query.isBlank() ? uri.getRawPath() : uri.getRawPath() + "?" + query;
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        var sanitized = value
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("(?i)authorization\\s*[:=]\\s*\\S+", "Authorization=[redacted]");
        if (!isBlank(personalAccessToken)) {
            sanitized = sanitized.replace(personalAccessToken, "[redacted]");
        }
        sanitized = sanitized.trim();
        if (sanitized.length() > MAX_SAFE_DETAIL_LENGTH) {
            return sanitized.substring(0, MAX_SAFE_DETAIL_LENGTH) + "...";
        }
        return sanitized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizedIdentity(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (var value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestProjectListResponse(Integer count, List<AdoRestProjectResponse> value) {
        AdoRestProjectListResponse {
            value = value == null ? List.of() : List.copyOf(value);
        }

        Integer rawCount() {
            return count == null ? value.size() : count;
        }
    }

    record AdoFetchResult<T>(T body, ConfigLookupResult<?> failure, int httpStatus, String safePath) {
        static <T> AdoFetchResult<T> success(T body, int httpStatus, String safePath) {
            return new AdoFetchResult<>(body, null, httpStatus, safePath);
        }

        static <T> AdoFetchResult<T> failure(ConfigLookupResult<?> failure, int httpStatus, String safePath) {
            return new AdoFetchResult<>(null, failure, httpStatus, safePath);
        }

        boolean successful() {
            return failure == null;
        }
    }

    record ProcessIdCandidate(String propertyName, String processId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestProjectResponse(String id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestWorkItemTypeListResponse(Integer count, List<AdoRestWorkItemTypeResponse> value) {
        AdoRestWorkItemTypeListResponse {
            value = value == null ? List.of() : List.copyOf(value);
        }

        Integer rawCount() {
            return count == null ? value.size() : count;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestWorkItemTypeResponse(String name, String referenceName, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestProjectPropertiesResponse(Integer count, List<AdoRestProjectPropertyResponse> value) {
        AdoRestProjectPropertiesResponse {
            value = value == null ? List.of() : List.copyOf(value);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestProjectPropertyResponse(String name, JsonNode value) {
        String valueAsString() {
            if (value == null || value.isNull()) {
                return "";
            }
            if (value.isTextual()) {
                return value.asText();
            }
            if (value.has("id")) {
                return value.get("id").asText();
            }
            if (value.has("value")) {
                return value.get("value").asText();
            }
            return "";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestFieldListResponse(Integer count, List<AdoRestFieldResponse> value) {
        AdoRestFieldListResponse {
            value = value == null ? List.of() : List.copyOf(value);
        }

        Integer rawCount() {
            return count == null ? value.size() : count;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestFieldResponse(String referenceName, String name, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestStateListResponse(Integer count, List<AdoRestStateResponse> value) {
        AdoRestStateListResponse {
            value = value == null ? List.of() : List.copyOf(value);
        }

        Integer rawCount() {
            return count == null ? value.size() : count;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestStateResponse(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestIdentityListResponse(Integer count, List<AdoRestIdentityResponse> value, List<AdoRestIdentityResponse> identities) {
        AdoRestIdentityListResponse {
            value = value == null ? List.of() : List.copyOf(value);
            identities = identities == null ? List.of() : List.copyOf(identities);
        }

        List<AdoRestIdentityResponse> items() {
            return value.isEmpty() ? identities : value;
        }

        Integer rawCount() {
            return count == null ? items().size() : count;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestIdentityResponse(
            String id,
            String descriptor,
            String subjectDescriptor,
            String displayName,
            String providerDisplayName,
            String uniqueName,
            JsonNode properties
    ) {
        String mail() {
            return property("Mail");
        }

        String account() {
            return property("Account");
        }

        String signInAddress() {
            return property("SignInAddress");
        }

        String displayLabel() {
            return firstNonBlank(displayName, providerDisplayName, uniqueName, id);
        }

        public String subjectDescriptor() {
            return firstNonBlank(subjectDescriptor, descriptor, id);
        }

        private String property(String name) {
            if (properties == null || properties.isNull() || !properties.has(name)) {
                return "";
            }
            var property = properties.get(name);
            if (property.isTextual()) {
                return property.asText();
            }
            if (property.has("$value")) {
                return property.get("$value").asText();
            }
            if (property.has("value")) {
                return property.get("value").asText();
            }
            return "";
        }

        private String firstNonBlank(String... values) {
            for (var value : values) {
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
            return "";
        }
    }
}
