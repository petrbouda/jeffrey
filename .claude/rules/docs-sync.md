---
paths:
  - "jeffrey-pages/**"
---

## Jeffrey Pages documentation rules

Paths below are relative to `jeffrey-pages/src/views/docs/`.

| Code module | Documentation pages |
|---|---|
| `jeffrey-microscope/core-microscope` | `docs/microscope/` (overview, quick start, workspaces, recordings, storage, profiler builder), `docs/microscope/projects/`, `docs/microscope/configuration/` |
| `jeffrey-microscope/profiles/**` | `docs/microscope/profiles/` — one page per analysis feature |
| `jeffrey-hub/core-hub` | `docs/hub/` (overview, architecture, storage, gRPC API), `docs/hub/recording-sessions/`, `docs/hub/configuration/`, `docs/hub/deployment/` |
| `shared/hub-api/` proto changes | `docs/hub/HubGrpcApiPage.vue` |
| `utilities/jeffrey-heartbeat/` + starter | `docs/agent/` |
| tracing instrumentation (`utilities/`) | `docs/tracing/`, `docs/tracing/tracer-api/` (one page per Tracer API method) |
| `jeffrey-provisioner/` | `docs/provisioner/` |
| Jib build | `docs/jib/` |
| MCP server + `jeffrey-claude-plugin/` | `docs/microscope-mcp/` — overview, enabling, every client, Claude Code / Codex / Gemini, tool reference, skills, agents, recipes |
| IntelliJ plugin | `docs/intellij-plugin/` |
| Architecture changes | `docs/architecture/ArchitectureOverviewPage.vue` |
| Install/onboarding | `docs/getting-started/` |

### Registering a page
- A new page needs **two** registrations: a route in `jeffrey-pages/src/router/index.ts` and a sidebar entry in the product's `DocSection[]` in `jeffrey-pages/src/composables/useDocsNavigation.ts`. Route-only pages are invisible in sidebar, breadcrumbs and prev/next (all derived from that array).
- A new product panel needs: `Product` union + `PRODUCTS` entry, a `<NAME>_SEGMENTS` set with a branch in `getProductForPath`, its `DocSection[]` plus a branch in `navigationForProduct` and the `docsNavigation` union, and a `DocsProductCard` on `DocsIndexPage.vue`. `getAdjacentPages` derives prev/next.
- `DocsIndexPage.vue` states the MCP read-only tool count in prose; update it with `McpToolsetAssemblerTest` when tools change.
- Run the `docs-sync-checker` agent (or `/update-jeffrey-pages`) after adding, renaming or removing a user-visible feature.
