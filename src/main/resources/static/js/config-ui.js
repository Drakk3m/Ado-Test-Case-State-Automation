const statusEl = document.getElementById("status");
const yamlOutputEl = document.getElementById("yamlOutput");
const projectsEl = document.getElementById("projects");
const validationSummaryEl = document.getElementById("validationSummary");
const saveBtn = document.getElementById("saveBtn");
const diagnosticsPanelEl = document.getElementById("configUiDiagnosticsPanel");
const diagnosticsContentEl = document.getElementById("configUiDiagnosticsContent");
const discoveredProjectsDebugEl = document.getElementById("discoveredProjectsDebug");

let state = { ado: { projects: [] } };
let lastPreview = null;
let previewTimer = null;
let projectOptionLookup = { status: "NOT_CHECKED", message: "", values: [] };
let projectDiscovery = [];
let discoveryRequestSequence = 0;
let selectorDiagnostics = {};
let identitySearchState = {};
let identitySearchTimers = {};

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");
}

function splitLines(value) {
    return value
        .split(/\r?\n|,/)
        .map((it) => it.trim())
        .filter((it) => it.length > 0);
}

function setStatus(text, isError) {
    statusEl.textContent = text || "";
    statusEl.classList.toggle("error", !!isError);
}

function isConfigUiDebugEnabled() {
    return new URLSearchParams(window.location.search).get("debugConfigUi") === "true"
        || localStorage.getItem("configUiDebug") === "true";
}

function debugDiscovery(event, details = {}) {
    if (!isConfigUiDebugEnabled()) {
        return;
    }
    console.debug("[config-ui-discovery]", event, safeDiscoveryDetails(details));
}

function errorDiscovery(event, details = {}) {
    console.error("[config-ui-discovery]", event, safeDiscoveryDetails(details));
}

function safeDiscoveryDetails(details) {
    const safe = {};
    for (const [key, value] of Object.entries(details || {})) {
        if (["authorization", "pat", "sharedSecret", "secret", "yaml", "generatedYaml"].includes(key)) {
            continue;
        }
        safe[key] = value;
    }
    return safe;
}

function nowTimestamp() {
    return new Date().toLocaleTimeString();
}

function sanitizeMessage(message) {
    return String(message ?? "")
        .replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g, " ")
        .replace(/\s+/g, " ")
        .trim()
        .slice(0, 240);
}

function ensureSelectorDiagnostic(selectorName) {
    if (!selectorDiagnostics[selectorName]) {
        selectorDiagnostics[selectorName] = {
            selector: selectorName,
            status: "NOT_CHECKED",
            backendOptionCount: 0,
            receivedLength: 0,
            normalizedLength: 0,
            renderedOptionCount: 0,
            domOptionCount: "",
            rawFieldCount: "",
            approvalFieldCount: "",
            reversibleFieldCount: "",
            duplicateErrors: "",
            lastQueryLength: "",
            resultCount: "",
            selectedCount: "",
            unresolvedCount: "",
            identityWarnings: "",
            enabled: false,
            message: "",
            staleIgnoredCount: 0,
            lastUpdated: ""
        };
    }
    return selectorDiagnostics[selectorName];
}

function updateSelectorDiagnostics(selectorName, updates = {}) {
    const current = ensureSelectorDiagnostic(selectorName);
    selectorDiagnostics[selectorName] = {
        ...current,
        ...safeDiscoveryDetails(updates),
        lastUpdated: nowTimestamp()
    };
    renderDiagnosticsPanel();
}

function incrementStaleIgnored(selectorName, reason) {
    const current = ensureSelectorDiagnostic(selectorName);
    updateSelectorDiagnostics(selectorName, {
        staleIgnoredCount: current.staleIgnoredCount + 1,
        message: reason ? `Stale response ignored: ${reason}.` : "Stale response ignored."
    });
}

function renderDiagnosticsPanel() {
    const debugEnabled = isConfigUiDebugEnabled();
    if (diagnosticsPanelEl) {
        diagnosticsPanelEl.hidden = !debugEnabled;
    }
    if (discoveredProjectsDebugEl) {
        discoveredProjectsDebugEl.hidden = !debugEnabled;
    }
    if (!debugEnabled || !diagnosticsContentEl) {
        return;
    }
    const rows = Object.values(selectorDiagnostics)
        .sort((left, right) => left.selector.localeCompare(right.selector))
        .map((item) => `
            <tr>
                <td>${escapeHtml(item.selector)}</td>
                <td>${escapeHtml(item.status)}</td>
                <td>${escapeHtml(item.backendOptionCount)}</td>
                <td>${escapeHtml(item.receivedLength)}</td>
                <td>${escapeHtml(item.normalizedLength)}</td>
                <td>${escapeHtml(item.renderedOptionCount)}</td>
                <td>${escapeHtml(item.domOptionCount)}</td>
                <td>${escapeHtml(item.rawFieldCount)}</td>
                <td>${escapeHtml(item.approvalFieldCount)}</td>
                <td>${escapeHtml(item.reversibleFieldCount)}</td>
                <td>${escapeHtml(item.duplicateErrors)}</td>
                <td>${escapeHtml(item.lastQueryLength)}</td>
                <td>${escapeHtml(item.resultCount)}</td>
                <td>${escapeHtml(item.selectedCount)}</td>
                <td>${escapeHtml(item.unresolvedCount)}</td>
                <td>${escapeHtml(item.identityWarnings)}</td>
                <td>${item.enabled ? "enabled" : "disabled"}</td>
                <td>${escapeHtml(item.staleIgnoredCount)}</td>
                <td>${escapeHtml(item.lastUpdated)}</td>
                <td>${escapeHtml(item.message)}</td>
            </tr>
        `).join("");
    diagnosticsContentEl.innerHTML = `
        <table class="diagnostics-table">
            <thead>
                <tr>
                    <th>selector</th>
                    <th>status</th>
                    <th>backend optionCount</th>
                    <th>received length</th>
                    <th>normalized length</th>
                    <th>rendered count</th>
                    <th>DOM options</th>
                    <th>raw fields</th>
                    <th>approval fields</th>
                    <th>reversible fields</th>
                    <th>duplicate errors</th>
                    <th>query length</th>
                    <th>user results</th>
                    <th>selected users</th>
                    <th>unresolved users</th>
                    <th>identity warnings</th>
                    <th>decision</th>
                    <th>stale ignored</th>
                    <th>updated</th>
                    <th>message</th>
                </tr>
            </thead>
            <tbody>${rows || `<tr><td colspan="20">No selector diagnostics captured yet.</td></tr>`}</tbody>
        </table>
    `;
}

function lookupOptionCount(lookup) {
    return lookup?.optionCount ?? rawOptionItems(lookup).length;
}

function rawOptionItems(lookup) {
    if (Array.isArray(lookup)) {
        return lookup;
    }
    if (Array.isArray(lookup?.values)) {
        return lookup.values;
    }
    if (Array.isArray(lookup?.options)) {
        return lookup.options;
    }
    if (Array.isArray(lookup?.items)) {
        return lookup.items;
    }
    return [];
}

function normalizeSelectorOption(item) {
    if (item == null) {
        return null;
    }
    if (typeof item === "string") {
        const value = item.trim();
        return value ? { value, displayName: value, description: "", source: "" } : null;
    }
    const value = String(item.value ?? item.referenceName ?? item.name ?? "").trim();
    if (!value) {
        return null;
    }
    const displayName = String(item.displayName ?? item.name ?? value).trim() || value;
    return {
        value,
        displayName,
        description: String(item.description ?? item.type ?? "").trim(),
        source: String(item.source ?? "").trim(),
        referenceName: String(item.referenceName ?? value).trim()
    };
}

function selectorOptions(lookup) {
    return rawOptionItems(lookup)
        .map(normalizeSelectorOption)
        .filter((option) => option && option.value);
}

const ALWAYS_REVERSIBLE_FIELDS = new Set([
    "system.title",
    "system.description",
    "microsoft.vsts.tcm.steps",
    "microsoft.vsts.tcm.localdatasource"
]);

const INTERNAL_FIELD_PARTS = [
    ".id",
    ".rev",
    ".revised",
    ".changed",
    ".created",
    ".authorized",
    ".watermark",
    ".external",
    ".node",
    ".area",
    ".iteration",
    ".reason",
    ".state",
    ".workitemtype",
    ".assignedto",
    ".tags",
    ".history",
    ".board",
    ".stackrank",
    ".priority",
    ".severity"
];

function normalizedText(value) {
    return String(value || "").trim().toLowerCase();
}

function optionText(option) {
    return normalizedText(`${option?.value || ""} ${option?.displayName || ""} ${option?.description || ""}`);
}

function isInternalFieldOption(option) {
    const value = normalizedText(option?.value);
    const text = optionText(option);
    return value.startsWith("system.") && INTERNAL_FIELD_PARTS.some((part) => value.includes(part))
        || text.includes("readonly")
        || text.includes("read only")
        || text.includes("audit")
        || text.includes("watermark");
}

function isApprovalFieldOption(option) {
    const value = normalizedText(option?.value);
    const text = optionText(option);
    if (isInternalFieldOption(option)) {
        return false;
    }
    return value === "custom.approvertech"
        || value === "custom.approvertest"
        || text.includes("approver")
        || text.includes("approval")
        || text.includes("reviewer")
        || text.includes("sme")
        || text.includes("sqa")
        || text.includes("identity")
        || text.includes("person");
}

function isReversibleBusinessFieldOption(option) {
    const value = normalizedText(option?.value);
    if (ALWAYS_REVERSIBLE_FIELDS.has(value)) {
        return true;
    }
    if (isInternalFieldOption(option) || isApprovalFieldOption(option)) {
        return false;
    }
    return value.startsWith("custom.")
        || value === "system.description"
        || value === "system.title"
        || value.startsWith("microsoft.vsts.tcm.");
}

function uniqueOptions(options) {
    const seen = new Set();
    return options.filter((option) => {
        const key = normalizedText(option.value);
        if (!key || seen.has(key)) {
            return false;
        }
        seen.add(key);
        return true;
    });
}

function lookupWithOptions(lookup, options) {
    return { ...(lookup || {}), values: options, optionCount: options.length };
}

function filteredFieldLookups(project, fieldsLookup) {
    const rawOptions = selectorOptions(fieldsLookup);
    const sme = normalizedText(project.fields.approvedBySme);
    const sqa = normalizedText(project.fields.approvedBySqa);
    const reversible = new Set((project.fields.reversibleBusinessFields || []).map(normalizedText));
    const duplicateErrors = duplicateFieldMessages(project).length;
    const approvalOptions = uniqueOptions(rawOptions.filter(isApprovalFieldOption));
    const reversibleOptions = uniqueOptions(rawOptions.filter(isReversibleBusinessFieldOption));
    const smeOptions = approvalOptions.filter((option) => {
        const value = normalizedText(option.value);
        return value === sme || (value !== sqa && !reversible.has(value));
    });
    const sqaOptions = approvalOptions.filter((option) => {
        const value = normalizedText(option.value);
        return value === sqa || (value !== sme && !reversible.has(value));
    });
    const reversibleFiltered = reversibleOptions.filter((option) => {
        const value = normalizedText(option.value);
        return value !== sme && value !== sqa;
    });

    return {
        rawFieldCount: rawOptions.length,
        approvalFieldCount: approvalOptions.length,
        reversibleFieldCount: reversibleFiltered.length,
        approvalOptions,
        reversibleOptions: reversibleFiltered,
        smeLookup: {
            ...lookupWithOptions(fieldsLookup, smeOptions),
            rawFieldCount: rawOptions.length,
            approvalFieldCount: approvalOptions.length,
            reversibleFieldCount: reversibleFiltered.length,
            duplicateErrors
        },
        sqaLookup: {
            ...lookupWithOptions(fieldsLookup, sqaOptions),
            rawFieldCount: rawOptions.length,
            approvalFieldCount: approvalOptions.length,
            reversibleFieldCount: reversibleFiltered.length,
            duplicateErrors
        },
        reversibleLookup: {
            ...lookupWithOptions(fieldsLookup, reversibleFiltered),
            rawFieldCount: rawOptions.length,
            approvalFieldCount: approvalOptions.length,
            reversibleFieldCount: reversibleFiltered.length,
            duplicateErrors
        }
    };
}

function duplicateFieldMessages(project) {
    const messages = [];
    const sme = normalizedText(project.fields.approvedBySme);
    const sqa = normalizedText(project.fields.approvedBySqa);
    const reversible = (project.fields.reversibleBusinessFields || []).map((field) => ({
        value: field,
        normalized: normalizedText(field)
    })).filter((field) => field.normalized);
    if (sme && sqa && sme === sqa) {
        messages.push("SME and SQA approval fields must be different.");
    }
    const seen = new Set();
    for (const field of reversible) {
        if (seen.has(field.normalized)) {
            messages.push(`Duplicate reversible field: ${field.value}.`);
        }
        seen.add(field.normalized);
        if (sme && field.normalized === sme) {
            messages.push("SME approval field cannot also be reversible.");
        }
        if (sqa && field.normalized === sqa) {
            messages.push("SQA approval field cannot also be reversible.");
        }
    }
    return messages;
}

function removeValue(values, value) {
    const normalized = normalizedText(value);
    return (values || []).filter((item) => normalizedText(item) !== normalized);
}

function uniqueValues(values) {
    const seen = new Set();
    const result = [];
    for (const value of values || []) {
        const normalized = normalizedText(value);
        if (!normalized || seen.has(normalized)) {
            continue;
        }
        seen.add(normalized);
        result.push(value);
    }
    return result;
}

function cleanFieldConflicts(project, changedField) {
    const sme = project.fields.approvedBySme || "";
    const sqa = project.fields.approvedBySqa || "";
    if (changedField === "fields.approvedBySme" && sme && normalizedText(sme) === normalizedText(sqa)) {
        project.fields.approvedBySqa = "";
    }
    if (changedField === "fields.approvedBySqa" && sqa && normalizedText(sqa) === normalizedText(sme)) {
        project.fields.approvedBySme = "";
    }
    project.fields.reversibleBusinessFields = uniqueValues(project.fields.reversibleBusinessFields);
    project.fields.reversibleBusinessFields = removeValue(project.fields.reversibleBusinessFields, project.fields.approvedBySme);
    project.fields.reversibleBusinessFields = removeValue(project.fields.reversibleBusinessFields, project.fields.approvedBySqa);
}

function identityKey(index, role) {
    return `${index}:${role}`;
}

function ensureIdentitySearchState(index, role) {
    const key = identityKey(index, role);
    if (!identitySearchState[key]) {
        identitySearchState[key] = {
            query: "",
            lookup: { status: "NOT_CHECKED", message: "Type at least 2 characters to search ADO identities.", values: [], optionCount: 0 },
            searching: false
        };
    }
    return identitySearchState[key];
}

function roleUsers(project, role) {
    return role === "sme" ? project.approvals.smeUsers : project.approvals.sqaUsers;
}

function setRoleUsers(project, role, users) {
    if (role === "sme") {
        project.approvals.smeUsers = users;
    } else {
        project.approvals.sqaUsers = users;
    }
}

function normalizedIdentity(value) {
    return String(value || "").trim().toLowerCase();
}

function isResolvableIdentityValue(value) {
    const normalized = normalizedIdentity(value);
    return normalized.includes("@") || normalized.includes("\\");
}

function duplicateIdentityMessages(project) {
    const messages = [];
    for (const role of ["sme", "sqa"]) {
        const seen = new Set();
        for (const user of roleUsers(project, role) || []) {
            const normalized = normalizedIdentity(user);
            if (normalized && seen.has(normalized)) {
                messages.push(`${role.toUpperCase()} users contain duplicate identity: ${normalized}.`);
            }
            seen.add(normalized);
        }
    }
    const sqa = new Set((project.approvals.sqaUsers || []).map(normalizedIdentity));
    for (const smeUser of project.approvals.smeUsers || []) {
        const normalized = normalizedIdentity(smeUser);
        if (normalized && sqa.has(normalized)) {
            messages.push("Same identity appears in both SME and SQA lists.");
            break;
        }
    }
    return messages;
}

function unresolvedIdentityCount(project, role) {
    return (roleUsers(project, role) || []).filter((user) => !isResolvableIdentityValue(user)).length;
}

function userInitials(option) {
    const label = String(option?.displayName || option?.value || "").trim();
    const words = label.replace(/<[^>]+>/g, "").split(/\s+/).filter(Boolean);
    if (words.length === 0) {
        return "?";
    }
    return words.slice(0, 2).map((word) => word[0].toUpperCase()).join("");
}

function addUserToRole(project, role, value) {
    const normalized = normalizedIdentity(value);
    if (!normalized) {
        return false;
    }
    const users = roleUsers(project, role) || [];
    if (users.map(normalizedIdentity).includes(normalized)) {
        return false;
    }
    setRoleUsers(project, role, [...users, normalized]);
    return true;
}

function removeUserFromRole(project, role, value) {
    const normalized = normalizedIdentity(value);
    setRoleUsers(project, role, (roleUsers(project, role) || []).filter((user) => normalizedIdentity(user) !== normalized));
}

function identitySearchStatus(index, role, project) {
    const stateForRole = ensureIdentitySearchState(index, role);
    const selectedCount = (roleUsers(project, role) || []).length;
    const unresolvedCount = unresolvedIdentityCount(project, role);
    const warnings = duplicateIdentityMessages(project).length;
    updateSelectorDiagnostics(`${role}Users`, {
        status: stateForRole.lookup.status || "NOT_CHECKED",
        backendOptionCount: lookupOptionCount(stateForRole.lookup),
        receivedLength: rawOptionItems(stateForRole.lookup).length,
        normalizedLength: renderedOptionCount(stateForRole.lookup),
        renderedOptionCount: renderedOptionCount(stateForRole.lookup),
        domOptionCount: renderedOptionCount(stateForRole.lookup),
        lastQueryLength: stateForRole.query.length,
        resultCount: renderedOptionCount(stateForRole.lookup),
        selectedCount,
        unresolvedCount,
        identityWarnings: warnings,
        enabled: true,
        message: sanitizeMessage(stateForRole.lookup.message)
    });
}

function selectedUserChips(project, role) {
    const users = roleUsers(project, role) || [];
    if (users.length === 0) {
        return `<p class="note compact">No ${role.toUpperCase()} users selected.</p>`;
    }
    return `<div class="identity-chip-list">${users.map((user) => `
        <span class="identity-chip">
            <span>${escapeHtml(user)}</span>
            <button type="button" data-action="remove-user" data-role="${role}" data-user-value="${escapeHtml(user)}" aria-label="Remove ${escapeHtml(user)}">x</button>
        </span>
    `).join("")}</div>`;
}

function identitySearchResults(project, role, lookup) {
    const options = selectorOptions(lookup);
    if (lookup?.status === "VALID" && options.length === 0) {
        return `<p class="lookup-status">${validationBadge("WARNING")} No selectable identities returned.</p>`;
    }
    if (lookup?.status && lookup.status !== "VALID" && lookup.status !== "NOT_CHECKED") {
        return lookupBadge(lookup);
    }
    if (options.length === 0) {
        return "";
    }
    const selected = new Set((roleUsers(project, role) || []).map(normalizedIdentity));
    return `<div class="identity-results">${options.map((option) => {
        const normalized = normalizedIdentity(option.value);
        const disabled = !normalized || selected.has(normalized) ? "disabled" : "";
        const email = option.description && option.description !== option.displayName ? option.description : option.value;
        return `
            <button type="button" class="identity-result" data-action="add-user" data-role="${role}" data-user-value="${escapeHtml(option.value)}" ${disabled}>
                <span class="identity-avatar">${escapeHtml(userInitials(option))}</span>
                <span class="identity-result-text">
                    <strong>${escapeHtml(option.displayName || option.value)}</strong>
                    <small>${escapeHtml(email)}</small>
                </span>
            </button>
        `;
    }).join("")}</div>`;
}

function identityUserPicker(project, index, role, enabled) {
    const searchState = ensureIdentitySearchState(index, role);
    const disabled = enabled ? "" : "disabled";
    identitySearchStatus(index, role, project);
    return `
        <div class="identity-picker">
            <label>${role.toUpperCase()} users
                <input type="search" data-action="identity-search" data-role="${role}" value="${escapeHtml(searchState.query)}" placeholder="Search by email, login, or name" ${disabled}>
            </label>
            ${selectedUserChips(project, role)}
            ${identitySearchResults(project, role, searchState.lookup)}
        </div>
    `;
}

function renderedOptionCount(lookup) {
    return selectorOptions(lookup).length;
}

function createProjectModel() {
    return {
        name: "",
        enabled: true,
        supportedWorkItemTypes: [],
        states: { design: "Design", inReview: "In Review", approved: "Approved" },
        fields: { approvedBySme: "", approvedBySqa: "", reversibleBusinessFields: [] },
        approvals: { smeUsers: [], sqaUsers: [] }
    };
}

function createDiscoveryState() {
    return {
        requestToken: 0,
        projectStatus: { status: "NOT_CHECKED", message: "Project has not been loaded." },
        workItemTypes: { status: "NOT_CHECKED", message: "Load a project first.", values: [] },
        fields: { status: "NOT_CHECKED", message: "Select a Work Item type first.", values: [] },
        states: { status: "NOT_CHECKED", message: "Select a Work Item type first.", values: [] }
    };
}

function ensureDiscovery() {
    while (projectDiscovery.length < state.ado.projects.length) {
        projectDiscovery.push(createDiscoveryState());
    }
    if (projectDiscovery.length > state.ado.projects.length) {
        projectDiscovery = projectDiscovery.slice(0, state.ado.projects.length);
    }
}

function invalidatePreview() {
    lastPreview = null;
    saveBtn.disabled = true;
}

function schedulePreview() {
    invalidatePreview();
    clearTimeout(previewTimer);
    previewTimer = setTimeout(() => {
        previewDraft(false).catch(() => undefined);
    }, 250);
}

function clearChildSelections(project) {
    project.supportedWorkItemTypes = [];
    project.fields.approvedBySme = "";
    project.fields.approvedBySqa = "";
    project.fields.reversibleBusinessFields = [];
    project.states = { design: "Design", inReview: "In Review", approved: "Approved" };
}

function clearTypeSelections(project) {
    project.fields.approvedBySme = "";
    project.fields.approvedBySqa = "";
    project.fields.reversibleBusinessFields = [];
    project.states = { design: "Design", inReview: "In Review", approved: "Approved" };
}

function clearStaleProjectSelections() {
    if (!lookupHasOptions(projectOptionLookup)) {
        return;
    }
    state.ado.projects.forEach((project, index) => {
        if (project.name && !lookupContainsValue(projectOptionLookup, project.name)) {
            project.name = "";
            clearChildSelections(project);
            clearDiscovery(index, "project");
        }
    });
}

function clearDiscovery(index, level) {
    const discovery = projectDiscovery[index];
    if (!discovery) {
        return;
    }
    discovery.requestToken = ++discoveryRequestSequence;
    debugDiscovery("dependent-selectors-cleared", { index, level });
    if (level === "project") {
        discovery.projectStatus = { status: "NOT_CHECKED", message: "Project selection changed." };
        discovery.workItemTypes = { status: "NOT_CHECKED", message: "Load the project again.", values: [] };
        discovery.fields = { status: "NOT_CHECKED", message: "Select a Work Item type first.", values: [] };
        discovery.states = { status: "NOT_CHECKED", message: "Select a Work Item type first.", values: [] };
        for (const selector of ["workItemType", "approvedBySmeField", "approvedBySqaField", "reversibleBusinessFields", "designState", "inReviewState", "approvedState"]) {
            updateSelectorDiagnostics(selector, {
                status: "NOT_CHECKED",
                backendOptionCount: 0,
                receivedLength: 0,
                normalizedLength: 0,
                renderedOptionCount: 0,
                domOptionCount: "",
                enabled: false,
                message: "Cleared after Project selection changed."
            });
        }
    }
    if (level === "type") {
        discovery.fields = { status: "NOT_CHECKED", message: "Work Item type changed.", values: [] };
        discovery.states = { status: "NOT_CHECKED", message: "Work Item type changed.", values: [] };
        for (const selector of ["approvedBySmeField", "approvedBySqaField", "reversibleBusinessFields", "designState", "inReviewState", "approvedState"]) {
            updateSelectorDiagnostics(selector, {
                status: "NOT_CHECKED",
                backendOptionCount: 0,
                receivedLength: 0,
                normalizedLength: 0,
                renderedOptionCount: 0,
                domOptionCount: "",
                enabled: false,
                message: "Cleared after Work Item Type changed."
            });
        }
    }
}

function validationBadge(label) {
    return `<span class="badge badge-${label.toLowerCase().replace("_", "-")}">${label}</span>`;
}

function lookupBadge(lookup) {
    if (!lookup?.status) {
        return "";
    }
    return `<span class="lookup-status">${validationBadge(lookup.status)} ${escapeHtml(lookup.message || "")}</span>`;
}

function renderValidation(preview) {
    lastPreview = preview;
    const validation = preview?.validation;
    const fields = validation?.fields || [];
    const uiAdoDiscoveryCurrent = isUiAdoDiscoveryCurrent();
    saveBtn.disabled = !preview?.finalYamlAllowed || !uiAdoDiscoveryCurrent;

    if (fields.length === 0) {
        validationSummaryEl.innerHTML = "";
        return;
    }

    const rows = fields.map((field) => `
        <li>
            ${validationBadge(field.status)}
            <code>${escapeHtml(field.field)}</code>
            <span>${escapeHtml(field.message)}</span>
        </li>
    `).join("");

    const finalState = preview.finalYamlAllowed && uiAdoDiscoveryCurrent
        ? "Final YAML allowed."
        : "Final YAML blocked until errors, Not checked values, and current ADO selector verification are resolved.";
    validationSummaryEl.innerHTML = `
        <div class="validation-heading">
            <strong>Validation</strong>
            <span>${escapeHtml(finalState)}</span>
        </div>
        <ul>${rows}</ul>
    `;
}

function optionLabel(option, selectorName = "") {
    if (!option) {
        return "";
    }
    const fieldSelector = ["approvedBySmeField", "approvedBySqaField", "reversibleBusinessFields"].includes(selectorName);
    if (option.displayName && option.displayName !== option.value) {
        return fieldSelector ? `${option.displayName} - ${option.value}` : option.displayName;
    }
    return option.value;
}

function lookupContainsValue(lookup, value) {
    const normalized = String(value || "").trim().toLowerCase();
    return normalized.length > 0 && selectorOptions(lookup)
        .some((option) => option.value.trim().toLowerCase() === normalized);
}

function lookupHasOptions(lookup) {
    return lookup?.status === "VALID" && renderedOptionCount(lookup) > 0;
}

function normalizeOptionsLookup(lookup, emptyMessage, selectorName = "selector") {
    const backendOptionCount = lookupOptionCount(lookup);
    const receivedLength = rawOptionItems(lookup).length;
    const renderedOptions = selectorOptions(lookup);
    const status = lookup?.status || "NOT_CHECKED";
    const countMismatch = backendOptionCount !== receivedLength || backendOptionCount !== renderedOptions.length;
    const message = sanitizeMessage(
            lookup?.message || (countMismatch ? "Backend optionCount differs from received or normalized option count." : "")
    );
    debugDiscovery("discovery-response-received", {
        selector: selectorName,
        status,
        backendOptionCount,
        receivedLength,
        renderedOptionCount: renderedOptions.length
    });
    updateSelectorDiagnostics(selectorName, {
        status,
        backendOptionCount,
        receivedLength,
        normalizedLength: renderedOptions.length,
        renderedOptionCount: renderedOptions.length,
        enabled: status === "VALID" && renderedOptions.length > 0,
        message
    });
    if (backendOptionCount > 0 && renderedOptions.length === 0) {
        const message = `${selectorName} selector could not be populated from the ADO discovery response.`;
        errorDiscovery("selector-render-failed", {
            selector: selectorName,
            status,
            backendOptionCount,
            receivedLength,
            renderedOptionCount: renderedOptions.length,
            reason: "backend-count-without-renderable-options"
        });
        setStatus(message, true);
        updateSelectorDiagnostics(selectorName, {
            status: "ERROR",
            backendOptionCount,
            receivedLength,
            normalizedLength: 0,
            renderedOptionCount: 0,
            enabled: false,
            message
        });
        return { status: "ERROR", message, values: [], optionCount: 0 };
    }
    if (lookup?.status === "VALID" && renderedOptions.length === 0) {
        updateSelectorDiagnostics(selectorName, {
            status: "WARNING",
            backendOptionCount,
            receivedLength,
            normalizedLength: 0,
            renderedOptionCount: 0,
            enabled: false,
            message: emptyMessage
        });
        return { status: "WARNING", message: emptyMessage, values: [], optionCount: 0 };
    }
    return { ...(lookup || {}), values: renderedOptions, optionCount: renderedOptions.length };
}

function isCurrentDiscoveryRequest(index, requestToken, projectName, workItemType) {
    const project = state.ado.projects[index];
    const discovery = projectDiscovery[index];
    if (!project || !discovery || discovery.requestToken !== requestToken) {
        debugDiscovery("stale-response-ignored", { index, projectName, workItemType, reason: "request-token" });
        incrementStaleIgnored(workItemType === undefined ? "workItemType" : "fields", "request-token");
        return false;
    }
    if ((project.name || "").trim() !== projectName) {
        debugDiscovery("stale-response-ignored", { index, projectName, workItemType, reason: "project-changed" });
        incrementStaleIgnored(workItemType === undefined ? "workItemType" : "fields", "project-changed");
        return false;
    }
    if (workItemType !== undefined && (project.supportedWorkItemTypes?.[0] || "") !== workItemType) {
        debugDiscovery("stale-response-ignored", { index, projectName, workItemType, reason: "work-item-type-changed" });
        incrementStaleIgnored("fields", "work-item-type-changed");
        incrementStaleIgnored("states", "work-item-type-changed");
        return false;
    }
    return true;
}

function isProjectVerified(discovery, project) {
    return discovery?.projectStatus?.status === "VALID"
        && lookupContainsValue(discovery.projectStatus, project.name);
}

function hasSelectedWorkItemType(project) {
    return !!(project.supportedWorkItemTypes?.[0] || "").trim();
}

function areFieldsAndStatesReady(discovery, project) {
    return hasSelectedWorkItemType(project)
        && lookupHasOptions(discovery?.fields)
        && lookupHasOptions(discovery?.states);
}

function allValuesInLookup(values, lookup) {
    return values.every((value) => lookupContainsValue(lookup, value));
}

function isProjectDiscoveryCurrent(project, discovery) {
    const selectedType = project.supportedWorkItemTypes?.[0] || "";
    const fieldLookups = filteredFieldLookups(project, discovery?.fields || {});
    const requiredFields = [
        project.fields.approvedBySme,
        project.fields.approvedBySqa,
        ...(project.fields.reversibleBusinessFields || [])
    ];
    const requiredStates = [
        project.states.design,
        project.states.inReview,
        project.states.approved
    ];

    return isProjectVerified(discovery, project)
        && lookupContainsValue(discovery.workItemTypes, selectedType)
        && areFieldsAndStatesReady(discovery, project)
        && allValuesInLookup(requiredFields, discovery.fields)
        && duplicateFieldMessages(project).length === 0
        && lookupContainsValue(fieldLookups.smeLookup, project.fields.approvedBySme)
        && lookupContainsValue(fieldLookups.sqaLookup, project.fields.approvedBySqa)
        && allValuesInLookup(project.fields.reversibleBusinessFields || [], fieldLookups.reversibleLookup)
        && allValuesInLookup(requiredStates, discovery.states);
}

function isUiAdoDiscoveryCurrent() {
    ensureDiscovery();
    return (state.ado.projects || []).length > 0
        && state.ado.projects.every((project, index) => isProjectDiscoveryCurrent(project, projectDiscovery[index]));
}

function selectOptions(selectorName, lookup, selected, placeholder, enabled = false) {
    const options = selectorOptions(lookup);
    const hasSelected = options.some((option) => option.value === selected);
    const rows = [`<option value="">${escapeHtml(placeholder)}</option>`];
    if (selected && !hasSelected && lookup?.status !== "VALID") {
        rows.push(`<option value="${escapeHtml(selected)}" selected>${escapeHtml(selected)} - unchecked/manual</option>`);
    }
    for (const option of options) {
        rows.push(`<option value="${escapeHtml(option.value)}" ${option.value === selected ? "selected" : ""}>${escapeHtml(optionLabel(option, selectorName))}</option>`);
    }
    debugDiscovery("selector-rendered", {
        selector: selectorName,
        renderedOptionCount: options.length,
        selected,
        enabled: enabled && options.length > 0
    });
    updateSelectorDiagnostics(selectorName, {
        status: lookup?.status || "NOT_CHECKED",
        backendOptionCount: lookupOptionCount(lookup),
        receivedLength: rawOptionItems(lookup).length,
        normalizedLength: options.length,
        renderedOptionCount: options.length,
        domOptionCount: rows.length,
        rawFieldCount: lookup?.rawFieldCount ?? "",
        approvalFieldCount: lookup?.approvalFieldCount ?? "",
        reversibleFieldCount: lookup?.reversibleFieldCount ?? "",
        duplicateErrors: lookup?.duplicateErrors ?? "",
        enabled: enabled && options.length > 0,
        message: sanitizeMessage(lookup?.message)
    });
    return rows.join("");
}

function renderProjects() {
    ensureDiscovery();
    projectsEl.innerHTML = "";
    state.ado.projects.forEach((project, index) => {
        const discovery = projectDiscovery[index];
        const selectedType = project.supportedWorkItemTypes?.[0] || "";
        const projectVerified = isProjectVerified(discovery, project);
        const dependentOptionsReady = areFieldsAndStatesReady(discovery, project);
        const workItemTypeDisabled = projectVerified && lookupHasOptions(discovery.workItemTypes) ? "" : "disabled";
        const fieldAndStateDisabled = dependentOptionsReady ? "" : "disabled";
        const workItemTypeEnabled = !workItemTypeDisabled;
        const fieldAndStateEnabled = !fieldAndStateDisabled;
        const projectSelectorEnabled = lookupHasOptions(projectOptionLookup);
        const projectSelectorDisabled = projectSelectorEnabled ? "" : "disabled";
        const fieldLookups = filteredFieldLookups(project, discovery.fields);
        const fieldDuplicateMessages = duplicateFieldMessages(project);
        const identityMessages = duplicateIdentityMessages(project);
        const fieldDuplicateStatus = fieldDuplicateMessages.length
                ? `<span class="lookup-status">${validationBadge("ERROR")} ${escapeHtml(fieldDuplicateMessages.join(" "))}</span>`
                : "";
        const identityStatus = identityMessages.length
                ? `<span class="lookup-status">${validationBadge("WARNING")} ${escapeHtml(identityMessages.join(" "))}</span>`
                : "";
        debugDiscovery("selector-state", {
            index,
            project: project.name,
            projectVerified,
            projectSelectorDisabled: !!projectSelectorDisabled,
            workItemTypeDisabled: !!workItemTypeDisabled,
            fieldAndStateDisabled: !!fieldAndStateDisabled,
            projectOptionCount: lookupOptionCount(projectOptionLookup),
            projectRenderedOptionCount: renderedOptionCount(projectOptionLookup),
            workItemTypeOptionCount: lookupOptionCount(discovery.workItemTypes),
            workItemTypeRenderedOptionCount: renderedOptionCount(discovery.workItemTypes),
            fieldOptionCount: lookupOptionCount(discovery.fields),
            fieldRenderedOptionCount: renderedOptionCount(discovery.fields),
            stateOptionCount: lookupOptionCount(discovery.states),
            stateRenderedOptionCount: renderedOptionCount(discovery.states)
        });
        updateSelectorDiagnostics("reversibleBusinessFields", {
            status: discovery.fields?.status || "NOT_CHECKED",
            backendOptionCount: lookupOptionCount(discovery.fields),
            receivedLength: rawOptionItems(discovery.fields).length,
            normalizedLength: fieldLookups.reversibleFieldCount,
            renderedOptionCount: fieldLookups.reversibleFieldCount,
            domOptionCount: fieldLookups.reversibleFieldCount,
            rawFieldCount: fieldLookups.rawFieldCount,
            approvalFieldCount: fieldLookups.approvalFieldCount,
            reversibleFieldCount: fieldLookups.reversibleFieldCount,
            duplicateErrors: fieldDuplicateMessages.length,
            enabled: fieldAndStateEnabled && fieldLookups.reversibleFieldCount > 0,
            message: sanitizeMessage(fieldDuplicateMessages.join(" ") || discovery.fields?.message)
        });
        const card = document.createElement("div");
        card.className = "project-card";
        card.innerHTML = `
            <div class="row-between">
                <h3>Proyecto ${index + 1}</h3>
                <button type="button" class="remove" data-action="remove">Eliminar</button>
            </div>
            <div class="selector-grid">
                <label>Project
                    <select data-field="name" data-selector-name="project" ${projectSelectorDisabled}>
                        ${selectOptions("project", projectOptionLookup, project.name || "", "Load and select a discovered project", projectSelectorEnabled)}
                    </select>
                </label>
                <button type="button" data-action="load-project">Verify Project</button>
            </div>
            ${lookupBadge(discovery.projectStatus)}
            <label class="switch-row"><input data-field="enabled" type="checkbox" ${project.enabled ? "checked" : ""}> Enabled</label>
            <label>Work Item Type
                <select data-field="supportedWorkItemTypes.0" ${workItemTypeDisabled}>
                    ${selectOptions("workItemType", discovery.workItemTypes, selectedType, "Select a discovered Work Item type", workItemTypeEnabled)}
                </select>
            </label>
            ${lookupBadge(discovery.workItemTypes)}
            <div class="grid-2">
                <label>State design
                    <select data-field="states.design" ${fieldAndStateDisabled}>
                        ${selectOptions("designState", discovery.states, project.states.design || "", "Select a discovered state", fieldAndStateEnabled)}
                    </select>
                </label>
                <label>State in-review
                    <select data-field="states.inReview" ${fieldAndStateDisabled}>
                        ${selectOptions("inReviewState", discovery.states, project.states.inReview || "", "Select a discovered state", fieldAndStateEnabled)}
                    </select>
                </label>
            </div>
            <label>State approved
                <select data-field="states.approved" ${fieldAndStateDisabled}>
                    ${selectOptions("approvedState", discovery.states, project.states.approved || "", "Select a discovered final state", fieldAndStateEnabled)}
                </select>
            </label>
            ${lookupBadge(discovery.states)}
            <div class="grid-2">
                <label>Field approved-by-sme
                    <select data-field="fields.approvedBySme" ${fieldAndStateDisabled}>
                        ${selectOptions("approvedBySmeField", fieldLookups.smeLookup, project.fields.approvedBySme || "", "Select a discovered approval field", fieldAndStateEnabled)}
                    </select>
                </label>
                <label>Field approved-by-sqa
                    <select data-field="fields.approvedBySqa" ${fieldAndStateDisabled}>
                        ${selectOptions("approvedBySqaField", fieldLookups.sqaLookup, project.fields.approvedBySqa || "", "Select a discovered approval field", fieldAndStateEnabled)}
                    </select>
                </label>
            </div>
            <label>Reversible business fields
                <select data-field="fields.reversibleBusinessFields" multiple size="6" ${fieldAndStateDisabled}>
                    ${selectorOptions(fieldLookups.reversibleLookup).map((option) => `
                        <option value="${escapeHtml(option.value)}" ${(project.fields.reversibleBusinessFields || []).includes(option.value) ? "selected" : ""}>
                            ${escapeHtml(optionLabel(option, "reversibleBusinessFields"))}
                        </option>
                    `).join("")}
                </select>
            </label>
            ${lookupBadge(discovery.fields)}
            ${fieldDuplicateStatus}
            <div class="grid-2">
                ${identityUserPicker(project, index, "sme", projectVerified)}
                ${identityUserPicker(project, index, "sqa", projectVerified)}
            </div>
            ${identityStatus}
            <p class="note compact">Display names are shown for selection only. YAML stores normalized email/login values.</p>
        `;

        card.addEventListener("input", (event) => {
            handleProjectInput(project, index, event);
        });
        card.addEventListener("change", (event) => {
            handleProjectInput(project, index, event);
        });

        card.querySelector("[data-action='remove']").addEventListener("click", () => {
            state.ado.projects.splice(index, 1);
            projectDiscovery.splice(index, 1);
            invalidatePreview();
            renderProjects();
            schedulePreview();
        });

        card.querySelector("[data-action='load-project']").addEventListener("click", async () => {
            await loadProject(index);
        });
        for (const input of card.querySelectorAll("[data-action='identity-search']")) {
            input.addEventListener("input", (event) => {
                handleIdentitySearchInput(index, event.target.getAttribute("data-role"), event.target.value);
            });
        }
        for (const button of card.querySelectorAll("[data-action='add-user']")) {
            button.addEventListener("click", () => {
                if (addUserToRole(project, button.getAttribute("data-role"), button.getAttribute("data-user-value"))) {
                    renderProjects();
                    schedulePreview();
                }
            });
        }
        for (const button of card.querySelectorAll("[data-action='remove-user']")) {
            button.addEventListener("click", () => {
                removeUserFromRole(project, button.getAttribute("data-role"), button.getAttribute("data-user-value"));
                renderProjects();
                schedulePreview();
            });
        }

        projectsEl.appendChild(card);
    });
}

function handleProjectInput(project, index, event) {
    const field = event.target.getAttribute("data-field");
    if (!field) {
        return;
    }
    if (event.type === "input" && event.target.tagName === "SELECT") {
        return;
    }

    if (field === "enabled") {
        project.enabled = event.target.checked;
        schedulePreview();
        return;
    }

    if (field === "name") {
        project.name = event.target.value;
        clearChildSelections(project);
        clearDiscovery(index, "project");
        if (event.type === "change") {
            renderProjects();
        }
        schedulePreview();
        return;
    }

    if (field === "supportedWorkItemTypes.0") {
        project.supportedWorkItemTypes = event.target.value ? [event.target.value] : [];
        clearTypeSelections(project);
        clearDiscovery(index, "type");
        renderProjects();
        loadFieldAndStateOptions(index).catch((error) => setStatus(error.message, true));
        schedulePreview();
        return;
    }

    if (field === "fields.reversibleBusinessFields") {
        project.fields.reversibleBusinessFields = uniqueValues(Array.from(event.target.selectedOptions).map((option) => option.value));
        cleanFieldConflicts(project, field);
        renderProjects();
        schedulePreview();
        return;
    }
    const parts = field.split(".");
    if (parts.length === 1) {
        project[parts[0]] = event.target.value;
    } else {
        project[parts[0]][parts[1]] = event.target.value;
    }
    if (field === "fields.approvedBySme" || field === "fields.approvedBySqa") {
        cleanFieldConflicts(project, field);
        renderProjects();
    }
    schedulePreview();
}

function handleIdentitySearchInput(index, role, query) {
    const stateForRole = ensureIdentitySearchState(index, role);
    stateForRole.query = query || "";
    stateForRole.lookup = stateForRole.query.trim().length < 2
            ? { status: "NOT_CHECKED", message: "Type at least 2 characters to search ADO identities.", values: [], optionCount: 0 }
            : stateForRole.lookup;
    identitySearchStatus(index, role, state.ado.projects[index]);
    clearTimeout(identitySearchTimers[identityKey(index, role)]);
    if (stateForRole.query.trim().length < 2) {
        renderProjects();
        return;
    }
    identitySearchTimers[identityKey(index, role)] = setTimeout(() => {
        loadIdentityOptions(index, role, stateForRole.query.trim()).catch((error) => setStatus(error.message, true));
    }, 300);
}

function readFormToState() {
    state.ado.organization = document.getElementById("adoOrganization").value.trim();
    state.ado.httpClientEnabled = document.getElementById("adoHttpClientEnabled").checked;
    state.ado.dryRun = document.getElementById("adoDryRun").checked;

    state.bot.identityEmail = document.getElementById("botIdentityEmail").value.trim();

    state.webhook.sharedSecret.enabled = document.getElementById("webhookEnabled").checked;
    state.webhook.sharedSecret.headerName = document.getElementById("webhookHeaderName").value.trim();

    state.retry.maxAttempts = Number(document.getElementById("retryMaxAttempts").value || 3);
    state.retry.defaultBackoffSeconds = Number(document.getElementById("retryBackoff").value || 30);
    state.retry.respectRetryAfter = document.getElementById("retryRespectAfter").checked;

    state.idempotency.type = document.getElementById("idempotencyType").value.trim();
    state.idempotency.sqlitePath = document.getElementById("idempotencyPath").value.trim();
    state.idempotency.ttlHours = Number(document.getElementById("idempotencyTtl").value || 24);
    state.idempotency.maxRecords = Number(document.getElementById("idempotencyMaxRecords").value || 10000);
}

function fillFormFromState() {
    document.getElementById("adoOrganization").value = state.ado.organization || "";
    document.getElementById("adoHttpClientEnabled").checked = !!state.ado.httpClientEnabled;
    document.getElementById("adoDryRun").checked = state.ado.dryRun !== false;

    document.getElementById("botIdentityEmail").value = state.bot.identityEmail || "";

    document.getElementById("webhookEnabled").checked = state.webhook.sharedSecret.enabled !== false;
    document.getElementById("webhookHeaderName").value = state.webhook.sharedSecret.headerName || "X-ADO-Webhook-Secret";

    document.getElementById("retryMaxAttempts").value = state.retry.maxAttempts ?? 3;
    document.getElementById("retryBackoff").value = state.retry.defaultBackoffSeconds ?? 30;
    document.getElementById("retryRespectAfter").checked = state.retry.respectRetryAfter !== false;

    document.getElementById("idempotencyType").value = state.idempotency.type || "sqlite";
    document.getElementById("idempotencyPath").value = state.idempotency.sqlitePath || "./data/approval-bot-sandbox.sqlite";
    document.getElementById("idempotencyTtl").value = state.idempotency.ttlHours ?? 24;
    document.getElementById("idempotencyMaxRecords").value = state.idempotency.maxRecords ?? 10000;

    renderProjects();
}

async function postJson(url, body) {
    const response = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
    });
    let payload;
    try {
        payload = await response.json();
    } catch (error) {
        throw new Error(`Request failed because the response was not valid JSON. HTTP ${response.status}.`);
    }
    if (!response.ok) {
        throw new Error(payload.error || `Request failed with HTTP ${response.status}.`);
    }
    return payload;
}

async function postConfig(url) {
    readFormToState();
    return postJson(url, state);
}

async function discover(operation, url, body) {
    const started = performance.now();
    const safeDetails = {
        operation,
        url,
        organization: body?.organization,
        project: body?.project,
        workItemType: body?.workItemType
    };
    debugDiscovery("request-started", safeDetails);
    try {
        const result = await postJson(url, body);
        debugDiscovery("request-completed", {
            ...safeDetails,
            status: result.status,
            optionCount: lookupOptionCount(result),
            durationMs: Math.round(performance.now() - started)
        });
        if (result.status === "ERROR") {
            setStatus(result.message || "ADO discovery returned an error.", true);
        } else if (result.status === "NOT_CHECKED" || result.status === "WARNING") {
            setStatus(result.message || "ADO discovery was not fully checked.");
        }
        return result;
    } catch (error) {
        const result = {
            status: "ERROR",
            message: error.message || "ADO discovery request failed.",
            values: [],
            optionCount: 0
        };
        setStatus(result.message, true);
        errorDiscovery("request-failed", {
            ...safeDetails,
            status: result.status,
            optionCount: result.optionCount,
            durationMs: Math.round(performance.now() - started),
            message: result.message
        });
        return result;
    }
}

async function loadProjects() {
    readFormToState();
    projectOptionLookup = normalizeOptionsLookup(await discover("list-projects", "/api/config-ui/discovery/projects", {
        organization: state.ado.organization
    }), "No projects were returned for the configured organization.", "project");
    clearStaleProjectSelections();
    renderProjectSelectors();
    renderProjects();
    schedulePreview();
}

async function loadProject(index) {
    readFormToState();
    ensureDiscovery();
    const project = state.ado.projects[index];
    const discovery = projectDiscovery[index];
    const projectName = (project.name || "").trim();
    debugDiscovery("verify-project-clicked", { index, project: projectName });
    clearChildSelections(project);
    clearDiscovery(index, "project");
    const requestToken = ++discoveryRequestSequence;
    discovery.requestToken = requestToken;
    const projectStatus = await discover("verify-project", "/api/config-ui/discovery/validate-project", {
        organization: state.ado.organization,
        project: projectName
    });
    if (!isCurrentDiscoveryRequest(index, requestToken, projectName)) {
        return;
    }
    discovery.projectStatus = projectStatus;
    if (isProjectVerified(discovery, project)) {
        const workItemTypes = await discover("load-work-item-types", "/api/config-ui/discovery/work-item-types", {
            organization: state.ado.organization,
            project: projectName
        });
        if (!isCurrentDiscoveryRequest(index, requestToken, projectName)) {
            return;
        }
        discovery.workItemTypes = normalizeOptionsLookup(
                workItemTypes,
                "No Work Item Types were returned for the verified project.",
                "workItemType"
        );
        debugDiscovery("selector-populated", {
            index,
            selector: "workItemType",
            status: discovery.workItemTypes.status,
            backendOptionCount: lookupOptionCount(workItemTypes),
            renderedOptionCount: renderedOptionCount(discovery.workItemTypes)
        });
    } else {
        discovery.workItemTypes = { status: "NOT_CHECKED", message: "Verify the project before selecting a Work Item type.", values: [], optionCount: 0 };
    }
    renderProjects();
    schedulePreview();
}

async function loadFieldAndStateOptions(index) {
    readFormToState();
    ensureDiscovery();
    const project = state.ado.projects[index];
    const type = project.supportedWorkItemTypes?.[0] || "";
    if (!project.name || !type) {
        return;
    }
    const discovery = projectDiscovery[index];
    if (!isProjectVerified(discovery, project)) {
        discovery.fields = { status: "NOT_CHECKED", message: "Verify the project first.", values: [], optionCount: 0 };
        discovery.states = { status: "NOT_CHECKED", message: "Verify the project first.", values: [], optionCount: 0 };
        renderProjects();
        schedulePreview();
        return;
    }
    const projectName = (project.name || "").trim();
    const requestToken = ++discoveryRequestSequence;
    discovery.requestToken = requestToken;
    const fields = await discover("load-fields", "/api/config-ui/discovery/fields", {
        organization: state.ado.organization,
        project: projectName,
        workItemType: type
    });
    if (!isCurrentDiscoveryRequest(index, requestToken, projectName, type)) {
        return;
    }
    const states = await discover("load-states", "/api/config-ui/discovery/states", {
        organization: state.ado.organization,
        project: projectName,
        workItemType: type
    });
    if (!isCurrentDiscoveryRequest(index, requestToken, projectName, type)) {
        return;
    }
    discovery.fields = normalizeOptionsLookup(fields, "No fields were returned for the selected Work Item Type.", "fields");
    discovery.states = normalizeOptionsLookup(states, "No states were returned for the selected Work Item Type.", "states");
    debugDiscovery("selector-populated", {
        index,
        selector: "fields",
        status: discovery.fields.status,
        backendOptionCount: lookupOptionCount(fields),
        renderedOptionCount: renderedOptionCount(discovery.fields)
    });
    debugDiscovery("selector-populated", {
        index,
        selector: "states",
        status: discovery.states.status,
        backendOptionCount: lookupOptionCount(states),
        renderedOptionCount: renderedOptionCount(discovery.states)
    });
    renderProjects();
    schedulePreview();
}

async function loadIdentityOptions(index, role, query) {
    readFormToState();
    ensureDiscovery();
    const project = state.ado.projects[index];
    const discovery = projectDiscovery[index];
    if (!project || !isProjectVerified(discovery, project)) {
        const searchState = ensureIdentitySearchState(index, role);
        searchState.lookup = { status: "NOT_CHECKED", message: "Verify the project before searching users.", values: [], optionCount: 0 };
        renderProjects();
        return;
    }
    const searchState = ensureIdentitySearchState(index, role);
    const requestQuery = query || "";
    searchState.searching = true;
    const result = await discover("search-users", "/api/config-ui/discovery/users/search", {
        organization: state.ado.organization,
        project: project.name,
        query: requestQuery
    });
    if (ensureIdentitySearchState(index, role).query.trim() !== requestQuery) {
        incrementStaleIgnored(`${role}Users`, "identity-query-changed");
        return;
    }
    searchState.lookup = normalizeOptionsLookup(
            result,
            "No selectable ADO identities were returned for the search.",
            `${role}Users`
    );
    searchState.searching = false;
    debugDiscovery("selector-populated", {
        index,
        selector: `${role}Users`,
        status: searchState.lookup.status,
        backendOptionCount: lookupOptionCount(result),
        renderedOptionCount: renderedOptionCount(searchState.lookup),
        queryLength: requestQuery.length
    });
    renderProjects();
}

async function previewDraft(showStatus = true) {
    const payload = await postConfig("/api/config-ui/preview");
    yamlOutputEl.textContent = payload.yaml || "";
    renderValidation(payload);
    if (showStatus) {
        if (payload.draftYamlAvailable) {
            setStatus(payload.finalYamlAllowed ? "YAML final permitido." : "YAML de borrador generado; faltan validaciones ADO.");
        } else {
            setStatus("Errores bloqueantes: no se genero YAML.", true);
        }
    }
    return payload;
}

function renderProjectSelectors() {
    const options = selectorOptions(projectOptionLookup);
    document.getElementById("projectLookupStatus").innerHTML = lookupBadge(projectOptionLookup);
    debugDiscovery("selector-rendered", {
        selector: "project",
        status: projectOptionLookup.status,
        backendOptionCount: lookupOptionCount(projectOptionLookup),
        renderedOptionCount: options.length,
        enabled: options.length > 0
    });
    updateSelectorDiagnostics("project", {
        status: projectOptionLookup.status || "NOT_CHECKED",
        backendOptionCount: lookupOptionCount(projectOptionLookup),
        receivedLength: rawOptionItems(projectOptionLookup).length,
        normalizedLength: options.length,
        renderedOptionCount: options.length,
        domOptionCount: options.length > 0 ? options.length + 1 : 1,
        enabled: options.length > 0,
        message: sanitizeMessage(projectOptionLookup.message || "Project selector renders all discovered project options.")
    });
    renderDiscoveredProjectsDebug(options);
}

function renderDiscoveredProjectsDebug(options) {
    if (!discoveredProjectsDebugEl) {
        return;
    }
    if (!isConfigUiDebugEnabled()) {
        discoveredProjectsDebugEl.hidden = true;
        discoveredProjectsDebugEl.innerHTML = "";
        return;
    }
    discoveredProjectsDebugEl.hidden = false;
    const rows = options.map((option) => `
        <li>
            <button type="button" data-project-value="${escapeHtml(option.value)}">${escapeHtml(optionLabel(option))}</button>
        </li>
    `).join("");
    discoveredProjectsDebugEl.innerHTML = `
        <strong>Discovered projects</strong>
        <p class="note compact">These are the same discovered project options rendered by the Project selector.</p>
        <ul>${rows || "<li>No projects rendered.</li>"}</ul>
    `;
    for (const button of discoveredProjectsDebugEl.querySelectorAll("[data-project-value]")) {
        button.addEventListener("click", () => {
            if (state.ado.projects.length === 0) {
                state.ado.projects.push(createProjectModel());
                projectDiscovery.push(createDiscoveryState());
            }
            state.ado.projects[0].name = button.getAttribute("data-project-value") || "";
            clearChildSelections(state.ado.projects[0]);
            clearDiscovery(0, "project");
            renderProjects();
            schedulePreview();
        });
    }
}

function handleGlobalInput() {
    readFormToState();
    schedulePreview();
}

function handleOrganizationChanged() {
    readFormToState();
    projectOptionLookup = { status: "NOT_CHECKED", message: "Organization changed; reload projects.", values: [] };
    for (const project of state.ado.projects) {
        clearChildSelections(project);
    }
    projectDiscovery = state.ado.projects.map(createDiscoveryState);
    renderProjectSelectors();
    renderProjects();
    schedulePreview();
}

async function initialize() {
    setStatus("Cargando configuracion...");
    renderDiagnosticsPanel();
    const response = await fetch("/api/config-ui/model");
    state = await response.json();
    if (!Array.isArray(state.ado.projects)) {
        state.ado.projects = [];
    }
    projectDiscovery = state.ado.projects.map(createDiscoveryState);
    fillFormFromState();
    saveBtn.disabled = true;
    setStatus("Configuracion cargada. Carga ADO discovery para poblar selectores.");
    await previewDraft(false);
}

document.getElementById("loadProjects").addEventListener("click", () => {
    loadProjects().catch((error) => setStatus(error.message, true));
});

document.getElementById("adoOrganization").addEventListener("input", handleOrganizationChanged);
document.getElementById("adoHttpClientEnabled").addEventListener("change", handleGlobalInput);
document.getElementById("adoDryRun").addEventListener("change", handleGlobalInput);
document.getElementById("botIdentityEmail").addEventListener("input", handleGlobalInput);
document.getElementById("webhookEnabled").addEventListener("change", handleGlobalInput);
document.getElementById("webhookHeaderName").addEventListener("input", handleGlobalInput);
document.getElementById("retryMaxAttempts").addEventListener("input", handleGlobalInput);
document.getElementById("retryBackoff").addEventListener("input", handleGlobalInput);
document.getElementById("retryRespectAfter").addEventListener("change", handleGlobalInput);
document.getElementById("idempotencyType").addEventListener("input", handleGlobalInput);
document.getElementById("idempotencyPath").addEventListener("input", handleGlobalInput);
document.getElementById("idempotencyTtl").addEventListener("input", handleGlobalInput);
document.getElementById("idempotencyMaxRecords").addEventListener("input", handleGlobalInput);

document.getElementById("addProject").addEventListener("click", () => {
    state.ado.projects.push(createProjectModel());
    projectDiscovery.push(createDiscoveryState());
    renderProjects();
    schedulePreview();
});

document.getElementById("previewBtn").addEventListener("click", async () => {
    try {
        await previewDraft(true);
    } catch (error) {
        setStatus(error.message, true);
    }
});

document.getElementById("saveBtn").addEventListener("click", async () => {
    try {
        if (!isUiAdoDiscoveryCurrent()) {
            setStatus("Verify project and select current ADO-backed values before saving final YAML.", true);
            saveBtn.disabled = true;
            return;
        }
        const payload = await postConfig("/api/config-ui/save");
        yamlOutputEl.textContent = payload.preview?.yaml || "";
        renderValidation(payload.preview);
        setStatus(`${payload.message} (${payload.path})`);
    } catch (error) {
        setStatus(error.message, true);
    }
});

initialize().catch((error) => {
    setStatus(error.message, true);
});
