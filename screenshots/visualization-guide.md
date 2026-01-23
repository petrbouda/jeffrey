# Jeffrey Visualization - Screenshot Capture Guide

## Overview

This document provides a comprehensive guide for capturing screenshots of all pages in the Visualization section, including Flamegraphs and SubSecond analysis views.

---

## Profile Selection

1. Click **"Profiles"** in the sidebar menu
2. Select the profile: `jeffrey-persons-direct-serde-cpu.jfr`
3. Click on the **"Visualization"** tab in the top navigation

---

## Navigation Structure

Visualization:
- FLAMEGRAPHS:
  - Primary - Event type selection page with cards
  - Differential (locked without secondary profile)
- SUBSECOND:
  - Primary - Event type selection page with cards
  - Differential (locked without secondary profile)

### Flamegraph Event Types (Primary)
- Execution Samples (blue card) - CPU profiling data
- Wall-Clock Samples (yellow card) - Wall-clock time samples
- Allocation Samples (green card) - Memory allocation samples
- Java Monitor Blocked (red card) - Lock contention
- Java Thread Park (red card) - Thread parking events
- Java Monitor Wait (red card) - Monitor wait events

### SubSecond Event Types (Primary)
- Execution Samples
- Wall-Clock Samples
- Allocation Samples

---

## Folder Structure

```
screenshots/target/visualization/
- flamegraphs/
  - primary-selection.png          # Event type selection page
  - execution-samples-timeline.png # Timeline view
  - execution-samples-flamegraph.png # Flamegraph visualization (scrolled)
  - wall-clock-samples-timeline.png
  - wall-clock-samples-flamegraph.png
  - allocation-samples-timeline.png
  - allocation-samples-flamegraph.png
- subsecond/
  - primary-selection.png          # Event type selection page
  - execution-samples-timeline.png # Timeline with mini-map
  - execution-samples-heatmap.png  # Heatmap visualization (scrolled)
  - wall-clock-samples-timeline.png
  - wall-clock-samples-heatmap.png
```

---

## Detailed Capture Plan

### FLAMEGRAPHS Section

#### 1. Primary Selection Page (/flamegraphs/primary)
Navigate: Click **"Primary"** under FLAMEGRAPHS in sidebar
- **primary-selection.png** - Full page showing all event type cards
- Scroll down if needed to capture all cards (blocked events at bottom)

#### 2. Execution Samples Flamegraph
Navigate: Click **"View Flamegraph"** on Execution Samples card
- **execution-samples-timeline.png** - Timeline chart at top of page
- **execution-samples-flamegraph.png** - Scroll down to capture the flamegraph/icicle visualization

#### 3. Wall-Clock Samples Flamegraph
Navigate: Go back to Primary, click **"View Flamegraph"** on Wall-Clock Samples card
- **wall-clock-samples-timeline.png** - Timeline chart
- **wall-clock-samples-flamegraph.png** - Flamegraph visualization

#### 4. Allocation Samples Flamegraph
Navigate: Go back to Primary, click **"View Flamegraph"** on Allocation Samples card
- **allocation-samples-timeline.png** - Timeline chart
- **allocation-samples-flamegraph.png** - Flamegraph visualization

### SUBSECOND Section

#### 5. Primary Selection Page (/subsecond/primary)
Navigate: Click **"Primary"** under SUBSECOND in sidebar
- **primary-selection.png** - Full page showing event type cards

#### 6. Execution Samples SubSecond
Navigate: Click **"Show SubSecond Graph"** on Execution Samples card
- **execution-samples-timeline.png** - Timeline with time range selector
- **execution-samples-heatmap.png** - Scroll down to capture the heatmap visualization

#### 7. Wall-Clock Samples SubSecond
Navigate: Go back to Primary, click **"Show SubSecond Graph"** on Wall-Clock Samples card
- **wall-clock-samples-timeline.png** - Timeline view
- **wall-clock-samples-heatmap.png** - Heatmap visualization

---

## Instructions

For each page:
1. Navigate to the page via sidebar menu or button clicks
2. Wait for charts and visualizations to fully render
3. Take screenshot of the initial (top) view
4. Scroll down to capture additional content (flamegraphs, heatmaps)
5. Use descriptive filenames as specified above

### Navigating Back
- Use browser back button or click sidebar menu items to return to selection pages
- From flamegraph/subsecond views, click **"Primary"** in sidebar to return to selection

### Tips
- Flamegraphs may take a moment to render - wait for the visualization to appear
- SubSecond heatmaps show time-based activity patterns - ensure the full grid is visible
- The timeline charts show sample distribution over time - useful context for the visualizations
