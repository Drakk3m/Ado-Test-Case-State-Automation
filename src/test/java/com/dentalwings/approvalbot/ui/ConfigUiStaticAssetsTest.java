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
                .contains("/api/config-ui/discovery/users/search")
                .contains("data-field=\"fields.approvedBySme\"")
                .contains("data-field=\"fields.approvedBySqa\"");
    }

    @Test
    void javascriptMakesProjectVerificationTheFirstAdoDiscoveryGate() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("button.verifyProject")
                .contains("isProjectVerified(discovery, project)")
                .contains("const workItemTypeDisabled = projectVerified && lookupHasOptions(discovery.workItemTypes) ? \"\" : \"disabled\"")
                .contains("const fieldAndStateDisabled = dependentOptionsReady ? \"\" : \"disabled\"")
                .contains("if (isProjectVerified(discovery, project))")
                .contains("message.verifyBeforeType");
    }

    @Test
    void javascriptDoesNotEnableEmptySelectorListsSilently() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("lookupHasOptions(lookup)")
                .contains("lookupOptionCount(lookup)")
                .contains("renderedOptionCount(lookup)")
                .contains("normalizeOptionsLookup(lookup, emptyMessage, selectorName = \"selector\", projectConfigId = \"\")")
                .contains("backend-count-without-renderable-options")
                .contains("selector-render-failed")
                .contains("message.noWorkItemTypes")
                .contains("message.noFields")
                .contains("message.noStates");
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
                .contains("selectOptions(selectorName, lookup, selected, placeholder, enabled = false, projectConfigId = \"\")")
                .contains("option.value === selected")
                .contains("optionLabel(option, selectorName)")
                .contains("selectorOptions(projectOptionLookup)")
                .contains("data-field=\"name\" data-selector-name=\"project\"")
                .contains("selectOptions(\"workItemType\", discovery.workItemTypes")
                .contains("selectOptions(\"approvedBySmeField\", fieldLookups.smeLookup")
                .contains("selectOptions(\"approvedState\", discovery.states");
    }

    @Test
    void javascriptFormatsProjectAndFieldOptionLabelsForHumans() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("function optionLabel(option, selectorName = \"\")")
                .contains("[\"approvedBySmeField\", \"approvedBySqaField\", \"reversibleBusinessFields\"].includes(selectorName)")
                .contains("return fieldSelector ? `${option.displayName} - ${option.value}` : option.displayName")
                .doesNotContain("option.description ? ` - ${option.description}`");
    }

    @Test
    void javascriptFiltersFieldSelectorsByPurposeAndKnownSandboxFields() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("function isApprovalFieldOption(option)")
                .contains("value === \"custom.approvertech\"")
                .contains("value === \"custom.approvertest\"")
                .contains("text.includes(\"identity\")")
                .contains("text.includes(\"person\")")
                .contains("function isReversibleBusinessFieldOption(option)")
                .contains("\"system.title\"")
                .contains("\"system.description\"")
                .contains("\"microsoft.vsts.tcm.steps\"")
                .contains("\"microsoft.vsts.tcm.localdatasource\"")
                .contains("function isInternalFieldOption(option)");
    }

    @Test
    void javascriptHidesIncompatibleFieldOptionsAndBlocksDuplicateSelections() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("function filteredFieldLookups(project, fieldsLookup)")
                .contains("smeLookup")
                .contains("sqaLookup")
                .contains("reversibleLookup")
                .contains("function duplicateFieldMessages(project)")
                .contains("message.sameApprovalFields")
                .contains("message.smeFieldAlsoReversible")
                .contains("message.sqaFieldAlsoReversible")
                .contains("cleanFieldConflicts(project, field)")
                .contains("saveBtn.disabled = !preview?.finalYamlAllowed || !uiAdoDiscoveryCurrent");
    }

    @Test
    void javascriptIgnoresStaleDiscoveryResponsesAfterParentChanges() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("discoveryRequestSequence")
                .contains("discovery.requestToken")
                .contains("isCurrentDiscoveryRequest(projectConfigId, requestToken, projectName)")
                .contains("isCurrentDiscoveryRequest(projectConfigId, requestToken, projectName, type)");
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
                .contains(".diagnostic-group")
                .contains(".diagnostic-grid")
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
                .contains("raw fields")
                .contains("approval fields")
                .contains("reversible fields")
                .contains("duplicate errors")
                .contains("query length")
                .contains("user results")
                .contains("pending identity")
                .contains("selected users")
                .contains("unresolved users")
                .contains("identity warnings")
                .contains("stale ignored")
                .contains("lastUpdated")
                .contains("selectorDiagnostics")
                .contains("function diagnosticGroups()")
                .contains("adoDiscoveryRequestCount")
                .contains("projectMetadataCacheHit")
                .contains("processIdCacheHit")
                .contains("workItemTypeOptionsCacheHit")
                .contains("fieldOptionsCacheHit")
                .contains("stateOptionsCacheHit")
                .contains("function adoDiscoveryDiagnostics(lookup)")
                .contains("function diagnosticGroupMarkup(group)")
                .contains("function diagnosticItemMarkup(item)")
                .doesNotContain("class=\"diagnostics-table\"");
    }

    @Test
    void javascriptGuardsStructuralDiscoveryByFreshnessAndInFlightRequest() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("projectValidationCurrentFor")
                .contains("workItemTypesCurrentForProjectId")
                .contains("fieldsCurrentForProjectIdAndWorkItemType")
                .contains("statesCurrentForProjectIdAndWorkItemType")
                .contains("function runStructuralDiscovery(")
                .contains("discovery.inFlight[inFlightKey]")
                .contains("inFlightDedupedCount")
                .contains("skippedBecauseCurrentCount")
                .contains("frontendValidateProjectCallCount")
                .contains("frontendLoadWitCallCount")
                .contains("frontendLoadFieldsCallCount")
                .contains("frontendLoadStatesCallCount")
                .contains("function structuralLookupIsCurrent(")
                .contains("function updateStructuralDiscoveryDiagnostics(")
                .contains("STRUCTURAL_DISCOVERY_TTL_MS")
                .contains("structuralDiscoverySuppressedCount")
                .contains("lastStructuralDiscoveryReason")
                .contains("lastStructuralDiscoveryDependencyKey")
                .contains("function suppressStructuralDiscovery(")
                .contains("validateProject:current")
                .contains("loadWorkItemTypes:current")
                .contains("loadFields:current")
                .contains("loadStates:current")
                .contains("yaml-preview")
                .contains("identity-search");
    }

    @Test
    void pageProvidesLanguageSelectorAndSupportedLanguages() throws Exception {
        var html = read("src/main/resources/templates/index.html");
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(html)
                .contains("id=\"languageSelector\"")
                .contains("<option value=\"en\">English</option>")
                .contains("<option value=\"fr\">Français</option>")
                .contains("<option value=\"es\">Español</option>")
                .contains("data-i18n=\"language.label\"");
        assertThat(javascript)
                .contains("const LANGUAGE_STORAGE_KEY = \"configUiLanguage\"")
                .contains("localStorage.getItem(LANGUAGE_STORAGE_KEY)")
                .contains("localStorage.setItem(LANGUAGE_STORAGE_KEY, currentLanguage)")
                .contains("function setLanguage(language)")
                .contains("function applyStaticTranslations()");
    }

    @Test
    void javascriptDefinesEnglishFrenchAndSpanishTranslations() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("const I18N = {")
                .contains("en: {")
                .contains("fr: {")
                .contains("es: {")
                .contains("\"app.title\": \"Approval Bot configuration\"")
                .contains("\"app.title\": \"Configuration Approval Bot\"")
                .contains("\"app.title\": \"Configuración de Approval Bot\"")
                .contains("\"button.verifyProject\"")
                .contains("\"identity.searchPlaceholder\"")
                .contains("\"status.saveBlocked\"");
    }

    @Test
    void changingLanguageRerendersUiWithoutClearingFormStateOrYamlKeys() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("readFormToState();")
                .contains("applyStaticTranslations();")
                .contains("renderProjects();")
                .contains("renderValidation(lastPreview);")
                .contains("state.ado.organization = document.getElementById(\"adoOrganization\").value.trim()")
                .contains("yamlOutputEl.textContent = payload.yaml || \"\"")
                .contains("states: { design: \"Design\", inReview: \"In Review\", approved: \"Approved\" }");
    }

    @Test
    void javascriptSupportsCollapsibleProjectSections() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("let projectLayoutState = new Map()")
                .contains("function projectLayout(projectConfigId)")
                .contains("function projectSummary(project, index, selectedType, status)")
                .contains("project.title")
                .contains("data-action=\"toggle-project\"")
                .contains("data-action=\"collapse-project\"")
                .contains("project-card${collapsed ? \" collapsed\" : \"\"}")
                .contains("projectLayout(projectConfigId).collapsed = true")
                .contains("projectLayout(projectConfigId).collapsed = false");
    }

    @Test
    void javascriptOnlyCollapsesValidatedProjectSections() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("function projectCanCollapse(project, discovery, fieldDuplicateMessages, identityMessages)")
                .contains("isProjectDiscoveryCurrent(project, discovery)")
                .contains("const collapseDisabled = canCollapse ? \"\" : \"disabled\"")
                .contains("status.resolveBeforeCollapse")
                .contains("projectLayout(projectConfigId).collapsed = false;")
                .contains("projectLayoutState.delete(projectConfigId)");
    }

    @Test
    void cssStylesProjectSectionsAndResponsiveDiagnosticsWithoutWideTable() throws Exception {
        var css = read("src/main/resources/static/css/nova-lite.css");

        assertThat(css)
                .contains(".project-card.collapsed")
                .contains(".project-card-header")
                .contains(".project-summary")
                .contains(".project-card-body")
                .contains(".project-collapsed-body")
                .contains(".diagnostics-groups")
                .contains(".diagnostic-group")
                .contains(".diagnostic-grid")
                .doesNotContain(".diagnostics-table");
    }

    @Test
    void javascriptUsesAdoBackedIdentitySearchAndChipsForUsers() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("function identityUserPicker(project, projectConfigId, role, enabled)")
                .contains("data-action=\"identity-search\"")
                .contains("data-action=\"select-pending-user\"")
                .contains("data-action=\"add-pending-user\"")
                .contains("data-action=\"remove-user\"")
                .contains("function loadIdentityOptions(projectConfigId, role, query, requestVersion)")
                .contains("function updateIdentityPicker(projectConfigId, role)")
                .contains("function pendingIdentityPreview(project, role, pending)")
                .contains("identity.typeToSearch")
                .contains("identity.selectionNote")
                .contains("addPendingIdentity(projectConfigId, role)")
                .contains("removeUserFromRole(project, role, button.getAttribute(\"data-user-value\"))")
                .doesNotContain("textarea data-field=\"approvals.smeUsers\"")
                .doesNotContain("textarea data-field=\"approvals.sqaUsers\"");
    }

    @Test
    void javascriptIdentitySelectionStoresNormalizedValuesAndShowsCrossRoleWarnings() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("function normalizedIdentity(value)")
                .contains("setRoleUsers(project, role, [...users, normalized])")
                .contains("function setPendingIdentity(projectConfigId, role, value)")
                .contains("function duplicateIdentityMessages(project)")
                .contains("message.crossRoleIdentity")
                .contains("unresolvedIdentityCount(project, role)")
                .contains("lookupOptionCount(stateForRole.lookup)")
                .contains("lastQueryLength: stateForRole.query.length")
                .contains("pendingIdentityStatus")
                .contains("selectedCount")
                .contains("unresolvedCount");
    }

    @Test
    void javascriptKeepsIdentitySearchInputStableWhileTyping() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("handleIdentitySearchInput(projectConfigId, role, query)")
                .contains("updateIdentityPicker(projectConfigId, role)")
                .contains("clearTimeout(identitySearchTimers[identityKey(projectConfigId, role)])")
                .contains("const IDENTITY_MIN_QUERY_LENGTH = 3")
                .contains("const IDENTITY_SEARCH_DEBOUNCE_MS = 450")
                .contains("requestVersion")
                .contains("input.focus()")
                .doesNotContain("if (normalizedQuery.length < IDENTITY_MIN_QUERY_LENGTH) {\n        renderProjects();");
    }

    @Test
    void javascriptClientFiltersIdentityResultsByDisplayNameOrEmail() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("function identityContainsQuery(option, query)")
                .contains("option.displayName || \"\"")
                .contains("option.value || \"\"")
                .contains("option.description || \"\"")
                .contains("identityOptionsForSearch(searchState)")
                .contains("function findIdentitySearchCache(projectConfigId, role, organization, project, query)")
                .contains("identitySearchCacheKey(projectConfigId, role, organization, project, query)")
                .contains("normalizedQuery.startsWith(entry.query)")
                .contains("IDENTITY_CACHE_TTL_MS")
                .contains("IDENTITY_CACHE_MAX_ENTRIES")
                .contains("cached.options.length >= IDENTITY_CACHE_USEFUL_RESULT_COUNT")
                .contains("graphFallbackAttempted")
                .contains("graphNegativeCacheHit")
                .contains("projectPoolMatchCount")
                .contains("avatarAdoRequestCount")
                .contains("frontendRequestCount");
    }

    @Test
    void javascriptRendersAdoAvatarsWithInitialsFallback() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("option.avatarUrl")
                .contains("identityAvatarMarkup(option)")
                .contains("wireIdentityAvatarFallbacks")
                .contains("data-identity-avatar")
                .contains("identity-avatar-fallback")
                .contains("image.nextElementSibling")
                .contains("fallback.hidden = false")
                .doesNotContain("Authorization: Bearer");
    }

    @Test
    void cssStylesIdentityPickerResultsAndChips() throws Exception {
        var css = read("src/main/resources/static/css/nova-lite.css");

        assertThat(css)
                .contains(".identity-picker")
                .contains(".identity-chip-list")
                .contains(".identity-chip")
                .contains(".identity-results")
                .contains(".identity-result")
                .contains(".identity-pending")
                .contains(".identity-add")
                .contains(".identity-avatar");
    }

    @Test
    void javascriptRendersProjectOptionsWithRealSelectorDiagnostics() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("selector: \"project\"")
                .contains("data-selector-name=\"project\"")
                .contains("message.projectSelectorRendered")
                .contains("renderDiscoveredProjectsDebug(options)")
                .contains("diagnostics.discoveredProjectsNote")
                .contains("diagnostics.discoveredProjects")
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
                .contains("status.saveBlocked");
    }

    @Test
    void javascriptClearsDependentSelectionsWhenParentsChange() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("clearChildSelections(project)")
                .contains("clearTypeSelections(project)")
                .contains("clearDiscovery(projectConfigId, \"project\")")
                .contains("clearDiscovery(projectConfigId, \"type\")")
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
                .contains("scheduleLocalPreview(")
                .contains("updateYamlPreviewLocalOnly(false, trigger)")
                .contains("runExplicitFieldAndStateDiscovery(projectConfigId)")
                .contains("/api/config-ui/preview");
    }

    @Test
    void normalEditingUsesLocalValidationWithoutStrictAdoCalls() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");
        var workItemTypeHandler = section(
                javascript,
                "if (field === \"supportedWorkItemTypes.0\")",
                "if (field === \"fields.reversibleBusinessFields\")"
        );
        var renderProjects = section(javascript, "function renderProjects()", "function handleProjectInput(");
        var diagnostics = section(javascript, "function renderDiagnosticsPanel()", "function diagnosticGroups()");

        assertThat(javascript)
                .contains("function scheduleLocalPreview(trigger = \"edit\", countStrictSkip = true)")
                .contains("function updateYamlPreviewLocalOnly(showStatus = true, trigger = \"manual-preview\")")
                .contains("strictValidationSkippedDuringEditCount")
                .doesNotContain("card.addEventListener(\"input\"");
        assertThat(workItemTypeHandler)
                .contains("scheduleLocalPreview(\"work-item-type-change\")")
                .doesNotContain("runExplicitFieldAndStateDiscovery")
                .doesNotContain("/api/config-ui/validate");
        assertThat(renderProjects).doesNotContain("/api/config-ui/validate", "postConfig(");
        assertThat(diagnostics).doesNotContain("/api/config-ui/validate", "postConfig(", "discover(");
    }

    @Test
    void strictValidationAndStructuralDiscoveryRequireExplicitButtons() throws Exception {
        var html = read("src/main/resources/templates/index.html");
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(html).contains("id=\"validateConfigBtn\"");
        assertThat(javascript)
                .contains("data-action=\"load-project\"")
                .contains("data-action=\"load-fields-states\"")
                .contains("loadFieldsStatesButton.addEventListener(\"click\"")
                .contains("function runExplicitProjectVerification(projectConfigId)")
                .contains("function runExplicitFieldAndStateDiscovery(projectConfigId)")
                .contains("function runStrictAdoValidation(trigger)")
                .contains("function saveWithStrictAdoValidation()")
                .contains("document.getElementById(\"validateConfigBtn\").addEventListener(\"click\"")
                .contains("runStrictAdoValidation(\"validate-generated-config\")")
                .contains("recordStrictValidation(\"save\")")
                .containsOnlyOnce("/api/config-ui/validate");
    }

    @Test
    void validationBoundaryDiagnosticsExposeSafeTriggerCounters() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("localValidationRunCount")
                .contains("strictValidationRunCount")
                .contains("strictValidationSkippedDuringEditCount")
                .contains("yamlPreviewLocalOnlyCount")
                .contains("backendStrictValidationCallCount")
                .contains("lastStrictValidationTrigger")
                .contains("lastStrictValidationAt")
                .contains("function recordLocalValidation(trigger)")
                .contains("function recordStrictValidation(trigger)");
    }

    @Test
    void javascriptIsolatesEveryProjectCardByStableLocalId() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("function ensureProjectConfigId(project)")
                .contains("Object.defineProperty(project, \"projectConfigId\"")
                .contains("enumerable: false")
                .contains("let projectDiscovery = new Map()")
                .contains("let projectLayoutState = new Map()")
                .contains("projectDiscovery.get(projectConfigId)")
                .contains("projectByConfigId(projectConfigId)")
                .contains("card.dataset.projectConfigId = projectConfigId")
                .contains("removeProjectState(projectConfigId)")
                .doesNotContain("projectDiscovery[index]")
                .doesNotContain("data-index=\"");
    }

    @Test
    void javascriptCreatesFreshNestedProjectCollections() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("function createProjectModel()")
                .contains("supportedWorkItemTypes: []")
                .contains("reversibleBusinessFields: []")
                .contains("approvals: { smeUsers: [], sqaUsers: [] }")
                .contains("project.supportedWorkItemTypes = [...(project.supportedWorkItemTypes || [])]")
                .contains("reversibleBusinessFields: [...(project.fields?.reversibleBusinessFields || [])]")
                .contains("smeUsers: [...(project.approvals?.smeUsers || [])]")
                .contains("sqaUsers: [...(project.approvals?.sqaUsers || [])]");
    }

    @Test
    void javascriptScopesControlsIdentityStateAndDiagnosticsToProjectId() throws Exception {
        var javascript = read("src/main/resources/static/js/config-ui.js");

        assertThat(javascript)
                .contains("function projectControlId(projectConfigId, controlName)")
                .contains("data-project-config-id=\"${projectConfigId}\"")
                .contains("identityKey(projectConfigId, role)")
                .contains("diagnosticKey(selectorName, projectConfigId)")
                .contains("projectConfigId,")
                .contains("updateSelectorDiagnostics(`${role}Users`, {")
                .contains("}, projectConfigId);");
    }

    private String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private String section(String text, String startMarker, String endMarker) {
        var start = text.indexOf(startMarker);
        var end = text.indexOf(endMarker, start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        return text.substring(start, end);
    }
}
