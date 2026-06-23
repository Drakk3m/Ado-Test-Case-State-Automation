package com.dentalwings.approvalbot.config.spring;

import java.util.LinkedHashSet;
import java.util.Set;

public class ProjectApprovalProperties {

    private boolean enabled;
    private Set<String> supportedWorkItemTypes = new LinkedHashSet<>();
    private ApprovalFieldsProperties fields = new ApprovalFieldsProperties();
    private ApprovalUsersProperties approvals = new ApprovalUsersProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<String> getSupportedWorkItemTypes() {
        return supportedWorkItemTypes;
    }

    public void setSupportedWorkItemTypes(Set<String> supportedWorkItemTypes) {
        this.supportedWorkItemTypes = supportedWorkItemTypes == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(supportedWorkItemTypes);
    }

    public ApprovalFieldsProperties getFields() {
        return fields;
    }

    public void setFields(ApprovalFieldsProperties fields) {
        this.fields = fields == null ? new ApprovalFieldsProperties() : fields;
    }

    public ApprovalUsersProperties getApprovals() {
        return approvals;
    }

    public void setApprovals(ApprovalUsersProperties approvals) {
        this.approvals = approvals == null ? new ApprovalUsersProperties() : approvals;
    }
}
