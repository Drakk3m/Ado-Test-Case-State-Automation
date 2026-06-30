package com.dentalwings.approvalbot.webhook.spring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.dentalwings.approvalbot.webhook.spring.dto.AdoServiceHookWorkItemUpdatedRequest;

class WebhookDebugCaptureServiceTest
{

    @Test
    void disabledCaptureDoesNotStorePayload()
    {
        var service = new WebhookDebugCaptureService(false);

        service.capture("{\"eventType\":\"workitem.updated\"}", request("ProjectA", 101, 7));

        assertThat(service.latestCapture()).isEmpty();
    }

    @Test
    void enabledCaptureStoresOnlyLastEventWithMetadata()
    {
        var service = new WebhookDebugCaptureService(true);

        service.capture("first", request("ProjectA", 101, 7));
        service.capture("second", request("ProjectB", 202, 8));

        var capture = service.latestCapture().orElseThrow();
        assertThat(capture.rawRequestBody()).isEqualTo("second");
        assertThat(capture.project()).isEqualTo("ProjectB");
        assertThat(capture.workItemId()).isEqualTo(202);
        assertThat(capture.revision()).isEqualTo(8);
        assertThat(capture.eventType()).isEqualTo("workitem.updated");
        assertThat(capture.receivedAt()).isNotNull();
    }

    @Test
    void enabledCaptureHandlesMissingBodyAsEmptyString()
    {
        var service = new WebhookDebugCaptureService(true);

        service.capture(null, request("ProjectA", 1, 2));

        assertThat(service.latestCapture()).isPresent();
        assertThat(service.latestCapture().orElseThrow().rawRequestBody()).isEqualTo("");
    }

    private AdoServiceHookWorkItemUpdatedRequest request(String project, int workItemId, int revision)
    {
        var fields = java.util.Map.<String, Object>of("System.TeamProject", project);
        var revisionData = new AdoServiceHookWorkItemUpdatedRequest.Revision(revision, fields);
        var resource = new AdoServiceHookWorkItemUpdatedRequest.Resource((long) workItemId, revision, project,
                "Test Case", null, revisionData, java.util.Map.of());
        return new AdoServiceHookWorkItemUpdatedRequest("workitem.updated", "workitem.updated", "org", resource);
    }
}


