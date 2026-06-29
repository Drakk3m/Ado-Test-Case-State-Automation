const statusEl = document.getElementById("status");
const yamlOutputEl = document.getElementById("yamlOutput");
const projectsEl = document.getElementById("projects");
const validationSummaryEl = document.getElementById("validationSummary");
const saveBtn = document.getElementById("saveBtn");
const diagnosticsPanelEl = document.getElementById("configUiDiagnosticsPanel");
const diagnosticsContentEl = document.getElementById("configUiDiagnosticsContent");
const languageSelectorEl = document.getElementById("languageSelector");

let state = { ado: { projects: [] } };
let lastPreview = null;
let previewTimer = null;
let projectOptionLookup = { status: "NOT_CHECKED", message: "", values: [] };
let projectDiscovery = new Map();
let discoveryRequestSequence = 0;
let selectorDiagnostics = {};
let identitySearchState = {};
let identitySearchTimers = {};
let identityOptionCache = {};
const identitySearchResultCache = new Map();
const IDENTITY_MIN_QUERY_LENGTH = 3;
const IDENTITY_SEARCH_DEBOUNCE_MS = 450;
const IDENTITY_CACHE_TTL_MS = 10 * 60 * 1000;
const IDENTITY_CACHE_MAX_ENTRIES = 50;
const IDENTITY_CACHE_USEFUL_RESULT_COUNT = 3;
const STRUCTURAL_DISCOVERY_TTL_MS = 10 * 60 * 1000;
const DUPLICATE_PROJECT_MESSAGE = "This project is already configured.";
let projectLayoutState = new Map();
let localProjectSequence = 0;
const LANGUAGE_STORAGE_KEY = "configUiLanguage";
let currentLanguage = localStorage.getItem(LANGUAGE_STORAGE_KEY) || "en";

const I18N = {
    en: {
        "language.label": "Language",
        "app.title": "Approval Bot configuration",
        "app.subtitle": "Prepare an <code>application-local.yml</code> draft and separate unchecked text from verified values.",
        "app.secretNote": "Secrets are not written to disk: they stay as <code>${ADO_PERSONAL_ACCESS_TOKEN:}</code> and <code>${ADO_WEBHOOK_SHARED_SECRET:}</code>.",
        "app.discoveryNote": "Read-only ADO validation loads projects, types, fields, and states when the PAT is available. Hot-load remains deferred: restart the service after YAML changes.",
        "section.ado": "ADO",
        "section.botWebhook": "Bot / Webhook",
        "section.projects": "Projects",
        "section.diagnostics": "Config UI diagnostics",
        "section.retry": "Retry",
        "section.idempotency": "Idempotency",
        "field.organization": "Organization",
        "field.httpClientEnabled": "HTTP client enabled",
        "field.dryRun": "Dry run",
        "field.botIdentityEmail": "Bot identity email",
        "field.webhookSecretEnabled": "Webhook shared secret enabled",
        "field.webhookHeader": "Webhook header",
        "field.maxAttempts": "Max attempts",
        "field.defaultBackoffSeconds": "Default backoff seconds",
        "field.respectRetryAfter": "Respect Retry-After",
        "field.idempotencyType": "Type",
        "field.sqlitePath": "SQLite path",
        "field.ttlHours": "TTL hours",
        "field.maxRecords": "Max records",
        "button.loadProjects": "Load Projects",
        "button.addProject": "+ Add project",
        "button.previewYaml": "Preview YAML",
        "button.validateConfig": "Validate generated config",
        "button.saveYaml": "Save application-local.yml",
        "button.verifyProject": "Verify Project",
        "button.loadFieldsStates": "Load fields and states",
        "button.edit": "Edit",
        "button.collapse": "Collapse",
        "button.remove": "Remove",
        "project.title": "Project: {name}",
        "project.fallbackName": "Project {number}",
        "project.noWorkItemType": "No Work Item Type selected",
        "project.workItemTypeCount": "Work Item Types: {count}",
        "project.fieldCount": "{count} field{plural}",
        "project.userCount": "{count} user{plural}",
        "project.projectLabel": "Project",
        "project.enabled": "Enabled",
        "project.workItemType": "Work Item Type",
        "project.stateDesign": "Design state",
        "project.stateInReview": "In-review state",
        "project.stateApproved": "Approved state",
        "project.fieldApprovedBySme": "SME approval field",
        "project.fieldApprovedBySqa": "SQA approval field",
        "project.reversibleBusinessFields": "Reversible business fields",
        "identity.smeUsers": "SME users",
        "identity.sqaUsers": "SQA users",
        "identity.searchPlaceholder": "Search by email, login, or name",
        "identity.selectionNote": "Display names are shown for selection only. YAML stores normalized email/login values.",
        "identity.pendingNone": "Select a resolved identity from the search results.",
        "identity.pendingUnresolved": "Selected result has no email/login and cannot be added.",
        "identity.noResults": "No matching users yet.",
        "identity.searching": "Searching...",
        "identity.typeToSearch": "Type at least 3 characters to search ADO identities.",
        "diagnostics.visibleWhenDebug": "Visible only when config UI debug is enabled.",
        "diagnostics.description": "Selector diagnostics show the discovered options normalized and rendered into real selector controls.",
        "diagnostics.noItems": "No diagnostics captured yet.",
        "diagnostics.items": "{count} item{plural}",
        "diagnostics.projects": "Projects",
        "diagnostics.workItemTypes": "Work Item Types",
        "diagnostics.fields": "Fields",
        "diagnostics.identities": "Identities",
        "diagnostics.states": "States",
        "diagnostics.yamlValidation": "YAML/Validation",
        "diagnostics.decision": "decision",
        "diagnostics.updated": "updated",
        "diagnostics.enabled": "enabled",
        "diagnostics.disabled": "disabled",
        "diagnostics.discoveredProjects": "Discovered projects",
        "diagnostics.discoveredProjectsNote": "These are the same discovered project options rendered by the Project selector.",
        "diagnostics.noProjectsRendered": "No projects rendered.",
        "validation.heading": "Validation",
        "validation.finalAllowed": "Final YAML allowed.",
        "validation.finalBlocked": "Final YAML blocked until errors, Not checked values, and current ADO selector verification are resolved.",
        "status.loading": "Loading configuration...",
        "status.loaded": "Configuration loaded. Run ADO discovery to populate selectors.",
        "status.draftAllowed": "Final YAML allowed.",
        "status.draftBlocked": "Draft YAML generated; ADO validations are still missing.",
        "status.yamlBlocked": "Blocking errors: YAML was not generated.",
        "status.resolveBeforeCollapse": "Resolve project validation before collapsing this section.",
        "status.saveBlocked": "Verify project and select current ADO-backed values before saving final YAML.",
        "message.staleIgnored": "Stale response ignored.",
        "message.staleIgnoredReason": "Stale response ignored: {reason}.",
        "message.projectNotLoaded": "Project has not been loaded.",
        "message.loadProjectFirst": "Load a project first.",
        "message.selectWorkItemTypeFirst": "Select a Work Item type first.",
        "message.projectSelectionChanged": "Project selection changed.",
        "message.loadProjectAgain": "Load the project again.",
        "message.workItemTypeChanged": "Work Item type changed.",
        "message.clearedProject": "Cleared after Project selection changed.",
        "message.clearedWorkItemType": "Cleared after Work Item Type changed.",
        "message.verifyBeforeType": "Verify the project before selecting a Work Item type.",
        "message.verifyProjectFirst": "Verify the project first.",
        "message.verifyBeforeUsers": "Verify the project before searching users.",
        "message.noProjects": "No projects were returned for the configured organization.",
        "message.noWorkItemTypes": "No Work Item Types were returned for the verified project.",
        "message.noFields": "No fields were returned for the selected Work Item Type.",
        "message.noStates": "No states were returned for the selected Work Item Type.",
        "message.noIdentities": "No selectable ADO identities were returned for the search.",
        "message.projectSelectorRendered": "Project selector renders all discovered project options.",
        "message.organizationChanged": "Organization changed; reload projects.",
        "message.adoDiscoveryError": "ADO discovery returned an error.",
        "message.adoDiscoveryUnchecked": "ADO discovery was not fully checked.",
        "message.adoDiscoveryRequestFailed": "ADO discovery request failed.",
        "message.invalidJson": "Request failed because the response was not valid JSON. HTTP {status}.",
        "message.httpFailed": "Request failed with HTTP {status}.",
        "message.selectorCountMismatch": "Backend optionCount differs from received or normalized option count.",
        "message.selectorEmptyButCount": "Backend optionCount was greater than zero, but no renderable options were found.",
        "message.manualUncheckedSuffix": "unchecked/manual",
        "message.sameApprovalFields": "SME and SQA approval fields must be different.",
        "message.duplicateReversibleField": "Duplicate reversible field: {field}.",
        "message.smeFieldAlsoReversible": "SME approval field cannot also be reversible.",
        "message.sqaFieldAlsoReversible": "SQA approval field cannot also be reversible.",
        "message.duplicateIdentity": "{role} users contain duplicate identity: {identity}.",
        "message.crossRoleIdentity": "Same identity appears in both SME and SQA lists.",
        "message.duplicateProjectSelection": "That project is already selected in another card."
    },
    fr: {
        "language.label": "Langue",
        "app.title": "Configuration Approval Bot",
        "app.subtitle": "Préparez un brouillon <code>application-local.yml</code> et séparez le texte non vérifié des valeurs vérifiées.",
        "app.secretNote": "Les secrets ne sont pas écrits sur disque : ils restent sous la forme <code>${ADO_PERSONAL_ACCESS_TOKEN:}</code> et <code>${ADO_WEBHOOK_SHARED_SECRET:}</code>.",
        "app.discoveryNote": "La validation ADO en lecture seule charge les projets, types, champs et états lorsque le PAT est disponible. Le rechargement à chaud reste différé : redémarrez le service après les changements YAML.",
        "section.ado": "ADO",
        "section.botWebhook": "Bot / Webhook",
        "section.projects": "Projets",
        "section.diagnostics": "Diagnostics de l'UI de configuration",
        "section.retry": "Nouvelle tentative",
        "section.idempotency": "Idempotence",
        "field.organization": "Organisation",
        "field.httpClientEnabled": "Client HTTP activé",
        "field.dryRun": "Mode simulation",
        "field.botIdentityEmail": "E-mail d'identité du bot",
        "field.webhookSecretEnabled": "Secret partagé du webhook activé",
        "field.webhookHeader": "En-tête webhook",
        "field.maxAttempts": "Nombre maximal de tentatives",
        "field.defaultBackoffSeconds": "Attente par défaut en secondes",
        "field.respectRetryAfter": "Respecter Retry-After",
        "field.idempotencyType": "Type",
        "field.sqlitePath": "Chemin SQLite",
        "field.ttlHours": "TTL en heures",
        "field.maxRecords": "Nombre maximal d'enregistrements",
        "button.loadProjects": "Charger les projets",
        "button.addProject": "+ Ajouter un projet",
        "button.previewYaml": "Prévisualiser YAML",
        "button.validateConfig": "Valider la configuration générée",
        "button.saveYaml": "Enregistrer application-local.yml",
        "button.verifyProject": "Vérifier le projet",
        "button.loadFieldsStates": "Charger les champs et les états",
        "button.edit": "Modifier",
        "button.collapse": "Réduire",
        "button.remove": "Supprimer",
        "project.title": "Projet : {name}",
        "project.fallbackName": "Projet {number}",
        "project.noWorkItemType": "Aucun type de Work Item sélectionné",
        "project.workItemTypeCount": "Types de Work Item : {count}",
        "project.fieldCount": "{count} champ{plural}",
        "project.userCount": "{count} utilisateur{plural}",
        "project.projectLabel": "Projet",
        "project.enabled": "Activé",
        "project.workItemType": "Type de Work Item",
        "project.stateDesign": "État de conception",
        "project.stateInReview": "État en revue",
        "project.stateApproved": "État approuvé",
        "project.fieldApprovedBySme": "Champ d'approbation SME",
        "project.fieldApprovedBySqa": "Champ d'approbation SQA",
        "project.reversibleBusinessFields": "Champs métier réversibles",
        "identity.smeUsers": "Utilisateurs SME",
        "identity.sqaUsers": "Utilisateurs SQA",
        "identity.searchPlaceholder": "Rechercher par e-mail, login ou nom",
        "identity.selectionNote": "Les noms affichés servent uniquement à la sélection. Le YAML stocke les e-mails/logins normalisés.",
        "identity.pendingNone": "Sélectionnez une identité résolue dans les résultats.",
        "identity.pendingUnresolved": "Le résultat sélectionné n'a pas d'e-mail/login et ne peut pas être ajouté.",
        "identity.noResults": "Aucun utilisateur correspondant pour le moment.",
        "identity.searching": "Recherche...",
        "identity.typeToSearch": "Saisissez au moins 3 caractères pour rechercher des identités ADO.",
        "diagnostics.visibleWhenDebug": "Visible uniquement quand le débogage de l'UI de configuration est activé.",
        "diagnostics.description": "Les diagnostics des sélecteurs montrent les options découvertes, normalisées et rendues dans de vrais sélecteurs.",
        "diagnostics.noItems": "Aucun diagnostic capturé pour le moment.",
        "diagnostics.items": "{count} élément{plural}",
        "diagnostics.projects": "Projets",
        "diagnostics.workItemTypes": "Types de Work Item",
        "diagnostics.fields": "Champs",
        "diagnostics.identities": "Identités",
        "diagnostics.states": "États",
        "diagnostics.yamlValidation": "YAML/Validation",
        "diagnostics.decision": "décision",
        "diagnostics.updated": "mis à jour",
        "diagnostics.enabled": "activé",
        "diagnostics.disabled": "désactivé",
        "diagnostics.discoveredProjects": "Projets découverts",
        "diagnostics.discoveredProjectsNote": "Ce sont les mêmes options de projet découvertes que celles rendues par le sélecteur de projet.",
        "diagnostics.noProjectsRendered": "Aucun projet rendu.",
        "validation.heading": "Validation",
        "validation.finalAllowed": "YAML final autorisé.",
        "validation.finalBlocked": "YAML final bloqué jusqu'à résolution des erreurs, valeurs non vérifiées et validations ADO courantes.",
        "status.loading": "Chargement de la configuration...",
        "status.loaded": "Configuration chargée. Lancez la découverte ADO pour remplir les sélecteurs.",
        "status.draftAllowed": "YAML final autorisé.",
        "status.draftBlocked": "Brouillon YAML généré ; des validations ADO manquent encore.",
        "status.yamlBlocked": "Erreurs bloquantes : aucun YAML généré.",
        "status.resolveBeforeCollapse": "Résolvez la validation du projet avant de réduire cette section.",
        "status.saveBlocked": "Vérifiez le projet et sélectionnez des valeurs ADO courantes avant d'enregistrer le YAML final.",
        "message.staleIgnored": "Réponse obsolète ignorée.",
        "message.staleIgnoredReason": "Réponse obsolète ignorée : {reason}.",
        "message.projectNotLoaded": "Le projet n'a pas été chargé.",
        "message.loadProjectFirst": "Chargez d'abord un projet.",
        "message.selectWorkItemTypeFirst": "Sélectionnez d'abord un type de Work Item.",
        "message.projectSelectionChanged": "La sélection du projet a changé.",
        "message.loadProjectAgain": "Rechargez le projet.",
        "message.workItemTypeChanged": "Le type de Work Item a changé.",
        "message.clearedProject": "Effacé après changement de sélection du projet.",
        "message.clearedWorkItemType": "Effacé après changement de type de Work Item.",
        "message.verifyBeforeType": "Vérifiez le projet avant de sélectionner un type de Work Item.",
        "message.verifyProjectFirst": "Vérifiez d'abord le projet.",
        "message.verifyBeforeUsers": "Vérifiez le projet avant de rechercher des utilisateurs.",
        "message.noProjects": "Aucun projet n'a été retourné pour l'organisation configurée.",
        "message.noWorkItemTypes": "Aucun type de Work Item n'a été retourné pour le projet vérifié.",
        "message.noFields": "Aucun champ n'a été retourné pour le type de Work Item sélectionné.",
        "message.noStates": "Aucun état n'a été retourné pour le type de Work Item sélectionné.",
        "message.noIdentities": "Aucune identité ADO sélectionnable n'a été retournée par la recherche.",
        "message.projectSelectorRendered": "Le sélecteur de projet rend toutes les options de projet découvertes.",
        "message.organizationChanged": "L'organisation a changé ; rechargez les projets.",
        "message.adoDiscoveryError": "La découverte ADO a retourné une erreur.",
        "message.adoDiscoveryUnchecked": "La découverte ADO n'a pas été entièrement vérifiée.",
        "message.adoDiscoveryRequestFailed": "La requête de découverte ADO a échoué.",
        "message.invalidJson": "La requête a échoué car la réponse n'était pas du JSON valide. HTTP {status}.",
        "message.httpFailed": "La requête a échoué avec HTTP {status}.",
        "message.selectorCountMismatch": "Le compteur backend diffère du nombre d'options reçues ou normalisées.",
        "message.selectorEmptyButCount": "Le compteur backend était supérieur à zéro, mais aucune option rendable n'a été trouvée.",
        "message.manualUncheckedSuffix": "non vérifié/manuel",
        "message.sameApprovalFields": "Les champs d'approbation SME et SQA doivent être différents.",
        "message.duplicateReversibleField": "Champ réversible dupliqué : {field}.",
        "message.smeFieldAlsoReversible": "Le champ d'approbation SME ne peut pas aussi être réversible.",
        "message.sqaFieldAlsoReversible": "Le champ d'approbation SQA ne peut pas aussi être réversible.",
        "message.duplicateIdentity": "Les utilisateurs {role} contiennent une identité dupliquée : {identity}.",
        "message.crossRoleIdentity": "La même identité apparaît dans les listes SME et SQA.",
        "message.duplicateProjectSelection": "Ce projet est déjà sélectionné dans une autre carte."
    },
    es: {
        "language.label": "Idioma",
        "app.title": "Configuración de Approval Bot",
        "app.subtitle": "Prepara un borrador de <code>application-local.yml</code> y separa texto no verificado de valores verificados.",
        "app.secretNote": "Los secretos no se escriben en disco: se mantienen como <code>${ADO_PERSONAL_ACCESS_TOKEN:}</code> y <code>${ADO_WEBHOOK_SHARED_SECRET:}</code>.",
        "app.discoveryNote": "La validación ADO de solo lectura carga proyectos, tipos, campos y estados cuando el PAT está disponible. Hot-load queda diferido: reinicia el servicio después de cambios YAML.",
        "section.ado": "ADO",
        "section.botWebhook": "Bot / Webhook",
        "section.projects": "Proyectos",
        "section.diagnostics": "Diagnósticos del Config UI",
        "section.retry": "Reintentos",
        "section.idempotency": "Idempotencia",
        "field.organization": "Organización",
        "field.httpClientEnabled": "Cliente HTTP habilitado",
        "field.dryRun": "Modo dry-run",
        "field.botIdentityEmail": "Email de identidad del bot",
        "field.webhookSecretEnabled": "Secreto compartido del webhook habilitado",
        "field.webhookHeader": "Header del webhook",
        "field.maxAttempts": "Intentos máximos",
        "field.defaultBackoffSeconds": "Espera predeterminada en segundos",
        "field.respectRetryAfter": "Respetar Retry-After",
        "field.idempotencyType": "Tipo",
        "field.sqlitePath": "Ruta SQLite",
        "field.ttlHours": "TTL en horas",
        "field.maxRecords": "Registros máximos",
        "button.loadProjects": "Cargar proyectos",
        "button.addProject": "+ Agregar proyecto",
        "button.previewYaml": "Previsualizar YAML",
        "button.validateConfig": "Validar configuración generada",
        "button.saveYaml": "Guardar application-local.yml",
        "button.verifyProject": "Verificar proyecto",
        "button.loadFieldsStates": "Cargar campos y estados",
        "button.edit": "Editar",
        "button.collapse": "Colapsar",
        "button.remove": "Eliminar",
        "project.title": "Proyecto: {name}",
        "project.fallbackName": "Proyecto {number}",
        "project.noWorkItemType": "No hay Work Item Type seleccionado",
        "project.workItemTypeCount": "Work Item Types: {count}",
        "project.fieldCount": "{count} campo{plural}",
        "project.userCount": "{count} usuario{plural}",
        "project.projectLabel": "Proyecto",
        "project.enabled": "Habilitado",
        "project.workItemType": "Work Item Type",
        "project.stateDesign": "Estado de diseño",
        "project.stateInReview": "Estado en revisión",
        "project.stateApproved": "Estado aprobado",
        "project.fieldApprovedBySme": "Campo de aprobación SME",
        "project.fieldApprovedBySqa": "Campo de aprobación SQA",
        "project.reversibleBusinessFields": "Campos de negocio reversibles",
        "identity.smeUsers": "Usuarios SME",
        "identity.sqaUsers": "Usuarios SQA",
        "identity.searchPlaceholder": "Buscar por email, login o nombre",
        "identity.selectionNote": "Los nombres visibles sólo se muestran para selección. YAML guarda emails/logins normalizados.",
        "identity.pendingNone": "Selecciona una identidad resuelta desde los resultados.",
        "identity.pendingUnresolved": "El resultado seleccionado no tiene email/login y no puede agregarse.",
        "identity.noResults": "Aún no hay usuarios coincidentes.",
        "identity.searching": "Buscando...",
        "identity.typeToSearch": "Escribe al menos 3 caracteres para buscar identidades ADO.",
        "diagnostics.visibleWhenDebug": "Visible sólo cuando el debug del Config UI está habilitado.",
        "diagnostics.description": "Los diagnósticos de selectores muestran las opciones descubiertas, normalizadas y renderizadas en controles reales.",
        "diagnostics.noItems": "Aún no hay diagnósticos capturados.",
        "diagnostics.items": "{count} elemento{plural}",
        "diagnostics.projects": "Proyectos",
        "diagnostics.workItemTypes": "Work Item Types",
        "diagnostics.fields": "Campos",
        "diagnostics.identities": "Identidades",
        "diagnostics.states": "Estados",
        "diagnostics.yamlValidation": "YAML/Validación",
        "diagnostics.decision": "decisión",
        "diagnostics.updated": "actualizado",
        "diagnostics.enabled": "habilitado",
        "diagnostics.disabled": "deshabilitado",
        "diagnostics.discoveredProjects": "Proyectos descubiertos",
        "diagnostics.discoveredProjectsNote": "Estas son las mismas opciones de proyecto descubiertas que renderiza el selector de proyecto.",
        "diagnostics.noProjectsRendered": "No hay proyectos renderizados.",
        "validation.heading": "Validación",
        "validation.finalAllowed": "YAML final permitido.",
        "validation.finalBlocked": "YAML final bloqueado hasta resolver errores, valores no verificados y validación ADO actual.",
        "status.loading": "Cargando configuración...",
        "status.loaded": "Configuración cargada. Ejecuta ADO discovery para poblar selectores.",
        "status.draftAllowed": "YAML final permitido.",
        "status.draftBlocked": "Borrador YAML generado; aún faltan validaciones ADO.",
        "status.yamlBlocked": "Errores bloqueantes: no se generó YAML.",
        "status.resolveBeforeCollapse": "Resuelve la validación del proyecto antes de colapsar esta sección.",
        "status.saveBlocked": "Verifica el proyecto y selecciona valores ADO actuales antes de guardar el YAML final.",
        "message.staleIgnored": "Respuesta obsoleta ignorada.",
        "message.staleIgnoredReason": "Respuesta obsoleta ignorada: {reason}.",
        "message.projectNotLoaded": "El proyecto no se ha cargado.",
        "message.loadProjectFirst": "Carga un proyecto primero.",
        "message.selectWorkItemTypeFirst": "Selecciona primero un Work Item Type.",
        "message.projectSelectionChanged": "La selección de proyecto cambió.",
        "message.loadProjectAgain": "Carga el proyecto otra vez.",
        "message.workItemTypeChanged": "El Work Item Type cambió.",
        "message.clearedProject": "Limpiado después de cambiar la selección de proyecto.",
        "message.clearedWorkItemType": "Limpiado después de cambiar el Work Item Type.",
        "message.verifyBeforeType": "Verifica el proyecto antes de seleccionar un Work Item Type.",
        "message.verifyProjectFirst": "Verifica el proyecto primero.",
        "message.verifyBeforeUsers": "Verifica el proyecto antes de buscar usuarios.",
        "message.noProjects": "No se devolvieron proyectos para la organización configurada.",
        "message.noWorkItemTypes": "No se devolvieron Work Item Types para el proyecto verificado.",
        "message.noFields": "No se devolvieron campos para el Work Item Type seleccionado.",
        "message.noStates": "No se devolvieron estados para el Work Item Type seleccionado.",
        "message.noIdentities": "La búsqueda no devolvió identidades ADO seleccionables.",
        "message.projectSelectorRendered": "El selector de proyecto renderiza todas las opciones descubiertas.",
        "message.organizationChanged": "La organización cambió; recarga proyectos.",
        "message.adoDiscoveryError": "ADO discovery devolvió un error.",
        "message.adoDiscoveryUnchecked": "ADO discovery no se verificó completamente.",
        "message.adoDiscoveryRequestFailed": "La solicitud de ADO discovery falló.",
        "message.invalidJson": "La solicitud falló porque la respuesta no era JSON válido. HTTP {status}.",
        "message.httpFailed": "La solicitud falló con HTTP {status}.",
        "message.selectorCountMismatch": "El optionCount del backend difiere de las opciones recibidas o normalizadas.",
        "message.selectorEmptyButCount": "El optionCount del backend era mayor que cero, pero no se encontraron opciones renderizables.",
        "message.manualUncheckedSuffix": "no verificado/manual",
        "message.sameApprovalFields": "Los campos de aprobación SME y SQA deben ser diferentes.",
        "message.duplicateReversibleField": "Campo reversible duplicado: {field}.",
        "message.smeFieldAlsoReversible": "El campo de aprobación SME no puede ser reversible también.",
        "message.sqaFieldAlsoReversible": "El campo de aprobación SQA no puede ser reversible también.",
        "message.duplicateIdentity": "Los usuarios {role} contienen una identidad duplicada: {identity}.",
        "message.crossRoleIdentity": "La misma identidad aparece en las listas SME y SQA.",
        "message.duplicateProjectSelection": "Ese proyecto ya está seleccionado en otra tarjeta."
    }
};
if (!I18N[currentLanguage]) {
    currentLanguage = "en";
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");
}

function interpolate(template, values = {}) {
    return String(template ?? "").replace(/\{(\w+)}/g, (_, key) => values[key] ?? "");
}

function t(key, values = {}) {
    const dictionary = I18N[currentLanguage] || I18N.en;
    return interpolate(dictionary[key] ?? I18N.en[key] ?? key, values);
}

function localizeMessage(message) {
    if (!message) {
        return "";
    }
    return t(message, {}) === message ? message : t(message);
}

function applyStaticTranslations() {
    document.documentElement.lang = currentLanguage;
    if (languageSelectorEl) {
        languageSelectorEl.value = currentLanguage;
    }
    for (const element of document.querySelectorAll("[data-i18n]")) {
        element.textContent = t(element.getAttribute("data-i18n"));
    }
    for (const element of document.querySelectorAll("[data-i18n-html]")) {
        element.innerHTML = t(element.getAttribute("data-i18n-html"));
    }
}

function setLanguage(language) {
    currentLanguage = I18N[language] ? language : "en";
    localStorage.setItem(LANGUAGE_STORAGE_KEY, currentLanguage);
    readFormToState();
    applyStaticTranslations();
    renderProjectSelectors();
    renderProjects();
    renderDiagnosticsPanel();
    if (lastPreview) {
        renderValidation(lastPreview);
    }
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

function diagnosticKey(selectorName, projectConfigId = "") {
    return projectConfigId ? `${projectConfigId}:${selectorName}` : selectorName;
}

function ensureSelectorDiagnostic(selectorName, projectConfigId = "") {
    const key = diagnosticKey(selectorName, projectConfigId);
    if (!selectorDiagnostics[key]) {
        selectorDiagnostics[key] = {
            selector: selectorName,
            projectConfigId,
            selectedProjectName: "",
            normalizedProjectName: "",
            duplicateProjectStatus: false,
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
            pendingIdentityStatus: "",
            selectedCount: "",
            unresolvedCount: "",
            identityWarnings: "",
            debouncePending: false,
            frontendCacheHit: false,
            frontendCacheHits: 0,
            frontendCacheMisses: 0,
            backendCacheHit: false,
            backendCacheMiss: false,
            frontendRequestCount: 0,
            backendRequestCount: 0,
            adoRequestCount: 0,
            adoDiscoveryRequestCount: 0,
            lastDiscoveryOperation: "",
            discoveryCacheHit: false,
            projectMetadataCacheHit: false,
            processIdCacheHit: false,
            workItemTypeOptionsCacheHit: false,
            fieldOptionsCacheHit: false,
            stateOptionsCacheHit: false,
            processFailureCacheHit: false,
            frontendValidateProjectCallCount: 0,
            frontendLoadWitCallCount: 0,
            frontendLoadFieldsCallCount: 0,
            frontendLoadStatesCallCount: 0,
            inFlightDedupedCount: 0,
            skippedBecauseCurrentCount: 0,
            structuralDiscoverySuppressedCount: 0,
            lastStructuralDiscoveryReason: "",
            lastStructuralDiscoveryDependencyKey: "",
            localValidationRunCount: 0,
            strictValidationRunCount: 0,
            strictValidationSkippedDuringEditCount: 0,
            yamlPreviewLocalOnlyCount: 0,
            backendStrictValidationCallCount: 0,
            lastStrictValidationTrigger: "",
            lastStrictValidationAt: "",
            candidatePoolSource: "",
            candidatePoolSize: 0,
            candidatePoolCacheHit: false,
            projectPoolMatchCount: 0,
            graphFallbackAttempted: false,
            graphCacheHit: false,
            graphNegativeCacheHit: false,
            avatarCacheHitCount: 0,
            avatarCacheMissCount: 0,
            avatarAdoRequestCount: 0,
            enabled: false,
            message: "",
            staleIgnoredCount: 0,
            lastUpdated: ""
        };
    }
    return selectorDiagnostics[key];
}

function updateSelectorDiagnostics(selectorName, updates = {}, projectConfigId = "") {
    const key = diagnosticKey(selectorName, projectConfigId);
    const current = ensureSelectorDiagnostic(selectorName, projectConfigId);
    selectorDiagnostics[key] = {
        ...current,
        ...safeDiscoveryDetails(updates),
        lastUpdated: nowTimestamp()
    };
    renderDiagnosticsPanel();
}

function incrementStaleIgnored(selectorName, reason, projectConfigId = "") {
    const current = ensureSelectorDiagnostic(selectorName, projectConfigId);
    updateSelectorDiagnostics(selectorName, {
        staleIgnoredCount: current.staleIgnoredCount + 1,
        message: reason ? t("message.staleIgnoredReason", { reason }) : t("message.staleIgnored")
    }, projectConfigId);
}

function renderDiagnosticsPanel() {
    const debugEnabled = isConfigUiDebugEnabled();
    if (diagnosticsPanelEl) {
        diagnosticsPanelEl.hidden = !debugEnabled;
    }
    if (!debugEnabled || !diagnosticsContentEl) {
        return;
    }
    const groups = diagnosticGroups();
    diagnosticsContentEl.innerHTML = `<div class="diagnostics-groups">${groups.map((group) => diagnosticGroupMarkup(group)).join("")}</div>`;
}

function diagnosticGroups() {
    const diagnostics = Object.values(selectorDiagnostics)
        .sort((left, right) => left.selector.localeCompare(right.selector));
    const projectGroups = state.ado.projects.map((project, index) => {
        const projectConfigId = ensureProjectConfigId(project);
        return {
            title: `${projectDisplayName(project, index)} (${projectConfigId})`,
            selectors: [],
            items: diagnostics.filter((item) => item.projectConfigId === projectConfigId)
        };
    });
    const globalDiagnostics = diagnostics.filter((item) => !item.projectConfigId);
    const groups = [
        { titleKey: "diagnostics.projects", selectors: ["project"], items: [] },
        { titleKey: "diagnostics.workItemTypes", selectors: ["workItemType"], items: [] },
        { titleKey: "diagnostics.fields", selectors: ["approvedBySmeField", "approvedBySqaField", "reversibleBusinessFields"], items: [] },
        { titleKey: "diagnostics.identities", selectors: ["smeUsers", "sqaUsers"], items: [] },
        { titleKey: "diagnostics.states", selectors: ["designState", "inReviewState", "approvedState"], items: [] },
        { titleKey: "diagnostics.yamlValidation", selectors: [], items: [] }
    ];
    for (const item of globalDiagnostics) {
        const group = groups.find((candidate) => candidate.selectors.includes(item.selector)) || groups[groups.length - 1];
        group.items.push(item);
    }
    return [...projectGroups, ...groups];
}

function diagnosticGroupMarkup(group) {
    const rows = group.items.map((item) => diagnosticItemMarkup(item)).join("");
    return `
        <details class="diagnostic-group" open>
            <summary>
                <strong>${escapeHtml(group.title || t(group.titleKey))}</strong>
                <span>${escapeHtml(t("diagnostics.items", { count: group.items.length, plural: group.items.length === 1 ? "" : "s" }))}</span>
            </summary>
            <div class="diagnostic-grid">
                ${rows || `<p class="note compact">${escapeHtml(t("diagnostics.noItems"))}</p>`}
            </div>
        </details>
    `;
}

function diagnosticItemMarkup(item) {
    const metrics = [
        ["projectConfigId", item.projectConfigId],
        ["selected project", item.selectedProjectName],
        ["normalized project", item.normalizedProjectName],
        ["duplicate project", item.duplicateProjectStatus],
        ["backend optionCount", item.backendOptionCount],
        ["received length", item.receivedLength],
        ["normalized length", item.normalizedLength],
        ["rendered count", item.renderedOptionCount],
        ["DOM options", item.domOptionCount],
        ["raw fields", item.rawFieldCount],
        ["approval fields", item.approvalFieldCount],
        ["reversible fields", item.reversibleFieldCount],
        ["duplicate errors", item.duplicateErrors],
        ["query length", item.lastQueryLength],
        ["user results", item.resultCount],
        ["pending identity", item.pendingIdentityStatus],
        ["selected users", item.selectedCount],
        ["unresolved users", item.unresolvedCount],
        ["identity warnings", item.identityWarnings],
        ["debounce pending", item.debouncePending],
        ["frontend cache hit", item.frontendCacheHit],
        ["frontend cache hits", item.frontendCacheHits],
        ["frontend cache misses", item.frontendCacheMisses],
        ["backend cache hit", item.backendCacheHit],
        ["backend cache miss", item.backendCacheMiss],
        ["frontend requests", item.frontendRequestCount],
        ["backend requests", item.backendRequestCount],
        ["ADO requests", item.adoRequestCount],
        ["ADO discovery requests", item.adoDiscoveryRequestCount],
        ["last discovery operation", item.lastDiscoveryOperation],
        ["discovery cache hit", item.discoveryCacheHit],
        ["project metadata cache hit", item.projectMetadataCacheHit],
        ["process id cache hit", item.processIdCacheHit],
        ["Work Item Type cache hit", item.workItemTypeOptionsCacheHit],
        ["field cache hit", item.fieldOptionsCacheHit],
        ["state cache hit", item.stateOptionsCacheHit],
        ["process failure cache hit", item.processFailureCacheHit],
        ["frontend validate calls", item.frontendValidateProjectCallCount],
        ["frontend WIT calls", item.frontendLoadWitCallCount],
        ["frontend field calls", item.frontendLoadFieldsCallCount],
        ["frontend state calls", item.frontendLoadStatesCallCount],
        ["in-flight deduped", item.inFlightDedupedCount],
        ["skipped because current", item.skippedBecauseCurrentCount],
        ["structural discovery suppressed", item.structuralDiscoverySuppressedCount],
        ["last structural reason", item.lastStructuralDiscoveryReason],
        ["last structural dependency", item.lastStructuralDiscoveryDependencyKey],
        ["local validation runs", item.localValidationRunCount],
        ["strict validation runs", item.strictValidationRunCount],
        ["strict validation skipped during edit", item.strictValidationSkippedDuringEditCount],
        ["local-only YAML previews", item.yamlPreviewLocalOnlyCount],
        ["backend strict validation calls", item.backendStrictValidationCallCount],
        ["last strict validation trigger", item.lastStrictValidationTrigger],
        ["last strict validation at", item.lastStrictValidationAt],
        ["candidate source", item.candidatePoolSource],
        ["candidate pool size", item.candidatePoolSize],
        ["candidate pool cache hit", item.candidatePoolCacheHit],
        ["project pool matches", item.projectPoolMatchCount],
        ["Graph fallback attempted", item.graphFallbackAttempted],
        ["Graph cache hit", item.graphCacheHit],
        ["Graph negative cache hit", item.graphNegativeCacheHit],
        ["avatar cache hits", item.avatarCacheHitCount],
        ["avatar cache misses", item.avatarCacheMissCount],
        ["avatar ADO requests", item.avatarAdoRequestCount],
        ["stale ignored", item.staleIgnoredCount]
    ].filter((metric) => metric[1] !== "" && metric[1] !== null && metric[1] !== undefined);
    return `
        <section class="diagnostic-item">
            <div class="diagnostic-item-heading">
                <strong>${escapeHtml(item.selector)}</strong>
                ${validationBadge(item.status || "NOT_CHECKED")}
            </div>
            <dl>
                ${metrics.map(([label, value]) => `
                    <div>
                        <dt>${escapeHtml(label)}</dt>
                        <dd>${escapeHtml(value)}</dd>
                    </div>
                `).join("")}
                <div>
                    <dt>${escapeHtml(t("diagnostics.decision"))}</dt>
                    <dd>${item.enabled ? escapeHtml(t("diagnostics.enabled")) : escapeHtml(t("diagnostics.disabled"))}</dd>
                </div>
                <div>
                    <dt>${escapeHtml(t("diagnostics.updated"))}</dt>
                    <dd>${escapeHtml(item.lastUpdated)}</dd>
                </div>
            </dl>
            ${item.message ? `<p class="note compact">${escapeHtml(item.message)}</p>` : ""}
        </section>
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
        referenceName: String(item.referenceName ?? value).trim(),
        avatarUrl: String(item.avatarUrl ?? "").trim(),
        resolved: item.resolved !== false
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
        messages.push(t("message.sameApprovalFields"));
    }
    const seen = new Set();
    for (const field of reversible) {
        if (seen.has(field.normalized)) {
            messages.push(t("message.duplicateReversibleField", { field: field.value }));
        }
        seen.add(field.normalized);
        if (sme && field.normalized === sme) {
            messages.push(t("message.smeFieldAlsoReversible"));
        }
        if (sqa && field.normalized === sqa) {
            messages.push(t("message.sqaFieldAlsoReversible"));
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

function identityKey(projectConfigId, role) {
    return `${projectConfigId}:${role}`;
}

function ensureIdentitySearchState(projectConfigId, role) {
    const key = identityKey(projectConfigId, role);
    if (!identitySearchState[key]) {
        identitySearchState[key] = {
            query: "",
            lookup: { status: "NOT_CHECKED", message: t("identity.typeToSearch"), values: [], optionCount: 0 },
            pending: null,
            searching: false,
            debouncePending: false,
            requestVersion: 0,
            frontendCacheHits: 0,
            frontendCacheMisses: 0,
            frontendRequestCount: 0,
            backendRequestCount: 0,
            adoRequestCount: 0,
            backendCacheHit: false,
            backendCacheMiss: false,
            candidatePoolSource: "",
            candidatePoolSize: 0,
            candidatePoolCacheHit: false,
            projectPoolMatchCount: 0,
            graphFallbackAttempted: false,
            graphCacheHit: false,
            graphNegativeCacheHit: false,
            avatarCacheHitCount: 0,
            avatarCacheMissCount: 0,
            avatarAdoRequestCount: 0
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

function normalizedIdentityQuery(value) {
    return normalizedIdentity(value).replace(/\s+/g, " ");
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
                messages.push(t("message.duplicateIdentity", { role: role.toUpperCase(), identity: normalized }));
            }
            seen.add(normalized);
        }
    }
    const sqa = new Set((project.approvals.sqaUsers || []).map(normalizedIdentity));
    for (const smeUser of project.approvals.smeUsers || []) {
        const normalized = normalizedIdentity(smeUser);
        if (normalized && sqa.has(normalized)) {
            messages.push(t("message.crossRoleIdentity"));
            break;
        }
    }
    return messages;
}

function unresolvedIdentityCount(project, role) {
    return (roleUsers(project, role) || []).filter((user) => !isResolvableIdentityValue(user)).length;
}

function userInitials(option) {
    const label = identityDisplayName(option);
    const words = label.replace(/<[^>]+>/g, "").split(/\s+/).filter(Boolean);
    if (words.length === 0) {
        return "?";
    }
    return words.slice(0, 2).map((word) => word[0].toUpperCase()).join("");
}

function identityOption(optionOrValue) {
    return typeof optionOrValue === "string"
            ? identityOptionCache[normalizedIdentity(optionOrValue)] || { value: optionOrValue }
            : (optionOrValue || {});
}

function identityAvatarMarkup(optionOrValue) {
    const option = identityOption(optionOrValue);
    const initials = escapeHtml(userInitials(optionOrValue));
    if (!option.avatarUrl) {
        return `<span class="identity-avatar"><span class="identity-avatar-fallback">${initials}</span></span>`;
    }
    return `<span class="identity-avatar">
        <img data-identity-avatar src="${escapeHtml(option.avatarUrl)}" alt="" loading="lazy">
        <span class="identity-avatar-fallback" hidden>${initials}</span>
    </span>`;
}

function wireIdentityAvatarFallbacks(root) {
    for (const image of root?.querySelectorAll?.("[data-identity-avatar]") || []) {
        image.addEventListener("error", () => {
            image.hidden = true;
            const fallback = image.nextElementSibling;
            if (fallback) {
                fallback.hidden = false;
            }
        }, { once: true });
    }
}

function identityDisplayName(optionOrValue) {
    if (typeof optionOrValue === "string") {
        return identityOptionCache[normalizedIdentity(optionOrValue)]?.displayNameOnly || optionOrValue;
    }
    const option = optionOrValue || {};
    const value = option.value || "";
    const display = option.displayName || value;
    const bracketIndex = display.indexOf(" <");
    if (bracketIndex > 0) {
        return display.slice(0, bracketIndex);
    }
    return display || value;
}

function identityEmail(optionOrValue) {
    if (typeof optionOrValue === "string") {
        return normalizedIdentity(optionOrValue);
    }
    const option = optionOrValue || {};
    return normalizedIdentity(option.description || option.value);
}

function cacheIdentityOptions(options) {
    for (const option of options || []) {
        const value = normalizedIdentity(option.value);
        if (!value) {
            continue;
        }
        identityOptionCache[value] = {
            ...option,
            displayNameOnly: identityDisplayName(option),
            email: identityEmail(option)
        };
    }
}

function identityContainsQuery(option, query) {
    const normalizedQuery = normalizedIdentity(query);
    if (!normalizedQuery) {
        return true;
    }
    return normalizedIdentity(`${option.displayName || ""} ${option.value || ""} ${option.description || ""}`)
            .includes(normalizedQuery);
}

function identitySearchCacheKey(projectConfigId, role, organization, project, query) {
    return [projectConfigId, role, organization, project, normalizedIdentityQuery(query)]
            .map(normalizedIdentity)
            .join("::");
}

function pruneIdentitySearchCache(now = Date.now()) {
    for (const [key, entry] of identitySearchResultCache) {
        if (now - entry.createdAt >= IDENTITY_CACHE_TTL_MS) {
            identitySearchResultCache.delete(key);
        }
    }
    while (identitySearchResultCache.size > IDENTITY_CACHE_MAX_ENTRIES) {
        identitySearchResultCache.delete(identitySearchResultCache.keys().next().value);
    }
}

function putIdentitySearchCache(projectConfigId, role, organization, project, query, options) {
    const key = identitySearchCacheKey(projectConfigId, role, organization, project, query);
    identitySearchResultCache.delete(key);
    identitySearchResultCache.set(key, {
        organization: normalizedIdentity(organization),
        projectConfigId,
        role,
        project: normalizedIdentity(project),
        query: normalizedIdentityQuery(query),
        options: (options || []).map((option) => ({ ...option })),
        createdAt: Date.now()
    });
    pruneIdentitySearchCache();
}

function findIdentitySearchCache(projectConfigId, role, organization, project, query) {
    pruneIdentitySearchCache();
    const normalizedOrganization = normalizedIdentity(organization);
    const normalizedProject = normalizedIdentity(project);
    const normalizedQuery = normalizedIdentityQuery(query);
    const exact = identitySearchResultCache.get(identitySearchCacheKey(projectConfigId, role, organization, project, query));
    if (exact) {
        return { exact: true, options: exact.options.filter((option) => identityContainsQuery(option, normalizedQuery)) };
    }
    const broader = Array.from(identitySearchResultCache.values())
            .filter((entry) => entry.projectConfigId === projectConfigId
                    && entry.role === role
                    && entry.organization === normalizedOrganization
                    && entry.project === normalizedProject
                    && normalizedQuery.startsWith(entry.query))
            .sort((left, right) => right.query.length - left.query.length)[0];
    if (!broader) {
        return null;
    }
    return { exact: false, options: broader.options.filter((option) => identityContainsQuery(option, normalizedQuery)) };
}

function identityLookupFromCache(options) {
    return {
        status: options.length > 0 ? "VALID" : "WARNING",
        message: options.length > 0 ? "" : t("identity.noResults"),
        values: options,
        optionCount: options.length
    };
}

function identityOptionsForSearch(searchState) {
    const options = selectorOptions(searchState.lookup);
    const filtered = options.filter((option) => identityContainsQuery(option, searchState.query));
    cacheIdentityOptions(filtered);
    return filtered;
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

function pendingIdentityValue(projectConfigId, role) {
    return ensureIdentitySearchState(projectConfigId, role).pending?.value || "";
}

function setPendingIdentity(projectConfigId, role, value) {
    const searchState = ensureIdentitySearchState(projectConfigId, role);
    const normalized = normalizedIdentity(value);
    searchState.pending = identityOptionsForSearch(searchState)
            .find((option) => normalizedIdentity(option.value) === normalized) || null;
}

function clearIdentitySearch(projectConfigId, role) {
    const searchState = ensureIdentitySearchState(projectConfigId, role);
    clearTimeout(identitySearchTimers[identityKey(projectConfigId, role)]);
    searchState.requestVersion += 1;
    searchState.query = "";
    searchState.pending = null;
    searchState.searching = false;
    searchState.debouncePending = false;
    searchState.lookup = { status: "NOT_CHECKED", message: t("identity.typeToSearch"), values: [], optionCount: 0 };
    const picker = identityPickerElement(projectConfigId, role);
    const input = picker?.querySelector("[data-action='identity-search']");
    if (input) {
        input.value = "";
        input.focus();
    }
}

function removeUserFromRole(project, role, value) {
    const normalized = normalizedIdentity(value);
    setRoleUsers(project, role, (roleUsers(project, role) || []).filter((user) => normalizedIdentity(user) !== normalized));
}

function identitySearchStatus(projectConfigId, role, project) {
    const stateForRole = ensureIdentitySearchState(projectConfigId, role);
    const selectedCount = (roleUsers(project, role) || []).length;
    const unresolvedCount = unresolvedIdentityCount(project, role);
    const warnings = duplicateIdentityMessages(project).length;
    const resultCount = identityOptionsForSearch(stateForRole).length;
    const pendingIdentityStatus = stateForRole.pending
            ? (isResolvableIdentityValue(stateForRole.pending.value) ? "resolved" : "unresolved")
            : "none";
    updateSelectorDiagnostics(`${role}Users`, {
        status: stateForRole.lookup.status || "NOT_CHECKED",
        backendOptionCount: lookupOptionCount(stateForRole.lookup),
        receivedLength: rawOptionItems(stateForRole.lookup).length,
        normalizedLength: resultCount,
        renderedOptionCount: resultCount,
        domOptionCount: resultCount,
        lastQueryLength: stateForRole.query.length,
        resultCount,
        pendingIdentityStatus,
        selectedCount,
        unresolvedCount,
        identityWarnings: warnings,
        debouncePending: stateForRole.debouncePending,
        frontendCacheHit: stateForRole.frontendCacheHit || false,
        frontendCacheHits: stateForRole.frontendCacheHits,
        frontendCacheMisses: stateForRole.frontendCacheMisses,
        frontendRequestCount: stateForRole.frontendRequestCount,
        backendCacheHit: stateForRole.backendCacheHit,
        backendCacheMiss: stateForRole.backendCacheMiss,
        backendRequestCount: stateForRole.backendRequestCount,
        adoRequestCount: stateForRole.adoRequestCount,
        candidatePoolSource: stateForRole.candidatePoolSource,
        candidatePoolSize: stateForRole.candidatePoolSize,
        candidatePoolCacheHit: stateForRole.candidatePoolCacheHit,
        projectPoolMatchCount: stateForRole.projectPoolMatchCount,
        graphFallbackAttempted: stateForRole.graphFallbackAttempted,
        graphCacheHit: stateForRole.graphCacheHit,
        graphNegativeCacheHit: stateForRole.graphNegativeCacheHit,
        avatarCacheHitCount: stateForRole.avatarCacheHitCount,
        avatarCacheMissCount: stateForRole.avatarCacheMissCount,
        avatarAdoRequestCount: stateForRole.avatarAdoRequestCount,
        enabled: true,
        message: sanitizeMessage(stateForRole.lookup.message)
    }, projectConfigId);
}

function identityPickerElement(projectConfigId, role) {
    return projectsEl.querySelector(`[data-identity-picker][data-project-config-id="${projectConfigId}"][data-role="${role}"]`);
}

function updateIdentityPicker(projectConfigId, role) {
    const project = projectByConfigId(projectConfigId);
    if (!project) {
        return;
    }
    const picker = identityPickerElement(projectConfigId, role);
    if (!picker) {
        identitySearchStatus(projectConfigId, role, project);
        return;
    }
    const searchState = ensureIdentitySearchState(projectConfigId, role);
    const pendingEl = picker.querySelector("[data-identity-pending]");
    const resultsEl = picker.querySelector("[data-identity-results]");
    const selectedEl = picker.querySelector("[data-identity-selected]");
    if (pendingEl) {
        pendingEl.innerHTML = pendingIdentityPreview(project, role, searchState.pending);
    }
    if (resultsEl) {
        resultsEl.innerHTML = identitySearchResults(project, role, searchState);
    }
    if (selectedEl) {
        selectedEl.innerHTML = selectedUserChips(project, role);
    }
    wireIdentityAvatarFallbacks(picker);
    identitySearchStatus(projectConfigId, role, project);
}

function updateIdentityPickers(projectConfigId) {
    updateIdentityPicker(projectConfigId, "sme");
    updateIdentityPicker(projectConfigId, "sqa");
}

function selectedUserChips(project, role) {
    const users = roleUsers(project, role) || [];
    if (users.length === 0) {
        return `<p class="note compact">${escapeHtml(t(role === "sme" ? "identity.smeUsers" : "identity.sqaUsers"))}: 0</p>`;
    }
    return `<div class="identity-chip-list">${users.map((user) => `
        <span class="identity-chip">
            ${identityAvatarMarkup(user)}
            <span class="identity-chip-text">
                <strong>${escapeHtml(identityDisplayName(user))}</strong>
                <small>${escapeHtml(identityEmail(user))}</small>
            </span>
            <button type="button" data-action="remove-user" data-role="${role}" data-user-value="${escapeHtml(user)}" aria-label="${escapeHtml(t("button.remove"))} ${escapeHtml(user)}">x</button>
        </span>
    `).join("")}</div>`;
}

function pendingIdentityPreview(project, role, pending) {
    const normalized = normalizedIdentity(pending?.value);
    const selected = new Set((roleUsers(project, role) || []).map(normalizedIdentity));
    const canAdd = normalized && isResolvableIdentityValue(normalized) && !selected.has(normalized);
    const pendingBody = pending ? `
        ${identityAvatarMarkup(pending)}
        <span class="identity-result-text">
            <strong>${escapeHtml(identityDisplayName(pending))}</strong>
            <small>${escapeHtml(identityEmail(pending))}</small>
        </span>
    ` : `<span class="note compact">${escapeHtml(t("identity.pendingNone"))}</span>`;
    return `
        <div class="identity-pending">
            <div class="identity-pending-user">${pendingBody}</div>
            <button type="button" class="identity-add" data-action="add-pending-user" data-role="${role}" ${canAdd ? "" : "disabled"}>+</button>
        </div>
    `;
}

function identitySearchResults(project, role, searchState) {
    const options = identityOptionsForSearch(searchState);
    const lookup = searchState.lookup;
    if (lookup?.status === "VALID" && options.length === 0) {
        return `<p class="lookup-status">${validationBadge("WARNING")} ${escapeHtml(t("identity.noResults"))}</p>`;
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
        const selectedClass = searchState.pending && normalizedIdentity(searchState.pending.value) === normalized ? " selected" : "";
        const email = identityEmail(option);
        return `
            <button type="button" class="identity-result${selectedClass}" data-action="select-pending-user" data-role="${role}" data-user-value="${escapeHtml(option.value)}" ${disabled}>
                ${identityAvatarMarkup(option)}
                <span class="identity-result-text">
                    <strong>${escapeHtml(identityDisplayName(option))}</strong>
                    <small>${escapeHtml(email)}</small>
                </span>
            </button>
        `;
    }).join("")}</div>`;
}

function identityUserPicker(project, projectConfigId, role, enabled) {
    const searchState = ensureIdentitySearchState(projectConfigId, role);
    const disabled = enabled ? "" : "disabled";
    identitySearchStatus(projectConfigId, role, project);
    const inputId = projectControlId(projectConfigId, `${role}-identity-search`);
    return `
        <div class="identity-picker" data-identity-picker data-project-config-id="${projectConfigId}" data-role="${role}">
            <label for="${inputId}">${escapeHtml(t(role === "sme" ? "identity.smeUsers" : "identity.sqaUsers"))}</label>
                <input id="${inputId}" type="search" data-action="identity-search" data-role="${role}" value="${escapeHtml(searchState.query)}" placeholder="${escapeHtml(t("identity.searchPlaceholder"))}" ${disabled}>
            <div data-identity-pending>${pendingIdentityPreview(project, role, searchState.pending)}</div>
            <div data-identity-results>${identitySearchResults(project, role, searchState)}</div>
            <div data-identity-selected>${selectedUserChips(project, role)}</div>
        </div>
    `;
}

function renderedOptionCount(lookup) {
    return selectorOptions(lookup).length;
}

function newProjectConfigId() {
    localProjectSequence += 1;
    return `project-${Date.now().toString(36)}-${localProjectSequence.toString(36)}`;
}

function ensureProjectConfigId(project) {
    if (!project.projectConfigId) {
        Object.defineProperty(project, "projectConfigId", {
            value: newProjectConfigId(),
            writable: false,
            enumerable: false
        });
    }
    return project.projectConfigId;
}

function createProjectModel() {
    const project = {
        name: "",
        enabled: true,
        supportedWorkItemTypes: [],
        states: { design: "Design", inReview: "In Review", approved: "Approved" },
        fields: { approvedBySme: "", approvedBySqa: "", reversibleBusinessFields: [] },
        approvals: { smeUsers: [], sqaUsers: [] }
    };
    ensureProjectConfigId(project);
    return project;
}

function prepareProjectState(project) {
    project.supportedWorkItemTypes = [...(project.supportedWorkItemTypes || [])];
    project.states = { design: "Design", inReview: "In Review", approved: "Approved", ...(project.states || {}) };
    project.fields = {
        approvedBySme: "",
        approvedBySqa: "",
        ...(project.fields || {}),
        reversibleBusinessFields: [...(project.fields?.reversibleBusinessFields || [])]
    };
    project.approvals = {
        smeUsers: [...(project.approvals?.smeUsers || [])],
        sqaUsers: [...(project.approvals?.sqaUsers || [])]
    };
    ensureProjectConfigId(project);
    return project;
}

function projectByConfigId(projectConfigId) {
    return state.ado.projects.find((project) => ensureProjectConfigId(project) === projectConfigId);
}

function duplicateProjectConfigIds() {
    const projectIdsByName = new Map();
    for (const project of state.ado.projects) {
        const normalizedName = normalizedText(project.name);
        if (!normalizedName) {
            continue;
        }
        const projectConfigId = ensureProjectConfigId(project);
        const projectIds = projectIdsByName.get(normalizedName) || [];
        projectIds.push(projectConfigId);
        projectIdsByName.set(normalizedName, projectIds);
    }
    return new Set(Array.from(projectIdsByName.values())
        .filter((projectIds) => projectIds.length > 1)
        .flat());
}

function projectControlId(projectConfigId, controlName) {
    return `${projectConfigId}-${controlName}`;
}

function createDiscoveryState() {
    return {
        requestToken: 0,
        projectId: "",
        projectValidationCurrentFor: "",
        projectValidationUpdatedAt: 0,
        workItemTypesCurrentForProjectId: "",
        workItemTypesUpdatedAt: 0,
        fieldsCurrentForProjectIdAndWorkItemType: "",
        fieldsUpdatedAt: 0,
        statesCurrentForProjectIdAndWorkItemType: "",
        statesUpdatedAt: 0,
        inFlight: {},
        frontendValidateProjectCallCount: 0,
        frontendLoadWitCallCount: 0,
        frontendLoadFieldsCallCount: 0,
        frontendLoadStatesCallCount: 0,
        inFlightDedupedCount: 0,
        skippedBecauseCurrentCount: 0,
        structuralDiscoverySuppressedCount: 0,
        lastStructuralDiscoveryReason: "",
        lastStructuralDiscoveryDependencyKey: "",
        backendCacheHit: false,
        processIdCacheHit: false,
        processFailureCacheHit: false,
        projectStatus: { status: "NOT_CHECKED", message: t("message.projectNotLoaded") },
        workItemTypes: { status: "NOT_CHECKED", message: t("message.loadProjectFirst"), values: [] },
        fields: { status: "NOT_CHECKED", message: t("message.selectWorkItemTypeFirst"), values: [] },
        states: { status: "NOT_CHECKED", message: t("message.selectWorkItemTypeFirst"), values: [] }
    };
}

function ensureDiscovery() {
    const activeIds = new Set();
    for (const project of state.ado.projects) {
        const projectConfigId = ensureProjectConfigId(project);
        activeIds.add(projectConfigId);
        if (!projectDiscovery.has(projectConfigId)) {
            projectDiscovery.set(projectConfigId, createDiscoveryState());
        }
        if (!projectLayoutState.has(projectConfigId)) {
            projectLayoutState.set(projectConfigId, { collapsed: false });
        }
    }
    for (const projectConfigId of projectDiscovery.keys()) {
        if (!activeIds.has(projectConfigId)) {
            projectDiscovery.delete(projectConfigId);
            projectLayoutState.delete(projectConfigId);
        }
    }
}

function projectLayout(projectConfigId) {
    ensureDiscovery();
    return projectLayoutState.get(projectConfigId) || { collapsed: false };
}

function removeProjectState(projectConfigId) {
    const index = state.ado.projects.findIndex((project) => ensureProjectConfigId(project) === projectConfigId);
    if (index >= 0) {
        state.ado.projects.splice(index, 1);
    }
    projectDiscovery.delete(projectConfigId);
    projectLayoutState.delete(projectConfigId);
    for (const role of ["sme", "sqa"]) {
        const key = identityKey(projectConfigId, role);
        clearTimeout(identitySearchTimers[key]);
        delete identitySearchTimers[key];
        delete identitySearchState[key];
    }
    for (const key of Object.keys(selectorDiagnostics)) {
        if (key.startsWith(`${projectConfigId}:`)) {
            delete selectorDiagnostics[key];
        }
    }
}

function resetProjectScopedState() {
    for (const timer of Object.values(identitySearchTimers)) {
        clearTimeout(timer);
    }
    projectDiscovery = new Map();
    projectLayoutState = new Map();
    identitySearchState = {};
    identitySearchTimers = {};
    selectorDiagnostics = {};
    ensureDiscovery();
}

function invalidatePreview() {
    lastPreview = null;
    saveBtn.disabled = true;
}

function validationBoundaryDiagnostic() {
    return ensureSelectorDiagnostic("validationBoundary");
}

function recordLocalValidation(trigger) {
    const diagnostic = validationBoundaryDiagnostic();
    updateSelectorDiagnostics("validationBoundary", {
        status: "VALID",
        localValidationRunCount: diagnostic.localValidationRunCount + 1,
        yamlPreviewLocalOnlyCount: diagnostic.yamlPreviewLocalOnlyCount + 1,
        message: `Local-only validation: ${trigger}`
    });
}

function recordStrictValidation(trigger) {
    const diagnostic = validationBoundaryDiagnostic();
    updateSelectorDiagnostics("validationBoundary", {
        status: "VALID",
        strictValidationRunCount: diagnostic.strictValidationRunCount + 1,
        backendStrictValidationCallCount: diagnostic.backendStrictValidationCallCount + 1,
        lastStrictValidationTrigger: trigger,
        lastStrictValidationAt: nowTimestamp(),
        message: `Explicit strict validation: ${trigger}`
    });
}

function scheduleLocalPreview(trigger = "edit", countStrictSkip = true) {
    invalidatePreview();
    if (countStrictSkip) {
        const diagnostic = validationBoundaryDiagnostic();
        updateSelectorDiagnostics("validationBoundary", {
            strictValidationSkippedDuringEditCount: diagnostic.strictValidationSkippedDuringEditCount + 1,
            message: `Strict validation skipped during ${trigger}`
        });
    }
    clearTimeout(previewTimer);
    previewTimer = setTimeout(() => {
        updateYamlPreviewLocalOnly(false, trigger).catch(() => undefined);
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
    state.ado.projects.forEach((project) => {
        const projectConfigId = ensureProjectConfigId(project);
        if (project.name && !lookupContainsValue(projectOptionLookup, project.name)) {
            project.name = "";
            clearChildSelections(project);
            clearDiscovery(projectConfigId, "project");
        }
    });
}

function clearDiscovery(projectConfigId, level) {
    const discovery = projectDiscovery.get(projectConfigId);
    if (!discovery) {
        return;
    }
    discovery.requestToken = ++discoveryRequestSequence;
    debugDiscovery("dependent-selectors-cleared", { projectConfigId, level });
    if (level === "project") {
        discovery.projectId = "";
        discovery.projectValidationCurrentFor = "";
        discovery.projectValidationUpdatedAt = 0;
        discovery.workItemTypesCurrentForProjectId = "";
        discovery.workItemTypesUpdatedAt = 0;
        discovery.fieldsCurrentForProjectIdAndWorkItemType = "";
        discovery.fieldsUpdatedAt = 0;
        discovery.statesCurrentForProjectIdAndWorkItemType = "";
        discovery.statesUpdatedAt = 0;
        discovery.projectStatus = { status: "NOT_CHECKED", message: t("message.projectSelectionChanged") };
        discovery.workItemTypes = { status: "NOT_CHECKED", message: t("message.loadProjectAgain"), values: [] };
        discovery.fields = { status: "NOT_CHECKED", message: t("message.selectWorkItemTypeFirst"), values: [] };
        discovery.states = { status: "NOT_CHECKED", message: t("message.selectWorkItemTypeFirst"), values: [] };
        for (const selector of ["workItemType", "approvedBySmeField", "approvedBySqaField", "reversibleBusinessFields", "designState", "inReviewState", "approvedState"]) {
            updateSelectorDiagnostics(selector, {
                status: "NOT_CHECKED",
                backendOptionCount: 0,
                receivedLength: 0,
                normalizedLength: 0,
                renderedOptionCount: 0,
                domOptionCount: "",
                enabled: false,
                message: t("message.clearedProject")
            }, projectConfigId);
        }
    }
    if (level === "type") {
        discovery.fieldsCurrentForProjectIdAndWorkItemType = "";
        discovery.fieldsUpdatedAt = 0;
        discovery.statesCurrentForProjectIdAndWorkItemType = "";
        discovery.statesUpdatedAt = 0;
        discovery.fields = { status: "NOT_CHECKED", message: t("message.workItemTypeChanged"), values: [] };
        discovery.states = { status: "NOT_CHECKED", message: t("message.workItemTypeChanged"), values: [] };
        for (const selector of ["approvedBySmeField", "approvedBySqaField", "reversibleBusinessFields", "designState", "inReviewState", "approvedState"]) {
            updateSelectorDiagnostics(selector, {
                status: "NOT_CHECKED",
                backendOptionCount: 0,
                receivedLength: 0,
                normalizedLength: 0,
                renderedOptionCount: 0,
                domOptionCount: "",
                enabled: false,
                message: t("message.clearedWorkItemType")
            }, projectConfigId);
        }
    }
}

function validationBadge(label) {
    return `<span class="badge badge-${label.toLowerCase().replace("_", "-")}">${label}</span>`;
}

function projectDisplayName(project, index) {
    return (project.name || "").trim() || t("project.fallbackName", { number: index + 1 });
}

function projectSectionStatus(project, discovery, fieldDuplicateMessages, identityMessages) {
    if (duplicateProjectConfigIds().has(ensureProjectConfigId(project)) || fieldDuplicateMessages.length > 0) {
        return "ERROR";
    }
    if (identityMessages.length > 0) {
        return "WARNING";
    }
    if (isProjectDiscoveryCurrent(project, discovery)) {
        return "VALID";
    }
    return discovery?.projectStatus?.status || "NOT_CHECKED";
}

function projectSummary(project, index, selectedType, status) {
    const selectedTypes = (project.supportedWorkItemTypes || []).filter((type) => type && type.trim());
    const typeLabel = selectedTypes.length ? selectedTypes.join(", ") : t("project.noWorkItemType");
    const fieldCount = [
        project.fields.approvedBySme,
        project.fields.approvedBySqa,
        ...(project.fields.reversibleBusinessFields || [])
    ].filter((field) => field && field.trim()).length;
    const userCount = (project.approvals.smeUsers || []).length + (project.approvals.sqaUsers || []).length;
    return `
        <div class="project-summary">
            <div>
                <h3>${escapeHtml(t("project.title", { name: projectDisplayName(project, index) }))}</h3>
                <p class="note compact">${escapeHtml(typeLabel)}</p>
            </div>
            <div class="project-summary-meta">
                ${validationBadge(status)}
                <span>${escapeHtml(t("project.fieldCount", { count: fieldCount, plural: fieldCount === 1 ? "" : "s" }))}</span>
                <span>${escapeHtml(t("project.userCount", { count: userCount, plural: userCount === 1 ? "" : "s" }))}</span>
            </div>
        </div>
    `;
}

function projectCanCollapse(project, discovery, fieldDuplicateMessages, identityMessages) {
    return isProjectDiscoveryCurrent(project, discovery)
        && !duplicateProjectConfigIds().has(ensureProjectConfigId(project))
        && fieldDuplicateMessages.length === 0
        && identityMessages.length === 0;
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
            <span>${escapeHtml(localizeMessage(field.message))}</span>
        </li>
    `).join("");

    const finalState = preview.finalYamlAllowed && uiAdoDiscoveryCurrent
        ? t("validation.finalAllowed")
        : t("validation.finalBlocked");
    validationSummaryEl.innerHTML = `
        <div class="validation-heading">
            <strong>${escapeHtml(t("validation.heading"))}</strong>
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

function normalizeOptionsLookup(lookup, emptyMessage, selectorName = "selector", projectConfigId = "") {
    const backendOptionCount = lookupOptionCount(lookup);
    const receivedLength = rawOptionItems(lookup).length;
    const renderedOptions = selectorOptions(lookup);
    const status = lookup?.status || "NOT_CHECKED";
    const countMismatch = backendOptionCount !== receivedLength || backendOptionCount !== renderedOptions.length;
    const message = sanitizeMessage(
            lookup?.message || (countMismatch ? t("message.selectorCountMismatch") : "")
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
        message,
        ...adoDiscoveryDiagnostics(lookup)
    }, projectConfigId);
    if (backendOptionCount > 0 && renderedOptions.length === 0) {
        const message = t("message.selectorEmptyButCount");
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
        }, projectConfigId);
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
        }, projectConfigId);
        return { status: "WARNING", message: emptyMessage, values: [], optionCount: 0 };
    }
    return { ...(lookup || {}), values: renderedOptions, optionCount: renderedOptions.length };
}

function adoDiscoveryDiagnostics(lookup) {
    const diagnostics = lookup?.diagnostics || {};
    return {
        adoDiscoveryRequestCount: Number(diagnostics.adoDiscoveryRequestCount ?? 0),
        lastDiscoveryOperation: String(diagnostics.lastDiscoveryOperation ?? ""),
        discoveryCacheHit: diagnostics.discoveryCacheHit === true,
        projectMetadataCacheHit: diagnostics.projectMetadataCacheHit === true,
        processIdCacheHit: diagnostics.processIdCacheHit === true,
        workItemTypeOptionsCacheHit: diagnostics.workItemTypeOptionsCacheHit === true,
        fieldOptionsCacheHit: diagnostics.fieldOptionsCacheHit === true,
        stateOptionsCacheHit: diagnostics.stateOptionsCacheHit === true,
        processFailureCacheHit: diagnostics.processFailureCacheHit === true
    };
}

function isCurrentDiscoveryRequest(projectConfigId, requestToken, projectName, workItemType) {
    const project = projectByConfigId(projectConfigId);
    const discovery = projectDiscovery.get(projectConfigId);
    if (!project || !discovery || discovery.requestToken !== requestToken) {
        debugDiscovery("stale-response-ignored", { projectConfigId, projectName, workItemType, reason: "request-token" });
        incrementStaleIgnored(workItemType === undefined ? "workItemType" : "fields", "request-token", projectConfigId);
        return false;
    }
    if ((project.name || "").trim() !== projectName) {
        debugDiscovery("stale-response-ignored", { projectConfigId, projectName, workItemType, reason: "project-changed" });
        incrementStaleIgnored(workItemType === undefined ? "workItemType" : "fields", "project-changed", projectConfigId);
        return false;
    }
    if (workItemType !== undefined && (project.supportedWorkItemTypes?.[0] || "") !== workItemType) {
        debugDiscovery("stale-response-ignored", { projectConfigId, projectName, workItemType, reason: "work-item-type-changed" });
        incrementStaleIgnored("fields", "work-item-type-changed", projectConfigId);
        incrementStaleIgnored("states", "work-item-type-changed", projectConfigId);
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
        && duplicateProjectConfigIds().size === 0
        && state.ado.projects.every((project) => isProjectDiscoveryCurrent(project, projectDiscovery.get(ensureProjectConfigId(project))));
}

function selectOptions(selectorName, lookup, selected, placeholder, enabled = false, projectConfigId = "", disabledValues = new Set()) {
    const options = selectorOptions(lookup);
    const hasSelected = options.some((option) => option.value === selected);
    const rows = [`<option value="">${escapeHtml(placeholder)}</option>`];
    if (selected && !hasSelected && lookup?.status !== "VALID") {
        rows.push(`<option value="${escapeHtml(selected)}" selected>${escapeHtml(selected)} - ${escapeHtml(t("message.manualUncheckedSuffix"))}</option>`);
    }
    for (const option of options) {
        const isDisabled = disabledValues.has(normalizedText(option.value));
        rows.push(`<option value="${escapeHtml(option.value)}" ${option.value === selected ? "selected" : ""} ${isDisabled ? "disabled" : ""}>${escapeHtml(optionLabel(option, selectorName))}</option>`);
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
    }, projectConfigId);
    return rows.join("");
}

function renderProjects() {
    ensureDiscovery();
    projectsEl.innerHTML = "";
    const duplicateProjectIds = duplicateProjectConfigIds();
    state.ado.projects.forEach((project, index) => {
        const projectConfigId = ensureProjectConfigId(project);
        const duplicateProject = duplicateProjectIds.has(projectConfigId);
        const discovery = projectDiscovery.get(projectConfigId);
        const selectedType = project.supportedWorkItemTypes?.[0] || "";
        const projectVerified = isProjectVerified(discovery, project);
        const dependentOptionsReady = areFieldsAndStatesReady(discovery, project);
        const workItemTypeDisabled = projectVerified && lookupHasOptions(discovery.workItemTypes) ? "" : "disabled";
        const loadFieldsStatesDisabled = projectVerified && selectedType ? "" : "disabled";
        const fieldAndStateDisabled = dependentOptionsReady ? "" : "disabled";
        const workItemTypeEnabled = !workItemTypeDisabled;
        const fieldAndStateEnabled = !fieldAndStateDisabled;
        const projectSelectorEnabled = lookupHasOptions(projectOptionLookup);
        const projectSelectorDisabled = projectSelectorEnabled ? "" : "disabled";
        const selectedProjectNamesByOtherProjects = new Set(
            state.ado.projects
                .filter((candidate) => ensureProjectConfigId(candidate) !== projectConfigId)
                .map((candidate) => normalizedText(candidate.name))
                .filter((candidateName) => candidateName.length > 0)
        );
        const fieldLookups = filteredFieldLookups(project, discovery.fields);
        const fieldDuplicateMessages = duplicateFieldMessages(project);
        const identityMessages = duplicateIdentityMessages(project);
        const fieldDuplicateStatus = fieldDuplicateMessages.length
                ? `<span class="lookup-status">${validationBadge("ERROR")} ${escapeHtml(fieldDuplicateMessages.join(" "))}</span>`
                : "";
        const identityStatus = identityMessages.length
                ? `<span class="lookup-status">${validationBadge("WARNING")} ${escapeHtml(identityMessages.join(" "))}</span>`
                : "";
        const layout = projectLayout(projectConfigId);
        const collapsed = !!layout.collapsed;
        const sectionStatus = projectSectionStatus(project, discovery, fieldDuplicateMessages, identityMessages);
        const canCollapse = projectCanCollapse(project, discovery, fieldDuplicateMessages, identityMessages);
        const collapseDisabled = canCollapse ? "" : "disabled";
        debugDiscovery("selector-state", {
            projectConfigId,
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
        updateSelectorDiagnostics("projectSelection", {
            status: duplicateProject ? "ERROR" : (project.name ? "VALID" : "NOT_CHECKED"),
            selectedProjectName: project.name || "",
            normalizedProjectName: normalizedText(project.name),
            duplicateProjectStatus: duplicateProject,
            enabled: !duplicateProject,
            message: duplicateProject ? DUPLICATE_PROJECT_MESSAGE : ""
        }, projectConfigId);
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
        }, projectConfigId);
        const card = document.createElement("div");
        card.className = `project-card${collapsed ? " collapsed" : ""}`;
        card.dataset.projectConfigId = projectConfigId;
        card.innerHTML = `
            <div class="project-card-header">
                ${projectSummary(project, index, selectedType, sectionStatus)}
                <div class="project-card-actions">
                    <button type="button" data-action="toggle-project" ${!collapsed && !canCollapse ? "disabled" : ""}>${collapsed ? escapeHtml(t("button.edit")) : escapeHtml(t("button.collapse"))}</button>
                    <button type="button" class="remove" data-action="remove">${escapeHtml(t("button.remove"))}</button>
                </div>
            </div>
            ${collapsed ? `
                <div class="project-collapsed-body">
                    <span>${escapeHtml(t("project.workItemTypeCount", { count: (project.supportedWorkItemTypes || []).length }))}</span>
                    ${duplicateProject ? `<span>${validationBadge("ERROR")} ${escapeHtml(DUPLICATE_PROJECT_MESSAGE)}</span>` : ""}
                    ${fieldDuplicateMessages.length ? `<span>${validationBadge("ERROR")} ${escapeHtml(fieldDuplicateMessages.join(" "))}</span>` : ""}
                    ${identityMessages.length ? `<span>${validationBadge("WARNING")} ${escapeHtml(identityMessages.join(" "))}</span>` : ""}
                </div>
            ` : `
                <div class="project-card-body">
                    <div class="selector-grid">
                        <label>${escapeHtml(t("project.projectLabel"))}
                            <select id="${projectControlId(projectConfigId, "project")}" data-field="name" data-selector-name="project" ${projectSelectorDisabled}>
                                ${selectOptions("project", projectOptionLookup, project.name || "", t("message.loadProjectFirst"), projectSelectorEnabled, projectConfigId, selectedProjectNamesByOtherProjects)}
                            </select>
                        </label>
                        <button type="button" data-action="load-project">${escapeHtml(t("button.verifyProject"))}</button>
                    </div>
                    ${duplicateProject ? `<span class="lookup-status">${validationBadge("ERROR")} ${escapeHtml(DUPLICATE_PROJECT_MESSAGE)}</span>` : ""}
                    ${lookupBadge(discovery.projectStatus)}
                    <label class="switch-row"><input data-field="enabled" type="checkbox" ${project.enabled ? "checked" : ""}> ${escapeHtml(t("project.enabled"))}</label>
                    <label>${escapeHtml(t("project.workItemType"))}
                        <select id="${projectControlId(projectConfigId, "work-item-type")}" data-field="supportedWorkItemTypes.0" ${workItemTypeDisabled}>
                            ${selectOptions("workItemType", discovery.workItemTypes, selectedType, t("message.selectWorkItemTypeFirst"), workItemTypeEnabled, projectConfigId)}
                        </select>
                    </label>
                    ${lookupBadge(discovery.workItemTypes)}
                    <button type="button" data-action="load-fields-states" ${loadFieldsStatesDisabled}>${escapeHtml(t("button.loadFieldsStates"))}</button>
                    <div class="grid-2">
                        <label>${escapeHtml(t("project.stateDesign"))}
                            <select id="${projectControlId(projectConfigId, "state-design")}" data-field="states.design" ${fieldAndStateDisabled}>
                                ${selectOptions("designState", discovery.states, project.states.design || "", t("message.noStates"), fieldAndStateEnabled, projectConfigId)}
                            </select>
                        </label>
                        <label>${escapeHtml(t("project.stateInReview"))}
                            <select id="${projectControlId(projectConfigId, "state-in-review")}" data-field="states.inReview" ${fieldAndStateDisabled}>
                                ${selectOptions("inReviewState", discovery.states, project.states.inReview || "", t("message.noStates"), fieldAndStateEnabled, projectConfigId)}
                            </select>
                        </label>
                    </div>
                    <label>${escapeHtml(t("project.stateApproved"))}
                        <select id="${projectControlId(projectConfigId, "state-approved")}" data-field="states.approved" ${fieldAndStateDisabled}>
                            ${selectOptions("approvedState", discovery.states, project.states.approved || "", t("message.noStates"), fieldAndStateEnabled, projectConfigId)}
                        </select>
                    </label>
                    ${lookupBadge(discovery.states)}
                    <div class="grid-2">
                        <label>${escapeHtml(t("project.fieldApprovedBySme"))}
                            <select id="${projectControlId(projectConfigId, "field-sme")}" data-field="fields.approvedBySme" ${fieldAndStateDisabled}>
                                ${selectOptions("approvedBySmeField", fieldLookups.smeLookup, project.fields.approvedBySme || "", t("message.noFields"), fieldAndStateEnabled, projectConfigId)}
                            </select>
                        </label>
                        <label>${escapeHtml(t("project.fieldApprovedBySqa"))}
                            <select id="${projectControlId(projectConfigId, "field-sqa")}" data-field="fields.approvedBySqa" ${fieldAndStateDisabled}>
                                ${selectOptions("approvedBySqaField", fieldLookups.sqaLookup, project.fields.approvedBySqa || "", t("message.noFields"), fieldAndStateEnabled, projectConfigId)}
                            </select>
                        </label>
                    </div>
                    <label>${escapeHtml(t("project.reversibleBusinessFields"))}
                        <select id="${projectControlId(projectConfigId, "fields-reversible")}" data-field="fields.reversibleBusinessFields" multiple size="6" ${fieldAndStateDisabled}>
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
                        ${identityUserPicker(project, projectConfigId, "sme", projectVerified)}
                        ${identityUserPicker(project, projectConfigId, "sqa", projectVerified)}
                    </div>
                    ${identityStatus}
                    <div class="row-between">
                        <p class="note compact">${escapeHtml(t("identity.selectionNote"))}</p>
                        <button type="button" data-action="collapse-project" ${collapseDisabled}>${escapeHtml(t("button.collapse"))}</button>
                    </div>
                </div>
            `}
        `;

        card.addEventListener("change", (event) => {
            handleProjectInput(projectConfigId, event);
        });

        card.querySelector("[data-action='remove']").addEventListener("click", () => {
            removeProjectState(projectConfigId);
            invalidatePreview();
            renderProjects();
            scheduleLocalPreview("remove-project");
        });

        card.querySelector("[data-action='toggle-project']").addEventListener("click", () => {
            suppressStructuralDiscovery(projectConfigId, collapsed ? "expand-project" : "collapse-project",
                    structuralProjectKey(project));
            if (collapsed) {
                projectLayout(projectConfigId).collapsed = false;
            } else if (canCollapse) {
                projectLayout(projectConfigId).collapsed = true;
            }
            renderProjects();
        });
        const collapseButton = card.querySelector("[data-action='collapse-project']");
        if (collapseButton) {
            collapseButton.addEventListener("click", () => {
                suppressStructuralDiscovery(projectConfigId, "collapse-project", structuralProjectKey(project));
                if (!canCollapse) {
                    projectLayout(projectConfigId).collapsed = false;
                    setStatus(t("status.resolveBeforeCollapse"), true);
                    renderProjects();
                    return;
                }
                projectLayout(projectConfigId).collapsed = true;
                renderProjects();
            });
        }
        const loadProjectButton = card.querySelector("[data-action='load-project']");
        if (loadProjectButton) {
            loadProjectButton.addEventListener("click", async () => {
                await runExplicitProjectVerification(projectConfigId);
            });
        }
        const loadFieldsStatesButton = card.querySelector("[data-action='load-fields-states']");
        if (loadFieldsStatesButton) {
            loadFieldsStatesButton.addEventListener("click", async () => {
                await runExplicitFieldAndStateDiscovery(projectConfigId);
            });
        }
        for (const input of card.querySelectorAll("[data-action='identity-search']")) {
            input.addEventListener("input", (event) => {
                handleIdentitySearchInput(projectConfigId, event.target.getAttribute("data-role"), event.target.value);
            });
            input.addEventListener("keydown", (event) => {
                if (event.key === "Enter") {
                    event.preventDefault();
                    addPendingIdentity(projectConfigId, event.target.getAttribute("data-role"));
                }
            });
        }
        card.addEventListener("click", (event) => {
            const button = event.target.closest("[data-action='select-pending-user'], [data-action='add-pending-user'], [data-action='remove-user']");
            if (!button) {
                return;
            }
            const role = button.getAttribute("data-role");
            if (button.getAttribute("data-action") === "select-pending-user") {
                setPendingIdentity(projectConfigId, role, button.getAttribute("data-user-value"));
                updateIdentityPicker(projectConfigId, role);
                return;
            }
            if (button.getAttribute("data-action") === "add-pending-user") {
                addPendingIdentity(projectConfigId, role);
                return;
            }
            removeUserFromRole(project, role, button.getAttribute("data-user-value"));
            updateIdentityPickers(projectConfigId);
            scheduleLocalPreview("identity-remove");
        });

        projectsEl.appendChild(card);
        wireIdentityAvatarFallbacks(card);
    });
}

function handleProjectInput(projectConfigId, event) {
    const project = projectByConfigId(projectConfigId);
    if (!project) {
        return;
    }
    const field = event.target.getAttribute("data-field");
    if (!field) {
        return;
    }
    const projectCard = event.target.closest(".project-card");
    if (!projectCard || projectCard.dataset.projectConfigId !== projectConfigId) {
        return;
    }
    if (projectLayout(projectConfigId).collapsed) {
        return;
    }
    if (event.type === "input" && event.target.tagName === "SELECT") {
        return;
    }

    if (field === "enabled") {
        project.enabled = event.target.checked;
        scheduleLocalPreview("project-enabled-change");
        return;
    }

    if (field === "name") {
        const nextProjectName = event.target.value;
        if (isProjectNameTakenByOtherConfig(projectConfigId, nextProjectName)) {
            event.target.value = project.name || "";
            setStatus(t("message.duplicateProjectSelection"), true);
            renderProjects();
            return;
        }
        project.name = nextProjectName;
        projectLayout(projectConfigId).collapsed = false;
        clearChildSelections(project);
        clearDiscovery(projectConfigId, "project");
        if (event.type === "change") {
            renderProjects();
        }
        scheduleLocalPreview("project-selection-change");
        return;
    }

    if (field === "supportedWorkItemTypes.0") {
        project.supportedWorkItemTypes = event.target.value ? [event.target.value] : [];
        projectLayout(projectConfigId).collapsed = false;
        clearTypeSelections(project);
        clearDiscovery(projectConfigId, "type");
        renderProjects();
        scheduleLocalPreview("work-item-type-change");
        return;
    }

    if (field === "fields.reversibleBusinessFields") {
        project.fields.reversibleBusinessFields = uniqueValues(Array.from(event.target.selectedOptions).map((option) => option.value));
        cleanFieldConflicts(project, field);
        renderProjects();
        scheduleLocalPreview("reversible-field-change");
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
    scheduleLocalPreview("project-field-change");
}

function handleIdentitySearchInput(projectConfigId, role, query) {
    const project = projectByConfigId(projectConfigId);
    suppressStructuralDiscovery(projectConfigId, `${role}-identity-search`, structuralProjectKey(project));
    const stateForRole = ensureIdentitySearchState(projectConfigId, role);
    stateForRole.query = query || "";
    stateForRole.pending = null;
    stateForRole.searching = false;
    stateForRole.requestVersion += 1;
    stateForRole.frontendCacheHit = false;
    stateForRole.backendCacheHit = false;
    stateForRole.backendCacheMiss = false;
    stateForRole.debouncePending = false;
    clearTimeout(identitySearchTimers[identityKey(projectConfigId, role)]);
    const normalizedQuery = normalizedIdentityQuery(stateForRole.query);
    if (normalizedQuery.length < IDENTITY_MIN_QUERY_LENGTH) {
        stateForRole.lookup = { status: "NOT_CHECKED", message: t("identity.typeToSearch"), values: [], optionCount: 0 };
        stateForRole.searching = false;
        updateIdentityPicker(projectConfigId, role);
        return;
    }

    const cached = findIdentitySearchCache(
            projectConfigId,
            role,
            state.ado.organization,
            project?.name || "",
            normalizedQuery
    );
    if (cached) {
        stateForRole.frontendCacheHit = true;
        stateForRole.frontendCacheHits += 1;
        stateForRole.lookup = identityLookupFromCache(cached.options);
        cacheIdentityOptions(cached.options);
        if (cached.exact || cached.options.length >= IDENTITY_CACHE_USEFUL_RESULT_COUNT) {
            stateForRole.searching = false;
            updateIdentityPicker(projectConfigId, role);
            return;
        }
    } else {
        stateForRole.frontendCacheMisses += 1;
        stateForRole.lookup = { status: "NOT_CHECKED", message: t("identity.searching"), values: [], optionCount: 0 };
    }
    stateForRole.debouncePending = true;
    updateIdentityPicker(projectConfigId, role);
    const requestVersion = stateForRole.requestVersion;
    identitySearchTimers[identityKey(projectConfigId, role)] = setTimeout(() => {
        loadIdentityOptions(projectConfigId, role, normalizedQuery, requestVersion).catch((error) => setStatus(error.message, true));
    }, IDENTITY_SEARCH_DEBOUNCE_MS);
}

function addPendingIdentity(projectConfigId, role) {
    const project = projectByConfigId(projectConfigId);
    if (!project) {
        return;
    }
    const value = pendingIdentityValue(projectConfigId, role);
    if (!isResolvableIdentityValue(value)) {
        updateIdentityPicker(projectConfigId, role);
        return;
    }
    if (addUserToRole(project, role, value)) {
        clearIdentitySearch(projectConfigId, role);
        updateIdentityPickers(projectConfigId);
        scheduleLocalPreview(`${role}-identity-add`);
    }
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
        throw new Error(t("message.invalidJson", { status: response.status }));
    }
    if (!response.ok) {
        throw new Error(payload.error || t("message.httpFailed", { status: response.status }));
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
            setStatus(result.message || t("message.adoDiscoveryError"), true);
        } else if (result.status === "NOT_CHECKED" || result.status === "WARNING") {
            setStatus(result.message || t("message.adoDiscoveryUnchecked"));
        }
        return result;
    } catch (error) {
        const result = {
            status: "ERROR",
            message: error.message || t("message.adoDiscoveryRequestFailed"),
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

async function runExplicitProjectListDiscovery() {
    readFormToState();
    projectOptionLookup = normalizeOptionsLookup(await discover("list-projects", "/api/config-ui/discovery/projects", {
        organization: state.ado.organization
    }), t("message.noProjects"), "project");
    clearStaleProjectSelections();
    renderProjectSelectors();
    renderProjects();
    scheduleLocalPreview("load-projects", false);
}

function structuralProjectKey(project) {
    return `${normalizedText(state.ado.organization)}|${normalizedText(project?.name)}`;
}

function structuralLookupIsCurrent(lookup, updatedAt) {
    const fresh = Number(updatedAt || 0) + STRUCTURAL_DISCOVERY_TTL_MS > Date.now();
    return fresh && structuralLookupSucceeded(lookup);
}

function structuralLookupSucceeded(lookup) {
    return lookup?.status === "VALID" || lookup?.status === "WARNING";
}

function structuralTypeKey(discovery, project, workItemType) {
    return `${normalizedText(discovery.projectId || structuralProjectKey(project))}|${normalizedText(workItemType)}`;
}

function updateStructuralDiscoveryDiagnostics(projectConfigId, discovery, result = null) {
    const backend = result?.diagnostics || {};
    if (result) {
        discovery.backendCacheHit = backend.discoveryCacheHit === true;
        discovery.processIdCacheHit = backend.processIdCacheHit === true;
        discovery.processFailureCacheHit = backend.processFailureCacheHit === true;
    }
    updateSelectorDiagnostics("structuralDiscovery", {
        status: discovery.projectStatus.status || "NOT_CHECKED",
        frontendValidateProjectCallCount: discovery.frontendValidateProjectCallCount,
        frontendLoadWitCallCount: discovery.frontendLoadWitCallCount,
        frontendLoadFieldsCallCount: discovery.frontendLoadFieldsCallCount,
        frontendLoadStatesCallCount: discovery.frontendLoadStatesCallCount,
        inFlightDedupedCount: discovery.inFlightDedupedCount,
        skippedBecauseCurrentCount: discovery.skippedBecauseCurrentCount,
        structuralDiscoverySuppressedCount: discovery.structuralDiscoverySuppressedCount,
        lastStructuralDiscoveryReason: discovery.lastStructuralDiscoveryReason,
        lastStructuralDiscoveryDependencyKey: discovery.lastStructuralDiscoveryDependencyKey,
        backendCacheHit: discovery.backendCacheHit,
        processIdCacheHit: discovery.processIdCacheHit,
        processFailureCacheHit: discovery.processFailureCacheHit,
        ...(result ? adoDiscoveryDiagnostics(result) : {})
    }, projectConfigId);
}

function suppressStructuralDiscovery(projectConfigId, reason, dependencyKey = "") {
    const discovery = projectDiscovery.get(projectConfigId);
    if (!discovery) {
        return;
    }
    discovery.structuralDiscoverySuppressedCount += 1;
    discovery.lastStructuralDiscoveryReason = reason;
    discovery.lastStructuralDiscoveryDependencyKey = dependencyKey;
    updateStructuralDiscoveryDiagnostics(projectConfigId, discovery);
}

function skipCurrentStructuralDiscovery(projectConfigId, discovery, reason, dependencyKey) {
    discovery.skippedBecauseCurrentCount += 1;
    discovery.structuralDiscoverySuppressedCount += 1;
    discovery.lastStructuralDiscoveryReason = reason;
    discovery.lastStructuralDiscoveryDependencyKey = dependencyKey;
    updateStructuralDiscoveryDiagnostics(projectConfigId, discovery);
}

function runStructuralDiscovery(projectConfigId, discovery, operation, dependencyKey, counterName, loader) {
    const inFlightKey = `${operation}:${dependencyKey}`;
    if (discovery.inFlight[inFlightKey]) {
        discovery.inFlightDedupedCount += 1;
        discovery.structuralDiscoverySuppressedCount += 1;
        discovery.lastStructuralDiscoveryReason = `${operation}:in-flight`;
        discovery.lastStructuralDiscoveryDependencyKey = dependencyKey;
        updateStructuralDiscoveryDiagnostics(projectConfigId, discovery);
        return discovery.inFlight[inFlightKey];
    }
    discovery[counterName] += 1;
    discovery.lastStructuralDiscoveryReason = `${operation}:requested`;
    discovery.lastStructuralDiscoveryDependencyKey = dependencyKey;
    const request = Promise.resolve()
        .then(loader)
        .then((result) => {
            updateStructuralDiscoveryDiagnostics(projectConfigId, discovery, result);
            return result;
        })
        .finally(() => {
            delete discovery.inFlight[inFlightKey];
        });
    discovery.inFlight[inFlightKey] = request;
    updateStructuralDiscoveryDiagnostics(projectConfigId, discovery);
    return request;
}

async function runExplicitProjectVerification(projectConfigId) {
    readFormToState();
    ensureDiscovery();
    const project = projectByConfigId(projectConfigId);
    const discovery = projectDiscovery.get(projectConfigId);
    if (!project || !discovery) {
        return;
    }
    const projectName = (project.name || "").trim();
    debugDiscovery("verify-project-clicked", { projectConfigId, project: projectName });
    const dependencyKey = structuralProjectKey(project);
    const requestToken = discovery.requestToken;
    let projectStatus = discovery.projectStatus;
    let projectValidationRequested = false;
    if (discovery.projectValidationCurrentFor === dependencyKey
            && structuralLookupIsCurrent(discovery.projectStatus, discovery.projectValidationUpdatedAt)) {
        skipCurrentStructuralDiscovery(projectConfigId, discovery, "validateProject:current", dependencyKey);
    } else {
        projectValidationRequested = true;
        projectStatus = await runStructuralDiscovery(projectConfigId, discovery, "validateProject", dependencyKey,
                "frontendValidateProjectCallCount", () => discover("verify-project",
                    "/api/config-ui/discovery/validate-project", {
                        organization: state.ado.organization,
                        project: projectName
                    }));
    }
    if (!isCurrentDiscoveryRequest(projectConfigId, requestToken, projectName)) {
        return;
    }
    discovery.projectStatus = projectStatus;
    discovery.projectId = String(projectStatus.diagnostics?.projectId || discovery.projectId || "");
    discovery.projectValidationCurrentFor = dependencyKey;
    if (projectValidationRequested) {
        discovery.projectValidationUpdatedAt = structuralLookupSucceeded(projectStatus) ? Date.now() : 0;
    }
    updateSelectorDiagnostics("projectValidation", {
        status: projectStatus.status || "NOT_CHECKED",
        message: sanitizeMessage(projectStatus.message),
        ...adoDiscoveryDiagnostics(projectStatus)
    }, projectConfigId);
    if (isProjectVerified(discovery, project)) {
        const witDependencyKey = normalizedText(discovery.projectId || dependencyKey);
        let workItemTypes = discovery.workItemTypes;
        let workItemTypesRequested = false;
        if (discovery.workItemTypesCurrentForProjectId === witDependencyKey
                && structuralLookupIsCurrent(discovery.workItemTypes, discovery.workItemTypesUpdatedAt)) {
            skipCurrentStructuralDiscovery(projectConfigId, discovery, "loadWorkItemTypes:current", witDependencyKey);
        } else {
            workItemTypesRequested = true;
            workItemTypes = await runStructuralDiscovery(projectConfigId, discovery, "loadWorkItemTypes",
                    witDependencyKey, "frontendLoadWitCallCount", () => discover("load-work-item-types",
                        "/api/config-ui/discovery/work-item-types", {
                            organization: state.ado.organization,
                            project: projectName
                        }));
        }
        if (!isCurrentDiscoveryRequest(projectConfigId, requestToken, projectName)) {
            return;
        }
        discovery.workItemTypes = normalizeOptionsLookup(
                workItemTypes,
                t("message.noWorkItemTypes"),
                "workItemType",
                projectConfigId
        );
        discovery.projectId = String(workItemTypes.diagnostics?.projectId || discovery.projectId || "");
        discovery.workItemTypesCurrentForProjectId = structuralLookupSucceeded(discovery.workItemTypes)
            ? normalizedText(discovery.projectId || dependencyKey)
            : "";
        if (workItemTypesRequested) {
            discovery.workItemTypesUpdatedAt = discovery.workItemTypesCurrentForProjectId ? Date.now() : 0;
        }
        debugDiscovery("selector-populated", {
            projectConfigId,
            selector: "workItemType",
            status: discovery.workItemTypes.status,
            backendOptionCount: lookupOptionCount(workItemTypes),
            renderedOptionCount: renderedOptionCount(discovery.workItemTypes)
        });
    } else {
        discovery.projectValidationCurrentFor = "";
        discovery.projectValidationUpdatedAt = 0;
        discovery.workItemTypesCurrentForProjectId = "";
        discovery.workItemTypesUpdatedAt = 0;
        projectLayout(projectConfigId).collapsed = false;
        discovery.workItemTypes = { status: "NOT_CHECKED", message: t("message.verifyBeforeType"), values: [], optionCount: 0 };
    }
    renderProjects();
    scheduleLocalPreview("verify-project", false);
}

async function runExplicitFieldAndStateDiscovery(projectConfigId) {
    readFormToState();
    ensureDiscovery();
    const project = projectByConfigId(projectConfigId);
    if (!project) {
        return;
    }
    const type = project.supportedWorkItemTypes?.[0] || "";
    if (!project.name || !type) {
        return;
    }
    const discovery = projectDiscovery.get(projectConfigId);
    if (!isProjectVerified(discovery, project)) {
        discovery.fields = { status: "NOT_CHECKED", message: t("message.verifyProjectFirst"), values: [], optionCount: 0 };
        discovery.states = { status: "NOT_CHECKED", message: t("message.verifyProjectFirst"), values: [], optionCount: 0 };
        renderProjects();
        scheduleLocalPreview("load-fields-states-not-ready", false);
        return;
    }
    const projectName = (project.name || "").trim();
    const requestToken = discovery.requestToken;
    const dependencyKey = structuralTypeKey(discovery, project, type);
    let fields = discovery.fields;
    let fieldsRequested = false;
    if (discovery.fieldsCurrentForProjectIdAndWorkItemType === dependencyKey
            && structuralLookupIsCurrent(discovery.fields, discovery.fieldsUpdatedAt)) {
        skipCurrentStructuralDiscovery(projectConfigId, discovery, "loadFields:current", dependencyKey);
    } else {
        fieldsRequested = true;
        fields = await runStructuralDiscovery(projectConfigId, discovery, "loadFields", dependencyKey,
                "frontendLoadFieldsCallCount", () => discover("load-fields", "/api/config-ui/discovery/fields", {
                    organization: state.ado.organization,
                    project: projectName,
                    workItemType: type
                }));
    }
    if (!isCurrentDiscoveryRequest(projectConfigId, requestToken, projectName, type)) {
        return;
    }
    let states = discovery.states;
    let statesRequested = false;
    if (discovery.statesCurrentForProjectIdAndWorkItemType === dependencyKey
            && structuralLookupIsCurrent(discovery.states, discovery.statesUpdatedAt)) {
        skipCurrentStructuralDiscovery(projectConfigId, discovery, "loadStates:current", dependencyKey);
    } else {
        statesRequested = true;
        states = await runStructuralDiscovery(projectConfigId, discovery, "loadStates", dependencyKey,
                "frontendLoadStatesCallCount", () => discover("load-states", "/api/config-ui/discovery/states", {
                    organization: state.ado.organization,
                    project: projectName,
                    workItemType: type
                }));
    }
    if (!isCurrentDiscoveryRequest(projectConfigId, requestToken, projectName, type)) {
        return;
    }
    discovery.fields = normalizeOptionsLookup(fields, t("message.noFields"), "fields", projectConfigId);
    discovery.states = normalizeOptionsLookup(states, t("message.noStates"), "states", projectConfigId);
    discovery.fieldsCurrentForProjectIdAndWorkItemType = structuralLookupSucceeded(discovery.fields)
        ? dependencyKey
        : "";
    if (fieldsRequested) {
        discovery.fieldsUpdatedAt = discovery.fieldsCurrentForProjectIdAndWorkItemType ? Date.now() : 0;
    }
    discovery.statesCurrentForProjectIdAndWorkItemType = structuralLookupSucceeded(discovery.states)
        ? dependencyKey
        : "";
    if (statesRequested) {
        discovery.statesUpdatedAt = discovery.statesCurrentForProjectIdAndWorkItemType ? Date.now() : 0;
    }
    debugDiscovery("selector-populated", {
        projectConfigId,
        selector: "fields",
        status: discovery.fields.status,
        backendOptionCount: lookupOptionCount(fields),
        renderedOptionCount: renderedOptionCount(discovery.fields)
    });
    debugDiscovery("selector-populated", {
        projectConfigId,
        selector: "states",
        status: discovery.states.status,
        backendOptionCount: lookupOptionCount(states),
        renderedOptionCount: renderedOptionCount(discovery.states)
    });
    renderProjects();
    scheduleLocalPreview("load-fields-states", false);
}

async function loadIdentityOptions(projectConfigId, role, query, requestVersion) {
    readFormToState();
    ensureDiscovery();
    const project = projectByConfigId(projectConfigId);
    const discovery = projectDiscovery.get(projectConfigId);
    if (!project || !isProjectVerified(discovery, project)) {
        const searchState = ensureIdentitySearchState(projectConfigId, role);
        searchState.lookup = { status: "NOT_CHECKED", message: t("message.verifyBeforeUsers"), values: [], optionCount: 0 };
        updateIdentityPicker(projectConfigId, role);
        return;
    }
    const searchState = ensureIdentitySearchState(projectConfigId, role);
    const requestQuery = normalizedIdentityQuery(query);
    if (searchState.requestVersion !== requestVersion || normalizedIdentityQuery(searchState.query) !== requestQuery) {
        incrementStaleIgnored(`${role}Users`, "identity-query-changed-before-request", projectConfigId);
        return;
    }
    searchState.debouncePending = false;
    searchState.searching = true;
    searchState.frontendRequestCount += 1;
    updateIdentityPicker(projectConfigId, role);
    const result = await discover("search-users", "/api/config-ui/discovery/users/search", {
        organization: state.ado.organization,
        project: project.name,
        query: requestQuery
    });
    if (ensureIdentitySearchState(projectConfigId, role).requestVersion !== requestVersion
            || normalizedIdentityQuery(ensureIdentitySearchState(projectConfigId, role).query) !== requestQuery) {
        incrementStaleIgnored(`${role}Users`, "identity-query-changed", projectConfigId);
        return;
    }
    searchState.lookup = normalizeOptionsLookup(
            result,
            t("message.noIdentities"),
            `${role}Users`,
            projectConfigId
    );
    cacheIdentityOptions(selectorOptions(searchState.lookup));
    if (searchState.lookup.status === "VALID" || searchState.lookup.status === "WARNING") {
        putIdentitySearchCache(
                projectConfigId,
                role,
                state.ado.organization,
                project.name,
                requestQuery,
                selectorOptions(searchState.lookup)
        );
    }
    const backendDiagnostics = result.diagnostics || {};
    searchState.backendCacheHit = backendDiagnostics.backendCacheHit === true;
    searchState.backendCacheMiss = backendDiagnostics.backendCacheMiss === true;
    searchState.backendRequestCount = Number(backendDiagnostics.backendRequestCount ?? searchState.backendRequestCount);
    searchState.adoRequestCount = Number(backendDiagnostics.adoRequestCount ?? searchState.adoRequestCount);
    searchState.candidatePoolSource = String(backendDiagnostics.candidatePoolSource ?? "");
    searchState.candidatePoolSize = Number(backendDiagnostics.candidatePoolSize ?? 0);
    searchState.candidatePoolCacheHit = backendDiagnostics.candidatePoolCacheHit === true;
    searchState.projectPoolMatchCount = Number(backendDiagnostics.projectPoolMatchCount ?? 0);
    searchState.graphFallbackAttempted = backendDiagnostics.graphFallbackAttempted === true;
    searchState.graphCacheHit = backendDiagnostics.graphCacheHit === true;
    searchState.graphNegativeCacheHit = backendDiagnostics.graphNegativeCacheHit === true;
    searchState.avatarCacheHitCount = Number(backendDiagnostics.avatarCacheHitCount ?? 0);
    searchState.avatarCacheMissCount = Number(backendDiagnostics.avatarCacheMissCount ?? 0);
    searchState.avatarAdoRequestCount = Number(backendDiagnostics.avatarAdoRequestCount ?? 0);
    searchState.searching = false;
    debugDiscovery("selector-populated", {
        projectConfigId,
        selector: `${role}Users`,
        status: searchState.lookup.status,
        backendOptionCount: lookupOptionCount(result),
        renderedOptionCount: renderedOptionCount(searchState.lookup),
        queryLength: requestQuery.length
    });
    updateIdentityPicker(projectConfigId, role);
}

async function updateYamlPreviewLocalOnly(showStatus = true, trigger = "manual-preview") {
    for (const project of state.ado.projects) {
        const projectConfigId = ensureProjectConfigId(project);
        suppressStructuralDiscovery(projectConfigId, "yaml-preview", structuralProjectKey(project));
    }
    const payload = await postConfig("/api/config-ui/preview");
    recordLocalValidation(trigger);
    yamlOutputEl.textContent = payload.yaml || "";
    renderValidation(payload);
    if (showStatus) {
        if (payload.draftYamlAvailable) {
            setStatus(payload.finalYamlAllowed ? t("status.draftAllowed") : t("status.draftBlocked"));
        } else {
            setStatus(t("status.yamlBlocked"), true);
        }
    }
    return payload;
}

async function runStrictAdoValidation(trigger) {
    recordStrictValidation(trigger);
    return postConfig("/api/config-ui/validate");
}

async function saveWithStrictAdoValidation() {
    recordStrictValidation("save");
    return postConfig("/api/config-ui/save");
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
        message: sanitizeMessage(projectOptionLookup.message || t("message.projectSelectorRendered"))
    });
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
            <span>${escapeHtml(optionLabel(option))}</span>
        </li>
    `).join("");
    discoveredProjectsDebugEl.innerHTML = `
        <strong>${escapeHtml(t("diagnostics.discoveredProjects"))}</strong>
        <p class="note compact">${escapeHtml(t("diagnostics.discoveredProjectsNote"))}</p>
        <ul>${rows || `<li>${escapeHtml(t("diagnostics.noProjectsRendered"))}</li>`}</ul>
    `;
    for (const button of discoveredProjectsDebugEl.querySelectorAll("[data-project-value]")) {
        button.addEventListener("click", () => {
            if (state.ado.projects.length === 0) {
                state.ado.projects.push(createProjectModel());
                ensureDiscovery();
            }
            state.ado.projects[0].name = button.getAttribute("data-project-value") || "";
            clearChildSelections(state.ado.projects[0]);
            clearDiscovery(ensureProjectConfigId(state.ado.projects[0]), "project");
            renderProjects();
            scheduleLocalPreview("debug-project-selection");
        });
    }
    return state.ado.projects
        .filter((project) => ensureProjectConfigId(project) !== projectConfigId)
        .some((project) => normalizedText(project.name) === normalizedProjectName);
}

function handleGlobalInput() {
    readFormToState();
    scheduleLocalPreview("global-field-change");
}

function handleOrganizationChanged() {
    readFormToState();
    projectOptionLookup = { status: "NOT_CHECKED", message: t("message.organizationChanged"), values: [] };
    for (const project of state.ado.projects) {
        clearChildSelections(project);
    }
    resetProjectScopedState();
    renderProjectSelectors();
    renderProjects();
    scheduleLocalPreview("organization-change");
}

async function initialize() {
    applyStaticTranslations();
    setStatus(t("status.loading"));
    renderDiagnosticsPanel();
    const response = await fetch("/api/config-ui/model");
    state = await response.json();
    if (!Array.isArray(state.ado.projects)) {
        state.ado.projects = [];
    }
    state.ado.projects = state.ado.projects.map(prepareProjectState);
    resetProjectScopedState();
    fillFormFromState();
    saveBtn.disabled = true;
    setStatus(t("status.loaded"));
    await updateYamlPreviewLocalOnly(false, "initial-load");
}

document.getElementById("loadProjects").addEventListener("click", () => {
    runExplicitProjectListDiscovery().catch((error) => setStatus(error.message, true));
});

languageSelectorEl?.addEventListener("change", (event) => {
    setLanguage(event.target.value);
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
    ensureDiscovery();
    renderProjects();
    scheduleLocalPreview("add-project");
});

document.getElementById("previewBtn").addEventListener("click", async () => {
    try {
        await updateYamlPreviewLocalOnly(true, "manual-preview");
    } catch (error) {
        setStatus(error.message, true);
    }
});

document.getElementById("validateConfigBtn").addEventListener("click", async () => {
    try {
        const validation = await runStrictAdoValidation("validate-generated-config");
        const preview = lastPreview || await updateYamlPreviewLocalOnly(false, "strict-validation-context");
        renderValidation({
            ...preview,
            validation,
            finalYamlAllowed: !!validation.canGenerateFinalYaml
        });
        setStatus(validation.canGenerateFinalYaml ? t("status.draftAllowed") : t("status.draftBlocked"),
                !!validation.hasBlockingErrors);
    } catch (error) {
        setStatus(error.message, true);
    }
});

document.getElementById("saveBtn").addEventListener("click", async () => {
    try {
        if (!isUiAdoDiscoveryCurrent()) {
            setStatus(t("status.saveBlocked"), true);
            saveBtn.disabled = true;
            return;
        }
        const payload = await saveWithStrictAdoValidation();
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
