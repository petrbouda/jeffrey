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
  { id: 'events', text: 'What It Records', level: 2 },
  { id: 'registration', text: 'Registering the Interceptor', level: 2 },
  { id: 'parameters', text: 'Parameter Capture', level: 2 },
  { id: 'versus-datasource', text: 'MyBatis or the DataSource Wrapper', level: 2 },
  { id: 'spring-support', text: 'Using Spring Boot?', level: 2 },
  { id: 'pitfalls', text: 'Pitfalls', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const mybatisRegistration = `// Programmatic MyBatis: the interceptor goes on the Configuration
Configuration configuration = new Configuration(environment);
configuration.addInterceptor(new JeffreyMyBatisInterceptor());

// mybatis-spring, building the factory yourself
factoryBean.setPlugins(new JeffreyMyBatisInterceptor());

// Register it through exactly ONE mechanism — these two, the XML form below,
// or (on Spring) an Interceptor bean, which mybatis-spring adds to the
// SqlSessionFactory it builds. Registered twice, it records every statement twice.`;

const mybatisXmlRegistration = `<plugins>
    <plugin interceptor="cafe.jeffrey.jfr.events.mybatis.JeffreyMyBatisInterceptor">
        <property name="capture-parameters" value="false"/>
        <property name="max-value-length" value="256"/>
    </plugin>
</plugins>`;

const settingsExample = `// The default: record parameters, truncate any value longer than 256 characters
new JeffreyMyBatisInterceptor(MyBatisStatementSettings.defaults());

// Record the statement and its name, and nothing about what it ran with
new JeffreyMyBatisInterceptor(MyBatisStatementSettings.noParameters());

// Or set the truncation point yourself
new JeffreyMyBatisInterceptor(new MyBatisStatementSettings(true, 64));`;

const mybatisOutput = `jeffrey.JdbcQuery {
  duration = 3.9 ms
  traceId = 6872570733206835563
  spanId = 903275117
  parentSpanId = 4444722480460712002   // nested under the request's span
  name = "UserMapper.selectById"       // the statement id, not parsed SQL
  kind = "CLIENT"
  status = "UNSET"
  sql = "select * from users where id = ?"
  params = "{\\"id\\":42}"                // what MyBatis bound it with
  group = "UserMapper"
  rows = 1
}`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="MyBatis Events"
      icon="bi bi-diagram-2"
    />

    <div class="docs-content">
      <p><code>jeffrey-tracing-mybatis</code> intercepts at the MyBatis <code>Executor</code> and names every statement by its <strong>statement id</strong> — <code>UserMapper.selectById</code>, one name per mapper method, stable however the SQL is assembled, and the name a developer would search for. It also records the parameter values the statement was bound with (<code>{"id":42,"name":"grace"}</code>), which a <code>DataSource</code> proxy cannot do cheaply.</p>

      <h2 id="events">What It Records</h2>

      <p>The same statement events as any other JDBC instrumentation — <code>jeffrey.JdbcQuery</code>, <code>jeffrey.JdbcInsert</code>, <code>jeffrey.JdbcUpdate</code>, <code>jeffrey.JdbcDelete</code>, and <code>jeffrey.JdbcExecute</code> for anything whose <code>SqlCommandType</code> is neither — each a <strong>leaf span</strong> nested under whatever span was in progress. See <router-link to="/docs/tracing/jdbc-events">JDBC Events</router-link> for the full event table and their fields; what changes here is the <code>name</code>, the <code>group</code> and the presence of <code>params</code>:</p>

      <DocsCodeBlock :code="mybatisOutput" language="text" />

      <p>The <code>Executor</code> is the interception point rather than <code>StatementHandler</code>, so cached and batched execution are covered too. Under <code>ExecutorType.BATCH</code>, read the result with that in mind: <code>flushStatements</code> is not intercepted, so a span times the <em>enqueue</em> rather than the flush, and <code>rows</code> carries MyBatis' batch sentinel instead of a count.</p>

      <h2 id="registration">Registering the Interceptor</h2>

      <DocsCodeBlock :code="mybatisRegistration" language="java" />

      <p>Registered through XML or MyBatis' own configuration properties, the settings arrive as plugin properties rather than a constructor argument:</p>

      <DocsCodeBlock :code="mybatisXmlRegistration" language="xml" />

      <h2 id="parameters">Parameter Capture</h2>

      <p>Parameter capture is <strong>on by default</strong> for MyBatis, unlike the HTTP filter's capture flags. The two cases are not the same: a query string is free-form user input that happens to travel with a request, while a statement's parameters <em>are</em> the statement — the reason one call out of thousands with the same SQL was the slow one, and what Jeffrey's Database dashboard has a column for. Recording the SQL and hiding what it ran with makes the slow statement unreadable.</p>

      <DocsCodeBlock :code="settingsExample" language="java" />

      <p>Values are truncated at 256 characters by default, whatever the source, so one CLOB parameter cannot bloat every recording; LOBs and streams record <code>&lt;lob-value&gt;</code> rather than being consumed.</p>

      <DocsCallout type="warning">
        <strong>The values are recorded verbatim, and a recording is a file that gets uploaded, shared and kept.</strong> An application whose mappers take e-mail addresses, tokens or anything else you would not paste into a bug report should use <code>MyBatisStatementSettings.noParameters()</code> — and treat recordings already made as containing them.
      </DocsCallout>

      <h2 id="versus-datasource">MyBatis or the DataSource Wrapper</h2>

      <p>MyBatis is worth instrumenting directly even though the <router-link to="/docs/tracing/jdbc-events">DataSource wrapper</router-link> would already catch its statements, because MyBatis knows something the driver does not: the statement id. A proxy has to name a statement by reading its SQL; this names it by the mapper method.</p>

      <DocsCallout type="warning">
        <strong>Use the MyBatis module <em>or</em> the DataSource wrapper — never both.</strong> Each records the same statement, so every mapper call would appear twice: once under <code>UserMapper.selectById</code>, once under a name parsed out of its SQL. The trade-off: an application using MyBatis <em>and</em> a plain <code>JdbcTemplate</code> loses the template's statements when MyBatis naming wins — leave the wrapper in charge instead when mixed coverage matters more than the better names.
      </DocsCallout>

      <h2 id="spring-support">Using Spring Boot?</h2>

      <p>The starter registers the interceptor for any application with a <code>SqlSessionFactory</code> bean and stands the <code>DataSource</code> wrapper down for you, so the double-recording above cannot happen. Parameter capture and truncation are bound from <code>jeffrey.tracing.*</code> instead of the settings record.</p>

      <DocsLinkCard
        to="/docs/tracing/spring-support"
        icon="bi bi-flower1"
        title="Spring Support"
        description="The MyBatis auto-configuration, why a SqlSessionFactory bean is the condition, and the jeffrey.tracing.mybatis-* properties."
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
            <td>Both the MyBatis interceptor and the <code>DataSource</code> wrapper are active</td>
            <td>Register one of the two, never both</td>
          </tr>
          <tr>
            <td>Every mapper call appears twice, only one module in use</td>
            <td>The interceptor was registered through two of the mechanisms above</td>
            <td>Pick one — a bean, <code>addInterceptor</code>, <code>setPlugins</code>, or <code>&lt;plugins&gt;</code></td>
          </tr>
          <tr>
            <td>Statements named from SQL, not by mapper method</td>
            <td>The interceptor never reached the <code>SqlSessionFactory</code></td>
            <td>mybatis-spring only picks up <code>Interceptor</code> <em>beans</em>; built by hand, add it to the <code>Configuration</code></td>
          </tr>
          <tr>
            <td>Statements in the Database dashboard but not in Traces</td>
            <td>The statement ran outside a bound span (no HTTP filter, a batch job)</td>
            <td>Register the root filter; wrap background work with <router-link to="/docs/tracing/tracer-api/fork">Tracer.fork</router-link> / <router-link to="/docs/tracing/tracer-api/continue-in">continueIn</router-link></td>
          </tr>
          <tr>
            <td>A recording carries values that should not leave the building</td>
            <td>Parameter capture is on, as it is by default</td>
            <td><code>MyBatisStatementSettings.noParameters()</code>, and treat existing recordings as containing them</td>
          </tr>
          <tr>
            <td>Lazy-loaded statements orphaned</td>
            <td>Lazy loading executed after the request span closed, or on another thread</td>
            <td>Prefer eager fetching in traced paths, or accept the orphan</td>
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
