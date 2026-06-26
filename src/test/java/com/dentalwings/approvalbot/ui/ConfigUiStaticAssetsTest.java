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
                .contains("lookupOptionCount(lookup)")
                .contains("renderedOptionCount(lookup)")
                .contains("normalizeOptionsLookup(lookup, emptyMessage, selectorName = \"selector\")")
                .contains("backend-count-without-renderable-options")
                .contains("selector-render-failed")
                .contains("No Work Item Types were returned for the verified project.")
                .contains("No fields were returned for the selected Work Item Type.")
                .contains("No states were returned for the selected Work Item Type.");
    }

    @Test
    void javascriptNormalizesBackendSelectorResponseShapesBeforeRendering() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("rawOptionItems(lookup)")
                .contains("Array.isArray(lookup?.values)")
                .contains("Array.isArray(lookup?.options)")
                .contains("Array.isArray(lookup?.items)")
                .contains("normalizeSelectorOption(item)")
                .contains("item.value ?? item.referenceName ?? item.name")
                .contains("item.displayName ?? item.name ?? value")
                .contains("selectorOptions(lookup)");
    }

    @Test
    void javascriptRendersSelectorsFromValueAndDisplayName() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("selectOptions(selectorName, lookup, selected, placeholder, enabled = false)")
                .contains("option.value === selected")
                .contains("optionLabel(option) + description")
                .contains("selectorOptions(projectOptionLookup)")
                .contains("select data-field=\"name\" data-selector-name=\"project\"")
                .contains("selectOptions(\"workItemType\", discovery.workItemTypes")
                .contains("selectOptions(\"approvedBySmeField\", discovery.fields")
                .contains("selectOptions(\"approvedState\", discovery.states");
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
    void javascriptDiagnosticsAreOptInAndAvoidSecretKeys() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("isConfigUiDebugEnabled()")
                .contains("debugConfigUi")
                .contains("localStorage.getItem(\"configUiDebug\") === \"true\"")
                .contains("console.debug(\"[config-ui-discovery]\"")
                .contains("console.error(\"[config-ui-discovery]\"")
                .contains("\"authorization\", \"pat\", \"sharedSecret\", \"secret\", \"yaml\", \"generatedYaml\"");
    }

    @Test
    void javascriptDiscoveryDiagnosticsRecordRequestsAndFailures() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("request-started")
                .contains("request-completed")
                .contains("request-failed")
                .contains("discovery-response-received")
                .contains("selector-populated")
                .contains("selector-rendered")
                .contains("selector-state")
                .contains("dependent-selectors-cleared")
                .contains("verify-project-clicked")
                .contains("backendOptionCount")
                .contains("renderedOptionCount");
    }

    @Test
    void pageDefinesVisibleDiagnosticsPanelHiddenByDefault() throws Exception {
        var html = read("src/main/resources/templates/index.html");
        var css = read("src/main/resources/static/css/nova-lite.css");

        assertThat(html)
                .contains("id=\"configUiDiagnosticsPanel\"")
                .contains("class=\"card diagnostics-panel\" hidden")
                .contains("id=\"configUiDiagnosticsContent\"")
                .contains("id=\"discoveredProjectsDebug\"")
                .contains("real selector controls")
                .doesNotContain("<datalist");
        assertThat(css)
                .contains(".diagnostics-panel")
                .contains(".diagnostics-table")
                .contains(".debug-project-list");
    }

    @Test
    void javascriptShowsDiagnosticsPanelWhenDebugIsEnabled() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("diagnosticsPanelEl.hidden = !debugEnabled")
                .contains("discoveredProjectsDebugEl.hidden = !debugEnabled")
                .contains("debugConfigUi")
                .contains("localStorage.getItem(\"configUiDebug\") === \"true\"")
                .contains("renderDiagnosticsPanel()");
    }

    @Test
    void javascriptDiagnosticsPanelShowsSelectorHydrationCounts() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("backend optionCount")
                .contains("received length")
                .contains("normalized length")
                .contains("rendered count")
                .contains("DOM options")
                .contains("stale ignored")
                .contains("lastUpdated")
                .contains("selectorDiagnostics");
    }

    @Test
    void javascriptRendersProjectOptionsWithRealSelectorDiagnostics() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("selector: \"project\"")
                .contains("data-selector-name=\"project\"")
                .contains("Project selector renders all discovered project options.")
                .contains("renderDiscoveredProjectsDebug(options)")
                .contains("These are the same discovered project options rendered by the Project selector.")
                .contains("Discovered projects")
                .doesNotContain("datalist")
                .doesNotContain("browser dropdown may show fewer");
    }

    @Test
    void pageDoesNotUseBrowserDatalistForRequiredSelectors() throws Exception {
        var html = read("src/main/resources/templates/index.html");
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(html).doesNotContain("<datalist", "list=\"adoProjectOptions\"");
        assertThat(javascript)
                .doesNotContain("projectDatalist")
                .doesNotContain("renderProjectDatalist")
                .doesNotContain("adoProjectOptions");
    }

    @Test
    void javascriptTracksSelectorNamesRequiredForSandboxDiagnostics() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("selector: \"project\"")
                .contains("workItemType")
                .contains("approvedBySmeField")
                .contains("approvedBySqaField")
                .contains("designState")
                .contains("inReviewState")
                .contains("approvedState")
                .contains("reversibleBusinessFields");
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
                .contains("clearStaleProjectSelections()")
                .contains("!lookupContainsValue(projectOptionLookup, project.name)")
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
