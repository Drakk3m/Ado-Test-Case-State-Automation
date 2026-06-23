package com.dentalwings.approvalbot.ado;

import com.dentalwings.approvalbot.domain.PatchOperation;
import com.dentalwings.approvalbot.identity.EmailNormalizer;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdoClientContractsTest {

    @Test
    void adoClientContractCanReferenceExistingPatchOperation() throws NoSuchMethodException {
        Method patchMethod = AdoClient.class.getMethod("patchWorkItem", AdoWorkItemKey.class, List.class);

        assertThat(patchMethod.getGenericParameterTypes()[1].getTypeName())
                .contains(PatchOperation.class.getName());
    }

    @Test
    void adoWorkItemStoresRawFieldValuesWithoutNormalization() {
        var fields = Map.<String, Object>of(
                "System.Title", "  Raw title  ",
                "Custom.Flag", true,
                "Custom.Count", 7
        );

        var workItem = new AdoWorkItem(10, "ProjectA", "Test Case", 27, "In Review", fields);

        assertThat(workItem.fields().get("System.Title")).isEqualTo("  Raw title  ");
        assertThat(workItem.fields().get("Custom.Flag")).isEqualTo(true);
        assertThat(workItem.fields().get("Custom.Count")).isEqualTo(7);
    }

    @Test
    void adoWorkItemRevisionStoresRawFieldValuesWithoutNormalization() {
        var revision = new AdoWorkItemRevision(
                10,
                26,
                new AdoIdentity("Ana Perez", " ANA@example.com "),
                Map.of("System.Description", "  Raw description  "),
                Set.of("System.Description")
        );

        assertThat(revision.fields().get("System.Description")).isEqualTo("  Raw description  ");
        assertThat(revision.changedBy().emailOrLogin()).isEqualTo(" ANA@example.com ");
    }

    @Test
    void adoIdentityKeepsRawEmailAndNormalizationBelongsToIdentityServices() {
        var identity = new AdoIdentity("Ana Perez", " ANA@example.com ");

        assertThat(identity.emailOrLogin()).isEqualTo(" ANA@example.com ");
        assertThat(new EmailNormalizer().normalize(identity.emailOrLogin())).contains("ana@example.com");
    }

    @Test
    void contractClassesDoNotDependOnSpringHttpTypes() {
        assertNoForbiddenTypeReferences(
                "org.springframework.http",
                AdoClient.class,
                AdoWorkItemKey.class,
                AdoWorkItem.class,
                AdoWorkItemRevision.class,
                AdoIdentity.class,
                AdoPatchResult.class,
                AdoCommentResult.class
        );
    }

    @Test
    void contractClassesDoNotDependOnWebClientRestTemplateOrResponseEntity() {
        assertNoForbiddenTypeReferences(
                "WebClient",
                AdoClient.class,
                AdoWorkItemKey.class,
                AdoWorkItem.class,
                AdoWorkItemRevision.class,
                AdoIdentity.class,
                AdoPatchResult.class,
                AdoCommentResult.class
        );
        assertNoForbiddenTypeReferences("RestTemplate", AdoClient.class);
        assertNoForbiddenTypeReferences("ResponseEntity", AdoClient.class);
    }

    @Test
    void noRealHttpImplementationExistsInThisBranch() {
        assertThat(AdoClient.class.isInterface()).isTrue();
        assertThat(AdoClient.class.getPackageName()).isEqualTo("com.dentalwings.approvalbot.ado");
    }

    @Test
    void patchOperationsPassedToClientPreserveReplaceWithNull() {
        var client = new RecordingAdoClient();
        var clearOperation = PatchOperation.replaceField("Custom.ApprovedBySME", null);

        client.patchWorkItem(new AdoWorkItemKey("org", "ProjectA", 10), List.of(clearOperation));

        assertThat(client.patchOperations).containsExactly(new PatchOperation("replace", "/fields/Custom.ApprovedBySME", null));
    }

    private void assertNoForbiddenTypeReferences(String forbiddenText, Class<?>... contractClasses) {
        for (Class<?> contractClass : contractClasses) {
            assertThat(contractClass.getName()).doesNotContain(forbiddenText);
            for (Method method : contractClass.getDeclaredMethods()) {
                assertThat(method.toGenericString()).doesNotContain(forbiddenText);
            }
            for (var constructor : contractClass.getDeclaredConstructors()) {
                assertThat(constructor.toGenericString()).doesNotContain(forbiddenText);
            }
            for (var field : contractClass.getDeclaredFields()) {
                assertThat(field.toGenericString()).doesNotContain(forbiddenText);
            }
        }
    }

    private static class RecordingAdoClient implements AdoClient {

        private List<PatchOperation> patchOperations = List.of();

        @Override
        public AdoWorkItem fetchWorkItem(AdoWorkItemKey key) {
            return null;
        }

        @Override
        public AdoWorkItemRevision fetchWorkItemRevision(AdoWorkItemKey key, int revision) {
            return null;
        }

        @Override
        public AdoPatchResult patchWorkItem(AdoWorkItemKey key, List<PatchOperation> patchOperations) {
            this.patchOperations = List.copyOf(patchOperations);
            return AdoPatchResult.success(28);
        }

        @Override
        public AdoCommentResult createWorkItemComment(AdoWorkItemKey key, String commentText) {
            return AdoCommentResult.success("1");
        }
    }
}
