package com.dentalwings.approvalbot.identity;

public record IdentityClassification(boolean sme, boolean sqa) {

    public boolean authorizedApprover() {
        return sme || sqa;
    }
}
