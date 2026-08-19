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
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'concepts', text: 'Concepts', level: 2 },
  { id: 'quick-start', text: 'Quick Start', level: 2 },
  { id: 'api', text: 'API Reference', level: 2 },
  { id: 'scenarios', text: 'Worked Scenarios', level: 2 },
  { id: 'reading-the-tree', text: 'Reading the Tree', level: 2 },
  { id: 'traced-events', text: 'Events That Are Already Spans', level: 2 },
  { id: 'naming', text: 'Naming Spans', level: 2 },
  { id: 'volume', text: 'Controlling Volume', level: 2 },
  { id: 'limits', text: 'Limits & Gotchas', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const mavenDependency = `<dependency>
    <groupId>cafe.jeffrey-analyst</groupId>
    <artifactId>jeffrey-events</artifactId>
    <version>0.12.0</version>
</dependency>`;

const quickStartExample = `import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.Tracer;

Tracer.run("order.checkout", SpanKind.SERVER, () -> {
    Tracer.run("inventory.reserve", SpanKind.CLIENT, this::reserve);
    Tracer.run("payment.charge", SpanKind.CLIENT, this::charge);
});`;

const quickStartTree = `Trace 4f2a9c81b30e7d55                             412.0 ms   3 spans

order.checkout                  SERVER    412.0 ms   self    1.4 ms
├─ inventory.reserve            CLIENT     88.3 ms   self   88.3 ms
└─ payment.charge               CLIENT    322.3 ms   self  322.3 ms`;

const contextExample = `public record SpanContext(long traceId, long spanId, long parentSpanId) { }

// Inside any span, the context in progress is available without threading it through:
Optional<SpanContext> current = Tracer.current();`;

const callExample = `// Void form, kind defaults to INTERNAL.
Tracer.run("cache.warm", () -> warmCache());

// Explicit kind.
Tracer.run("inventory.reserve", SpanKind.CLIENT, this::reserve);

// Value-returning form; the body may throw a checked exception, which is
// declared through the type variable rather than wrapped.
Order order = Tracer.call("order.load", SpanKind.INTERNAL, () -> repository.load(id));`;

const inSpanExample = `// Opens a span so the work underneath hangs together. Emits nothing itself.
Tracer.inSpan(() -> {
    runFirstStatement();     // each of these emits its own event
    runSecondStatement();
    return null;
});`;

const inSpanTree = `# inSpan opened a span, but nothing emitted an event for it, so it is absent
# from the recording. Its children point at a parent that is not there and are
# promoted to roots:

SELECT … FROM workspaces        CLIENT     8.2 ms   ← promoted to root
SELECT … FROM projects          CLIENT    24.7 ms   ← promoted to root`;

const inSpanOfExample = `HttpServerExchangeEvent event = new HttpServerExchangeEvent();
if (!event.isEnabled()) {
    chain.doFilter(request, response);
    return;
}

event.begin();
try {
    // The exchange event *is* the root span. No jeffrey.TraceSpan is emitted,
    // because that would record the same interval twice.
    Tracer.inSpanOf(event, () -> {
        chain.doFilter(request, response);
        return null;
    });
} finally {
    event.end();
    if (event.shouldCommit()) {
        event.uri = template;
        event.statusCode = response.getStatus();
        event.commitSpan();
    }
}`;

const stampExample = `JdbcQueryEvent event = new JdbcQueryEvent("listSpans", "profile");
if (event.isEnabled()) {
    event.begin();
    // ... run the statement ...
    event.end();
    event.sql = sql;
    event.rows = rows;
    // Gives the statement a span of its own nested under the span in progress,
    // derives name/kind/status from the event's own fields, then commits.
    event.stampAndCommit();
}`;

const continueInExample = `// ScopedValue does not propagate through a plain executor, so the parent
// context is captured before the fork and re-established inside the task.
SpanContext parent = Tracer.current().orElse(null);

executor.submit(() -> Tracer.continueIn(parent, "chunk.parse", SpanKind.INTERNAL, () -> {
    parseChunk(file);
    return null;
}));`;

const continueInTree = `Trace 8be15c07a4d9f1c2                            2797.0 ms   4 spans

recording.parse     tomcat-handler-53   INTERNAL  2797.0 ms   self 2797.0 ms
├─ chunk.parse      bulk-parallel-1     INTERNAL  2730.0 ms
├─ chunk.parse      bulk-parallel-2     INTERNAL  2571.0 ms
└─ chunk.parse      bulk-parallel-3     INTERNAL  2402.0 ms`;

const reenterExample = `// A gRPC call arrives in pieces, on threads the interceptor does not control,
// so the span is opened without binding and resumed around every callback.
SpanContext span = Tracer.openSpanOf(event);

return new SimpleForwardingServerCallListener<>(listener) {
    @Override
    public void onHalfClose() {          // where a unary handler actually runs
        Tracer.reenter(span, () -> {
            super.onHalfClose();
            return null;
        });
    }
};`;

const reenterTree = `Trace 7f3ae2914c60b8dd                             42.1 ms   5 spans

WorkspaceService/ListWorkspaces   SERVER    42.1 ms   self  0.7 ms
├─ workspaces.list                INTERNAL  39.4 ms   self  6.5 ms
│  ├─ SELECT … FROM workspaces    CLIENT     8.2 ms
│  └─ SELECT … FROM projects      CLIENT    24.7 ms
└─ workspaces.enrich              INTERNAL   2.0 ms`;

const scopeTree = `# The scopes are not spans and never appear in the waterfall. They record
# where the re-entered span was actually running:

jeffrey.TraceScope  scopedSpanId=…b8dd  grpc-worker-ELG-3-2       0.3 ms   (onMessage)
jeffrey.TraceScope  scopedSpanId=…b8dd  grpc-default-executor-0  41.4 ms   (onHalfClose)
jeffrey.TraceScope  scopedSpanId=…b8dd  grpc-worker-ELG-3-2       0.2 ms   (close)`;

const errorExample = `Tracer.run("order.checkout", SpanKind.SERVER, () -> {
    Tracer.run("payment.charge", SpanKind.CLIENT, () -> {
        throw new IllegalStateException("card declined");
    });
});`;

const errorTree = `order.checkout       SERVER     12.9 ms   ERROR   java.lang.IllegalStateException
└─ payment.charge    CLIENT     12.7 ms   ERROR   java.lang.IllegalStateException`;

const idsTree = `traceId           spanId            parentSpanId       name
4f2a9c81b30e7d55  a1b2c3d4e5f60718  0000000000000000   order.checkout
4f2a9c81b30e7d55  b2c3d4e5f6071829  a1b2c3d4e5f60718   inventory.reserve
4f2a9c81b30e7d55  c3d4e5f607182a3b  a1b2c3d4e5f60718   payment.charge`;

const volumeExample = `# Drop spans shorter than 1 ms (their children are orphaned and promoted to roots)
-XX:StartFlightRecording:...,cafe.jeffrey.jfr.events.trace.TraceSpanEvent#threshold=1ms

# Keep re-entry nesting but stop recording where a span ran
-XX:StartFlightRecording:...,cafe.jeffrey.jfr.events.trace.TraceScopeEvent#enabled=false`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Tracer API"
      icon="bi bi-code-slash"
    />

    <div class="docs-content">
      <p>How to instrument an application with <code>Tracer</code>, the tracing API in <code>jeffrey-events</code>. This page is the reference for writing the code; the <router-link to="/docs/microscope/profiles/traces">Traces &amp; Spans</router-link> page documents the views you read afterwards.</p>

      <h2 id="overview">Overview</h2>

      <p><code>Tracer</code> records nested spans as ordinary JFR events. That is the whole design: because a span is an event in the same recording as the CPU samples, allocations and lock events, Jeffrey can show you the flamegraph of what the JVM did <em>inside</em> a span, which a tracer writing to a separate backend cannot.</p>

      <DocsCodeBlock :code="mavenDependency" language="xml" />

      <DocsCallout type="warning">
        <strong>Java 25 or newer.</strong> The API is built on <code>ScopedValue</code> (JEP&nbsp;506) and <code>jdk.jfr.Contextual</code>, both finalized in Java&nbsp;25. Applications on Java 17–24 can use the HTTP, gRPC and JDBC events from an earlier release, but not <code>Tracer</code>. <code>openSpanOf</code>, <code>reenter</code> and <code>jeffrey.TraceScope</code> arrived after 0.12.0 and need the release that follows it.
      </DocsCallout>

      <p><strong>Safe to leave in production.</strong> When <code>jeffrey.TraceSpan</code> is disabled — no recording running, or a configuration that leaves the event off — <code>run</code> and <code>call</code> execute the body directly with no binding established and nothing committed. The only residual cost is an event instance the JIT can usually scalar-replace.</p>

      <h2 id="concepts">Concepts</h2>

      <p>A <strong>span</strong> is one timed operation: a name, a start, a duration, a thread, and a parent. A <strong>trace</strong> is the tree of spans reachable from one root. A span with no parent is a root, and a trace is named after it.</p>

      <p>Every span carries a <code>SpanContext</code> — the three ids that place it in its trace:</p>

      <DocsCodeBlock :code="contextExample" language="java" />

      <p><code>0</code> means "absent", so <code>parentSpanId == 0</code> is how a root is encoded. The ids are 64-bit <code>long</code>s rather than strings because JFR varint-encodes integral fields while every distinct string enters the per-chunk constant pool — trace and span ids are the highest-cardinality values an event can carry.</p>

      <p>The span in progress is published through a <code>ScopedValue</code>. Nesting therefore needs nothing threaded through the call chain: a nested <code>run</code> or <code>call</code> discovers its parent by itself, and the binding is bounded by the lambda, so it cannot outlive the span and never needs clearing.</p>

      <p><strong><code>SpanKind</code></strong> says what role the operation plays — in practice, whether the time was the process's own work or time spent waiting on something else. Jeffrey uses OpenTelemetry's names with a deliberately smaller set:</p>

      <ul>
        <li><code>INTERNAL</code> — work the process performs for itself. The default.</li>
        <li><code>SERVER</code> — handling an inbound request, e.g. an HTTP or gRPC server exchange.</li>
        <li><code>CLIENT</code> — issuing an outbound request and waiting, e.g. an HTTP call or a SQL query.</li>
      </ul>

      <p><code>PRODUCER</code> and <code>CONSUMER</code> are omitted: their purpose is pairing a span in one process with its counterpart in another, and a trace assembled from a single JVM recording has no counterpart to pair with.</p>

      <p><strong><code>SpanStatus</code></strong> is <code>UNSET</code>, <code>OK</code> or <code>ERROR</code>. <code>UNSET</code> is the default and is deliberate — an operation that completed without the instrumentation expressing an opinion is not the same as one explicitly declared successful.</p>

      <h2 id="quick-start">Quick Start</h2>

      <DocsCodeBlock :code="quickStartExample" language="java" />

      <p>Three spans, one trace, no context passed anywhere:</p>

      <DocsCodeBlock :code="quickStartTree" language="plaintext" />

      <p>The root's <strong>self time</strong> is its duration minus what its children accounted for — 412.0 − (88.3 + 322.3) = 1.4&nbsp;ms — which is the number that answers "was the time in this operation, or in something it called?"</p>

      <h2 id="api">API Reference</h2>

      <h3><code>current()</code></h3>

      <p><code>Optional&lt;SpanContext&gt; current()</code> — the span in progress on this thread, or empty when there is none. Used to capture a context before crossing a thread boundary.</p>

      <h3><code>run(...)</code> and <code>call(...)</code></h3>

      <p>The everyday form. Opens a span as a child of whatever is bound (a fresh root when nothing is), emits a <code>jeffrey.TraceSpan</code> event, and binds it for the body. <code>run</code> takes a <code>Runnable</code>, <code>call</code> returns a value and propagates a checked exception through its type variable.</p>

      <DocsCodeBlock :code="callExample" language="java" />

      <p>An exception escaping the body marks the span <code>ERROR</code>, records the exception's class name in <code>errorType</code>, and is <strong>rethrown unchanged</strong>.</p>

      <h3><code>inSpan(body)</code></h3>

      <p>Opens a span and binds it, but emits nothing and attaches to no event — a scope that exists only so the work underneath it hangs together.</p>

      <DocsCodeBlock :code="inSpanExample" language="java" />

      <DocsCallout type="warning">
        <strong><code>inSpan</code> is invisible on its own.</strong> Because it emits no event, the span it opens is not in the recording. If nothing underneath emits a span either, nothing appears at all — and children that <em>do</em> emit point at a parent the reader never sees, so they are promoted to roots:
      </DocsCallout>

      <DocsCodeBlock :code="inSpanTree" language="plaintext" />

      <p>Reach for <code>inSpanOf</code> when you have an event that should <em>be</em> the span, and for <code>run</code> when you want the span recorded in its own right.</p>

      <h3><code>inSpanOf(event, body)</code></h3>

      <p>Opens a span and makes <code>event</code> that span. For instrumentation whose own event already describes the interval — an HTTP exchange, a gRPC call — where emitting a separate <code>jeffrey.TraceSpan</code> would record the same interval twice.</p>

      <DocsCodeBlock :code="inSpanOfExample" language="java" />

      <p>The ids are stamped when the span opens, not when the event commits: they are known up front, and the <code>ScopedValue</code> binding is already gone by the time a <code>finally</code> block runs, so stamping there would silently do nothing.</p>

      <h3><code>openSpanOf(event)</code></h3>

      <p><code>SpanContext openSpanOf(AbstractTracedEvent event)</code> — stamps the event and hands back its context <strong>without binding it</strong>, for instrumentation that cannot wrap its work in a lambda because the work has not started yet and will not arrive on this thread.</p>

      <p>A caller that forgets to re-enter loses the nesting, not the span: the event still carries its identity and still appears in the trace.</p>

      <h3><code>reenter(context, body)</code></h3>

      <p>Re-establishes an existing span on this thread, so work belonging to a span opened elsewhere nests underneath it. It resumes <strong>that same span</strong> — not a child of it — because a protocol's callbacks are not separate operations, they are one operation arriving in pieces.</p>

      <DocsCodeBlock :code="reenterExample" language="java" />

      <p>Each re-entry emits a <code>jeffrey.TraceScope</code> event recording which thread the span ran on and for how long. See <a href="#callbacks">the callback scenario</a> for what that buys.</p>

      <h3><code>continueIn(parent, name, kind, body)</code></h3>

      <p>Opens a span whose parent is the <em>passed</em> context rather than whatever this thread has bound — the way to keep a trace connected across an executor boundary. Pass <code>null</code> to start a fresh trace.</p>

      <p>Unlike <code>reenter</code>, this mints a <strong>child</strong> and emits an event for it, which is right when the receiving thread is doing a separate piece of work.</p>

      <h3><code>stamp(event)</code></h3>

      <p>Gives <code>event</code> a span of its own, nested inside the span in progress. Does nothing when no span is in progress, leaving the ids at <code>0</code> — the encoding for "not part of a trace".</p>

      <p>An emitter that commits in its own <code>finally</code> should not call it directly: <code>event.stampAndCommit()</code> folds the stamp into the commit, so forgetting the stamp — which silently drops the event from every trace — stops being possible. Reach for <code>stamp</code> itself only when the commit is deferred past the enclosing binding, e.g. an event committed from a stream's <code>close()</code>.</p>

      <DocsCodeBlock :code="stampExample" language="java" />

      <DocsCallout type="tip">
        <strong>A span of its own, not a copy of the enclosing one.</strong> Every statement issued inside one span would otherwise carry that same span id, and a span id has to identify exactly one span. Nothing is bound to the minted id, which is what makes a stamped event a <em>leaf</em>: the work it describes is the event itself, not a scope other spans nest inside.
      </DocsCallout>

      <h3>Summary</h3>

      <div class="table-responsive">
        <table class="table table-sm">
          <thead>
            <tr>
              <th>Method</th>
              <th>Mints</th>
              <th>Binds</th>
              <th>Emits</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><code>run</code> / <code>call</code></td>
              <td>child of current</td>
              <td>yes</td>
              <td><code>jeffrey.TraceSpan</code></td>
            </tr>
            <tr>
              <td><code>inSpan</code></td>
              <td>child of current</td>
              <td>yes</td>
              <td>nothing</td>
            </tr>
            <tr>
              <td><code>inSpanOf</code></td>
              <td>child of current</td>
              <td>yes</td>
              <td>nothing — the passed event <em>is</em> the span</td>
            </tr>
            <tr>
              <td><code>openSpanOf</code></td>
              <td>child of current</td>
              <td><strong>no</strong> — returns the context</td>
              <td>nothing</td>
            </tr>
            <tr>
              <td><code>continueIn</code></td>
              <td>child of the <em>passed</em> parent</td>
              <td>yes</td>
              <td><code>jeffrey.TraceSpan</code></td>
            </tr>
            <tr>
              <td><code>reenter</code></td>
              <td>nothing</td>
              <td>the passed context, verbatim</td>
              <td><code>jeffrey.TraceScope</code></td>
            </tr>
            <tr>
              <td><code>stamp</code></td>
              <td>child of current, onto the event</td>
              <td>no</td>
              <td>nothing — the event is a leaf</td>
            </tr>
          </tbody>
        </table>
      </div>

      <DocsCallout type="info">
        <strong>Behaviour when the event type is disabled differs on purpose.</strong> <code>call</code> runs the body with <em>no</em> binding, because an enclosing binding survives and there is something to fall back on. <code>inSpan</code>, <code>continueIn</code> and <code>reenter</code> bind <em>regardless</em>: their caller reached for them precisely because this thread has nothing bound, so skipping the binding would drop the work out of the trace entirely and no-op every <code>stamp</code> underneath it.
      </DocsCallout>

      <h2 id="scenarios">Worked Scenarios</h2>

      <h3>A failure</h3>

      <DocsCodeBlock :code="errorExample" language="java" />

      <DocsCodeBlock :code="errorTree" language="plaintext" />

      <p>Both spans are <code>ERROR</code>, because the exception escaped both bodies. Catch it inside the body if a handled failure should not mark the enclosing operation as failed.</p>

      <h3>Fan-out to an executor</h3>

      <p><code>ScopedValue</code> propagates to child threads only through structured concurrency; work submitted to a plain executor does not inherit the current span.</p>

      <DocsCodeBlock :code="continueInExample" language="java" />

      <DocsCodeBlock :code="continueInTree" language="plaintext" />

      <DocsCallout type="tip">
        Note the parent's self time — 2797&nbsp;ms, the whole span, even though its children account for far more than that between them. Self time only subtracts children that shared the parent's <strong>thread</strong>, so concurrent work on other threads does not make a parent look artificially idle.
      </DocsCallout>

      <h3 id="callbacks">A callback-driven protocol</h3>

      <p>A gRPC call is not a single block of work: the handler runs from listener callbacks after the interceptor returned, on threads it does not control. <code>openSpanOf</code> opens the span without binding, and <code>reenter</code> resumes it around each callback — every one of them, since a streaming call spreads its work across all of them.</p>

      <DocsCodeBlock :code="reenterExample" language="java" />

      <DocsCodeBlock :code="reenterTree" language="plaintext" />

      <p>Re-entering from several callbacks yields several bindings of the <em>same</em> span — not several nesting levels, and not several traces. Alongside it, the scope events record where that span really ran:</p>

      <DocsCodeBlock :code="scopeTree" language="plaintext" />

      <p>This matters because JFR attributes a duration event to the thread that <strong>commits</strong> it. The exchange above is committed by the transport thread that calls <code>close()</code>, even though 41.4 of its 42.1&nbsp;ms ran on the executor thread — so without the scopes, a flamegraph scoped to that operation would sample a parked event-loop thread. Spans that are never re-entered emit no scopes: <code>call</code>, <code>inSpan</code> and <code>inSpanOf</code> are thread-confined already, so their span is its own single scope and existing instrumentation pays nothing.</p>

      <h2 id="reading-the-tree">Reading the Tree</h2>

      <p>The waterfall walks each root and its subtree depth-first, ordering siblings by start time. Underneath, the shape is just the three ids:</p>

      <DocsCodeBlock :code="idsTree" language="plaintext" />

      <p>Two malformed shapes survive without losing a span. A span whose parent is <strong>missing</strong> — it fell below a threshold, or was never instrumented — is promoted to a root and its dangling parent id is dropped, so the tree the UI receives is self-consistent. A parent <strong>cycle</strong>, which cannot arise from correct instrumentation, leaves its members unreachable from any root; rather than dropping them, the traversal breaks into the earliest one and carries on until every span is placed.</p>

      <h2 id="traced-events">Events That Are Already Spans</h2>

      <p>The HTTP, gRPC and JDBC events in <code>jeffrey-events</code> extend <code>AbstractTracedEvent</code>, which carries the whole span shape — <code>traceId</code>, <code>spanId</code>, <code>parentSpanId</code>, <code>name</code>, <code>kind</code>, <code>status</code>, <code>errorType</code> and <code>attributes</code>. An event that has them <em>is</em> a span; there is nothing for a reader to interpret.</p>

      <p>Each type answers for its own span shape by overriding <code>describeSpan()</code> — an HTTP exchange names itself by method and matched URI template and fails at status 400, a gRPC call names itself by service and method and fails on anything but <code>OK</code>. Commit through <code>commitSpan()</code>, which is <code>describeSpan()</code> followed by <code>commit()</code>, so an emitter cannot forget the first half.</p>

      <p><code>jeffrey.TraceSpan</code> declares no fields of its own: a hand-written span is simply the case where the span shape is all there is.</p>

      <DocsCallout type="tip">
        The derivation names no event type at all. It treats an event as a span when the recording's own metadata says it declares a <code>spanId</code> field, so instrumentation written outside Jeffrey takes part in traces with no change to Jeffrey — extend <code>AbstractTracedEvent</code> and stamp.
      </DocsCallout>

      <h2 id="naming">Naming Spans</h2>

      <p>Span names enter the JFR per-chunk string pool, so they must be <strong>stable and low-cardinality</strong>. Name the operation, not the instance of it: <code>order.checkout</code>, never <code>order.checkout.a3f9c1</code>. A name built from a request id or a user id inflates the recording without making it more useful — the identity belongs in the trace id, which is already there.</p>

      <h2 id="volume">Controlling Volume</h2>

      <p>Every span is recorded, however short. A duration threshold is a decision about one application's span volume, not one the event should make on its behalf: dropping short spans orphans their children and moves their samples into the parent's self time. Set one explicitly per recording when the volume calls for it:</p>

      <DocsCodeBlock :code="volumeExample" language="bash" />

      <p>Disabling <code>TraceScopeEvent</code> is safe in a different way: <code>reenter</code> binds whether or not the event type is enabled, so the tree stays exactly as it was and only the cross-thread windows are lost.</p>

      <h2 id="limits">Limits &amp; Gotchas</h2>

      <ul>
        <li><strong>A span's own event names the thread that ended it.</strong> JFR attributes a duration event to the thread that commits it. For work that is a separate operation on the receiving thread, use <code>continueIn</code>, which opens a child there and stays thread-confined. For one operation arriving in pieces, use <code>reenter</code>, which records a scope per activation.</li>
        <li><strong><code>ScopedValue</code> does not cross a plain executor.</strong> Capture the context with <code>current()</code> before the fork and re-establish it explicitly.</li>
        <li><strong>Trace ids are 64-bit</strong>, not the 128-bit W3C shape. Ample for a single JVM that mints all of its own ids, but it means an application already running OpenTelemetry cannot hand Jeffrey its real <code>traceparent</code> and expect the ids to match what Jaeger or Datadog display.</li>
        <li><strong>Spans on virtual threads have no flamegraph.</strong> A span is matched to samples by <code>(thread, window)</code>, and CPU samples are attributed to the <em>carrier</em> thread, so a span that stayed on a virtual thread matches none of them. Fork the work onto a pool thread with <code>continueIn</code>, or let the JVM sample CPU itself — the <router-link to="/docs/microscope/profiles/traces">Traces page</router-link> works the case through with a real trace.</li>
        <li><strong>Not a distributed tracer.</strong> Traces are scoped to a single JVM — one recording, one set of traces. Jeffrey will not stitch a request across service boundaries.</li>
      </ul>

      <p class="docs-read-more">
        <router-link to="/docs/microscope/profiles/traces">Back to the Traces &amp; Spans reference &rarr;</router-link>
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
