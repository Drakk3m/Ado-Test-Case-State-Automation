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
