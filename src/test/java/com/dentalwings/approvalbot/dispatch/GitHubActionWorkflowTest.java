package com.dentalwings.approvalbot.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
        var authenticationAction = Files.readString(
                Path.of(".github/actions/azure-service-principal-auth/action.yml"));
        var config = Files.readString(Path.of("config/application-github-action.yml"));
        var documentation = Files.readString(Path.of("docs/github-action-e2e-testing.md"));

        assertThat(new YamlPropertySourceLoader().load("github-action",
                new FileSystemResource(".github/workflows/ado-work-item-updated.yml"))).isNotEmpty();
        assertThat(new YamlPropertySourceLoader().load("azure-auth-action",
                new FileSystemResource(".github/actions/azure-service-principal-auth/action.yml"))).isNotEmpty();

        assertThat(workflow).contains("repository_dispatch:", "types: [ado-work-item-updated]",
                "github.event.client_payload.ado_event.resource.workItemId",
                "CLIENT_PAYLOAD: ${{ toJson(github.event.client_payload) }}",
                "com.dentalwings.approvalbot.dispatch.RepositoryDispatchOneShotRunner",
                "--config \"config/application-github-action.yml\"",
                "uses: ./.github/actions/azure-service-principal-auth",
                "sp_client_id: ${{ secrets.CAL__AZURE_CLIENT_ID }}",
                "sp_client_tenant_id: ${{ secrets.CAL__AZURE_TENANT_ID }}",
                "sp_client_secret: ${{ secrets.CAL__AZURE_CLIENT_SECRET }}",
                "ADO_ACCESS_TOKEN: ${{ steps.auth_token.outputs.token }}")
                .doesNotContain("SpringApplication", "ADO_WEBHOOK_SHARED_SECRET", "echo \"$CLIENT_PAYLOAD\"",
                        "secrets.ADO_PERSONAL_ACCESS_TOKEN");
        var maskedInternalName = new String(new char[] { 110, 105, 109, 98, 117, 115 });
        assertThat(workflow.toLowerCase()).doesNotContain(maskedInternalName);
        assertThat(documentation.toLowerCase()).doesNotContain(maskedInternalName);
        assertThat(authenticationAction).contains("using: composite", "auth_method: \"sp_creds\"",
                "value: ${{ steps.auth_token.outputs.token }}", "sp_client_id:", "sp_client_tenant_id:",
                "sp_client_secret:");
        assertThat(config).contains("mode: bearer", "bearer-token: ${ADO_ACCESS_TOKEN:}", "dry-run: true")
                .doesNotContain("personal-access-token", "ADO_PERSONAL_ACCESS_TOKEN")
                .doesNotContain("webhook:", "sqlite");

        var properties = new ApprovalBotYamlConfigLoader().load(Path.of("config/application-github-action.yml"));
        assertThat(properties.getAdo().isDryRun()).isTrue();
        assertThat(properties.getAdo().getAuthentication().getMode()).isEqualTo(AdoAuthenticationMode.BEARER);
        var project = new ProjectApprovalConfigResolver(properties).findByProjectName("Example Sandbox Project")
                .orElseThrow();
        assertThat(project.enabled()).isTrue();
    }
}
