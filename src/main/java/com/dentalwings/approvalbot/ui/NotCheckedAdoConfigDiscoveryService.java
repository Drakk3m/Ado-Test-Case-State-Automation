package com.dentalwings.approvalbot.ui;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(AdoConfigDiscoveryService.class)
public class NotCheckedAdoConfigDiscoveryService implements AdoConfigDiscoveryService
{

    private static final String TODO_MESSAGE = "Not checked. ADO-backed discovery is intentionally a contract in this draft.";

    @Override
    public ConfigLookupResult<String> listProjects(String organization)
    {
        return ConfigLookupResult.notChecked(TODO_MESSAGE);
    }

    @Override
    public ConfigLookupResult<String> validateProject(String organization, String project)
    {
        return ConfigLookupResult.notChecked(TODO_MESSAGE);
    }

    @Override
    public ConfigLookupResult<String> listWorkItemTypes(String organization, String project)
    {
        return ConfigLookupResult.notChecked(TODO_MESSAGE);
    }

    @Override
    public ConfigLookupResult<String> listFieldReferenceNames(String organization, String project,
            String workItemType)
    {
        return ConfigLookupResult.notChecked(TODO_MESSAGE);
    }

    @Override
    public ConfigLookupResult<String> listObservedStateNames(String organization, String project, String workItemType)
    {
        return ConfigLookupResult.notChecked(TODO_MESSAGE);
    }

    @Override
    public ConfigLookupResult<String> resolveUsers(String organization, List<String> users)
    {
        return ConfigLookupResult.notChecked(TODO_MESSAGE);
    }
}
