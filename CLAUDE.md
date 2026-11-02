@AGENTS.md

## Claude Code

- The `rikr` MCP server from `.mcp.json` lets you inspect and rename the bytecode of any JAR while working, for
  example to check what a change to the rename engine does on a real JAR. Approve it once with `/mcp`.
- To see a GUI change working, build with `mvn -q -DskipTests package` and start the GUI; there is no automated UI
  driver in the repository yet.
