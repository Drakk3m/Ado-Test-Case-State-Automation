package com.dentalwings.approvalbot.ui;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config-ui")
public class ConfigUiApiController {

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
        return configService.preview(model);
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
                "preview", configService.preview(model)
        );
    }

    @PostMapping("/discovery/projects")
    public ConfigLookupResult<ConfigSelectorOption> projects(@RequestBody ConfigDiscoveryRequest request) {
        return discoveryService.listProjectOptions(request.organization());
    }

    @PostMapping("/discovery/validate-project")
    public ConfigLookupResult<String> validateProject(@RequestBody ConfigDiscoveryRequest request) {
        return discoveryService.validateProject(request.organization(), request.project());
    }

    @PostMapping("/discovery/work-item-types")
    public ConfigLookupResult<ConfigSelectorOption> workItemTypes(@RequestBody ConfigDiscoveryRequest request) {
        return discoveryService.listWorkItemTypeOptions(request.organization(), request.project());
    }

    @PostMapping("/discovery/fields")
    public ConfigLookupResult<ConfigSelectorOption> fields(@RequestBody ConfigDiscoveryRequest request) {
        return discoveryService.listFieldOptions(request.organization(), request.project(), request.workItemType());
    }

    @PostMapping("/discovery/states")
    public ConfigLookupResult<ConfigSelectorOption> states(@RequestBody ConfigDiscoveryRequest request) {
        return discoveryService.listStateOptions(request.organization(), request.project(), request.workItemType());
    }

    @PostMapping("/discovery/users/search")
    public ConfigLookupResult<ConfigSelectorOption> users(@RequestBody ConfigDiscoveryRequest request) {
        return discoveryService.searchIdentityOptions(request.organization(), request.query());
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

