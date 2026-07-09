<h1 align="center">
  <img src="static/logo/jeffrey-icon.svg" alt="" width="34" valign="middle" />
  Jeffrey — Performance Analyst
</h1>

<p align="center">
  <strong>Open a JFR recording or heap dump and read flamegraphs that <em>finally</em> render fast.</strong><br/>
  A self-hosted performance analyst for the JVM — flamegraphs, heap dumps, JVM dashboards and an AI assistant, all in one app.
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

## 🚀 Getting started

**Requires Java 25+.** Pre-loaded with sample recordings — nothing to configure, just look around:

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

Then open **http://localhost:8080**, go to **Recordings → upload a JFR or Heap dump → Analyze**,
and Jeffrey builds a profile you can explore. That's it. 🎉

## 🧩 The Jeffrey ecosystem

The complete toolbox — Microscope and Hub are the core, the rest round it out:

| Product | What it does |
|---|---|
| **Jeffrey Microscope** | The standalone analyzer. Open a JFR recording or heap dump — or connect to a Hub — and read flamegraphs, sub-second timelines, JVM & tech dashboards, plus an AI assistant. Runs as a JAR or container. |
| **Jeffrey Hub** | A containerised collector for Kubernetes. Collects JFR recordings, artifacts and lifecycle events from your running services via shared volume, and serves them over gRPC to Microscope. |
| **[IntelliJ Plugin](https://plugins.jetbrains.com/plugin/31963-jeffrey-microscope)** | Jump from any flamegraph frame straight to the source line in your open IntelliJ — Java & Kotlin — or pull inline source back into the profile. |
| **Provisioner** | One HOCON file lays out your workspace / project / session tree, registers sessions with the Hub, and generates the JVM argfile that starts your app under the profiler. |
| **Jeffrey JIB** | A Jib (Gradle/Maven) extension that wraps your container entrypoint so profiling starts before your app does — no command override, no binaries baked into the image. |
| **Jeffrey Agent** | A tiny `-javaagent` that writes a heartbeat file to the shared volume, so the Hub can track liveness and tell a clean shutdown from a crash. No network, no gRPC. |
| **Performance Analyst** 🆕 | *Incubating.* An AI companion that pulls recordings from a Hub and turns each profile into source-code-level recommendations — severity-graded, with ready-to-apply patches. From profiles to pull requests. |
| **[Jeffrey Events](https://central.sonatype.com/artifact/cafe.jeffrey-analyst/jeffrey-events)** | A lightweight custom-JFR-event library (HTTP, gRPC, database, connection-pool, heartbeat) that powers the tech dashboards. |

## 📖 Links

- 🌐 **Website:** https://www.jeffrey-analyst.cafe/
- 📦 **Releases:** https://github.com/petrbouda/jeffrey/releases
- 🧩 **IntelliJ plugin:** https://plugins.jetbrains.com/plugin/31963-jeffrey-microscope
- 📡 **Jeffrey Events:** https://central.sonatype.com/artifact/cafe.jeffrey-analyst/jeffrey-events

## 📄 License

Jeffrey is released under the **[GNU Affero General Public License v3.0](LICENSE)** (AGPL-3.0).
