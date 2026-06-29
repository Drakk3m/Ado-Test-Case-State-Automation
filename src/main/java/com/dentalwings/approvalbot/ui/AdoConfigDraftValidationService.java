package com.dentalwings.approvalbot.ui;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AdoConfigDraftValidationService
{

    private static final String PAT_ENV = "ADO_PERSONAL_ACCESS_TOKEN";
    private static final String WEBHOOK_SECRET_ENV = "ADO_WEBHOOK_SHARED_SECRET";

    private final AdoConfigDiscoveryService discoveryService;
    private final Map<String, String> environment;

    @Autowired
    public AdoConfigDraftValidationService(AdoConfigDiscoveryService discoveryService)
    {
        this(discoveryService, System.getenv());
    }

    AdoConfigDraftValidationService(AdoConfigDiscoveryService discoveryService, Map<String, String> environment)
    {
        this.discoveryService = discoveryService;
        this.environment = environment;
    }

    public ConfigValidationResult validate(ConfigUiModel model) {
        return validate(model, true);
    }

    public ConfigValidationResult validateLocalDraft(ConfigUiModel model) {
        return validate(model, false);
    }

    private ConfigValidationResult validate(ConfigUiModel model, boolean useAdoDiscovery) {
        var result = new ConfigValidationResult();
        if (model == null)
        {
            result.add("root", ConfigValidationStatus.ERROR, "Configuration draft is required.");
            return result;
        }

        validateAdo(model, result);
        validateBot(model, result);
        validateWebhook(model, result);
        validateProjects(model, result, useAdoDiscovery);
        validateReloadNotice(result);
        return result;
    }

    private void validateAdo(ConfigUiModel model, ConfigValidationResult result)
    {
        var ado = model.getAdo();
        if (isBlank(ado.getOrganization()))
        {
            result.add("ado.organization", ConfigValidationStatus.ERROR, "ADO organization is required.");
            return;
        }
        result.add("ado.organization", ConfigValidationStatus.VALID,
                "Organization value is present; project discovery validates ADO access.");

        if (isBlank(environment.get(PAT_ENV)))
        {
            result.add("ado.personal-access-token", ConfigValidationStatus.ERROR,
                    "PAT environment variable is required for ADO-backed validation. Keep the value as a placeholder in YAML.");
        }
        else
        {
            result.add("ado.personal-access-token", ConfigValidationStatus.VALID,
                    "PAT environment variable is present; value is not displayed.");
        }

        if (!ado.isDryRun())
        {
            result.add("ado.dry-run", ConfigValidationStatus.WARNING,
                    "Write-enabled mode can PATCH and comment in ADO. Use only for controlled sandbox validation.");
        }
        else
        {
            result.add("ado.dry-run", ConfigValidationStatus.VALID,
                    "Dry-run keeps real ADO reads but suppresses writes.");
        }
    }

    private void validateBot(ConfigUiModel model, ConfigValidationResult result)
    {
        if (isBlank(model.getBot().getIdentityEmail()))
        {
            result.add("bot.identity-email", ConfigValidationStatus.ERROR, "Bot identity email is required.");
        }
        else
        {
            result.add("bot.identity-email", ConfigValidationStatus.VALID, "Bot identity is configured.");
        }
    }

    private void validateWebhook(ConfigUiModel model, ConfigValidationResult result)
    {
        var sharedSecret = model.getWebhook().getSharedSecret();
        if (isBlank(sharedSecret.getHeaderName()))
        {
            result.add("webhook.shared-secret.header-name", ConfigValidationStatus.ERROR,
                    "Webhook shared-secret header name is required.");
        }
        if (sharedSecret.isEnabled() && isBlank(environment.get(WEBHOOK_SECRET_ENV)))
        {
            result.add("webhook.shared-secret.value", ConfigValidationStatus.WARNING,
                    "Webhook secret environment variable is not present. Keep the value as a placeholder in YAML.");
        }
        else if (sharedSecret.isEnabled())
        {
            result.add("webhook.shared-secret.value", ConfigValidationStatus.VALID,
                    "Webhook secret environment variable is present; value is not displayed.");
        }
    }

    private void validateProjects(ConfigUiModel model, ConfigValidationResult result, boolean useAdoDiscovery) {
        var projects = model.getAdo().getProjects();
        if (projects.isEmpty())
        {
            result.add("ado.projects", ConfigValidationStatus.ERROR, "At least one sandbox project is required.");
            return;
        }

        var organization = model.getAdo().getOrganization();
        var seen = new HashSet<String>();
        for (int i = 0; i < projects.size(); i++) {
            validateProject(model, projects.get(i), i, seen, result, useAdoDiscovery);
        }
    }

    private void validateProject(
            ConfigUiModel model,
            ConfigUiModel.ProjectConfig project,
            int index,
            Set<String> seen,
            ConfigValidationResult result,
            boolean useAdoDiscovery
    ) {
        var prefix = "ado.projects[" + index + "]";
        if (isBlank(project.getName()))
        {
            result.add(prefix + ".name", ConfigValidationStatus.ERROR, "Project name is required.");
            return;
        }

        var normalizedName = normalize(project.getName());
        if (!seen.add(normalizedName)) {
            result.add(prefix + ".name", ConfigValidationStatus.ERROR, "This project is already configured.");
        } else if (useAdoDiscovery) {
            var projectLookup = discoveryService.validateProject(model.getAdo().getOrganization(), project.getName());
            validateValueFromLookup(prefix + ".name", project.getName(), projectLookup, "Project was found in ADO.", "Project was not found in ADO.", result);
        } else {
            result.add(prefix + ".name", ConfigValidationStatus.VALID,
                    "Project value is locally valid; current UI discovery state controls final save eligibility.");
        }

        validateWorkItemTypes(model, project, prefix, result, useAdoDiscovery);
        validateFields(model, project, prefix, result, useAdoDiscovery);
        validateStates(model, project, prefix, result, useAdoDiscovery);
        validateUsers(model, project, prefix, result, useAdoDiscovery);
    }

    private void validateWorkItemTypes(ConfigUiModel model, ConfigUiModel.ProjectConfig project, String prefix,
            ConfigValidationResult result, boolean useAdoDiscovery) {
        if (project.getSupportedWorkItemTypes().isEmpty()) {
            result.add(prefix + ".supported-work-item-types", ConfigValidationStatus.ERROR, "At least one supported Work Item type is required.");
            return;
        }

        var lookup = useAdoDiscovery
                ? discoveryService.listWorkItemTypes(model.getAdo().getOrganization(), project.getName())
                : null;
        for (var type : project.getSupportedWorkItemTypes()) {
            if (isBlank(type)) {
                result.add(prefix + ".supported-work-item-types", ConfigValidationStatus.ERROR, "Work Item type must not be blank.");
            } else if (useAdoDiscovery) {
                validateValueFromLookup(prefix + ".supported-work-item-types", type, lookup, "Work Item type was found in ADO.", "Work Item type was not found in ADO.", result);
            } else {
                result.add(prefix + ".supported-work-item-types", ConfigValidationStatus.VALID,
                        "Work Item type value is locally valid.");
            }
        }
    }

    private void validateFields(ConfigUiModel model, ConfigUiModel.ProjectConfig project, String prefix,
            ConfigValidationResult result, boolean useAdoDiscovery) {
        var firstType = project.getSupportedWorkItemTypes().isEmpty() ? "" : project.getSupportedWorkItemTypes().get(0);
        var lookup = useAdoDiscovery
                ? discoveryService.listFieldReferenceNames(model.getAdo().getOrganization(), project.getName(), firstType)
                : null;
        validateFieldUniqueness(project, prefix, result);
        validateRequiredField(prefix + ".fields.approved-by-sme", project.getFields().getApprovedBySme(), lookup,
                result, useAdoDiscovery);
        validateRequiredField(prefix + ".fields.approved-by-sqa", project.getFields().getApprovedBySqa(), lookup,
                result, useAdoDiscovery);
        for (var field : project.getFields().getReversibleBusinessFields()) {
            validateRequiredField(prefix + ".fields.reversible-business-fields", field, lookup, result,
                    useAdoDiscovery);
        }
    }

    private void validateFieldUniqueness(ConfigUiModel.ProjectConfig project, String prefix,
            ConfigValidationResult result)
    {
        var smeField = project.getFields().getApprovedBySme();
        var sqaField = project.getFields().getApprovedBySqa();
        var reversibleFields = project.getFields().getReversibleBusinessFields();

        if (!isBlank(smeField) && !isBlank(sqaField) && normalize(smeField).equals(normalize(sqaField)))
        {
            result.add(prefix + ".fields.approved-by-sqa", ConfigValidationStatus.ERROR,
                    "SME and SQA approval fields must be different.");
        }

        var seenReversible = new HashSet<String>();
        for (var field : reversibleFields)
        {
            var normalizedField = normalize(field);
            if (normalizedField.isBlank())
            {
                continue;
            }
            if (!seenReversible.add(normalizedField))
            {
                result.add(prefix + ".fields.reversible-business-fields", ConfigValidationStatus.ERROR,
                        "Reversible business fields must not contain duplicates.");
            }
            if (!isBlank(smeField) && normalizedField.equals(normalize(smeField)))
            {
                result.add(prefix + ".fields.reversible-business-fields", ConfigValidationStatus.ERROR,
                        "SME approval field must not also be reversible.");
            }
            if (!isBlank(sqaField) && normalizedField.equals(normalize(sqaField)))
            {
                result.add(prefix + ".fields.reversible-business-fields", ConfigValidationStatus.ERROR,
                        "SQA approval field must not also be reversible.");
            }
        }
    }

    private void validateRequiredField(String fieldName, String value, ConfigLookupResult<String> lookup,
            ConfigValidationResult result, boolean useAdoDiscovery) {
        if (isBlank(value)) {
            result.add(fieldName, ConfigValidationStatus.ERROR, "Field reference name is required.");
            return;
        }
        if (useAdoDiscovery) {
            validateValueFromLookup(fieldName, value, lookup, "Field reference name was found in ADO.",
                    "Field reference name was not found in ADO.", result);
        } else {
            result.add(fieldName, ConfigValidationStatus.VALID, "Field reference name is locally valid.");
        }
    }

    private void validateStates(ConfigUiModel model, ConfigUiModel.ProjectConfig project, String prefix,
            ConfigValidationResult result, boolean useAdoDiscovery) {
        var states = project.getStates();
        var firstType = project.getSupportedWorkItemTypes().isEmpty() ? "" : project.getSupportedWorkItemTypes().get(0);
        var lookup = useAdoDiscovery
                ? discoveryService.listObservedStateNames(model.getAdo().getOrganization(), project.getName(), firstType)
                : null;

        validateState(prefix + ".states.design", states.getDesign(), lookup, result, useAdoDiscovery);
        validateState(prefix + ".states.in-review", states.getInReview(), lookup, result, useAdoDiscovery);
        validateState(prefix + ".states.approved", states.getApproved(), lookup, result, useAdoDiscovery);
    }

    private void validateState(String field, String state, ConfigLookupResult<String> lookup,
            ConfigValidationResult result, boolean useAdoDiscovery) {
        if (isBlank(state)) {
            result.add(field, ConfigValidationStatus.ERROR, "Workflow state value is required.");
            return;
        }
        if (useAdoDiscovery) {
            validateValueFromLookup(field, state, lookup, "State value has been observed in ADO.",
                    "State value was not observed in ADO.", result);
        } else {
            result.add(field, ConfigValidationStatus.VALID, "State value is locally valid.");
        }
    }

    private void validateUsers(ConfigUiModel model, ConfigUiModel.ProjectConfig project, String prefix,
            ConfigValidationResult result, boolean useAdoDiscovery) {
        validateUserList(model, prefix + ".approvals.sme-users", project.getApprovals().getSmeUsers(), result,
                useAdoDiscovery);
        validateUserList(model, prefix + ".approvals.sqa-users", project.getApprovals().getSqaUsers(), result,
                useAdoDiscovery);
        validateCrossRoleUsers(prefix, project.getApprovals().getSmeUsers(), project.getApprovals().getSqaUsers(), result);
    }

    private void validateUserList(ConfigUiModel model, String field, List<String> users, ConfigValidationResult result,
            boolean useAdoDiscovery) {
        if (users.isEmpty()) {
            result.add(field, ConfigValidationStatus.ERROR, "At least one user is required.");
            return;
        }

        var blanks = users.stream().anyMatch(this::isBlank);
        if (blanks)
        {
            result.add(field, ConfigValidationStatus.ERROR, "User list must not contain blank entries.");
            return;
        }

        var duplicate = firstDuplicate(users);
        if (!duplicate.isBlank())
        {
            result.add(field, ConfigValidationStatus.ERROR,
                    "User list must not contain duplicate identities: " + duplicate);
            return;
        }

        var displayNameOnly = users.stream().filter(this::isLikelyDisplayNameOnly).toList();
        if (!displayNameOnly.isEmpty())
        {
            result.add(field, ConfigValidationStatus.ERROR,
                    "User values must be email/login based; displayName-only values are not valid authorization identities.");
            return;
        }

        if (!useAdoDiscovery) {
            result.add(field, ConfigValidationStatus.VALID, "User identity values are locally valid.");
            return;
        }

        var lookup = discoveryService.resolveUsers(model.getAdo().getOrganization(), users);
        if (lookup.status() == ConfigValidationStatus.VALID)
        {
            var resolved = normalizedSet(lookup.values());
            for (var user : users)
            {
                if (resolved.contains(normalize(user)))
                {
                    result.add(field, ConfigValidationStatus.VALID, "User resolved in ADO: " + user);
                }
                else
                {
                    result.add(field, ConfigValidationStatus.ERROR, "User was not resolved in ADO: " + user);
                }
            }
        }
        else
        {
            result.add(field, lookup.status(), lookup.message());
        }
    }

    private void validateCrossRoleUsers(String prefix, List<String> smeUsers, List<String> sqaUsers,
            ConfigValidationResult result)
    {
        var sqa = normalizedSet(sqaUsers);
        for (var smeUser : smeUsers)
        {
            var normalized = normalize(smeUser);
            if (!normalized.isBlank() && sqa.contains(normalized))
            {
                result.add(prefix + ".approvals", ConfigValidationStatus.WARNING,
                        "Same identity appears in both SME and SQA lists. Workflow still requires different actual approvers.");
                return;
            }
        }
    }

    private String firstDuplicate(List<String> users)
    {
        var seen = new HashSet<String>();
        for (var user : users)
        {
            var normalized = normalize(user);
            if (!normalized.isBlank() && !seen.add(normalized))
            {
                return normalized;
            }
        }
        return "";
    }

    private void validateReloadNotice(ConfigValidationResult result)
    {
        result.add("config.reload", ConfigValidationStatus.WARNING,
                "Hot-load is intentionally deferred. Restart is still required after YAML changes.");
    }

    private void validateValueFromLookup(String field, String value, ConfigLookupResult<String> lookup,
            String successMessage, String errorMessage, ConfigValidationResult result)
    {
        if (lookup.status() == ConfigValidationStatus.VALID)
        {
            if (normalizedSet(lookup.values()).contains(normalize(value)))
            {
                result.add(field, ConfigValidationStatus.VALID, successMessage);
            }
            else
            {
                result.add(field, ConfigValidationStatus.ERROR, errorMessage + " Value: " + value);
            }
            return;
        }
        result.add(field, lookup.status(), lookup.message());
    }

    private Set<String> normalizedSet(List<String> values)
    {
        var normalized = new HashSet<String>();
        for (var value : values)
        {
            normalized.add(normalize(value));
        }
        return normalized;
    }

    private String normalize(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isLikelyDisplayNameOnly(String value)
    {
        var normalized = normalize(value);
        return !normalized.contains("@") && !normalized.contains("\\");
    }

    private boolean isBlank(String value)
    {
        return value == null || value.isBlank();
    }
}
