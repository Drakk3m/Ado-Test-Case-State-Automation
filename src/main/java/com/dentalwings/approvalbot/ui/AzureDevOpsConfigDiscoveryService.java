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
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AzureDevOpsConfigDiscoveryService implements AdoConfigDiscoveryService {

    private static final String MISSING_PAT_MESSAGE = "ADO_PERSONAL_ACCESS_TOKEN is required for read-only ADO discovery.";

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
                () -> urlBuilder.projectsUrl(organization),
                AdoRestProjectListResponse.class,
                response -> response.value().stream().map(AdoRestProjectResponse::name).toList()
        );
    }

    @Override
    public ConfigLookupResult<ConfigSelectorOption> listProjectOptions(String organization) {
        return readList(
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
                () -> urlBuilder.projectUrl(organization, project),
                AdoRestProjectResponse.class,
                response -> List.of(response.name())
        );
    }

    @Override
    public ConfigLookupResult<String> listWorkItemTypes(String organization, String project) {
        return readList(
                () -> urlBuilder.workItemTypesUrl(organization, project),
                AdoRestWorkItemTypeListResponse.class,
                response -> response.value().stream().map(AdoRestWorkItemTypeResponse::name).toList()
        );
    }

    @Override
    public ConfigLookupResult<ConfigSelectorOption> listWorkItemTypeOptions(String organization, String project) {
        return readList(
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
                () -> urlBuilder.workItemTypeFieldsUrl(organization, project, workItemType),
                AdoRestFieldListResponse.class,
                response -> response.value().stream().map(AdoRestFieldResponse::referenceName).toList()
        );
    }

    @Override
    public ConfigLookupResult<ConfigSelectorOption> listFieldOptions(String organization, String project, String workItemType) {
        return readList(
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
                () -> urlBuilder.workItemTypeStatesUrl(organization, project, workItemType),
                AdoRestStateListResponse.class,
                response -> response.value().stream().map(AdoRestStateResponse::name).toList()
        );
    }

    @Override
    public ConfigLookupResult<ConfigSelectorOption> listStateOptions(String organization, String project, String workItemType) {
        return readList(
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

    private <R, T> ConfigLookupResult<R> readOne(Supplier<String> url, Class<T> responseType, Function<T, List<R>> mapper) {
        return read(url, responseType, mapper);
    }

    private <R, T> ConfigLookupResult<R> readList(Supplier<String> url, Class<T> responseType, Function<T, List<R>> mapper) {
        return read(url, responseType, mapper);
    }

    private <R, T> ConfigLookupResult<R> read(Supplier<String> url, Class<T> responseType, Function<T, List<R>> mapper) {
        if (isBlank(personalAccessToken)) {
            return ConfigLookupResult.error(MISSING_PAT_MESSAGE);
        }
        try {
            return webClient.get()
                    .uri(URI.create(url.get()))
                    .header(HttpHeaders.AUTHORIZATION, new AzureDevOpsAuth().basicAuthHeader(personalAccessToken))
                    .exchangeToMono(response -> handleResponse(response, responseType, mapper))
                    .onErrorResume(error -> Mono.just(ConfigLookupResult.notChecked(
                            "ADO discovery could not be completed due to a transport error."
                    )))
                    .block();
        } catch (IllegalArgumentException ex) {
            return ConfigLookupResult.error(ex.getMessage());
        }
    }

    private <R, T> Mono<ConfigLookupResult<R>> handleResponse(
            ClientResponse response,
            Class<T> responseType,
            Function<T, List<R>> mapper
    ) {
        var status = response.statusCode().value();
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(responseType)
                    .map(body -> ConfigLookupResult.valid(mapper.apply(body)))
                    .defaultIfEmpty(ConfigLookupResult.valid(List.of()));
        }
        if (status == 401 || status == 403) {
            return Mono.just(ConfigLookupResult.error("ADO discovery authorization failed."));
        }
        if (status == 404) {
            return Mono.just(ConfigLookupResult.error("ADO discovery resource was not found."));
        }
        if (status == 429 || status >= 500) {
            return Mono.just(ConfigLookupResult.notChecked(
                    "ADO discovery returned retryable status " + status + "."
            ));
        }
        return Mono.just(ConfigLookupResult.error("ADO discovery failed with status " + status + "."));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestProjectListResponse(List<AdoRestProjectResponse> value) {
        AdoRestProjectListResponse {
            value = value == null ? List.of() : List.copyOf(value);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestProjectResponse(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestWorkItemTypeListResponse(List<AdoRestWorkItemTypeResponse> value) {
        AdoRestWorkItemTypeListResponse {
            value = value == null ? List.of() : List.copyOf(value);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestWorkItemTypeResponse(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestFieldListResponse(List<AdoRestFieldResponse> value) {
        AdoRestFieldListResponse {
            value = value == null ? List.of() : List.copyOf(value);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestFieldResponse(String referenceName, String name, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestStateListResponse(List<AdoRestStateResponse> value) {
        AdoRestStateListResponse {
            value = value == null ? List.of() : List.copyOf(value);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestStateResponse(String name) {
    }
}
