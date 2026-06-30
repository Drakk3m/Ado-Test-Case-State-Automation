package com.dentalwings.approvalbot.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApprovalBotYamlConfigLoaderTest
{

    @TempDir
    Path tempDir;

    @Test
    void loadsApprovalBotConfigurationFromYaml() throws IOException
    {
        var file = tempDir.resolve("application-test.yml");
        Files.writeString(file, """
                ado:
                  organization: STMN-Group
                  personal-access-token: ${ADO_PERSONAL_ACCESS_TOKEN:}
                  http-client-enabled: true
                  dry-run: true
                  projects:
                    "[Project A]":
                      enabled: true
                      supported-work-item-types:
                        - Test Case
                      fields:
                        approved-by-sme: Custom.ApproverTech
                        approved-by-sqa: Custom.ApproverTest
                        reversible-business-fields:
                          - System.Title
                      approvals:
                        sme-users:
                          - sme@example.com
                        sqa-users:
                          - sqa@example.com
                bot:
                  identity-email: bot@example.com
                """);

        var properties = new ApprovalBotYamlConfigLoader().load(file);

        assertThat(properties.getAdo().getOrganization()).isEqualTo("STMN-Group");
        assertThat(properties.getAdo().isHttpClientEnabled()).isTrue();
        assertThat(properties.getAdo().isDryRun()).isTrue();
        assertThat(properties.getAdo().getProjects()).containsKey("Project A");
        assertThat(properties.getAdo().getProjects().get("Project A").getSupportedWorkItemTypes())
                .contains("Test Case");
        assertThat(properties.getBot().getIdentityEmail()).isEqualTo("bot@example.com");
    }
}

