package com.dentalwings.approvalbot.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import com.dentalwings.approvalbot.config.ProjectApprovalConfig;

class EventClassifierTest
{

    private static final String SME_FIELD = "Custom.ApprovedBySME";
    private static final String SQA_FIELD = "Custom.ApprovedBySQA";
    private static final String CONFIGURED_ORGANIZATION = "configured-org";

    private final EventClassifier classifier = new EventClassifier(CONFIGURED_ORGANIZATION);

    @Test
    void enabledProjectSupportedTestCaseValidIdAndRevisionReturnsProcessable()
    {
        var classification = classifier.classify(validEvent(), config(true, "Test Case"));

        assertThat(classification.status()).isEqualTo(EventClassificationStatus.PROCESSABLE);
        assertThat(classification.maybeWorkItemKey()).hasValueSatisfying(key -> {
            assertThat(key.organization()).isEqualTo(CONFIGURED_ORGANIZATION);
            assertThat(key.project()).isEqualTo("ProjectA");
            assertThat(key.workItemId()).isEqualTo(123L);
        });
        assertThat(classification.maybeRevision()).contains(27);
    }

    @Test
    void processableEventUsesConfiguredOrganizationWhenWebhookOrganizationIsMissing()
    {
        var event = new AdoWebhookEvent("workitem.updated", null, validEvent().resource(), null);

        var classification = classifier.classify(event, config(true, "Test Case"));

        assertThat(classification.status()).isEqualTo(EventClassificationStatus.PROCESSABLE);
        assertThat(classification.maybeWorkItemKey()).hasValueSatisfying(key -> {
            assertThat(key.organization()).isEqualTo(CONFIGURED_ORGANIZATION);
            assertThat(key.project()).isEqualTo("ProjectA");
            assertThat(key.workItemId()).isEqualTo(123L);
        });
    }

    @Test
    void disabledProjectIsSkipped()
    {
        var classification = classifier.classify(validEvent(), config(false, "Test Case"));

        assertThat(classification.status()).isEqualTo(EventClassificationStatus.SKIPPED_DISABLED_PROJECT);
    }

    @Test
    void unsupportedWorkItemTypeIsSkipped()
    {
        var event = event("ProjectA", 123L, "Bug", 27, "Approval Bot", "user@example.com", Set.of());

        var classification = classifier.classify(event, config(true, "Test Case"));

        assertThat(classification.status()).isEqualTo(EventClassificationStatus.SKIPPED_UNSUPPORTED_WORK_ITEM_TYPE);
    }

    @Test
    void missingProjectIsMalformed()
    {
        var classification = classifier.classify(
                event(" ", 123L, "Test Case", 27, "User", "user@example.com", Set.of()), config(true, "Test Case"));

        assertThat(classification.status()).isEqualTo(EventClassificationStatus.FAILED_MALFORMED_EVENT);
    }

    @Test
    void missingWorkItemIdIsMalformed()
    {
        var classification = classifier.classify(
                event("ProjectA", null, "Test Case", 27, "User", "user@example.com", Set.of()),
                config(true, "Test Case"));

        assertThat(classification.status()).isEqualTo(EventClassificationStatus.FAILED_MALFORMED_EVENT);
    }

    @Test
    void invalidWorkItemIdIsMalformed()
    {
        var classification = classifier.classify(
                event("ProjectA", 0L, "Test Case", 27, "User", "user@example.com", Set.of()),
                config(true, "Test Case"));

        assertThat(classification.status()).isEqualTo(EventClassificationStatus.FAILED_MALFORMED_EVENT);
    }

    @Test
    void missingRevisionIsMalformed()
    {
        var classification = classifier.classify(
                event("ProjectA", 123L, "Test Case", null, "User", "user@example.com", Set.of()),
                config(true, "Test Case"));

        assertThat(classification.status()).isEqualTo(EventClassificationStatus.FAILED_MALFORMED_EVENT);
    }

    @Test
    void invalidRevisionIsMalformed()
    {
        var classification = classifier.classify(
                event("ProjectA", 123L, "Test Case", -1, "User", "user@example.com", Set.of()),
                config(true, "Test Case"));

        assertThat(classification.status()).isEqualTo(EventClassificationStatus.FAILED_MALFORMED_EVENT);
    }

    @Test
    void changedByEmailMatchingBotIdentityIsSkippedAsBotEvent()
    {
        var classification = classifier.classify(
                event("ProjectA", 123L, "Test Case", 27, "Any Name", "bot@example.com", Set.of()),
                config(true, "Test Case"));

        assertThat(classification.status()).isEqualTo(EventClassificationStatus.SKIPPED_BOT_EVENT);
    }

    @Test
    void changedByEmailMatchingBotIdentityWithDifferentCasingAndSpacingIsSkipped()
    {
        var classification = classifier.classify(
                event("ProjectA", 123L, "Test Case", 27, "Any Name", " BOT@example.com ", Set.of()),
                config(true, "Test Case"));

        assertThat(classification.status()).isEqualTo(EventClassificationStatus.SKIPPED_BOT_EVENT);
    }

    @Test
    void changedByDisplayNameMatchingBotButMissingEmailIsNotSkippedAsBot()
    {
        var classification = classifier.classify(
                event("ProjectA", 123L, "Test Case", 27, "Approval Bot", null, Set.of()), config(true, "Test Case"));

        assertThat(classification.status()).isEqualTo(EventClassificationStatus.PROCESSABLE);
    }

    @Test
    void missingChangedByEmailRemainsProcessableWhenEventIsOtherwiseValid()
    {
        var classification = classifier.classify(event("ProjectA", 123L, "Test Case", 27, "Human User", null, Set.of()),
                config(true, "Test Case"));

        assertThat(classification.status()).isEqualTo(EventClassificationStatus.PROCESSABLE);
    }

    @Test
    void unconfiguredChangedFieldsDoNotCauseSkipHere()
    {
        var classification = classifier.classify(
                event("ProjectA", 123L, "Test Case", 27, "User", "user@example.com", Set.of("Custom.Unconfigured")),
                config(true, "Test Case"));

        assertThat(classification.status()).isEqualTo(EventClassificationStatus.PROCESSABLE);
    }

    @Test
    void systemStateChangeIsNotClassifiedAsContentHere()
    {
        var classification = classifier.classify(
                event("ProjectA", 123L, "Test Case", 27, "User", "user@example.com", Set.of("System.State")),
                config(true, "Test Case"));

        assertThat(classification.status()).isEqualTo(EventClassificationStatus.PROCESSABLE);
    }

    @Test
    void approvalFieldChangeIsNotClassifiedAsApprovalHere()
    {
        var classification = classifier.classify(
                event("ProjectA", 123L, "Test Case", 27, "User", "user@example.com", Set.of(SME_FIELD)),
                config(true, "Test Case"));

        assertThat(classification.status()).isEqualTo(EventClassificationStatus.PROCESSABLE);
    }

    @Test
    void classifierDoesNotDependOnSpringMvcOrControllerClasses()
    {
        assertNoForbiddenTypeReferences("org.springframework.web", EventClassifier.class, AdoWebhookEvent.class,
                AdoWebhookResource.class);
        assertNoForbiddenTypeReferences("Controller", EventClassifier.class, AdoWebhookEvent.class,
                AdoWebhookResource.class);
    }

    @Test
    void classifierDoesNotDependOnHttpClientClasses()
    {
        assertNoForbiddenTypeReferences("WebClient", EventClassifier.class, AdoWebhookEvent.class,
                AdoWebhookResource.class);
        assertNoForbiddenTypeReferences("RestTemplate", EventClassifier.class, AdoWebhookEvent.class,
                AdoWebhookResource.class);
        assertNoForbiddenTypeReferences("ResponseEntity", EventClassifier.class, AdoWebhookEvent.class,
                AdoWebhookResource.class);
        assertNoForbiddenTypeReferences("HttpClient", EventClassifier.class, AdoWebhookEvent.class,
                AdoWebhookResource.class);
    }

    private AdoWebhookEvent validEvent()
    {
        return event("ProjectA", 123L, "Test Case", 27, "Human User", "user@example.com", Set.of("System.Title"));
    }

    private AdoWebhookEvent event(String project, Long workItemId, String workItemType, Integer revision,
            String displayName, String email, Set<String> changedFields)
    {
        return AdoWebhookEvent.workItemUpdated("org", project, workItemId, workItemType, revision, displayName, email,
                changedFields);
    }

    private ProjectApprovalConfig config(boolean enabled, String... supportedWorkItemTypes)
    {
        return new ProjectApprovalConfig("ProjectA", enabled, Set.of(supportedWorkItemTypes), SME_FIELD, SQA_FIELD,
                Set.of("System.Title"), Set.of("sme@example.com"), Set.of("sqa@example.com"), "bot@example.com");
    }

    private void assertNoForbiddenTypeReferences(String forbiddenText, Class<?>... classes)
    {
        for (Class<?> type : classes)
        {
            assertThat(type.getName()).doesNotContain(forbiddenText);
            for (Method method : type.getDeclaredMethods())
            {
                assertThat(method.toGenericString()).doesNotContain(forbiddenText);
            }
            for (var constructor : type.getDeclaredConstructors())
            {
                assertThat(constructor.toGenericString()).doesNotContain(forbiddenText);
            }
            for (var field : type.getDeclaredFields())
            {
                assertThat(field.toGenericString()).doesNotContain(forbiddenText);
            }
        }
    }
}
