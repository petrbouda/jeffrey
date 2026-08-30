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
import DocsSpanTree from '@/components/docs/DocsSpanTree.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'ways', text: 'Five Ways to Instrument', level: 2 },
  { id: 'model', text: 'The Tracer Model', level: 2 },
  { id: 'events', text: 'The JFR Events Behind It', level: 2 },
  { id: 'errors', text: 'Error Handling', level: 2 },
  { id: 'semantics', text: 'Semantics at a Glance', level: 2 },
  { id: 'choosing', text: 'Choosing the Right Method', level: 2 },
  { id: 'composed-tree', text: 'A Complete Tree', level: 2 },
  { id: 'usages', text: 'Where Jeffrey Uses It', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const quickStartSpans = [
  { depth: 0, name: 'order.checkout', kind: 'SERVER' as const, start: 0, duration: 84.2,
    event: 'jeffrey.TraceSpan', note: 'root' },
  { depth: 1, name: 'inventory.reserve', kind: 'CLIENT' as const, start: 2.1, duration: 31.7,
    event: 'jeffrey.TraceSpan' },
  { depth: 1, name: 'payment.charge', kind: 'CLIENT' as const, start: 35.4, duration: 46.5,
    event: 'jeffrey.TraceSpan' }
];

const quickStart = `import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.Tracer;

Tracer.run("order.checkout", SpanKind.SERVER, () -> {
    Tracer.run("inventory.reserve", SpanKind.CLIENT, this::reserve);
    Tracer.run("payment.charge", SpanKind.CLIENT, this::charge);
});`;

const quickStartJfr = `jfr print --events jeffrey.TraceSpan app.jfr

jeffrey.TraceSpan {
  duration = 84.2 ms
  traceId = 6872570733206835563
  spanId = 4444722480460712002
  parentSpanId = 0                     // the root: order.checkout
  name = "order.checkout"
  kind = "SERVER"
  status = "UNSET"
}
jeffrey.TraceSpan {
  duration = 31.7 ms
  traceId = 6872570733206835563        // same trace
  spanId = 1265226274086170307
  parentSpanId = 4444722480460712002   // nested under order.checkout
  name = "inventory.reserve"
  kind = "CLIENT"
  status = "UNSET"
}`;

const errorExample = `IllegalStateException thrown = assertThrows(IllegalStateException.class,
    () -> Tracer.run("payment.charge", SpanKind.CLIENT, () -> {
        throw new IllegalStateException("card declined");
    }));

// The span is still recorded:
//   name      = "payment.charge"
//   status    = ERROR
//   errorType = "java.lang.IllegalStateException"
// and the exception is rethrown unchanged — same instance, no wrapping.`;

const composedSpans = [
  { depth: 0, name: 'POST /api/internal/recordings/upload', kind: 'SERVER' as const,
    start: 0, duration: 4400, event: 'HttpServerExchangeEvent', note: 'inSpanOf' },
  { depth: 1, name: 'profile.initialize', kind: 'INTERNAL' as const,
    start: 150, duration: 4102, event: 'jeffrey.TraceSpan', note: 'call' },
  { depth: 2, name: 'profile-info.insert', kind: 'INTERNAL' as const,
    start: 155, duration: 12, event: 'jeffrey.TraceSpan', note: 'run' },
  { depth: 3, name: 'insert_profile', kind: 'CLIENT' as const,
    start: 157, duration: 8, event: 'JdbcInsertEvent', note: 'commitSpan' },
  { depth: 2, name: 'recording.parse', kind: 'INTERNAL' as const,
    start: 175, duration: 2797, event: 'jeffrey.TraceSpan', note: 'run' },
  { depth: 3, name: 'chunk.parse', kind: 'INTERNAL' as const,
    start: 200, duration: 2730, event: 'jeffrey.TraceSpan', note: 'fork' },
  { depth: 3, name: 'chunk.parse', kind: 'INTERNAL' as const,
    start: 215, duration: 2571, event: 'jeffrey.TraceSpan', note: 'fork' },
  { depth: 2, name: 'events.flush', kind: 'INTERNAL' as const,
    start: 2980, duration: 212, event: 'jeffrey.TraceSpan', note: 'run' },
  { depth: 3, name: 'insert_events', kind: 'CLIENT' as const,
    start: 2990, duration: 190, event: 'JdbcInsertEvent', note: 'commitSpan' },
  { depth: 2, name: 'profile.data-init', kind: 'INTERNAL' as const,
    start: 3200, duration: 933, event: 'jeffrey.TraceSpan', note: 'run' },
  { depth: 3, name: 'eventviewer.tree', kind: 'INTERNAL' as const,
    start: 3215, duration: 410, event: 'jeffrey.TraceSpan', note: 'fork' },
  { depth: 3, name: 'guardian.results', kind: 'INTERNAL' as const,
    start: 3215, duration: 916, event: 'jeffrey.TraceSpan', note: 'fork' },
  { depth: 3, name: 'threads.rows', kind: 'INTERNAL' as const,
    start: 3220, duration: 380, event: 'jeffrey.TraceSpan', note: 'fork' }
];
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Instrumentation Overview"
      icon="bi bi-code-square"
    />

    <div class="docs-content">
      <p>Everything in this section produces the same thing — JFR events that end up as spans of a trace — through five complementary routes. Four of them carry the trace identity in the event itself; the fifth lets the JVM record the method and leaves the attaching to analysis time. This page is the map: the model they all share, the events they emit, and which tool fits which situation. The <strong>Tracer API Reference</strong> beneath it documents every method on its own page, with use-cases, examples and the recorded output.</p>

      <h2 id="ways">Five Ways to Instrument</h2>

      <table>
        <thead>
          <tr>
            <th>Route</th>
            <th>What it looks like</th>
            <th>Where it is documented</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><strong>Hand-written spans</strong></td>
            <td><code>Tracer.run("order.checkout", …)</code> around a block of code</td>
            <td>The <router-link to="/docs/tracing/tracer-api/run">Tracer API Reference</router-link> — one page per method</td>
          </tr>
          <tr>
            <td><strong>Annotated methods</strong></td>
            <td><code>@Traced</code> on a method, woven by the Jeffrey Agent</td>
            <td><router-link to="/docs/tracing/traced-annotation">@Traced &amp; the Agent</router-link></td>
          </tr>
          <tr>
            <td><strong>JFR method tracing</strong></td>
            <td>A filter in the recording configuration — no code, no agent, no redeploy</td>
            <td><router-link to="/docs/tracing/method-tracing">JFR Method Tracing</router-link></td>
          </tr>
          <tr>
            <td><strong>Built-in framework events</strong></td>
            <td>A filter, interceptor or wrapper you register once</td>
            <td><router-link to="/docs/tracing/http-events">HTTP</router-link>, <router-link to="/docs/tracing/grpc-events">gRPC</router-link>, <router-link to="/docs/tracing/jdbc-events">JDBC</router-link>, <router-link to="/docs/tracing/mybatis-events">MyBatis</router-link> Events; on Spring, <router-link to="/docs/tracing/spring-support">Spring Support</router-link></td>
          </tr>
          <tr>
            <td><strong>Custom traced events</strong></td>
            <td>Your own event type extending <code>AbstractTracedEvent</code></td>
            <td><router-link to="/docs/tracing/custom-events">Custom Traced Events</router-link>; instants in <router-link to="/docs/tracing/notifications-exceptions">Notifications &amp; Exceptions</router-link></td>
          </tr>
        </tbody>
      </table>

      <p>They compose freely because everything feeds the same span shape — an HTTP exchange, a hand-written span and a JDBC statement land in one tree without ever referencing each other in code.</p>

      <h2 id="model">The Tracer Model</h2>

      <p>The <code>Tracer</code> class (<code>cafe.jeffrey.jfr.events.trace.Tracer</code>) is a small set of static methods that record nested spans into the JFR recording. The span currently in progress is published through a <code>ScopedValue</code>: a nested call discovers its parent by itself — nothing is threaded through the call chain, and the binding is bounded by the lambda, so it cannot outlive the span and never needs clearing:</p>

      <DocsCodeBlock :code="quickStart" language="java" />

      <p>Those three calls produce this tree:</p>

      <DocsSpanTree
        trace="5f3a90c2…"
        :spans="quickStartSpans"
        caption="A container bar is drawn hollow: its time is mostly its children's, not its own."
      />

      <p>and this is what the recording itself holds — <code>jeffrey.TraceSpan</code> events whose ids encode the tree:</p>

      <DocsCodeBlock :code="quickStartJfr" language="text" />

      <DocsCallout type="info">
        <strong>Cost when nothing is recording.</strong> When the <code>jeffrey.TraceSpan</code> event type is disabled — no JFR recording, or a configuration that leaves it off — the body runs directly: no binding is established, no event is committed, and the event instance itself is escape-analysable. Instrumentation is safe to leave in production code permanently.
      </DocsCallout>

      <DocsCallout type="warning">
        <strong>Java 25 or newer.</strong> The API is built on <code>ScopedValue</code> (JEP&nbsp;506) and <code>jdk.jfr.Contextual</code>, both finalized in Java&nbsp;25.
      </DocsCallout>

      <p>The data model behind every method — <code>SpanContext</code>, <code>SpanKind</code>, <code>SpanStatus</code>, and the id semantics — is on the <router-link to="/docs/tracing/concepts">Core Concepts</router-link> page.</p>

      <h2 id="events">The JFR Events Behind It</h2>

      <table>
        <thead>
          <tr>
            <th>Event</th>
            <th>Role</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.TraceSpan</code></td>
            <td>A hand-written span — one named interval of work. Emitted by <router-link to="/docs/tracing/tracer-api/run">run</router-link>, <router-link to="/docs/tracing/tracer-api/call">call</router-link>, <router-link to="/docs/tracing/tracer-api/continue-in">continueIn</router-link> (and therefore <router-link to="/docs/tracing/tracer-api/fork">fork</router-link>/<router-link to="/docs/tracing/tracer-api/fork-callable">forkCallable</router-link>), and by <code>@Traced</code>. Declares no fields of its own; everything lives on <code>AbstractTracedEvent</code>.</td>
          </tr>
          <tr>
            <td><code>jeffrey.TraceScope</code></td>
            <td>One stretch of time during which a span was active on one thread. Emitted only by <router-link to="/docs/tracing/tracer-api/reenter">reenter</router-link> (and by <router-link to="/docs/tracing/tracer-api/propagating">propagating</router-link>'s task activations) — a span event answers <em>what happened</em>, a scope answers <em>where and when it was running</em>, and the two come apart as soon as a span crosses threads.</td>
          </tr>
          <tr>
            <td><code>jdk.MethodTrace</code></td>
            <td>Recorded by the JVM itself (JEP&nbsp;520) and carrying no trace ids at all. The derivation promotes it into an <code>INTERNAL</code> span under whatever was open on that thread — see <router-link to="/docs/tracing/method-tracing">JFR Method Tracing</router-link>.</td>
          </tr>
          <tr>
            <td>Any <code>AbstractTracedEvent</code></td>
            <td>An instrumented event — HTTP, gRPC, JDBC, or your own — <em>is</em> a span. <router-link to="/docs/tracing/tracer-api/in-span-of">inSpanOf</router-link>, <router-link to="/docs/tracing/tracer-api/open-span-of">openSpanOf</router-link> and <router-link to="/docs/tracing/tracer-api/stamp">stamp</router-link> operate on these directly, and no <code>jeffrey.TraceSpan</code> is emitted for them: the event carries the ids itself.</td>
          </tr>
        </tbody>
      </table>

      <h2 id="errors">Error Handling</h2>

      <p>An exception escaping any span body marks the span <code>ERROR</code>, records the exception's class name in <code>errorType</code>, and is <strong>rethrown unchanged</strong> — same instance, no wrapping:</p>

      <DocsCodeBlock :code="errorExample" language="java" />

      <ul>
        <li><strong>Catch inside the body</strong> if a handled failure should not mark the operation failed — a span only turns red for exceptions that <em>escape</em> it.</li>
        <li>Only the exception's <strong>class name</strong> is recorded on the span — never the message or the stack trace. The full throw (message, stack, instant) is correlated back into the trace at analysis time from <code>jdk.JavaExceptionThrow</code> — see <router-link to="/docs/tracing/notifications-exceptions">Notifications &amp; Exceptions</router-link>.</li>
        <li>On instrumented events, state failures with <code>event.failed(throwable)</code> and rethrow — never assign <code>status</code> directly.</li>
      </ul>

      <h2 id="semantics">Semantics at a Glance</h2>

      <table>
        <thead>
          <tr>
            <th>Method</th>
            <th>Emits</th>
            <th>Binds a context</th>
            <th>When the event type is disabled</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><router-link to="/docs/tracing/tracer-api/run">run</router-link> / <router-link to="/docs/tracing/tracer-api/call">call</router-link></td>
            <td><code>jeffrey.TraceSpan</code></td>
            <td>Yes — child of the current span, or a fresh root</td>
            <td>Body runs directly; no binding, no event</td>
          </tr>
          <tr>
            <td><router-link to="/docs/tracing/tracer-api/current">current</router-link></td>
            <td>—</td>
            <td>—</td>
            <td>Empty when nothing is bound</td>
          </tr>
          <tr>
            <td><router-link to="/docs/tracing/tracer-api/in-span-of">inSpanOf</router-link></td>
            <td>Nothing (the event is the span)</td>
            <td>Yes — always</td>
            <td>Still binds: recording is the event's decision</td>
          </tr>
          <tr>
            <td><router-link to="/docs/tracing/tracer-api/stamp">stamp</router-link></td>
            <td>Nothing (fills the event's ids)</td>
            <td>No</td>
            <td>Stamps whenever a span is bound; no-op otherwise</td>
          </tr>
          <tr>
            <td><router-link to="/docs/tracing/tracer-api/open-span-of">openSpanOf</router-link></td>
            <td>Nothing (fills the event's ids)</td>
            <td>No — deliberately</td>
            <td>Still stamps</td>
          </tr>
          <tr>
            <td><router-link to="/docs/tracing/tracer-api/reenter">reenter</router-link></td>
            <td>One <code>jeffrey.TraceScope</code> per re-entry</td>
            <td>Yes — the same span, not a child</td>
            <td>Still binds; no scope emitted</td>
          </tr>
          <tr>
            <td><router-link to="/docs/tracing/tracer-api/continue-in">continueIn</router-link></td>
            <td><code>jeffrey.TraceSpan</code></td>
            <td>Yes — child of the given parent</td>
            <td>Still binds; no event. The handed-over context is the only link, so dropping it would orphan every stamp underneath</td>
          </tr>
          <tr>
            <td><router-link to="/docs/tracing/tracer-api/fork">fork</router-link> / <router-link to="/docs/tracing/tracer-api/fork-callable">forkCallable</router-link></td>
            <td><code>jeffrey.TraceSpan</code> (via <code>continueIn</code>, when the task runs)</td>
            <td>Captures at wrap time; binds inside the task</td>
            <td>Same as <code>continueIn</code>: the task still binds, no event</td>
          </tr>
          <tr>
            <td><router-link to="/docs/tracing/tracer-api/propagating">propagating</router-link></td>
            <td>One <code>jeffrey.TraceScope</code> per task activation (via <code>reenter</code>)</td>
            <td>Captures per submission; re-binds the <em>same</em> span inside the task</td>
            <td>Still binds; no scope emitted. A task submitted outside any span passes through untouched</td>
          </tr>
        </tbody>
      </table>

      <h2 id="choosing">Choosing the Right Method</h2>

      <table>
        <thead>
          <tr>
            <th>Situation</th>
            <th>Use</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>A block of in-process work worth timing</td>
            <td><router-link to="/docs/tracing/tracer-api/run">run</router-link> (side-effecting) / <router-link to="/docs/tracing/tracer-api/call">call</router-link> (value-returning)</td>
          </tr>
          <tr>
            <td>An event of your own already describes the interval, and work nests inside it</td>
            <td><router-link to="/docs/tracing/tracer-api/in-span-of">inSpanOf</router-link></td>
          </tr>
          <tr>
            <td>An event describes a self-contained action with nothing nesting inside — a statement, a message</td>
            <td><code>commitSpan()</code> on the event (fold the stamp into the commit); <router-link to="/docs/tracing/tracer-api/stamp">stamp</router-link> only for a deferred commit</td>
          </tr>
          <tr>
            <td>Work handed to a plain executor — a separate operation on the receiving thread</td>
            <td><router-link to="/docs/tracing/tracer-api/fork">fork</router-link> / <router-link to="/docs/tracing/tracer-api/fork-callable">forkCallable</router-link> around the task, on the submitting thread</td>
          </tr>
          <tr>
            <td>A whole pool serves traced requests; tasks are the same operation continuing elsewhere</td>
            <td><router-link to="/docs/tracing/tracer-api/propagating">propagating</router-link> around the pool, once</td>
          </tr>
          <tr>
            <td>The parent context comes from somewhere other than the submitting site — stored, or handed over by a protocol</td>
            <td><router-link to="/docs/tracing/tracer-api/continue-in">continueIn</router-link> with that context</td>
          </tr>
          <tr>
            <td>One operation arriving in pieces — a protocol's callbacks</td>
            <td><router-link to="/docs/tracing/tracer-api/open-span-of">openSpanOf</router-link> once + <router-link to="/docs/tracing/tracer-api/reenter">reenter</router-link> per callback</td>
          </tr>
          <tr>
            <td>A running application is slow inside a method nobody instrumented, and you cannot change it</td>
            <td><router-link to="/docs/tracing/method-tracing">JFR method tracing</router-link> — name the method in the recording configuration</td>
          </tr>
          <tr>
            <td>Something outside the tree needs the current span's identity — log correlation, a stored hand-off</td>
            <td><router-link to="/docs/tracing/tracer-api/current">current</router-link></td>
          </tr>
        </tbody>
      </table>

      <h2 id="composed-tree">A Complete Tree</h2>

      <p>Jeffrey's own recording-upload flow composes almost every method. An HTTP request roots the trace through <code>inSpanOf</code>, the pipeline stages are <code>call</code>/<code>run</code> spans, chunk parsing and profile-data branches are handed to pools with <code>fork</code>, and every SQL statement along the way is a leaf stamped by <code>commitSpan()</code>:</p>

      <DocsSpanTree
        trace="a3f9c1d4…"
        :spans="composedSpans"
        caption="The three forked branches overlap because they ran on different pool threads at the same time."
      />

      <h2 id="usages">Where Jeffrey Uses It</h2>

      <p>Every method is exercised by Jeffrey's own codebase; these are the reference call sites to read next to the method pages:</p>

      <table>
        <thead>
          <tr>
            <th>Call site</th>
            <th>Methods</th>
            <th>What it traces</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>HttpExchangeFilter</code></td>
            <td><code>inSpanOf</code></td>
            <td>Each HTTP request; the <code>HttpServerExchangeEvent</code> is the trace root</td>
          </tr>
          <tr>
            <td><code>JfrGrpcServerInterceptor</code></td>
            <td><code>openSpanOf</code> + <code>reenter</code></td>
            <td>Each inbound gRPC call; every listener callback re-enters the span</td>
          </tr>
          <tr>
            <td><code>DatabaseClient</code></td>
            <td><code>commitSpan</code> (leaf stamping)</td>
            <td>Every SQL statement as a leaf span (<code>JdbcQueryEvent</code>, <code>JdbcInsertEvent</code>, …)</td>
          </tr>
          <tr>
            <td><code>ProfileInitializerImpl</code></td>
            <td><code>call</code> + <code>run</code></td>
            <td>Profile-creation pipeline: parse, flush, trace derivation, data init</td>
          </tr>
          <tr>
            <td><code>ParallelRecordingFileIterator</code></td>
            <td><code>fork</code></td>
            <td>Per-chunk JFR parsing forked to a worker pool</td>
          </tr>
          <tr>
            <td><code>ProfileDataInitializerImpl</code></td>
            <td><code>fork</code></td>
            <td>Parallel initialization branches: event viewer, guardian, thread viewer</td>
          </tr>
          <tr>
            <td><code>DbBasedFlamegraphGenerator</code></td>
            <td><code>fork</code> + <code>call</code></td>
            <td>Flamegraph and timeseries generated as parallel branches of one request</td>
          </tr>
          <tr>
            <td><code>PipelineRunRegistry</code></td>
            <td><code>inSpanOf</code></td>
            <td>Each pipeline run as its own trace root, with a hand-built <code>TraceSpanEvent</code> recording the observed outcome (<code>OK</code>/<code>ERROR</code>)</td>
          </tr>
          <tr>
            <td><code>ReflectiveToolset</code></td>
            <td><code>inSpanOf</code></td>
            <td>Each AI MCP tool invocation, separating model time from query time</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="tip">
        A background job forked onto its own thread — a pipeline run — is deliberately its <em>own</em> trace root rather than a child of the request that triggered it: its lifetime is unrelated to the request. See <router-link to="/docs/tracing/analysis">Analyzing Traces → Limits</router-link> for this and the other boundaries of the model.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
