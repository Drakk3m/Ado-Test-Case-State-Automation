package com.dentalwings.approvalbot.ui;

public interface AdoConfigDiscoveryService {

    ConfigLookupResult<String> listProjects(String organization);

    default ConfigLookupResult<ConfigSelectorOption> listProjectOptions(String organization) {
        return toOptions(listProjects(organization));
    }

    default ConfigLookupResult<String> validateProject(String organization, String project) {
        return listProjects(organization);
    }

    ConfigLookupResult<String> listWorkItemTypes(String organization, String project);

    default ConfigLookupResult<ConfigSelectorOption> listWorkItemTypeOptions(String organization, String project) {
        return toOptions(listWorkItemTypes(organization, project));
    }

    ConfigLookupResult<String> listFieldReferenceNames(String organization, String project, String workItemType);

    default ConfigLookupResult<ConfigSelectorOption> listFieldOptions(String organization, String project, String workItemType) {
        return toOptions(listFieldReferenceNames(organization, project, workItemType));
    }

    ConfigLookupResult<String> listObservedStateNames(String organization, String project, String workItemType);

    default ConfigLookupResult<ConfigSelectorOption> listStateOptions(String organization, String project, String workItemType) {
        return toOptions(listObservedStateNames(organization, project, workItemType));
    }

    ConfigLookupResult<String> resolveUsers(String organization, java.util.List<String> users);

    default ConfigLookupResult<ConfigSelectorOption> searchIdentityOptions(String organization, String query) {
        return ConfigLookupResult.warning("ADO user discovery is not available yet; use email/login values, not display names.");
    }

    private ConfigLookupResult<ConfigSelectorOption> toOptions(ConfigLookupResult<String> lookup) {
        return new ConfigLookupResult<>(
                lookup.status(),
                lookup.message(),
                lookup.values().stream().map(ConfigSelectorOption::ado).toList()
        );
    }
}
