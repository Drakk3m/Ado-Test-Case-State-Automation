# Ado-Test-Case-State-Automation

Azure DevOps Test Case approval automation for V1 sandbox validation.

For local execution, safe sandbox setup, and manual validation steps, see:

* [Local Run and Azure DevOps Sandbox Validation](docs/Local%20Run%20and%20Azure%20DevOps%20Sandbox%20Validation.md)
* [Azure DevOps Sandbox Validation Playbook](docs/Azure%20DevOps%20Sandbox%20Validation%20Playbook.md)
* [Azure DevOps Write Enabled Sandbox Validation](docs/Azure%20DevOps%20Write%20Enabled%20Sandbox%20Validation.md)
* [Sandbox Validation Checklist](docs/sandbox-validation-checklist.md)
* [Pre-Sandbox Readiness Checklist](docs/pre-sandbox-readiness-checklist.md)

Sandbox configuration reminders:

* `ado.organization` is required when `ado.http-client-enabled=true`.
* `ado.projects` keys must match the Azure DevOps webhook project name exactly.
* Project names with spaces, dots, or special characters should use Spring Boot YAML bracket notation, for example `"[Example Sandbox Project 2.0]"`.
* `ado.dry-run=true` still performs real Azure DevOps reads, but suppresses PATCH and comment writes.
