package com.dentalwings.approvalbot.config.spring;

import java.util.LinkedHashSet;
import java.util.Set;

public class ApprovalFieldsProperties {

    private String approvedBySme;
    private String approvedBySqa;
    private Set<String> reversibleBusinessFields = new LinkedHashSet<>();

    public String getApprovedBySme() {
        return approvedBySme;
    }

    public void setApprovedBySme(String approvedBySme) {
        this.approvedBySme = approvedBySme;
    }

    public String getApprovedBySqa() {
        return approvedBySqa;
    }

    public void setApprovedBySqa(String approvedBySqa) {
        this.approvedBySqa = approvedBySqa;
    }

    public Set<String> getReversibleBusinessFields() {
        return reversibleBusinessFields;
    }

    public void setReversibleBusinessFields(Set<String> reversibleBusinessFields) {
        this.reversibleBusinessFields = reversibleBusinessFields == null ? new LinkedHashSet<>()
                : new LinkedHashSet<>(reversibleBusinessFields);
    }
}
