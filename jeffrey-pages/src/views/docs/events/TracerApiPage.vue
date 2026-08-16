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
  { id: 'concepts', text: 'Trace, Span, SpanContext', level: 2 },
  { id: 'events', text: 'The JFR Events Behind It', level: 2 },
  { id: 'api', text: 'API Reference', level: 2 },
  { id: 'api-run-call', text: 'run / call', level: 3 },
  { id: 'api-current', text: 'current', level: 3 },
  { id: 'api-stamp', text: 'stamp', level: 3 },
  { id: 'api-inspanof', text: 'inSpanOf', level: 3 },
  { id: 'api-openspanof-reenter', text: 'openSpanOf / reenter', level: 3 },
  { id: 'api-continuein', text: 'continueIn', level: 3 },
  { id: 'api-fork', text: 'fork', level: 3 },
  { id: 'semantics', text: 'Semantics at a Glance', level: 2 },
  { id: 'choosing', text: 'Choosing the Right Method', level: 2 },
  { id: 'composed-tree', text: 'A Complete Tree', level: 2 },
  { id: 'usages', text: 'Where Jeffrey Uses It', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const quickStart = `import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.Tracer;

Tracer.run("order.checkout", SpanKind.SERVER, () -> {
    Tracer.run("inventory.reserve", SpanKind.CLIENT, this::reserve);
    Tracer.run("payment.charge", SpanKind.CLIENT, this::charge);
});`;

const quickStartTree = `trace 5f3a90c2…                                      emitted event
└─ order.checkout        SERVER   parentSpanId=0     jeffrey.TraceSpan
   ├─ inventory.reserve  CLIENT                      jeffrey.TraceSpan
   └─ payment.charge     CLIENT                      jeffrey.TraceSpan`;

const spanContextShape = `public record SpanContext(long traceId, long spanId, long parentSpanId) {
    public static SpanContext root() { … }        // fresh trace, no parent
    public SpanContext child() { … }              // same trace, new span id, parented here
    public boolean isRoot() { return parentSpanId == 0; }

    // Forms taking a RandomGenerator explicitly exist for tests that need deterministic ids.
}`;

const runCallSignatures = `// Runnable forms — for side-effecting work
static void run(String name, Runnable body)                    // kind = INTERNAL
static void run(String name, SpanKind kind, Runnable body)

// Value-returning forms — the body may throw a checked exception,
// and the thrown type X is inferred, not wrapped
static <R, X extends Throwable>
R call(String name, ScopedValue.CallableOp<? extends R, X> body) throws X

static <R, X extends Throwable>
R call(String name, SpanKind kind, ScopedValue.CallableOp<? extends R, X> body) throws X`;

const callExample = `// From ProfileInitializerImpl — the profile-creation pipeline
return Tracer.call("profile.initialize", SpanKind.INTERNAL, () -> {
    Tracer.run("profile-info.insert", () -> { … });
    Tracer.run("recording.parse", () -> recordingEventParser.start(eventWriter, recordingPath));
    Tracer.run("events.flush", eventWriter::onComplete);
    Tracer.run("traces.derive", () -> { … });
    Tracer.run("profile.data-init", () -> profileDataInitializer.initialize(profileManager));
    return profileInfo;
});`;

const callTree = `trace c81d02aa…
└─ profile.initialize    INTERNAL  parentSpanId=0    jeffrey.TraceSpan
   ├─ profile-info.insert INTERNAL                   jeffrey.TraceSpan
   ├─ recording.parse    INTERNAL                    jeffrey.TraceSpan
   ├─ events.flush       INTERNAL                    jeffrey.TraceSpan
   ├─ traces.derive      INTERNAL                    jeffrey.TraceSpan
   └─ profile.data-init  INTERNAL                    jeffrey.TraceSpan`;

const callErrorExample = `IllegalStateException thrown = assertThrows(IllegalStateException.class,
    () -> Tracer.run("payment.charge", SpanKind.CLIENT, () -> {
        throw new IllegalStateException("card declined");
    }));

// The span is still recorded:
//   name      = "payment.charge"
//   status    = ERROR
//   errorType = "java.lang.IllegalStateException"
// and the exception is rethrown unchanged — same instance, no wrapping.`;

const currentSignature = `static Optional<SpanContext> current()

// Typical use: capture the context when the hand-off site and the wrapping
// site are not the same place — otherwise prefer fork, which captures for you
SpanContext parent = Tracer.current().orElse(null);
request.attachTraceContext(parent);   // continueIn(parent, …) runs elsewhere, later`;

const stampExample = `// From DatabaseClient — every SQL statement becomes a leaf span
JdbcInsertEvent event = new JdbcInsertEvent("insert_recording", "microscope");
Tracer.stamp(event);          // child ids under the span in progress; no-op outside one
event.begin();
int rows = delegate.update(sql, paramSource);
event.end();
if (event.shouldCommit()) {
    event.sql = sql;
    event.rows = rows;
    event.commitSpan();       // the event derives name/status for itself, then commits
}`;

const stampTree = `trace 8c1d33f0…
└─ GET /api/internal/recordings   SERVER  (the span in progress)
   ├─ insert_recording   JdbcInsertEvent  CLIENT   leaf — nothing can nest under it
   └─ select_recordings  JdbcQueryEvent   CLIENT   leaf

Two events stamped inside the same span each get their own span id;
they share the trace id and the parent id, never the span id.`;

const inSpanOfExample = `// From JeffreyJfrHttpEventFilter — the HTTP exchange event IS the root span
HttpServerExchangeEvent event = new HttpServerExchangeEvent();
event.begin();
try {
    Tracer.inSpanOf(event, () -> {
        filterChain.doFilter(request, response);   // everything traced inside nests here
        return null;
    });
} finally {
    event.end();
    if (event.shouldCommit()) {
        event.uri = resolveTemplateUri(request);
        event.method = request.getMethod();
        event.statusCode = response.getStatus();
        event.commitSpan();
    }
}`;

const inSpanOfTree = `trace 2b7fe410…
└─ GET /api/internal/profiles/{profileId}   HttpServerExchangeEvent  SERVER  parentSpanId=0
   ├─ flamegraph.generate                   jeffrey.TraceSpan        INTERNAL
   │  └─ select_frames                      JdbcQueryEvent           CLIENT  leaf
   └─ flamegraph.marshalling                jeffrey.TraceSpan        INTERNAL

No jeffrey.TraceSpan is emitted for the root — the exchange event carries
the ids itself. Emitting one would record the same interval twice.`;

const openReenterExample = `// From JfrGrpcServerInterceptor — a gRPC call runs from listener callbacks
// long after the interceptor returned, on threads it does not control.
GrpcServerExchangeEvent event = new GrpcServerExchangeEvent();
event.begin();
SpanContext span = Tracer.openSpanOf(event);   // stamps the event, binds NOTHING

return new SimpleForwardingServerCallListener<>(listener) {
    @Override
    public void onHalfClose() {                          // where a unary handler actually runs
        Tracer.reenter(span, () -> super.onHalfClose()); // resumes the SAME span, not a child
    }
    // onMessage / onCancel / onComplete / onReady wrapped the same way
};`;

const openReenterTree = `trace 4e11d5b8…
└─ jeffrey.api.v1.WorkspaceService/List  GrpcServerExchangeEvent  SERVER  parentSpanId=0
   └─ select_workspaces                  JdbcQueryEvent  CLIENT   (stamped inside a re-entry)

plus one jeffrey.TraceScope per activation — not part of the tree,
but the only honest record of where the span actually ran:
   jeffrey.TraceScope  scopedSpanId=<root>  thread=grpc-default-executor-0   (onMessage)
   jeffrey.TraceScope  scopedSpanId=<root>  thread=grpc-default-executor-2   (onHalfClose)`;

const continueInExample = `// The manual form: capture the context on the submitting thread,
// re-establish it inside the task. fork (below) packages this pattern.
SpanContext parent = Tracer.current().orElse(null);

executor.submit(() ->
    Tracer.continueIn(parent, "chunk.parse", SpanKind.INTERNAL, () -> parseChunk(chunk)));`;

const forkExample = `// From ParallelRecordingFileIterator — per-chunk parsing forked to a pool.
// fork captures the enclosing span HERE, on the submitting thread, and
// continueIn runs inside the task when the pool eventually executes it.
return CompletableFuture.supplyAsync(
        Tracer.fork("chunk.parse",
                () -> singleFileIterator.apply(recording).partialCollect(collector)),
        Schedulers.sharedBulkParallel());

// From ProfileDataInitializerImpl — the Runnable form
CompletableFuture.runAsync(
        Tracer.fork("guardian.results",
                () -> profileManager.guardianManager().guardResults()),
        executor);`;

const continueInTree = `trace 9d02f7c3…
└─ POST /api/internal/recordings   SERVER              (request thread)
   └─ profile.initialize           INTERNAL            (request thread)
      └─ recording.parse           INTERNAL            (request thread)
         ├─ chunk.parse            INTERNAL            (pool thread A)  ← continueIn
         └─ chunk.parse            INTERNAL            (pool thread B)  ← continueIn

Passing null instead of a parent context starts a fresh trace:
trace f01b44d7…   (unrelated to the one above)
└─ chunk.parse   INTERNAL   parentSpanId=0`;

const composedTree = `trace a3f9c1d4…                                         event type
└─ POST /api/internal/recordings/upload   SERVER       HttpServerExchangeEvent   ← inSpanOf
   └─ profile.initialize                  INTERNAL     jeffrey.TraceSpan         ← call
      ├─ profile-info.insert              INTERNAL     jeffrey.TraceSpan         ← run
      │  └─ insert_profile                CLIENT       JdbcInsertEvent           ← stamp
      ├─ recording.parse                  INTERNAL     jeffrey.TraceSpan         ← run
      │  ├─ chunk.parse                   INTERNAL     jeffrey.TraceSpan         ← fork (pool)
      │  └─ chunk.parse                   INTERNAL     jeffrey.TraceSpan         ← fork (pool)
      ├─ events.flush                     INTERNAL     jeffrey.TraceSpan         ← run
      │  └─ insert_events                 CLIENT       JdbcInsertEvent           ← stamp
      └─ profile.data-init                INTERNAL     jeffrey.TraceSpan         ← run
         ├─ eventviewer.tree              INTERNAL     jeffrey.TraceSpan         ← fork (pool)
         ├─ guardian.results              INTERNAL     jeffrey.TraceSpan         ← fork (pool)
         └─ threads.rows                  INTERNAL     jeffrey.TraceSpan         ← fork (pool)`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Tracer API"
      icon="bi bi-diagram-3"
    />

    <div class="docs-content">
      <p>The <code>Tracer</code> class (<code>cafe.jeffrey.jfr.events.trace.Tracer</code>) is the whole tracing API of the <router-link to="/docs/events/overview">Jeffrey Events</router-link> library: a small set of static methods that record nested spans into the JFR recording. This page is the method-by-method reference — what each call does, what it emits, and the span tree that exists after it runs. For how Jeffrey then renders those trees, see <router-link to="/docs/microscope/profiles/traces">Traces &amp; Spans</router-link>.</p>

      <h2 id="overview">Overview</h2>

      <p>The span currently in progress is published through a <code>ScopedValue</code>. A nested call discovers its parent by itself — nothing is threaded through the call chain, and the binding is bounded by the lambda, so it cannot outlive the span and never needs clearing:</p>

      <DocsCodeBlock :code="quickStart" language="java" />

      <p>Those three calls produce this tree in the recording:</p>

      <DocsCodeBlock :code="quickStartTree" language="text" />

      <DocsCallout type="info">
        <strong>Cost when nothing is recording.</strong> When the <code>jeffrey.TraceSpan</code> event type is disabled — no JFR recording, or a configuration that leaves it off — the body runs directly: no binding is established, no event is committed, and the event instance itself is escape-analysable. Instrumentation is safe to leave in production code permanently.
      </DocsCallout>

      <DocsCallout type="warning">
        <strong>Java 25 or newer.</strong> The API is built on <code>ScopedValue</code> (JEP&nbsp;506) and <code>jdk.jfr.Contextual</code>, both finalized in Java&nbsp;25.
      </DocsCallout>

      <h2 id="concepts">Trace, Span, SpanContext</h2>

      <p>A <strong>span</strong> is one timed operation: a name, a kind, a start, a duration, a thread, an outcome, and a parent. A <strong>trace</strong> is the tree of spans reachable from one root. A span's position in its trace is fully described by a <code>SpanContext</code> — the value the <code>ScopedValue</code> carries:</p>

      <DocsCodeBlock :code="spanContextShape" language="java" />

      <p>The ids are 64-bit longs where <code>0</code> means "absent": a span with <code>parentSpanId == 0</code> is a root, and an event whose ids are all zero is not part of any trace. The record is immutable — a nested span never mutates its parent's context, it derives a child — which is what makes it safe to publish through a <code>ScopedValue</code> and to carry across threads.</p>

      <p>Two enums complete the shape. <code>SpanKind</code> says what role the operation plays — <code>SERVER</code> (handling an inbound request), <code>CLIENT</code> (waiting on something outside the process), <code>INTERNAL</code> (in-process work, the default). <code>SpanStatus</code> says how it finished — <code>UNSET</code> (no opinion expressed, the default), <code>OK</code> (success the code observed), <code>ERROR</code> (failed, with the exception class recorded in <code>errorType</code>).</p>

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
            <td>A hand-written span — one named interval of work. Emitted by <code>run</code>, <code>call</code> and <code>continueIn</code>. Declares no fields of its own; everything lives on the base type below.</td>
          </tr>
          <tr>
            <td><code>AbstractTracedEvent</code></td>
            <td>The span shape every traced event carries: <code>traceId</code>, <code>spanId</code>, <code>parentSpanId</code>, <code>name</code>, <code>kind</code>, <code>status</code>, <code>errorType</code>, <code>attributes</code>. The HTTP, gRPC and JDBC events extend it, so an instrumented event <em>is</em> a span — Jeffrey recognises spans by the declared <code>spanId</code> field, not by a list of event types.</td>
          </tr>
          <tr>
            <td><code>jeffrey.TraceScope</code></td>
            <td>One stretch of time during which a span was active on one thread. Emitted only by <code>reenter</code> — a span event answers <em>what happened</em>, a scope answers <em>where and when it was running</em>, and the two come apart as soon as a span is re-entered across threads.</td>
          </tr>
        </tbody>
      </table>

      <p>An instrumented event fills in its own span shape in <code>describeSpan()</code>, invoked by <code>commitSpan()</code> just before the commit: an HTTP exchange names itself <code>GET /api/internal/profiles/{profileId}</code> and turns <code>ERROR</code> from status 400 upwards; a gRPC call names itself <code>service/Method</code> and fails on anything but <code>OK</code>; a JDBC statement is born named after its label, is always <code>CLIENT</code>, and <code>failed(throwable)</code> settles its outcome.</p>

      <p>Jeffrey applies those same conventions when it derives a trace, reading the exchange's own fields rather than the recorded name, so that one endpoint is one operation across library versions. Overriding <code>describeSpan()</code> on one of the event types Jeffrey ships therefore changes what the recording says but not what Trace Operations lists. A new event type of your own is named by whatever it writes in <code>describeSpan()</code> — or, better, by the <code>@Span</code> template it declares on the class: the template travels inside the recording's metadata, Jeffrey discovers and applies it with no change on its side, and it keeps working even for events committed with plain <code>commit()</code>. The verdict does not: a span's status is only ever recorded, so commit through <code>commitSpan()</code> if failures should count.</p>

      <h2 id="api">API Reference</h2>

      <h3 id="api-run-call">run / call — record a span around a block</h3>

      <DocsCodeBlock :code="runCallSignatures" language="java" />

      <p>The workhorses. Both open a span whose parent is whatever span is bound on the current thread — or a fresh root when none is — run the body with the new context bound, and emit one <code>jeffrey.TraceSpan</code> when it completes. <code>run</code> is the <code>Runnable</code> form; <code>call</code> returns the body's result and lets checked exceptions flow through with their real type, because <code>ScopedValue.CallableOp</code> infers the thrown type instead of wrapping it.</p>

      <DocsCodeBlock :code="callExample" language="java" />

      <DocsCodeBlock :code="callTree" language="text" />

      <p>An exception escaping the body marks the span <code>ERROR</code>, records the exception's class name, and is rethrown unchanged:</p>

      <DocsCodeBlock :code="callErrorExample" language="java" />

      <DocsCallout type="warning">
        <strong>Span names must be stable and low-cardinality.</strong> Every distinct name enters the JFR per-chunk string pool, so a name built from a request id or a user id inflates the recording. Name the operation, not the instance of it: <code>order.checkout</code>, never <code>order.checkout.a3f9c1</code>.
      </DocsCallout>

      <h3 id="api-current">current — read the span in progress</h3>

      <DocsCodeBlock :code="currentSignature" language="java" />

      <p>Returns the <code>SpanContext</code> bound on this thread, or empty when none is. Its job is capturing the parent when work will continue somewhere <code>ScopedValue</code> cannot reach — for the common executor case <a href="#api-fork">fork</a> does the capture itself, so reach for <code>current()</code> when the captured context has to travel further than the wrapping site, paired with <a href="#api-continuein">continueIn</a>.</p>

      <h3 id="api-stamp">stamp — make an event a leaf of the current span</h3>

      <p><code>static void stamp(AbstractTracedEvent event)</code></p>

      <p>Gives the event a span of its own, nested inside the span currently in progress, so the event takes its place in the trace. Nothing is bound to the minted id — that is what makes a stamped event a <strong>leaf</strong>: the work it describes is the event itself, not a scope other spans can nest inside. Outside any span it does nothing, leaving the ids at <code>0</code> — the encoding for "not part of a trace" — so the same instrumentation works traced and untraced.</p>

      <DocsCodeBlock :code="stampExample" language="java" />

      <DocsCodeBlock :code="stampTree" language="text" />

      <p>Each stamped event gets a span id of its own rather than a copy of the enclosing one — every statement issued inside one request would otherwise carry the same span id, and a span id has to identify exactly one span.</p>

      <h3 id="api-inspanof">inSpanOf — the event is the span</h3>

      <p><code>static &lt;R, X&gt; R inSpanOf(AbstractTracedEvent event, ScopedValue.CallableOp&lt;R, X&gt; body) throws X</code><br>
      <code>static void inSpanOf(AbstractTracedEvent event, Runnable body)</code></p>

      <p>For instrumentation whose <em>own</em> event already describes the interval — an HTTP exchange, a gRPC call, a pipeline run. Emitting a <code>jeffrey.TraceSpan</code> alongside such an event would record the same interval twice, so none is emitted: the event carries the ids, and everything traced inside the body nests underneath it. The ids are stamped when the span opens, not when the event commits — they are known up front, and the binding is gone by the time a caller's <code>finally</code> block runs, so stamping there would silently do nothing.</p>

      <DocsCodeBlock :code="inSpanOfExample" language="java" />

      <DocsCodeBlock :code="inSpanOfTree" language="text" />

      <p>Unlike <code>call</code>, this always establishes the binding, even with nothing recording: whether an interval is recorded at all is the caller's event's decision, not this method's.</p>

      <h3 id="api-openspanof-reenter">openSpanOf / reenter — a span the work arrives back into</h3>

      <p><code>static SpanContext openSpanOf(AbstractTracedEvent event)</code><br>
      <code>static &lt;R, X&gt; R reenter(SpanContext context, ScopedValue.CallableOp&lt;R, X&gt; body) throws X</code><br>
      <code>static void reenter(SpanContext context, Runnable body)</code></p>

      <p>For instrumentation that cannot wrap its work in one lambda. A callback-driven protocol is the case this pair exists for: a gRPC call runs from listener callbacks long after the interceptor that started it has returned, on threads it does not control, so there is no single block for <code>inSpanOf</code> to enclose. <code>openSpanOf</code> stamps the event and hands back the context <em>without binding anything</em>; the caller keeps it and re-establishes it per callback with <code>reenter</code>.</p>

      <p><code>reenter</code> resumes the <em>same</em> span — not a child of it — because a protocol's callbacks are not separate operations, they are one operation arriving in pieces. Each re-entry emits one <code>jeffrey.TraceScope</code> recording which thread the span ran on and for how long. That matters because JFR attributes a duration event to the thread that <em>commits</em> it: a re-entered span can be closed on a thread it barely ran on, and the scopes are then the only record of where the work actually happened.</p>

      <DocsCodeBlock :code="openReenterExample" language="java" />

      <DocsCodeBlock :code="openReenterTree" language="text" />

      <p>A caller that forgets to re-enter loses the nesting, not the span — the event still carries its identity and still appears in the trace, and anything traced in an un-reentered callback starts a fresh trace instead of nesting.</p>

      <h3 id="api-continuein">continueIn — carry a trace across an executor</h3>

      <p><code>static &lt;R, X&gt; R continueIn(SpanContext parent, String name, SpanKind kind, ScopedValue.CallableOp&lt;R, X&gt; body) throws X</code><br>
      <code>static void continueIn(SpanContext parent, String name, SpanKind kind, Runnable body)</code><br>
      Kind-less forms of both default to <code>SpanKind.INTERNAL</code>, like <code>run</code> and <code>call</code>.</p>

      <p><code>ScopedValue</code> propagates to child threads only through structured concurrency; work submitted to a plain executor does not inherit the current span. <code>continueIn</code> is the bridge: it records a span whose parent is the <em>given</em> context rather than whatever the receiving thread has bound, so the forked work stays in the trace. It mints a <strong>child</strong> and emits a <code>jeffrey.TraceSpan</code> for it — the receiving thread is doing a separate piece of work, which is exactly what distinguishes it from <code>reenter</code>. Pass <code>null</code> to start a fresh trace.</p>

      <DocsCodeBlock :code="continueInExample" language="java" />

      <DocsCodeBlock :code="continueInTree" language="text" />

      <p>The capture-and-continue two-step above is packaged by <a href="#api-fork">fork</a>; <code>continueIn</code> remains the primitive for when the context arrives from somewhere other than the wrapping site — stored on a request object, or handed over by a protocol.</p>

      <h3 id="api-fork">fork — wrap a task for an executor</h3>

      <p><code>static Runnable fork(String name, SpanKind kind, Runnable body)</code><br>
      <code>static &lt;T&gt; Supplier&lt;T&gt; fork(String name, SpanKind kind, Supplier&lt;T&gt; body)</code><br>
      Kind-less forms of both default to <code>SpanKind.INTERNAL</code> — forked work is in-process work unless declared otherwise.</p>

      <p>The packaged form of the executor pattern: <code>fork</code> captures the span in progress <em>when it is called</em> and returns a task that, wherever it eventually runs, records its work as a child of that span via <code>continueIn</code>. Wrap on the thread whose span the work belongs to, submit the result. The <code>Supplier</code> form hands straight to <code>CompletableFuture.supplyAsync</code>; called outside any span, the task starts a fresh trace.</p>

      <p>This exists because the manual two-step had a silent failure mode: forgetting the capture didn't break anything visibly — the forked work just fell out of the trace and became a root of its own. With <code>fork</code> the capture cannot be forgotten, because it <em>is</em> the wrap.</p>

      <DocsCodeBlock :code="forkExample" language="java" />

      <p>The resulting tree is exactly the one shown for <code>continueIn</code> above — <code>fork</code> is that call, made later, with the capture already done.</p>

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
            <td><code>run</code> / <code>call</code></td>
            <td><code>jeffrey.TraceSpan</code></td>
            <td>Yes — child of the current span, or a fresh root</td>
            <td>Body runs directly; no binding, no event</td>
          </tr>
          <tr>
            <td><code>current</code></td>
            <td>—</td>
            <td>—</td>
            <td>Empty when nothing is bound</td>
          </tr>
          <tr>
            <td><code>stamp</code></td>
            <td>Nothing (fills the event's ids)</td>
            <td>No</td>
            <td>Stamps whenever a span is bound; no-op otherwise</td>
          </tr>
          <tr>
            <td><code>inSpanOf</code></td>
            <td>Nothing (the event is the span)</td>
            <td>Yes — always</td>
            <td>Still binds: recording is the event's decision</td>
          </tr>
          <tr>
            <td><code>openSpanOf</code></td>
            <td>Nothing (fills the event's ids)</td>
            <td>No — deliberately</td>
            <td>Still stamps</td>
          </tr>
          <tr>
            <td><code>reenter</code></td>
            <td>One <code>jeffrey.TraceScope</code> per re-entry</td>
            <td>Yes — the same span, not a child</td>
            <td>Still binds; no scope emitted</td>
          </tr>
          <tr>
            <td><code>continueIn</code></td>
            <td><code>jeffrey.TraceSpan</code></td>
            <td>Yes — child of the given parent</td>
            <td>Still binds; no event. The handed-over context is the only link, so dropping it would orphan every stamp underneath</td>
          </tr>
          <tr>
            <td><code>fork</code></td>
            <td><code>jeffrey.TraceSpan</code> (via <code>continueIn</code>, when the task runs)</td>
            <td>Captures at wrap time; binds inside the task</td>
            <td>Same as <code>continueIn</code>: the task still binds, no event</td>
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
            <td><code>run</code> / <code>call</code></td>
          </tr>
          <tr>
            <td>An event of your own already describes the interval, and work nests inside it</td>
            <td><code>inSpanOf</code></td>
          </tr>
          <tr>
            <td>An event describes a self-contained action with nothing nesting inside — a statement, a message</td>
            <td><code>stamp</code></td>
          </tr>
          <tr>
            <td>Work handed to a plain executor — a separate operation on the receiving thread</td>
            <td><code>fork</code> around the task, on the submitting thread</td>
          </tr>
          <tr>
            <td>The parent context comes from somewhere other than the submitting site — stored, or handed over by a protocol</td>
            <td><code>continueIn</code> with that context</td>
          </tr>
          <tr>
            <td>One operation arriving in pieces — a protocol's callbacks</td>
            <td><code>openSpanOf</code> once + <code>reenter</code> per callback</td>
          </tr>
        </tbody>
      </table>

      <h2 id="composed-tree">A Complete Tree</h2>

      <p>Jeffrey's own recording-upload flow composes almost every method on this page. An HTTP request roots the trace through <code>inSpanOf</code>, the pipeline stages are <code>call</code>/<code>run</code> spans, chunk parsing and profile-data branches are handed to pools with <code>fork</code>, and every SQL statement along the way is a <code>stamp</code>ed leaf:</p>

      <DocsCodeBlock :code="composedTree" language="text" />

      <p>All of it lands in one tree because everything feeds the same span shape — the HTTP exchange, the hand-written spans and the JDBC statements never reference each other in code.</p>

      <h2 id="usages">Where Jeffrey Uses It</h2>

      <p>Every method above is exercised by Jeffrey's own codebase; these are the reference call sites to read next to this page:</p>

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
            <td><code>JeffreyJfrHttpEventFilter</code></td>
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
            <td><code>stamp</code></td>
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
            <td><code>Guardian</code></td>
            <td><code>call</code></td>
            <td>Per-category guardian evaluation</td>
          </tr>
          <tr>
            <td><code>HprofIndex</code></td>
            <td><code>call</code></td>
            <td>Heap-dump indexing phases</td>
          </tr>
          <tr>
            <td><code>PipelineRunRegistry</code></td>
            <td><code>inSpanOf</code></td>
            <td>Each pipeline run as its own trace root, with a hand-built <code>TraceSpanEvent</code> that records the observed outcome (<code>OK</code>/<code>ERROR</code>)</td>
          </tr>
          <tr>
            <td><code>ReflectiveToolset</code></td>
            <td><code>inSpanOf</code></td>
            <td>Each AI MCP tool invocation, separating model time from query time</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="tip">
        A background job forked onto its own thread — a pipeline run — is deliberately its <em>own</em> trace root rather than a child of the request that triggered it: its lifetime is unrelated to the request. See <router-link to="/docs/microscope/profiles/traces">Traces &amp; Spans → Limits</router-link> for this and the other boundaries of the model.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
