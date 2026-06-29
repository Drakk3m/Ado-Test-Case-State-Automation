package com.dentalwings.approvalbot.config.spring;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.dentalwings.approvalbot.ado.AdoWorkItemKey;
import com.dentalwings.approvalbot.ado.RuntimeAdoCredentialService;
import com.dentalwings.approvalbot.ado.http.AdoClientNonRetryableException;

class MissingPatAdoClientTest
{

    @Test
    void missingPatRejectsAdoCallAsNotConfiguredBeforeCreatingHttpDelegate()
    {
        var properties = new ApprovalBotProperties();
        properties.getAdo().setHttpClientEnabled(true);
        var client = new MissingPatAdoClient(properties.getAdo(), new RuntimeAdoCredentialService(properties));

        assertThatThrownBy(() -> client.fetchWorkItem(new AdoWorkItemKey("org", "project", 123)))
                .isInstanceOf(AdoClientNonRetryableException.class)
                .hasMessageContaining("NOT_CONFIGURED")
                .hasMessageContaining("ADO_PERSONAL_ACCESS_TOKEN")
                .hasMessageNotContaining("Authorization");
    }
}
