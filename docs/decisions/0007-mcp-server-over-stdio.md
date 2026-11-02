# 0007: Agents use Rikr through its own MCP server over stdio

**Status:** accepted

## Context

Agentic tools such as Claude Code should be able to use Rikr's features, not only read its source. The Model
Context Protocol is the common way for them to call tools. The protocol moved from a handshake (`initialize`, up to
revision 2025-11-25) to stateless requests that carry their version (2026-07-28).

## Decision

`bin/rikr-mcp` runs `org.de.rikr.agent.RikrMcp`, an MCP server over stdio written on Gson rather than an SDK: Rikr
needs only tools, which are a small part of the protocol. It holds one headless `Project`. `McpServer` serves both
protocol eras, choosing per request from its `_meta`. Every feature that an agent could use gets a tool in
`RikrTools`, with errors the agent can act on.

## Consequences

- No extra dependency, but new protocol revisions have to be implemented by hand and checked against the spec.
- stdout carries only protocol messages; everything else goes to stderr.
- The tools are covered by `RikrMcpTest`, which drives the server through JSON-RPC messages.
