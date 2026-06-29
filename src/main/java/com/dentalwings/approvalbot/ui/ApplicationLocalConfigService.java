package com.dentalwings.approvalbot.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

@Service
public class ApplicationLocalConfigService
{

    private static final String PAT_PLACEHOLDER = "${ADO_PERSONAL_ACCESS_TOKEN:}";
    private static final String WEBHOOK_SECRET_PLACEHOLDER = "${ADO_WEBHOOK_SHARED_SECRET:}";

    private final Path configPath;
    private final AdoConfigDraftValidationService validationService;

    @Autowired
    public ApplicationLocalConfigService(
            @Value("${approvalbot.ui.config-file:src/main/resources/application-local.yml}") String configFile,
            AdoConfigDraftValidationService validationService
    )
    {
        this(Path.of(configFile), validationService);
    }

    ApplicationLocalConfigService(Path configPath)
    {
        this(configPath, new AdoConfigDraftValidationService(new NotCheckedAdoConfigDiscoveryService(), Map.of()));
    }

    ApplicationLocalConfigService(Path configPath, AdoConfigDraftValidationService validationService)
    {
        this.configPath = configPath;
        this.validationService = validationService;
    }

    public ConfigUiModel load()
    {
        if (!Files.exists(configPath))
        {
            return new ConfigUiModel();
        }

        try
        {
            var yamlText = Files.readString(configPath, StandardCharsets.UTF_8);
            var yaml = new Yaml();
            var parsed = yaml.load(new StringReader(yamlText));
            if (!(parsed instanceof Map<?, ?> root))
            {
                return new ConfigUiModel();
            }
            return mapToModel(root);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Cannot read config file: " + configPath.toAbsolutePath(), ex);
        }
    }

    public AdoConfigPreview preview(ConfigUiModel model)
    {
        var validation = validationService.validate(model);
        var yaml = validation.canGenerateDraftYaml() ? toYaml(model) : "";
        return new AdoConfigPreview(
                yaml,
                validation,
                validation.canGenerateDraftYaml(),
                validation.canGenerateFinalYaml()
        );
    }

    public AdoConfigPreview previewLocalDraft(ConfigUiModel model)
    {
        var validation = validationService.validateLocalDraft(model);
        var yaml = validation.canGenerateDraftYaml() ? toYaml(model) : "";
        return new AdoConfigPreview(
                yaml,
                validation,
                validation.canGenerateDraftYaml(),
                validation.canGenerateFinalYaml()
        );
    }

    public ConfigValidationResult validate(ConfigUiModel model)
    {
        return validationService.validate(model);
    }

    public String previewYaml(ConfigUiModel model)
    {
        var preview = preview(model);
        if (!preview.draftYamlAvailable())
        {
            throw new IllegalArgumentException("Blocking validation errors prevent YAML preview generation.");
        }
        return preview.yaml();
    }

    public Path save(ConfigUiModel model)
    {
        var preview = preview(model);
        if (!preview.finalYamlAllowed())
        {
            throw new IllegalArgumentException("Final YAML cannot be saved until all blocking errors and unchecked ADO values are resolved.");
        }
        var yaml = preview.yaml();
        try
        {
            var parent = configPath.toAbsolutePath().getParent();
            if (parent != null)
            {
                Files.createDirectories(parent);
            }
            Files.writeString(configPath.toAbsolutePath(), yaml, StandardCharsets.UTF_8);
            return configPath.toAbsolutePath();
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Cannot write config file: " + configPath.toAbsolutePath(), ex);
        }
    }

    private ConfigUiModel mapToModel(Map<?, ?> root)
    {
        var model = new ConfigUiModel();

        var adoMap = map(root.get("ado"));
        model.getAdo().setOrganization(text(adoMap.get("organization")));
        model.getAdo().setHttpClientEnabled(bool(adoMap.get("http-client-enabled"), false));
        model.getAdo().setDryRun(bool(adoMap.get("dry-run"), true));
        model.getAdo().setProjects(parseProjects(map(adoMap.get("projects"))));

        var botMap = map(root.get("bot"));
        model.getBot().setIdentityEmail(text(botMap.get("identity-email")));

        var webhookMap = map(root.get("webhook"));
        var sharedSecretMap = map(webhookMap.get("shared-secret"));
        model.getWebhook().getSharedSecret().setEnabled(bool(sharedSecretMap.get("enabled"), true));
        model.getWebhook().getSharedSecret().setHeaderName(defaultIfBlank(text(sharedSecretMap.get("header-name")), "X-ADO-Webhook-Secret"));

        var retryMap = map(root.get("retry"));
        model.getRetry().setMaxAttempts(number(retryMap.get("max-attempts"), 3));
        model.getRetry().setDefaultBackoffSeconds(numberLong(retryMap.get("default-backoff-seconds"), 30L));
        model.getRetry().setRespectRetryAfter(bool(retryMap.get("respect-retry-after"), true));

        var idempotencyMap = map(root.get("idempotency"));
        model.getIdempotency().setType(defaultIfBlank(text(idempotencyMap.get("type")), "sqlite"));
        model.getIdempotency().setSqlitePath(defaultIfBlank(text(idempotencyMap.get("sqlite-path")), "./data/approval-bot-sandbox.sqlite"));
        model.getIdempotency().setTtlHours(numberLong(idempotencyMap.get("ttl-hours"), 24));
        model.getIdempotency().setMaxRecords(number(idempotencyMap.get("max-records"), 10000));

        return model;
    }

    private List<ConfigUiModel.ProjectConfig> parseProjects(Map<?, ?> projectsMap)
    {
        var projects = new ArrayList<ConfigUiModel.ProjectConfig>();
        for (var entry : projectsMap.entrySet())
        {
            var projectMap = map(entry.getValue());
            var project = new ConfigUiModel.ProjectConfig();
            project.setName(stripBracketNotation(String.valueOf(entry.getKey())));
            project.setEnabled(bool(projectMap.get("enabled"), true));
            project.setSupportedWorkItemTypes(listOfText(projectMap.get("supported-work-item-types")));

            var statesMap = map(projectMap.get("states"));
            project.getStates().setDesign(defaultIfBlank(text(statesMap.get("design")), "Design"));
            project.getStates().setInReview(defaultIfBlank(text(statesMap.get("in-review")), "In Review"));
            project.getStates().setApproved(defaultIfBlank(text(statesMap.get("approved")), "Approved"));

            var fieldsMap = map(projectMap.get("fields"));
            project.getFields().setApprovedBySme(text(fieldsMap.get("approved-by-sme")));
            project.getFields().setApprovedBySqa(text(fieldsMap.get("approved-by-sqa")));
            project.getFields().setReversibleBusinessFields(listOfText(fieldsMap.get("reversible-business-fields")));

            var approvalsMap = map(projectMap.get("approvals"));
            project.getApprovals().setSmeUsers(listOfText(approvalsMap.get("sme-users")));
            project.getApprovals().setSqaUsers(listOfText(approvalsMap.get("sqa-users")));

            projects.add(project);
        }
        return projects;
    }

    private String toYaml(ConfigUiModel model)
    {
        var out = new StringBuilder();

        out.append("ado:\n");
        out.append("  organization: ").append(quoted(model.getAdo().getOrganization())).append("\n");
        out.append("  http-client-enabled: ").append(model.getAdo().isHttpClientEnabled()).append("\n");
        out.append("  dry-run: ").append(model.getAdo().isDryRun()).append("\n");
        out.append("  personal-access-token: ").append(PAT_PLACEHOLDER).append("\n\n");
        out.append("  projects:\n");

        if (model.getAdo().getProjects().isEmpty())
        {
            out.append("    {}\n\n");
        }
        else
        {
            for (var project : model.getAdo().getProjects())
            {
                out.append("    ").append(quoted(projectKey(project.getName()))).append(":\n");
                out.append("      enabled: ").append(project.isEnabled()).append("\n");
                out.append("      states:\n");
                out.append("        design: ").append(quoted(project.getStates().getDesign())).append("\n");
                out.append("        in-review: ").append(quoted(project.getStates().getInReview())).append("\n");
                out.append("        approved: ").append(quoted(project.getStates().getApproved())).append("\n");
                out.append("      supported-work-item-types:\n");
                appendList(out, project.getSupportedWorkItemTypes(), 8);
                out.append("      fields:\n");
                out.append("        approved-by-sme: ").append(quoted(project.getFields().getApprovedBySme())).append("\n");
                out.append("        approved-by-sqa: ").append(quoted(project.getFields().getApprovedBySqa())).append("\n");
                out.append("        reversible-business-fields:\n");
                appendList(out, project.getFields().getReversibleBusinessFields(), 10);
                out.append("      approvals:\n");
                out.append("        sme-users:\n");
                appendList(out, project.getApprovals().getSmeUsers(), 10);
                out.append("        sqa-users:\n");
                appendList(out, project.getApprovals().getSqaUsers(), 10);
            }
            out.append("\n");
        }

        out.append("bot:\n");
        out.append("  identity-email: ").append(quoted(model.getBot().getIdentityEmail())).append("\n\n");

        out.append("webhook:\n");
        out.append("  shared-secret:\n");
        out.append("    enabled: ").append(model.getWebhook().getSharedSecret().isEnabled()).append("\n");
        out.append("    header-name: ").append(quoted(model.getWebhook().getSharedSecret().getHeaderName())).append("\n");
        out.append("    value: ").append(WEBHOOK_SECRET_PLACEHOLDER).append("\n\n");

        out.append("retry:\n");
        out.append("  max-attempts: ").append(model.getRetry().getMaxAttempts()).append("\n");
        out.append("  default-backoff-seconds: ").append(model.getRetry().getDefaultBackoffSeconds()).append("\n");
        out.append("  respect-retry-after: ").append(model.getRetry().isRespectRetryAfter()).append("\n\n");

        out.append("idempotency:\n");
        out.append("  type: ").append(quoted(model.getIdempotency().getType())).append("\n");
        out.append("  sqlite-path: ").append(quoted(model.getIdempotency().getSqlitePath())).append("\n");
        out.append("  ttl-hours: ").append(model.getIdempotency().getTtlHours()).append("\n");
        out.append("  max-records: ").append(model.getIdempotency().getMaxRecords()).append("\n");

        return out.toString();
    }

    private void appendList(StringBuilder out, List<String> values, int indentation)
    {
        var indent = " ".repeat(indentation);
        var normalized = values.stream()
                .map(this::defaultString)
                .filter(value -> !value.isBlank())
                .toList();

        if (normalized.isEmpty())
        {
            out.append(indent).append("- ").append(quoted("")).append("\n");
            return;
        }
        for (var value : normalized)
        {
            out.append(indent).append("- ").append(quoted(value)).append("\n");
        }
    }

    private String quoted(String value)
    {
        var safe = defaultString(value).replace("'", "''");
        return "'" + safe + "'";
    }

    private String projectKey(String name)
    {
        var trimmed = defaultString(name).trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]"))
        {
            return trimmed;
        }
        return "[" + trimmed + "]";
    }

    private String stripBracketNotation(String value)
    {
        var trimmed = defaultString(value).trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length() > 2)
        {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private Map<?, ?> map(Object value)
    {
        if (value instanceof Map<?, ?> m)
        {
            return m;
        }
        return new LinkedHashMap<>();
    }

    private List<String> listOfText(Object value)
    {
        if (!(value instanceof List<?> list))
        {
            return new ArrayList<>();
        }
        return list.stream().map(this::text).toList();
    }

    private String text(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean bool(Object value, boolean fallback)
    {
        if (value instanceof Boolean b)
        {
            return b;
        }
        if (value instanceof String s)
        {
            return Boolean.parseBoolean(s);
        }
        return fallback;
    }

    private int number(Object value, int fallback)
    {
        if (value instanceof Number n)
        {
            return n.intValue();
        }
        if (value instanceof String s)
        {
            try
            {
                return Integer.parseInt(s.trim());
            }
            catch (NumberFormatException ignored)
            {
                return fallback;
            }
        }
        return fallback;
    }

    private long numberLong(Object value, long fallback)
    {
        if (value instanceof Number n)
        {
            return n.longValue();
        }
        if (value instanceof String s)
        {
            try
            {
                return Long.parseLong(s.trim());
            }
            catch (NumberFormatException ignored)
            {
                return fallback;
            }
        }
        return fallback;
    }

    private String defaultIfBlank(String value, String fallback)
    {
        return isBlank(value) ? fallback : value;
    }

    private String defaultString(String value)
    {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value)
    {
        return value == null || value.isBlank();
    }
}


