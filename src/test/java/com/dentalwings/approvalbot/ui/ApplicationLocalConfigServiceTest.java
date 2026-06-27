package com.dentalwings.approvalbot.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplicationLocalConfigServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void previewWritesYamlWithSecretPlaceholdersBracketProjectKeysStatesAndApprovalFields() {
        var service = new ApplicationLocalConfigService(tempDir.resolve("application-local.yml"), validatingService(validDiscovery()));
        var model = validModel();

        var preview = service.preview(model);

        assertThat(preview.finalYamlAllowed()).isTrue();
        assertThat(preview.yaml()).contains("personal-access-token: ${ADO_PERSONAL_ACCESS_TOKEN:}");
        assertThat(preview.yaml()).contains("value: ${ADO_WEBHOOK_SHARED_SECRET:}");
        assertThat(preview.yaml()).contains("'[ADOnis 2.0 Test Project]':");
        assertThat(preview.yaml()).contains("approved: 'Approval'");
        assertThat(preview.yaml()).contains("approved-by-sme: 'Custom.ApproverTech'");
        assertThat(preview.yaml()).contains("approved-by-sqa: 'Custom.ApproverTest'");
        assertThat(preview.yaml()).doesNotContain("real-pat");
        assertThat(preview.yaml()).doesNotContain("real-secret");
    }

    @Test
    void previewKeepsMultipleProjectConfigurationsIndependent() {
        var discovery = new FixedDiscovery(
                List.of("Project A", "Project B"),
                List.of("Test Case"),
                List.of("System.Title", "Custom.ProjectASme", "Custom.ProjectASqa", "Custom.ProjectBSme", "Custom.ProjectBSqa"),
                List.of("Design", "In Review", "Approved"),
                List.of("a-sme@example.test", "a-sqa@example.test", "b-sme@example.test", "b-sqa@example.test")
        );
        var service = new ApplicationLocalConfigService(tempDir.resolve("application-local.yml"), validatingService(discovery));
        var model = new ConfigUiModel();
        model.getAdo().setOrganization("ExampleOrg");
        model.getAdo().setHttpClientEnabled(true);
        model.getAdo().setDryRun(true);
        model.getBot().setIdentityEmail("bot@example.test");
        model.getAdo().getProjects().add(project(
                "Project A", "Custom.ProjectASme", "Custom.ProjectASqa", "a-sme@example.test", "a-sqa@example.test"
        ));
        model.getAdo().getProjects().add(project(
                "Project B", "Custom.ProjectBSme", "Custom.ProjectBSqa", "b-sme@example.test", "b-sqa@example.test"
        ));

        var preview = service.preview(model);

        assertThat(preview.yaml())
                .contains("'[Project A]':")
                .contains("approved-by-sme: 'Custom.ProjectASme'")
                .contains("- 'a-sme@example.test'")
                .contains("'[Project B]':")
                .contains("approved-by-sme: 'Custom.ProjectBSme'")
                .contains("- 'b-sme@example.test'");
    }

    @Test
    void saveWritesYamlOnlyWhenFinalValidationPasses() throws Exception {
        var configFile = tempDir.resolve("application-local.yml");
        var service = new ApplicationLocalConfigService(configFile, validatingService(validDiscovery()));

        service.save(validModel());

        var written = Files.readString(configFile, StandardCharsets.UTF_8);
        assertThat(written).contains("'[ADOnis 2.0 Test Project]':");
    }

    @Test
    void apiSaveResponseDoesNotExposeEnvironmentSecretValues() throws Exception {
        var configFile = tempDir.resolve("application-local.yml");
        var discovery = validDiscovery();
        var service = new ApplicationLocalConfigService(configFile, validatingService(discovery));
        var controller = new ConfigUiApiController(service, discovery);

        var response = controller.save(validModel());
        var json = new ObjectMapper().writeValueAsString(response);

        assertThat(json)
                .contains("${ADO_PERSONAL_ACCESS_TOKEN:}")
                .contains("${ADO_WEBHOOK_SHARED_SECRET:}")
                .doesNotContain("real-pat")
                .doesNotContain("real-secret");
    }

    @Test
    void discoveryEndpointResponseDoesNotExposeEnvironmentSecretValues() throws Exception {
        var configFile = tempDir.resolve("application-local.yml");
        var discovery = validDiscovery();
        var service = new ApplicationLocalConfigService(configFile, validatingService(discovery));
        var controller = new ConfigUiApiController(service, discovery);

        var response = controller.fields(new ConfigDiscoveryRequest(
                "STMN-Group",
                "ADOnis 2.0 Test Project",
                "Test Case",
                ""
        ));
        var json = new ObjectMapper().writeValueAsString(response);

        assertThat(json)
                .contains("Custom.ApproverTech")
                .doesNotContain("real-pat")
                .doesNotContain("real-secret")
                .doesNotContain("Authorization");
    }

    @Test
    void saveRejectsUncheckedAdoValues() {
        var service = new ApplicationLocalConfigService(tempDir.resolve("application-local.yml"),
                validatingService(new NotCheckedAdoConfigDiscoveryService()));

        assertThatThrownBy(() -> service.save(validModel()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unchecked ADO values");
    }

    @Test
    void blockingValidationErrorsPreventYamlPreview() {
        var service = new ApplicationLocalConfigService(tempDir.resolve("application-local.yml"), validatingService(validDiscovery()));
        var model = validModel();
        model.getAdo().setOrganization("");

        var preview = service.preview(model);

        assertThat(preview.draftYamlAvailable()).isFalse();
        assertThat(preview.yaml()).isEmpty();
        assertThatThrownBy(() -> service.previewYaml(model))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Blocking validation errors");
    }

    @Test
    void loadReadsExistingYamlIntoUiModel() throws Exception {
        Path configFile = tempDir.resolve("application-local.yml");
        Files.writeString(configFile, """
                ado:
                  organization: STMN-Group
                  http-client-enabled: true
                  dry-run: false
                  projects:
                    "[Sandbox A]":
                      enabled: true
                      supported-work-item-types:
                        - Test Case
                      states:
                        design: Design
                        in-review: In Review
                        approved: Approval
                      fields:
                        approved-by-sme: Custom.ApproverTech
                        approved-by-sqa: Custom.ApproverTest
                        reversible-business-fields:
                          - System.Title
                      approvals:
                        sme-users:
                          - user1@example.com
                        sqa-users:
                          - user2@example.com
                bot:
                  identity-email: bot@example.com
                webhook:
                  shared-secret:
                    enabled: true
                    header-name: X-ADO-Webhook-Secret
                """, StandardCharsets.UTF_8);

        var service = new ApplicationLocalConfigService(configFile, validatingService(validDiscovery()));
        var model = service.load();

        assertThat(model.getAdo().getOrganization()).isEqualTo("STMN-Group");
        assertThat(model.getAdo().getProjects()).hasSize(1);
        assertThat(model.getAdo().getProjects().get(0).getName()).isEqualTo("Sandbox A");
        assertThat(model.getBot().getIdentityEmail()).isEqualTo("bot@example.com");
    }

    static AdoConfigDraftValidationService validatingService(AdoConfigDiscoveryService discovery) {
        return new AdoConfigDraftValidationService(discovery, Map.of(
                "ADO_PERSONAL_ACCESS_TOKEN", "real-pat",
                "ADO_WEBHOOK_SHARED_SECRET", "real-secret"
        ));
    }

    static ConfigUiModel validModel() {
        var model = new ConfigUiModel();
        model.getAdo().setOrganization("STMN-Group");
        model.getAdo().setHttpClientEnabled(true);
        model.getAdo().setDryRun(true);

        var project = new ConfigUiModel.ProjectConfig();
        project.setName("ADOnis 2.0 Test Project");
        project.getSupportedWorkItemTypes().add("Test Case");
        project.getStates().setDesign("Design");
        project.getStates().setInReview("In Review");
        project.getStates().setApproved("Approval");
        project.getFields().setApprovedBySme("Custom.ApproverTech");
        project.getFields().setApprovedBySqa("Custom.ApproverTest");
        project.getFields().getReversibleBusinessFields().add("System.Title");
        project.getApprovals().getSmeUsers().add("sme@example.test");
        project.getApprovals().getSqaUsers().add("sqa@example.test");
        model.getAdo().getProjects().add(project);

        model.getBot().setIdentityEmail("bot@example.test");
        return model;
    }

    private static ConfigUiModel.ProjectConfig project(
            String name,
            String smeField,
            String sqaField,
            String smeUser,
            String sqaUser
    ) {
        var project = new ConfigUiModel.ProjectConfig();
        project.setName(name);
        project.getSupportedWorkItemTypes().add("Test Case");
        project.getStates().setDesign("Design");
        project.getStates().setInReview("In Review");
        project.getStates().setApproved("Approved");
        project.getFields().setApprovedBySme(smeField);
        project.getFields().setApprovedBySqa(sqaField);
        project.getFields().getReversibleBusinessFields().add("System.Title");
        project.getApprovals().getSmeUsers().add(smeUser);
        project.getApprovals().getSqaUsers().add(sqaUser);
        return project;
    }

    static AdoConfigDiscoveryService validDiscovery() {
        return new FixedDiscovery(
                List.of("ADOnis 2.0 Test Project"),
                List.of("Test Case"),
                List.of("System.Title", "Custom.ApproverTech", "Custom.ApproverTest"),
                List.of("Design", "In Review", "Approval"),
                List.of("sme@example.test", "sqa@example.test")
        );
    }

    record FixedDiscovery(
            List<String> projects,
            List<String> workItemTypes,
            List<String> fields,
            List<String> states,
            List<String> users
    ) implements AdoConfigDiscoveryService {

        @Override
        public ConfigLookupResult<String> listProjects(String organization) {
            return ConfigLookupResult.valid(projects);
        }

        @Override
        public ConfigLookupResult<String> listWorkItemTypes(String organization, String project) {
            return ConfigLookupResult.valid(workItemTypes);
        }

        @Override
        public ConfigLookupResult<String> listFieldReferenceNames(String organization, String project, String workItemType) {
            return ConfigLookupResult.valid(fields);
        }

        @Override
        public ConfigLookupResult<String> listObservedStateNames(String organization, String project, String workItemType) {
            return ConfigLookupResult.valid(states);
        }

        @Override
        public ConfigLookupResult<String> resolveUsers(String organization, List<String> usersToResolve) {
            return ConfigLookupResult.valid(users);
        }
    }
}
