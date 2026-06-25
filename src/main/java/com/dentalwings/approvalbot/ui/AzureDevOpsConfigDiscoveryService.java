package com.dentalwings.approvalbot.ui;

import com.dentalwings.approvalbot.ado.http.AzureDevOpsAuth;
import com.dentalwings.approvalbot.ado.http.AzureDevOpsUrlBuilder;
import com.dentalwings.approvalbot.config.spring.ApprovalBotProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.util.List;
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
        return readList(
                "listWorkItemTypes",
                () -> urlBuilder.workItemTypesUrl(organization, project),
                AdoRestWorkItemTypeListResponse.class,
                response -> response.value().stream().map(AdoRestWorkItemTypeResponse::name).toList()
        );
    }

    @Override
    public ConfigLookupResult<ConfigSelectorOption> listWorkItemTypeOptions(String organization, String project) {
        return readList(
                "listWorkItemTypeOptions",
                () -> urlBuilder.workItemTypesUrl(organization, project),
                AdoRestWorkItemTypeListResponse.class,
                response -> response.value().stream()
                        .map(type -> new ConfigSelectorOption(type.name(), type.name(), "", "ADO"))
                        .toList()
        );
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
        return ConfigLookupResult.warning("ADO user discovery is not available yet; use email/login values, not display names.");
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
        return null;
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestProjectListResponse(Integer count, List<AdoRestProjectResponse> value) {
        AdoRestProjectListResponse {
            value = value == null ? List.of() : List.copyOf(value);
        }

        Integer rawCount() {
            return count == null ? value.size() : count;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestProjectResponse(String name) {
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
    record AdoRestWorkItemTypeResponse(String name) {
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
}
