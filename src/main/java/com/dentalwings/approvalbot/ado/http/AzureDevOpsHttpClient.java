package com.dentalwings.approvalbot.ado.http;

import com.dentalwings.approvalbot.ado.AdoClient;
import com.dentalwings.approvalbot.ado.AdoCommentResult;
import com.dentalwings.approvalbot.ado.AdoPatchResult;
import com.dentalwings.approvalbot.ado.AdoWorkItem;
import com.dentalwings.approvalbot.ado.AdoWorkItemKey;
import com.dentalwings.approvalbot.ado.AdoWorkItemRevision;
import com.dentalwings.approvalbot.config.spring.AdoProperties;
import com.dentalwings.approvalbot.domain.PatchOperation;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class AzureDevOpsHttpClient implements AdoClient {

    private static final String COMMENTS_UNSUPPORTED = "Azure DevOps comments API is not implemented yet.";
    private static final MediaType JSON_PATCH = MediaType.parseMediaType("application/json-patch+json");

    private final WebClient webClient;
    private final AzureDevOpsUrlBuilder urlBuilder;
    private final AzureDevOpsResponseMapper responseMapper;

    public AzureDevOpsHttpClient(WebClient webClient, AzureDevOpsUrlBuilder urlBuilder) {
        this(webClient, urlBuilder, new AzureDevOpsResponseMapper());
    }

    AzureDevOpsHttpClient(
            WebClient webClient,
            AzureDevOpsUrlBuilder urlBuilder,
            AzureDevOpsResponseMapper responseMapper
    ) {
        this.webClient = webClient;
        this.urlBuilder = urlBuilder;
        this.responseMapper = responseMapper;
    }

    public static AzureDevOpsHttpClient fromProperties(AdoProperties properties) {
        var authHeader = new AzureDevOpsAuth().basicAuthHeader(properties.getPersonalAccessToken());
        var webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .build();
        return new AzureDevOpsHttpClient(webClient, new AzureDevOpsUrlBuilder());
    }

    public static AzureDevOpsHttpClient forExchangeFunction(ExchangeFunction exchangeFunction, String personalAccessToken) {
        var authHeader = new AzureDevOpsAuth().basicAuthHeader(personalAccessToken);
        var webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .defaultHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .build();
        return new AzureDevOpsHttpClient(webClient, new AzureDevOpsUrlBuilder());
    }

    @Override
    public AdoWorkItem fetchWorkItem(AdoWorkItemKey key) {
        var response = get(urlBuilder.workItemUrl(key), AdoRestWorkItemResponse.class);
        return responseMapper.toWorkItem(key, response);
    }

    @Override
    public AdoWorkItemRevision fetchWorkItemRevision(AdoWorkItemKey key, int revision) {
        var response = get(urlBuilder.workItemRevisionUrl(key, revision), AdoRestRevisionResponse.class);
        return responseMapper.toRevision(key, response);
    }

    @Override
    public AdoPatchResult patchWorkItem(AdoWorkItemKey key, List<PatchOperation> patchOperations) {
        var validationResult = validatePatchOperations(patchOperations);
        if (validationResult != null) {
            return validationResult;
        }

        return webClient.patch()
                .uri(urlBuilder.workItemPatchUrl(key))
                .contentType(JSON_PATCH)
                .bodyValue(patchOperations)
                .exchangeToMono(this::handlePatchResponse)
                .onErrorResume(error -> Mono.just(AdoPatchResult.retryableFailure(
                        "Azure DevOps PATCH request failed with retryable transport error."
                )))
                .block();
    }

    @Override
    public AdoCommentResult createWorkItemComment(AdoWorkItemKey key, String commentText) {
        throw new UnsupportedOperationException(COMMENTS_UNSUPPORTED);
    }

    private <T> T get(String url, Class<T> responseType) {
        return webClient.get()
                .uri(url)
                .exchangeToMono(response -> handleResponse(response, responseType))
                .block();
    }

    private <T> Mono<T> handleResponse(ClientResponse response, Class<T> responseType) {
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(responseType);
        }
        return response.createException()
                .flatMap(exception -> Mono.error(exceptionFor(response.statusCode(), exception)));
    }

    private AdoClientException exceptionFor(HttpStatusCode statusCode, Throwable cause) {
        var status = statusCode.value();
        if (status == 401 || status == 403) {
            return new AdoClientNonRetryableException("Azure DevOps authorization failed.", cause);
        }
        if (status == 404) {
            return new AdoClientNonRetryableException("Azure DevOps resource was not found.", cause);
        }
        if (status == 429 || status >= 500) {
            return new AdoClientRetryableException("Azure DevOps read request failed with retryable status " + status + ".", cause);
        }
        return new AdoClientNonRetryableException("Azure DevOps read request failed with status " + status + ".", cause);
    }

    private AdoPatchResult validatePatchOperations(List<PatchOperation> patchOperations) {
        if (patchOperations == null || patchOperations.isEmpty()) {
            return AdoPatchResult.nonRetryableFailure("Azure DevOps PATCH requires at least one operation.");
        }
        for (var operation : patchOperations) {
            if (operation == null) {
                return AdoPatchResult.nonRetryableFailure("Azure DevOps PATCH operation must not be null.");
            }
            if (operation.path() == null || operation.path().isBlank()) {
                return AdoPatchResult.nonRetryableFailure("Azure DevOps PATCH operation path must not be blank.");
            }
            if ("remove".equalsIgnoreCase(operation.op())) {
                return AdoPatchResult.nonRetryableFailure("Azure DevOps PATCH remove operations are not supported.");
            }
        }

        var first = patchOperations.getFirst();
        if (!"test".equalsIgnoreCase(first.op()) || !"/rev".equals(first.path())) {
            return AdoPatchResult.nonRetryableFailure("Azure DevOps PATCH must start with a /rev test operation.");
        }

        return null;
    }

    private Mono<AdoPatchResult> handlePatchResponse(ClientResponse response) {
        var status = response.statusCode().value();
        if (status == 200 || status == 201) {
            return response.bodyToMono(AdoRestWorkItemResponse.class)
                    .map(result -> AdoPatchResult.success(result.rev()))
                    .defaultIfEmpty(AdoPatchResult.success(-1));
        }
        if (status == 204) {
            return Mono.just(AdoPatchResult.success(-1));
        }
        if (status == 409 || status == 412 || status == 429 || status >= 500) {
            return Mono.just(AdoPatchResult.retryableFailure(patchFailureMessage(status)));
        }
        return Mono.just(AdoPatchResult.nonRetryableFailure(patchFailureMessage(status)));
    }

    private String patchFailureMessage(int status) {
        return "Azure DevOps PATCH request failed with status " + status + ".";
    }
}
