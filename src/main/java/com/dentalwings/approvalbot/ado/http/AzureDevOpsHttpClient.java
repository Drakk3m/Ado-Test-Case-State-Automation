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
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class AzureDevOpsHttpClient implements AdoClient {

    private static final String PATCH_UNSUPPORTED = "Azure DevOps PATCH is not implemented yet.";
    private static final String COMMENTS_UNSUPPORTED = "Azure DevOps comments API is not implemented yet.";

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
        throw new UnsupportedOperationException(PATCH_UNSUPPORTED);
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
}
