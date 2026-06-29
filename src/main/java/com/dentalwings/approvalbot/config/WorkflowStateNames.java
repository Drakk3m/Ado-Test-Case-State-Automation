package com.dentalwings.approvalbot.config;

public record WorkflowStateNames(String design, String inReview, String approved)
{

    public static final String DEFAULT_DESIGN = "Design";
    public static final String DEFAULT_IN_REVIEW = "In Review";
    public static final String DEFAULT_APPROVED = "Approved";

    public static WorkflowStateNames defaults()
    {
        return new WorkflowStateNames(DEFAULT_DESIGN, DEFAULT_IN_REVIEW, DEFAULT_APPROVED);
    }
}
