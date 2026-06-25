package com.dentalwings.approvalbot.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConfigValidationResult {

    private final List<ConfigFieldValidation> fields = new ArrayList<>();

    public void add(String field, ConfigValidationStatus status, String message) {
        fields.add(new ConfigFieldValidation(field, status, message));
    }

    public List<ConfigFieldValidation> fields() {
        return Collections.unmodifiableList(fields);
    }

    public List<ConfigFieldValidation> getFields() {
        return fields();
    }

    public boolean hasBlockingErrors() {
        return fields.stream().anyMatch(field -> field.status() == ConfigValidationStatus.ERROR);
    }

    public boolean isHasBlockingErrors() {
        return hasBlockingErrors();
    }

    public boolean hasUncheckedItems() {
        return fields.stream().anyMatch(field -> field.status() == ConfigValidationStatus.NOT_CHECKED);
    }

    public boolean isHasUncheckedItems() {
        return hasUncheckedItems();
    }

    public boolean canGenerateDraftYaml() {
        return !hasBlockingErrors();
    }

    public boolean isCanGenerateDraftYaml() {
        return canGenerateDraftYaml();
    }

    public boolean canGenerateFinalYaml() {
        return !hasBlockingErrors() && !hasUncheckedItems();
    }

    public boolean isCanGenerateFinalYaml() {
        return canGenerateFinalYaml();
    }
}
