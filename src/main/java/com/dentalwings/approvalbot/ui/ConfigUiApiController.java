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

    public ConfigUiApiController(ApplicationLocalConfigService configService) {
        this.configService = configService;
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidConfig(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleStateErrors(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
    }
}

