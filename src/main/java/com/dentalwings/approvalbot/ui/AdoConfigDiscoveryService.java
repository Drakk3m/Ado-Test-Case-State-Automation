package com.dentalwings.approvalbot.ui;

public interface AdoConfigDiscoveryService {

    ConfigLookupResult<String> listProjects(String organization);

    ConfigLookupResult<String> listWorkItemTypes(String organization, String project);

    ConfigLookupResult<String> listFieldReferenceNames(String organization, String project, String workItemType);

    ConfigLookupResult<String> listObservedStateNames(String organization, String project, String workItemType);

    ConfigLookupResult<String> resolveUsers(String organization, java.util.List<String> users);
}
