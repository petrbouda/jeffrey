# Microscope JFR — Initial Recording Dashboard (Mockups)

Five HTML mockups exploring an "initial" dashboard for a JFR recording in
**jeffrey-microscope**. Open any file in a browser.

Each mockup is self-contained (Poppins + Bootstrap Icons via CDN) and uses
Jeffrey's real design tokens (`#5e64ff` primary, the card/border/shadow system,
`Poppins` typography). All identity fields come from the `jeffrey.AppInformation`
JFR event (PR #104): `workspaceId, projectId, projectName, projectLabel,
instanceId, sessionId, sessionOrder, attributes, provisionedAt, jvmStartedAt`.

When the event is absent the identity panel falls back to
**"Recording not handled by Jeffrey"** (demonstrated in mockups 2 and 5).

| # | File | Idea |
|---|------|------|
| 1 | `mockup-1-mission-control.html`  | Gradient identity hero + KPI tiles + bento grid (top-5 events, event-over-time sparkline, JVM/GC/attributes) |
| 2 | `mockup-2-identity-spotlight.html` | Sticky "passport" identity card + stat tiles + conic event-distribution donut; shows the fallback state |
| 3 | `mockup-3-dense-analyst.html` | Closest to the current app — PageHeader, identity+GC sidebar, mini-stat grid, DataTable-style top events |
| 4 | `mockup-4-timeline-first.html` | Slim identity strip + large layered events-over-time area chart as the hero |
| 5 | `mockup-5-glass-cards.html` | Glassmorphism hero + gradient stat cards with sparklines; styled fallback variant |

## Data surfaced
Application identity · total events + type count · duration · size · event source ·
chunk count · top-5 event types (count + share) · events-over-time · JVM / vendor /
GC / heap · OS / cores / container · GC summary (collections, p99 pause, overhead) ·
CPU avg/peak · threads (incl. virtual) · custom attributes.

> These are design mockups only — no application wiring yet.
