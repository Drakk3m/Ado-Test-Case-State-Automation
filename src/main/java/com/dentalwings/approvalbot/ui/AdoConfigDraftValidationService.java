package com.dentalwings.approvalbot.ui;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AdoConfigDraftValidationService {

    private static final String PAT_ENV = "ADO_PERSONAL_ACCESS_TOKEN";
    private static final String WEBHOOK_SECRET_ENV = "ADO_WEBHOOK_SHARED_SECRET";

    private final AdoConfigDiscoveryService discoveryService;
    private final Map<String, String> environment;

    @Autowired
    public AdoConfigDraftValidationService(AdoConfigDiscoveryService discoveryService) {
        this(discoveryService, System.getenv());
    }

    AdoConfigDraftValidationService(AdoConfigDiscoveryService discoveryService, Map<String, String> environment) {
        this.discoveryService = discoveryService;
        this.environment = environment;
    }

    public ConfigValidationResult validate(ConfigUiModel model) {
        var result = new ConfigValidationResult();
        if (model == null) {
            result.add("root", ConfigValidationStatus.ERROR, "Configuration draft is required.");
            return result;
        }

        validateAdo(model, result);
        validateBot(model, result);
        validateWebhook(model, result);
        validateProjects(model, result);
        validateReloadNotice(result);
        return result;
    }

    private void validateAdo(ConfigUiModel model, ConfigValidationResult result) {
        var ado = model.getAdo();
        if (isBlank(ado.getOrganization())) {
            result.add("ado.organization", ConfigValidationStatus.ERROR, "ADO organization is required.");
            return;
        }
        result.add("ado.organization", ConfigValidationStatus.VALID, "Organization value is present; project discovery validates ADO access.");

        if (ado.isHttpClientEnabled() && isBlank(environment.get(PAT_ENV))) {
            result.add("ado.personal-access-token", ConfigValidationStatus.WARNING, "PAT environment variable is not present. Keep the value as a placeholder in YAML.");
        } else if (ado.isHttpClientEnabled()) {
            result.add("ado.personal-access-token", ConfigValidationStatus.VALID, "PAT environment variable is present; value is not displayed.");
        }

        if (!ado.isDryRun()) {
            result.add("ado.dry-run", ConfigValidationStatus.WARNING, "Write-enabled mode can PATCH and comment in ADO. Use only for controlled sandbox validation.");
        } else {
            result.add("ado.dry-run", ConfigValidationStatus.VALID, "Dry-run keeps real ADO reads but suppresses writes.");
        }
    }

    private void validateBot(ConfigUiModel model, ConfigValidationResult result) {
        if (isBlank(model.getBot().getIdentityEmail())) {
            result.add("bot.identity-email", ConfigValidationStatus.ERROR, "Bot identity email is required.");
        } else {
            result.add("bot.identity-email", ConfigValidationStatus.VALID, "Bot identity is configured.");
        }
    }

    private void validateWebhook(ConfigUiModel model, ConfigValidationResult result) {
        var sharedSecret = model.getWebhook().getSharedSecret();
        if (isBlank(sharedSecret.getHeaderName())) {
            result.add("webhook.shared-secret.header-name", ConfigValidationStatus.ERROR, "Webhook shared-secret header name is required.");
        }
        if (sharedSecret.isEnabled() && isBlank(environment.get(WEBHOOK_SECRET_ENV))) {
            result.add("webhook.shared-secret.value", ConfigValidationStatus.WARNING, "Webhook secret environment variable is not present. Keep the value as a placeholder in YAML.");
        } else if (sharedSecret.isEnabled()) {
            result.add("webhook.shared-secret.value", ConfigValidationStatus.VALID, "Webhook secret environment variable is present; value is not displayed.");
        }
    }

    private void validateProjects(ConfigUiModel model, ConfigValidationResult result) {
        var projects = model.getAdo().getProjects();
        if (projects.isEmpty()) {
            result.add("ado.projects", ConfigValidationStatus.ERROR, "At least one sandbox project is required.");
            return;
        }

        var organization = model.getAdo().getOrganization();
        var projectNames = discoveryService.listProjects(organization);
        var seen = new HashSet<String>();
        for (int i = 0; i < projects.size(); i++) {
            validateProject(model, projects.get(i), i, seen, projectNames, result);
        }
    }

    private void validateProject(
            ConfigUiModel model,
            ConfigUiModel.ProjectConfig project,
            int index,
            Set<String> seen,
            ConfigLookupResult<String> projectNames,
            ConfigValidationResult result
    ) {
        var prefix = "ado.projects[" + index + "]";
        if (isBlank(project.getName())) {
            result.add(prefix + ".name", ConfigValidationStatus.ERROR, "Project name is required.");
            return;
        }

        var normalizedName = normalize(project.getName());
        if (!seen.add(normalizedName)) {
            result.add(prefix + ".name", ConfigValidationStatus.ERROR, "Duplicate project name.");
        } else {
            validateValueFromLookup(prefix + ".name", project.getName(), projectNames, "Project was found in ADO.", "Project was not found in ADO.", result);
        }

        validateWorkItemTypes(model, project, prefix, result);
        validateFields(model, project, prefix, result);
        validateStates(model, project, prefix, result);
        validateUsers(model, project, prefix, result);
    }

    private void validateWorkItemTypes(ConfigUiModel model, ConfigUiModel.ProjectConfig project, String prefix, ConfigValidationResult result) {
        if (project.getSupportedWorkItemTypes().isEmpty()) {
            result.add(prefix + ".supported-work-item-types", ConfigValidationStatus.ERROR, "At least one supported Work Item type is required.");
            return;
        }

        var lookup = discoveryService.listWorkItemTypes(model.getAdo().getOrganization(), project.getName());
        for (var type : project.getSupportedWorkItemTypes()) {
            if (isBlank(type)) {
                result.add(prefix + ".supported-work-item-types", ConfigValidationStatus.ERROR, "Work Item type must not be blank.");
            } else {
                validateValueFromLookup(prefix + ".supported-work-item-types", type, lookup, "Work Item type was found in ADO.", "Work Item type was not found in ADO.", result);
            }
        }
    }

    private void validateFields(ConfigUiModel model, ConfigUiModel.ProjectConfig project, String prefix, ConfigValidationResult result) {
        var firstType = project.getSupportedWorkItemTypes().isEmpty() ? "" : project.getSupportedWorkItemTypes().get(0);
        var lookup = discoveryService.listFieldReferenceNames(model.getAdo().getOrganization(), project.getName(), firstType);
        validateRequiredField(prefix + ".fields.approved-by-sme", project.getFields().getApprovedBySme(), lookup, result);
        validateRequiredField(prefix + ".fields.approved-by-sqa", project.getFields().getApprovedBySqa(), lookup, result);
        for (var field : project.getFields().getReversibleBusinessFields()) {
            validateRequiredField(prefix + ".fields.reversible-business-fields", field, lookup, result);
        }
    }

    private void validateRequiredField(String fieldName, String value, ConfigLookupResult<String> lookup, ConfigValidationResult result) {
        if (isBlank(value)) {
            result.add(fieldName, ConfigValidationStatus.ERROR, "Field reference name is required.");
            return;
        }
        validateValueFromLookup(fieldName, value, lookup, "Field reference name was found in ADO.", "Field reference name was not found in ADO.", result);
    }

    private void validateStates(ConfigUiModel model, ConfigUiModel.ProjectConfig project, String prefix, ConfigValidationResult result) {
        var states = project.getStates();
        var firstType = project.getSupportedWorkItemTypes().isEmpty() ? "" : project.getSupportedWorkItemTypes().get(0);
        var lookup = discoveryService.listObservedStateNames(model.getAdo().getOrganization(), project.getName(), firstType);

        validateState(prefix + ".states.design", states.getDesign(), lookup, result);
        validateState(prefix + ".states.in-review", states.getInReview(), lookup, result);
        validateState(prefix + ".states.approved", states.getApproved(), lookup, result);
    }

    private void validateState(String field, String state, ConfigLookupResult<String> lookup, ConfigValidationResult result) {
        if (isBlank(state)) {
            result.add(field, ConfigValidationStatus.ERROR, "Workflow state value is required.");
            return;
        }
        validateValueFromLookup(field, state, lookup, "State value has been observed in ADO.", "State value was not observed in ADO.", result);
    }

    private void validateUsers(ConfigUiModel model, ConfigUiModel.ProjectConfig project, String prefix, ConfigValidationResult result) {
        validateUserList(model, prefix + ".approvals.sme-users", project.getApprovals().getSmeUsers(), result);
        validateUserList(model, prefix + ".approvals.sqa-users", project.getApprovals().getSqaUsers(), result);
    }

    private void validateUserList(ConfigUiModel model, String field, List<String> users, ConfigValidationResult result) {
        if (users.isEmpty()) {
            result.add(field, ConfigValidationStatus.ERROR, "At least one user is required.");
            return;
        }

        var blanks = users.stream().anyMatch(this::isBlank);
        if (blanks) {
            result.add(field, ConfigValidationStatus.ERROR, "User list must not contain blank entries.");
            return;
        }

        var lookup = discoveryService.resolveUsers(model.getAdo().getOrganization(), users);
        if (lookup.status() == ConfigValidationStatus.VALID) {
            var resolved = normalizedSet(lookup.values());
            for (var user : users) {
                if (resolved.contains(normalize(user))) {
                    result.add(field, ConfigValidationStatus.VALID, "User resolved in ADO: " + user);
                } else {
                    result.add(field, ConfigValidationStatus.ERROR, "User was not resolved in ADO: " + user);
                }
            }
        } else {
            result.add(field, lookup.status(), lookup.message());
        }
    }

    private void validateReloadNotice(ConfigValidationResult result) {
        result.add("config.reload", ConfigValidationStatus.WARNING, "Hot-load is intentionally deferred. Restart is still required after YAML changes.");
    }

    private void validateValueFromLookup(
            String field,
            String value,
            ConfigLookupResult<String> lookup,
            String successMessage,
            String errorMessage,
            ConfigValidationResult result
    ) {
        if (lookup.status() == ConfigValidationStatus.VALID) {
            if (normalizedSet(lookup.values()).contains(normalize(value))) {
                result.add(field, ConfigValidationStatus.VALID, successMessage);
            } else {
                result.add(field, ConfigValidationStatus.ERROR, errorMessage + " Value: " + value);
            }
            return;
        }
        result.add(field, lookup.status(), lookup.message());
    }

    private Set<String> normalizedSet(List<String> values) {
        var normalized = new HashSet<String>();
        for (var value : values) {
            normalized.add(normalize(value));
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
