<h1 align="center">
  <img src="static/logo/jeffrey-icon.svg" alt="" width="34" valign="middle" />
  Jeffrey — the JFR Analyst
</h1>

<p align="center">
  <strong>Open a JFR recording and read flamegraphs that <em>finally</em> render fast.</strong><br/>
  A self-hosted profiling &amp; diagnostics tool for the JVM — flamegraphs, heap dumps, JVM dashboards and an AI assistant, all in one app.
</p>

<p align="center">
  <a href="https://github.com/petrbouda/jeffrey/blob/master/LICENSE"><img alt="License: AGPL-3.0" src="https://img.shields.io/badge/License-AGPL--3.0-2563eb.svg"></a>
  <a href="https://github.com/petrbouda/jeffrey"><img alt="Java 25+" src="https://img.shields.io/badge/Java-25%2B-orange.svg"></a>
  <a href="https://github.com/petrbouda/jeffrey/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/petrbouda/jeffrey?color=7c3aed&label=release"></a>
  <a href="https://hub.docker.com/r/petrbouda/microscope"><img alt="Docker pulls" src="https://img.shields.io/docker/pulls/petrbouda/microscope?color=0ea5e9&logo=docker&logoColor=white"></a>
  <a href="https://plugins.jetbrains.com/plugin/31963-jeffrey-microscope"><img alt="JetBrains Marketplace" src="https://img.shields.io/jetbrains/plugin/v/31963-jeffrey-microscope?label=IntelliJ%20plugin&color=fb923c"></a>
  <a href="https://github.com/petrbouda/jeffrey/stargazers"><img alt="GitHub stars" src="https://img.shields.io/github/stars/petrbouda/jeffrey?style=flat&color=eab308"></a>
</p>

<p align="center">
  <a href="https://www.jeffrey-analyst.cafe/"><strong>🌐 Website</strong></a> ·
  <a href="https://www.jeffrey-analyst.cafe/docs"><strong>📖 Docs</strong></a> ·
  <a href="https://github.com/petrbouda/jeffrey/releases/latest/download/microscope.jar"><strong>⬇️ Download</strong></a> ·
  <a href="https://hub.docker.com/r/petrbouda/microscope"><strong>🐳 Docker Hub</strong></a>
</p>

---

## ⚡ Try me in one command

Pre-loaded with sample recordings — nothing to configure, just look around:

```bash
docker run -it --network host petrbouda/microscope-examples
```

Then open **http://localhost:8080** and start clicking. That's it. 🎉

---

## ✨ What I can do for you

Point me at a recording and you get a whole analysis suite — not just a single flamegraph.

### 🔥 Flamegraphs that render fast — and differential ones too

Interactive flamegraphs for **every** JFR event that carries a stacktrace: CPU, wall-clock,
allocations, locks, and more. Shipped a change and want to know if it regressed? Put two profiles
side by side and let the **differential flamegraph** show you exactly what got worse.

### ⏱️ Sub-second timelines

Averages hide the spike that took your service down. **Zoom into the millisecond your service
stalled** with sub-second heatmaps — and compare them across profiles.

### 🛡️ Guardian & Auto-Analysis — I find the problems for you

You don't need to be a JVM wizard. Dozens of automated checks flag the usual culprits —
**lock contention, GC pressure, safepoint outliers, HashMap collisions, excessive allocations** —
and color-code them OK / Warning / Critical so you know where to look first.

### 📊 Purpose-built JVM dashboards

Specialized views instead of a raw event dump: **Garbage Collection**, **Thread Statistics &
Timeline**, **JIT Compilation**, **Heap Memory**, JVM flags, event types and a full event viewer.

### 🌐 Tech dashboards — observability without the agent zoo

Paired with the [Jeffrey Events](https://github.com/petrbouda/jeffrey-events) library, get
application-level dashboards straight from JFR: **HTTP server & client**, **JDBC statements**,
**connection pools**, and method tracing — response times, P99s, success rates and slowest traces.

### 🩺 Heap dump forensics

Chasing a leak or an OOM? Browse **class histograms**, walk **dominator trees**, hunt **leak
suspects**, and run **OQL queries** — straight from a single heap dump file.

### 🤖 AI assistant that actually reads your data

Chat with **Claude** or **OpenAI** over the active profile or heap dump. The model gets direct,
read-only tool access to your data — so you get answers grounded in your actual recording, not
hallucinated guesses. Includes a natural-language **OQL assistant**: ask a question, get the query.

> AI is disabled by default; API keys are encrypted at rest. Works with the Claude Code CLI
> (your Pro/Max subscription — no per-token charges), OpenAI, or a self-hosted Ollama.

---

## ☁️ Going to production? Meet Jeffrey Hub

**One JFR pipeline. Two halves that work alone.**

Microscope lives on your desk. **Jeffrey Hub** is its counterpart in the cluster — a containerised
collector that runs alongside your Java fleet and **collects runtime data from running services**:
JFR recordings, artifacts and lifecycle events. It catalogs them by workspace, project and instance,
schedules recording jobs, and serves everything over gRPC.

```
Your services  ──collect──▶  Jeffrey Hub  ──gRPC──▶  Microscope
```

- 🧊 **Runs as a container** in Kubernetes, right next to your apps
- 💾 **Shared-volume collection** — zero agent overhead, cheap integration
- 📡 **Live streaming, replay sessions & merged downloads** over gRPC
- 🔌 Serve **Microscope**, your own custom consumer, or both at once

No analysis happens on the server — that's Microscope's job, on demand, on your machine (so you
keep the expensive compute off your cloud bill).

---

## 🧩 The Jeffrey ecosystem

Microscope and Hub are the core. A few friends round out the toolbox:

| Product | What it does |
|---|---|
| **[IntelliJ Plugin](https://plugins.jetbrains.com/plugin/31963-jeffrey-microscope)** | Jump from any flamegraph frame straight to the source line in your open IntelliJ — Java & Kotlin — or pull inline source back into the profile. |
| **Provisioner** | One HOCON file lays out your workspace / project / session tree, registers sessions with the Hub, and generates the JVM argfile that starts your app under the profiler. |
| **Jeffrey JIB** | A Jib (Gradle/Maven) extension that wraps your container entrypoint so profiling starts before your app does — no command override, no binaries baked into the image. |
| **Performance Analyst** 🆕 | *Incubating.* An AI companion that pulls recordings from a Hub and turns each profile into source-code-level recommendations — severity-graded, with ready-to-apply patches. From profiles to pull requests. |
| **[Jeffrey Events](https://github.com/petrbouda/jeffrey-events)** | A lightweight custom-JFR-event library (HTTP, gRPC, database, connection-pool, heartbeat) that powers the tech dashboards. |

---

## 🚀 Getting started

**Requires Java 25+.**

### 🐳 Docker

```bash
# with pre-loaded example recordings
docker run -it --network host petrbouda/microscope-examples

# clean, bring your own JFR files
docker run -it --network host petrbouda/microscope
```

### ☕ Plain JAR

```bash
# grab the latest release
curl -L -o microscope.jar \
  https://github.com/petrbouda/jeffrey/releases/latest/download/microscope.jar

java -jar microscope.jar
```

Open **http://localhost:8080**, go to **Recordings → upload a `.jfr` → Analyze**, and Jeffrey
builds a profile you can explore.

---

## 📖 Links

- 🌐 **Website:** https://www.jeffrey-analyst.cafe/
- 📦 **Releases:** https://github.com/petrbouda/jeffrey/releases
- 🧩 **IntelliJ plugin:** https://plugins.jetbrains.com/plugin/31963-jeffrey-microscope
- 📡 **Jeffrey Events:** https://github.com/petrbouda/jeffrey-events

## 📄 License

Jeffrey is released under the **[GNU Affero General Public License v3.0](LICENSE)** (AGPL-3.0).
