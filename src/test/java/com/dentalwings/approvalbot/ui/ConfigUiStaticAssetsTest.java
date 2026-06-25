package com.dentalwings.approvalbot.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConfigUiStaticAssetsTest {

    @Test
    void javascriptUsesAdoDiscoveryEndpointsForSelectorOptions() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("/api/config-ui/discovery/projects")
                .contains("/api/config-ui/discovery/validate-project")
                .contains("/api/config-ui/discovery/work-item-types")
                .contains("/api/config-ui/discovery/fields")
                .contains("/api/config-ui/discovery/states")
                .contains("select data-field=\"fields.approvedBySme\"")
                .contains("select data-field=\"fields.approvedBySqa\"");
    }

    @Test
    void javascriptMakesProjectVerificationTheFirstAdoDiscoveryGate() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("Verify Project")
                .contains("isProjectVerified(discovery, project)")
                .contains("const workItemTypeDisabled = projectVerified && lookupHasOptions(discovery.workItemTypes) ? \"\" : \"disabled\"")
                .contains("const fieldAndStateDisabled = dependentOptionsReady ? \"\" : \"disabled\"")
                .contains("if (isProjectVerified(discovery, project))")
                .contains("Verify the project before selecting a Work Item type");
    }

    @Test
    void javascriptDoesNotEnableEmptySelectorListsSilently() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("lookupHasOptions(lookup)")
                .contains("normalizeOptionsLookup(lookup, emptyMessage)")
                .contains("No Work Item Types were returned for the verified project.")
                .contains("No fields were returned for the selected Work Item Type.")
                .contains("No states were returned for the selected Work Item Type.");
    }

    @Test
    void javascriptIgnoresStaleDiscoveryResponsesAfterParentChanges() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("discoveryRequestSequence")
                .contains("discovery.requestToken")
                .contains("isCurrentDiscoveryRequest(index, requestToken, projectName)")
                .contains("isCurrentDiscoveryRequest(index, requestToken, projectName, type)");
    }

    @Test
    void javascriptBlocksFinalSaveUntilAdoSelectorStateIsCurrent() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("isUiAdoDiscoveryCurrent()")
                .contains("isProjectDiscoveryCurrent(project, discovery)")
                .contains("saveBtn.disabled = !preview?.finalYamlAllowed || !uiAdoDiscoveryCurrent")
                .contains("Verify project and select current ADO-backed values before saving final YAML.");
    }

    @Test
    void javascriptClearsDependentSelectionsWhenParentsChange() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("clearChildSelections(project)")
                .contains("clearTypeSelections(project)")
                .contains("clearDiscovery(index, \"project\")")
                .contains("clearDiscovery(index, \"type\")")
                .contains("project.fields.approvedBySme = \"\"")
                .contains("project.fields.approvedBySqa = \"\"")
                .contains("project.states = { design: \"Design\", inReview: \"In Review\", approved: \"Approved\" }");
    }

    @Test
    void javascriptRefreshesYamlPreviewFromSelectorChanges() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("schedulePreview()")
                .contains("previewDraft(false)")
                .contains("loadFieldAndStateOptions(index)")
                .contains("/api/config-ui/preview");
    }

    private String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
