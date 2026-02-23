# Cognis Migration Plan

## Objective
Port the HKUDS reference implementation to Java with production-grade maintainability, testability, and performance while preserving behavior parity.

## Baseline Decisions
- Java: 21 LTS
- Build: Maven multi-module
- Architecture style: Hexagonal (ports/adapters)
- Concurrency: virtual threads for blocking I/O, bounded executors for CPU-bound tasks
- Serialization: Jackson
- CLI: Picocli
- Logging: SLF4J + Logback
- Tests: JUnit 5 + AssertJ

## Migration Principles
- Keep domain logic framework-agnostic (`cognis-core`)
- Add adapters behind interfaces (providers, channels, tools)
- Migrate feature-by-feature with parity tests before extension
- Avoid monolithic PRs; ship incremental vertical slices

## Module Layout
- `cognis-core`: domain model, orchestration, provider/tool/channel ports, scheduler, config model
- `cognis-cli`: command layer and UX
- `cognis-app`: wiring/composition root

## Phase Plan

### Phase 0: Foundation (completed in this bootstrap)
- Multi-module build
- Core orchestration skeleton
- Provider router + registry
- Tool registry
- Unit test harness
- Migration documentation

### Phase 1: Config + CLI parity
- Implement config loader with defaults and schema validation
- Recreate `onboard`, `agent`, `gateway`, `status` command surfaces
- Add integration tests for config merge/refresh behavior

### Phase 2: Provider parity
- OpenAI-compatible HTTP provider (streaming + tool calling) [done]
- Anthropic Messages API adapter [done]
- Provider retry/backoff + fallback chain [done for openrouter/openai/anthropic aliases]
- OAuth adapters for Codex/Copilot flows
- Add provider contract tests (mock server based) [done for OpenAI-compatible + Anthropic]

### Phase 3: Tooling parity
- Shell/filesystem tools [done]
- Workspace restriction and permission guards [done for filesystem/shell]
- message/spawn/cron/web tools [pending]
- SSRF and path traversal protections
- Add tool-level tests and policy tests

### Phase 4: Scheduler + memory
- Cron service and job store [done]
- Session history persistence (filesystem first) [done]
- Recovery tests and state evolution tests [partially done]

### Phase 5: Channel adapters
- Telegram/Discord/Slack/WhatsApp bridge first
- DingTalk/Feishu/Email/QQ second
- Channel conformance tests with fake bus

### Phase 6: Hardening
- Metrics + tracing hooks
- Structured error taxonomy
- Load tests for concurrent sessions
- Security review and dependency audit

## Testing Strategy
- Unit tests: pure domain/service behavior
- Contract tests: provider + channel adapter conformance
- Integration tests: CLI + config + runtime wiring
- Performance tests: benchmark high-throughput tool and message flows

## Performance Guidelines
- Prefer immutable records and copy-on-write boundaries
- Keep hot-path allocations low in agent loop
- Use streaming APIs for provider responses
- Isolate channel I/O from orchestration CPU work
- Add backpressure for outbound queues

## Definition of Done per Feature
- Behavior parity confirmed against upstream reference behavior
- Unit tests and contract tests added
- Error handling and logging verified
- No cross-layer leakage (domain independent of transport frameworks)
