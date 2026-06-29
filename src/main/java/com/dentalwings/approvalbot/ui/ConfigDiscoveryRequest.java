package com.dentalwings.approvalbot.ui;

public record ConfigDiscoveryRequest(String organization, String project, String workItemType, String query)
{
}
