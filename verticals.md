# Cognis Vertical Plugin Architecture

## Goal

Extract domain-specific logic out of `cognis-core` and `cognis-app` into independently deployable
vertical JARs. `cognis-core` becomes a product you ship and never touch per customer. Every
customisation — tools, HTTP routes, integration providers — lives in a vertical JAR the customer
(or you on their behalf) develops and maintains independently.

---

## Module Dependency Graph (target state)

```
cognis-core          (Tool, ToolContext, ToolRegistry, IntegrationProvider)
     ↑
cognis-sdk           (CognisVertical, RouteDefinition, RouteHandler)
     ↑
cognis-vertical-*    (customer or domain vertical JARs)
     ↑
cognis-app           (boots everything, runs ServiceLoader discovery)
```

`cognis-mcp-server` continues to hold `ToolRouter`, `AbstractHttpIntegrationProvider`, and
concrete third-party providers (Stripe, Twilio, etc.). It imports `IntegrationProvider` from
`cognis-core` (post-move).

`cognis-cli` and `cognis-mcp-server` are unchanged by this work except where noted.

---

## Design Decisions

### 1. Move `IntegrationProvider` from `cognis-mcp-server` → `cognis-core`

**Rationale:** `cognis-sdk` must be the single dependency for vertical authors. If
`IntegrationProvider` stays in `cognis-mcp-server`, every vertical would transitively pull in the
full MCP server (Undertow, OkHttp, etc.). Moving it to `cognis-core` keeps the SDK lightweight.

**Concrete change:** Move `io.cognis.mcp.server.provider.IntegrationProvider` to
`io.cognis.core.provider.IntegrationProvider`. Update all import sites in `cognis-mcp-server`.
The interface signature does not change.

### 2. `cognis-sdk` depends only on `cognis-core`

Verticals get `Tool`, `ToolContext`, and `IntegrationProvider` transitively through `cognis-sdk`.
No direct dependency on `cognis-core` is needed in vertical POMs.

### 3. `RouteDefinition` is framework-agnostic

`cognis-sdk` must not expose Undertow types — that would couple every vertical author to an HTTP
server implementation detail. Instead, `RouteDefinition` uses a plain functional interface
(`RouteHandler`) that `GatewayServer.registerRoute()` adapts internally into an Undertow
`HttpHandler`.

### 4. ServiceLoader as the discovery mechanism

Zero runtime dependency needed. No Spring, no Guice. Each vertical JAR declares its
implementation in `META-INF/services/io.cognis.sdk.CognisVertical`. `CognisApplication` calls
`ServiceLoader.load(CognisVertical.class)` after the registry and server are built, then wires
the vertical's tools, routes, and providers in.

### 5. `ToolRouter` becomes mutable (lazy registration)

Currently `ToolRouter` accepts providers at construction time only. Add a `register(IntegrationProvider)`
method so verticals discovered after construction can be plugged in before the first request is served.

---

## Interface Contracts (cognis-sdk)

### `RouteHandler.java`

```java
package io.cognis.sdk;

import java.io.InputStream;
import java.util.Map;

@FunctionalInterface
public interface RouteHandler {
    /**
     * Handle an inbound HTTP request. Implementations must write a complete HTTP response
     * via the provided {@link RouteResponse}.
     *
     * @param method  HTTP method (GET, POST, ...)
     * @param path    request path including query string
     * @param headers request headers (lowercase keys)
     * @param body    request body stream (empty stream for bodyless methods)
     * @param response mutable response handle — set status, headers, write body
     */
    void handle(String method,
                String path,
                Map<String, String> headers,
                InputStream body,
                RouteResponse response) throws Exception;
}
```

### `RouteResponse.java`

```java
package io.cognis.sdk;

public interface RouteResponse {
    void status(int code);
    void header(String name, String value);
    void body(byte[] bytes);
    void json(String json);   // convenience: sets Content-Type + body
}
```

### `RouteDefinition.java`

```java
package io.cognis.sdk;

public interface RouteDefinition {
    String method();     // "GET", "POST", ...
    String path();       // "/webhook/sms"
    RouteHandler handler();
}
```

### `CognisVertical.java`

```java
package io.cognis.sdk;

import io.cognis.core.provider.IntegrationProvider;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;

import java.util.List;

public interface CognisVertical {

    /** Unique identifier for logging and diagnostics. */
    String name();

    /** Tools this vertical contributes to the agent's tool registry. */
    List<Tool> tools();

    /** HTTP routes to register with GatewayServer. */
    List<RouteDefinition> routes();

    /**
     * Integration providers to register with ToolRouter (MCP).
     * Default: empty — verticals that don't need MCP providers skip this.
     */
    default List<IntegrationProvider> providers() {
        return List.of();
    }

    /**
     * Called once during application startup, after the ToolContext is fully assembled.
     * Use to perform any one-time initialisation (DB migrations, asset loading, etc.).
     */
    default void initialize(ToolContext context) {}
}
```

---

## Step-by-Step Build Order

### Step 1 — Create `cognis-sdk` module

**What:** New Maven module. Three interfaces (`RouteHandler`, `RouteResponse`, `RouteDefinition`,
`CognisVertical`) and nothing else. No logic.

**Location:** `/Users/robson/nanobot4j/cognis-sdk/`

**POM dependencies:** `cognis-core` only.

**Files to create:**
```
cognis-sdk/
  pom.xml
  src/main/java/io/cognis/sdk/
    RouteHandler.java
    RouteResponse.java
    RouteDefinition.java
    CognisVertical.java
  src/test/java/io/cognis/sdk/
    CognisVerticalContractTest.java   ← compile-only test: ensure interfaces are stable
```

**Add to parent POM:** `<module>cognis-sdk</module>`

**Test:** `CognisVerticalContractTest` is a compile-only sanity check — creates an anonymous
implementation of each interface and asserts that defaults compile and behave as specified.

---

### Step 2 — Move `IntegrationProvider` + update `cognis-mcp-server`

**What:** Move `IntegrationProvider` from `io.cognis.mcp.server.provider` →
`io.cognis.core.provider`. Update all import sites in `cognis-mcp-server`.

**Files to move/update:**
- Move: `cognis-mcp-server/…/provider/IntegrationProvider.java`
  → `cognis-core/…/provider/IntegrationProvider.java`
- Update imports in:
  - `AbstractHttpIntegrationProvider.java`
  - `ToolRouter.java`
  - `McpServerApplication.java`
  - All concrete providers (Stripe, Twilio, Amazon, Uber, Lyft, Instacart, Doordash)
  - `ToolRouterTest.java`

**Test:** All existing `cognis-mcp-server` tests must pass unchanged (import paths differ,
behaviour identical).

---

### Step 3 — Modify `GatewayServer` — add `registerRoute()`

**File:** `cognis-core/src/main/java/io/cognis/core/api/GatewayServer.java`

**What:** Add a `registerRoute(RouteDefinition route)` method callable before `start()`. Routes
registered via this method are added to the Undertow `PathHandler` alongside built-in routes.

**Design notes:**
- Store registered routes in a `List<RouteDefinition>` field initialised at construction.
- In the `buildHandler()` / route-wiring section of `start()`, iterate the list and attach each
  as an Undertow `HttpHandler` adapter.
- The adapter translates Undertow's `HttpServerExchange` into the SDK's `RouteHandler` signature
  and wraps the response in a `RouteResponse` implementation.
- `GatewayServer` must NOT depend on `cognis-sdk`. The adapter lives in `cognis-app` or a
  separate package that bridges the two. Alternatively, introduce a second overload:
  `registerRoute(String method, String path, io.undertow.server.HttpHandler handler)` as the
  internal API and add a bridge method in `cognis-app` that converts `RouteDefinition` →
  `HttpHandler`. This keeps `cognis-core` free of the SDK dependency.

  **Preferred approach:** Keep `GatewayServer` free of SDK types. Add:
  ```java
  // in GatewayServer
  public void registerRoute(String method, String path, HttpHandler handler)
  ```
  And wire via a bridge in `CognisApplication`:
  ```java
  RouteDefinition rd = ...;
  gateway.registerRoute(rd.method(), rd.path(), exchange -> {
      // adapt exchange → RouteHandler
  });
  ```

**New test:** `GatewayServerTest` — add a test that registers a custom POST route before server
start and asserts a real HTTP POST to that path returns the expected response.

---

### Step 4 — Modify `ToolRouter` — add `register(IntegrationProvider)`

**File:** `cognis-mcp-server/src/main/java/io/cognis/mcp/server/ToolRouter.java`

**What:** Change internal provider map from construction-time immutable to mutable; add:
```java
public synchronized void register(IntegrationProvider provider)
```

**Design notes:**
- Current: `LinkedHashMap<String, IntegrationProvider>` built once from constructor list.
- Change: Keep constructor as-is for backward compatibility; `register()` inserts additional
  providers into the same map. Synchronise on the map for thread safety (or use
  `ConcurrentHashMap`).
- Duplicate tool name registration: log a warning and allow the later registration to win
  (last-wins policy, consistent with ToolRegistry's ConcurrentHashMap put semantics).

**New test:** `ToolRouterTest` — add test that registers a provider after construction and
verifies `listTools()` and `callTool()` see the newly registered provider.

---

### Step 5 — Modify `CognisApplication` — ServiceLoader discovery loop

**File:** `cognis-app/src/main/java/io/cognis/app/CognisApplication.java`

**What:** After `ToolRegistry`, `GatewayServer`, and `ToolRouter` are assembled, add:

```java
// ServiceLoader discovery — runs after all core services are wired
ToolContext verticalContext = new ToolContext(workspacePath, services);
ServiceLoader<CognisVertical> verticals = ServiceLoader.load(CognisVertical.class);
for (CognisVertical vertical : verticals) {
    log.info("Loading vertical: {}", vertical.name());
    vertical.initialize(verticalContext);
    vertical.tools().forEach(toolRegistry::register);
    vertical.routes().forEach(route ->
        gatewayServer.registerRoute(route.method(), route.path(), toUndertowHandler(route.handler()))
    );
    vertical.providers().forEach(toolRouter::register);
}
```

**`toUndertowHandler` adapter** — private static method in `CognisApplication` (or extracted to
`io.cognis.app.VerticalAdapter`):
- Reads body bytes from `HttpServerExchange`
- Builds `Map<String, String>` headers from exchange
- Creates `RouteResponse` impl backed by exchange send methods
- Calls `RouteHandler.handle(...)` inside `exchange.dispatch()`
- Wraps exceptions: logs + returns 500 JSON error

**Add `cognis-sdk` as a dependency in `cognis-app/pom.xml`.**

**Test:** Integration test `VerticalLoaderTest` — create a minimal in-memory `CognisVertical`
implementation, verify that tools, routes, and providers are discovered and wired correctly
without starting a live server.

---

### Step 6 — Build `cognis-vertical-humanitarian`

**Location:** `/Users/robson/nanobot4j/cognis-vertical-humanitarian/`

**Purpose:** Prove the plugin pattern works end-to-end against real UNICEF use cases.

**POM dependencies:** `cognis-sdk` only (Tool, ToolContext, IntegrationProvider come transitively).

**Initial scope (two deliverables):**

#### 6a — `SupplyTrackingTool`

```
Package: io.cognis.vertical.humanitarian.supply
Class: SupplyTrackingTool implements Tool
```

- `name()` → `"supply_tracking"`
- `description()` → describes warehouse-to-recipient chain operations
- `schema()` → JSON schema with fields:
  - `action`: enum `["log_dispatch", "confirm_delivery", "check_status", "list_overdue"]`
  - `consignment_id`: string
  - `location`: string (optional)
  - `recipient_phone`: string (optional)
- `execute()` → persists and queries consignment state via a `SupplyStore` injected through
  `ToolContext.service("supplyStore", SupplyStore.class)`

**Supporting types:**
- `SupplyStore` interface (query / persist consignment records)
- `FileSupplyStore` implements `SupplyStore` (JSON-per-consignment in workspace dir)
- `Consignment` record: id, status (DISPATCHED/IN_TRANSIT/DELIVERED/OVERDUE), timestamps, recipient

#### 6b — `SmsWebhookRoute`

```
Package: io.cognis.vertical.humanitarian.webhook
Class: SmsWebhookRoute implements RouteDefinition
```

- `method()` → `"POST"`
- `path()` → `"/webhook/sms"`
- `handler()` → parses inbound Twilio/Africa's Talking SMS payload, extracts sender + body,
  routes to `AgentOrchestrator` (injected at construction) for processing, returns TwiML/plain
  ACK response

**`HumanitarianVertical` — the entry point:**

```java
package io.cognis.vertical.humanitarian;

public class HumanitarianVertical implements CognisVertical {
    @Override public String name() { return "humanitarian"; }
    @Override public List<Tool> tools() { return List.of(new SupplyTrackingTool()); }
    @Override public List<RouteDefinition> routes() { return List.of(new SmsWebhookRoute(...)); }
}
```

**ServiceLoader registration:**
```
META-INF/services/io.cognis.sdk.CognisVertical
→ io.cognis.vertical.humanitarian.HumanitarianVertical
```

**Tests:**
- `SupplyTrackingToolTest` — unit tests for each action using a temp-dir FileSupplyStore
- `SmsWebhookRouteTest` — parse Twilio/AT payloads, verify routing
- `HumanitarianVerticalTest` — verify tools() and routes() are non-empty and correctly typed

---

### Step 7 — Create `cognis-vertical-starter` (template)

**Location:** `/Users/robson/nanobot4j/cognis-vertical-starter/`

**Purpose:** Minimal starting point for any new customer vertical. Contains:
- `StarterVertical` with TODO comments explaining every extension point
- `ExampleTool` with schema and execute() stubbed out
- `ExampleRoute` with handler stubbed out
- `META-INF/services/io.cognis.sdk.CognisVertical` pre-populated
- `README.md` (inside the module) explaining how to rename and ship

---

## Module Summary (target state)

| Module | Role | Ships to customer? |
|---|---|---|
| `cognis-core` | Runtime, tools, agent loop | Yes — as dependency |
| `cognis-cli` | CLI interface | Yes — as binary |
| `cognis-mcp-server` | MCP server + third-party providers | Optional |
| **`cognis-sdk`** | Plugin interfaces only | **Yes — as compile dependency** |
| `cognis-app` | Bootstrap + ServiceLoader wiring | Yes — as executable JAR |
| **`cognis-vertical-humanitarian`** | UNICEF vertical | UNICEF only |
| **`cognis-vertical-starter`** | Template for new verticals | Every new client |

---

## Test Coverage Requirements

Every step must ship with tests. No step is done until:
- All new classes have unit tests
- All modified classes have updated tests covering the new behaviour
- `mvn test` passes across all modules with no skips

**Test naming convention:** `{ClassName}Test` in the same package as the class under test.

**Test style (matching existing patterns):**
- JUnit 5 (`@Test`, `@TempDir`, `@BeforeEach`)
- AssertJ for assertions
- `MockWebServer` (OkHttp) for HTTP boundary tests
- No Mockito — use minimal real implementations or hand-rolled fakes

---

## Execution Checklist

- [ ] Step 1 — Create `cognis-sdk` module + 4 interface files + contract test
- [ ] Step 2 — Move `IntegrationProvider` to `cognis-core` + update all imports
- [ ] Step 3 — Add `registerRoute(method, path, HttpHandler)` to `GatewayServer` + test
- [ ] Step 4 — Add `register(IntegrationProvider)` to `ToolRouter` + test
- [ ] Step 5 — Add ServiceLoader loop to `CognisApplication` + adapter + integration test
- [ ] Step 6 — Build `cognis-vertical-humanitarian`: `SupplyTrackingTool` + `SmsWebhookRoute` + `HumanitarianVertical`
- [ ] Step 7 — Create `cognis-vertical-starter` template

---

*Last updated: 2026-03-19*
