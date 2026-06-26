package com.dentalwings.approvalbot.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AdoConfigDraftValidationServiceTest {

    @Test
    void validationResultModelDistinguishesStatuses() {
        var result = new ConfigValidationResult();
        result.add("valid", ConfigValidationStatus.VALID, "ok");
        result.add("warning", ConfigValidationStatus.WARNING, "careful");
        result.add("error", ConfigValidationStatus.ERROR, "bad");
        result.add("unchecked", ConfigValidationStatus.NOT_CHECKED, "later");

        assertThat(result.fields()).extracting(ConfigFieldValidation::status)
                .containsExactly(
                        ConfigValidationStatus.VALID,
                        ConfigValidationStatus.WARNING,
                        ConfigValidationStatus.ERROR,
                        ConfigValidationStatus.NOT_CHECKED
                );
        assertThat(result.hasBlockingErrors()).isTrue();
        assertThat(result.hasUncheckedItems()).isTrue();
        assertThat(result.canGenerateFinalYaml()).isFalse();
    }

    @Test
    void fieldReferenceValidationDetectsMissingFieldFromAdoFieldList() {
        var discovery = new ApplicationLocalConfigServiceTest.FixedDiscovery(
                List.of("ADOnis 2.0 Test Project"),
                List.of("Test Case"),
                List.of("System.Title", "Custom.ApproverTech"),
                List.of("Design", "In Review", "Approval"),
                List.of("sme@example.test", "sqa@example.test")
        );
        var service = ApplicationLocalConfigServiceTest.validatingService(discovery);

        var result = service.validate(ApplicationLocalConfigServiceTest.validModel());

        assertThat(result.fields())
                .anySatisfy(field -> {
                    assertThat(field.field()).contains("approved-by-sqa");
                    assertThat(field.status()).isEqualTo(ConfigValidationStatus.ERROR);
                    assertThat(field.message()).contains("not found");
                });
    }

    @Test
    void workItemTypeValidationDetectsMissingTestCaseFromAdoTypeList() {
        var discovery = new ApplicationLocalConfigServiceTest.FixedDiscovery(
                List.of("ADOnis 2.0 Test Project"),
                List.of("Bug"),
                List.of("System.Title", "Custom.ApproverTech", "Custom.ApproverTest"),
                List.of("Design", "In Review", "Approval"),
                List.of("sme@example.test", "sqa@example.test")
        );
        var service = ApplicationLocalConfigServiceTest.validatingService(discovery);

        var result = service.validate(ApplicationLocalConfigServiceTest.validModel());

        assertThat(result.fields())
                .anySatisfy(field -> {
                    assertThat(field.field()).contains("supported-work-item-types");
                    assertThat(field.status()).isEqualTo(ConfigValidationStatus.ERROR);
                    assertThat(field.message()).contains("not found");
                });
    }

    @Test
    void userValidationDistinguishesUnresolvedUsers() {
        var discovery = new ApplicationLocalConfigServiceTest.FixedDiscovery(
                List.of("ADOnis 2.0 Test Project"),
                List.of("Test Case"),
                List.of("System.Title", "Custom.ApproverTech", "Custom.ApproverTest"),
                List.of("Design", "In Review", "Approval"),
                List.of("sme@example.test")
        );
        var service = ApplicationLocalConfigServiceTest.validatingService(discovery);

        var result = service.validate(ApplicationLocalConfigServiceTest.validModel());

        assertThat(result.fields())
                .anySatisfy(field -> {
                    assertThat(field.field()).contains("sqa-users");
                    assertThat(field.status()).isEqualTo(ConfigValidationStatus.ERROR);
                    assertThat(field.message()).contains("not resolved");
                });
    }

    @Test
    void displayNameOnlyUserIsNotMarkedValid() {
        var service = ApplicationLocalConfigServiceTest.validatingService(ApplicationLocalConfigServiceTest.validDiscovery());
        var model = ApplicationLocalConfigServiceTest.validModel();
        model.getAdo().getProjects().getFirst().getApprovals().getSmeUsers().clear();
        model.getAdo().getProjects().getFirst().getApprovals().getSmeUsers().add("Sandbox User");

        var result = service.validate(model);

        assertThat(result.fields())
                .anySatisfy(field -> {
                    assertThat(field.field()).contains("sme-users");
                    assertThat(field.status()).isEqualTo(ConfigValidationStatus.ERROR);
                    assertThat(field.message()).contains("displayName-only");
                });
    }

    @Test
    void duplicateSmeUserBlocksFinalYaml() {
        var service = ApplicationLocalConfigServiceTest.validatingService(ApplicationLocalConfigServiceTest.validDiscovery());
        var model = ApplicationLocalConfigServiceTest.validModel();
        model.getAdo().getProjects().getFirst().getApprovals().getSmeUsers().add("SME@EXAMPLE.TEST");

        var result = service.validate(model);

        assertThat(result.canGenerateFinalYaml()).isFalse();
        assertThat(result.fields())
                .anySatisfy(field -> {
                    assertThat(field.field()).contains("sme-users");
                    assertThat(field.status()).isEqualTo(ConfigValidationStatus.ERROR);
                    assertThat(field.message()).contains("duplicate identities");
                });
    }

    @Test
    void duplicateSqaUserBlocksFinalYaml() {
        var service = ApplicationLocalConfigServiceTest.validatingService(ApplicationLocalConfigServiceTest.validDiscovery());
        var model = ApplicationLocalConfigServiceTest.validModel();
        model.getAdo().getProjects().getFirst().getApprovals().getSqaUsers().add("SQA@EXAMPLE.TEST");

        var result = service.validate(model);

        assertThat(result.canGenerateFinalYaml()).isFalse();
        assertThat(result.fields())
                .anySatisfy(field -> {
                    assertThat(field.field()).contains("sqa-users");
                    assertThat(field.status()).isEqualTo(ConfigValidationStatus.ERROR);
                    assertThat(field.message()).contains("duplicate identities");
                });
    }

    @Test
    void sameIdentityAcrossSmeAndSqaIsVisibleWarning() {
        var discovery = new ApplicationLocalConfigServiceTest.FixedDiscovery(
                List.of("ADOnis 2.0 Test Project"),
                List.of("Test Case"),
                List.of("System.Title", "Custom.ApproverTech", "Custom.ApproverTest"),
                List.of("Design", "In Review", "Approval"),
                List.of("sme@example.test", "sqa@example.test")
        );
        var service = ApplicationLocalConfigServiceTest.validatingService(discovery);
        var model = ApplicationLocalConfigServiceTest.validModel();
        model.getAdo().getProjects().getFirst().getApprovals().getSqaUsers().add("sme@example.test");

        var result = service.validate(model);

        assertThat(result.fields())
                .anySatisfy(field -> {
                    assertThat(field.field()).contains(".approvals");
                    assertThat(field.status()).isEqualTo(ConfigValidationStatus.WARNING);
                    assertThat(field.message()).contains("both SME and SQA");
                });
    }

    @Test
    void duplicateApprovalFieldsAreBlockingErrors() {
        var service = ApplicationLocalConfigServiceTest.validatingService(ApplicationLocalConfigServiceTest.validDiscovery());
        var model = ApplicationLocalConfigServiceTest.validModel();
        var fields = model.getAdo().getProjects().getFirst().getFields();
        fields.setApprovedBySqa("Custom.ApproverTech");

        var result = service.validate(model);

        assertThat(result.canGenerateFinalYaml()).isFalse();
        assertThat(result.fields())
                .anySatisfy(field -> {
                    assertThat(field.field()).contains("approved-by-sqa");
                    assertThat(field.status()).isEqualTo(ConfigValidationStatus.ERROR);
                    assertThat(field.message()).contains("must be different");
                });
    }

    @Test
    void reversibleBusinessFieldsMustNotDuplicateApprovalFieldsOrEachOther() {
        var service = ApplicationLocalConfigServiceTest.validatingService(ApplicationLocalConfigServiceTest.validDiscovery());
        var model = ApplicationLocalConfigServiceTest.validModel();
        var reversibleFields = model.getAdo().getProjects().getFirst().getFields().getReversibleBusinessFields();
        reversibleFields.add("System.Title");
        reversibleFields.add("Custom.ApproverTech");

        var result = service.validate(model);

        assertThat(result.canGenerateFinalYaml()).isFalse();
        assertThat(result.fields())
                .anySatisfy(field -> {
                    assertThat(field.field()).contains("reversible-business-fields");
                    assertThat(field.status()).isEqualTo(ConfigValidationStatus.ERROR);
                    assertThat(field.message()).contains("duplicates");
                })
                .anySatisfy(field -> {
                    assertThat(field.field()).contains("reversible-business-fields");
                    assertThat(field.status()).isEqualTo(ConfigValidationStatus.ERROR);
                    assertThat(field.message()).contains("SME approval field");
                });
    }

    @Test
    void validDistinctFieldsRemainAccepted() {
        var service = ApplicationLocalConfigServiceTest.validatingService(ApplicationLocalConfigServiceTest.validDiscovery());

        var result = service.validate(ApplicationLocalConfigServiceTest.validModel());

        assertThat(result.fields())
                .filteredOn(field -> field.field().contains(".fields."))
                .noneSatisfy(field -> assertThat(field.message()).contains("duplicate", "must be different", "also be reversible"));
    }

    @Test
    void missingPatEnvironmentVariableBlocksAdoBackedValidation() {
        var service = new AdoConfigDraftValidationService(ApplicationLocalConfigServiceTest.validDiscovery(), java.util.Map.of(
                "ADO_WEBHOOK_SHARED_SECRET", "real-secret"
        ));

        var result = service.validate(ApplicationLocalConfigServiceTest.validModel());

        assertThat(result.canGenerateFinalYaml()).isFalse();
        assertThat(result.fields())
                .anySatisfy(field -> {
                    assertThat(field.field()).isEqualTo("ado.personal-access-token");
                    assertThat(field.status()).isEqualTo(ConfigValidationStatus.ERROR);
                    assertThat(field.message()).contains("required");
                });
    }

    @Test
    void notCheckedDiscoveryMarksValuesAsNotCheckedWithoutPretendingValid() {
        var service = ApplicationLocalConfigServiceTest.validatingService(new NotCheckedAdoConfigDiscoveryService());

        var result = service.validate(ApplicationLocalConfigServiceTest.validModel());

        assertThat(result.hasBlockingErrors()).isFalse();
        assertThat(result.hasUncheckedItems()).isTrue();
        assertThat(result.canGenerateDraftYaml()).isTrue();
        assertThat(result.canGenerateFinalYaml()).isFalse();
        assertThat(result.fields()).anyMatch(field -> field.status() == ConfigValidationStatus.NOT_CHECKED);
    }
}
