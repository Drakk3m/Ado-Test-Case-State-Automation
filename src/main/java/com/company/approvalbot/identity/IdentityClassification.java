package com.company.approvalbot.identity;

public record IdentityClassification(boolean sme, boolean sqa) {

    public boolean authorizedApprover() {
        return sme || sqa;
    }
}
