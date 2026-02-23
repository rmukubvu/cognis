---
name: send-sms
description: Send SMS through Cognis MCP tools with contact alias memory. Use when users ask to save a phone-number alias (for example wife, mom, driver) or to send a text message to a known person by alias or number.
---

# SMS Via MCP

Use this workflow to keep SMS behavior provider-agnostic and memory-driven.

## Workflow

1. Discover available MCP capabilities.
2. Resolve who to text (alias or raw phone number).
3. Persist useful aliases for future requests.
4. Call the MCP SMS tool with normalized arguments.
5. Return a clear success or failure summary.

## 1) Discover MCP Tool

Call:

- Tool: `mcp`
- Input: `{"action":"list_tools"}`

Find an SMS-capable tool name. Prefer `twilio.send_sms` when present.

If no SMS tool exists, stop and explain that no MCP SMS provider is currently exposed.

## 2) Resolve Recipient

If user says "text my wife" or similar alias:

1. Try `profile` first (relationships or preferences).
2. Try `memory` recall next.
3. If still missing, ask for the phone number and whether to save alias.

If user provides raw number, use it directly and optionally ask to save alias.

## 3) Persist Alias For Future Turns

When user provides or confirms alias mapping, store both:

1. `profile` preference:
   - action: `set_preference`
   - key: `contacts.<alias>.sms`
   - value: E.164 number (for example `+14379615920`)
2. `memory`:
   - action: `remember`
   - content: `<alias> sms number is <E164>`
   - tags: `["contact","sms","alias"]`

Always normalize to E.164 (`+` and country code) before saving.

## 4) Send SMS

Call:

- Tool: `mcp`
- Input:
  `{"action":"call_tool","tool":"twilio.send_sms","arguments":{"body":{"From":"<from>","To":"<to>","Body":"<message>"}}}`

Rules:

1. Require non-empty `Body`.
2. Require explicit `To`.
3. If `From` is missing, ask user or use configured default only if known.
4. Do not fabricate delivery status; report only returned fields.

## 5) Response Format

On success:

1. Confirm recipient and short message preview.
2. Include provider response identifiers if available (`sid`, `status`).

On failure:

1. Show the concrete error from MCP/tool response.
2. Provide one next action (fix number, fix sender, retry).
