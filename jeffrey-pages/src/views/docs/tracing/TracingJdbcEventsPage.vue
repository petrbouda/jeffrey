<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
-->

<script setup lang="ts">
import { onMounted } from 'vue';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'events', text: 'The Statement Events', level: 2 },
  { id: 'datasource', text: 'The DataSource Wrapper', level: 2 },
  { id: 'mybatis', text: 'MyBatis: Names from Mapper Methods', level: 2 },
  { id: 'manual', text: 'Emitting a Statement by Hand', level: 2 },
  { id: 'pool', text: 'Connection-Pool Events (Not Spans)', level: 2 },
  { id: 'hikari', text: 'HikariCP Integration', level: 2 },
  { id: 'pitfalls', text: 'Pitfalls', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const mybatisRegistration = `// mybatis-spring-boot-starter: declare it as a bean — every Interceptor
// bean is added to the SqlSessionFactory automatically. (On the Jeffrey
// spring-boot-starter there is nothing to declare at all.)
@Bean
JeffreyMyBatisInterceptor jeffreyMyBatisInterceptor() {
    return new JeffreyMyBatisInterceptor();
}

// Plain mybatis-spring:      factoryBean.setPlugins(new JeffreyMyBatisInterceptor());
// Programmatic MyBatis:      configuration.addInterceptor(new JeffreyMyBatisInterceptor());
// XML config:                <plugins><plugin interceptor="cafe.jeffrey.jfr.events.mybatis.JeffreyMyBatisInterceptor"/></plugins>`;

const manualEmit = `// TracedEvents.emit is the whole leaf lifecycle: guard, begin, end on
// success, failed(t) on a throw (the span shows red), commitSpan() nesting
// the statement under the span in progress on this thread.
JdbcQueryEvent event = new JdbcQueryEvent("UserMapper.selectById", "UserMapper");
List<User> users = TracedEvents.emit(event,
        () -> doQuery(),                     // checked exceptions carry through typed
        (e, result) -> {                     // runs only when actually committing;
            e.sql = sql;                     // result is null on the failure path
            e.rows = result != null ? result.size() : 0;
        });

// Verb dispatch when the statement kind is dynamic:
//   JdbcStatementEvents.forVerb("INSERT", name, group)  -> JdbcInsertEvent
//   JdbcStatementEvents.forSql(sql, name, group)        -> parses the leading verb
//     (skips whitespace and comments; WITH counts as a query)`;

const manualOutput = `jeffrey.JdbcQuery {
  duration = 3.9 ms
  traceId = 6872570733206835563
  spanId = 903275117
  parentSpanId = 4444722480460712002   // nested under the request's span
  name = "UserMapper.selectById"
  kind = "CLIENT"
  status = "UNSET"
  sql = "select * from users where id = ?"    // placeholders LEFT IN
  params = "{\\"id\\":42}"                       // the values live here instead
  group = "UserMapper"
  rows = 1
}`;

const streamExample = `// A query consumed as a stream commits from close() — after the enclosing
// binding is gone. JdbcStreamEvent is the deferred-commit variant: stamp
// eagerly inside the span, commit later.
JdbcStreamEvent event = new JdbcStreamEvent("stream_events", "profile");
Tracer.stamp(event);                     // capture identity NOW
event.begin();
Stream<EventRow> rows = runStreamingQuery();
return rows.onClose(() -> {
    event.end();
    if (event.shouldCommit()) {
        event.sql = sql;
        event.commitSpan();              // ids already set — never re-stamped
    }
});`;

const hikariExample = `// jeffrey-tracing-hikari: one metrics tracker on the pool
HikariConfig config = new HikariConfig();
config.setMetricsTrackerFactory(new JfrMetricsTrackerFactory());

// On the Jeffrey spring-boot-starter this is automatic
// (jeffrey.tracing.hikari-enabled=true, the default).`;

const poolManual = `// For any other pool: emit from the pool's own hook points.
// Durations/timeouts per operation:
PooledJdbcConnectionAcquiredEvent event = new PooledJdbcConnectionAcquiredEvent();
event.poolName = poolName;
event.elapsedTime = elapsedAcquiredNanos;
event.commit();                          // plain commit — pool events are NOT spans

// The gauge is periodic — register once, JFR calls it on its own schedule:
FlightRecorder.addPeriodicEvent(JdbcPoolStatisticsEvent.class, () -> {
    JdbcPoolStatisticsEvent stats = new JdbcPoolStatisticsEvent();
    stats.poolName = poolName;
    stats.total = poolBean.getTotalConnections();
    stats.active = poolBean.getActiveConnections();
    stats.idle = poolBean.getIdleConnections();
    stats.pendingThreads = poolBean.getThreadsAwaitingConnection();
    stats.commit();
});`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="JDBC Events"
      icon="bi bi-database"
    />

    <div class="docs-content">
      <p>Every SQL statement becomes a <strong>leaf span</strong> — one event per statement, split by verb — nested under whatever span was in progress when it ran, which is what makes an HTTP request show its statements as native children without either side knowing about the other. Alongside the statements, a separate family of <strong>pool events</strong> (deliberately not spans) feeds the connection-pool dashboard.</p>

      <h2 id="events">The Statement Events</h2>

      <table>
        <thead>
          <tr>
            <th>Event</th>
            <th>Emitted for</th>
            <th>Extra fields</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.JdbcQuery</code></td>
            <td>SELECT (and <code>WITH</code>)</td>
            <td><code>samples</code></td>
          </tr>
          <tr>
            <td><code>jeffrey.JdbcInsert</code></td>
            <td>INSERT</td>
            <td><code>isLob</code>, <code>isBatch</code></td>
          </tr>
          <tr>
            <td><code>jeffrey.JdbcUpdate</code></td>
            <td>UPDATE</td>
            <td>—</td>
          </tr>
          <tr>
            <td><code>jeffrey.JdbcDelete</code></td>
            <td>DELETE</td>
            <td>—</td>
          </tr>
          <tr>
            <td><code>jeffrey.JdbcExecute</code></td>
            <td>DDL and anything else</td>
            <td>—</td>
          </tr>
          <tr>
            <td><code>jeffrey.JdbcStream</code></td>
            <td>A query consumed as a stream (deferred commit)</td>
            <td>extends <code>JdbcQueryEvent</code></td>
          </tr>
        </tbody>
      </table>

      <p>All extend <code>JdbcBaseEvent</code> — constructor <code>(String name, String group)</code>, kind always <code>CLIENT</code>, <code>@Span("{name}")</code> — and carry <code>sql</code>, <code>params</code> (JSON), <code>group</code> (the Database dashboard groups on it) and <code>rows</code> (returned for a query, affected for everything else). Failures are recorded with <code>event.failed(throwable)</code>, never by setting <code>status</code> directly.</p>

      <DocsCallout type="info">
        <strong><code>sql</code> keeps its <code>?</code> placeholders on purpose.</strong> Identical statements aggregate in the dashboard; the values that made one execution slow live in <code>params</code>. Inlining values into the SQL text would give every execution its own statement — and its own constant-pool entry.
      </DocsCallout>

      <h2 id="datasource">The DataSource Wrapper</h2>

      <p><code>jeffrey-tracing-jdbc</code> wraps a <code>DataSource</code> so that <strong>every</strong> statement is recorded, whoever issues it — JdbcTemplate, Hibernate, jOOQ and MyBatis alike — with names derived from the SQL. On the <router-link to="/docs/tracing/http-events">Spring Boot starter</router-link> this is automatic: every <code>DataSource</code> bean is wrapped (<code>jeffrey.tracing.jdbc-enabled=true</code>, the default). On plain Spring, <code>@Import(JeffreyJdbcTracingConfiguration.class)</code>; without Spring, wrap the pool's <code>DataSource</code> in <code>TracingDataSource</code> where you build it.</p>

      <h2 id="mybatis">MyBatis: Names from Mapper Methods</h2>

      <p><code>jeffrey-tracing-mybatis</code> intercepts at the MyBatis <code>Executor</code> and names every statement by its <strong>statement id</strong> — <code>UserMapper.selectById</code>, one name per mapper method, stable however the SQL is assembled, and the name a developer would search for. It also records the parameter values the statement was bound with (<code>{"id":42,"name":"grace"}</code>), which a <code>DataSource</code> proxy cannot do cheaply.</p>

      <DocsCodeBlock :code="mybatisRegistration" language="java" />

      <DocsCallout type="warning">
        <strong>Use the MyBatis module <em>or</em> the DataSource wrapper — never both.</strong> Each records the same statement, so every mapper call would appear twice. On the Spring Boot starter this is handled: registering the MyBatis interceptor stands the <code>DataSource</code> wrapper down automatically. Wiring by hand, also set <code>jeffrey.tracing.jdbc-enabled=false</code>. The trade-off: an application using MyBatis <em>and</em> a plain <code>JdbcTemplate</code> loses the template's statements when MyBatis naming wins — set <code>jeffrey.tracing.mybatis-enabled=false</code> to leave the wrapper in charge when mixed coverage matters more than the better names.
      </DocsCallout>

      <p>Parameter capture is <strong>on by default</strong> for MyBatis — a statement's parameters are what make a slow statement readable, unlike a query string, which is free-form user input. Values are truncated at 256 characters (<code>jeffrey.tracing.mybatis-max-parameter-length</code>); LOBs and streams record <code>&lt;lob-value&gt;</code> rather than being consumed. For mappers that take e-mail addresses, tokens, or anything you would not paste into a bug report: <code>jeffrey.tracing.mybatis-capture-parameters=false</code>.</p>

      <h2 id="manual">Emitting a Statement by Hand</h2>

      <p>For a data-access layer the modules don't cover, the emit shape is the standard leaf lifecycle:</p>

      <DocsCodeBlock :code="manualEmit" language="java" />
      <DocsCodeBlock :code="manualOutput" language="text" />

      <p>A streamed result is the one statement whose commit outlives the enclosing span binding — <code>jeffrey.JdbcStream</code> exists for exactly that, paired with an eager <code>Tracer.stamp</code>:</p>

      <DocsCodeBlock :code="streamExample" language="java" />

      <h2 id="pool">Connection-Pool Events (Not Spans)</h2>

      <table>
        <thead>
          <tr>
            <th>Event</th>
            <th>What it records</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.JdbcPoolStatistics</code></td>
            <td>Periodic gauge (<code>@Period("1 s")</code>): total / idle / active / max / min connections and pending threads</td>
          </tr>
          <tr>
            <td><code>jeffrey.PooledJdbcConnectionAcquired</code></td>
            <td>How long an acquire took (<code>elapsedTime</code>)</td>
          </tr>
          <tr>
            <td><code>jeffrey.PooledJdbcConnectionBorrowed</code></td>
            <td>How long a borrow took</td>
          </tr>
          <tr>
            <td><code>jeffrey.PooledJdbcConnectionCreated</code></td>
            <td>How long creating a physical connection took</td>
          </tr>
          <tr>
            <td><code>jeffrey.AcquiringPooledJdbcConnectionTimeout</code></td>
            <td>An acquire that gave up</td>
          </tr>
        </tbody>
      </table>

      <p>These are plain events, committed with plain <code>commit()</code>, and <strong>deliberately not spans</strong>: a pool reports <em>after the fact</em>, with no interval JFR can time — a pool event drawn as a span would render as a zero-width bar while its own <code>elapsedTime</code> field claimed 200&nbsp;ms. They feed the pool dashboard instead.</p>

      <h2 id="hikari">HikariCP Integration</h2>

      <DocsCodeBlock :code="hikariExample" language="java" />

      <p>For any other pool, emit from its own hook points — the events mirror the callbacks a pool already exposes:</p>

      <DocsCodeBlock :code="poolManual" language="java" />

      <h2 id="pitfalls">Pitfalls</h2>

      <table>
        <thead>
          <tr>
            <th>Symptom</th>
            <th>Cause</th>
            <th>Fix</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Every mapper call appears twice</td>
            <td>Both the MyBatis module and the <code>DataSource</code> wrapper are active</td>
            <td>On Boot both halves are handled; wiring by hand, set <code>jeffrey.tracing.jdbc-enabled=false</code></td>
          </tr>
          <tr>
            <td>Statements in the Database dashboard but not in Traces</td>
            <td>Committed with <code>commit()</code></td>
            <td><code>TracedEvents.emit</code>, or <code>commitSpan()</code> in the <code>finally</code></td>
          </tr>
          <tr>
            <td>SQL spans are roots of their own one-span traces</td>
            <td>Statement ran outside a bound span (no HTTP filter, <code>@Async</code>, batch job)</td>
            <td>Register the root filter; wrap background work with <code>Tracer.fork</code>/<code>continueIn</code></td>
          </tr>
          <tr>
            <td>One "statement" per parameter combination</td>
            <td>Parameter values leaked into the event <strong>name</strong></td>
            <td>Name from the label or mapper id only; values go to <code>params</code> at most</td>
          </tr>
          <tr>
            <td>Failed statements look green</td>
            <td>Exception path missing <code>failed(t)</code></td>
            <td><code>TracedEvents.emit</code> records it; by hand, catch, call <code>event.failed(t)</code>, rethrow</td>
          </tr>
          <tr>
            <td>Lazy-loaded statements orphaned</td>
            <td>Lazy loading executed after the request span closed, or on another thread</td>
            <td>Prefer eager fetching in traced paths, or accept the orphan</td>
          </tr>
          <tr>
            <td>A recording carries values that should not leave the building</td>
            <td>Parameter capture is on, as it is by default for MyBatis</td>
            <td><code>jeffrey.tracing.mybatis-capture-parameters=false</code>, and treat existing recordings as containing them</td>
          </tr>
        </tbody>
      </table>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
