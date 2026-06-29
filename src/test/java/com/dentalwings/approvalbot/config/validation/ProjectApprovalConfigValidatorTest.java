package com.dentalwings.approvalbot.config.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import com.dentalwings.approvalbot.config.ProjectApprovalConfig;
import com.dentalwings.approvalbot.config.WorkflowStateNames;

class ProjectApprovalConfigValidatorTest
{

    private static final String SME_FIELD = "Custom.ApprovedBySME";
    private static final String SQA_FIELD = "Custom.ApprovedBySQA";
    private static final String TITLE_FIELD = "System.Title";

    private final ProjectApprovalConfigValidator validator = new ProjectApprovalConfigValidator();

    @Test
    void validEnabledProjectPassesWithNoFatalErrors()
    {
        var result = validator.validate(validConfig());

        assertThat(result.hasFatalErrors()).isFalse();
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void missingSmeApprovalFieldIsFatal()
    {
        assertFatal(validConfig(null, SQA_FIELD, fields(TITLE_FIELD), users("ana@example.com"),
                        users("sam@example.com"), "bot@example.com", workItemTypes("Test Case")),
                "Missing SME approval field config.");
    }

    @Test
    void missingSqaApprovalFieldIsFatal()
    {
        assertFatal(validConfig(SME_FIELD, " ", fields(TITLE_FIELD), users("ana@example.com"), users("sam@example.com"),
                "bot@example.com", workItemTypes("Test Case")), "Missing SQA approval field config.");
    }

    @Test
    void missingSmeUsersIsFatal()
    {
        assertFatal(validConfig(SME_FIELD, SQA_FIELD, fields(TITLE_FIELD), Set.of(), users("sam@example.com"),
                "bot@example.com", workItemTypes("Test Case")), "Missing SME users.");
    }

    @Test
    void missingSqaUsersIsFatal()
    {
        assertFatal(validConfig(SME_FIELD, SQA_FIELD, fields(TITLE_FIELD), users("ana@example.com"), null,
                "bot@example.com", workItemTypes("Test Case")), "Missing SQA users.");
    }

    @Test
    void invalidSmeEmailIsFatal()
    {
        assertFatal(validConfig(SME_FIELD, SQA_FIELD, fields(TITLE_FIELD), users("not-an-email"),
                users("sam@example.com"), "bot@example.com", workItemTypes("Test Case")), "Invalid SME email entry.");
    }

    @Test
    void invalidSqaEmailIsFatal()
    {
        assertFatal(validConfig(SME_FIELD, SQA_FIELD, fields(TITLE_FIELD), users("ana@example.com"), users("sam"),
                "bot@example.com", workItemTypes("Test Case")), "Invalid SQA email entry.");
    }

    @Test
    void missingBotIdentityEmailIsFatal()
    {
        assertFatal(validConfig(SME_FIELD, SQA_FIELD, fields(TITLE_FIELD), users("ana@example.com"),
                users("sam@example.com"), "", workItemTypes("Test Case")), "Missing bot identity email.");
    }

    @Test
    void invalidBotIdentityEmailIsFatal()
    {
        assertFatal(validConfig(SME_FIELD, SQA_FIELD, fields(TITLE_FIELD), users("ana@example.com"),
                users("sam@example.com"), "bot", workItemTypes("Test Case")), "Invalid bot identity email.");
    }

    @Test
    void approvalFieldInsideReversibleBusinessFieldsIsFatal()
    {
        assertFatal(
                validConfig(SME_FIELD, SQA_FIELD, fields(TITLE_FIELD, SME_FIELD), users("ana@example.com"),
                        users("sam@example.com"), "bot@example.com", workItemTypes("Test Case")),
                "SME approval field appears in reversible business fields.");
    }

    @Test
    void systemStateInsideReversibleBusinessFieldsIsFatal()
    {
        assertFatal(
                validConfig(SME_FIELD, SQA_FIELD, fields(TITLE_FIELD, "System.State"), users("ana@example.com"),
                        users("sam@example.com"), "bot@example.com", workItemTypes("Test Case")),
                "System.State appears in reversible business fields.");
    }

    @Test
    void missingSupportedWorkItemTypesIsFatal()
    {
        assertFatal(
                validConfig(SME_FIELD, SQA_FIELD, fields(TITLE_FIELD), users("ana@example.com"),
                        users("sam@example.com"), "bot@example.com", Set.of()),
                "Supported work item types are missing or empty.");
    }

    @Test
    void enabledProjectWithoutTestCaseSupportIsFatal()
    {
        assertFatal(
                validConfig(SME_FIELD, SQA_FIELD, fields(TITLE_FIELD), users("ana@example.com"),
                        users("sam@example.com"), "bot@example.com", workItemTypes("Bug")),
                "Enabled V1 project must support Test Case work item type.");
    }

    @Test
    void missingReversibleBusinessFieldsIsFatal()
    {
        assertFatal(
                validConfig(SME_FIELD, SQA_FIELD, null, users("ana@example.com"), users("sam@example.com"),
                        "bot@example.com", workItemTypes("Test Case")),
                "Reversible business fields list is missing or empty.");
    }

    @Test
    void blankConfiguredWorkflowStateNameIsFatal()
    {
        assertFatal(validConfig(SME_FIELD, SQA_FIELD, fields(TITLE_FIELD), users("ana@example.com"),
                users("sam@example.com"), "bot@example.com", workItemTypes("Test Case"),
                new WorkflowStateNames("Design", " ", "Approved")), "Missing workflow in-review state name.");
    }

    @Test
    void duplicateSmeEmailCreatesWarning()
    {
        var result = validator.validate(
                validConfig(SME_FIELD, SQA_FIELD, fields(TITLE_FIELD), users("Ana@Example.com", " ana@example.com "),
                        users("sam@example.com"), "bot@example.com", workItemTypes("Test Case")));

        assertThat(result.hasFatalErrors()).isFalse();
        assertThat(result.warnings()).extracting(ConfigValidationIssue::message)
                .contains("Duplicate email within SME list.");
    }

    @Test
    void duplicateSqaEmailCreatesWarning()
    {
        var result = validator.validate(validConfig(SME_FIELD, SQA_FIELD, fields(TITLE_FIELD), users("ana@example.com"),
                users("Sam@Example.com", " sam@example.com "), "bot@example.com", workItemTypes("Test Case")));

        assertThat(result.hasFatalErrors()).isFalse();
        assertThat(result.warnings()).extracting(ConfigValidationIssue::message)
                .contains("Duplicate email within SQA list.");
    }

    @Test
    void sameEmailInSmeAndSqaCreatesWarningNotFatal()
    {
        var result = validator.validate(validConfig(SME_FIELD, SQA_FIELD, fields(TITLE_FIELD),
                users("dual@example.com"), users("DUAL@example.com"), "bot@example.com", workItemTypes("Test Case")));

        assertThat(result.hasFatalErrors()).isFalse();
        assertThat(result.warnings()).extracting(ConfigValidationIssue::message)
                .contains("Same email appears in both SME and SQA lists.");
    }

    @Test
    void disabledProjectWithOtherwiseInvalidConfigDoesNotProduceFatalErrors()
    {
        var result = validator
                .validate(new ProjectApprovalConfig("ProjectA", false, null, null, null, null, null, null, null));

        assertThat(result.hasFatalErrors()).isFalse();
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void emailNormalizationUsesTrimAndLowercase()
    {
        var result = validator
                .validate(validConfig(SME_FIELD, SQA_FIELD, fields(TITLE_FIELD), users(" ANA@example.com "),
                        users(" SAM@example.com "), " BOT@example.com ", workItemTypes("Test Case")));

        assertThat(result.hasFatalErrors()).isFalse();
        assertThat(result.issues()).isEmpty();
    }

    private void assertFatal(ProjectApprovalConfig config, String message)
    {
        var result = validator.validate(config);

        assertThat(result.hasFatalErrors()).isTrue();
        assertThat(result.fatalErrors()).extracting(ConfigValidationIssue::message).contains(message);
    }

    private ProjectApprovalConfig validConfig()
    {
        return validConfig(SME_FIELD, SQA_FIELD, fields(TITLE_FIELD), users("ana@example.com"),
                users("sam@example.com"), "bot@example.com", workItemTypes("Test Case"));
    }

    private ProjectApprovalConfig validConfig(String approvedBySmeField, String approvedBySqaField,
            Set<String> reversibleBusinessFields, Set<String> smeUsers, Set<String> sqaUsers, String botIdentityEmail,
            Set<String> supportedWorkItemTypes)
    {
        return validConfig(approvedBySmeField, approvedBySqaField, reversibleBusinessFields, smeUsers, sqaUsers,
                botIdentityEmail, supportedWorkItemTypes, WorkflowStateNames.defaults());
    }

    private ProjectApprovalConfig validConfig(String approvedBySmeField, String approvedBySqaField,
            Set<String> reversibleBusinessFields, Set<String> smeUsers, Set<String> sqaUsers, String botIdentityEmail,
            Set<String> supportedWorkItemTypes, WorkflowStateNames stateNames)
    {
        return new ProjectApprovalConfig("ProjectA", true, supportedWorkItemTypes, approvedBySmeField,
                approvedBySqaField, reversibleBusinessFields, smeUsers, sqaUsers, botIdentityEmail, stateNames);
    }

    private Set<String> fields(String... values)
    {
        return orderedSet(values);
    }

    private Set<String> users(String... values)
    {
        return orderedSet(values);
    }

    private Set<String> workItemTypes(String... values)
    {
        return orderedSet(values);
    }

    private Set<String> orderedSet(String... values)
    {
        return new LinkedHashSet<>(Arrays.asList(values));
    }
}
