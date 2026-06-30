package com.dentalwings.approvalbot.webhook.spring;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.dentalwings.approvalbot.webhook.spring.WebhookDebugCaptureService.CapturedWebhookEvent;

/**
 * Tests for {@link AdoWebhookDebugController} when debug capture is <em>enabled</em>.
 *
 * <p>Verifies that the endpoint is available and returns the expected body + safe metadata, or 404
 * when no event has been captured yet.
 */
@WebMvcTest(AdoWebhookDebugController.class)
@TestPropertySource(properties = "webhook.debug-capture-enabled=true")
class AdoWebhookDebugControllerTest
{

    private static final String ENDPOINT = "/debug/ado-webhook/last-event";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookDebugCaptureService captureService;

    @Test
    void returnsNotFoundWhenNoCaptureExists() throws Exception
    {
        when(captureService.latestCapture()).thenReturn(Optional.empty());

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsLastEventWithSafeMetadataWhenCaptureExists() throws Exception
    {
        var receivedAt = Instant.parse("2026-06-30T12:00:00Z");
        var captured = new CapturedWebhookEvent(
                receivedAt,
                "{\"eventType\":\"workitem.updated\"}",
                "workitem.updated",
                "ProjectA",
                123,
                27);
        when(captureService.latestCapture()).thenReturn(Optional.of(captured));

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedAt").value("2026-06-30T12:00:00Z"))
                .andExpect(jsonPath("$.rawRequestBody").value("{\"eventType\":\"workitem.updated\"}"))
                .andExpect(jsonPath("$.eventType").value("workitem.updated"))
                .andExpect(jsonPath("$.project").value("ProjectA"))
                .andExpect(jsonPath("$.workItemId").value(123))
                .andExpect(jsonPath("$.revision").value(27));
    }

    @Test
    void responseNeverContainsHeadersOrSecrets() throws Exception
    {
        var captured = new CapturedWebhookEvent(
                Instant.now(),
                "{}",
                "workitem.updated",
                "ProjectA",
                1,
                1);
        when(captureService.latestCapture()).thenReturn(Optional.of(captured));

        var response = mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Ensure no secret-like keys appear in the response body
        org.assertj.core.api.Assertions.assertThat(response)
                .doesNotContainIgnoringCase("secret")
                .doesNotContainIgnoringCase("authorization")
                .doesNotContainIgnoringCase("X-ADO")
                .doesNotContainIgnoringCase("password")
                .doesNotContainIgnoringCase("token");
    }

    @Test
    void returns404WhenCaptureIsEmptyOptional() throws Exception
    {
        when(captureService.latestCapture()).thenReturn(Optional.empty());

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isNotFound());
    }
}

