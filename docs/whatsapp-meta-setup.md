# WhatsApp via Meta Cloud API — setup guide

Cognis supports inbound + outbound WhatsApp messages directly through
the [Meta Cloud API](https://developers.facebook.com/docs/whatsapp/cloud-api).
**No Twilio account needed. No per-message cost beyond Meta's conversation pricing.**

---

## Prerequisites

1. A [Meta Business Account](https://business.facebook.com)
2. A WhatsApp Business API phone number (verified in Meta Business Manager)
3. A public HTTPS URL for the webhook — use [ngrok](https://ngrok.com) for local dev

---

## 1 — Get your credentials from Meta

In [Meta Business Manager](https://business.facebook.com) → **WhatsApp** → **API Setup**:

| Field | Where to find it |
|---|---|
| **Phone Number ID** | WhatsApp → Phone Numbers → select number → *Phone number ID* |
| **Access Token** | WhatsApp → API Setup → *Temporary access token* (or create a System User token) |
| **App Secret** | Apps → select app → Settings → Basic → *App Secret* |

---

## 2 — Add to `~/.cognis/config.json`

```json
{
  "whatsapp": {
    "provider":       "meta",
    "accessToken":    "EAAxxxxxxxxx...",
    "phoneNumberId":  "123456789012345",
    "verifyToken":    "my_random_verify_token",
    "appSecret":      "your_meta_app_secret"
  }
}
```

- **`verifyToken`** — any random string you choose; you will paste the same value in Meta's webhook settings.
- **`appSecret`** — enables HMAC-SHA256 signature validation on every inbound request. Strongly recommended.

---

## 3 — Expose Cognis to the internet (local dev)

```bash
ngrok http 8787
# → Forwarding https://abc123.ngrok.io → http://localhost:8787
```

Your webhook URL will be: `https://abc123.ngrok.io/webhook/meta`

---

## 4 — Register the webhook with Meta

In [Meta Business Manager](https://business.facebook.com) → Apps → select app → **Webhooks** → **WhatsApp Business Account**:

1. Click **Edit**
2. **Callback URL**: `https://abc123.ngrok.io/webhook/meta`
3. **Verify Token**: the same string you set as `verifyToken` in config.json
4. Click **Verify and Save** — Meta sends `GET /webhook/meta?hub.mode=subscribe&hub.verify_token=...&hub.challenge=...`; Cognis echoes back the challenge automatically.
5. Subscribe to the **messages** field.

---

## 5 — Start Cognis

```bash
java -jar cognis-app.jar gateway
```

You should see:
```
Meta Cloud API webhook active: GET/POST /webhook/meta
Gateway started on http://127.0.0.1:8787
```

---

## 6 — Test it

Send a WhatsApp message to your Business number. Cognis receives it at `POST /webhook/meta`,
runs it through the AgentOrchestrator, and replies via the Meta Cloud API — zero Twilio.

---

## Running under StratusOS (Tier 1b)

When Cognis is supervised by StratusOS, add to `stratus.toml`:

```toml
[cognis]
enabled     = true
jar         = "/opt/cognis/cognis-app.jar"
gateway_url = "http://localhost:7070"

[[cognis.vertical]]
name         = "humanitarian"
token_budget = 50000
```

Every inbound WhatsApp message is now:
- **Policy enforced** — StratusOS applies capability checks before the agent acts
- **Audited** — every tool call appears in the portal trace view at `/agents/{id}`
- **Token budgeted** — agent cannot exceed the configured spending limit
- **Sandboxed** — Landlock + seccomp applied if `sandbox.enabled = true`

---

## Architecture summary

```
WhatsApp user
     │ sends message
     ▼
Meta Cloud API
     │ POST /webhook/meta  (HMAC-SHA256 verified)
     ▼
Cognis GatewayServer (:8787)
     │ MetaWebhookHandler.messageHandler()
     ▼
AgentOrchestrator  ──► StratusOS /syscall (tool execution)
     │                          │
     │                    policy.toml
     │                    ledger (audit)
     │                    sandbox (Landlock+seccomp)
     ▼
MetaCloudApiSender
     │ POST graph.facebook.com/v18.0/{phoneNumberId}/messages
     ▼
WhatsApp user receives reply
```
