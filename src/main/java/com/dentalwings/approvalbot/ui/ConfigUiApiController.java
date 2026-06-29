package com.dentalwings.approvalbot.ui;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config-ui")
public class ConfigUiApiController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigUiApiController.class);
    private static final int MAX_SAFE_MESSAGE_LENGTH = 500;

    private final ApplicationLocalConfigService configService;
    private final AdoConfigDiscoveryService discoveryService;

    public ConfigUiApiController(ApplicationLocalConfigService configService, AdoConfigDiscoveryService discoveryService) {
        this.configService = configService;
        this.discoveryService = discoveryService;
    }

    @GetMapping("/model")
    public ConfigUiModel loadModel() {
        return configService.load();
    }

    @PostMapping("/preview")
    public AdoConfigPreview preview(@RequestBody ConfigUiModel model) {
        return configService.previewLocalDraft(model);
    }

    @PostMapping("/validate")
    public ConfigValidationResult validate(@RequestBody ConfigUiModel model) {
        return configService.validate(model);
    }

    @PostMapping("/save")
    public Map<String, Object> save(@RequestBody ConfigUiModel model) {
        var path = configService.save(model);
        return Map.of(
                "message", "application-local.yml actualizado sin persistir secretos.",
                "path", path.toString(),
                "preview", configService.previewLocalDraft(model)
        );
    }

    @PostMapping("/discovery/projects")
    public ConfigLookupResult<ConfigSelectorOption> projects(@RequestBody ConfigDiscoveryRequest request) {
        return withDiscoveryDiagnostics(
                "configUiListProjects",
                request,
                () -> discoveryService.listProjectOptions(request.organization())
        );
    }

    @PostMapping("/discovery/validate-project")
    public ConfigLookupResult<String> validateProject(@RequestBody ConfigDiscoveryRequest request) {
        return withDiscoveryDiagnostics(
                "configUiVerifyProject",
                request,
                () -> discoveryService.validateProject(request.organization(), request.project())
        );
    }

    @PostMapping("/discovery/work-item-types")
    public ConfigLookupResult<ConfigSelectorOption> workItemTypes(@RequestBody ConfigDiscoveryRequest request) {
        return withDiscoveryDiagnostics(
                "configUiLoadWorkItemTypes",
                request,
                () -> discoveryService.listWorkItemTypeOptions(request.organization(), request.project())
        );
    }

    @PostMapping("/discovery/fields")
    public ConfigLookupResult<ConfigSelectorOption> fields(@RequestBody ConfigDiscoveryRequest request) {
        return withDiscoveryDiagnostics(
                "configUiLoadFields",
                request,
                () -> discoveryService.listFieldOptions(request.organization(), request.project(), request.workItemType())
        );
    }

    @PostMapping("/discovery/states")
    public ConfigLookupResult<ConfigSelectorOption> states(@RequestBody ConfigDiscoveryRequest request) {
        return withDiscoveryDiagnostics(
                "configUiLoadStates",
                request,
                () -> discoveryService.listStateOptions(request.organization(), request.project(), request.workItemType())
        );
    }

    @PostMapping("/discovery/users/search")
    public ConfigLookupResult<ConfigSelectorOption> users(@RequestBody ConfigDiscoveryRequest request) {
        return withDiscoveryDiagnostics(
                "configUiSearchUsers",
                request,
                () -> discoveryService.searchIdentityOptions(request.organization(), request.project(), request.query())
        );
    }

    @GetMapping("/discovery/users/avatar")
    public ResponseEntity<byte[]> userAvatar(
            @RequestParam String organization,
            @RequestParam String descriptor
    ) {
        return discoveryService.loadIdentityAvatar(organization, descriptor)
                .map(avatar -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(avatar.contentType()))
                        .cacheControl(CacheControl.noStore())
                        .body(avatar.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private <T> ConfigLookupResult<T> withDiscoveryDiagnostics(
            String operation,
            ConfigDiscoveryRequest request,
            Supplier<ConfigLookupResult<T>> discovery
    ) {
        var requestId = UUID.randomUUID().toString();
        var started = System.nanoTime();
        try {
            var result = discovery.get();
            logDiscoveryResult(operation, requestId, request, result, elapsedMillis(started));
            return result;
        } catch (RuntimeException ex) {
            var result = ConfigLookupResult.<T>error("Config UI discovery request failed.");
            LOGGER.warn(
                    "Config UI discovery failed operation={} requestId={} organization={} project={} workItemType={} queryLength={} status={} optionCount={} durationMs={} failureCategory={} message={}",
                    operation,
                    requestId,
                    safe(request.organization()),
                    safe(request.project()),
                    safe(request.workItemType()),
                    queryLength(request),
                    result.status(),
                    result.optionCount(),
                    elapsedMillis(started),
                    "exception",
                    safeMessage(ex.getMessage())
            );
            return result;
        }
    }

    private <T> void logDiscoveryResult(
            String operation,
            String requestId,
            ConfigDiscoveryRequest request,
            ConfigLookupResult<T> result,
            long durationMs
    ) {
        var failureCategory = failureCategory(result);
        var message = safeMessage(result.message());
        if (result.status() == ConfigValidationStatus.VALID && result.optionCount() > 0) {
            LOGGER.info(
                    "Config UI discovery completed operation={} requestId={} organization={} project={} workItemType={} queryLength={} status={} optionCount={} cacheHit={} skippedBecauseCurrent=false inFlightDeduped=false processFailureCacheHit={} durationMs={} failureCategory={} message={}",
                    operation,
                    requestId,
                    safe(request.organization()),
                    safe(request.project()),
                    safe(request.workItemType()),
                    queryLength(request),
                    result.status(),
                    result.optionCount(),
                    diagnosticFlag(result, "discoveryCacheHit"),
                    diagnosticFlag(result, "processFailureCacheHit"),
                    durationMs,
                    failureCategory,
                    message
            );
            return;
        }
        LOGGER.warn(
                "Config UI discovery needs attention operation={} requestId={} organization={} project={} workItemType={} queryLength={} status={} optionCount={} cacheHit={} skippedBecauseCurrent=false inFlightDeduped=false processFailureCacheHit={} durationMs={} failureCategory={} message={}",
                operation,
                requestId,
                safe(request.organization()),
                safe(request.project()),
                safe(request.workItemType()),
                queryLength(request),
                result.status(),
                result.optionCount(),
                diagnosticFlag(result, "discoveryCacheHit"),
                diagnosticFlag(result, "processFailureCacheHit"),
                durationMs,
                failureCategory,
                message
        );
    }

    private boolean diagnosticFlag(ConfigLookupResult<?> result, String key) {
        return Boolean.TRUE.equals(result.diagnostics().get(key));
    }

    private String failureCategory(ConfigLookupResult<?> result) {
        if (result.status() == ConfigValidationStatus.VALID && result.optionCount() == 0) {
            return "empty";
        }
        return switch (result.status()) {
            case VALID -> "none";
            case WARNING -> result.optionCount() == 0 ? "empty-or-warning" : "warning";
            case ERROR -> "error";
            case NOT_CHECKED -> "not-checked";
        };
    }

    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "" : value.strip();
    }

    private int queryLength(ConfigDiscoveryRequest request) {
        return request == null || request.query() == null ? 0 : request.query().strip().length();
    }

    private String safeMessage(String message) {
        var safe = message == null ? "" : message.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ").strip();
        if (safe.length() <= MAX_SAFE_MESSAGE_LENGTH) {
            return safe;
        }
        return safe.substring(0, MAX_SAFE_MESSAGE_LENGTH) + "...";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidConfig(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleStateErrors(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
    }
}

