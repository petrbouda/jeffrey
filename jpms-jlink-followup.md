# Jeffrey — JPMS / jlink follow-up plan

Branched from `claude/jersey-to-spring-migration` (Jersey fully removed,
`@RestController` migration complete). This document tracks the remaining
work needed to deliver a real JPMS / jlink-shippable distribution.

## Where we are now

The Jersey migration commits removed the only piece of Jeffrey's own
code that was incompatible with strict JPMS (`packages(...)` classpath
scanning) — but more importantly, they cut `~10–15` jars out of the fat
jar (`spring-boot-starter-jersey`, `jersey-media-multipart`,
`jersey-media-sse`, `jersey-test-framework-provider-grizzly2`,
`shared/jackson-jaxrs`).

Status:
- `core-local` and `core-server` build clean against Java 25
- 99 controller tests passing
- Spring 7 native `JacksonJsonHttpMessageConverter` (Jackson 3) used
  throughout
- `spring-boot-starter-test` brings transitive test deps cleanly
- All 50 controllers use `@RestController` + component scan

What we have **not** done yet:
- Any `jlink` build step (the actual JPMS deliverable)
- `module-info.java` for any module
- `jdeps` validation of the runtime image's required modules
- Custom JRE assembly + distribution layout
- `jpackage` (optional)
- CI integration

## Scope of "JPMS modularity"

The original `jpms-migration-plan.md` recommends approach **(b)**:
**jlink the JRE, keep the app on the classpath**. This is still the
right path. Specifically:

> `jlink` only needs the *JDK* to be modular — which it already is —
> to produce a trimmed runtime image. The application stays on the
> classpath, the fat jar still works, and we get the binary-size win.

We are **not** going to write `module-info.java` for `core-local` /
`core-server` themselves — `grpc-netty-shaded` and `duckdb_jdbc` are
unnamed-module fat jars that would force a hybrid setup anyway. The
~25–30 hours that approach (a) would cost yields modest extra benefit
over approach (b).

---

## Remaining steps

### 1. Pin Java 25 in the build environment _(nicety, not a blocker)_

- [ ] Document the Java 25 requirement in `build/scripts/` (or
      `README.md`) and confirm CI uses Java 25.
- [ ] Add a Maven Enforcer rule for `java.version >= 25` if not
      already present.

**Why:** prerequisite. Already required by `<release>25</release>` in
the parent pom; the plugin will fail loudly without it.

**Acceptance:** `mvn -version` on CI shows JDK 25; an enforcer rule
catches the mismatch on developer machines.

---

### 2. Determine the JDK-module set with `jdeps` _(prep)_

- [ ] After a successful `mvn -pl build/build-local install -am
      -DskipTests`, run:

  ```bash
  jdeps --multi-release 25 \
        --print-module-deps \
        --ignore-missing-deps \
        --recursive \
        --class-path "build/build-local/target/jeffrey.jar:$(... fat jar libs ...)" \
        build/build-local/target/jeffrey.jar
  ```

  Or, more practically (Spring Boot fat jar layout is special):

  ```bash
  # Explode the Spring Boot jar
  mkdir -p target/exploded
  cd target/exploded && jar xf ../jeffrey.jar
  # Run jdeps over BOOT-INF/lib + BOOT-INF/classes
  jdeps --multi-release 25 --print-module-deps --ignore-missing-deps \
        --module-path BOOT-INF/lib \
        --recursive BOOT-INF/lib/*.jar BOOT-INF/classes
  ```

- [ ] Capture the resulting comma-separated module list as the
      authoritative `--add-modules` set for jlink.

**Why:** machine-derived list is more reliable than guessing. The
typical Spring Boot 4 / DuckDB / gRPC superset is roughly
`java.base,java.logging,java.sql,java.xml,java.naming,java.management,java.net.http,java.desktop,java.security.jgss,jdk.jfr,jdk.unsupported,jdk.crypto.ec,jdk.crypto.cryptoki` — but `jdeps` will tell us exactly.

**Acceptance:** the captured module list is checked into
`build/build-local/jlink-modules.txt` (or similar) and consumed by
the build.

---

### 3. Add a `jlink` step to `build/build-local` _(the core deliverable)_

- [ ] In `build/build-local/pom.xml`, add an `exec-maven-plugin`
      execution (or `maven-antrun-plugin`) that runs:

  ```bash
  jlink \
    --module-path "$JAVA_HOME/jmods" \
    --add-modules @${project.basedir}/jlink-modules.txt \
    --strip-debug --no-header-files --no-man-pages \
    --compress=zip-6 \
    --output ${project.build.directory}/runtime
  ```

- [ ] Wire the execution to the `package` phase so it runs after
      `spring-boot:repackage`.

- [ ] Bind to a build profile (e.g. `-Pjlink`) so default `mvn install`
      stays fast for developers.

**Why:** this is the actual JPMS / jlink deliverable.

**Acceptance:**
- `mvn -Pjlink -pl build/build-local package -am` produces
  `target/runtime/bin/java`
- `target/runtime/bin/java -jar target/jeffrey.jar` boots the app
- `du -sh target/runtime/` is ≤ 100 MB (vs the ~300 MB full JDK)

---

### 4. Mirror the same jlink step for `build/build-server` _(parity)_

- [ ] Same `jlink` execution in `build/build-server/pom.xml`.

**Why:** server distribution should ship the same way. The module set
will likely be very close to the local app's; pin both with `jdeps`
output rather than copy-pasting.

**Acceptance:** both `build-local` and `build-server` produce a
`target/runtime/` directory that boots the respective app.

---

### 5. Distribution layout + launcher script _(packaging)_

- [ ] Use `maven-assembly-plugin` (or `maven-antrun`) to bundle:

  ```
  jeffrey-<version>/
    runtime/        (jlinked JRE, ~70–90 MB)
    lib/jeffrey.jar (existing Spring Boot fat jar)
    bin/jeffrey     (launcher script)
    bin/jeffrey.bat (Windows launcher)
  ```

- [ ] Launcher script:

  ```sh
  #!/usr/bin/env sh
  set -e
  here="$(cd -- "$(dirname -- "$0")"/.. && pwd)"
  exec "$here/runtime/bin/java" $JEFFREY_JAVA_OPTS -jar "$here/lib/jeffrey.jar" "$@"
  ```

- [ ] Produce `.tar.gz` for Linux/macOS and `.zip` for Windows.

**Acceptance:** unzipping the bundle on a clean machine with **no
JDK installed** and running `bin/jeffrey` boots the app.

---

### 6. Smoke-test the bundle end-to-end _(quality gate)_

- [ ] Boot the bundled app on:
  - Linux x86_64
  - macOS arm64 (if Mac CI available)
  - Windows x86_64
- [ ] Open the SPA in a browser, exercise:
  - workspaces / projects / profiles drill-down
  - flamegraph render (protobuf endpoint)
  - heap-dump upload (multipart)
  - live-stream / replay-stream (SSE)
  - quick-analysis recording upload (multipart)
  - download-task SSE progress

**Why:** catches missing JDK modules (e.g. `jdk.unsupported` for
Netty, `java.desktop` for AWT-touching libs) before users hit them.

**Acceptance:** all flows green on each platform.

---

### 7. CI integration _(make it durable)_

- [ ] GitHub Actions / similar: a job that runs `mvn -Pjlink package -am`
      and uploads the artifacts.
- [ ] Optional: a smoke-test job that boots the bundled app
      (`bin/jeffrey &`) and curls a known endpoint
      (`/api/internal/version`).

**Acceptance:** every PR / main commit produces a downloadable
artifact and verifies it actually starts.

---

### 8. _(Optional)_ `jpackage` for native installers

- [ ] Add a `jpackage` execution that wraps the jlinked image into a
      platform-native installer (DMG/MSI/DEB).

  ```bash
  jpackage \
    --name Jeffrey \
    --runtime-image target/runtime \
    --input target/lib \
    --main-jar jeffrey.jar \
    --type ${jpackage.type}   # app-image | dmg | msi | deb
  ```

- [ ] Per-OS profiles (`-Pjpackage-mac`, `-Pjpackage-win`,
      `-Pjpackage-linux`).

**Why:** professional install experience; auto-update friendly.
Useful for desktop-style distribution of `jeffrey-local`. Less
relevant for `jeffrey-server` (Docker is the better packaging there).

**Acceptance:** `jpackage` produces a working installer per platform.

---

### 9. _(Optional)_ Per-module `module-info.java` for leaf modules

- [ ] Add `module-info.java` only to safe leaf modules where the
      blast radius is small:
  - `shared/common` (no third-party reflection)
  - `shared/sql-builder` (pure utility)
  - `shared/persistence` (interfaces + records)
- [ ] **Do not** modularize `core-local`, `core-server`, or any
      module that depends on `grpc-netty-shaded` or `duckdb_jdbc`.
      Those keep classpath status (hybrid mode) per the original
      plan's recommendation.

**Why:** pure tidiness. Doesn't enable anything jlink can't already do.

**Acceptance:** the leaf modules expose only their public packages
via `exports`; reactor still builds and tests pass.

---

### 10. _(Optional)_ Docker image based on the jlinked runtime

- [ ] Update `docker/` to use a multi-stage build:
  - Stage 1 builds the jlinked image with full JDK
  - Stage 2 copies just the `runtime/` + `lib/` into a `distroless`
    or `ubuntu:24.04`-slim base
- [ ] Resulting image should be **significantly** smaller than the
      current full-JDK image.

**Why:** smaller container images, faster pulls, smaller attack
surface.

**Acceptance:** `docker images` shows the new image is ≤ 200 MB
(vs full-JDK ~600 MB).

---

## Recommended order

1. **Step 2** (`jdeps`) — gives you the module list.
2. **Step 3** (`jlink` for `build-local`) — the core win. Once this
   works locally, everything else follows.
3. **Step 5** (bundle layout) — makes the output usable.
4. **Step 6** (smoke test) — catches surprises before users do.
5. **Step 4** (`jlink` for `build-server`) — parity.
6. **Step 7** (CI) — makes it durable.
7. Steps 8–10 — opportunistic improvements; pick based on priorities.

## Out of scope (per the original plan)

- Full JPMS modularization of `core-local` / `core-server`
  (`module-info.java` for them) — the shaded gRPC + DuckDB jars force
  a hybrid setup anyway; the cost (~25–30 h) is not justified by the
  marginal benefit over jlink alone.
- Removing `grpc-netty-shaded` in favour of the non-shaded `grpc-netty`
  + raw Netty — high risk of version conflicts; status quo is fine.

## Effort estimates

| Step | Effort |
|---|---|
| 1. Pin Java 25 | 30 min |
| 2. `jdeps` module discovery | 1 h |
| 3. `jlink` build-local | 2 h |
| 4. `jlink` build-server | 30 min |
| 5. Distribution layout | 2 h |
| 6. Smoke test | 1–3 h |
| 7. CI integration | 2 h |
| 8. `jpackage` (optional) | 4–6 h |
| 9. Leaf `module-info` (optional) | 2 h |
| 10. Docker (optional) | 2 h |
| **Core path (1–7)** | **~10 h** |
| **Everything** | **~18 h** |
