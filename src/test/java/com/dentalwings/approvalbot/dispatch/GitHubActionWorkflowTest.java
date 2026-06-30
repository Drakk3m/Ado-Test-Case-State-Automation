package com.dentalwings.approvalbot.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;

import com.dentalwings.approvalbot.config.spring.ProjectApprovalConfigResolver;

class GitHubActionWorkflowTest
{

    @Test
    void workflowRunsCanonicalRepositoryDispatchWithSafeDryRunConfig() throws IOException
    {
        var workflow = Files.readString(Path.of(".github/workflows/ado-work-item-updated.yml"));
        var config = Files.readString(Path.of("config/application-github-action.yml"));

        assertThat(new YamlPropertySourceLoader().load("github-action",
                new FileSystemResource(".github/workflows/ado-work-item-updated.yml"))).isNotEmpty();

        assertThat(workflow).contains("repository_dispatch:", "types: [ado-work-item-updated]",
                "github.event.client_payload.ado_event.resource.workItemId",
                "CLIENT_PAYLOAD: ${{ toJson(github.event.client_payload) }}",
                "com.dentalwings.approvalbot.dispatch.RepositoryDispatchOneShotRunner",
                "--config \"config/application-github-action.yml\"",
                "ADO_PERSONAL_ACCESS_TOKEN: ${{ secrets.ADO_PERSONAL_ACCESS_TOKEN }}")
                .doesNotContain("SpringApplication", "ADO_WEBHOOK_SHARED_SECRET", "echo \"$CLIENT_PAYLOAD\"");
        assertThat(config).contains("personal-access-token: ${ADO_PERSONAL_ACCESS_TOKEN:}", "dry-run: true")
                .doesNotContain("webhook:", "sqlite");

        var properties = new ApprovalBotYamlConfigLoader().load(Path.of("config/application-github-action.yml"));
        assertThat(properties.getAdo().isDryRun()).isTrue();
        var project = new ProjectApprovalConfigResolver(properties).findByProjectName("Example Sandbox Project")
                .orElseThrow();
        assertThat(project.enabled()).isTrue();
    }
}
