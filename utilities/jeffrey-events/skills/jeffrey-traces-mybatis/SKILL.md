---
name: jeffrey-traces-mybatis
description: Instrument MyBatis data access with Jeffrey JFR events so every mapper call emits a JdbcQuery/JdbcInsert/JdbcUpdate/JdbcDelete/JdbcExecute event named by its MyBatis statement id, landing as a leaf span in Jeffrey Traces and feeding the Database dashboard. Covers the jeffrey-events-mybatis module (one dependency, one registration line), recording the parameter values a statement was bound with, when to prefer it over the DataSource wrapper in jeffrey-events-jdbc, and the hand-written interceptor for older releases. Requires the jeffrey-traces-core skill for the data model, emit rules, recording setup, and verification.
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

On the Spring Boot starter there is no bean to declare — one property registers it:

```properties
jeffrey.tracing.mybatis-enabled=true
```

It is a property rather than the default because the MyBatis jar on the classpath says nothing
about whether the application uses it, and turning this on makes the `DataSource` wrapper stand
down (§2). Guessed from the classpath, that rule would silently stop recording statements in an
application that has the jar and no mappers.

## 2. This module or the `DataSource` wrapper — one of the two

`jeffrey-events-jdbc` wraps the `DataSource` and would already record MyBatis statements, because
they reach the driver like everyone else's. Use it when one wrapper should cover a mixed
application (JdbcTemplate *and* MyBatis *and* Hibernate), and accept SQL-derived names.

Use `jeffrey-events-mybatis` when MyBatis is how the application talks to the database, because
MyBatis knows something the driver cannot: the **statement id**. A `DataSource` proxy has to name a
statement by parsing its SQL (`SELECT users`); this names it `UserMapper.selectById` — one name per
mapper method, stable however the SQL is assembled, and the name a developer would search for.

Do not enable both: each records the same statement, so every mapper call appears twice, once under
each name. On the Spring Boot starter this is handled for you — `jeffrey.tracing.mybatis-enabled=true`
registers the interceptor *and* stands the `DataSource` wrapper down, so there is nothing to
remember and no window where both are recording. Wiring MyBatis by hand (a bean, XML, or
`@Import(JeffreyMyBatisTracingConfiguration.class)`) leaves that to you: set
`jeffrey.tracing.jdbc-enabled=false`, or do not import `JeffreyJdbcTracingConfiguration`.

The trade-off is worth seeing before you flip it: an application that uses MyBatis **and** a plain
`JdbcTemplate` loses the template's statements, because only one recorder runs. Leave the wrapper in
charge when the mixed coverage matters more than the better names.

## 3. What it records

| Field | Value |
|---|---|
| span name | `UserMapper.selectById` — the statement id with its package (and any enclosing class) dropped |
| `group` | `UserMapper` — the Database dashboard groups on it |
| event type | picked from `SqlCommandType`: SELECT → `JdbcQueryEvent`, INSERT → `JdbcInsertEvent`, UPDATE → `JdbcUpdateEvent`, DELETE → `JdbcDeleteEvent`, anything else → `JdbcExecuteEvent` |
| `sql` | the bound SQL, **with its `?` placeholders left in** |
| `params` | the values it was bound with, as a JSON object — see §4 |
| `rows` | rows returned for a query, rows affected for everything else |

`sql` keeping its placeholders is the point, not a limitation: identical statements aggregate in the
dashboard, and the values that made one execution slow live in `params` instead.

## 4. The values a statement ran with

MyBatis is the one integration that can record them cheaply: it hands the interceptor a `BoundSql`
carrying the parameter mappings and the parameter object — the same inputs it binds the statement
from. A `DataSource` proxy would have to intercept every `setXxx` call to know the same thing, and
`jeffrey-events-jdbc` does not.

```json
{"id":42,"name":"grace"}
```

That is what Jeffrey's Database dashboard shows beside the SQL, and it is what separates the one
slow call from the thousands that share its statement.

**It is on by default**, unlike the HTTP filter's capture flags. The two cases are not alike: a
query string is free-form user input that happens to travel with a request, while a statement's
parameters *are* the statement. Recording the SQL and hiding what it ran with makes a slow statement
unreadable.

Still, the values are recorded verbatim and a recording is a file that gets uploaded, shared and
kept. An application whose mappers take e-mail addresses, tokens, or anything else you would not
paste into a bug report turns capture off:

```properties
jeffrey.tracing.mybatis-capture-parameters=false
```

```java
new JeffreyMyBatisInterceptor(MyBatisStatementSettings.noParameters());   // or, wiring by hand
```

```xml
<plugin interceptor="cafe.jeffrey.jfr.events.mybatis.JeffreyMyBatisInterceptor">
    <property name="capture-parameters" value="false"/>
</plugin>
```

What the rules are, and why:

| Rule | Reason |
|---|---|
| Keys are the property names MyBatis uses — `id` from `@Param("id")`, `arg0` unnamed, `__frch_item_0` inside a `foreach` | the name you can match to the mapper method |
| A property used twice is written once | `#{id} … #{id}` resolves to the same value both times |
| Numbers and booleans are JSON numbers and booleans; everything else is a string | `{"id":42}`, not `{"id":"42"}` |
| `byte[]`, `Blob`, `Clob`, `InputStream`, `Reader`, `SQLXML` record `<lob-value>` | rendering a stream would consume it and break the statement |
| Values longer than 256 characters are truncated with a `…` | always on, capture flag or not: one CLOB parameter must not bloat every recording. `jeffrey.tracing.mybatis-max-parameter-length` moves the limit |
| Nothing is computed unless the event commits | a statement under the recording threshold pays for none of this |

## 5. Older releases: the hand-written interceptor

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

## 6. Correctness notes for the database

- Do not inline parameter values into `sql`; they belong in `params`, which the module fills (§4).
  Writing them into the SQL text would give every execution its own statement in the dashboard.
- Batched inserts: set `event.isBatch = true` (and skip `sql`/`params` if the batch is large) when
  you intercept batch execution.
- Lazy loading and nested selects execute wherever the property is touched. If that happens on the
  request thread inside the span, they nest correctly; if it happens later or on another thread, the
  statement records as its own root — prefer eager fetching in traced paths, or accept the orphan.

## 7. MyBatis-specific pitfalls

| Symptom | Cause | Fix |
|---|---|---|
| Every mapper call appears twice | both this module and the `DataSource` wrapper are active | on Boot, `jeffrey.tracing.mybatis-enabled=true` does both halves; wiring by hand, also set `jeffrey.tracing.jdbc-enabled=false` |
| Duplicate events per query | interceptor registered twice (bean *and* XML) | register through exactly one mechanism |
| Statements named after their SQL, not the mapper | the `DataSource` wrapper is recording them, not this | register the interceptor and disable the wrapper |
| Statements present in the Database dashboard but not in Traces | committed with `commit()` (hand-written version) | `TracedEvents.emit`, or `commitSpan()` in the `finally` |
| SQL spans are roots of their own one-span traces | statement ran outside a bound span (no HTTP filter, `@Async`, batch job) | register the root-span filter (`jeffrey-traces-spring-rest-server`); wrap background work with `Tracer.fork`/`continueIn` |
| One "statement" per parameter combination | parameter values leaked into the event **name** | name from `MappedStatement.getId()` only; values go to `params` at most |
| Failed statements look green | exception path missing `failed(t)` | `TracedEvents.emit` records it; by hand, catch `Throwable`, call `event.failed(t)`, rethrow |
| A recording carries values that should not leave the building | parameter capture is on, as it is by default | `jeffrey.tracing.mybatis-capture-parameters=false`, and treat existing recordings as containing them |
| `params` empty for statements that clearly take arguments | capture turned off somewhere — a settings bean, a plugin property | check `JeffreyMyBatisInterceptor#settings()`; a hand-declared `MyBatisStatementSettings` bean wins over the property |
