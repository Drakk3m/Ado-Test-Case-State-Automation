package com.dentalwings.approvalbot.ado.http;

import com.dentalwings.approvalbot.ado.AdoWorkItemKey;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class AzureDevOpsUrlBuilder {

    public static final String WORK_ITEM_API_VERSION = "7.1";
    public static final String COMMENTS_API_VERSION = "7.1-preview";

    public String workItemUrl(AdoWorkItemKey key) {
        validateKey(key);
        return "https://dev.azure.com/"
                + encode(key.organization())
                + "/"
                + encode(key.project())
                + "/_apis/wit/workitems/"
                + key.workItemId()
                + "?api-version="
                + WORK_ITEM_API_VERSION;
    }

    public String workItemRevisionUrl(AdoWorkItemKey key, int revision) {
        validateKey(key);
        return "https://dev.azure.com/"
                + encode(key.organization())
                + "/"
                + encode(key.project())
                + "/_apis/wit/workItems/"
                + key.workItemId()
                + "/revisions/"
                + revision
                + "?api-version="
                + WORK_ITEM_API_VERSION;
    }

    public String workItemPatchUrl(AdoWorkItemKey key) {
        return workItemUrl(key);
    }

    public String workItemCommentsUrl(AdoWorkItemKey key) {
        validateKey(key);
        return "https://dev.azure.com/"
                + encode(key.organization())
                + "/"
                + encode(key.project())
                + "/_apis/wit/workItems/"
                + key.workItemId()
                + "/comments?api-version="
                + COMMENTS_API_VERSION;
    }

    private void validateKey(AdoWorkItemKey key) {
        if (key == null) {
            throw new IllegalArgumentException("Azure DevOps Work Item key must not be null.");
        }
        requireComponent("organization", key.organization());
        requireComponent("project", key.project());
    }

    private void requireComponent(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Azure DevOps " + name + " must not be blank.");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
