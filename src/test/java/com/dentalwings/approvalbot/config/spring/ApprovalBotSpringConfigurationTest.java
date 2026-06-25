package com.dentalwings.approvalbot.config.spring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.dentalwings.approvalbot.ApprovalBotApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalBotSpringConfigurationTest {

    @Test
    void springPropertiesBindValidYamlCorrectly() {
        var properties = bind(validYaml());

        assertThat(properties.getAdo().getOrganization()).isEqualTo("my-org");
        assertThat(properties.getAdo().getPersonalAccessToken()).isEqualTo("test-token");
        assertThat(properties.getAdo().isDryRun()).isTrue();
        assertThat(properties.getWebhook().getSharedSecret().isEnabled()).isTrue();
        assertThat(properties.getWebhook().getSharedSecret().getHeaderName()).isEqualTo("X-ADO-Webhook-Secret");
        assertThat(properties.getWebhook().getSharedSecret().getValue()).isEqualTo("test-webhook-secret");
        assertThat(properties.getAdo().getProjects()).containsKey("ProjectA");
        assertThat(properties.getAdo().getProjects().get("ProjectA").getSupportedWorkItemTypes())
                .containsExactly("Test Case");
        assertThat(properties.getAdo().getProjects().get("ProjectA").getFields().getApprovedBySme())
                .isEqualTo("Custom.ApprovedBySME");
        assertThat(properties.getAdo().getProjects().get("ProjectA").getStates().getDesign()).isEqualTo("Design");
        assertThat(properties.getAdo().getProjects().get("ProjectA").getStates().getInReview()).isEqualTo("In Review");
        assertThat(properties.getAdo().getProjects().get("ProjectA").getStates().getApproved()).isEqualTo("Approved");
        assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(3);
        assertThat(properties.getIdempotency().getType()).isEqualTo("sqlite");
    }

    @Test
    void springContextRegistersApprovalBotProperties() {
        new ApplicationContextRunner()
                .withUserConfiguration(ApprovalBotApplication.class)
                .withPropertyValues(
                        "ado.organization=my-org",
                        "ado.personal-access-token=test-token",
                        "ado.projects.ProjectA.enabled=true",
                        "ado.projects.ProjectA.supported-work-item-types[0]=Test Case",
                        "ado.projects.ProjectA.fields.approved-by-sme=Custom.ApprovedBySME",
                        "ado.projects.ProjectA.fields.approved-by-sqa=Custom.ApprovedBySQA",
                        "ado.projects.ProjectA.fields.reversible-business-fields[0]=System.Title",
                        "ado.projects.ProjectA.approvals.sme-users[0]=ana.perez@company.com",
                        "ado.projects.ProjectA.approvals.sqa-users[0]=carlos.gomez@company.com",
                        "bot.identity-email=ado-approval-bot@company.com",
                        "webhook.shared-secret.value=test-webhook-secret",
                        "idempotency.type=in-memory"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ApprovalBotProperties.class);
                    assertThat(context.getBean(ApprovalBotProperties.class).getAdo().getProjects())
                            .containsKey("ProjectA");
                });
    }

    @Test
    void boundPropertiesMapToProjectApprovalConfig() {
        var properties = bind(validYaml());
        var mapper = new ProjectApprovalConfigMapper();

        var mapped = mapper.toProjectConfigs(properties).get("ProjectA");

        assertThat(mapped.projectName()).isEqualTo("ProjectA");
        assertThat(mapped.enabled()).isTrue();
        assertThat(mapped.approvedBySmeField()).isEqualTo("Custom.ApprovedBySME");
        assertThat(mapped.approvedBySqaField()).isEqualTo("Custom.ApprovedBySQA");
        assertThat(mapped.smeUsers()).containsExactly("ana.perez@company.com");
        assertThat(mapped.sqaUsers()).containsExactly("carlos.gomez@company.com");
        assertThat(mapped.botIdentityEmail()).isEqualTo("ado-approval-bot@company.com");
        assertThat(mapped.stateNames().design()).isEqualTo("Design");
        assertThat(mapped.stateNames().inReview()).isEqualTo("In Review");
        assertThat(mapped.stateNames().approved()).isEqualTo("Approved");
    }

    @Test
    void projectStateNamesCanOverrideApprovedState() {
        var properties = bind(validYamlWithStates("Design", "In Review", "Approval"));
        var mapped = new ProjectApprovalConfigMapper().toProjectConfigs(properties).get("ProjectA");

        assertThat(properties.getAdo().getProjects().get("ProjectA").getStates().getApproved()).isEqualTo("Approval");
        assertThat(mapped.stateNames().approved()).isEqualTo("Approval");
    }

    @Test
    void validEnabledProjectPassesStartupValidation() {
        var validator = startupValidator(bind(validYaml()));

        var report = validator.validate();

        assertThat(report.fatalMessages()).isEmpty();
        assertThat(report.warningMessages()).isEmpty();
    }

    @Test
    void missingBotIdentityEmailFailsStartupValidation() {
        var validator = startupValidator(bind(validYaml().replace("ado-approval-bot@company.com", "")));

        assertThatThrownBy(validator::validate)
                .isInstanceOf(ApprovalBotConfigurationException.class)
                .hasMessageContaining("Project 'ProjectA': Missing bot identity email.");
    }

    @Test
    void missingAdoTokenFailsStartupValidationWhenHttpClientIsEnabled() {
        var validator = startupValidator(bind(validYaml()
                .replace("personal-access-token: test-token", "personal-access-token: \"\"")
                .replace("http-client-enabled: false", "http-client-enabled: true")));

        assertThatThrownBy(validator::validate)
                .isInstanceOf(ApprovalBotConfigurationException.class)
                .hasMessageContaining("ado.personal-access-token is missing.");
    }

    @Test
    void missingAdoTokenPassesStartupValidationWhenHttpClientIsDisabled() {
        var validator = startupValidator(bind(validYaml()
                .replace("personal-access-token: test-token", "personal-access-token: \"\"")));

        var report = validator.validate();

        assertThat(report.fatalMessages()).isEmpty();
    }

    @Test
    void missingAdoOrganizationFailsStartupValidationWhenHttpClientIsEnabled() {
        var validator = startupValidator(bind(validYaml()
                .replace("organization: my-org", "organization: \"\"")
                .replace("http-client-enabled: false", "http-client-enabled: true")));

        assertThatThrownBy(validator::validate)
                .isInstanceOf(ApprovalBotConfigurationException.class)
                .hasMessageContaining("ado.organization is missing while ado.http-client-enabled=true.");
    }

    @Test
    void missingAdoOrganizationPassesStartupValidationWhenHttpClientIsDisabled() {
        var validator = startupValidator(bind(validYaml()
                .replace("organization: my-org", "organization: \"\"")));

        var report = validator.validate();

        assertThat(report.fatalMessages()).isEmpty();
    }

    @Test
    void missingWebhookSharedSecretFailsStartupValidationWhenEnabled() {
        var validator = startupValidator(bind(validYaml().replace("value: test-webhook-secret", "value: \"\"")));

        assertThatThrownBy(validator::validate)
                .isInstanceOf(ApprovalBotConfigurationException.class)
                .hasMessageContaining("webhook.shared-secret.value is missing while webhook.shared-secret.enabled=true.")
                .hasMessageNotContaining("test-webhook-secret");
    }

    @Test
    void webhookSharedSecretPassesStartupValidationWhenEnabledAndValuePresent() {
        var validator = startupValidator(bind(validYaml()));

        var report = validator.validate();

        assertThat(report.fatalMessages()).isEmpty();
    }

    @Test
    void webhookSharedSecretPassesStartupValidationWhenDisabledAndValueMissing() {
        var validator = startupValidator(bind(validYaml()
                .replace("enabled: true\n    header-name: X-ADO-Webhook-Secret\n    value: test-webhook-secret",
                        "enabled: false\n    header-name: X-ADO-Webhook-Secret\n    value: \"\"")));

        var report = validator.validate();

        assertThat(report.fatalMessages()).isEmpty();
    }

    @Test
    void invalidEnabledProjectFailsStartupValidationWithFatalIssueDetails() {
        var validator = startupValidator(bind(validYaml()
                .replace("approved-by-sme: Custom.ApprovedBySME", "approved-by-sme: \"\"")
                .replace("sme-users:\n          - ana.perez@company.com", "sme-users: []")));

        assertThatThrownBy(validator::validate)
                .isInstanceOf(ApprovalBotConfigurationException.class)
                .hasMessageContaining("Project 'ProjectA': Missing SME approval field config.")
                .hasMessageContaining("Project 'ProjectA': Missing SME users.");
    }

    @Test
    void blankWorkflowStateNameFailsStartupValidation() {
        var validator = startupValidator(bind(validYamlWithStates("Design", "\"\"", "Approved")));

        assertThatThrownBy(validator::validate)
                .isInstanceOf(ApprovalBotConfigurationException.class)
                .hasMessageContaining("Project 'ProjectA': Missing workflow in-review state name.");
    }

    @Test
    void disabledInvalidProjectDoesNotFailStartupValidation() {
        var validator = startupValidator(bind(validYaml()
                .replace("enabled: true", "enabled: false")
                .replace("approved-by-sme: Custom.ApprovedBySME", "approved-by-sme: \"\"")
                .replace("sme-users:\n          - ana.perez@company.com", "sme-users: []")));

        var report = validator.validate();

        assertThat(report.fatalMessages()).isEmpty();
    }

    @Test
    void duplicateSmeEmailReportsWarningButDoesNotFail() {
        var validator = startupValidator(bind(validYaml().replace(
                "sme-users:\n          - ana.perez@company.com",
                "sme-users:\n          - Ana.Perez@company.com\n          -  ana.perez@company.com ")));

        var report = validator.validate();

        assertThat(report.fatalMessages()).isEmpty();
        assertThat(report.warningMessages())
                .contains("Project 'ProjectA': Duplicate email within SME list.");
    }

    @Test
    void dualRoleUserReportsWarningButDoesNotFail() {
        var validator = startupValidator(bind(validYaml().replace(
                "sqa-users:\n          - carlos.gomez@company.com",
                "sqa-users:\n          - ANA.PEREZ@company.com")));

        var report = validator.validate();

        assertThat(report.fatalMessages()).isEmpty();
        assertThat(report.warningMessages())
                .contains("Project 'ProjectA': Same email appears in both SME and SQA lists.");
    }

    @Test
    void approvalFieldInsideReversibleBusinessFieldsFailsStartupValidation() {
        var validator = startupValidator(bind(validYaml().replace(
                "- System.Title",
                "- System.Title\n          - Custom.ApprovedBySME")));

        assertThatThrownBy(validator::validate)
                .isInstanceOf(ApprovalBotConfigurationException.class)
                .hasMessageContaining("Project 'ProjectA': SME approval field appears in reversible business fields.");
    }

    @Test
    void systemStateInsideReversibleBusinessFieldsFailsStartupValidation() {
        var validator = startupValidator(bind(validYaml().replace(
                "- System.Title",
                "- System.Title\n          - System.State")));

        assertThatThrownBy(validator::validate)
                .isInstanceOf(ApprovalBotConfigurationException.class)
                .hasMessageContaining("Project 'ProjectA': System.State appears in reversible business fields.");
    }

    @Test
    void applicationYmlDoesNotContainRealSecrets() throws IOException {
        var yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(yaml).contains("${ADO_PERSONAL_ACCESS_TOKEN:}");
        assertThat(yaml).contains("${ADO_WEBHOOK_SHARED_SECRET:}");
        assertThat(yaml).doesNotContain("test-token");
        assertThat(yaml).doesNotContain("test-webhook-secret");
        assertThat(yaml).doesNotContain("Basic ");
        assertThat(yaml).doesNotContain("Bearer ");
    }

    @Test
    void sampleSandboxYamlBindsBracketedProjectNameWithSpacesAndDots() throws IOException {
        var properties = bind(Files.readString(Path.of("docs/sample-application-sandbox.yml")));

        assertThat(properties.getAdo().getProjects())
                .containsKey("Example Sandbox Project 2.0")
                .doesNotContainKey("[Example Sandbox Project 2.0]");
    }

    private ProjectApprovalConfigStartupValidator startupValidator(ApprovalBotProperties properties) {
        return new ProjectApprovalConfigStartupValidator(
                properties,
                new ProjectApprovalConfigMapper(),
                new com.dentalwings.approvalbot.config.validation.ProjectApprovalConfigValidator()
        );
    }

    private ApprovalBotProperties bind(String yaml) {
        var environment = new StandardEnvironment();
        var loader = new YamlPropertySourceLoader();
        try {
            for (var propertySource : loader.load("approvalBotTest", new ByteArrayResource(yaml.getBytes()))) {
                environment.getPropertySources().addFirst(propertySource);
            }
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
        return Binder.get(environment).bind("", ApprovalBotProperties.class)
                .orElseThrow(() -> new AssertionError("Approval bot properties were not bound."));
    }

    private String validYaml() {
        return """
                ado:
                  organization: my-org
                  personal-access-token: test-token
                  http-client-enabled: false
                  projects:
                    ProjectA:
                      enabled: true
                      supported-work-item-types:
                        - Test Case
                      fields:
                        approved-by-sme: Custom.ApprovedBySME
                        approved-by-sqa: Custom.ApprovedBySQA
                        reversible-business-fields:
                          - System.Title
                          - System.Description
                      approvals:
                        sme-users:
                          - ana.perez@company.com
                        sqa-users:
                          - carlos.gomez@company.com

                bot:
                  identity-email: ado-approval-bot@company.com

                webhook:
                  shared-secret:
                    enabled: true
                    header-name: X-ADO-Webhook-Secret
                    value: test-webhook-secret

                retry:
                  max-attempts: 3
                  default-backoff-seconds: 30
                  respect-retry-after: true

                idempotency:
                  type: sqlite
                  sqlite-path: ./data/approval-bot.sqlite
                  ttl-hours: 24
                  max-records: 10000
                """;
    }

    private String validYamlWithStates(String design, String inReview, String approved) {
        return validYaml().replaceFirst(
                "(?m)^(\\s*)fields:",
                "$1states:\n"
                        + "$1  design: " + design + "\n"
                        + "$1  in-review: " + inReview + "\n"
                        + "$1  approved: " + approved + "\n"
                        + "$1fields:"
        );
    }
}
