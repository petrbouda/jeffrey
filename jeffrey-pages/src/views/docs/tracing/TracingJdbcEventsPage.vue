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
import DocsLinkCard from '@/components/docs/DocsLinkCard.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'events', text: 'The Statement Events', level: 2 },
  { id: 'datasource', text: 'The DataSource Wrapper', level: 2 },
  { id: 'manual', text: 'Emitting a Statement by Hand', level: 2 },
  { id: 'pool', text: 'Connection-Pool Events (Not Spans)', level: 2 },
  { id: 'hikari', text: 'HikariCP Integration', level: 2 },
  { id: 'spring-support', text: 'Using Spring Boot?', level: 2 },
  { id: 'pitfalls', text: 'Pitfalls', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const dataSourceExample = `// The label statements are grouped under in Jeffrey's Database dashboard —
// usually the database or pool name.
DataSource traced = new TracingDataSource(hikariDataSource, "orders-db");

// Or with names better than the default verb-and-primary-table:
DataSource traced = new TracingDataSource(hikariDataSource, "orders-db", myNaming);`;

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
config.setMetricsTrackerFactory(new JfrMetricsTrackerFactory());`;

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

      <p>All extend <code>JdbcBaseEvent</code> — constructor <code>(String name, String group)</code>, kind always <code>CLIENT</code>, <code>@Span("{name}")</code> — and carry <code>sql</code>, <code>params</code> (JSON), <code>group</code> (the Database dashboard groups on it) and <code>rows</code> (returned for a query, affected for everything else — the <code>DataSource</code> wrapper below does not proxy the <code>ResultSet</code>, so it leaves a query's <code>rows</code> at <code>0</code>). Failures are recorded with <code>event.failed(throwable)</code>, never by setting <code>status</code> directly.</p>

      <DocsCallout type="info">
        <strong><code>sql</code> keeps its <code>?</code> placeholders on purpose.</strong> Identical statements aggregate in the dashboard; the values that made one execution slow live in <code>params</code>. Inlining values into the SQL text would give every execution its own statement — and its own constant-pool entry.
      </DocsCallout>

      <h2 id="datasource">The DataSource Wrapper</h2>

      <p><code>jeffrey-tracing-jdbc</code> wraps a <code>DataSource</code> so that <strong>every</strong> statement is recorded, whoever issues it — JdbcTemplate, Hibernate, jOOQ and MyBatis alike — with names derived from the SQL. Wrapping the interface rather than instrumenting a persistence framework is what makes one module cover all of them, including the statements an ORM generates that nobody wrote by hand.</p>

      <DocsCodeBlock :code="dataSourceExample" language="java" />

      <p>Statement naming defaults to verb and primary table; an application with better names — a repository method, a mapper id — supplies its own <code>StatementNaming</code>.</p>

      <DocsCallout type="warning">
        <strong>Statement parameters are never read, let alone recorded.</strong> They are the values most likely to be personal data, and a proxy cannot know which are safe. The SQL text is recorded as the driver received it, so a statement built with placeholders stays aggregatable — and one built by string concatenation carries whatever was concatenated into it. Recording the values a statement ran with is exactly what <router-link to="/docs/tracing/mybatis-events">MyBatis Events</router-link> adds; use that module <em>or</em> this wrapper, never both, or every mapper call is recorded twice.
      </DocsCallout>

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

      <h2 id="spring-support">Using Spring Boot?</h2>

      <p>The starter wraps every <code>DataSource</code> bean and gives each Hikari pool a tracker for you — no <code>TracingDataSource</code> in your own code, and the bean name becomes the statement group.</p>

      <DocsLinkCard
        to="/docs/tracing/spring-support"
        icon="bi bi-flower1"
        title="Spring Support"
        description="The DataSource and Hikari bean post-processors, why their ordering matters, and the jeffrey.tracing.* switches."
      />

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
            <td>Both the <router-link to="/docs/tracing/mybatis-events">MyBatis module</router-link> and the <code>DataSource</code> wrapper are active</td>
            <td>Register one of the two, never both</td>
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
            <td>Parameter capture is on, as it is by default for <router-link to="/docs/tracing/mybatis-events">MyBatis</router-link></td>
            <td><code>MyBatisStatementSettings.noParameters()</code>, and treat existing recordings as containing them</td>
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
