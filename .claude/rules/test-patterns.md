---
paths:
  - "**/*Test.java"
  - "**/*IntegrationTest.java"
---

## Java Test Rules

### Test Framework
- JUnit 5 with nested classes (`@Nested`) to group logical parts
- Mockito for mocking dependencies

### Database Tests
- Use `@DuckDBTest` custom annotation (from `shared/test`) for database integration tests
- Each test gets an isolated DuckDB instance

### Time in Tests
- Use `Clock.fixed(instant, zone)` for deterministic time behavior
- Never rely on real clock in tests

### Async Assertions
- Use Awaitility (`org.awaitility:awaitility`) for async/polling assertions
- Example: `await().atMost(5, SECONDS).untilAsserted(() -> assertEquals("expected", getResult()))`
- Never use manual `Thread.sleep` loops

### gRPC Tests
- Use `InProcessServerBuilder` / `InProcessChannelBuilder` for integration tests
- Test validation errors with expected status codes
- Test streaming with real data where applicable
- Reference: `EventStreamingGrpcServiceTest`

### Test Naming
- Test class: `{ClassUnderTest}Test` or `{Feature}IntegrationTest`
- Test methods: descriptive names explaining the scenario
- Nested classes: group by method or scenario being tested
