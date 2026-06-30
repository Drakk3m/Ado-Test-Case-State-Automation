# Local ADO Webhook Testing

This guide captures the quickest path to test a **real Azure DevOps Service Hook** against your local Spring Boot app and inspect the latest received event.

## 1) Start Spring Boot locally

From `ado_test_case_approval_automation/`, run:

```powershell
$env:ADO_WEBHOOK_SHARED_SECRET = "<your-shared-secret>"
mvn spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.arguments=--webhook.debug-capture-enabled=true"
```

Notes:
- `webhook.debug-capture-enabled=true` enables `GET /debug/ado-webhook/last-event`.
- Keep this debug flag for local troubleshooting only.

## 2) Expose `localhost:8080` to Azure DevOps

Use either ngrok or Cloudflare Tunnel.

### Option A: ngrok

```powershell
ngrok http 8080
```

Copy the HTTPS forwarding URL, for example:

```text
https://abc123.ngrok-free.app
```

### Option B: Cloudflare Tunnel

```powershell
cloudflared tunnel --url http://localhost:8080
```

Copy the generated HTTPS URL, for example:

```text
https://random-name.trycloudflare.com
```

## 3) Configure the Azure DevOps Service Hook

In Azure DevOps, create/update a **Work item updated** Service Hook with:
- **URL**: `<TUNNEL_URL>/api/ado/webhooks/work-item-updated`
- **Method**: `POST`
- **Header**: `X-ADO-Webhook-Secret: <your-shared-secret>`

Important:
- The header value must match `ADO_WEBHOOK_SHARED_SECRET` in your local terminal.
- If your Service Hook UI does not allow custom headers, do not expose this endpoint publicly; keep testing in a controlled sandbox only.

## 4) Trigger a real event

In the sandbox project, update a real work item (for example, change title/state/description) so Azure DevOps emits a new revision.

## 5) Inspect logs and the captured event

In the app terminal, confirm webhook handling logs are emitted.

Then query the debug endpoint:

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/debug/ado-webhook/last-event"
```

Expected behavior:
- `200 OK` with JSON when at least one event was captured.
- `404 Not Found` if no event has been captured yet.

## Safety warning

- Do **not** commit PATs, webhook secrets, private tunnel URLs, or any real credentials.
- Keep secrets in environment variables or a local secret manager only.

