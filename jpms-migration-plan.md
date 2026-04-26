# Jeffrey — JPMS / jlink Migration Plan

Living plan for trimming Jeffrey's distribution via `jlink` and removing
classpath-scanning blockers along the way. Two parts:

1. **Feasibility findings** — what's possible today, what blocks strict JPMS.
2. **First step — Jersey → Spring REST migration** — concrete plan that
   removes the only hard JPMS blocker in our own code while preserving the
   project's "no stereotype scanning, explicit `@Bean`" wiring style.

---

## Part 1 — Feasibility findings

### TL;DR

Full JPMS modularization is **not** worth it. The combination of Spring
Boot 4 + Jersey package-scanning + shaded gRPC dependencies forces a hybrid
(classpath + module path) regardless of effort spent. **However, `jlink`
itself does not require JPMS** — we can ship a custom, trimmed JRE today
with zero application code changes once the Jersey scanning is gone (and
even before that, since classpath jars run fine on a jlinked JRE).

**Recommendation:** keep the app on the classpath, use `jlink` to ship a
minimal JRE (~40–50 % smaller than full JDK 25). Targeted Jersey →
Spring REST migration removes the last classpath-scan in our own code and
opens the door to optional per-module `module-info.java` later.

### Current state (verified)

- 60 Maven modules, **zero** `module-info.java`.
- Both apps are Spring Boot 4.0.5 fat jars produced by
  `spring-boot-maven-plugin:repackage` (`build/build-local`,
  `build/build-server`).
- Both depend on `grpc-netty-shaded` (`jeffrey-local/grpc-client/pom.xml:41`,
  `jeffrey-server/core-server/pom.xml:99`).
- UI is bundled as classpath resources from `pages-local` / `pages-server`
  and served through Spring's `ClassPathResource` resolver.

### Blockers for strict JPMS

#### Jersey package scanning — **hard blocker** (verified)

`jeffrey-local/core-local/.../resources/JerseyConfig.java:41` and the
matching server file:

```java
packages("pbouda.jeffrey.resources", "pbouda.jeffrey.profile.resources");
```

Jersey's `packages(...)` walks the classpath looking for `@Path` /
`@Provider` classes. Under strict JPMS, modules cannot be scanned this way
across boundaries — every resource class would have to be enumerated with
explicit `register(...)` calls. We have 42 such classes today.

#### Shaded uber-jars become unnamed modules — **hard blocker**

- `grpc-netty-shaded` (gRPC + Netty + Guava + OpenCensus all repackaged) —
  no `Automatic-Module-Name`.
- `duckdb_jdbc` 1.5.x — no `Automatic-Module-Name`.

A named module cannot `requires` an unnamed module. The standard
workaround is to keep these jars on the classpath while named modules sit
on the module path — which means the application is **never fully
modular**. Full cost, partial benefit.

#### Spring Boot 4 / Spring 7 — partial JPMS only

Spring publishes `Automatic-Module-Name` headers but does not ship real
`module-info` descriptors, and embedded Tomcat is similarly
half-modularized. Spring's documented stance remains classpath-first.

### Things that help

- **No Spring component scanning.** The codebase enforces explicit `@Bean`
  registration (`.claude/rules/backend-java.md`). This eliminates an
  entire class of JPMS pain.
- **Reflection is local.** The only deep reflection
  (`DominatorTreeReflection` in `heap-dump`) is intra-module — easy to
  handle with `opens` if we ever do modularize.
- **No SPI proliferation.** `META-INF/services` shows up for SLF4J, JDBC,
  and Flyway-style standard SPI only — all JPMS-compatible via
  `ServiceLoader`.

### Three possible paths

| Approach | What it is | Effort | What you get |
|---|---|---|---|
| **(a) Full JPMS** | Write `module-info` for ~30 internal modules, refactor Jersey, accept hybrid for shaded jars | ~25–30 h dev + ongoing maintenance | Strict encapsulation; jlink works; **but** still hybrid because of `grpc-netty-shaded` and `duckdb_jdbc` |
| **(b) jlink only** *(recommended)* | Keep app on classpath. Use `jlink` to build a minimal JRE | ~2–4 h | ~40–50 % smaller distribution, no code changes, works with current fat jar |
| **(c) jpackage** | (b) + native installers (DMG / MSI / DEB) | +4–6 h on top of (b) | End-user installer story |

`jlink` only needs the *JDK* to be modular — which it already is — to
produce a trimmed runtime image. Our app stays on the classpath, the fat
jar still works, and we get the binary-size win.

Sketch for `build/build-local/pom.xml`:

```bash
jlink \
  --module-path "$JAVA_HOME/jmods" \
  --add-modules java.base,java.logging,java.sql,java.xml,java.naming,\
                java.management,java.net.http,java.desktop,\
                jdk.jfr,jdk.unsupported,jdk.crypto.ec \
  --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
  --output target/runtime
```

Distribution layout:

```
jeffrey-local/
  runtime/        (jlinked JRE, ~70–90 MB)
  lib/jeffrey.jar (existing Spring Boot fat jar)
  bin/jeffrey     (script: exec runtime/bin/java -jar lib/jeffrey.jar "$@")
```

The `--add-modules` set must be validated with `jdeps
--print-module-deps --ignore-missing-deps` against the produced fat jars.

### Recommendation

1. **Don't pursue full JPMS modularization.** Pay-off is poor.
2. **Add a `jlink` step** to `build/build-local` and `build/build-server`.
3. **Optionally add `jpackage`** later for desktop installers.
4. **Migrate Jersey → Spring REST** (Part 2) to remove the last
   classpath-scan in our own code. Side effect: ~10–15 fewer jars in the
   fat jar, simpler stack, future-proofs us if we ever do want module-info
   files.

---

## Part 2 — First step: Jersey → Spring REST migration

### Why this first

The Jersey `packages(...)` scan is the **only piece of our own code** that
is fundamentally incompatible with strict JPMS. Removing it:

- Eliminates the classpath scan and prepares for jlink-friendly modularity.
- Drops `spring-boot-starter-jersey`, Jersey core, HK2, Jersey media
  jars (~10–15 fewer transitive jars).
- Removes the parallel servlet/container stack that sits next to Spring.
  We already run on Spring Boot — second framework gone.
- Removes the custom `shared/jackson-jaxrs` module (Spring 7 has native
  `tools.jackson` 3 support via `JacksonJsonHttpMessageConverter`).

### Does it make sense?

Yes. The cost is far smaller than typical JAX-RS → Spring rewrites
because of how this codebase is already shaped:

- All 42 resources are **plain classes registered as `@Bean`** with
  constructor injection (verified). No `@Component` /
  `@Autowired` / `@RestController` to unwind.
- Domain logic is already **framework-neutral**
  (`.claude/rules/backend-java.md` enforces this). Only the resource
  classes themselves touch Jersey types.
- Jersey-specific surface is small:
  - 2 `JerseyConfig`, 2 `*JerseyConfigurer`
  - 1 `ExceptionMappers`
  - 2 filters (`JfrHttpEventFilter`, `RequestLoggingFilter`)
  - 1 `JacksonJson3Feature` (in `shared/jackson-jaxrs`)
  - SSE in **1** resource, multipart in **~6** resources

### Approach: Spring functional routing (`RouterFunction` / `WebMvc.fn`)

To preserve the project's "no stereotype scanning, everything is an
explicit `@Bean`" rule, **do not** migrate to `@RestController`. Use
Spring's functional web routing instead.

#### Why functional routing fits this codebase

| Project rule | Annotation MVC | Functional routing |
|---|---|---|
| No `@RestController` / `@Controller` | ✗ | ✓ — handlers are plain classes |
| No classpath scanning | mostly ✓ (bean iteration, not classpath) | ✓ — pure `@Bean` |
| Explicit `@Bean` registration | needs care | ✓ — every route is a `@Bean RouterFunction<ServerResponse>` |
| Domain free of framework types | ✓ | ✓ — handler classes touch only `ServerRequest` / `ServerResponse` |
| JPMS-friendly | ✓ | ✓ — zero reflection over packages |

Functional routing is fully supported in Spring Boot 4 / Spring 7 with
embedded Tomcat (no need to switch to WebFlux/Netty). All standard MVC
features work: content negotiation, validation, filters,
exception handling.

#### Per-resource skeleton

Plain handler class (was: `RemoteWorkspacesResource` with `@Path`):

```java
public class RemoteWorkspacesHandler {
    private final RemoteClients.Factory remoteClientsFactory;
    private final WorkspacesManager workspacesManager;

    public RemoteWorkspacesHandler(
            RemoteClients.Factory remoteClientsFactory,
            WorkspacesManager workspacesManager) {
        this.remoteClientsFactory = remoteClientsFactory;
        this.workspacesManager = workspacesManager;
    }

    public ServerResponse list(ServerRequest req) {
        var body = req.body(RemoteWorkspaceConnectionRequest.class);
        // ... existing logic ...
        return ServerResponse.ok().body(result);
    }

    public ServerResponse create(ServerRequest req) { /* ... */ }
}
```

Routes declared as a `@Bean` in a `@Configuration` class:

```java
@Bean
public RouterFunction<ServerResponse> remoteWorkspaceRoutes(
        RemoteWorkspacesHandler handler) {
    return route()
        .path("/api/internal/remote-workspaces", b -> b
            .POST("/list",   handler::list)
            .POST("/create", handler::create))
        .build();
}
```

Same shape as today's Jersey resources — plain classes, registered as
beans, with HTTP routes declared explicitly. The routing info just moves
from class-level `@Path` annotations to a `RouterFunction` factory.

### Migration phases

#### Phase 0 — Preparation (~1–2 days)

- Add `spring-boot-starter-web` alongside `spring-boot-starter-jersey`
  (both run together during the cutover).
- Configure Spring MVC under a different prefix so both stacks can
  coexist during migration (e.g. Jersey on `/api/...`, Spring on
  `/api2/...`, then swap at the end).
- Wire `JacksonJsonHttpMessageConverter` (Spring 7 / Jackson 3) using the
  same `ObjectMapper` as `JacksonJson3Feature` produces.
- Port `ExceptionMappers` to a `HandlerExceptionResolver` `@Bean`.
- Port `RequestLoggingFilter` and `JfrHttpEventFilter` to
  `jakarta.servlet.Filter` beans (or Spring `HandlerInterceptor`).
- Verify `ClassPathResource` + `pages-local` SPA still serves correctly
  (no functional change expected).

#### Phase 1 — Pilot (1 day)

- Migrate **`RemoteWorkspacesResource`** (small, self-contained, 2–3
  endpoints).
- Write/port tests using `MockMvc` or `WebTestClient`.
- Lock down the patterns: route configuration, request body binding, path
  variables, error mapping, JSON encoding.
- Document the pattern at the top of this file (or a sibling cookbook).

#### Phase 2 — Bulk migration (~3–5 days)

Migrate by module so each commit is a green build:

| Module | Files |
|---|---|
| `jeffrey-local/core-local/.../resources/` | 17 |
| `jeffrey-local/profiles/profile-management/.../resources/` | 22 |
| `jeffrey-server/core-server/.../resources/` | 3 |

Special cases to handle as they appear:

- **Multipart upload (~6 files)** — JAX-RS `@FormDataParam` →
  `MultipartFile` parameter / `request.multipartData()` in functional
  handlers. Verify `spring.servlet.multipart.max-file-size` is set to
  match Jersey's current limits.
- **SSE (`HeapDumpResource`)** — Jersey `SseFeature` / `SseEventSink` →
  Spring `ServerResponse.sse(Consumer<SseBuilder>)` from `WebMvc.fn`
  (Spring 6+). Plain Servlet under the hood — no Reactor / WebFlux
  needed, same thread / container model we use today. `SseBuilder` is
  thread-safe for `send` / `complete` / `error`, so long-running streams
  can push from a background executor; pass an explicit timeout via
  `ServerResponse.sse(timeout, consumer)` for analyses that may run for
  minutes (heap-dump analysis).
- **Streaming endpoints** (`ProjectLiveStreamResource`,
  `ProjectReplayStreamResource`, `ProjectDownloadTaskResource`) — Spring
  supports `StreamingResponseBody` and `ResponseEntity<InputStreamResource>`.
- **gRPC error translation** — already isolated to resource classes; keep
  it there in the new handlers (per the rule "map framework-specific
  types at the boundary").

#### Phase 3 — Cleanup (1 day)

- Remove `spring-boot-starter-jersey` from `core-local`, `core-server`,
  test modules.
- Delete `JerseyConfig`, `LocalJerseyConfigurer`,
  `ServerJerseyConfigurer`, `ExceptionMappers` (Jersey version),
  Jersey filter classes.
- Delete `shared/jackson-jaxrs` and its dependents — Spring 7 wires
  Jackson 3 natively.
- Drop the `/api2` prefix; Spring routes take over `/api/...`.
- Remove Jersey from the reactor BOM if it lingers there.

#### Phase 4 — Follow-up (parallel / after)

- Add a `jlink` build step in `build/build-local` and `build/build-server`
  (the original goal).
- Optional: add `module-info.java` to leaf modules
  (e.g. `shared/common`, `shared/sql-builder`) where it's cheap and
  uncontroversial. **Do not** add module-info to `core-local` /
  `core-server` until shaded-jar story changes — that's where the hybrid
  forces remain.

### Risk register

| Risk | Mitigation |
|---|---|
| Subtle param-binding differences (Jersey vs. Spring) | Pilot phase locks down patterns; integration tests catch regressions per endpoint. |
| Multipart / SSE behaviour diverges | Targeted manual tests against the `pages-local` SPA for each affected endpoint. |
| Tests rely on `JerseyTest` | Replace with `MockMvc` / `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`. |
| Jackson 3 wiring in Spring 7 | Verify `JacksonJsonHttpMessageConverter` (Spring 7 / `tools.jackson`) is on the classpath before Phase 0; fall back to a manually registered converter if auto-config is missing. |
| Two web stacks coexisting during cutover | Use distinct path prefixes (`/api` vs `/api2`); flip at the end of Phase 2. |

### Pre-flight checklist before starting Phase 0

- [ ] Confirm Spring Boot 4.0.5 ships `JacksonJsonHttpMessageConverter`
      (Jackson 3 variant). If not, pin the converter manually.
- [ ] Confirm functional routing supports the `tools.jackson` `ObjectMapper`
      we already configure in `JacksonJson3Feature`.
- [ ] Choose pilot resource (proposal: `RemoteWorkspacesResource`).
- [ ] Decide cutover prefix strategy (proposal: temporary `/api2` for
      Spring, swap at end of Phase 2).
- [ ] Inventory tests that extend Jersey test infrastructure
      (`AbstractResourceTest`, etc.) — plan their replacement.
