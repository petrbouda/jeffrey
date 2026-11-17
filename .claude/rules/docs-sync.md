---
paths:
  - "jeffrey-pages/**"
---

## Jeffrey Pages documentation rules

Paths below are relative to `jeffrey-pages/src/views/docs/`.

| Code module | Documentation pages |
|---|---|
| `jeffrey-microscope/core-microscope`, `jeffrey-microscope/pages-microscope` | `docs/microscope/` (overview, quick start, recordings, storage, profiler builder), `docs/microscope/configuration/`, `docs/connectors/` (the Connector page: workspaces, projects, instances) |
| `jeffrey-microscope/connectors/**` (connector-api, volume-connector, azure-files-connector, local-connector) | `docs/connectors/` — overview, `ConnectorsDeclaringPage.vue` (what every kind shares: the common `jeffrey.microscope.connector.name`, read-only, startup), `ConnectorsAvailablePage.vue` (the catalog), one page per connector kind holding its own `jeffrey.microscope.connector.<kind>.*` block (`ConnectorAzureFilesPage.vue`, `ConnectorLocalPage.vue`), workspaces, `projects/`; plus the pointers in `docs/microscope/configuration/MicroscopeConfigApplicationPropertiesPage.vue#connector` and `docs/DocsMicroscopePage.vue#connector`. Browsing pages stay kind-neutral; storage-specific behaviour goes on the kind's page. A new connector kind needs: its page, a route, a child in the *Available Connectors* sidebar group, a row in the catalog table, a link card on the catalog page, and a mention in the overview's *Available Connectors* section |
| `jeffrey-microscope/profiles/**` | `docs/microscope/profiles/` — one page per analysis feature |
| `volume-scheduler` | `docs/volume-scheduler/` (overview, architecture, storage, `configuration/`, the REST API page `VolumeSchedulerApiPage.vue`); its `jeffrey.volume-scheduler.*` keys on `configuration/VolumeSchedulerConfigurationPage.vue`, its `/api/v1` endpoints and error codes on the API page |
| Recording sessions (the layout the provisioner writes and the volume scheduler ages) | `docs/connectors/recording-sessions/` (overview, configuration, lifecycle) — a section of the Connectors sidebar |
| Deployment walkthrough (jeffrey-testapp: JIB, shared volume, provisioner, Helm chart) | `docs/deployment/` — its own docs product, Deployment by Example |
| tracing instrumentation (`utilities/`) | `docs/tracing/`, `docs/tracing/tracer-api/` (one page per Tracer API method) |
| `jeffrey-provisioner/` | `docs/provisioner/` |
| Jib build | `docs/jib/` |
| MCP server (`jeffrey-microscope/mcp-protocol`, `jeffrey-microscope/profiles/mcp-server`, `core-microscope/**/mcp/**`) + `jeffrey-agent-plugin/` | `docs/microscope-mcp/` — overview, enabling, every client, Claude Code / Codex / Gemini, tool reference, skills, agents, recipes |
| IntelliJ plugin | `docs/intellij-plugin/` |
| Architecture changes | `docs/architecture/ArchitectureOverviewPage.vue` |
| Install/onboarding | `docs/microscope/MicroscopeQuickStartPage.vue`, `docs/DocsIndexPage.vue` |

### Registering a page
- A new page needs **two** registrations: a route in `jeffrey-pages/src/router/index.ts` and a sidebar entry in the product's `DocSection[]` in `jeffrey-pages/src/composables/useDocsNavigation.ts`. Route-only pages are invisible in sidebar, breadcrumbs and prev/next (all derived from that array).
- A new product panel needs: `Product` union + `PRODUCTS` entry, a `<NAME>_SEGMENTS` set with a branch in `getProductForPath`, its `DocSection[]` plus a branch in `navigationForProduct` and the `docsNavigation` union, a `DocsProductCard` on `DocsIndexPage.vue`, its id in `PRODUCT_ROUTE_CATEGORIES` in `components/docs/DocsSidebar.vue` (so its landing URL expands the lead section), and a `.sidebar-content.product-<id>` accent block in the same file's styles. `getAdjacentPages` derives prev/next.
- `DocsIndexPage.vue` states the MCP read-only tool count in prose; update it with `McpToolsetAssemblerTest` when tools change.
- Run the `docs-sync-checker` agent (or `/update-jeffrey-pages`) after adding, renaming or removing a user-visible feature.
