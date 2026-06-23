package com.dentalwings.approvalbot.ado.http;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AzureDevOpsAuth {

    public String basicAuthHeader(String personalAccessToken) {
        if (personalAccessToken == null || personalAccessToken.isBlank()) {
            throw new IllegalArgumentException("personalAccessToken must not be blank");
        }
        return "Basic " + Base64.getEncoder()
                .encodeToString((":" + personalAccessToken).getBytes(StandardCharsets.UTF_8));
    }
}
