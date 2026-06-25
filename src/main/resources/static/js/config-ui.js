const statusEl = document.getElementById("status");
const yamlOutputEl = document.getElementById("yamlOutput");
const projectsEl = document.getElementById("projects");
const validationSummaryEl = document.getElementById("validationSummary");
const saveBtn = document.getElementById("saveBtn");

let state = {
    ado: { projects: [] }
};
let lastPreview = null;

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
        supportedWorkItemTypes: ["Test Case"],
        states: { design: "Design", inReview: "In Review", approved: "Approval" },
        fields: {
            approvedBySme: "Custom.ApproverTech",
            approvedBySqa: "Custom.ApproverTest",
            reversibleBusinessFields: [
                "System.Title",
                "System.Description",
                "Microsoft.VSTS.TCM.Steps",
                "Microsoft.VSTS.TCM.LocalDataSource"
            ]
        },
        approvals: { smeUsers: [], sqaUsers: [] }
    };
}

function validationBadge(label) {
    return `<span class="badge badge-${label.toLowerCase().replace("_", "-")}">${label}</span>`;
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

function renderProjects() {
    projectsEl.innerHTML = "";
    state.ado.projects.forEach((project, index) => {
        const card = document.createElement("div");
        card.className = "project-card";
        card.innerHTML = `
            <div class="row-between">
                <h3>Proyecto ${index + 1}</h3>
                <button type="button" class="remove" data-action="remove">Eliminar</button>
            </div>
            <label>Nombre de proyecto
                <input data-field="name" type="text" value="${escapeHtml(project.name || "")}">
            </label>
            <label class="switch-row"><input data-field="enabled" type="checkbox" ${project.enabled ? "checked" : ""}> Enabled</label>
            <label>Supported work item types (coma o linea)
                <textarea data-field="supportedWorkItemTypes">${escapeHtml((project.supportedWorkItemTypes || []).join("\n"))}</textarea>
            </label>
            <div class="grid-2">
                <label>State design
                    <input data-field="states.design" type="text" value="${escapeHtml(project.states.design || "")}">
                </label>
                <label>State in-review
                    <input data-field="states.inReview" type="text" value="${escapeHtml(project.states.inReview || "")}">
                </label>
            </div>
            <label>State approved
                <input data-field="states.approved" type="text" value="${escapeHtml(project.states.approved || "")}">
            </label>
            <div class="grid-2">
                <label>Field approved-by-sme
                    <input data-field="fields.approvedBySme" type="text" value="${escapeHtml(project.fields.approvedBySme || "")}">
                </label>
                <label>Field approved-by-sqa
                    <input data-field="fields.approvedBySqa" type="text" value="${escapeHtml(project.fields.approvedBySqa || "")}">
                </label>
            </div>
            <label>Reversible business fields (coma o linea)
                <textarea data-field="fields.reversibleBusinessFields">${escapeHtml((project.fields.reversibleBusinessFields || []).join("\n"))}</textarea>
            </label>
            <div class="grid-2">
                <label>SME users (coma o linea)
                    <textarea data-field="approvals.smeUsers">${escapeHtml((project.approvals.smeUsers || []).join("\n"))}</textarea>
                </label>
                <label>SQA users (coma o linea)
                    <textarea data-field="approvals.sqaUsers">${escapeHtml((project.approvals.sqaUsers || []).join("\n"))}</textarea>
                </label>
            </div>
        `;

        card.addEventListener("input", (event) => {
            const field = event.target.getAttribute("data-field");
            if (!field) {
                return;
            }

            lastPreview = null;
            saveBtn.disabled = true;
            validationSummaryEl.innerHTML = "";

            if (field === "enabled") {
                project.enabled = event.target.checked;
                return;
            }

            if (field === "supportedWorkItemTypes") {
                project.supportedWorkItemTypes = splitLines(event.target.value);
                return;
            }
            if (field === "fields.reversibleBusinessFields") {
                project.fields.reversibleBusinessFields = splitLines(event.target.value);
                return;
            }
            if (field === "approvals.smeUsers") {
                project.approvals.smeUsers = splitLines(event.target.value);
                return;
            }
            if (field === "approvals.sqaUsers") {
                project.approvals.sqaUsers = splitLines(event.target.value);
                return;
            }

            const parts = field.split(".");
            if (parts.length === 1) {
                project[parts[0]] = event.target.value;
            } else {
                project[parts[0]][parts[1]] = event.target.value;
            }
        });

        card.querySelector("[data-action='remove']").addEventListener("click", () => {
            state.ado.projects.splice(index, 1);
            lastPreview = null;
            saveBtn.disabled = true;
            renderProjects();
        });

        projectsEl.appendChild(card);
    });
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

async function postConfig(url) {
    readFormToState();
    const response = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(state)
    });
    const payload = await response.json();
    if (!response.ok) {
        throw new Error(payload.error || "Error inesperado");
    }
    return payload;
}

async function initialize() {
    setStatus("Cargando configuracion...");
    const response = await fetch("/api/config-ui/model");
    state = await response.json();
    if (!Array.isArray(state.ado.projects)) {
        state.ado.projects = [];
    }
    fillFormFromState();
    saveBtn.disabled = true;
    setStatus("Configuracion cargada. Genera un preview para validar.");
}

document.getElementById("addProject").addEventListener("click", () => {
    state.ado.projects.push(createProjectModel());
    renderProjects();
});

document.getElementById("previewBtn").addEventListener("click", async () => {
    try {
        const payload = await postConfig("/api/config-ui/preview");
        yamlOutputEl.textContent = payload.yaml || "";
        renderValidation(payload);
        if (payload.draftYamlAvailable) {
            setStatus(payload.finalYamlAllowed ? "YAML final permitido." : "YAML de borrador generado; faltan validaciones ADO.");
        } else {
            setStatus("Errores bloqueantes: no se genero YAML.", true);
        }
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
