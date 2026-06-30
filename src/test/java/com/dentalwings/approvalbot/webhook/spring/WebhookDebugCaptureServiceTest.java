package com.dentalwings.approvalbot.webhook.spring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.dentalwings.approvalbot.event.NormalizedWorkItemEvent;

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

    private NormalizedWorkItemEvent request(String project, int workItemId, int revision)
    {
        return new NormalizedWorkItemEvent("ado-service-hook", "org", project, (long) workItemId, revision,
                "workitem.updated", null, null, null, null, "Test Case", java.util.Set.of());
    }
}


