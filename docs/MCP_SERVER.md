# Cognis MCP Server (Java, No Spring)

`cognis-mcp-server` is a lightweight MCP-style integration bridge for external APIs.

Current provider adapters:

- Stripe
- Amazon
- Uber
- Lyft
- Instacart
- DoorDash
- Twilio

## Goals

- Keep Cognis core clean while adding third-party integrations.
- Provide a stable tool contract (`/mcp/tools`, `/mcp/call`).
- Make providers easy to add via adapter classes.

## Run

```bash
mvn -pl cognis-mcp-server -am package
java -jar cognis-mcp-server/target/cognis-mcp-server-0.1.0-SNAPSHOT.jar
```

Default port: `8791`

## Endpoints

- `GET /healthz`
- `GET /mcp/tools`
- `POST /mcp/call`

Call example:

```bash
curl -X POST http://127.0.0.1:8791/mcp/call \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "stripe.create_payment_intent",
    "arguments": {
      "body": {"amount": 1000, "currency": "usd"}
    }
  }'
```

## Environment Variables

General:

- `COGNIS_MCP_PORT` (default `8791`)
- `COGNIS_MCP_HTTP_TIMEOUT_SECONDS` (default `25`)

Providers:

- Stripe: `STRIPE_BASE_URL`, `STRIPE_API_KEY`
- Amazon: `AMAZON_BASE_URL`, `AMAZON_API_KEY`
- Uber: `UBER_BASE_URL`, `UBER_API_KEY`
- Lyft: `LYFT_BASE_URL`, `LYFT_API_KEY`
- Instacart: `INSTACART_BASE_URL`, `INSTACART_API_KEY`
- DoorDash: `DOORDASH_BASE_URL`, `DOORDASH_API_KEY`
- Twilio: `TWILIO_BASE_URL`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`

## Notes

- This is a bootstrap integration layer with starter operations and provider adapters.
- Real production integrations will need provider-specific payload validation, idempotency, retries, and webhook/event handling.
