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

    public String projectUrl(String organization, String project) {
        requireComponent("organization", organization);
        requireComponent("project", project);
        return "https://dev.azure.com/"
                + encode(organization)
                + "/_apis/projects/"
                + encode(project)
                + "?api-version="
                + WORK_ITEM_API_VERSION;
    }

    public String projectsUrl(String organization) {
        requireComponent("organization", organization);
        return "https://dev.azure.com/"
                + encode(organization)
                + "/_apis/projects?api-version="
                + WORK_ITEM_API_VERSION;
    }

    public String workItemTypesUrl(String organization, String project) {
        requireComponent("organization", organization);
        requireComponent("project", project);
        return "https://dev.azure.com/"
                + encode(organization)
                + "/"
                + encode(project)
                + "/_apis/wit/workitemtypes?$expand=None&api-version="
                + WORK_ITEM_API_VERSION;
    }

    public String workItemTypeFieldsUrl(String organization, String project, String workItemType) {
        requireComponent("organization", organization);
        requireComponent("project", project);
        requireComponent("work item type", workItemType);
        return "https://dev.azure.com/"
                + encode(organization)
                + "/"
                + encode(project)
                + "/_apis/wit/workitemtypes/"
                + encode(workItemType)
                + "/fields?api-version="
                + WORK_ITEM_API_VERSION;
    }

    public String workItemTypeStatesUrl(String organization, String project, String workItemType) {
        requireComponent("organization", organization);
        requireComponent("project", project);
        requireComponent("work item type", workItemType);
        return "https://dev.azure.com/"
                + encode(organization)
                + "/"
                + encode(project)
                + "/_apis/wit/workitemtypes/"
                + encode(workItemType)
                + "/states?api-version="
                + WORK_ITEM_API_VERSION;
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
