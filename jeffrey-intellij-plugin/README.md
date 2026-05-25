# Jeffrey Microscope Plugin (IntelliJ)

A companion IntelliJ IDEA plugin for [Jeffrey Microscope](https://jeffrey-analyst.cafe). It exposes
a small HTTP API over IntelliJ's built-in server so Microscope can jump from a JFR flame-graph frame
straight to the corresponding source in the right open IDE window — and fetch source text to show
inline.

## How it pairs with Microscope

Microscope's backend talks to this plugin server-side (mode `jeffrey.microscope.ide.mode=default`).
The plugin advertises itself by writing a registry file under `~/.jeffrey/ide-registry/`, so
Microscope discovers the IDE's port and auth token without manual configuration.

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

- **Target IDE:** IntelliJ IDEA 2026.1+ (`since-build = 261`).
- **Java level:** 21 (matches the JetBrains Runtime; not related to Jeffrey's Java 25).

## Install

*Settings → Plugins → ⚙ → Install Plugin from Disk…* → pick the built zip → restart.

## Verify (once endpoints are wired)

```bash
curl http://127.0.0.1:63342/api/jeffrey/instance
```

## Configuration

*Settings → Tools → Jeffrey Microscope Plugin* — shows the port, auth token (masked), and registry
file path.

## Design

See the implementation plan in the Jeffrey repo (`CLAUDE_CODE_PLAN.md` and the analysis plan).
