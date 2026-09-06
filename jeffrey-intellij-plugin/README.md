# Jeffrey Microscope Plugin (IntelliJ)

A companion IntelliJ IDEA plugin for [Jeffrey Microscope](https://jeffrey-analyst.cafe). It exposes
a small HTTP API over IntelliJ's built-in server so Microscope can jump from a JFR flame-graph frame
straight to the corresponding source in the right open IDE window — and fetch source text to show
inline.

## How it pairs with Microscope

Microscope's backend talks to this plugin server-side; the default mode
(`jeffrey.microscope.ide.mode=jeffrey-plugin`) is this plugin. There is nothing to pair and no token
to paste: the plugin answers on IntelliJ's built-in server, and Microscope discovers it by scanning
the localhost port range `63342-63362`.

## Build

This is a **standalone Gradle project** — not part of Jeffrey's root Maven reactor (it pulls the
IntelliJ Platform SDK). Build from this directory:

```bash
cd jeffrey-intellij-plugin
./gradlew buildPlugin
```

Output: `build/distributions/jeffrey-intellij-plugin-<version>.zip`.

Useful tasks: `./gradlew runIde` (launch a sandbox IDE with the plugin), `./gradlew verifyPlugin`
(JetBrains plugin verifier), `./gradlew test`.

- **Target IDE:** IntelliJ IDEA 2025.1+ (`since-build = 251`, from `gradle.properties`).
- **Java level:** 21 (matches the JetBrains Runtime; not related to Jeffrey's Java 25).

## Install

*Settings → Plugins → ⚙ → Install Plugin from Disk…* → pick the built zip → restart.

## Verify

```bash
curl http://127.0.0.1:63342/api/jeffrey/instance
```

Reports the IDE, its protocol version, and the open trusted projects with the branch and commit each
is on. A disabled integration answers `404` — by design, so Microscope's scan sees nothing.

## Configuration

*Settings → Tools → Jeffrey Microscope Plugin* — an enable toggle, the built-in server port, and the
Microscope address used by the *Analyze in Jeffrey Microscope* context-menu action.

## Endpoints

| Endpoint | Purpose |
|---|---|
| `GET /api/jeffrey/ping` | Liveness and protocol version. |
| `GET /api/jeffrey/instance` | This IDE and its open trusted projects, with branch and HEAD commit. |
| `POST /api/jeffrey/navigate` | Resolve a frame, open it, focus the window. |
| `POST /api/jeffrey/resolve` | Resolve a frame and report it — **no** editor movement (protocol 2+). |
| `GET /api/jeffrey/has` | Whether a project contains a class (and optionally a method). |
| `GET /api/jeffrey/source` | Source text of a class, preferring attached sources. |

`resolve` exists for Microscope's `ide_` MCP tools: an AI agent grounding a finding needs the file
and the line, and must not move the developer's cursor while doing it.
