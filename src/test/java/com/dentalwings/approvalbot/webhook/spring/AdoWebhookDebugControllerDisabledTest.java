package com.dentalwings.approvalbot.webhook.spring;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that {@link AdoWebhookDebugController} is <em>not</em> registered when debug capture is
 * disabled.
 *
 * <p>Verifies that the endpoint is not accessible and the controller bean does not exist in the
 * application context when {@code webhook.debug-capture-enabled} is absent or {@code false}.
 */
@WebMvcTest(AdoWebhookDebugController.class)
@TestPropertySource(properties = "webhook.debug-capture-enabled=false")
class AdoWebhookDebugControllerDisabledTest
{

    private static final String ENDPOINT = "/debug/ado-webhook/last-event";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void endpointReturnsNotFoundWhenDebugCaptureIsDisabled() throws Exception
    {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isNotFound());
    }

    @Test
    void debugControllerBeanIsNotRegisteredWhenDisabled()
    {
        assertThat(applicationContext.getBeanNamesForType(AdoWebhookDebugController.class))
                .isEmpty();
    }
}

