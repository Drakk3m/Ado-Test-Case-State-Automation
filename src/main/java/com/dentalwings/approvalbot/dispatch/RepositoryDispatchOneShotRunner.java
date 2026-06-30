package com.dentalwings.approvalbot.dispatch;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Objects;

import com.dentalwings.approvalbot.ado.AdoClient;
import com.dentalwings.approvalbot.ado.AdoWorkItemKey;
import com.dentalwings.approvalbot.ado.DryRunAdoClient;
import com.dentalwings.approvalbot.ado.RetryingAdoClient;
import com.dentalwings.approvalbot.ado.http.AdoClientNonRetryableException;
import com.dentalwings.approvalbot.ado.http.AdoClientRetryableException;
import com.dentalwings.approvalbot.ado.http.AzureDevOpsHttpClient;
import com.dentalwings.approvalbot.config.ProjectApprovalConfig;
import com.dentalwings.approvalbot.config.spring.ApprovalBotProperties;
import com.dentalwings.approvalbot.config.spring.ProjectApprovalConfigResolver;
import com.dentalwings.approvalbot.config.validation.ProjectApprovalConfigValidator;
import com.dentalwings.approvalbot.domain.ProcessingResult;
import com.dentalwings.approvalbot.processing.ProcessWorkItemCommand;
import com.dentalwings.approvalbot.processing.WorkItemProcessingResult;
import com.dentalwings.approvalbot.processing.WorkItemProcessingService;
import com.dentalwings.approvalbot.workflow.WorkflowEngine;
import com.dentalwings.approvalbot.workflow.comment.CommentBuilder;
import com.dentalwings.approvalbot.workflow.patch.PatchBuilder;

public final class RepositoryDispatchOneShotRunner
{

    static final int EXIT_SUCCESS = 0;
    static final int EXIT_RETRYABLE_FAILURE = 1;
    static final int EXIT_VALIDATION_ERROR = 2;
    static final int EXIT_NON_RETRYABLE_FAILURE = 3;

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
        ExecutionContext context = null;
        try
        {
            var options = Options.parse(args);
            var payload = payloadParser.parse(options.payloadFile());
            context = ExecutionContext.from(payload);
            var properties = configLoader.load(options.configFile());
            var projectConfig = findProjectConfig(payload.project(), properties);
            if (projectConfig == null)
            {
                return finish(context, ProcessingResult.SKIPPED, "Project is not configured.", out);
            }
            if (!projectConfig.enabled())
            {
                return finish(context, ProcessingResult.SKIPPED, "Project is disabled.", out);
            }
            validateProjectConfig(projectConfig);

            var adoClient = adoClientFactory.create(properties);
            var key = new AdoWorkItemKey(configuredOrganization(properties), payload.project(), payload.workItemId());

            // V1 performs this eligibility read before constructing the processor so unsupported types can never
            // reach PATCH/comment. WorkItemProcessingService intentionally performs its own fresh source-of-truth
            // read; avoiding that duplicate read would require changing its workflow boundary.
            var currentWorkItem = adoClient.fetchWorkItem(key);
            out.printf("Fetched ADO source of truth project=%s workItemId=%d workItemType=%s revision=%d%n",
                    currentWorkItem.project(), currentWorkItem.id(), currentWorkItem.workItemType(),
                    currentWorkItem.revision());

            if (!projectConfig.supportedWorkItemTypes().contains(currentWorkItem.workItemType()))
            {
                return finish(context, ProcessingResult.SKIPPED,
                        "Work item type is unsupported by project configuration.", out);
            }

            var command = new ProcessWorkItemCommand(key, payload.revision(), projectConfig);
            var result = processorFactory.create(adoClient).process(command);
            printFinalResult(context, result, out);
            return exitCode(result.result());
        }
        catch (InvalidRepositoryDispatchPayloadException ex)
        {
            err.println("Invalid repository_dispatch payload:");
            ex.errors().forEach(error -> err.println("- " + error));
            return EXIT_VALIDATION_ERROR;
        }
        catch (AdoClientRetryableException ex)
        {
            printFailure(context, ProcessingResult.FAILED_RETRYABLE, "ADO operation failed after retry attempts.",
                    out);
            err.println("One-shot processing failed with a retryable ADO error.");
            return EXIT_RETRYABLE_FAILURE;
        }
        catch (AdoClientNonRetryableException ex)
        {
            printFailure(context, ProcessingResult.FAILED_NON_RETRYABLE,
                    "ADO operation failed with a non-retryable error.", out);
            err.println("One-shot processing failed with a non-retryable ADO error.");
            return EXIT_NON_RETRYABLE_FAILURE;
        }
        catch (IllegalArgumentException ex)
        {
            printStatus(context, "VALIDATION_ERROR", "Validation or configuration error.", out);
            err.println("One-shot validation/configuration error: " + ex.getMessage());
            return EXIT_VALIDATION_ERROR;
        }
        catch (RuntimeException ex)
        {
            printFailure(context, ProcessingResult.FAILED_NON_RETRYABLE, "Unexpected one-shot processing failure.",
                    out);
            err.println("One-shot execution failed with an unexpected non-retryable error.");
            return EXIT_NON_RETRYABLE_FAILURE;
        }
    }

    private static void printFinalResult(ExecutionContext context, WorkItemProcessingResult result, PrintStream out)
    {
        printSummary(context, result.result(), result.reason(), out);
    }

    private static int finish(ExecutionContext context, ProcessingResult result, String reason, PrintStream out)
    {
        printSummary(context, result, reason, out);
        return exitCode(result);
    }

    private static void printFailure(ExecutionContext context, ProcessingResult result, String reason, PrintStream out)
    {
        if (context != null)
        {
            printSummary(context, result, reason, out);
        }
    }

    private static void printSummary(ExecutionContext context, ProcessingResult result, String reason, PrintStream out)
    {
        printStatus(context, result.name(), reason, out);
    }

    private static void printStatus(ExecutionContext context, String result, String reason, PrintStream out)
    {
        if (context != null)
        {
            out.printf("project=%s workItemId=%d revision=%d result=%s reason=%s%n", context.project(),
                    context.workItemId(), context.revision(), result, reason);
        }
    }

    private static int exitCode(ProcessingResult result)
    {
        return switch (result)
        {
            case SKIPPED, COMPLETED, COMPLETED_WITH_WARNING -> EXIT_SUCCESS;
            case FAILED_RETRYABLE -> EXIT_RETRYABLE_FAILURE;
            case FAILED_NON_RETRYABLE -> EXIT_NON_RETRYABLE_FAILURE;
        };
    }

    private static ProjectApprovalConfig findProjectConfig(String projectName, ApprovalBotProperties properties)
    {
        return new ProjectApprovalConfigResolver(properties).findByProjectName(projectName)
                .orElse(null);
    }

    private static void validateProjectConfig(ProjectApprovalConfig projectConfig)
    {
        var validation = new ProjectApprovalConfigValidator().validate(projectConfig);
        if (validation.hasFatalErrors())
        {
            var messages = validation.fatalErrors().stream().map(issue -> issue.message()).toList();
            throw new IllegalArgumentException("Project configuration is invalid: " + String.join("; ", messages));
        }
    }

    private static String configuredOrganization(ApprovalBotProperties properties)
    {
        var organization = properties.getAdo().getOrganization();
        if (organization == null || organization.isBlank())
        {
            throw new IllegalArgumentException("ado.organization is required for one-shot execution.");
        }
        return organization.trim();
    }

    static AdoClient defaultAdoClient(ApprovalBotProperties properties)
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

    private record ExecutionContext(String project, long workItemId, int revision)
    {
        private static ExecutionContext from(RepositoryDispatchPayload payload)
        {
            return new ExecutionContext(payload.project(), payload.workItemId(), payload.revision());
        }
    }
}

