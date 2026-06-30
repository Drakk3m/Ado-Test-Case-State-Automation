package com.dentalwings.approvalbot.processing;

import com.dentalwings.approvalbot.ado.AdoClient;
import com.dentalwings.approvalbot.ado.AdoCommentResult;
import com.dentalwings.approvalbot.ado.AdoIdentity;
import com.dentalwings.approvalbot.ado.AdoPatchResult;
import com.dentalwings.approvalbot.ado.AdoWorkItem;
import com.dentalwings.approvalbot.ado.AdoWorkItemKey;
import com.dentalwings.approvalbot.ado.AdoWorkItemRevision;
import com.dentalwings.approvalbot.ado.RetryingAdoClient;
import com.dentalwings.approvalbot.ado.http.AdoClientNonRetryableException;
import com.dentalwings.approvalbot.ado.http.AdoClientRetryableException;
import com.dentalwings.approvalbot.config.ProjectApprovalConfig;
import com.dentalwings.approvalbot.domain.PatchOperation;
import com.dentalwings.approvalbot.domain.ProcessingResult;
import com.dentalwings.approvalbot.workflow.WorkflowEngine;
import com.dentalwings.approvalbot.workflow.comment.CommentBuilder;
import com.dentalwings.approvalbot.workflow.patch.PatchBuilder;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkItemProcessingServiceTest {

    private static final String SME_FIELD = "Custom.ApprovedBySME";
    private static final String SQA_FIELD = "Custom.ApprovedBySQA";
    private static final String TITLE_FIELD = "System.Title";
    private static final AdoWorkItemKey KEY = new AdoWorkItemKey("org", "ProjectA", 10);

    @Test
    void noOpWorkflowDecisionDoesNotCallPatchOrComment() {
        var client = fakeClient(
                workItem(10, 30, "Approved",
                        fields(SME_FIELD, "Ana <ana@example.com>", SQA_FIELD, "Sam <sam@example.com>")),
                revision(29, nonApprover(), fields("System.State", "Approved", SME_FIELD,
                        "Ana <ana@example.com>", SQA_FIELD, "Sam <sam@example.com>")));

        var result = service(client).process(command(30));

        assertThat(result.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(client.patchCalls).isZero();
        assertThat(client.commentCalls).isZero();
    }

    @Test
    void patchBuilderReturningOnlyRevisionTestDoesNotCallPatch() {
        var client = fakeClient(workItem(10, 30, "Approved", fields()),
                revision(29, nonApprover(), fields("System.State", "In Review")));
        var service = new WorkItemProcessingService(client, new WorkflowEngine(), new RevisionOnlyPatchBuilder(),
                new CommentBuilder());

        var result = service.process(command(30));

        assertThat(result.result()).isEqualTo(ProcessingResult.SKIPPED);
        assertThat(client.patchCalls).isZero();
        assertThat(client.commentCalls).isZero();
    }

    @Test
    void visibleWorkflowCorrectionCallsPatchWithPatchBuilderOperations() {
        var client = fakeClient(workItem(10, 30, "Approved", fields()),
                revision(29, nonApprover(), fields("System.State", "In Review")));

        var result = service(client).process(command(30));

        assertThat(result.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(client.patchCalls).isOne();
        assertThat(client.patchOperations).contains(PatchOperation.replaceField("System.State", "In Review"),
                PatchOperation.replaceField(SME_FIELD, ""), PatchOperation.replaceField(SQA_FIELD, ""));
    }

    @Test
    void patchOperationsIncludeRevisionTestFirst() {
        var client = fakeClient(workItem(10, 99, "Approved", fields()),
                revision(98, nonApprover(), fields("System.State", "In Review")));

        service(client).process(command(27));

        assertThat(client.patchOperations.get(0)).isEqualTo(PatchOperation.testRevision(99));
    }

    @Test
    void successfulPatchFollowedByRequiredCommentCallsCreateWorkItemComment() {
        var client = fakeClient(workItem(10, 30, "Approved", fields()),
                revision(29, nonApprover(), fields("System.State", "In Review")));

        var result = service(client).process(command(30));

        assertThat(result.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(client.commentCalls).isOne();
        assertThat(client.commentText).contains("does not have valid SME and SQA approvals");
    }

    @Test
    void patchFailureDoesNotCreateComment() {
        var client = fakeClient(workItem(10, 30, "Approved", fields()),
                revision(29, nonApprover(), fields("System.State", "In Review")));
        client.patchResult = AdoPatchResult.retryableFailure("conflict");

        var result = service(client).process(command(30));

        assertThat(result.result()).isEqualTo(ProcessingResult.FAILED_RETRYABLE);
        assertThat(client.commentCalls).isZero();
    }

    @Test
    void patchSuccessAndCommentFailureReturnsCompletedWithWarning() {
        var client = fakeClient(workItem(10, 30, "Approved", fields()),
                revision(29, nonApprover(), fields("System.State", "In Review")));
        client.commentResult = AdoCommentResult.failure("comment failed");

        var result = service(new RetryingAdoClient(client)).process(command(30));

        assertThat(result.result()).isEqualTo(ProcessingResult.COMPLETED_WITH_WARNING);
        assertThat(client.patchCalls).isOne();
        assertThat(client.commentCalls).isOne();
    }

    @Test
    void patchSuccessWithNoCommentReturnsCompleted() {
        var client = fakeClient(workItem(10, 30, "Design", fields(SME_FIELD, "Ana <ana@example.com>")),
                revision(29, nonApprover(), fields("System.State", "In Review")));

        var result = service(client).process(command(30));

        assertThat(result.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(client.patchCalls).isOne();
        assertThat(client.commentCalls).isZero();
    }

    @Test
    void patchFailureRetryableReturnsFailedRetryable() {
        var client = fakeClient(workItem(10, 30, "Approved", fields()),
                revision(29, nonApprover(), fields("System.State", "In Review")));
        client.patchResult = AdoPatchResult.retryableFailure("timeout");

        var result = service(client).process(command(30));

        assertThat(result.result()).isEqualTo(ProcessingResult.FAILED_RETRYABLE);
    }

    @Test
    void patchFailureNonRetryableReturnsFailedNonRetryable() {
        var client = fakeClient(workItem(10, 30, "Approved", fields()),
                revision(29, nonApprover(), fields("System.State", "In Review")));
        client.patchResult = AdoPatchResult.nonRetryableFailure("forbidden");

        var result = service(client).process(command(30));

        assertThat(result.result()).isEqualTo(ProcessingResult.FAILED_NON_RETRYABLE);
    }

    @Test
    void fetchWorkItemRetryableFailureReturnsFailedRetryableWithoutPatchOrComment() {
        var client = fakeClient(workItem(10, 30, "Approved", fields()),
                revision(29, nonApprover(), fields("System.State", "In Review")));
        client.fetchWorkItemException = new AdoClientRetryableException(
                "Azure DevOps read request failed with retryable status 503.");

        var result = service(client).process(command(30));

        assertThat(result.result()).isEqualTo(ProcessingResult.FAILED_RETRYABLE);
        assertThat(result.reason()).isEqualTo("ADO read failed with retryable error.");
        assertThat(client.patchCalls).isZero();
        assertThat(client.commentCalls).isZero();
    }

    @Test
    void exhaustedReadRetriesReturnFailedRetryableWithoutPatchOrComment() {
        var client = fakeClient(workItem(10, 30, "Approved", fields()),
                revision(29, nonApprover(), fields("System.State", "In Review")));
        client.fetchWorkItemException = new AdoClientRetryableException(
                "Azure DevOps read request failed with retryable status 503.");

        var result = service(new RetryingAdoClient(client)).process(command(30));

        assertThat(result.result()).isEqualTo(ProcessingResult.FAILED_RETRYABLE);
        assertThat(client.fetchWorkItemCalls).isEqualTo(3);
        assertThat(client.patchCalls).isZero();
        assertThat(client.commentCalls).isZero();
    }

    @Test
    void fetchWorkItemNonRetryableFailureReturnsFailedNonRetryableWithoutPatchOrComment() {
        var client = fakeClient(workItem(10, 30, "Approved", fields()),
                revision(29, nonApprover(), fields("System.State", "In Review")));
        client.fetchWorkItemException = new AdoClientNonRetryableException("Azure DevOps resource was not found.");

        var result = service(client).process(command(30));

        assertThat(result.result()).isEqualTo(ProcessingResult.FAILED_NON_RETRYABLE);
        assertThat(result.reason()).isEqualTo("ADO read failed with non-retryable error.");
        assertThat(client.patchCalls).isZero();
        assertThat(client.commentCalls).isZero();
    }

    @Test
    void fetchWorkItemRevisionRetryableFailureReturnsFailedRetryableWithoutPatchOrComment() {
        var client = fakeClient(workItem(10, 30, "Approved", fields()),
                revision(29, nonApprover(), fields("System.State", "In Review")));
        client.fetchRevisionException = new AdoClientRetryableException(
                "Azure DevOps read request failed with retryable status 429.");

        var result = service(client).process(command(30));

        assertThat(result.result()).isEqualTo(ProcessingResult.FAILED_RETRYABLE);
        assertThat(result.reason()).isEqualTo("ADO read failed with retryable error.");
        assertThat(client.patchCalls).isZero();
        assertThat(client.commentCalls).isZero();
    }

    @Test
    void processingUsesFetchedAdoDataNotWebhookChangedFields() {
        var client = fakeClient(workItem(10, 30, "In Review", fields(TITLE_FIELD, "New title")),
                revision(29, nonApprover(), fields("System.State", "In Review", TITLE_FIELD, "Old title")));

        var result = service(client).process(command(30));

        assertThat(result.result()).isEqualTo(ProcessingResult.COMPLETED);
        assertThat(client.patchOperations).contains(PatchOperation.replaceField(TITLE_FIELD, "Old title"));
    }

    @Test
    void currentWorkItemRevisionIsUsedForRevTest() {
        var client = fakeClient(workItem(10, 44, "In Review", fields(TITLE_FIELD, "New title")),
                revision(29, nonApprover(), fields("System.State", "In Review", TITLE_FIELD, "Old title")));

        service(client).process(command(30));

        assertThat(client.patchOperations.get(0)).isEqualTo(PatchOperation.testRevision(44));
    }

    @Test
    void previousRawFieldValuesFromAdoRevisionArePreservedThroughRevertPatch() {
        var client = fakeClient(workItem(10, 30, "In Review", fields(TITLE_FIELD, "New title")),
                revision(29, nonApprover(), fields("System.State", "In Review", TITLE_FIELD, "  Old title  ")));

        service(client).process(command(30));

        assertThat(client.patchOperations).contains(PatchOperation.replaceField(TITLE_FIELD, "  Old title  "));
    }

    @Test
    void orchestratorDoesNotDependOnSpringMvcOrControllerClasses() {
        assertNoForbiddenTypeReferences("org.springframework.web", WorkItemProcessingService.class,
                ProcessWorkItemCommand.class, WorkItemProcessingResult.class);
        assertNoForbiddenTypeReferences("Controller", WorkItemProcessingService.class, ProcessWorkItemCommand.class,
                WorkItemProcessingResult.class);
    }

    @Test
    void orchestratorDoesNotDependOnHttpClientClasses() {
        assertNoForbiddenTypeReferences("WebClient", WorkItemProcessingService.class, ProcessWorkItemCommand.class,
                WorkItemProcessingResult.class);
        assertNoForbiddenTypeReferences("RestTemplate", WorkItemProcessingService.class, ProcessWorkItemCommand.class,
                WorkItemProcessingResult.class);
        assertNoForbiddenTypeReferences("ResponseEntity", WorkItemProcessingService.class, ProcessWorkItemCommand.class,
                WorkItemProcessingResult.class);
    }

    private WorkItemProcessingService service(AdoClient client) {
        return new WorkItemProcessingService(client, new WorkflowEngine(), new PatchBuilder(), new CommentBuilder());
    }

    private ProcessWorkItemCommand command(int revision) {
        return new ProcessWorkItemCommand(KEY, revision, config());
    }

    private ProjectApprovalConfig config() {
        return new ProjectApprovalConfig("ProjectA", true, Set.of("Test Case"), SME_FIELD, SQA_FIELD,
                Set.of(TITLE_FIELD), Set.of("ana@example.com"), Set.of("sam@example.com"), "bot@example.com");
    }

    private FakeAdoClient fakeClient(AdoWorkItem currentWorkItem, AdoWorkItemRevision previousRevision) {
        return new FakeAdoClient(currentWorkItem, previousRevision);
    }

    private AdoWorkItem workItem(long id, int revision, String state, Map<String, Object> fields) {
        return new AdoWorkItem(id, "ProjectA", "Test Case", revision, state, fields);
    }

    private AdoWorkItemRevision revision(int revision, AdoIdentity changedBy, Map<String, Object> fields) {
        return new AdoWorkItemRevision(10, revision, changedBy, fields, fields.keySet());
    }

    private AdoIdentity nonApprover() {
        return new AdoIdentity("Nora User", "nora@example.com");
    }

    private Map<String, Object> fields(Object... entries) {
        var fields = new HashMap<String, Object>();
        for (int i = 0; i < entries.length; i += 2) {
            fields.put((String) entries[i], entries[i + 1]);
        }
        return fields;
    }

    private void assertNoForbiddenTypeReferences(String forbiddenText, Class<?>... classes) {
        for (Class<?> type : classes) {
            assertThat(type.getName()).doesNotContain(forbiddenText);
            for (Method method : type.getDeclaredMethods()) {
                assertThat(method.toGenericString()).doesNotContain(forbiddenText);
            }
            for (var constructor : type.getDeclaredConstructors()) {
                assertThat(constructor.toGenericString()).doesNotContain(forbiddenText);
            }
            for (var field : type.getDeclaredFields()) {
                assertThat(field.toGenericString()).doesNotContain(forbiddenText);
            }
        }
    }

    private static class FakeAdoClient implements AdoClient {

        private final AdoWorkItem currentWorkItem;
        private final AdoWorkItemRevision previousRevision;
        private AdoPatchResult patchResult = AdoPatchResult.success(31);
        private AdoCommentResult commentResult = AdoCommentResult.success("1");
        private RuntimeException fetchWorkItemException;
        private RuntimeException fetchRevisionException;
        private int fetchWorkItemCalls;
        private int patchCalls;
        private int commentCalls;
        private List<PatchOperation> patchOperations = List.of();
        private String commentText;

        private FakeAdoClient(AdoWorkItem currentWorkItem, AdoWorkItemRevision previousRevision) {
            this.currentWorkItem = currentWorkItem;
            this.previousRevision = previousRevision;
        }

        @Override
        public AdoWorkItem fetchWorkItem(AdoWorkItemKey key) {
            fetchWorkItemCalls++;
            if (fetchWorkItemException != null) {
                throw fetchWorkItemException;
            }
            return currentWorkItem;
        }

        @Override
        public AdoWorkItemRevision fetchWorkItemRevision(AdoWorkItemKey key, int revision) {
            if (fetchRevisionException != null) {
                throw fetchRevisionException;
            }
            return previousRevision;
        }

        @Override
        public AdoPatchResult patchWorkItem(AdoWorkItemKey key, List<PatchOperation> patchOperations) {
            patchCalls++;
            this.patchOperations = List.copyOf(patchOperations);
            return patchResult;
        }

        @Override
        public AdoCommentResult createWorkItemComment(AdoWorkItemKey key, String commentText) {
            commentCalls++;
            this.commentText = commentText;
            return commentResult;
        }
    }

    private static class RevisionOnlyPatchBuilder extends PatchBuilder {

        @Override
        public List<PatchOperation> build(int revision, com.dentalwings.approvalbot.domain.WorkflowDecision decision) {
            return List.of(PatchOperation.testRevision(revision));
        }
    }
}
