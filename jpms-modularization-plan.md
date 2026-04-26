# Jeffrey — JPMS modularization plan

Branched from `claude/jersey-to-spring-migration`. Scope: move every
Jeffrey-owned Maven module to JPMS (i.e. give each its own
`module-info.java`). **Out of scope: `jlink`** — that's a separate
follow-up once modularization is done.

## Goal

Every Java module that produces a Jeffrey-owned jar declares a
`module-info.java` that:
- explicitly `requires` the modules it actually uses,
- `exports` only the packages that other Jeffrey modules consume,
- `opens` packages where Jackson 3, Spring, Mockito, or JFR need
  reflective access,
- declares `provides` / `uses` for any `ServiceLoader` SPI.

## Non-goals

- `jlink` (deferred — separate follow-up).
- Switching off Spring Boot's fat-jar repackaging (we'll address this
  but the migration *can* land while the runtime classpath stays
  unchanged — JPMS at compile time first, runtime second).
- Rewriting any controller / manager. The migration is purely
  metadata: imports, `requires`, `exports`, `opens`, plus a few
  small dependency tweaks.

---

## Reality check — third-party JPMS status (verified)

Modular jars (real `module-info.class`):
- `tomcat-embed-core` / `-el` / `-websocket` 11.0.20
- `byte-buddy` / `byte-buddy-agent` 1.17.8
- `tools.jackson:jackson-core` / `jackson-databind` 3.1.x
- `jakarta.servlet-api` 6.1.0
- `grpc-netty-shaded` 1.80.0
- `junit-jupiter`, `mockito-core`, `mockito-junit-jupiter`,
  `assertj-core`, `byte-buddy`, `opentest4j`, `hibernate-validator`
  (test deps)

Auto-named jars (`Automatic-Module-Name` only — usable but coarse):
- All `spring-*` 7.x → `spring.core`, `spring.context`, …
- All `spring-boot-*` 4.x → `spring.boot`, `spring.boot.autoconfigure`, …
- `protobuf-java` 4.34.1 → `com.google.protobuf`
- `grpc-stub`, `grpc-api`, `grpc-protobuf`, `grpc-services` →
  `io.grpc.*`
- `flightrecorder.rules.jdk` → `org.openjdk.jmc.flightrecorder.rules.jdk`
- `config` (typesafe) → `typesafe.config`
- `duckdb_jdbc` 1.5.2.0 → `duckdb.jdbc`

Unnamed jars (real blockers — need a workaround):

| Jar | Used by | Workaround |
|---|---|---|
| `cafe.jeffrey-analyst:jeffrey-events` 0.10.0 | many modules | **We own the source** at `utilities/jeffrey-events/`. Just add `module-info.java` there and bump the version. |
| `org.hdrhistogram:HdrHistogram` 2.2.2 | `profile-management` | `moditect-maven-plugin` to inject `Automatic-Module-Name: org.hdrhistogram` |
| `org.flywaydb:flyway-core` 11.x | `*-sql-persistence` | `moditect` → `org.flywaydb.core` |
| `org.flywaydb:flyway-database-duckdb` 10.x | `*-sql-persistence` | `moditect` → `org.flywaydb.database.duckdb` |
| `io.grpc:grpc-context` 1.80.0 | transitive via grpc | `moditect` → `io.grpc.context` (if direct compile-time use; otherwise tolerate) |
| `org.springframework.ai:spring-ai-*` 2.0.0-M4 (7 jars) | `oql-assistant`, `duckdb-ai-mcp`, `heap-dump-ai-mcp` | `moditect` per artifact, or wait for the GA release with proper module names |
| `com.jayway.jsonpath:json-path` 2.10 (test) | controller tests | `moditect` → `json.path` (test-scope only) |
| `jsonassert` 1.5.3 (test, transitive) | tests | `moditect` (test-scope) |

> **`moditect-maven-plugin`** edits jars at build time to add
> `Automatic-Module-Name` (or full `module-info`) without forking
> upstream. It's the standard answer for unnamed third-parties.

---

## Code modules to modularize (37)

### Owned third-party (must do first — `jeffrey-events`)

- [ ] `utilities/jeffrey-events` → `cafe.jeffrey.jfr.events`

### `shared/` (8)

- [ ] `shared/common` → `pbouda.jeffrey.shared.common`
- [ ] `shared/sql-builder` → `pbouda.jeffrey.shared.sql.builder`
- [ ] `shared/persistence` → `pbouda.jeffrey.shared.persistence`
- [ ] `shared/recording-storage-api` → `pbouda.jeffrey.shared.recording.storage.api`
- [ ] `shared/filesystem-recording-storage` → `pbouda.jeffrey.shared.recording.storage.filesystem`
- [ ] `shared/folder-queue` → `pbouda.jeffrey.shared.folder.queue`
- [ ] `shared/persistent-queue` → `pbouda.jeffrey.shared.persistent.queue`
- [ ] `shared/server-api` (gRPC stubs) → `pbouda.jeffrey.server.api`
- [ ] `shared/test` → `pbouda.jeffrey.shared.test`

### `jeffrey-local/profiles/` leaves (16)

- [ ] `frame-ir` → `pbouda.jeffrey.profile.frame.ir`
- [ ] `subsecond` → `pbouda.jeffrey.profile.subsecond`
- [ ] `timeseries` → `pbouda.jeffrey.profile.timeseries`
- [ ] `profile-thread` → `pbouda.jeffrey.profile.thread`
- [ ] `flamegraph` → `pbouda.jeffrey.profile.flamegraph`
- [ ] `profile-guardian` → `pbouda.jeffrey.profile.guardian`
- [ ] `heap-dump` → `pbouda.jeffrey.profile.heapdump`
- [ ] `tools` → `pbouda.jeffrey.profile.tools`
- [ ] `common-profile` → `pbouda.jeffrey.profile.common`
- [ ] `ai-config` → `pbouda.jeffrey.profile.ai.config`
- [ ] `oql-assistant` → `pbouda.jeffrey.profile.ai.oql`
- [ ] `duckdb-ai-mcp` → `pbouda.jeffrey.profile.ai.mcp.duckdb`
- [ ] `heap-dump-ai-mcp` → `pbouda.jeffrey.profile.ai.mcp.heapdump`
- [ ] `recording-parser/jfr-parser-api` → `pbouda.jeffrey.profile.parser.api`
- [ ] `recording-parser/jdk-jfr-parser` → `pbouda.jeffrey.profile.parser.jdk`
- [ ] `recording-parser/db-jfr-parser` → `pbouda.jeffrey.profile.parser.db`

### `jeffrey-local/profiles/` aggregator (1)

- [ ] `profile-management` → `pbouda.jeffrey.profile.management`

### `jeffrey-local/profiles/` persistence (2)

- [ ] `profile-persistence-api` → `pbouda.jeffrey.profile.persistence.api`
- [ ] `profile-sql-persistence` → `pbouda.jeffrey.profile.persistence.sql`

### `jeffrey-local/` (4)

- [ ] `local-core-persistence-api` → `pbouda.jeffrey.local.core.persistence.api`
- [ ] `local-core-sql-persistence` → `pbouda.jeffrey.local.core.persistence.sql`
- [ ] `grpc-client` → `pbouda.jeffrey.local.core.grpc.client`
- [ ] `core-local` → `pbouda.jeffrey.local.core`

### `jeffrey-server/` (3)

- [ ] `server-persistence-api` → `pbouda.jeffrey.server.persistence.api`
- [ ] `server-sql-persistence` → `pbouda.jeffrey.server.persistence.sql`
- [ ] `core-server` → `pbouda.jeffrey.server.core`

### CLI / agent (2)

- [ ] `jeffrey-cli` → `pbouda.jeffrey.cli`
- [ ] `jeffrey-agent` → `pbouda.jeffrey.agent`

### Skip (intentionally unmodularized)

- `pages-local`, `pages-server` — Vue SPA jars, classpath resources only
- `build/build-*` — Spring Boot fat-jar assembly modules; produce
  the executable, no exported API
- `jmh-tests`, `manual-tests` — driver projects
- `utilities/jeffrey-jib/*` — Maven/Gradle plugin code, not part of
  the runtime
- Aggregator poms (no source) — `pom.xml` (root), `build/pom.xml`,
  `jeffrey-local/pom.xml`, `jeffrey-server/pom.xml`, `shared/pom.xml`,
  `utilities/pom.xml`, `jeffrey-local/profiles/pom.xml`,
  `jeffrey-local/profiles/recording-parser/pom.xml`

---

## Known-hard parts (and their workarounds)

### 1. `jeffrey-events` is unnamed

We own the source: `utilities/jeffrey-events/src/main/java/cafe/jeffrey/jfr/events/`. Add `module-info.java`:

```java
module cafe.jeffrey.jfr.events {
    requires jdk.jfr;                 // for @Event, @Name, etc.
    exports cafe.jeffrey.jfr.events.http;
    exports cafe.jeffrey.jfr.events.jdbc.pool;
    exports cafe.jeffrey.jfr.events.jdbc.statement;
    exports cafe.jeffrey.jfr.events.grpc;
    exports cafe.jeffrey.jfr.events.heartbeat;
    exports cafe.jeffrey.jfr.events.message;
}
```

Bump to `0.11.0` and consume the new artifact from the project root.

### 2. Reflection — Jackson 3 / Spring / JFR

Records and DTOs accessed by Jackson at the HTTP boundary need:

```java
opens pbouda.jeffrey.local.core.resources.request to tools.jackson.databind;
opens pbouda.jeffrey.local.core.resources.response to tools.jackson.databind;
```

Spring's component scan + AOP needs the controller package opened to
Spring at runtime:

```java
opens pbouda.jeffrey.local.core.web.controllers to spring.core, spring.beans, spring.context;
opens pbouda.jeffrey.local.core.web.controllers.profile to spring.core, spring.beans, spring.context;
```

JFR `@Name` event classes (in `jeffrey-events` and a few internal
ones) get registered via reflection — but they're loaded from the
same module that defines them, so no `opens` is needed.

### 3. Spring Boot repackaging vs module path

`spring-boot-maven-plugin:repackage` produces a fat jar with
`BOOT-INF/lib/`. At runtime, Spring Boot's custom classloader treats
everything as the **classpath** — JPMS module boundaries are not
enforced.

Two options:

**Option A — JPMS at compile time only (recommended for first pass)**
- Each module has `module-info.java` for compile-time enforcement.
- The fat jar still ships unchanged; runtime uses Spring Boot's
  classloader as today.
- Pros: minimal change; lets us land modularization now and tackle
  runtime separately.
- Cons: doesn't catch runtime split-package issues; not a real "JPMS
  app" at execution.

**Option B — Module-path runtime (deferred)**
- Stop using `spring-boot:repackage`; produce a regular `lib/` +
  launcher script that runs `java --module-path lib --module
  pbouda.jeffrey.local.core/...` plus a few classpath jars for the
  unnamed dependencies.
- Pros: real JPMS at runtime; pairs well with `jlink` later.
- Cons: significant launcher work; loses Spring Boot's loader
  features (layered jars, etc.).

This plan delivers **Option A**. Option B is a follow-up that
becomes much cheaper once Option A is done.

### 4. `moditect-maven-plugin` for the unnamed third-parties

Add to root `pom.xml` `<pluginManagement>`:

```xml
<plugin>
  <groupId>org.moditect</groupId>
  <artifactId>moditect-maven-plugin</artifactId>
  <version>1.4.0.Beta1</version>
  <executions>
    <execution>
      <id>add-module-info-to-3rd-parties</id>
      <phase>generate-resources</phase>
      <goals><goal>add-module-info</goal></goals>
      <configuration>
        <modules>
          <module>
            <artifact>
              <groupId>org.hdrhistogram</groupId>
              <artifactId>HdrHistogram</artifactId>
            </artifact>
            <moduleInfoSource>
              module org.hdrhistogram {
                  exports org.HdrHistogram;
              }
            </moduleInfoSource>
          </module>
          <!-- … one block per unnamed dep … -->
        </modules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### 5. gRPC stubs (`shared/server-api`)

Generated code lives under `pbouda.jeffrey.server.api.v1`. The
`module-info.java` needs `requires transitive io.grpc.stub;` and
`requires transitive com.google.protobuf;` plus `exports
pbouda.jeffrey.server.api.v1;`. The protoc plugin output already
compiles cleanly under JPMS — just declare the dependencies.

### 6. ServiceLoader SPI

Touch points discovered:
- `java.sql.Driver` — DuckDB registers itself. We don't `provide`
  anything; just `requires java.sql;` in modules that use JDBC.
- SLF4J's `org.slf4j.spi.SLF4JServiceProvider` — `requires
  org.slf4j;` is enough; the binding (Logback) self-registers.
- Spring Boot's `META-INF/spring.factories` — Spring Boot's loader
  reads these regardless of JPMS; no `provides` needed.

Nothing in the project's own code currently exposes a
`ServiceLoader` SPI, so no `provides` clauses are needed.

### 7. Tests

`shared/test` defines `@DuckDBTest`. Its `module-info.java`:

```java
module pbouda.jeffrey.shared.test {
    requires transitive duckdb.jdbc;
    requires transitive org.junit.jupiter.api;
    exports pbouda.jeffrey.shared.test;
}
```

Each test module's `src/test/java/module-info.java` (the
test-scope module descriptor) opens packages to Mockito and JUnit:

```java
open module pbouda.jeffrey.local.core.test {
    requires pbouda.jeffrey.local.core;
    requires org.junit.jupiter.api;
    requires org.mockito;
    requires spring.test;
    requires assertj.core;
}
```

(Use `open module` for tests — the cost of enumerating every package
that needs opening to test infrastructure isn't worth it.)

---

## Order of work (bottom-up; each commit compiles green)

### Phase 1 — Owned third-party

1. **`utilities/jeffrey-events`** — add `module-info.java`, bump
   version to `0.11.0`, publish locally, update root pom version
   property.

### Phase 2 — Add `moditect` configuration

2. Configure `moditect-maven-plugin` in root pom for HdrHistogram,
   flyway, spring-ai, json-path, jsonassert, grpc-context.

### Phase 3 — `shared/` modules

3. `shared/common` (no project deps)
4. `shared/sql-builder` (only `shared/common`)
5. `shared/persistence` (only `shared/common`)
6. `shared/recording-storage-api`
7. `shared/filesystem-recording-storage`
8. `shared/folder-queue`
9. `shared/persistent-queue`
10. `shared/server-api`
11. `shared/test`

### Phase 4 — Profile leaves

12. `frame-ir`, `subsecond`, `timeseries`, `profile-thread`,
    `flamegraph`, `profile-guardian`, `heap-dump`, `tools`,
    `common-profile`, `ai-config`, `oql-assistant`, `duckdb-ai-mcp`,
    `heap-dump-ai-mcp` (largely independent — can be done in
    parallel)
13. `recording-parser/jfr-parser-api`
14. `recording-parser/jdk-jfr-parser`, `recording-parser/db-jfr-parser`

### Phase 5 — Persistence

15. `profile-persistence-api`
16. `profile-sql-persistence`
17. `local-core-persistence-api`
18. `local-core-sql-persistence`
19. `server-persistence-api`
20. `server-sql-persistence`

### Phase 6 — Aggregator profile module

21. `profile-management` (depends on most profile leaves +
    persistence)

### Phase 7 — gRPC client

22. `grpc-client` (depends on `shared/server-api`)

### Phase 8 — Application modules

23. `core-local` (top of local tree)
24. `core-server` (top of server tree)

### Phase 9 — CLI / agent

25. `jeffrey-cli`, `jeffrey-agent`

### Phase 10 — Validation

26. Full reactor compile + test on Java 25.
27. Run both apps, exercise SPA flows: workspaces, profiles,
    flamegraph (protobuf), heap-dump upload (multipart), live-stream
    / replay-stream (SSE).
28. Confirm `mvn -pl jeffrey-local/core-local dependency:tree`
    matches what the new `module-info.java` declares.

---

## Module template

For each Maven module:

```java
// src/main/java/module-info.java
module pbouda.jeffrey.<name> {
    // Project deps (transitive when re-exposed via API)
    requires transitive pbouda.jeffrey.shared.common;

    // Third-party deps actually used (verify with grep + jdeps)
    requires org.slf4j;
    requires tools.jackson.databind;          // if mapping JSON
    requires spring.context;                  // if @Bean / @Configuration
    requires spring.web;                      // if controllers / converters
    requires java.sql;                        // if JDBC
    requires jdk.jfr;                         // if JFR events

    // Public API (only what other Jeffrey modules consume)
    exports pbouda.jeffrey.<name>;
    exports pbouda.jeffrey.<name>.api;

    // Reflection (DTOs, Spring beans, Jackson serialization)
    opens pbouda.jeffrey.<name>.dto to tools.jackson.databind;
    opens pbouda.jeffrey.<name>.config to spring.core, spring.beans, spring.context;
}
```

Verify each module in three steps:
1. `mvn -pl <module> compile` — passes.
2. `jdeps --module-path target/<module>.jar --print-module-deps target/<module>.jar`
   — list of needed modules matches `requires`.
3. `mvn -pl <module> test` — green; if Mockito or Spring tests fail,
   add the missing `opens` to the test-scope `module-info.java`.

---

## Per-module checklist

For each module, the work is the same shape but the inputs differ:

- [ ] **Audit the source tree** — list every `package` declaration.
- [ ] **`requires` list** — `mvn dependency:tree` minus what's only
      transitive; one `requires` per *direct* compile-scope dep.
- [ ] **`exports` list** — start from "every package", remove
      anything that's clearly internal (`*.internal`, `*.impl`).
- [ ] **`opens` list** — packages whose classes are reflected on:
      `request/`, `response/`, `*.model`, `*.config`, controller
      packages.
- [ ] **Annotations on classes/fields/methods** — already follow
      the project rule (own line); no change.
- [ ] **Compile** the single module; fix until green.
- [ ] **Run the module's tests** — fix `opens` for test
      infrastructure if reflection complains.

---

## Acceptance criteria

- Every Jeffrey-owned code module has `src/main/java/module-info.java`.
- `mvn clean install -DskipTests=false` (full reactor) passes on
  Java 25.
- All 99 controller tests still pass.
- gRPC services + clients still wire up; in-process gRPC tests pass.
- Both apps boot:
  - `mvn -pl build/build-local install -am` produces the local fat
    jar; `java -jar` starts the SPA.
  - `mvn -pl build/build-server install -am` produces the server
    fat jar; `java -jar` starts and serves
    `/api/internal/version`.
- `jdeps --check pbouda.jeffrey.local.core target/jeffrey.jar`
  reports no missing requires / unused requires for `core-local`.

---

## Risks

| Risk | Mitigation |
|---|---|
| Spring component scan can't see beans across module boundaries | Either `opens` the controller / config packages to `spring.core`/`spring.beans`/`spring.context`, or keep `core-local` as `open module pbouda.jeffrey.local.core { … }` for the first pass. |
| Jackson 3 fails to (de)serialize records | `opens <pkg> to tools.jackson.databind;` — same as Jackson 2's reflection requirement. |
| Spring AI 2.0.0-M is unnamed and immature | `moditect` in this PR; revisit when a GA release ships. |
| Cyclic module deps surfaced by JPMS | Flatten with a small "*-api" module; a couple of these may already need splitting (e.g. `profile-management` ↔ `recording-parser`). |
| `flightrecorder.rules.jdk` reflection (rules engine) | Open the rules-config package to `org.openjdk.jmc.flightrecorder.rules.jdk`. |
| Test compile fails because Mockito / AssertJ can't reach record fields | Use `open module` in test descriptors. |

---

## Effort estimate

| Phase | Effort |
|---|---|
| 1. `jeffrey-events` modularization + version bump | 1 h |
| 2. `moditect` config for unnamed third-parties | 2 h |
| 3. `shared/` modules (9) | 4 h |
| 4. Profile leaves (16) | 8 h |
| 5. Persistence modules (6) | 4 h |
| 6. `profile-management` | 2 h |
| 7. `grpc-client` | 1 h |
| 8. `core-local` + `core-server` (the controller / Spring touchpoints) | 4–6 h |
| 9. CLI + agent | 1 h |
| 10. Validation + smoke tests | 3 h |
| **Total** | **~30 h** |

Effort assumes Java 25 toolchain, the Jersey-removal branch as the
baseline, and one engineer working linearly. Modularization of
profile leaves can parallelize across multiple sessions; the
application modules (`core-local`, `core-server`) are the
critical-path items because they're the most reflection-heavy.

## Follow-up after this PR

Once Option A lands:
- **Option B (module-path runtime)** — stop using
  `spring-boot:repackage`, ship `lib/` + launcher.
- **`jlink`** — produce a custom JRE bundle.
- **`jpackage`** — native installers for the desktop variant.
