# Documentation Sync Checker

Audit documentation accuracy by comparing user-visible features against documentation pages.

## Instructions

1. **Scan frontend routes** from `jeffrey-microscope/pages-microscope/src/router/index.ts` to identify all user-facing features and pages.

2. **Scan documentation pages** from `jeffrey-pages/src/views/docs/` to identify all documented features.

3. **Compare using the mapping table** from CLAUDE.md:

   | Code module | Documentation pages |
   |---|---|
   | `jeffrey-microscope/core-microscope` | `jeffrey-pages/src/views/docs/platform/` |
   | `jeffrey-server/core-server` | `jeffrey-pages/src/views/docs/platform/` (scheduler) |
   | `jeffrey-microscope/profiles/profile-management` | `jeffrey-pages/src/views/docs/profiles/` |
   | `jeffrey-cli/` | `jeffrey-pages/src/views/docs/cli/` |
   | Architecture changes | `jeffrey-pages/src/views/docs/architecture/` |
   | Deployment changes | `jeffrey-pages/src/views/docs/deployments/` |

4. **Check for gaps**:
   - Features visible in the frontend (routes, navigation items, sidebar menu) that lack documentation
   - Documentation pages that reference features no longer present in the code
   - Recently changed code modules (via `git log --oneline -20`) without corresponding doc updates

5. **Report findings**:
   - **Missing documentation**: Features/pages without docs (list the route, component, and suggested doc location)
   - **Stale documentation**: Doc pages referencing removed or renamed features
   - **Recently changed, possibly outdated**: Code changes in the last 20 commits that may require doc updates
   - For each finding, suggest the specific action needed

6. If documentation is in sync, confirm with a brief summary of what was checked.

## When to Use

Run this agent:
- Before deploying documentation (via `/update-jeffrey-pages` command)
- After adding new features or pages to the frontend
- After removing or renaming existing features
- Periodically to catch documentation drift
