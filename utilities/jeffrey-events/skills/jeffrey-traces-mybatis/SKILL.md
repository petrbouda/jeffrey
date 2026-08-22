---
name: jeffrey-traces-mybatis
description: Instrument MyBatis data access with Jeffrey JFR events so every mapper call emits a JdbcQuery/JdbcInsert/JdbcUpdate/JdbcDelete/JdbcExecute event named by its MyBatis statement id, landing as a leaf span in Jeffrey Traces and feeding the Database dashboard. Covers the jeffrey-events-mybatis module (one dependency, one registration line), when to prefer it over the DataSource wrapper in jeffrey-events-jdbc, and the hand-written interceptor for older releases. Requires the jeffrey-traces-core skill for the data model, emit rules, recording setup, and verification.
---

# Jeffrey Traces — MyBatis Instrumentation

Read **`jeffrey-traces-core`** first — it defines the data model, the emit rules, the dependency,
recording configuration, and verification. This skill applies those rules to MyBatis.

Your mappers need **zero changes**. One statement event per mapper call, committed as a leaf under
whatever span is in progress.

Rules recap (from the core skill) that this embodies:

- Every statement event is a **leaf span**: committed with `commitSpan()`, so it nests under the
  span in progress on the executing thread (usually the HTTP request), or records as
  untraced-but-present when there is none. A bare `commit()` would drop it from every trace.
- The span name must be low-cardinality: the **MyBatis statement id** (`UserMapper.selectById`) —
  one name per mapper method — never the SQL text or anything carrying parameter values.
- Failures are recorded with `event.failed(throwable)` (sets `status=ERROR` + `errorType`) and
  rethrown.

---

## 1. Add the module, register it once

```xml
<dependency>
    <groupId>cafe.jeffrey-analyst</groupId>
    <artifactId>jeffrey-events-mybatis</artifactId>
    <version><!-- latest release --></version>
</dependency>
```

The interceptor is `cafe.jeffrey.jfr.events.mybatis.JeffreyMyBatisInterceptor`. Register it through
**exactly one** of these — registered twice, it records every statement twice:

- **mybatis-spring-boot-starter**: declare it as a bean; every `Interceptor` bean is added to the
  `SqlSessionFactory` automatically.

  ```java
  @Bean
  JeffreyMyBatisInterceptor jeffreyMyBatisInterceptor() {
      return new JeffreyMyBatisInterceptor();
  }
  ```

- **Plain mybatis-spring** (`SqlSessionFactoryBean`):
  `factoryBean.setPlugins(new JeffreyMyBatisInterceptor());`
- **Programmatic MyBatis**: `configuration.addInterceptor(new JeffreyMyBatisInterceptor());`
- **XML config**:
  `<plugins><plugin interceptor="cafe.jeffrey.jfr.events.mybatis.JeffreyMyBatisInterceptor"/></plugins>`

That is the whole integration. MyBatis is intercepted at the `Executor`, so cached and batched
execution are covered as well.

## 2. This module or the `DataSource` wrapper — one of the two

`jeffrey-events-jdbc` wraps the `DataSource` and would already record MyBatis statements, because
they reach the driver like everyone else's. Use it when one wrapper should cover a mixed
application (JdbcTemplate *and* MyBatis *and* Hibernate), and accept SQL-derived names.

Use `jeffrey-events-mybatis` when MyBatis is how the application talks to the database, because
MyBatis knows something the driver cannot: the **statement id**. A `DataSource` proxy has to name a
statement by parsing its SQL (`SELECT users`); this names it `UserMapper.selectById` — one name per
mapper method, stable however the SQL is assembled, and the name a developer would search for.

Do not enable both: each records the same statement, so every mapper call appears twice, once under
each name. On Spring Boot the `DataSource` wrapper is on by default — turn it off with
`jeffrey.tracing.jdbc-enabled=false` when you register this interceptor.

## 3. What it records

| Field | Value |
|---|---|
| span name | `UserMapper.selectById` — the statement id with its package (and any enclosing class) dropped |
| `group` | `UserMapper` — the Database dashboard groups on it |
| event type | picked from `SqlCommandType`: SELECT → `JdbcQueryEvent`, INSERT → `JdbcInsertEvent`, UPDATE → `JdbcUpdateEvent`, DELETE → `JdbcDeleteEvent`, anything else → `JdbcExecuteEvent` |
| `sql` | the bound SQL, **with its `?` placeholders left in** |
| `rows` | rows returned for a query, rows affected for everything else |

`sql` keeping its placeholders is the point, not a limitation: identical statements aggregate in the
dashboard. Parameter values are never recorded — a recording is a file that gets uploaded and kept.

## 4. Older releases: the hand-written interceptor

Before the module existed, this was copy-paste. If you are pinned to such a release:

```java
@Intercepts({
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                        CacheKey.class, BoundSql.class})
})
public class JeffreyJfrMyBatisInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];

        // TracedEvents.emit is the whole leaf lifecycle: guard, begin, end on success, failed(t)
        // on a throw (the span shows red), commitSpan() nesting the statement under the span in
        // progress on this thread — untraced-but-recorded when there is none.
        return TracedEvents.emit(createEvent(statement),
                invocation::proceed,
                (event, result) -> {
                    event.sql = statement.getBoundSql(parameter).getSql();
                    event.rows = countRows(result);   // null result on failure -> 0
                });
    }

    private static JdbcBaseEvent createEvent(MappedStatement statement) {
        String id = statement.getId();                          // com.example.mapper.UserMapper.selectById
        int methodDot = id.lastIndexOf('.');
        int mapperDot = id.lastIndexOf('.', methodDot - 1);
        String name = id.substring(mapperDot + 1);              // UserMapper.selectById
        String group = id.substring(mapperDot + 1, methodDot);  // UserMapper

        // The verb-to-event mapping is the library's own convention; forVerb applies it.
        // On 0.13.0, without JdbcStatementEvents, write the switch by hand.
        return JdbcStatementEvents.forVerb(statement.getSqlCommandType().name(), name, group);
    }

    private static long countRows(Object result) {
        return switch (result) {
            case List<?> rows -> rows.size();       // SELECT: returned rows
            case Integer affected -> affected;      // INSERT/UPDATE/DELETE: affected rows
            case null, default -> 0;
        };
    }
}
```

Note what this version does not handle: a mapper declared as a nested interface arrives as
`Outer$UserMapper.selectById`, so it is named `Outer$UserMapper.selectById` while the same mapper
declared top-level is named `UserMapper.selectById` — two names for one shape. The module drops the
enclosing class too.

## 5. Correctness notes for the database

- Do not inline parameter values into `sql`. If you want them, serialize them as a JSON object
  string into `event.params` (`SpanAttributes` builds one with no dependency) — and scrub anything
  sensitive; the recording and the profile database contain it verbatim.
- Batched inserts: set `event.isBatch = true` (and skip `sql`/`params` if the batch is large) when
  you intercept batch execution.
- Lazy loading and nested selects execute wherever the property is touched. If that happens on the
  request thread inside the span, they nest correctly; if it happens later or on another thread, the
  statement records as its own root — prefer eager fetching in traced paths, or accept the orphan.

## 6. MyBatis-specific pitfalls

| Symptom | Cause | Fix |
|---|---|---|
| Every mapper call appears twice | both this module and the `DataSource` wrapper are active | pick one; on Boot set `jeffrey.tracing.jdbc-enabled=false` |
| Duplicate events per query | interceptor registered twice (bean *and* XML) | register through exactly one mechanism |
| Statements named after their SQL, not the mapper | the `DataSource` wrapper is recording them, not this | register the interceptor and disable the wrapper |
| Statements present in the Database dashboard but not in Traces | committed with `commit()` (hand-written version) | `TracedEvents.emit`, or `commitSpan()` in the `finally` |
| SQL spans are roots of their own one-span traces | statement ran outside a bound span (no HTTP filter, `@Async`, batch job) | register the root-span filter (`jeffrey-traces-spring-rest-server`); wrap background work with `Tracer.fork`/`continueIn` |
| One "statement" per parameter combination | parameter values leaked into the event **name** | name from `MappedStatement.getId()` only; values go to `params` at most |
| Failed statements look green | exception path missing `failed(t)` | `TracedEvents.emit` records it; by hand, catch `Throwable`, call `event.failed(t)`, rethrow |
