---
paths:
  - "**/*.java"
---

## Java Backend Rules

### Packages and layout
- `cafe.jeffrey.microscope.*` (microscope), `cafe.jeffrey.hub.*` (hub), `cafe.jeffrey.profile.*` (profiles), `cafe.jeffrey.*` (shared).
- REST: Spring MVC `@RestController` + class-level `@RequestMapping`, picked up by component scan — never declared as `@Bean`. Microscope controllers: `/api/internal/**`; profile-scoped ones live in `core-microscope/.../web/controllers/profile/` (`profiles/profile-management/.../resources/` holds request DTOs only). Hub controllers in `core-hub/.../web/controllers/`, DTOs in `.../web/response/`.
- Manager pattern with service-layer separation; errors are custom exceptions mapped to HTTP status by `JeffreyExceptionHandler`.

### Bean registration
- Never `@Component`, `@Service`, `@Repository`, `@Controller` or `@Autowired`. Constructor injection only.
- The only stereotypes allowed, both web-boundary: `@RestController` (required on MVC controllers) and `@ControllerAdvice` (on `JeffreyExceptionHandler`).
- Everything else is registered by `@Bean` methods in `@Configuration` classes or a Spring `BeanRegistrar`, so wiring stays visible.

### Formatting
- Braces on every control-flow body, even one statement (`if (x) { return; }`); empty body is `{ }`.
- Annotations on classes, fields and methods go on their **own line** above the declaration (`@Bean`, `@Test`, `@Mock`, `@GetMapping`, …). Parameter annotations (`@PathVariable`, `@RequestBody`) stay inline.
- Always `import`; never a fully qualified class name inline.
- AGPL header on every file, year 2026 (copy from `LICENSE_HEADER`).

### Literals and membership
- Any string or number that is matched, compared, concatenated into SQL or used as configuration lives in a `private static final` constant with a descriptive name (SQL keywords, column aliases, row caps, timeouts, error tokens). Inline literals only when obvious from context (`Math.max(0, x)`, `LIMIT 1`).
- Set membership is `private static final Set<String> X = Set.of(...)` + `X.contains(s)`, never an `equals(...) || equals(...)` ladder.

### Time
- Never `Instant.now()` / `System.currentTimeMillis()`; inject `java.time.Clock` and call `clock.instant()`.
- Elapsed time: `Measuring.r(runnable)` → `Duration`, `Measuring.s(supplier)` → `Elapsed<T>` (`cafe.jeffrey.shared.common.measure.Measuring`), never manual `System.nanoTime()`.

### Logging
- SLF4J, structured `"Description of what happened: key1={} key2={}"`, no commas between pairs.

### Types and design
- Records for DTOs, immutable data and any group of 3+ related parameters or callbacks that travel together (`ChunkWindow.Selection` is the model; `ProgressCallback` is a nine-method interface, not an example of this).
- Validate invariants in compact constructors with standard exceptions (`IllegalArgumentException`), never framework ones.
- Sealed interfaces for closed hierarchies (`LivenessRead`, `Compression`, `TimeRange`); polymorphism or a `Map` lookup over switch ladders; composition over inheritance (`CompositeToolset` delegates to one `ReflectiveToolset` per family).
- Domain code is free of framework types: map `StatusRuntimeException` etc. at the controller/gRPC boundary. Extract repeated framework boilerplate into static utilities (`GrpcExceptions.notFound(...)`).
- Temp files: one dedicated UUID subdirectory per process, deleted whole on close.
- Design over micro-optimization by default. Do not inline, pack primitives, add caches or parallelism for speed unless asked; when you spot such a trade-off, present both options in a sentence each and let the user pick.
