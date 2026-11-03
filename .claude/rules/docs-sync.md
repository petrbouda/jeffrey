---
paths:
  - "jeffrey-pages/**"
---

## Jeffrey Pages documentation rules

Paths below are relative to `jeffrey-pages/src/views/docs/`.

| Code module | Documentation pages |
|---|---|
| `jeffrey-microscope/core-microscope`, `jeffrey-microscope/pages-microscope` | `docs/microscope/` (overview, quick start, recordings, storage, profiler builder), `docs/microscope/configuration/`, `docs/connectors/` (the Connector page: workspaces, projects, instances) |
| `jeffrey-microscope/connectors/**` (connector-api, volume-connector, azure-files-connector) | `docs/connectors/` — overview, `ConnectorsDeclaringPage.vue` (what every kind shares: the common `jeffrey.microscope.connector.name`, read-only, startup), `ConnectorsAvailablePage.vue` (the catalog), one page per connector kind holding its own `jeffrey.microscope.connector.<kind>.*` block (`ConnectorAzureFilesPage.vue`), workspaces, `projects/`; plus the pointers in `docs/microscope/configuration/MicroscopeConfigApplicationPropertiesPage.vue#connector` and `docs/DocsMicroscopePage.vue#connector`. Browsing pages stay kind-neutral; storage-specific behaviour goes on the kind's page. A new connector kind needs: its page, a route, a child in the *Available Connectors* sidebar group, a row in the catalog table, a link card on the catalog page, and a mention in the overview's *Available Connectors* section |
| `jeffrey-microscope/profiles/**` | `docs/microscope/profiles/` — one page per analysis feature |
| `jeffrey-hub/core-hub` | `docs/hub/` (overview, architecture, storage), `docs/hub/recording-sessions/`, `docs/hub/configuration/`, `docs/hub/deployment/` |
| tracing instrumentation (`utilities/`) | `docs/tracing/`, `docs/tracing/tracer-api/` (one page per Tracer API method) |
| `jeffrey-provisioner/` | `docs/provisioner/` |
| Jib build | `docs/jib/` |
| MCP server (`jeffrey-microscope/mcp-protocol`, `jeffrey-microscope/profiles/mcp-server`, `core-microscope/**/mcp/**`) + `jeffrey-claude-plugin/` | `docs/microscope-mcp/` — overview, enabling, every client, Claude Code / Codex / Gemini, tool reference, skills, agents, recipes |
| IntelliJ plugin | `docs/intellij-plugin/` |
| Architecture changes | `docs/architecture/ArchitectureOverviewPage.vue` |
| Install/onboarding | `docs/microscope/MicroscopeQuickStartPage.vue`, `docs/DocsIndexPage.vue` |

### Registering a page
- A new page needs **two** registrations: a route in `jeffrey-pages/src/router/index.ts` and a sidebar entry in the product's `DocSection[]` in `jeffrey-pages/src/composables/useDocsNavigation.ts`. Route-only pages are invisible in sidebar, breadcrumbs and prev/next (all derived from that array).
- A new product panel needs: `Product` union + `PRODUCTS` entry, a `<NAME>_SEGMENTS` set with a branch in `getProductForPath`, its `DocSection[]` plus a branch in `navigationForProduct` and the `docsNavigation` union, and a `DocsProductCard` on `DocsIndexPage.vue`. `getAdjacentPages` derives prev/next.
- `DocsIndexPage.vue` states the MCP read-only tool count in prose; update it with `McpToolsetAssemblerTest` when tools change.
- Run the `docs-sync-checker` agent (or `/update-jeffrey-pages`) after adding, renaming or removing a user-visible feature.
