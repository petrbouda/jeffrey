# Profiling Java in Kubernetes: Continuous Recording with Jeffrey Server

You can't attach a profiler to a Kubernetes pod the way you'd connect VisualVM to a local process. Pods are ephemeral — they scale up, crash, get evicted, and restart. By the time you notice a performance problem, the pod that exhibited it may be gone, along with all its profiling data.

Jeffrey solves this with a three-component architecture designed for production environments: **Jeffrey CLI** initializes containers for profiling, **Jeffrey Server** collects JFR recordings from running applications, and **Jeffrey Local** analyzes the data on your developer machine. The expensive computation happens locally, not in the cloud.

This article — the final in our series on Java profiling with Jeffrey — shows how to set up continuous profiling for Java applications in Kubernetes.

## The Three-Component Architecture

Jeffrey splits profiling into distinct responsibilities:

![Jeffrey Local + Server architecture](images/release-notes/server-recording/01-architecture.png)
*Split architecture: Server collects in the cloud, Local analyzes on your machine.*

**Jeffrey CLI** runs as an init step in your Docker container. It reads a configuration file, generates JVM arguments for Async Profiler, creates the directory structure on shared storage, and registers the application instance with Jeffrey Server. It runs once before your Java application starts.

**Jeffrey Server** runs as a service in your Kubernetes cluster. It watches shared storage for new JFR files and metadata, tracks recording sessions, manages instance lifecycles, and exposes a gRPC API for remote access. It collects — it doesn't analyze.

**Jeffrey Local** runs on your developer machine. It connects to Jeffrey Server as a "remote workspace," lets you browse instances and sessions, download recordings, and perform full analysis — flamegraphs, GC analysis, heap dumps, AI, everything covered in previous articles.

This separation keeps analysis costs off your cloud infrastructure. JFR recording has negligible overhead on the profiled application (under 2%), and Jeffrey Server is a lightweight collector. The heavy processing happens on your local machine where compute is free.

## Setting Up Jeffrey CLI in Your Container

Jeffrey CLI is a small JAR that runs before your application starts. It reads a HOCON configuration file and generates JVM arguments for Async Profiler.

Add it to your Dockerfile:

```dockerfile
# Copy Jeffrey CLI and Async Profiler
COPY jeffrey-cli.jar /data/jeffrey/libs/current/jeffrey-cli.jar
COPY libasyncProfiler.so /data/jeffrey/libs/current/libasyncProfiler.so
```

Create an entrypoint script:

```bash
#!/bin/sh
# Initialize profiling configuration
java -jar /data/jeffrey/libs/current/jeffrey-cli.jar \
  init --base-config /mnt/config/jeffrey-init.conf

# Start the application with generated JVM arguments
exec java @/tmp/jvm.args -jar /app/my-service.jar
```

The CLI reads the configuration, generates an `@argfile` with all necessary JVM flags (Async Profiler agent path, output directory, profiling events, JFR sync), and creates the directory structure on shared storage.

A typical configuration enables:

- **CPU, allocation, and lock profiling** via Async Profiler
- **JFR synchronization** (`jfrsync=default`) for GC, threading, and JVM events
- **Chunked recording** — JFR output split into time-based chunks (e.g., 15-minute loop) so files are manageable size
- **Heap dumps on OOM** — Automatically captured if the application crashes
- **Heartbeat monitoring** — Periodic signals so the Server knows the session is alive
- **JVM diagnostic logging** — Captured alongside JFR data

The CLI also publishes workspace events that Jeffrey Server watches to auto-discover new instances.

## Deploying Jeffrey Server

Jeffrey Server is a Spring Boot application that runs in your Kubernetes cluster. It needs access to the same shared storage where your profiled applications write JFR files.

```bash
docker run -it --network host \
  -v /mnt/jeffrey-data:/data/jeffrey \
  petrbouda/jeffrey-server
```

In Kubernetes, this typically means a shared PersistentVolumeClaim (PVC) or NFS mount that both the profiled pods and Jeffrey Server can access.

Jeffrey Server exposes two ports:

- **HTTP (8081)** — A minimal web UI for browsing workspaces and sessions directly
- **gRPC (9090)** — The primary API used by Jeffrey Local to connect remotely

The Server watches the shared storage directory structure and automatically discovers:

- **Workspaces** — Top-level environments (e.g., "production", "staging")
- **Projects** — Applications within a workspace (e.g., "order-service", "payment-service")
- **Instances** — Individual pods/containers for each project
- **Sessions** — Recording periods within each instance

## Workspaces and Projects

Jeffrey organizes profiling data hierarchically:

![Workspaces and projects](images/release-notes/server-recording/02-workspaces.png)
*Workspaces contain projects, projects contain instances and their recording sessions.*

A **workspace** represents an environment — production, staging, load-test. A **project** represents an application or microservice within that environment. This maps naturally to how Kubernetes clusters are organized.

Each project can have multiple **instances** (pods), and each instance can have multiple **recording sessions** (one per application restart). This hierarchy lets you compare behavior across pods, track regressions across deployments, and investigate specific incidents by time range.

## Instance Lifecycle

An **instance** represents a single pod lifecycle — from startup to termination. Jeffrey tracks instances through four states:

- **PENDING** — Created by CLI, waiting for the first recording session to start
- **ACTIVE** — At least one recording session is producing JFR data
- **FINISHED** — All sessions have ended (pod terminated)
- **EXPIRED** — Cleaned up after the configured retention period

![Instances overview and lifecycle](images/release-notes/server-recording/02-instances-overview.png)
*Instance list showing lifecycle state, session count, and timestamps.*

Jeffrey detects session liveness through a **dual heartbeat mechanism**. The Jeffrey Agent (bundled with your application) emits periodic JFR heartbeat events and writes timestamp files to shared storage. When heartbeats stop arriving, Jeffrey Server marks the session as finished. This also detects JVM crashes — if an `hs_err_pid` log file appears, the Server records it as a crash event.

![Instance timeline](images/release-notes/server-recording/03-instance-timeline.png)
*Timeline view showing instance activity, sessions, and events over time.*

## Recording Sessions

A **recording session** is the core unit of profiling data. It contains:

- **JFR chunks** — Multiple time-based JFR files (e.g., one per 15-minute loop cycle)
- **Artifacts** — Heap dumps (captured on OOM), JVM error logs, diagnostic logs, performance counter data
- **Metadata** — Session start/end times, configuration, instance information

![Session detail and artifacts](images/release-notes/server-recording/04-session-detail.png)
*Session detail view showing JFR files, artifacts, and metadata.*

Sessions are created automatically when your application starts with Jeffrey CLI configuration. JFR chunks are written continuously to shared storage, so Jeffrey Server sees them in near real-time. You don't need to wait for the application to stop before analyzing data.

## Connecting Jeffrey Local to Server

On your developer machine, open Jeffrey Local and add a **remote workspace** connection:

1. Enter the Jeffrey Server hostname and gRPC port (default: 9090)
2. Jeffrey Local connects and discovers all workspaces, projects, and instances
3. Browse the recording history just like a local workspace

From here, you can:

- **Browse instances and sessions** — See which pods are running, which have finished, and what data is available
- **Download recordings** — Select specific JFR chunks from a session and download them as a merged file

![Download assistant](images/release-notes/server-recording/05-download-assistant.png)
*Download assistant for selecting and merging JFR chunks from a remote session.*

Downloads happen via gRPC streaming in 64KB chunks, so even large files (multi-GB heap dumps) transfer efficiently without memory pressure.

Once downloaded, you create a profile in Jeffrey Local and have full access to every analysis feature: flamegraphs, differential analysis, GC investigation, heap dumps, application monitoring, and AI-powered analysis.

![Recordings management](images/release-notes/server-recording/06-recordings.png)
*Downloaded recordings ready for profile creation and analysis.*

## Profiler Settings: Configure from the UI

Jeffrey provides a **visual profiler settings builder** that generates Async Profiler commands from a UI form. Settings can be configured at three levels with inheritance:

- **Global** — Default settings for all workspaces
- **Workspace** — Override for a specific environment (e.g., production uses lower sampling rate)
- **Project** — Override for a specific application (e.g., enable lock profiling only for the service with contention issues)

![Visual Builder for profiler settings](images/release-notes/profiler-settings/01-visual-builder.png)
*Configure profiling events, sampling rates, and output options from the UI.*

Settings are propagated to shared storage, and Jeffrey CLI picks them up on the next application restart. This means you can change profiling configuration without redeploying your application — just update the settings and wait for the next pod rollout.

## Background Jobs

Jeffrey Server runs background scheduler jobs to maintain data hygiene:

- **Session finish detection** — Evaluates heartbeats and marks sessions as finished when pods terminate
- **Instance expiration** — Cleans up old instances after the configured retention period
- **Recording cleanup** — Limits disk usage by removing old JFR chunks from long-running sessions
- **Session cleanup** — Removes sessions older than the retention period and compresses finished JFR files

These jobs ensure that shared storage doesn't grow unbounded, even in high-scale environments with many pods and frequent deployments.

## Putting It All Together

The typical workflow for production profiling with Jeffrey:

1. **Once:** Add Jeffrey CLI and Async Profiler to your Docker image, configure the init step
2. **Once:** Deploy Jeffrey Server in your cluster with access to shared storage
3. **Continuously:** Your applications run with profiling enabled (negligible overhead)
4. **On demand:** Connect Jeffrey Local to the Server, browse instances, download the recording that covers the time period you're investigating
5. **Analyze:** Create a profile and use every tool in Jeffrey — flamegraphs, GC analysis, heap dumps, custom event dashboards, AI-assisted investigation

No SSH into pods. No manual JFR commands. No data loss when pods restart. Profiling data is always available, organized by workspace, project, and instance, with full history for post-mortem analysis.

## Series Recap

Over six articles, we've covered the complete Jeffrey profiling workflow:

1. **[Getting Started](01-getting-started-with-jeffrey.md)** — Recording with Async Profiler, launching Jeffrey, Quick Analysis
2. **[Flamegraphs](02-reading-flamegraphs-like-a-pro.md)** — CPU, allocation, lock contention, differential, sub-second, frame collapsing
3. **[Memory Investigation](03-finding-and-fixing-memory-leaks.md)** — GC analysis, heap trends, heap dump deep dives
4. **[Application Monitoring](04-monitoring-http-database-grpc-with-custom-jfr-events.md)** — HTTP, database, gRPC, connection pools with custom JFR events
5. **[AI-Powered Analysis](05-ai-powered-java-profiling.md)** — Natural language queries against profiling data
6. **Production Profiling** (this article) — Continuous recording in Kubernetes with Jeffrey Server

You can find the source code at [github.com/petrbouda/jeffrey](https://github.com/petrbouda/jeffrey) and the full documentation at [jeffrey-analyst.cafe](https://jeffrey-analyst.cafe).
