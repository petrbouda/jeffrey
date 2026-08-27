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
  { id: 'model', text: 'Spans, Traces, Instants', level: 2 },
  { id: 'span-shape', text: 'The Span Shape: AbstractTracedEvent', level: 2 },
  { id: 'span-context', text: 'SpanContext and the ScopedValue', level: 2 },
  { id: 'kind-status', text: 'SpanKind and SpanStatus', level: 2 },
  { id: 'discovery', text: 'Structural Span Discovery', level: 2 },
  { id: 'five-rules', text: 'The Five Rules', level: 2 },
  { id: 'naming', text: 'Names and Cardinality', level: 2 },
  { id: 'attributes', text: 'Attributes', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const spanContextShape = `public record SpanContext(long traceId, long spanId, long parentSpanId) {
    public static SpanContext root() { … }        // fresh trace, no parent
    public SpanContext child() { … }              // same trace, new span id, parented here
    public boolean isRoot() { return parentSpanId == 0; }

    // Forms taking a RandomGenerator explicitly exist for tests that need deterministic ids.
}`;

const attributesExample = `event.attributes = EventAttributes.create()
        .put("cache", "miss")
        .put("retries", 2)
        .json();                       // {"cache":"miss","retries":2}`;

const namingExamples = `// Good: one name per operation — stable, low-cardinality
"GET /api/users/{id}"          // the matched URI template
"UserMapper.selectById"        // the MyBatis statement id
"order.checkout"               // a domain operation

// Bad: one name per INSTANCE of the operation
"GET /api/users/42"            // raw path — one "operation" per user
"order.checkout.a3f9c1"        // an id in the name
"select * from users where id = 42"   // literals in a statement name`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Core Concepts"
      icon="bi bi-box"
    />

    <div class="docs-content">
      <p>Everything in Jeffrey Tracing follows from a small data model and a handful of rules. Read this page first; every instrumentation page assumes it.</p>

      <h2 id="model">Spans, Traces, Instants</h2>

      <ul>
        <li>A <strong>span</strong> is one timed operation: a name, a kind, a start, a duration, a thread, an outcome, and a parent. Concretely, a span is any JFR event that carries trace identity.</li>
        <li>A <strong>trace</strong> is the tree of spans reachable from one root — typically an inbound HTTP request or gRPC call. The tree is rebuilt entirely from the three ids <code>(traceId, spanId, parentSpanId)</code>.</li>
        <li>An <strong>instant</strong> is a moment, not an interval: no name, no duration, no place in the tree — only the span that was open when it fired. <code>jeffrey.Notification</code> is the shipped instant; see <router-link to="/docs/tracing/notifications-exceptions">Notifications &amp; Exceptions</router-link>.</li>
      </ul>

      <p>A span whose parent is missing from the recording — fell under a threshold, never instrumented — is <strong>promoted to a root</strong> at analysis time. Nothing is lost, but the nesting is.</p>

      <h2 id="span-shape">The Span Shape: <code>AbstractTracedEvent</code></h2>

      <p>All HTTP, gRPC and JDBC statement events — and any custom event you write — extend <code>cafe.jeffrey.jfr.events.trace.AbstractTracedEvent</code>, which declares:</p>

      <table>
        <thead>
          <tr>
            <th>Field</th>
            <th>Type</th>
            <th>Meaning</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>traceId</code></td>
            <td><code>long</code></td>
            <td>Identifies the whole trace; shared by every span in it</td>
          </tr>
          <tr>
            <td><code>spanId</code></td>
            <td><code>long</code></td>
            <td>Identifies this span; unique within the trace</td>
          </tr>
          <tr>
            <td><code>parentSpanId</code></td>
            <td><code>long</code></td>
            <td>Enclosing span's id; <code>0</code> = this span is a root</td>
          </tr>
          <tr>
            <td><code>name</code></td>
            <td><code>String</code></td>
            <td>Operation name — <strong>stable, low-cardinality</strong></td>
          </tr>
          <tr>
            <td><code>kind</code></td>
            <td><code>String</code></td>
            <td><code>INTERNAL</code> | <code>SERVER</code> | <code>CLIENT</code></td>
          </tr>
          <tr>
            <td><code>status</code></td>
            <td><code>String</code></td>
            <td><code>UNSET</code> | <code>OK</code> | <code>ERROR</code></td>
          </tr>
          <tr>
            <td><code>errorType</code></td>
            <td><code>String</code></td>
            <td>Exception class name when status is <code>ERROR</code></td>
          </tr>
          <tr>
            <td><code>attributes</code></td>
            <td><code>String</code></td>
            <td>Operation-specific detail as a JSON object</td>
          </tr>
        </tbody>
      </table>

      <p>The base class also carries the two verbs every emitter uses: <code>commitSpan()</code> — the single commit path, which stamps an event that does not yet carry identity as a child of the span in progress, lets the event derive its own <code>name</code>/<code>status</code> in <code>describeSpan()</code>, and commits — and <code>failed(Throwable)</code>, the one way a failure is stated (sets <code>status = ERROR</code> and <code>errorType</code> to the exception's class name; never assign <code>status</code> directly).</p>

      <DocsCallout type="warning">
        <strong><code>commitSpan()</code>, never <code>commit()</code>.</strong> The value <code>0</code> means "absent": an event committed with a bare <code>commit()</code> skips the stamp, carries all-zero ids, and still appears in the dashboards — but is silently dropped from every trace. A bare <code>commit()</code> is the deliberate opt-out, not the default.
      </DocsCallout>

      <p>Ids are <code>long</code>s rather than strings on purpose: JFR varint-encodes integrals, while every distinct string enters the per-chunk constant pool — and a zero is the cheapest varint there is, so the three id fields cost nearly nothing on an untraced code path.</p>

      <h2 id="span-context">SpanContext and the ScopedValue</h2>

      <p>A span's position in its trace is fully described by a <code>SpanContext</code>, the value the API publishes through a <code>ScopedValue</code>:</p>

      <DocsCodeBlock :code="spanContextShape" language="java" />

      <p>The record is immutable — a nested span never mutates its parent's context, it derives a child — which is what makes it safe to publish through a <code>ScopedValue</code> and to carry across threads. The binding is bounded by a lambda (<code>Tracer.run</code>, <code>call</code>, <code>inSpanOf</code>, …), so it cannot outlive the span and never needs clearing.</p>

      <DocsCallout type="info">
        <strong><code>ScopedValue</code> propagates only through structured concurrency.</strong> Work submitted to a plain executor does not inherit the current span — it must be handed over explicitly with <code>Tracer.fork</code>, <code>Tracer.continueIn</code> or a <code>Tracer.propagating(executor)</code> wrapper. See the <router-link to="/docs/tracing/tracer-api">Tracer API</router-link> for all three.
      </DocsCallout>

      <h2 id="kind-status">SpanKind and SpanStatus</h2>

      <table>
        <thead>
          <tr>
            <th>Kind</th>
            <th>Meaning</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>SERVER</code></td>
            <td>Work performed in response to an inbound call. Typically the trace root.</td>
          </tr>
          <tr>
            <td><code>CLIENT</code></td>
            <td>Waiting on something outside the process — a database, another service. 110&nbsp;ms of a 120&nbsp;ms request spent in <code>CLIENT</code> spans answers most latency questions on its own.</td>
          </tr>
          <tr>
            <td><code>INTERNAL</code></td>
            <td>In-process work. The default.</td>
          </tr>
        </tbody>
      </table>

      <p>Kind is how Jeffrey distinguishes "my time" from "their time". The set is deliberately smaller than OpenTelemetry's — there is no <code>PRODUCER</code>/<code>CONSUMER</code>, because a single-JVM recording has no counterpart span to pair with.</p>

      <p><code>SpanStatus</code> says how the operation finished: <code>UNSET</code> (no opinion expressed — the default, and what a successful <code>Tracer.run</code> records), <code>OK</code> (a success the code explicitly observed), <code>ERROR</code> (failed, with the exception class in <code>errorType</code>). Events that derive a verdict from their own fields — HTTP status ≥ 400, gRPC status ≠ <code>OK</code> — only ever <strong>escalate</strong>; they never paint over a failure recorded with <code>failed()</code>.</p>

      <h2 id="discovery">Structural Span Discovery</h2>

      <p>Jeffrey does not keep a list of span event types. When a profile is initialized, it reads the recording's own metadata and treats <strong>every event type that declares a <code>spanId</code> field</strong> as a span source — Jeffrey's shipped events and your own custom types alike, with zero configuration on Jeffrey's side.</p>

      <p>Two deliberate consequences of that contract:</p>

      <ul>
        <li>An instant names its span field <code>enclosingSpanId</code>, never <code>spanId</code> — otherwise every notification would be built into a nameless, durationless span.</li>
        <li><code>jeffrey.TraceScope</code> names its field <code>scopedSpanId</code> for the same reason: a scope records <em>where a span ran</em>, and is deliberately not a span itself.</li>
      </ul>

      <p>Naming is discovered the same way: annotate a custom event class with <code>@Span("{field} template")</code> and the template travels inside every recording's metadata, applied by Jeffrey without knowing the type — see <router-link to="/docs/tracing/custom-events">Custom Traced Events</router-link>.</p>

      <h2 id="five-rules">The Five Rules</h2>

      <p>Everything the instrumentation pages tell you is a special case of these five rules:</p>

      <ol>
        <li><strong>One root per request, opened with <code>Tracer.inSpanOf</code>.</strong> The inbound-request event <em>is</em> the root span; no separate span event is emitted for the same interval. Do not additionally call <code>Tracer.run</code> around the request — that would record the interval twice.</li>
        <li><strong>Everything commits through <code>commitSpan()</code>.</strong> It stamps an event that does not yet carry trace identity as a child of the span in progress, keeps one that does exactly as it is, and leaves the ids at zero when no span is bound (recorded, just untraced). For a leaf committed in its own <code>finally</code>, <code>TracedEvents.emit</code> writes the whole lifecycle in one call.</li>
        <li><strong>Failures are stated with <code>failed(throwable)</code></strong> — on any traced event — then rethrown. Derived verdicts (HTTP ≥ 400, gRPC ≠ OK) only ever escalate.</li>
        <li><strong>Names must be stable and low-cardinality</strong> — see below.</li>
        <li><strong>A trace does not cross a plain executor by itself.</strong> Wrap tasks with <code>Tracer.fork(...)</code>, carry <code>Tracer.current()</code> and re-establish it with <code>Tracer.continueIn(parent, ...)</code>, or wrap the whole pool with <code>Tracer.propagating(executor)</code>. For one operation arriving in callback pieces on foreign threads, open with <code>Tracer.openSpanOf(event)</code> and wrap each callback in <code>Tracer.reenter(ctx, ...)</code>.</li>
      </ol>

      <h2 id="naming">Names and Cardinality</h2>

      <p>Every distinct string in a recording enters the JFR per-chunk constant pool. Name the <em>operation</em>, not the instance of it — the identity of an individual request lives in the trace id, which is already there:</p>

      <DocsCodeBlock :code="namingExamples" language="java" />

      <p>The same rule extends to every string field on a traced event: <code>uri</code> must be the matched template, a statement's name must be its label or mapper id, and a notification's <code>message</code> must be constant per kind (see <router-link to="/docs/tracing/notifications-exceptions">Notifications</router-link> for why). High-cardinality names are the fastest way there is to make a recording enormous — and they shatter <router-link to="/docs/tracing/analysis">Traces by Operation</router-link> into one row per instance.</p>

      <h2 id="attributes">Attributes</h2>

      <p>Per-request values — an entity id, a retry count, a cache verdict — belong in <code>attributes</code>: an open JSON-object string carried by every span and every instant, built with <code>EventAttributes</code> rather than concatenated by hand (it escapes quotes, backslashes and control characters), and filled only inside the <code>shouldCommit()</code> block so an event under threshold pays nothing:</p>

      <DocsCodeBlock :code="attributesExample" language="java" />

      <p>Attributes are what Jeffrey's <router-link to="/docs/tracing/analysis">attribute search</router-link> indexes: they are flattened into the profile database one row per distinct value, so an id put in an attribute is queryable — the same id spliced into a span name or a message is not.</p>

      <DocsCallout type="warning">
        The recording and the profile database contain attribute values <strong>verbatim</strong> — scrub anything sensitive before it goes in. A recording is a file that gets uploaded, shared and kept.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
