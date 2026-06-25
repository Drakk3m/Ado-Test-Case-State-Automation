const statusEl = document.getElementById("status");
const yamlOutputEl = document.getElementById("yamlOutput");
const projectsEl = document.getElementById("projects");
const validationSummaryEl = document.getElementById("validationSummary");
const saveBtn = document.getElementById("saveBtn");

let state = { ado: { projects: [] } };
let lastPreview = null;
let previewTimer = null;
let projectOptionLookup = { status: "NOT_CHECKED", message: "", values: [] };
let projectDiscovery = [];

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

function clearDiscovery(index, level) {
    const discovery = projectDiscovery[index];
    if (!discovery) {
        return;
    }
    if (level === "project") {
        discovery.projectStatus = { status: "NOT_CHECKED", message: "Project selection changed." };
        discovery.workItemTypes = { status: "NOT_CHECKED", message: "Load the project again.", values: [] };
        discovery.fields = { status: "NOT_CHECKED", message: "Select a Work Item type first.", values: [] };
        discovery.states = { status: "NOT_CHECKED", message: "Select a Work Item type first.", values: [] };
    }
    if (level === "type") {
        discovery.fields = { status: "NOT_CHECKED", message: "Work Item type changed.", values: [] };
        discovery.states = { status: "NOT_CHECKED", message: "Work Item type changed.", values: [] };
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
    saveBtn.disabled = !preview?.finalYamlAllowed;

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

    const finalState = preview.finalYamlAllowed
        ? "Final YAML allowed."
        : "Final YAML blocked until errors and Not checked values are resolved.";
    validationSummaryEl.innerHTML = `
        <div class="validation-heading">
            <strong>Validation</strong>
            <span>${escapeHtml(finalState)}</span>
        </div>
        <ul>${rows}</ul>
    `;
}

function optionLabel(option) {
    if (!option) {
        return "";
    }
    if (option.displayName && option.displayName !== option.value) {
        return `${option.displayName} (${option.value})`;
    }
    return option.value;
}

function selectOptions(options, selected, placeholder) {
    const hasSelected = options.some((option) => option.value === selected);
    const rows = [`<option value="">${escapeHtml(placeholder)}</option>`];
    if (selected && !hasSelected) {
        rows.push(`<option value="${escapeHtml(selected)}" selected>${escapeHtml(selected)} - unchecked/manual</option>`);
    }
    for (const option of options) {
        const description = option.description ? ` - ${option.description}` : "";
        rows.push(`<option value="${escapeHtml(option.value)}" ${option.value === selected ? "selected" : ""}>${escapeHtml(optionLabel(option) + description)}</option>`);
    }
    return rows.join("");
}

function projectDatalist() {
    return (projectOptionLookup.values || [])
        .map((option) => `<option value="${escapeHtml(option.value)}">${escapeHtml(optionLabel(option))}</option>`)
        .join("");
}

function renderProjects() {
    ensureDiscovery();
    projectsEl.innerHTML = "";
    state.ado.projects.forEach((project, index) => {
        const discovery = projectDiscovery[index];
        const selectedType = project.supportedWorkItemTypes?.[0] || "";
        const card = document.createElement("div");
        card.className = "project-card";
        card.innerHTML = `
            <div class="row-between">
                <h3>Proyecto ${index + 1}</h3>
                <button type="button" class="remove" data-action="remove">Eliminar</button>
            </div>
            <div class="selector-grid">
                <label>Project
                    <input data-field="name" list="adoProjectOptions" type="text" value="${escapeHtml(project.name || "")}">
                </label>
                <button type="button" data-action="load-project">Validate / Load Project</button>
            </div>
            ${lookupBadge(discovery.projectStatus)}
            <label class="switch-row"><input data-field="enabled" type="checkbox" ${project.enabled ? "checked" : ""}> Enabled</label>
            <label>Work Item Type
                <select data-field="supportedWorkItemTypes.0">
                    ${selectOptions(discovery.workItemTypes.values || [], selectedType, "Select a discovered Work Item type")}
                </select>
            </label>
            ${lookupBadge(discovery.workItemTypes)}
            <div class="grid-2">
                <label>State design
                    <select data-field="states.design">
                        ${selectOptions(discovery.states.values || [], project.states.design || "", "Select a discovered state")}
                    </select>
                </label>
                <label>State in-review
                    <select data-field="states.inReview">
                        ${selectOptions(discovery.states.values || [], project.states.inReview || "", "Select a discovered state")}
                    </select>
                </label>
            </div>
            <label>State approved
                <select data-field="states.approved">
                    ${selectOptions(discovery.states.values || [], project.states.approved || "", "Select a discovered final state")}
                </select>
            </label>
            ${lookupBadge(discovery.states)}
            <div class="grid-2">
                <label>Field approved-by-sme
                    <select data-field="fields.approvedBySme">
                        ${selectOptions(discovery.fields.values || [], project.fields.approvedBySme || "", "Select a discovered field")}
                    </select>
                </label>
                <label>Field approved-by-sqa
                    <select data-field="fields.approvedBySqa">
                        ${selectOptions(discovery.fields.values || [], project.fields.approvedBySqa || "", "Select a discovered field")}
                    </select>
                </label>
            </div>
            <label>Reversible business fields
                <select data-field="fields.reversibleBusinessFields" multiple size="6">
                    ${(discovery.fields.values || []).map((option) => `
                        <option value="${escapeHtml(option.value)}" ${(project.fields.reversibleBusinessFields || []).includes(option.value) ? "selected" : ""}>
                            ${escapeHtml(optionLabel(option))}
                        </option>
                    `).join("")}
                </select>
            </label>
            ${lookupBadge(discovery.fields)}
            <div class="grid-2">
                <label>SME users (email/login)
                    <textarea data-field="approvals.smeUsers">${escapeHtml((project.approvals.smeUsers || []).join("\n"))}</textarea>
                </label>
                <label>SQA users (email/login)
                    <textarea data-field="approvals.sqaUsers">${escapeHtml((project.approvals.sqaUsers || []).join("\n"))}</textarea>
                </label>
            </div>
            <p class="note compact">User identity lookup remains warning-only unless ADO returns selectable identities.</p>
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
        project.fields.approvedBySme = "";
        project.fields.approvedBySqa = "";
        project.fields.reversibleBusinessFields = [];
        clearDiscovery(index, "type");
        renderProjects();
        loadFieldAndStateOptions(index).catch((error) => setStatus(error.message, true));
        schedulePreview();
        return;
    }

    if (field === "fields.reversibleBusinessFields") {
        project.fields.reversibleBusinessFields = Array.from(event.target.selectedOptions).map((option) => option.value);
        schedulePreview();
        return;
    }
    if (field === "approvals.smeUsers") {
        project.approvals.smeUsers = splitLines(event.target.value);
        schedulePreview();
        return;
    }
    if (field === "approvals.sqaUsers") {
        project.approvals.sqaUsers = splitLines(event.target.value);
        schedulePreview();
        return;
    }

    const parts = field.split(".");
    if (parts.length === 1) {
        project[parts[0]] = event.target.value;
    } else {
        project[parts[0]][parts[1]] = event.target.value;
    }
    schedulePreview();
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
    const payload = await response.json();
    if (!response.ok) {
        throw new Error(payload.error || "Error inesperado");
    }
    return payload;
}

async function postConfig(url) {
    readFormToState();
    return postJson(url, state);
}

async function discover(url, body) {
    const result = await postJson(url, body);
    if (result.status === "ERROR") {
        setStatus(result.message || "ADO discovery returned an error.", true);
    } else if (result.status === "NOT_CHECKED" || result.status === "WARNING") {
        setStatus(result.message || "ADO discovery was not fully checked.");
    }
    return result;
}

async function loadProjects() {
    readFormToState();
    projectOptionLookup = await discover("/api/config-ui/discovery/projects", {
        organization: state.ado.organization
    });
    renderProjectDatalist();
    renderProjects();
    schedulePreview();
}

async function loadProject(index) {
    readFormToState();
    ensureDiscovery();
    const project = state.ado.projects[index];
    const discovery = projectDiscovery[index];
    discovery.projectStatus = await discover("/api/config-ui/discovery/validate-project", {
        organization: state.ado.organization,
        project: project.name
    });
    discovery.workItemTypes = await discover("/api/config-ui/discovery/work-item-types", {
        organization: state.ado.organization,
        project: project.name
    });
    discovery.fields = { status: "NOT_CHECKED", message: "Select a Work Item type first.", values: [] };
    discovery.states = { status: "NOT_CHECKED", message: "Select a Work Item type first.", values: [] };
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
    discovery.fields = await discover("/api/config-ui/discovery/fields", {
        organization: state.ado.organization,
        project: project.name,
        workItemType: type
    });
    discovery.states = await discover("/api/config-ui/discovery/states", {
        organization: state.ado.organization,
        project: project.name,
        workItemType: type
    });
    renderProjects();
    schedulePreview();
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

function renderProjectDatalist() {
    document.getElementById("adoProjectOptions").innerHTML = projectDatalist();
    document.getElementById("projectLookupStatus").innerHTML = lookupBadge(projectOptionLookup);
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
    renderProjectDatalist();
    renderProjects();
    schedulePreview();
}

async function initialize() {
    setStatus("Cargando configuracion...");
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
