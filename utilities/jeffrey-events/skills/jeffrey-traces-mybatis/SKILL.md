---
name: jeffrey-traces-mybatis
description: Instrument MyBatis data access with Jeffrey JFR events — an Executor-level Interceptor that emits a JdbcQuery/JdbcInsert/JdbcUpdate/JdbcDelete/JdbcExecute event per statement, named by the MyBatis statement id, landing as leaf spans in Jeffrey Traces and feeding the Database dashboard. Use when wiring Jeffrey Traces into MyBatis (mybatis-spring-boot, mybatis-spring, or XML config). Requires the jeffrey-traces-core skill for the data model, emit rules, recording setup, and verification.
---

# Jeffrey Traces — MyBatis Instrumentation

Read **`jeffrey-traces-core`** first — it defines the data model, the emit
rules, the dependency, recording configuration, and verification. This skill
applies those rules to MyBatis.

Your mappers need **zero changes**. A MyBatis `Interceptor` (plugin) on the
`Executor` covers all mappers, including cached and batched execution.

Rules recap (from the core skill) that this code embodies:

- Every statement event is a **leaf span**: committed with `commitSpan()`
  in its own `finally`, so it nests under whatever span is in progress on the
  executing thread (usually the HTTP request), or records as untraced-but-
  present when there is none. A bare `commit()` would drop it from every trace.
- The span name must be low-cardinality: the **MyBatis statement id**
  (`UserMapper.selectById`) — one name per mapper method — never the SQL text
  or anything containing parameter values.
- Failures are recorded with `event.failed(throwable)` (sets `status=ERROR` +
  `errorType`), then rethrown.

---

## 1. The interceptor

The statement id gives a perfect low-cardinality name and `SqlCommandType`
picks the event class.

```java
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcBaseEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcStatementEvents;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.List;

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
        JdbcBaseEvent event = createEvent(statement);
        if (!event.isEnabled()) {
            return invocation.proceed();
        }

        Object parameter = invocation.getArgs()[1];
        event.begin();
        Object result = null;
        try {
            result = invocation.proceed();
            event.end();
            return result;
        } catch (Throwable t) {
            event.failed(t);        // status=ERROR + errorType; the span shows red
            throw t;
        } finally {
            if (event.shouldCommit()) {
                event.sql = statement.getBoundSql(parameter).getSql();
                event.rows = countRows(result);
                // Leaf span: nested under the HTTP request (or Tracer span)
                // in progress on this thread; untraced-but-recorded when none.
                event.commitSpan();
            }
        }
    }

    /**
     * name  = "UserMapper.selectById"  — stable, one per mapper method
     * group = "UserMapper"             — Database dashboard grouping
     */
    private static JdbcBaseEvent createEvent(MappedStatement statement) {
        String id = statement.getId();                        // com.example.mapper.UserMapper.selectById
        int methodDot = id.lastIndexOf('.');
        int mapperDot = id.lastIndexOf('.', methodDot - 1);
        String name = id.substring(mapperDot + 1);            // UserMapper.selectById
        String group = id.substring(mapperDot + 1, methodDot); // UserMapper

        // The verb-to-event mapping (SELECT -> JdbcQueryEvent, ..., other ->
        // JdbcExecuteEvent) is the library's own convention; forVerb applies it.
        // On 0.13.0, without JdbcStatementEvents, write the switch by hand.
        return JdbcStatementEvents.forVerb(statement.getSqlCommandType().name(), name, group);
    }

    private static long countRows(Object result) {
        if (result instanceof List<?> list) {
            return list.size();       // SELECT: returned rows
        }
        if (result instanceof Integer updated) {
            return updated;           // INSERT/UPDATE/DELETE: affected rows
        }
        return 0;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
}
```

## 2. Registration

- **mybatis-spring-boot-starter**: declare it as a bean — every `Interceptor`
  bean is added to the `SqlSessionFactory` automatically:

  ```java
  @Bean
  public JeffreyJfrMyBatisInterceptor jeffreyJfrMyBatisInterceptor() {
      return new JeffreyJfrMyBatisInterceptor();
  }
  ```

- **Plain mybatis-spring** (`SqlSessionFactoryBean`):
  `factoryBean.setPlugins(new JeffreyJfrMyBatisInterceptor());`
- **XML config**: `<plugins><plugin interceptor="…JeffreyJfrMyBatisInterceptor"/></plugins>`

## 3. Correctness notes for the database

- `sql` may contain `?` placeholders — that is *good*: identical statements
  aggregate. Do not inline parameter values into `sql`.
- If you also want parameter values, serialize them as a JSON object string
  into `event.params` — and scrub anything sensitive; the recording and the
  profile database will contain it verbatim.
- Batched inserts: set `event.isBatch = true` (and skip `sql`/`params` if the
  batch is large) when you intercept batch execution.
- Lazy loading / nested selects execute wherever the property is touched. If
  that happens on the request thread inside the span, they nest correctly; if
  it happens later or on another thread, the statement records as its own
  root — prefer eager fetching in traced paths or accept the orphan.

## 4. MyBatis-specific pitfalls

| Symptom | Cause | Fix |
|---|---|---|
| Statements present in the Database dashboard but not in Traces | committed with `commit()` | `commitSpan()` in the `finally` |
| SQL spans are roots of their own one-span traces | statement ran outside a bound span (no HTTP filter, `@Async`, batch job) | register the root-span filter (`jeffrey-traces-spring-rest-server`); wrap background work with `Tracer.fork`/`continueIn` |
| One "statement" per parameter combination | parameter values leaked into the event **name** | name from `MappedStatement.getId()` only; values go to `params` at most |
| Failed statements look green | exception path missing `failed(t)` | catch `Throwable`, call `event.failed(t)`, rethrow |
| Duplicate events per query | interceptor registered twice (bean + XML) | register through exactly one mechanism |
