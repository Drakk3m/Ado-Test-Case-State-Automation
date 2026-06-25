package com.dentalwings.approvalbot.config.spring;

import com.dentalwings.approvalbot.config.WorkflowStateNames;

public class WorkflowStateProperties {

    private String design = WorkflowStateNames.DEFAULT_DESIGN;
    private String inReview = WorkflowStateNames.DEFAULT_IN_REVIEW;
    private String approved = WorkflowStateNames.DEFAULT_APPROVED;

    public String getDesign() {
        return design;
    }

    public void setDesign(String design) {
        this.design = design;
    }

    public String getInReview() {
        return inReview;
    }

    public void setInReview(String inReview) {
        this.inReview = inReview;
    }

    public String getApproved() {
        return approved;
    }

    public void setApproved(String approved) {
        this.approved = approved;
    }
}
