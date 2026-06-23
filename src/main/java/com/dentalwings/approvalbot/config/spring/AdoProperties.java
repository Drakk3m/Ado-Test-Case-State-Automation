package com.dentalwings.approvalbot.config.spring;

import java.util.LinkedHashMap;
import java.util.Map;

public class AdoProperties {

    private String organization;
    private String personalAccessToken;
    private boolean httpClientEnabled;
    private boolean dryRun = true;
    private Map<String, ProjectApprovalProperties> projects = new LinkedHashMap<>();

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getPersonalAccessToken() {
        return personalAccessToken;
    }

    public void setPersonalAccessToken(String personalAccessToken) {
        this.personalAccessToken = personalAccessToken;
    }

    public boolean isHttpClientEnabled() {
        return httpClientEnabled;
    }

    public void setHttpClientEnabled(boolean httpClientEnabled) {
        this.httpClientEnabled = httpClientEnabled;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public Map<String, ProjectApprovalProperties> getProjects() {
        return projects;
    }

    public void setProjects(Map<String, ProjectApprovalProperties> projects) {
        this.projects = projects == null ? new LinkedHashMap<>() : new LinkedHashMap<>(projects);
    }
}
