package com.dentalwings.approvalbot.dispatch;

import java.util.Objects;
import java.io.PrintStream;
import java.nio.file.Path;

import com.dentalwings.approvalbot.ado.AdoClient;
import com.dentalwings.approvalbot.ado.AdoWorkItemKey;
import com.dentalwings.approvalbot.ado.DryRunAdoClient;
import com.dentalwings.approvalbot.ado.RetryingAdoClient;
import com.dentalwings.approvalbot.ado.http.AzureDevOpsHttpClient;
import com.dentalwings.approvalbot.config.ProjectApprovalConfig;
import com.dentalwings.approvalbot.config.spring.ApprovalBotProperties;
import com.dentalwings.approvalbot.config.spring.ProjectApprovalConfigResolver;
import com.dentalwings.approvalbot.processing.ProcessWorkItemCommand;
import com.dentalwings.approvalbot.processing.WorkItemProcessingResult;
import com.dentalwings.approvalbot.processing.WorkItemProcessingService;
import com.dentalwings.approvalbot.workflow.WorkflowEngine;
import com.dentalwings.approvalbot.workflow.comment.CommentBuilder;
import com.dentalwings.approvalbot.workflow.patch.PatchBuilder;

public final class RepositoryDispatchOneShotRunner
{

    private final RepositoryDispatchPayloadParser payloadParser;
    private final ApprovalBotYamlConfigLoader configLoader;
    private final AdoClientFactory adoClientFactory;
    private final WorkItemProcessorFactory processorFactory;

    public RepositoryDispatchOneShotRunner()
    {
        this(new RepositoryDispatchPayloadParser(), new ApprovalBotYamlConfigLoader(),
                RepositoryDispatchOneShotRunner::defaultAdoClient,
                RepositoryDispatchOneShotRunner::defaultProcessor);
    }

    RepositoryDispatchOneShotRunner(RepositoryDispatchPayloadParser payloadParser,
            ApprovalBotYamlConfigLoader configLoader, AdoClientFactory adoClientFactory,
            WorkItemProcessorFactory processorFactory)
    {
        this.payloadParser = Objects.requireNonNull(payloadParser);
        this.configLoader = Objects.requireNonNull(configLoader);
        this.adoClientFactory = Objects.requireNonNull(adoClientFactory);
        this.processorFactory = Objects.requireNonNull(processorFactory);
    }

    public static void main(String[] args)
    {
        var exitCode = new RepositoryDispatchOneShotRunner().run(args, System.out, System.err);
        if (exitCode != 0)
        {
            System.exit(exitCode);
        }
    }

    int run(String[] args, PrintStream out, PrintStream err)
    {
        try
        {
            var options = Options.parse(args);
            var payload = payloadParser.parse(options.payloadFile());
            var properties = configLoader.load(options.configFile());
            var projectConfig = resolveProjectConfig(payload.project(), properties);
            if (projectConfig == null)
            {
                out.println("Final result: SKIPPED (Project is not configured or disabled.)");
                return 0;
            }

            var adoClient = adoClientFactory.create(properties);
            var key = new AdoWorkItemKey(payload.organization(), payload.project(), payload.workItemId());

            var currentWorkItem = adoClient.fetchWorkItem(key);
            out.printf("Fetched ADO source of truth project=%s workItemId=%d workItemType=%s revision=%d%n",
                    currentWorkItem.project(), currentWorkItem.id(), currentWorkItem.workItemType(),
                    currentWorkItem.revision());

            if (!projectConfig.supportedWorkItemTypes().contains(currentWorkItem.workItemType()))
            {
                out.println("Final result: SKIPPED (Work item type is unsupported by project configuration.)");
                return 0;
            }

            var command = new ProcessWorkItemCommand(key, payload.revision(), projectConfig);
            var result = processorFactory.create(adoClient).process(command);
            printFinalResult(result, out);
            return 0;
        }
        catch (InvalidRepositoryDispatchPayloadException ex)
        {
            err.println("Invalid repository_dispatch payload:");
            ex.errors().forEach(error -> err.println("- " + error));
            return 2;
        }
        catch (RuntimeException ex)
        {
            err.println("One-shot execution failed: " + ex.getMessage());
            return 1;
        }
    }

    private static void printFinalResult(WorkItemProcessingResult result, PrintStream out)
    {
        out.printf("Final result: %s (%s)%n", result.result(), result.reason());
    }

    private static ProjectApprovalConfig resolveProjectConfig(String projectName, ApprovalBotProperties properties)
    {
        return new ProjectApprovalConfigResolver(properties).findByProjectName(projectName)
                .filter(ProjectApprovalConfig::enabled)
                .orElse(null);
    }

    private static AdoClient defaultAdoClient(ApprovalBotProperties properties)
    {
        if (!properties.getAdo().isHttpClientEnabled())
        {
            throw new IllegalArgumentException("ado.http-client-enabled=true is required for one-shot execution.");
        }
        var pat = resolvePat(properties.getAdo().getPersonalAccessToken());
        if (pat.isBlank())
        {
            throw new IllegalArgumentException("ADO PAT is required for one-shot execution.");
        }

        AdoClient client = AzureDevOpsHttpClient.fromProperties(properties.getAdo(), pat);
        if (properties.getAdo().isDryRun())
        {
            client = new DryRunAdoClient(client);
        }
        return new RetryingAdoClient(client);
    }

    private static String resolvePat(String configuredValue)
    {
        var normalized = configuredValue == null ? "" : configuredValue.trim();
        if (normalized.startsWith("${") && normalized.endsWith("}"))
        {
            var expression = normalized.substring(2, normalized.length() - 1);
            var separator = expression.indexOf(':');
            var variableName = separator < 0 ? expression : expression.substring(0, separator);
            var fallback = separator < 0 ? "" : expression.substring(separator + 1);
            var envValue = System.getenv(variableName);
            if (envValue != null && !envValue.isBlank())
            {
                return envValue.trim();
            }
            return fallback == null ? "" : fallback.trim();
        }
        return normalized;
    }

    private static WorkItemProcessor defaultProcessor(AdoClient adoClient)
    {
        var commentBuilder = new CommentBuilder();
        var processingService = new WorkItemProcessingService(adoClient, new WorkflowEngine(commentBuilder),
                new PatchBuilder(), commentBuilder);
        return processingService::process;
    }

    @FunctionalInterface
    interface AdoClientFactory
    {
        AdoClient create(ApprovalBotProperties properties);
    }

    @FunctionalInterface
    interface WorkItemProcessorFactory
    {
        WorkItemProcessor create(AdoClient adoClient);
    }

    @FunctionalInterface
    interface WorkItemProcessor
    {
        WorkItemProcessingResult process(ProcessWorkItemCommand command);
    }

    record Options(Path payloadFile, Path configFile)
    {
        static Options parse(String[] args)
        {
            Path payload = null;
            Path config = null;
            for (var index = 0; index < args.length; index++)
            {
                var arg = args[index];
                if ("--payload".equals(arg) && index + 1 < args.length)
                {
                    payload = Path.of(args[++index]);
                }
                else if ("--config".equals(arg) && index + 1 < args.length)
                {
                    config = Path.of(args[++index]);
                }
            }
            if (payload == null || config == null)
            {
                throw new IllegalArgumentException("Usage: --payload <dispatch-json> --config <application-yaml>");
            }
            return new Options(payload, config);
        }
    }
}

