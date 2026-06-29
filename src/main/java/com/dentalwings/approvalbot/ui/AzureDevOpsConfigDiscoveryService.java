package com.dentalwings.approvalbot.ui;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import com.dentalwings.approvalbot.ado.http.AzureDevOpsAuth;
import com.dentalwings.approvalbot.ado.http.AzureDevOpsUrlBuilder;
import com.dentalwings.approvalbot.ado.RuntimeAdoCredentialService;
import com.dentalwings.approvalbot.config.spring.ApprovalBotProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import reactor.core.publisher.Mono;

@Service
public class AzureDevOpsConfigDiscoveryService implements AdoConfigDiscoveryService
{

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureDevOpsConfigDiscoveryService.class);
    private static final String MISSING_PAT_MESSAGE = "ADO_PERSONAL_ACCESS_TOKEN is not configured. Submit PAT in Config UI to enable ADO-backed discovery. Local draft editing is still available.";
    private static final int MAX_SAFE_DETAIL_LENGTH = 1000;
    private static final int MIN_IDENTITY_QUERY_LENGTH = 3;
    private static final int MAX_PROJECT_CANDIDATES = 250;
    private static final int MAX_AVATAR_BYTES = 128 * 1024;
    private static final List<String> PROCESS_ID_PROPERTY_PREFERENCE = List.of("System.CurrentProcessTemplateId",
            "System.ProcessTemplateType", "System.Process Template");

    private final WebClient webClient;
    private final AzureDevOpsUrlBuilder urlBuilder;
    private final Supplier<String> personalAccessTokenSupplier;
    private final IdentitySearchResultCache identitySearchCache;
    private final ProjectIdentityCandidateCache projectIdentityCandidateCache;
    private final IdentityAvatarCache identityAvatarCache;
    private final GraphIdentityNegativeCache graphIdentityNegativeCache;
    private final ConfigUiAdoDiscoveryCache discoveryCache;
    private final AtomicLong discoveryAdoRequestCount = new AtomicLong();
    private final AtomicLong identityBackendRequestCount = new AtomicLong();
    private final AtomicLong identityAdoRequestCount = new AtomicLong();
    private final AtomicLong avatarCacheHitCount = new AtomicLong();
    private final AtomicLong avatarCacheMissCount = new AtomicLong();
    private final AtomicLong avatarFailureCachedCount = new AtomicLong();
    private final AtomicLong avatarAvailableCount = new AtomicLong();
    private final AtomicLong avatarFallbackCount = new AtomicLong();
    private final AtomicLong avatarAdoRequestCount = new AtomicLong();

    @Autowired
    public AzureDevOpsConfigDiscoveryService(ApprovalBotProperties properties,
            RuntimeAdoCredentialService credentialService)
    {
        this(credentialService::currentPersonalAccessToken, WebClient.builder().build(), new AzureDevOpsUrlBuilder(),
                new IdentitySearchResultCache(), new ProjectIdentityCandidateCache(), new IdentityAvatarCache(),
                new GraphIdentityNegativeCache(), new ConfigUiAdoDiscoveryCache());
    }

    AzureDevOpsConfigDiscoveryService(String personalAccessToken, ExchangeFunction exchangeFunction,
            AzureDevOpsUrlBuilder urlBuilder)
    {
        this(() -> personalAccessToken, WebClient.builder().exchangeFunction(exchangeFunction).build(), urlBuilder,
                new IdentitySearchResultCache(), new ProjectIdentityCandidateCache(), new IdentityAvatarCache(),
                new GraphIdentityNegativeCache(), new ConfigUiAdoDiscoveryCache());
    }

    AzureDevOpsConfigDiscoveryService(RuntimeAdoCredentialService credentialService,
            ExchangeFunction exchangeFunction, AzureDevOpsUrlBuilder urlBuilder)
    {
        this(credentialService::currentPersonalAccessToken,
                WebClient.builder().exchangeFunction(exchangeFunction).build(), urlBuilder,
                new IdentitySearchResultCache(), new ProjectIdentityCandidateCache(), new IdentityAvatarCache(),
                new GraphIdentityNegativeCache(), new ConfigUiAdoDiscoveryCache());
    }

    AzureDevOpsConfigDiscoveryService(String personalAccessToken, ExchangeFunction exchangeFunction,
            AzureDevOpsUrlBuilder urlBuilder, IdentitySearchResultCache identitySearchCache)
    {
        this(() -> personalAccessToken, WebClient.builder().exchangeFunction(exchangeFunction).build(), urlBuilder,
                identitySearchCache, new ProjectIdentityCandidateCache(), new IdentityAvatarCache(),
                new GraphIdentityNegativeCache(), new ConfigUiAdoDiscoveryCache());
    }

    AzureDevOpsConfigDiscoveryService(String personalAccessToken, ExchangeFunction exchangeFunction,
            AzureDevOpsUrlBuilder urlBuilder, IdentitySearchResultCache identitySearchCache,
            ProjectIdentityCandidateCache projectIdentityCandidateCache, IdentityAvatarCache identityAvatarCache)
    {
        this(() -> personalAccessToken, WebClient.builder().exchangeFunction(exchangeFunction).build(), urlBuilder,
                identitySearchCache, projectIdentityCandidateCache, identityAvatarCache, new GraphIdentityNegativeCache(),
                new ConfigUiAdoDiscoveryCache());
    }

    AzureDevOpsConfigDiscoveryService(
            String personalAccessToken,
            ExchangeFunction exchangeFunction,
            AzureDevOpsUrlBuilder urlBuilder,
            IdentitySearchResultCache identitySearchCache,
            ProjectIdentityCandidateCache projectIdentityCandidateCache,
            IdentityAvatarCache identityAvatarCache,
            GraphIdentityNegativeCache graphIdentityNegativeCache
    ) {
        this(() -> personalAccessToken, WebClient.builder().exchangeFunction(exchangeFunction).build(), urlBuilder,
                identitySearchCache, projectIdentityCandidateCache, identityAvatarCache, graphIdentityNegativeCache,
                new ConfigUiAdoDiscoveryCache());
    }

    AzureDevOpsConfigDiscoveryService(
            String personalAccessToken,
            ExchangeFunction exchangeFunction,
            AzureDevOpsUrlBuilder urlBuilder,
            ConfigUiAdoDiscoveryCache discoveryCache
    ) {
        this(() -> personalAccessToken, WebClient.builder().exchangeFunction(exchangeFunction).build(), urlBuilder,
                new IdentitySearchResultCache(), new ProjectIdentityCandidateCache(), new IdentityAvatarCache(),
                new GraphIdentityNegativeCache(), discoveryCache);
    }

    private AzureDevOpsConfigDiscoveryService(
            Supplier<String> personalAccessTokenSupplier,
            WebClient webClient,
            AzureDevOpsUrlBuilder urlBuilder,
            IdentitySearchResultCache identitySearchCache,
            ProjectIdentityCandidateCache projectIdentityCandidateCache,
            IdentityAvatarCache identityAvatarCache,
            GraphIdentityNegativeCache graphIdentityNegativeCache,
            ConfigUiAdoDiscoveryCache discoveryCache
    ) {
        this.personalAccessTokenSupplier = personalAccessTokenSupplier;
        this.webClient = webClient;
        this.urlBuilder = urlBuilder;
        this.identitySearchCache = identitySearchCache;
        this.projectIdentityCandidateCache = projectIdentityCandidateCache;
        this.identityAvatarCache = identityAvatarCache;
        this.graphIdentityNegativeCache = graphIdentityNegativeCache;
        this.discoveryCache = discoveryCache;
    }

    @Override
    public ConfigLookupResult<String> listProjects(String organization)
    {
        return readList("listProjects", () -> urlBuilder.projectsUrl(organization), AdoRestProjectListResponse.class,
                response -> response.value().stream().map(AdoRestProjectResponse::name).toList());
    }

    @Override
    public ConfigLookupResult<ConfigSelectorOption> listProjectOptions(String organization)
    {
        return readList("listProjectOptions", () -> urlBuilder.projectsUrl(organization),
                AdoRestProjectListResponse.class, response -> response.value().stream()
                        .map(project -> new ConfigSelectorOption(project.name(), project.name(), "", "ADO")).toList());
    }

    @Override
    public ConfigLookupResult<String> validateProject(String organization, String project)
    {
        if (isBlank(project))
        {
            return ConfigLookupResult.error("Project name is required.");
        }
        var cached = discoveryCache.project(organization, project);
        if (cached.isPresent()) {
            logDiscoveryCache("validateProject", true, organization, project, "");
            return new ConfigLookupResult<>(ConfigValidationStatus.VALID, "", List.of(cached.get().projectName()))
                    .withDiagnostics(discoveryDiagnostics("validateProject", true,
                            Map.of("projectMetadataCacheHit", true, "projectId",
                                    Optional.ofNullable(cached.get().projectId()).orElse(""))));
        }
        logDiscoveryCache("validateProject", false, organization, project, "");
        var fetched = fetchBody("validateProject", () -> urlBuilder.projectUrl(organization, project),
                AdoRestProjectResponse.class);
        if (!fetched.successful()) {
            return failedLookup(fetched);
        }
        discoveryCache.putProject(organization, project, fetched.body().id(), fetched.body().name());
        return new ConfigLookupResult<>(ConfigValidationStatus.VALID, "", List.of(fetched.body().name()))
                .withDiagnostics(discoveryDiagnostics("validateProject", false,
                        Map.of("projectMetadataCacheHit", false, "projectId",
                                Optional.ofNullable(fetched.body().id()).orElse(""))));
    }

    @Override
    public ConfigLookupResult<String> listWorkItemTypes(String organization, String project)
    {
        var options = listWorkItemTypeOptions(organization, project);
        return new ConfigLookupResult<>(options.status(), options.message(),
                options.values().stream().map(ConfigSelectorOption::value).toList());
    }

    @Override
    public ConfigLookupResult<ConfigSelectorOption> listWorkItemTypeOptions(String organization, String project)
    {
        var started = System.nanoTime();
        if (isBlank(project))
        {
            return ConfigLookupResult.error("Project name is required.");
        }
        var cachedProject = discoveryCache.project(organization, project);
        var projectCacheHit = cachedProject.isPresent();
        ConfigUiAdoDiscoveryCache.ProjectMetadata projectMetadata;
        if (cachedProject.isPresent()) {
            projectMetadata = cachedProject.get();
        } else {
            var projectFetch = fetchBody(
                    "resolveProjectForWorkItemTypes",
                    () -> urlBuilder.projectUrl(organization, project),
                    AdoRestProjectResponse.class
            );
            if (!projectFetch.successful()) {
                return failedLookup(projectFetch);
            }
            projectMetadata = new ConfigUiAdoDiscoveryCache.ProjectMetadata(
                    projectFetch.body().id(), projectFetch.body().name());
            discoveryCache.putProject(organization, project, projectMetadata.projectId(), projectMetadata.projectName());
        }
        var projectId = projectMetadata.projectId();
        if (isBlank(projectId)) {
            return ConfigLookupResult.error("ADO project id was not returned by project discovery.");
        }

        var cachedProcess = discoveryCache.process(organization, projectId);
        if (cachedProcess.isPresent()) {
            var selection = cachedProcess.get();
            var cachedOptions = discoveryCache.options("work-item-types", organization, projectId,
                    selection.processId());
            if (cachedOptions.isPresent()) {
                logDiscoveryCache("listWorkItemTypeOptions", true, organization, project, "");
                return cachedOptions.get().toResult().withDiagnostics(discoveryDiagnostics(
                        "listWorkItemTypeOptions", true,
                        Map.of("projectMetadataCacheHit", projectCacheHit, "processIdCacheHit", true,
                                "workItemTypeOptionsCacheHit", true, "processPropertyUsed", selection.propertyName(),
                                "failedProcessCandidateSkippedDueToCache", true, "processFailureCacheHit", false,
                                "processFallbackAttempted", false, "projectId", projectId)));
            }
            logDiscoveryCache("listWorkItemTypeOptions", false, organization, project, "");
            var processFetch = fetchBody(
                    "listProcessWorkItemTypes",
                    () -> urlBuilder.processWorkItemTypesUrl(organization, selection.processId()),
                    AdoRestWorkItemTypeListResponse.class
            );
            if (processFetch.successful()) {
                var result = workItemTypeOptions(processFetch.body());
                discoveryCache.putOptions("work-item-types", organization, projectId, selection.processId(), result);
                return result.withDiagnostics(discoveryDiagnostics("listWorkItemTypeOptions", false,
                        Map.of("projectMetadataCacheHit", projectCacheHit, "processIdCacheHit", true,
                                "workItemTypeOptionsCacheHit", false, "processPropertyUsed", selection.propertyName(),
                                "failedProcessCandidateSkippedDueToCache", true, "processFailureCacheHit", false,
                                "processFallbackAttempted", false, "projectId", projectId)));
            }
            discoveryCache.removeProcess(organization, projectId);
        } else {
            logDiscoveryCache("listWorkItemTypeOptions", false, organization, project, "");
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
                "listWorkItemTypeOptions", organization, project, projectId, propertiesFetch.safePath(),
                propertiesFetch.httpStatus(), candidates.size(), elapsedMillis(started));
        if (candidates.isEmpty())
        {
            LOGGER.warn(
                    "ADO config discovery failed operation={} organization={} project={} projectId={} processPropertyUsed={} processId={} path={} failureCategory={} message={} durationMs={}",
                    "listWorkItemTypeOptions", organization, project, projectId, "", "", propertiesFetch.safePath(),
                    "missing-process-id", "No usable process id property was returned by ADO.", elapsedMillis(started));
            return ConfigLookupResult.error("ADO project process id could not be resolved from project properties.");
        }

        ConfigLookupResult<ConfigSelectorOption> lastFailure = ConfigLookupResult.error("ADO project process id could not be resolved.");
        var processFailureCacheHit = false;
        var processFallbackAttempted = false;
        for (var candidate : candidates) {
            if (discoveryCache.processFailure(organization, projectId, candidate.processId())) {
                processFailureCacheHit = true;
                processFallbackAttempted = true;
                LOGGER.info("ADO config discovery skipped cached failed process candidate operation={} organization={} project={} projectId={} processId={} cacheHit=true skippedKnownFailedCandidate=true durationMs={}",
                        "listWorkItemTypeOptions", organization, project, projectId, candidate.processId(),
                        elapsedMillis(started));
                continue;
            }
            var processFetch = fetchBody(
                    "listProcessWorkItemTypes",
                    () -> urlBuilder.processWorkItemTypesUrl(organization, candidate.processId()),
                    AdoRestWorkItemTypeListResponse.class
            );
            if (processFetch.successful()) {
                var result = workItemTypeOptions(processFetch.body());
                var values = result.values();
                discoveryCache.putProcess(organization, projectId, candidate.propertyName(), candidate.processId());
                discoveryCache.putOptions("work-item-types", organization, projectId, candidate.processId(), result);
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
                return result.withDiagnostics(discoveryDiagnostics("listWorkItemTypeOptions", false,
                        Map.of("projectMetadataCacheHit", projectCacheHit, "processIdCacheHit", false,
                                "workItemTypeOptionsCacheHit", false, "processPropertyUsed", candidate.propertyName(),
                                "failedProcessCandidateSkippedDueToCache", processFailureCacheHit,
                                "processFailureCacheHit", processFailureCacheHit,
                                "processFallbackAttempted", processFallbackAttempted, "projectId", projectId)));
            }
            if (processFetch.httpStatus() == 404) {
                discoveryCache.putProcessFailure(organization, projectId, candidate.processId());
            }
            processFallbackAttempted = true;
            logProcessDiscoveryFailure("listWorkItemTypeOptions", organization, project, projectId, candidate.propertyName(), candidate.processId(), processFetch, started);
            lastFailure = failedLookup(processFetch);
        }
        return ConfigLookupResult
                .error("ADO process Work Item Types could not be discovered. " + lastFailure.message());
    }

    @Override
    public ConfigLookupResult<String> listFieldReferenceNames(String organization, String project, String workItemType) {
        var options = listFieldOptions(organization, project, workItemType);
        return new ConfigLookupResult<>(options.status(), options.message(),
                options.values().stream().map(ConfigSelectorOption::value).toList(), options.optionCount(),
                options.diagnostics());
    }

    @Override
    public ConfigLookupResult<ConfigSelectorOption> listFieldOptions(String organization, String project, String workItemType) {
        var scope = discoveryScope(organization, project);
        var cached = discoveryCache.options("fields", organization, scope, workItemType);
        if (cached.isPresent()) {
            logDiscoveryCache("listFieldOptions", true, organization, project, workItemType);
            return cached.get().toResult().withDiagnostics(discoveryDiagnostics("listFieldOptions", true,
                    Map.of("fieldOptionsCacheHit", true)));
        }
        logDiscoveryCache("listFieldOptions", false, organization, project, workItemType);
        var result = readList(
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
        discoveryCache.putOptions("fields", organization, scope, workItemType, result);
        return result.withDiagnostics(discoveryDiagnostics("listFieldOptions", false,
                Map.of("fieldOptionsCacheHit", false)));
    }

    @Override
    public ConfigLookupResult<String> listObservedStateNames(String organization, String project, String workItemType) {
        var options = listStateOptions(organization, project, workItemType);
        return new ConfigLookupResult<>(options.status(), options.message(),
                options.values().stream().map(ConfigSelectorOption::value).toList(), options.optionCount(),
                options.diagnostics());
    }

    @Override
    public ConfigLookupResult<ConfigSelectorOption> listStateOptions(String organization, String project, String workItemType) {
        var scope = discoveryScope(organization, project);
        var cached = discoveryCache.options("states", organization, scope, workItemType);
        if (cached.isPresent()) {
            logDiscoveryCache("listStateOptions", true, organization, project, workItemType);
            return cached.get().toResult().withDiagnostics(discoveryDiagnostics("listStateOptions", true,
                    Map.of("stateOptionsCacheHit", true)));
        }
        logDiscoveryCache("listStateOptions", false, organization, project, workItemType);
        var result = readList(
                "listStateOptions",
                () -> urlBuilder.workItemTypeStatesUrl(organization, project, workItemType),
                AdoRestStateListResponse.class,
                response -> response.value().stream()
                        .map(state -> new ConfigSelectorOption(state.name(), state.name(), "", "ADO"))
                        .toList()
        );
        discoveryCache.putOptions("states", organization, scope, workItemType, result);
        return result.withDiagnostics(discoveryDiagnostics("listStateOptions", false,
                Map.of("stateOptionsCacheHit", false)));
    }

    @Override
    public ConfigLookupResult<String> resolveUsers(String organization, List<String> users)
    {
        if (users == null || users.isEmpty())
        {
            return ConfigLookupResult.error("User list is required.");
        }
        var resolved = new LinkedHashSet<String>();
        var unresolved = new LinkedHashSet<String>();
        for (var user : users)
        {
            var normalizedUser = normalizedIdentity(user);
            if (normalizedUser.isBlank())
            {
                unresolved.add("");
                continue;
            }
            var lookup = searchIdentityOptions(organization, user);
            if (lookup.status() == ConfigValidationStatus.ERROR
                    || lookup.status() == ConfigValidationStatus.NOT_CHECKED
                    || lookup.status() == ConfigValidationStatus.NOT_CONFIGURED)
            {
                return new ConfigLookupResult<>(lookup.status(), lookup.message(), List.of());
            }
            var matched = lookup.values().stream().map(ConfigSelectorOption::value).map(this::normalizedIdentity)
                    .filter(normalizedUser::equals).findFirst();
            if (matched.isPresent())
            {
                resolved.add(matched.get());
            }
            else
            {
                unresolved.add(user);
            }
        }
        if (!unresolved.isEmpty())
        {
            return ConfigLookupResult.error("User identity was not resolved in ADO: " + String.join(", ", unresolved));
        }
        return ConfigLookupResult.valid(List.copyOf(resolved));
    }

    @Override
    public ConfigLookupResult<ConfigSelectorOption> searchIdentityOptions(String organization, String query)
    {
        var normalizedQuery = normalizedSearchQuery(query);
        var backendRequests = identityBackendRequestCount.incrementAndGet();
        if (normalizedQuery.length() < MIN_IDENTITY_QUERY_LENGTH)
        {
            return ConfigLookupResult
                    .<ConfigSelectorOption> notChecked("Type at least 3 characters to search ADO identities.")
                    .withDiagnostics(identityDiagnostics(false, backendRequests, identityAdoRequestCount.get(),
                            "legacy-query", 0, false));
        }
        var cached = identitySearchCache.get(organization, "", normalizedQuery);
        if (cached.isPresent())
        {
            return cached.get().toResult().withDiagnostics(identityDiagnostics(true, backendRequests,
                    identityAdoRequestCount.get(), "legacy-cache", cached.get().values().size(), true));
        }
        identityAdoRequestCount.incrementAndGet();
        var result = readList("searchIdentityOptions",
                () -> urlBuilder.identitySearchUrl(organization, normalizedQuery), AdoRestIdentityListResponse.class,
                this::identityOptions);
        if (result.status() == ConfigValidationStatus.VALID || result.status() == ConfigValidationStatus.WARNING)
        {
            identitySearchCache.put(organization, "", normalizedQuery, result);
        }
        return result.withDiagnostics(
                identityDiagnostics(false, backendRequests, identityAdoRequestCount.get(), "legacy-query", 0, false));
    }

    @Override
    public ConfigLookupResult<ConfigSelectorOption> searchIdentityOptions(String organization, String project,
            String query)
    {
        var started = System.nanoTime();
        var normalizedQuery = normalizedSearchQuery(query);
        var backendRequests = identityBackendRequestCount.incrementAndGet();
        if (normalizedQuery.length() < MIN_IDENTITY_QUERY_LENGTH)
        {
            return ConfigLookupResult
                    .<ConfigSelectorOption> notChecked("Type at least 3 characters to search ADO identities.")
                    .withDiagnostics(identityDiagnostics(false, backendRequests, identityAdoRequestCount.get(),
                            "not-checked", 0, false));
        }
        var poolLookup = projectCandidates(organization, project);
        var pool = poolLookup.candidates();
        var projectId = pool.projectId();
        var cached = identitySearchCache.get(organization, projectId, normalizedQuery);
        if (cached.isPresent())
        {
            var graphCacheHit = cached.get().values().stream().anyMatch(option -> option.source().contains("graph"));
            var result = cached.get().toResult()
                    .withDiagnostics(identityDiagnostics(true, backendRequests, identityAdoRequestCount.get(),
                            "search-cache", pool.values().size(), poolLookup.cacheHit(), projectId,
                            cached.get().values().size(), false, graphCacheHit, false));
            LOGGER.info(
                    "ADO identity discovery completed operation={} organization={} project={} projectId={} queryLength={} status={} finalOptionCount={} sourceSelected={} candidatePoolCacheHit={} candidatePoolSize={} projectPoolMatchCount={} graphFallbackAttempted=false graphCacheHit={} graphNegativeCacheHit=false backendRequestCount={} adoRequestCount={} durationMs={}",
                    "searchIdentityOptions", organization, project, projectId, normalizedQuery.length(),
                    result.status(), result.optionCount(), "search-cache", poolLookup.cacheHit(), pool.values().size(),
                    result.optionCount(), graphCacheHit, backendRequests, identityAdoRequestCount.get(),
                    elapsedMillis(started));
            return result;
        }

        var values = pool.values().stream().filter(option -> identityContains(option, normalizedQuery)).toList();
        var projectPoolMatchCount = values.size();
        var source = pool.available() ? "project-scope" : "graph-query";
        var graphFallbackAttempted = false;
        var graphNegativeCacheHit = false;
        if (values.isEmpty())
        {
            graphNegativeCacheHit = graphIdentityNegativeCache.matches(organization, projectId, normalizedQuery);
            if (graphNegativeCacheHit)
            {
                source = "graph-negative-cache";
            }
            else
            {
                graphFallbackAttempted = true;
                var fallback = graphSubjectQuery(organization, normalizedQuery, pool.scopeDescriptor());
                if (fallback.status() == ConfigValidationStatus.VALID)
                {
                    values = mergeIdentityOptions(values, fallback.values());
                    source = pool.available() ? "project-scope+graph-query" : "graph-query";
                }
                else if (fallback.status() == ConfigValidationStatus.WARNING)
                {
                    graphIdentityNegativeCache.put(organization, projectId, normalizedQuery);
                    source = "graph-query-empty";
                }
                else
                {
                    return fallback.withDiagnostics(identityDiagnostics(false, backendRequests,
                            identityAdoRequestCount.get(), "graph-query-failed", pool.values().size(),
                            poolLookup.cacheHit(), projectId, projectPoolMatchCount, true, false, false));
                }
            }
        }
        var result = values.isEmpty() ? ConfigLookupResult.<ConfigSelectorOption> warning(graphNegativeCacheHit
                ? "No project-pool match; Graph fallback skipped because a broader or identical query was already empty."
                : "No project-pool match; Graph fallback returned no matches.") : ConfigLookupResult.valid(values);
        if (result.status() == ConfigValidationStatus.VALID)
        {
            identitySearchCache.put(organization, projectId, normalizedQuery, result);
        }
        var response = result.withDiagnostics(identityDiagnostics(false, backendRequests, identityAdoRequestCount.get(),
                source, pool.values().size(), poolLookup.cacheHit(), projectId, projectPoolMatchCount,
                graphFallbackAttempted, false, graphNegativeCacheHit));
        LOGGER.info(
                "ADO identity discovery completed operation={} organization={} project={} projectId={} queryLength={} status={} finalOptionCount={} sourceSelected={} candidatePoolCacheHit={} candidatePoolSize={} projectPoolMatchCount={} graphFallbackAttempted={} graphCacheHit=false graphNegativeCacheHit={} backendRequestCount={} adoRequestCount={} durationMs={}",
                "searchIdentityOptions", organization, project, projectId, normalizedQuery.length(), response.status(),
                response.optionCount(), source, poolLookup.cacheHit(), pool.values().size(), projectPoolMatchCount,
                graphFallbackAttempted, graphNegativeCacheHit, backendRequests, identityAdoRequestCount.get(),
                elapsedMillis(started));
        return response;
    }

    @Override
    public Optional<IdentityAvatar> loadIdentityAvatar(String organization, String descriptor)
    {
        var personalAccessToken = currentPersonalAccessToken();
        if (isBlank(organization) || isBlank(descriptor) || isBlank(personalAccessToken))
        {
            avatarFallbackCount.incrementAndGet();
            return Optional.empty();
        }
        var started = System.nanoTime();
        var descriptorHash = Integer.toUnsignedString(normalizedIdentity(descriptor).hashCode(), 16);
        var cached = identityAvatarCache.get(organization, descriptor);
        if (cached.isPresent())
        {
            avatarCacheHitCount.incrementAndGet();
            if (!cached.get().available())
            {
                avatarFailureCachedCount.incrementAndGet();
                avatarFallbackCount.incrementAndGet();
                LOGGER.info(
                        "ADO avatar unavailable operation=loadIdentityAvatar descriptorHash={} cacheHit=true failureCached=true durationMs={}",
                        descriptorHash, elapsedMillis(started));
                return Optional.empty();
            }
            avatarAvailableCount.incrementAndGet();
            return Optional.of(new IdentityAvatar(cached.get().bytes(), cached.get().contentType(), true));
        }
        avatarCacheMissCount.incrementAndGet();
        avatarAdoRequestCount.incrementAndGet();
        var fetch = fetchAvatarBinary(organization, descriptor, personalAccessToken);
        if (!fetch.successful() || fetch.bytes().length == 0 || fetch.bytes().length > MAX_AVATAR_BYTES)
        {
            identityAvatarCache.putFailure(organization, descriptor);
            avatarFallbackCount.incrementAndGet();
            LOGGER.warn(
                    "ADO avatar unavailable operation=loadIdentityAvatar descriptorHash={} httpStatus={} contentType={} byteSize={} cacheHit=false failureCached=true durationMs={} reason={}",
                    descriptorHash, fetch.httpStatus(), fetch.contentType(), fetch.bytes().length,
                    elapsedMillis(started), fetch.failureMessage());
            return Optional.empty();
        }
        identityAvatarCache.putSuccess(organization, descriptor, fetch.bytes(), fetch.contentType());
        avatarAvailableCount.incrementAndGet();
        LOGGER.info(
                "ADO avatar loaded operation=loadIdentityAvatar descriptorHash={} httpStatus={} contentType={} byteSize={} cacheHit=false durationMs={}",
                descriptorHash, fetch.httpStatus(), fetch.contentType(), fetch.bytes().length, elapsedMillis(started));
        return Optional.of(new IdentityAvatar(fetch.bytes(), fetch.contentType(), false));
    }

    private AvatarBinaryFetch fetchAvatarBinary(String organization, String descriptor, String personalAccessToken)
    {
        try
        {
            var targetUrl = urlBuilder.graphAvatarUrl(organization, descriptor);
            return webClient.get().uri(URI.create(targetUrl))
                    .header(HttpHeaders.AUTHORIZATION, new AzureDevOpsAuth().basicAuthHeader(personalAccessToken))
                    .exchangeToMono(this::handleAvatarResponse).onErrorResume(error -> Mono.just(AvatarBinaryFetch
                            .failure(0, "", sanitize(error.getClass().getSimpleName() + ": " + error.getMessage()))))
                    .block();
        }
        catch (IllegalArgumentException ex)
        {
            return AvatarBinaryFetch.failure(0, "", sanitize(ex.getMessage()));
        }
    }

    private Mono<AvatarBinaryFetch> handleAvatarResponse(ClientResponse response)
    {
        var status = response.statusCode().value();
        var contentType = response.headers().contentType().orElse(MediaType.APPLICATION_OCTET_STREAM);
        var safeContentType = contentType.toString();
        if (!response.statusCode().is2xxSuccessful())
        {
            return response.releaseBody().thenReturn(AvatarBinaryFetch.failure(status, safeContentType,
                    "ADO avatar request failed with status " + status + "."));
        }
        if (!MediaType.IMAGE_PNG.isCompatibleWith(contentType))
        {
            return response.releaseBody().thenReturn(
                    AvatarBinaryFetch.failure(status, safeContentType, "ADO avatar response was not image/png."));
        }
        return response.bodyToMono(byte[].class).map(bytes -> AvatarBinaryFetch.success(bytes, status, safeContentType))
                .defaultIfEmpty(AvatarBinaryFetch.failure(status, safeContentType, "ADO avatar response was empty."));
    }

    private CandidatePoolLookup projectCandidates(String organization, String project)
    {
        if (isBlank(project))
        {
            return new CandidatePoolLookup(new ProjectIdentityCandidateCache.ProjectIdentityCandidates(false, "", "",
                    "graph-query", List.of()), false);
        }
        var cached = projectIdentityCandidateCache.get(organization, project);
        if (cached.isPresent())
        {
            return new CandidatePoolLookup(cached.get(), true);
        }
        var candidates = loadProjectCandidates(organization, project);
        projectIdentityCandidateCache.put(organization, project, candidates);
        return new CandidatePoolLookup(candidates, false);
    }

    private ProjectIdentityCandidateCache.ProjectIdentityCandidates loadProjectCandidates(String organization,
            String project)
    {
        identityAdoRequestCount.incrementAndGet();
        var projectFetch = fetchBody("resolveProjectForIdentitySearch",
                () -> urlBuilder.projectUrl(organization, project), AdoRestProjectResponse.class);
        if (!projectFetch.successful() || isBlank(projectFetch.body().id()))
        {
            return new ProjectIdentityCandidateCache.ProjectIdentityCandidates(false, "", "", "graph-query", List.of());
        }
        var projectId = projectFetch.body().id();
        identityAdoRequestCount.incrementAndGet();
        var descriptorFetch = fetchBody("resolveProjectScopeDescriptor",
                () -> urlBuilder.graphDescriptorUrl(organization, projectId), AdoGraphDescriptorResponse.class);
        if (!descriptorFetch.successful() || isBlank(descriptorFetch.body().value()))
        {
            return new ProjectIdentityCandidateCache.ProjectIdentityCandidates(false, projectId, "", "graph-query",
                    List.of());
        }
        var scopeDescriptor = descriptorFetch.body().value();
        identityAdoRequestCount.incrementAndGet();
        var usersFetch = fetchBody("listProjectScopedGraphUsers",
                () -> urlBuilder.scopedGraphUsersUrl(organization, scopeDescriptor), AdoGraphUserListResponse.class);
        if (!usersFetch.successful())
        {
            return new ProjectIdentityCandidateCache.ProjectIdentityCandidates(false, projectId, scopeDescriptor,
                    "graph-query", List.of());
        }
        // Scoped Graph users are a bounded candidate set, not proof of complete project permission membership.
        var options = usersFetch.body().value().stream().limit(MAX_PROJECT_CANDIDATES)
                .map(user -> graphUserOption(organization, user, "project-scope"))
                .filter(ConfigSelectorOption::resolved).toList();
        return new ProjectIdentityCandidateCache.ProjectIdentityCandidates(true, projectId, scopeDescriptor,
                "project-scope", options);
    }

    private ConfigLookupResult<ConfigSelectorOption> graphSubjectQuery(String organization, String query,
            String scopeDescriptor)
    {
        identityAdoRequestCount.incrementAndGet();
        return readPostList("queryGraphIdentities", () -> urlBuilder.graphSubjectQueryUrl(organization),
                new GraphSubjectQueryRequest(query, scopeDescriptor, List.of("User")),
                AdoGraphSubjectQueryResponse.class,
                response -> response.items().stream().filter(this::isUserSubject)
                        .map(user -> graphUserOption(organization, user, "graph-query"))
                        .filter(ConfigSelectorOption::resolved).toList());
    }

    private boolean isUserSubject(AdoGraphUserResponse subject)
    {
        return isBlank(subject.subjectKind()) || "user".equalsIgnoreCase(subject.subjectKind());
    }

    private List<ConfigSelectorOption> mergeIdentityOptions(List<ConfigSelectorOption> primary,
            List<ConfigSelectorOption> fallback)
    {
        var merged = new LinkedHashMap<String, ConfigSelectorOption>();
        primary.forEach(option -> merged.put(normalizedIdentity(option.value()), option));
        fallback.forEach(option -> merged.putIfAbsent(normalizedIdentity(option.value()), option));
        return List.copyOf(merged.values());
    }

    private boolean identityContains(ConfigSelectorOption option, String query)
    {
        return normalizedSearchQuery(option.displayName() + " " + option.description() + " " + option.value())
                .contains(query);
    }

    private ConfigSelectorOption graphUserOption(String organization, AdoGraphUserResponse user, String source)
    {
        var value = normalizedIdentity(firstNonBlank(user.mailAddress(), user.principalName()));
        var resolved = isResolvableIdentity(value);
        var displayName = firstNonBlank(user.displayName(), value);
        var displayLabel = displayName.equals(value) ? value : displayName + " <" + value + ">";
        return new ConfigSelectorOption(value, displayLabel, value, source, user.descriptor(),
                avatarProxyUrl(organization, user.descriptor()), resolved);
    }

    private String avatarProxyUrl(String organization, String descriptor)
    {
        if (isBlank(descriptor))
        {
            return "";
        }
        return "/api/config-ui/discovery/users/avatar?organization=" + encodeQuery(organization) + "&descriptor="
                + encodeQuery(descriptor);
    }

    private String encodeQuery(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private int resultCount(List<ConfigSelectorOption> values)
    {
        return values == null ? 0 : values.size();
    }

    private Map<String, Object> identityDiagnostics(boolean cacheHit, long backendRequestCount, long adoRequestCount,
            String source, int candidatePoolSize, boolean candidatePoolCacheHit)
    {
        return Map.ofEntries(Map.entry("backendCacheHit", cacheHit), Map.entry("backendCacheMiss", !cacheHit),
                Map.entry("backendRequestCount", backendRequestCount), Map.entry("adoRequestCount", adoRequestCount),
                Map.entry("candidatePoolSource", source), Map.entry("candidatePoolSize", candidatePoolSize),
                Map.entry("candidatePoolCacheHit", candidatePoolCacheHit),
                Map.entry("avatarCacheHitCount", avatarCacheHitCount.get()),
                Map.entry("avatarCacheMissCount", avatarCacheMissCount.get()),
                Map.entry("avatarFailureCachedCount", avatarFailureCachedCount.get()),
                Map.entry("avatarAvailableCount", avatarAvailableCount.get()),
                Map.entry("avatarFallbackCount", avatarFallbackCount.get()),
                Map.entry("avatarAdoRequestCount", avatarAdoRequestCount.get()));
    }

    private Map<String, Object> identityDiagnostics(boolean cacheHit, long backendRequestCount, long adoRequestCount,
            String source, int candidatePoolSize, boolean candidatePoolCacheHit, String projectId,
            int projectPoolMatchCount, boolean graphFallbackAttempted, boolean graphCacheHit,
            boolean graphNegativeCacheHit)
    {
        var diagnostics = new LinkedHashMap<>(identityDiagnostics(cacheHit, backendRequestCount, adoRequestCount,
                source, candidatePoolSize, candidatePoolCacheHit));
        diagnostics.put("projectId", projectId);
        diagnostics.put("sourceSelected", source);
        diagnostics.put("projectPoolMatchCount", projectPoolMatchCount);
        diagnostics.put("graphFallbackAttempted", graphFallbackAttempted);
        diagnostics.put("graphCacheHit", graphCacheHit);
        diagnostics.put("graphNegativeCacheHit", graphNegativeCacheHit);
        return Map.copyOf(diagnostics);
    }

    private List<ConfigSelectorOption> identityOptions(AdoRestIdentityListResponse response)
    {
        return response.items().stream().map(this::identityOption).filter(option -> !option.value().isBlank()).toList();
    }

    private ConfigSelectorOption identityOption(AdoRestIdentityResponse identity)
    {
        var value = normalizedIdentity(
                firstNonBlank(identity.uniqueName(), identity.mail(), identity.account(), identity.signInAddress()));
        if (value.isBlank())
        {
            return new ConfigSelectorOption("", identity.displayLabel(), "", "ADO", identity.subjectDescriptor());
        }
        var displayName = firstNonBlank(identity.displayName(), identity.providerDisplayName(), value);
        var displayLabel = displayName.equals(value) ? value : displayName + " <" + value + ">";
        return new ConfigSelectorOption(value, displayLabel, value, "legacy-query", identity.subjectDescriptor());
    }

    private <R, T> ConfigLookupResult<R> readOne(String operation, Supplier<String> url, Class<T> responseType,
            Function<T, List<R>> mapper)
    {
        return read(operation, url, responseType, mapper);
    }

    private <R, T> ConfigLookupResult<R> readList(String operation, Supplier<String> url, Class<T> responseType,
            Function<T, List<R>> mapper)
    {
        return read(operation, url, responseType, mapper);
    }

    private <R, T> ConfigLookupResult<R> readPostList(String operation, Supplier<String> url, Object requestBody,
            Class<T> responseType, Function<T, List<R>> mapper)
    {
        var personalAccessToken = currentPersonalAccessToken();
        if (isBlank(personalAccessToken))
        {
            return ConfigLookupResult.notConfigured(MISSING_PAT_MESSAGE);
        }
        try
        {
            var targetUrl = url.get();
            var safePath = safePath(targetUrl);
            return webClient.post().uri(URI.create(targetUrl))
                    .header(HttpHeaders.AUTHORIZATION, new AzureDevOpsAuth().basicAuthHeader(personalAccessToken))
                    .bodyValue(requestBody)
                    .exchangeToMono(response -> handleResponse(operation, safePath, response, responseType, mapper))
                    .onErrorResume(error -> Mono.just(handleTransportError(operation, safePath, error))).block();
        }
        catch (IllegalArgumentException ex)
        {
            return ConfigLookupResult.error(ex.getMessage());
        }
    }

    private <R, T> ConfigLookupResult<R> read(String operation, Supplier<String> url, Class<T> responseType,
            Function<T, List<R>> mapper)
    {
        var personalAccessToken = currentPersonalAccessToken();
        if (isBlank(personalAccessToken))
        {
            return ConfigLookupResult.notConfigured(MISSING_PAT_MESSAGE);
        }
        try
        {
            var targetUrl = url.get();
            var safePath = safePath(targetUrl);
            discoveryAdoRequestCount.incrementAndGet();
            return webClient.get()
                    .uri(URI.create(targetUrl))
                    .header(HttpHeaders.AUTHORIZATION, new AzureDevOpsAuth().basicAuthHeader(personalAccessToken))
                    .exchangeToMono(response -> handleResponse(operation, safePath, response, responseType, mapper))
                    .onErrorResume(error -> Mono.just(handleTransportError(operation, safePath, error))).block();
        }
        catch (IllegalArgumentException ex)
        {
            return ConfigLookupResult.error(ex.getMessage());
        }
    }

    private <T> AdoFetchResult<T> fetchBody(String operation, Supplier<String> url, Class<T> responseType)
    {
        var personalAccessToken = currentPersonalAccessToken();
        if (isBlank(personalAccessToken))
        {
            return AdoFetchResult.failure(ConfigLookupResult.notConfigured(MISSING_PAT_MESSAGE), 0, "");
        }
        try
        {
            var targetUrl = url.get();
            var safePath = safePath(targetUrl);
            discoveryAdoRequestCount.incrementAndGet();
            return webClient.get()
                    .uri(URI.create(targetUrl))
                    .header(HttpHeaders.AUTHORIZATION, new AzureDevOpsAuth().basicAuthHeader(personalAccessToken))
                    .exchangeToMono(response -> handleFetchResponse(operation, safePath, response, responseType))
                    .onErrorResume(error -> Mono.just(
                            AdoFetchResult.failure(handleTransportError(operation, safePath, error), 0, safePath)))
                    .block();
        }
        catch (IllegalArgumentException ex)
        {
            return AdoFetchResult.failure(ConfigLookupResult.error(ex.getMessage()), 0, "");
        }
    }

    private <T> Mono<AdoFetchResult<T>> handleFetchResponse(String operation, String safePath, ClientResponse response,
            Class<T> responseType)
    {
        var status = response.statusCode().value();
        if (response.statusCode().is2xxSuccessful())
        {
            return response.bodyToMono(responseType).map(body -> AdoFetchResult.success(body, status, safePath))
                    .defaultIfEmpty(AdoFetchResult.failure(
                            ConfigLookupResult.warning("ADO discovery returned no response body."), status, safePath));
        }
        return response.bodyToMono(String.class).defaultIfEmpty("").map(
                body -> AdoFetchResult.failure(handleHttpFailure(operation, safePath, status, body), status, safePath));
    }

    private <R> ConfigLookupResult<R> failedLookup(AdoFetchResult<?> fetchResult)
    {
        return new ConfigLookupResult<>(fetchResult.failure().status(), fetchResult.failure().message(), List.of());
    }

    private <R, T> Mono<ConfigLookupResult<R>> handleResponse(String operation, String safePath,
            ClientResponse response, Class<T> responseType, Function<T, List<R>> mapper)
    {
        var status = response.statusCode().value();
        if (response.statusCode().is2xxSuccessful())
        {
            return response.bodyToMono(responseType)
                    .map(body -> mapSuccessfulResponse(operation, safePath, status, body, mapper))
                    .defaultIfEmpty(ConfigLookupResult.valid(List.of()));
        }
        return response.bodyToMono(String.class).defaultIfEmpty("")
                .map(body -> handleHttpFailure(operation, safePath, status, body));
    }

    private <R, T> ConfigLookupResult<R> mapSuccessfulResponse(String operation, String safePath, int status, T body,
            Function<T, List<R>> mapper)
    {
        var values = mapper.apply(body);
        var result = ConfigLookupResult.valid(values);
        LOGGER.info(
                "ADO config discovery completed operation={} path={} httpStatus={} rawAdoCount={} mappedOptionCount={} finalOptionCount={}",
                operation, safePath, status, rawAdoCount(body), values.size(), result.optionCount());
        return result;
    }

    private <R> ConfigLookupResult<R> handleHttpFailure(String operation, String safePath, int status,
            String responseBody)
    {
        var safeBody = sanitize(responseBody);
        LOGGER.warn("ADO config discovery failed operation={} path={} httpStatus={} adoResponse={}", operation,
                safePath, status, safeBody);
        if (status == 401 || status == 403)
        {
            return ConfigLookupResult
                    .error("ADO discovery authorization failed with status " + status + detail(safeBody));
        }
        if (status == 404)
        {
            return ConfigLookupResult.error("ADO discovery resource was not found. status=404" + detail(safeBody));
        }
        if (status == 429 || status >= 500)
        {
            return ConfigLookupResult.error("ADO discovery failed with retryable status " + status + detail(safeBody));
        }
        return ConfigLookupResult.error("ADO discovery failed with status " + status + detail(safeBody));
    }

    private <R> ConfigLookupResult<R> handleTransportError(String operation, String safePath, Throwable error)
    {
        var safeDetail = sanitize(error.getClass().getSimpleName() + ": " + error.getMessage());
        LOGGER.warn("ADO config discovery transport failure operation={} path={} detail={}", operation, safePath,
                safeDetail);
        return ConfigLookupResult.error("ADO discovery transport error: " + safeDetail);
    }

    private String detail(String safeBody)
    {
        return safeBody.isBlank() ? "." : ". ADO response: " + safeBody;
    }

    private Integer rawAdoCount(Object body)
    {
        if (body instanceof AdoRestProjectListResponse response)
        {
            return response.rawCount();
        }
        if (body instanceof AdoRestWorkItemTypeListResponse response)
        {
            return response.rawCount();
        }
        if (body instanceof AdoRestFieldListResponse response)
        {
            return response.rawCount();
        }
        if (body instanceof AdoRestStateListResponse response)
        {
            return response.rawCount();
        }
        if (body instanceof AdoRestIdentityListResponse response)
        {
            return response.rawCount();
        }
        if (body instanceof AdoGraphSubjectQueryResponse response)
        {
            return response.rawCount();
        }
        return null;
    }

    private ConfigLookupResult<ConfigSelectorOption> workItemTypeOptions(AdoRestWorkItemTypeListResponse response) {
        return ConfigLookupResult.valid(response.value().stream()
                .map(type -> new ConfigSelectorOption(type.name(), type.name(), type.description(), "ADO",
                        type.referenceName()))
                .toList());
    }

    private String discoveryScope(String organization, String project) {
        return discoveryCache.project(organization, project)
                .map(ConfigUiAdoDiscoveryCache.ProjectMetadata::projectId)
                .filter(projectId -> !isBlank(projectId))
                .orElse(project);
    }

    private Map<String, Object> discoveryDiagnostics(String operation, boolean cacheHit,
            Map<String, Object> details) {
        var diagnostics = new LinkedHashMap<String, Object>();
        diagnostics.put("lastDiscoveryOperation", operation);
        diagnostics.put("discoveryCacheHit", cacheHit);
        diagnostics.put("adoDiscoveryRequestCount", discoveryAdoRequestCount.get());
        diagnostics.putAll(details);
        return Map.copyOf(diagnostics);
    }

    private void logDiscoveryCache(String operation, boolean cacheHit, String organization, String project,
            String workItemType) {
        LOGGER.info("ADO config discovery cache operation={} organization={} project={} workItemType={} cacheHit={} cacheMiss={} skippedBecauseCurrent=false inFlightDeduped=false processFailureCacheHit=false durationMs=0",
                operation, organization, project, workItemType, cacheHit, !cacheHit);
    }

    private List<ProcessIdCandidate> processIdCandidates(AdoRestProjectPropertiesResponse response) {
        return PROCESS_ID_PROPERTY_PREFERENCE.stream()
                .flatMap(propertyName -> response.value().stream()
                        .filter(property -> propertyName.equals(property.name()))
                        .map(property -> new ProcessIdCandidate(property.name(), property.valueAsString())))
                .filter(candidate -> !isBlank(candidate.processId())).distinct().toList();
    }

    private void logProcessDiscoveryFailure(String operation, String organization, String project, String projectId,
            String processPropertyUsed, String processId, AdoFetchResult<?> fetchResult, long started)
    {
        LOGGER.warn(
                "ADO config discovery failed operation={} organization={} project={} projectId={} processPropertyUsed={} processId={} path={} httpStatus={} failureCategory={} message={} durationMs={}",
                operation, organization, project, projectId, processPropertyUsed, processId, fetchResult.safePath(),
                fetchResult.httpStatus(), fetchResult.failure().status(), sanitize(fetchResult.failure().message()),
                elapsedMillis(started));
    }

    private long elapsedMillis(long started)
    {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private String safePath(String url)
    {
        var uri = URI.create(url);
        var query = uri.getRawQuery();
        return query == null || query.isBlank() ? uri.getRawPath() : uri.getRawPath() + "?" + query;
    }

    private String sanitize(String value)
    {
        if (value == null)
        {
            return "";
        }
        var sanitized = value.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ").replace('\r', ' ').replace('\n', ' ')
                .replace('\t', ' ').replaceAll("(?i)authorization\\s*[:=]\\s*\\S+", "Authorization=[redacted]");
        var personalAccessToken = currentPersonalAccessToken();
        if (!isBlank(personalAccessToken))
        {
            sanitized = sanitized.replace(personalAccessToken, "[redacted]");
        }
        sanitized = sanitized.trim();
        if (sanitized.length() > MAX_SAFE_DETAIL_LENGTH)
        {
            return sanitized.substring(0, MAX_SAFE_DETAIL_LENGTH) + "...";
        }
        return sanitized;
    }

    private boolean isBlank(String value)
    {
        return value == null || value.isBlank();
    }

    private String currentPersonalAccessToken()
    {
        var token = personalAccessTokenSupplier.get();
        return token == null ? "" : token;
    }

    private String normalizedIdentity(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizedSearchQuery(String value)
    {
        return normalizedIdentity(value).replaceAll("\\s+", " ");
    }

    private boolean isResolvableIdentity(String value)
    {
        return value != null && (value.contains("@") || value.contains("\\"));
    }

    private String firstNonBlank(String... values)
    {
        for (var value : values)
        {
            if (!isBlank(value))
            {
                return value.trim();
            }
        }
        return "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestProjectListResponse(Integer count, List<AdoRestProjectResponse> value)
    {
        AdoRestProjectListResponse
        {
            value = value == null ? List.of() : List.copyOf(value);
        }

        Integer rawCount()
        {
            return count == null ? value.size() : count;
        }
    }

    record AdoFetchResult<T>(T body, ConfigLookupResult<?> failure, int httpStatus, String safePath)
    {
        static <T> AdoFetchResult<T> success(T body, int httpStatus, String safePath)
        {
            return new AdoFetchResult<>(body, null, httpStatus, safePath);
        }

        static <T> AdoFetchResult<T> failure(ConfigLookupResult<?> failure, int httpStatus, String safePath)
        {
            return new AdoFetchResult<>(null, failure, httpStatus, safePath);
        }

        boolean successful()
        {
            return failure == null;
        }
    }

    record AvatarBinaryFetch(byte[] bytes, int httpStatus, String contentType, String failureMessage)
    {
        AvatarBinaryFetch
        {
            bytes = bytes == null ? new byte[0] : bytes.clone();
            contentType = contentType == null ? "" : contentType;
            failureMessage = failureMessage == null ? "" : failureMessage;
        }

        static AvatarBinaryFetch success(byte[] bytes, int httpStatus, String contentType)
        {
            return new AvatarBinaryFetch(bytes, httpStatus, contentType, "");
        }

        static AvatarBinaryFetch failure(int httpStatus, String contentType, String message)
        {
            return new AvatarBinaryFetch(new byte[0], httpStatus, contentType, message);
        }

        boolean successful()
        {
            return failureMessage.isBlank();
        }
    }

    record ProcessIdCandidate(String propertyName, String processId)
    {
    }

    record CandidatePoolLookup(ProjectIdentityCandidateCache.ProjectIdentityCandidates candidates, boolean cacheHit)
    {
    }

    record GraphSubjectQueryRequest(String query, String scopeDescriptor, List<String> subjectKind)
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestProjectResponse(String id, String name)
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoGraphDescriptorResponse(String value)
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoGraphUserListResponse(Integer count, List<AdoGraphUserResponse> value)
    {
        AdoGraphUserListResponse
        {
            value = value == null ? List.of() : List.copyOf(value);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoGraphSubjectQueryResponse(Integer count, List<AdoGraphUserResponse> value,
                                        List<AdoGraphUserResponse> identities)
    {
        AdoGraphSubjectQueryResponse
        {
            value = value == null ? List.of() : List.copyOf(value);
            identities = identities == null ? List.of() : List.copyOf(identities);
        }

        List<AdoGraphUserResponse> items()
        {
            return value.isEmpty() ? identities : value;
        }

        Integer rawCount()
        {
            return count == null ? items().size() : count;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoGraphUserResponse(@JsonAlias("subjectDescriptor") String descriptor, String displayName, @JsonAlias({
            "uniqueName", "signInAddress", "samAccountName" }) String principalName, String mailAddress,
                                @JsonAlias("entityType") String subjectKind)
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestWorkItemTypeListResponse(Integer count, List<AdoRestWorkItemTypeResponse> value)
    {
        AdoRestWorkItemTypeListResponse
        {
            value = value == null ? List.of() : List.copyOf(value);
        }

        Integer rawCount()
        {
            return count == null ? value.size() : count;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestWorkItemTypeResponse(String name, String referenceName, String description)
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestProjectPropertiesResponse(Integer count, List<AdoRestProjectPropertyResponse> value)
    {
        AdoRestProjectPropertiesResponse
        {
            value = value == null ? List.of() : List.copyOf(value);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestProjectPropertyResponse(String name, JsonNode value)
    {
        String valueAsString()
        {
            if (value == null || value.isNull())
            {
                return "";
            }
            if (value.isTextual())
            {
                return value.asText();
            }
            if (value.has("id"))
            {
                return value.get("id").asText();
            }
            if (value.has("value"))
            {
                return value.get("value").asText();
            }
            return "";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestFieldListResponse(Integer count, List<AdoRestFieldResponse> value)
    {
        AdoRestFieldListResponse
        {
            value = value == null ? List.of() : List.copyOf(value);
        }

        Integer rawCount()
        {
            return count == null ? value.size() : count;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestFieldResponse(String referenceName, String name, String type)
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestStateListResponse(Integer count, List<AdoRestStateResponse> value)
    {
        AdoRestStateListResponse
        {
            value = value == null ? List.of() : List.copyOf(value);
        }

        Integer rawCount()
        {
            return count == null ? value.size() : count;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestStateResponse(String name)
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestIdentityListResponse(Integer count, List<AdoRestIdentityResponse> value,
                                       List<AdoRestIdentityResponse> identities)
    {
        AdoRestIdentityListResponse
        {
            value = value == null ? List.of() : List.copyOf(value);
            identities = identities == null ? List.of() : List.copyOf(identities);
        }

        List<AdoRestIdentityResponse> items()
        {
            return value.isEmpty() ? identities : value;
        }

        Integer rawCount()
        {
            return count == null ? items().size() : count;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdoRestIdentityResponse(String id, String descriptor, String subjectDescriptor, String displayName,
                                   String providerDisplayName, String uniqueName, JsonNode properties)
    {
        String mail()
        {
            return property("Mail");
        }

        String account()
        {
            return property("Account");
        }

        String signInAddress()
        {
            return property("SignInAddress");
        }

        String displayLabel()
        {
            return firstNonBlank(displayName, providerDisplayName, uniqueName, id);
        }

        public String subjectDescriptor()
        {
            return firstNonBlank(subjectDescriptor, descriptor, id);
        }

        private String property(String name)
        {
            if (properties == null || properties.isNull() || !properties.has(name))
            {
                return "";
            }
            var property = properties.get(name);
            if (property.isTextual())
            {
                return property.asText();
            }
            if (property.has("$value"))
            {
                return property.get("$value").asText();
            }
            if (property.has("value"))
            {
                return property.get("value").asText();
            }
            return "";
        }

        private String firstNonBlank(String... values)
        {
            for (var value : values)
            {
                if (value != null && !value.isBlank())
                {
                    return value.trim();
                }
            }
            return "";
        }
    }
}
