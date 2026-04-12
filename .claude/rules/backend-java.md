---
paths:
  - "**/*.java"
---

## Java Backend Rules

### Bean Registration
- Never use `@Component`, `@Service`, `@Repository`, `@Controller`, `@RestController`, or `@Autowired`
- Always register beans via `@Bean` methods in `@Configuration` classes or Spring 4 `BeanRegistrar`

### Time Handling
- Never use `Instant.now()` or `System.currentTimeMillis()`
- Inject `java.time.Clock` as a constructor parameter, use `clock.instant()`

### Elapsed Time Measurement
- Use `Measuring.r(runnable)` for void operations (returns `Duration`)
- Use `Measuring.s(supplier)` for value-returning operations (returns `Elapsed<T>`)
- Never use manual `System.nanoTime()` bookkeeping

### Logging
- Use SLF4J structured key-value format: `"Description: key1={} key2={}"`
- No commas between key-value pairs

### License Header
- All Java files must include the AGPL header with year 2026

### Imports
- Always use import statements, never fully qualified class names inline

### Records and Sealed Types
- Use records for DTOs and immutable data
- Use sealed interfaces for type-safe hierarchies
- Validate invariants in compact constructors with `IllegalArgumentException`
- Keep domain logic free of framework types (gRPC, Jersey, Spring)
