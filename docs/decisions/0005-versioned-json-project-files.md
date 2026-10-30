# 0005: Project files are versioned JSON with their own records

**Status:** accepted

## Context

The first project files were Java properties with numbered keys, which were hard to read, could not nest rename
batches, and changed meaning whenever a field was renamed in code.

## Decision

Projects are saved as JSON with Gson (`ProjectFileJson`). The file has its own records, separate from the domain
types, and a `formatVersion`. Changing what is saved means changing those records and the version; files from a
newer version are refused with a clear message, and older formats keep a reader (`LegacyProjectFile` for the
properties format), so saving converts them. Files are written to a temporary file and moved into place.

## Consequences

- Renaming a domain field cannot silently change the file format.
- Errors name the exact field that is missing, such as `renames[0].oldName`.
- Gson is the one JSON dependency, also used by the MCP server.
