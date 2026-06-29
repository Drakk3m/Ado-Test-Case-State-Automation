package com.dentalwings.approvalbot.ado.http;

import java.util.List;
import java.util.stream.Collectors;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import com.dentalwings.approvalbot.ado.AdoClient;
import com.dentalwings.approvalbot.ado.AdoCommentResult;
import com.dentalwings.approvalbot.ado.AdoPatchResult;
import com.dentalwings.approvalbot.ado.AdoWorkItem;
import com.dentalwings.approvalbot.ado.AdoWorkItemKey;
import com.dentalwings.approvalbot.ado.AdoWorkItemRevision;
import com.dentalwings.approvalbot.config.spring.AdoProperties;
import com.dentalwings.approvalbot.domain.PatchOperation;

import reactor.core.publisher.Mono;

public class AzureDevOpsHttpClient implements AdoClient
{

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureDevOpsHttpClient.class);
    private static final MediaType JSON_PATCH = MediaType.parseMediaType("application/json-patch+json");
    private static final int MAX_DIAGNOSTIC_BODY_LENGTH = 1000;

    private final WebClient webClient;
    private final AzureDevOpsUrlBuilder urlBuilder;
    private final AzureDevOpsResponseMapper responseMapper;

    public AzureDevOpsHttpClient(WebClient webClient, AzureDevOpsUrlBuilder urlBuilder)
    {
        this(webClient, urlBuilder, new AzureDevOpsResponseMapper());
    }

    AzureDevOpsHttpClient(WebClient webClient, AzureDevOpsUrlBuilder urlBuilder,
            AzureDevOpsResponseMapper responseMapper)
    {
        this.webClient = webClient;
        this.urlBuilder = urlBuilder;
        this.responseMapper = responseMapper;
    }

    public static AzureDevOpsHttpClient fromProperties(AdoProperties properties)
    {
        var authHeader = new AzureDevOpsAuth().basicAuthHeader(properties.getPersonalAccessToken());
        var webClient = WebClient.builder().defaultHeader(HttpHeaders.AUTHORIZATION, authHeader).build();
        return new AzureDevOpsHttpClient(webClient, new AzureDevOpsUrlBuilder());
    }

    public static AzureDevOpsHttpClient forExchangeFunction(ExchangeFunction exchangeFunction,
            String personalAccessToken)
    {
        var authHeader = new AzureDevOpsAuth().basicAuthHeader(personalAccessToken);
        var webClient = WebClient.builder().exchangeFunction(exchangeFunction)
                .defaultHeader(HttpHeaders.AUTHORIZATION, authHeader).build();
        return new AzureDevOpsHttpClient(webClient, new AzureDevOpsUrlBuilder());
    }

    @Override
    public AdoWorkItem fetchWorkItem(AdoWorkItemKey key)
    {
        LOGGER.info("ADO HTTP operation started operation=fetchWorkItem organization={} project={} workItemId={}",
                key.organization(), key.project(), key.workItemId());
        var response = get(urlBuilder.workItemUrl(key), AdoRestWorkItemResponse.class);
        LOGGER.info(
                "ADO HTTP operation completed operation=fetchWorkItem organization={} project={} workItemId={} revision={}",
                key.organization(), key.project(), key.workItemId(), response.rev());
        return responseMapper.toWorkItem(key, response);
    }

    @Override
    public AdoWorkItemRevision fetchWorkItemRevision(AdoWorkItemKey key, int revision)
    {
        LOGGER.info(
                "ADO HTTP operation started operation=fetchWorkItemRevision organization={} project={} workItemId={} revision={}",
                key.organization(), key.project(), key.workItemId(), revision);
        var response = get(urlBuilder.workItemRevisionUrl(key, revision), AdoRestRevisionResponse.class);
        LOGGER.info(
                "ADO HTTP operation completed operation=fetchWorkItemRevision organization={} project={} workItemId={} revision={}",
                key.organization(), key.project(), key.workItemId(), response.rev());
        return responseMapper.toRevision(key, response);
    }

    @Override
    public AdoPatchResult patchWorkItem(AdoWorkItemKey key, List<PatchOperation> patchOperations)
    {
        var validationResult = validatePatchOperations(patchOperations);
        if (validationResult != null)
        {
            LOGGER.warn(
                    "ADO HTTP operation rejected operation=patchWorkItem organization={} project={} workItemId={} retryable={} message={}",
                    key.organization(), key.project(), key.workItemId(), validationResult.retryable(),
                    validationResult.message());
            return validationResult;
        }

        LOGGER.info(
                "ADO HTTP operation started operation=patchWorkItem organization={} project={} workItemId={} operationCount={} operationPaths={}",
                key.organization(), key.project(), key.workItemId(), patchOperations.size(),
                operationPaths(patchOperations));
        return webClient.patch().uri(uri(urlBuilder.workItemPatchUrl(key))).contentType(JSON_PATCH)
                .bodyValue(patchOperations)
                .exchangeToMono(response -> handlePatchResponse(key, patchOperations, response))
                .onErrorResume(error -> {
                    LOGGER.warn(
                            "ADO HTTP operation failed operation=patchWorkItem organization={} project={} workItemId={} retryable={} message={}",
                            key.organization(), key.project(), key.workItemId(), true,
                            "Azure DevOps PATCH request failed with retryable transport error.");
                    return Mono.just(AdoPatchResult
                            .retryableFailure("Azure DevOps PATCH request failed with retryable transport error."));
                }).block();
    }

    @Override
    public AdoCommentResult createWorkItemComment(AdoWorkItemKey key, String commentText)
    {
        if (commentText == null || commentText.isBlank())
        {
            LOGGER.warn(
                    "ADO HTTP operation rejected operation=createWorkItemComment organization={} project={} workItemId={} message={}",
                    key.organization(), key.project(), key.workItemId(),
                    "Azure DevOps comment text must not be blank.");
            return AdoCommentResult.failure("Azure DevOps comment text must not be blank.");
        }

        LOGGER.info(
                "ADO HTTP operation started operation=createWorkItemComment organization={} project={} workItemId={}",
                key.organization(), key.project(), key.workItemId());
        return webClient.post().uri(uri(urlBuilder.workItemCommentsUrl(key))).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AdoRestCommentRequest(commentText))
                .exchangeToMono(response -> handleCommentResponse(key, response)).onErrorResume(error -> {
                    LOGGER.warn(
                            "ADO HTTP operation failed operation=createWorkItemComment organization={} project={} workItemId={} message={}",
                            key.organization(), key.project(), key.workItemId(),
                            "Azure DevOps comment request failed with transport error.");
                    return Mono.just(
                            AdoCommentResult.failure("Azure DevOps comment request failed with transport error."));
                }).block();
    }

    private <T> T get(String url, Class<T> responseType)
    {
        return webClient.get().uri(uri(url)).exchangeToMono(response -> handleResponse(response, responseType)).block();
    }

    private URI uri(String url)
    {
        return URI.create(url);
    }

    private <T> Mono<T> handleResponse(ClientResponse response, Class<T> responseType)
    {
        if (response.statusCode().is2xxSuccessful())
        {
            return response.bodyToMono(responseType);
        }
        return response.createException()
                .flatMap(exception -> Mono.error(exceptionFor(response.statusCode(), exception)));
    }

    private AdoClientException exceptionFor(HttpStatusCode statusCode, Throwable cause)
    {
        var status = statusCode.value();
        if (status == 401 || status == 403)
        {
            return new AdoClientNonRetryableException("Azure DevOps authorization failed.", cause);
        }
        if (status == 404)
        {
            return new AdoClientNonRetryableException("Azure DevOps resource was not found.", cause);
        }
        if (status == 429 || status >= 500)
        {
            return new AdoClientRetryableException(
                    "Azure DevOps read request failed with retryable status " + status + ".", cause);
        }
        return new AdoClientNonRetryableException("Azure DevOps read request failed with status " + status + ".",
                cause);
    }

    private AdoPatchResult validatePatchOperations(List<PatchOperation> patchOperations)
    {
        if (patchOperations == null || patchOperations.isEmpty())
        {
            return AdoPatchResult.nonRetryableFailure("Azure DevOps PATCH requires at least one operation.");
        }
        for (var operation : patchOperations)
        {
            if (operation == null)
            {
                return AdoPatchResult.nonRetryableFailure("Azure DevOps PATCH operation must not be null.");
            }
            if (operation.path() == null || operation.path().isBlank())
            {
                return AdoPatchResult.nonRetryableFailure("Azure DevOps PATCH operation path must not be blank.");
            }
            if ("remove".equalsIgnoreCase(operation.op()))
            {
                return AdoPatchResult.nonRetryableFailure("Azure DevOps PATCH remove operations are not supported.");
            }
        }

        var first = patchOperations.getFirst();
        if (!"test".equalsIgnoreCase(first.op()) || !"/rev".equals(first.path()))
        {
            return AdoPatchResult.nonRetryableFailure("Azure DevOps PATCH must start with a /rev test operation.");
        }

        return null;
    }

    private List<String> operationPaths(List<PatchOperation> patchOperations)
    {
        return patchOperations.stream().map(PatchOperation::path).collect(Collectors.toUnmodifiableList());
    }

    private Mono<AdoPatchResult> handlePatchResponse(AdoWorkItemKey key, List<PatchOperation> patchOperations,
            ClientResponse response)
    {
        var status = response.statusCode().value();
        if (status == 200 || status == 201)
        {
            return response.bodyToMono(AdoRestWorkItemResponse.class).map(result -> {
                LOGGER.info(
                        "ADO HTTP operation completed operation=patchWorkItem organization={} project={} workItemId={} httpStatus={} success={} retryable={} resultingRevision={}",
                        key.organization(), key.project(), key.workItemId(), status, true, false, result.rev());
                return AdoPatchResult.success(result.rev());
            }).defaultIfEmpty(AdoPatchResult.success(-1)).doOnNext(result -> {
                if (result.revision() == -1)
                {
                    LOGGER.info(
                            "ADO HTTP operation completed operation=patchWorkItem organization={} project={} workItemId={} httpStatus={} success={} retryable={}",
                            key.organization(), key.project(), key.workItemId(), status, true, false);
                }
            });
        }
        if (status == 204)
        {
            LOGGER.info(
                    "ADO HTTP operation completed operation=patchWorkItem organization={} project={} workItemId={} httpStatus={} success={} retryable={}",
                    key.organization(), key.project(), key.workItemId(), status, true, false);
            return Mono.just(AdoPatchResult.success(-1));
        }
        if (status == 409 || status == 412 || status == 429 || status >= 500)
        {
            return patchFailure(key, patchOperations, response, status, true);
        }
        return patchFailure(key, patchOperations, response, status, false);
    }

    private Mono<AdoPatchResult> patchFailure(AdoWorkItemKey key, List<PatchOperation> patchOperations,
            ClientResponse response, int status, boolean retryable)
    {
        return response.bodyToMono(String.class).defaultIfEmpty("").map(body -> {
            var message = patchFailureMessage(status, body);
            LOGGER.warn(
                    "ADO HTTP operation failed operation=patchWorkItem organization={} project={} workItemId={} httpStatus={} retryable={} operationCount={} operationPaths={} message={}",
                    key.organization(), key.project(), key.workItemId(), status, retryable, patchOperations.size(),
                    operationPaths(patchOperations), message);
            return retryable ? AdoPatchResult.retryableFailure(message) : AdoPatchResult.nonRetryableFailure(message);
        });
    }

    private String patchFailureMessage(int status, String body)
    {
        var diagnosticBody = sanitizeDiagnosticBody(body);
        if (diagnosticBody.isBlank())
        {
            return "Azure DevOps PATCH request failed with status " + status + ".";
        }
        return "Azure DevOps PATCH request failed with status " + status + ". ADO response: " + diagnosticBody;
    }

    private String sanitizeDiagnosticBody(String body)
    {
        if (body == null || body.isBlank())
        {
            return "";
        }
        var sanitized = body.replaceAll("\\p{Cntrl}+", " ").trim();
        if (sanitized.length() <= MAX_DIAGNOSTIC_BODY_LENGTH)
        {
            return sanitized;
        }
        return sanitized.substring(0, MAX_DIAGNOSTIC_BODY_LENGTH) + "...";
    }

    private Mono<AdoCommentResult> handleCommentResponse(AdoWorkItemKey key, ClientResponse response)
    {
        var status = response.statusCode().value();
        if (status == 200 || status == 201)
        {
            return response.bodyToMono(AdoRestCommentResponse.class).map(result -> {
                LOGGER.info(
                        "ADO HTTP operation completed operation=createWorkItemComment organization={} project={} workItemId={} httpStatus={} success={} commentId={}",
                        key.organization(), key.project(), key.workItemId(), status, true, result.id());
                return AdoCommentResult.success(result.id());
            }).defaultIfEmpty(AdoCommentResult.success(null)).doOnNext(result -> {
                if (result.commentId() == null)
                {
                    LOGGER.info(
                            "ADO HTTP operation completed operation=createWorkItemComment organization={} project={} workItemId={} httpStatus={} success={}",
                            key.organization(), key.project(), key.workItemId(), status, true);
                }
            });
        }
        return response.bodyToMono(String.class).defaultIfEmpty("").map(body -> {
            var message = commentFailureMessage(status, body);
            LOGGER.warn(
                    "ADO HTTP operation failed operation=createWorkItemComment organization={} project={} workItemId={} httpStatus={} message={}",
                    key.organization(), key.project(), key.workItemId(), status, message);
            return AdoCommentResult.failure(message);
        });
    }

    private String commentFailureMessage(int status, String body)
    {
        var diagnosticBody = sanitizeDiagnosticBody(body);
        if (diagnosticBody.isBlank())
        {
            return "Azure DevOps comment request failed with status " + status + ".";
        }
        return "Azure DevOps comment request failed with status " + status + ". ADO response: " + diagnosticBody;
    }
}
