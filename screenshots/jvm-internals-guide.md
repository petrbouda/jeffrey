# Jeffrey JVM Internals - Screenshot Capture Guide

## Overview

This document provides a comprehensive guide for capturing screenshots of all pages and selected tabs in the JVM Internals section.

---

## Navigation Structure

JVM Internals:
- ANALYSIS:
  - Configuration (overview)
  - Guardian Analysis (guardian)
  - Auto Analysis (auto-analysis)
  - Performance Counters (performance-counters)
- EVENTS:
  - Event Types (event-types) - Special: Filter with alloc
  - Event Viewer (events)
  - JVM Flags (flags) - 2 tabs: JVM Flags, How It Works
- THREADS:
  - Statistics (thread-statistics)
  - Timeline (threads-timeline)
- MEMORY:
  - Heap Memory > Timeseries - 2 tabs: Before/After GC, Allocation Rate
  - Garbage Collection > Overview - 4 tabs: Pause Distribution, GC Efficiency, Longest Pauses, Concurrent Cycles
  - Garbage Collection > Timeseries - 3 tabs: Count, Max Pause, Sum of Pauses
  - Garbage Collection > Configuration
- COMPILER:
  - JIT Compilation (jit-compilation)
- INFRASTRUCTURE:
  - Container Configuration (container/configuration)

---

## Folder Structure

jeffrey-screenshots/jvm-internals/
- analysis/
  - configuration.png
  - guardian-analysis.png
  - auto-analysis.png
  - performance-counters.png
- events/
  - event-types.png
  - event-types-filtered-alloc.png
  - event-viewer.png
  - jvm-flags/ (tab-jvm-flags.png, tab-how-it-works.png)
- threads/
  - statistics.png
  - timeline.png
- memory/
  - heap-memory/ (tab-before-after-gc.png, tab-allocation-rate.png)
  - garbage-collection/
    - overview/ (tab-pause-distribution.png, tab-gc-efficiency.png, tab-longest-pauses.png, tab-concurrent-cycles.png)
    - timeseries/ (tab-count.png, tab-max-pause.png, tab-sum-of-pauses.png)
    - configuration.png
- compiler/jit-compilation.png
- infrastructure/container-configuration.png

---

## Detailed Capture Plan

### ANALYSIS Section

1. Configuration Page (/overview) - 1 screenshot, scroll if needed
2. Guardian Analysis (/guardian) - 1 screenshot, scroll if needed
3. Auto Analysis (/auto-analysis) - 1 screenshot, scroll if needed
4. Performance Counters (/performance-counters) - 1 screenshot

### EVENTS Section

5. Event Types (/event-types)
   - Default view: event-types.png
   - Filtered: Type alloc in search field -> event-types-filtered-alloc.png
6. Event Viewer (/events) - 1 screenshot, scroll if needed
7. JVM Flags (/flags) - 2 tabs: JVM Flags, How It Works

### THREADS Section

8. Statistics (/thread-statistics) - 1 screenshot, scroll if needed
9. Timeline (/threads-timeline) - 1 screenshot, scroll if needed

### MEMORY Section

10. Heap Memory > Timeseries (/heap-memory/timeseries) - 2 tabs
11. GC > Overview (/garbage-collection) - 4 tabs
12. GC > Timeseries (/garbage-collection/timeseries) - 3 tabs
13. GC > Configuration (/garbage-collection/configuration) - 1 screenshot

### COMPILER Section

14. JIT Compilation (/jit-compilation) - 1 screenshot, scroll if needed

### INFRASTRUCTURE Section

15. Container Configuration (/container/configuration) - 1 screenshot

---

## Instructions

For each page:
1. Navigate to the page/tab via sidebar menu
2. Take screenshot of the initial (top) view
3. If page has content below the fold:
   - Scroll down and capture additional screenshots
   - Name with suffixes: page-name-1.png, page-name-2.png, etc.
4. If page has tabs, click each tab and repeat
5. For Event Types filtered view, type alloc in search field
