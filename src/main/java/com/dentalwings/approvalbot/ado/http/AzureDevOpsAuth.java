package com.dentalwings.approvalbot.ado.http;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.dentalwings.approvalbot.config.spring.AdoAuthenticationMode;

public class AzureDevOpsAuth {

    public String authorizationHeader(AdoAuthenticationMode mode, String credential) {
        var effectiveMode = mode == null ? AdoAuthenticationMode.PAT : mode;
        return effectiveMode == AdoAuthenticationMode.BEARER ? bearerAuthHeader(credential)
                : basicAuthHeader(credential);
    }

    public String basicAuthHeader(String personalAccessToken) {
        if (personalAccessToken == null || personalAccessToken.isBlank()) {
            throw new IllegalArgumentException("personalAccessToken must not be blank");
        }
        return "Basic "
                + Base64.getEncoder().encodeToString((":" + personalAccessToken).getBytes(StandardCharsets.UTF_8));
    }

    public String bearerAuthHeader(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new IllegalArgumentException("bearerToken must not be blank");
        }
        return "Bearer " + bearerToken.trim();
    }
}
