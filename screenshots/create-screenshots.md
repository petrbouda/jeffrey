# Create Screenshots for Jeffrey Documentation

### Capture Screenshots Using Computer Use

Use Claude Computer Use to open a browser and capture screenshots of all JVM Internals pages.

#### Navigation Steps:

1. **Navigate to workspaces** - Click on "My Projects" or navigate to the workspaces view
2. **Select the workspace** that contains the example profiles
3. **Select the project** that contains the profile
4. **Select the profile**: `jeffrey-persons-direct-serde-cpu.jfr`
5. **Ensure JVM Internals section is active** (should be the default view)

#### Screenshots to Capture:

| # | Sidebar Item | Screenshot Filename |
|---|--------------|---------------------|
| 1 | Configuration (under ANALYSIS) | `01-configuration.png` |
| 2 | Guardian Analysis | `02-guardian-analysis.png` |
| 3 | Auto Analysis | `03-auto-analysis.png` |
| 4 | Performance Counters | `04-performance-counters.png` |
| 5 | Event Types (under EVENTS) | `05-event-types.png` |
| 6 | Event Viewer | `06-event-viewer.png` |
| 7 | JVM Flags | `07-jvm-flags.png` |
| 8 | Statistics (under THREADS) | `08-thread-statistics.png` |
| 9 | Timeline | `09-threads-timeline.png` |
| 10 | Timeseries (under HEAP MEMORY) | `10-heap-memory-timeseries.png` |
| 11 | Overview (under GARBAGE COLLECTION) | `11-gc-overview.png` |
| 12 | Timeseries (under GARBAGE COLLECTION) | `12-gc-timeseries.png` |
| 13 | Configuration (under GARBAGE COLLECTION) | `13-gc-configuration.png` |
| 14 | JIT Compilation (under COMPILER) | `14-jit-compilation.png` |
| 15 | Container Configuration (under INFRASTRUCTURE) | `15-container-configuration.png` |

#### Screenshot Capture Process:

For each page:
1. Click on the sidebar menu item to navigate to the page
2. Wait for the page to fully load (charts, tables, data should be visible)
3. Take a full-page screenshot or viewport screenshot

## Upload Screenshots

Upload the captured screenshots to the locally running server: http://localhost:3333/

## Notes

- Ensure each page is fully loaded before taking screenshots (wait for loading spinners to disappear)
- Screenshots should capture the main content area including the sidebar navigation for context
