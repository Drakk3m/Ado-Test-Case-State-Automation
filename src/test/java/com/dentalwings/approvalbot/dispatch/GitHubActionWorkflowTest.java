package com.dentalwings.approvalbot.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;

import com.dentalwings.approvalbot.config.spring.AdoAuthenticationMode;
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
                .doesNotContain("SpringApplication", "ADO_WEBHOOK_SHARED_SECRET", "echo \"$CLIENT_PAYLOAD\"",
                        "secrets.ADO_ACCESS_TOKEN", "CAL__AZURE_CLIENT_SECRET");
        assertThat(config).contains("mode: pat", "personal-access-token: ${ADO_PERSONAL_ACCESS_TOKEN:}", "dry-run: true")
                .doesNotContain("bearer-token", "ADO_ACCESS_TOKEN")
                .doesNotContain("webhook:", "sqlite");

        var properties = new ApprovalBotYamlConfigLoader().load(Path.of("config/application-github-action.yml"));
        assertThat(properties.getAdo().isDryRun()).isTrue();
        assertThat(properties.getAdo().getAuthentication().getMode()).isEqualTo(AdoAuthenticationMode.PAT);
        var project = new ProjectApprovalConfigResolver(properties).findByProjectName("ADOnis 2.0 Test Project")
                .orElseThrow();
        assertThat(project.enabled()).isTrue();
    }

    @Test
    void repositoryTextFilesDoNotContainInternalAuthenticationProjectName() throws IOException
    {
        var forbiddenName = new String(Base64.getDecoder().decode("bmltYnVz"), StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT);

        try (var paths = Files.walk(Path.of(".")))
        {
            List<Path> matches = paths.filter(Files::isRegularFile).filter(this::isRepositoryTextFile)
                    .filter(path -> containsIgnoreCase(path, forbiddenName)).toList();
            assertThat(matches).isEmpty();
        }
    }

    private boolean isRepositoryTextFile(Path path)
    {
        var normalized = path.toString().replace('\\', '/');
        if (normalized.contains("/.git/") || normalized.contains("/target/"))
        {
            return false;
        }
        return normalized.matches("(?i).*\\.(java|yml|yaml|md|xml|properties|js|html|css|txt)$");
    }

    private boolean containsIgnoreCase(Path path, String forbiddenName)
    {
        try
        {
            return Files.readString(path).toLowerCase(Locale.ROOT).contains(forbiddenName);
        }
        catch (IOException exception)
        {
            throw new IllegalStateException("Failed to scan repository text file: " + path, exception);
        }
    }
}
