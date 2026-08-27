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
  { id: 'events', text: 'The JFR Events Behind It', level: 2 },
  { id: 'api', text: 'API Reference', level: 2 },
  { id: 'api-run-call', text: 'run / call', level: 3 },
  { id: 'api-current', text: 'current', level: 3 },
  { id: 'api-inspanof', text: 'inSpanOf', level: 3 },
  { id: 'api-stamp', text: 'stamp', level: 3 },
  { id: 'api-openspanof-reenter', text: 'openSpanOf / reenter', level: 3 },
  { id: 'api-continuein', text: 'continueIn', level: 3 },
  { id: 'api-fork', text: 'fork / forkCallable', level: 3 },
  { id: 'api-propagating', text: 'propagating', level: 3 },
  { id: 'errors', text: 'Error Handling', level: 2 },
  { id: 'semantics', text: 'Semantics at a Glance', level: 2 },
  { id: 'choosing', text: 'Choosing the Right Method', level: 2 },
  { id: 'annotation', text: 'Without the Lambda: @Traced', level: 2 },
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

const runCallSignatures = `// Runnable forms — for side-effecting work
static void run(String name, Runnable body)                    // kind = INTERNAL
static void run(String name, SpanKind kind, Runnable body)

// Value-returning forms — the body may throw a checked exception,
// and the thrown type X is inferred, not wrapped
static <R, X extends Throwable>
R call(String name, ScopedValue.CallableOp<? extends R, X> body) throws X

static <R, X extends Throwable>
R call(String name, SpanKind kind, ScopedValue.CallableOp<? extends R, X> body) throws X`;

const runUseCases = `// Use-case 1: time a domain operation inside a request
Tracer.run("cart.validate", () -> validator.validate(cart));

// Use-case 2: a value-returning load — checked exceptions flow through typed,
// so a body throwing IOException still throws IOException, unwrapped
Order order = Tracer.call("order.load", () -> repository.load(id));   // throws IOException

// Use-case 3: an outbound wait that has no dedicated event type yet
Tracer.run("payment.charge", SpanKind.CLIENT, () -> paymentGateway.charge(order));

// Use-case 4: catch inside the body when a handled failure should NOT
// mark the operation as failed
Tracer.run("cache.warm", () -> {
    try {
        cache.preload();
    } catch (CacheUnavailableException e) {
        LOG.warn("Cache warmup skipped", e);   // span stays UNSET — this is fine
    }
});`;

const callExample = `// A real pipeline (Jeffrey's own profile creation): one call() around the
// whole operation, one run() per stage — every stage becomes a bar in
// the waterfall, and the parent's self time exposes anything unaccounted for.
return Tracer.call("profile.initialize", SpanKind.INTERNAL, () -> {
    Tracer.run("profile-info.insert", () -> { … });
    Tracer.run("recording.parse", () -> recordingEventParser.start(eventWriter, recordingPath));
    Tracer.run("events.flush", eventWriter::onComplete);
    Tracer.run("traces.derive", () -> { … });
    Tracer.run("profile.data-init", () -> profileDataInitializer.initialize(profileManager));
    return profileInfo;
});`;

const callTree = `trace c81d02aa…
└─ profile.initialize     INTERNAL  parentSpanId=0    jeffrey.TraceSpan   4102 ms
   ├─ profile-info.insert INTERNAL                    jeffrey.TraceSpan      2 ms
   ├─ recording.parse     INTERNAL                    jeffrey.TraceSpan   2797 ms
   ├─ events.flush        INTERNAL                    jeffrey.TraceSpan    212 ms
   ├─ traces.derive       INTERNAL                    jeffrey.TraceSpan    158 ms
   └─ profile.data-init   INTERNAL                    jeffrey.TraceSpan    933 ms`;

const currentSignature = `static Optional<SpanContext> current()`;

const currentExamples = `// Use-case 1: correlation ids in logs — print the trace id beside a log line,
// so a log entry can be matched to the trace it belongs to
Tracer.current().ifPresent(ctx ->
        MDC.put("traceId", Long.toHexString(ctx.traceId())));

// Use-case 2: capture the parent when the hand-off site and the wrapping site
// are not the same place — otherwise prefer fork, which captures for you
SpanContext parent = Tracer.current().orElse(null);
request.attachTraceContext(parent);      // continueIn(parent, …) runs elsewhere, later`;

const inSpanOfSignatures = `static <R, X extends Throwable>
R inSpanOf(AbstractTracedEvent event, ScopedValue.CallableOp<R, X> body) throws X

static void inSpanOf(AbstractTracedEvent event, Runnable body)`;

const inSpanOfExample = `// The HTTP filter (what jeffrey-tracing-servlet ships): the exchange event
// IS the root span — no separate jeffrey.TraceSpan is emitted for it.
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

const customSpanUsage = `// The same shape with an event type of your own — see Custom Traced Events
KafkaPublishEvent event = new KafkaPublishEvent();
event.topic = "orders";
Tracer.inSpanOf(event, () -> {                 // stamps traceId/spanId/parentSpanId
    // ... the work; anything traced inside nests under this span
});                                            // inSpanOf commits via commitSpan()`;

const stampSignature = `static void stamp(AbstractTracedEvent event)`;

const stampExample = `// Use-case 1: a leaf committed in its own finally — do NOT call stamp yourself;
// commitSpan() folds the stamp into the commit (from Jeffrey's DatabaseClient)
JdbcInsertEvent event = new JdbcInsertEvent("insert_recording", "microscope");
event.begin();
int rows = delegate.update(sql, paramSource);
event.end();
if (event.shouldCommit()) {
    event.sql = sql;
    event.rows = rows;
    event.commitSpan();       // stamps child ids under the span in progress, then commits
}

// Use-case 2: a DEFERRED commit — the event outlives the enclosing binding
// (e.g. a streamed result committed from close()). Stamp eagerly at
// construction, inside the span; commitSpan() later never re-stamps.
JdbcStreamEvent event = new JdbcStreamEvent("stream_events", "profile");
Tracer.stamp(event);                    // capture identity NOW, inside the span
event.begin();
return resultStream.onClose(() -> {
    event.end();
    if (event.shouldCommit()) {
        event.commitSpan();             // runs after the binding is gone — ids already set
    }
});`;

const stampTree = `trace 8c1d33f0…
└─ GET /api/internal/recordings   SERVER  (the span in progress)
   ├─ insert_recording   JdbcInsertEvent  CLIENT   leaf — nothing can nest under it
   └─ select_recordings  JdbcQueryEvent   CLIENT   leaf

Two events stamped inside the same span each get their own span id;
they share the trace id and the parent id, never the span id.`;

const openReenterSignatures = `static SpanContext openSpanOf(AbstractTracedEvent event)

static <R, X extends Throwable>
R reenter(SpanContext context, ScopedValue.CallableOp<R, X> body) throws X

static void reenter(SpanContext context, Runnable body)`;

const openReenterExample = `// The gRPC server interceptor (what jeffrey-tracing-grpc ships): a call runs
// from listener callbacks long after the interceptor returned, on threads it
// does not control — there is no single block for inSpanOf to enclose.
GrpcServerExchangeEvent event = new GrpcServerExchangeEvent();
event.begin();
SpanContext span = Tracer.openSpanOf(event);   // stamps the event, binds NOTHING

return new SimpleForwardingServerCallListener<>(listener) {
    @Override
    public void onHalfClose() {                          // where a unary handler actually runs
        Tracer.reenter(span, () -> super.onHalfClose()); // resumes the SAME span, not a child
    }
    // onMessage / onCancel / onComplete / onReady wrapped the same way;
    // the event is committed from onClose — the one callback that always arrives
};`;

const openReenterTree = `trace 4e11d5b8…
└─ jeffrey.api.v1.WorkspaceService/List  GrpcServerExchangeEvent  SERVER  parentSpanId=0
   └─ select_workspaces                  JdbcQueryEvent  CLIENT   (stamped inside a re-entry)

plus one jeffrey.TraceScope per activation — not part of the tree,
but the only honest record of where the span actually ran:
   jeffrey.TraceScope  scopedSpanId=<root>  thread=grpc-default-executor-0   (onMessage)
   jeffrey.TraceScope  scopedSpanId=<root>  thread=grpc-default-executor-2   (onHalfClose)`;

const continueInSignatures = `static <R, X extends Throwable>
R continueIn(SpanContext parent, String name, SpanKind kind,
             ScopedValue.CallableOp<R, X> body) throws X

static void continueIn(SpanContext parent, String name, SpanKind kind, Runnable body)
// Kind-less forms of both default to SpanKind.INTERNAL, like run and call.
// Passing null as the parent starts a fresh trace.`;

const continueInExample = `// The manual form: capture the context on the submitting thread,
// re-establish it inside the task. fork (below) packages this pattern.
SpanContext parent = Tracer.current().orElse(null);

executor.submit(() ->
    Tracer.continueIn(parent, "chunk.parse", SpanKind.INTERNAL, () -> parseChunk(chunk)));`;

const continueInTree = `trace 9d02f7c3…
└─ POST /api/internal/recordings   SERVER              (request thread)
   └─ profile.initialize           INTERNAL            (request thread)
      └─ recording.parse           INTERNAL            (request thread)
         ├─ chunk.parse            INTERNAL            (pool thread A)  ← continueIn
         └─ chunk.parse            INTERNAL            (pool thread B)  ← continueIn

Passing null instead of a parent context starts a fresh trace:
trace f01b44d7…   (unrelated to the one above)
└─ chunk.parse   INTERNAL   parentSpanId=0`;

const forkSignatures = `static Runnable fork(String name, SpanKind kind, Runnable body)
static Runnable fork(String name, Runnable body)                       // INTERNAL
static <T> Supplier<T> fork(String name, SpanKind kind, Supplier<T> body)
static <T> Supplier<T> fork(String name, Supplier<T> body)             // INTERNAL

// The Callable form for ExecutorService.submit — a distinct name, because a
// result-bearing lambda matches Supplier and Callable alike and would make
// every existing fork call ambiguous
static <T> Callable<T> forkCallable(String name, SpanKind kind, Callable<T> body)
static <T> Callable<T> forkCallable(String name, Callable<T> body)     // INTERNAL`;

const forkExample = `// Use-case 1: CompletableFuture — the Supplier form hands straight to supplyAsync
// (from Jeffrey's parallel JFR-chunk parsing)
return CompletableFuture.supplyAsync(
        Tracer.fork("chunk.parse",
                () -> singleFileIterator.apply(recording).partialCollect(collector)),
        Schedulers.sharedBulkParallel());

// Use-case 2: fire-and-forget — the Runnable form
CompletableFuture.runAsync(
        Tracer.fork("guardian.results",
                () -> profileManager.guardianManager().guardResults()),
        executor);

// Use-case 3: a typed Future — forkCallable for ExecutorService.submit
Future<Report> report = executor.submit(
        Tracer.forkCallable("report.render", () -> render(part)));`;

const propagatingSignature = `static ExecutorService propagating(ExecutorService delegate)`;

const propagatingExample = `// Wrap the POOL once, at construction …
ExecutorService executor = Tracer.propagating(Executors.newFixedThreadPool(8));

// … and every task submitted inside a span runs inside that span — no
// per-call-site wrapping, no name, no child span:
executor.submit(() -> parseChunk(file));      // leaf events inside stamp under the request

// A task that deserves its own NAMED bar in the waterfall still wraps itself —
// the two compose:
executor.submit(Tracer.fork("chunk.parse", () -> parseChunk(file)));`;

const propagatingTree = `trace 77b2e9c1…
└─ POST /api/internal/recordings       SERVER          (request thread)
   ├─ insert_chunk        JdbcInsertEvent  CLIENT      (pool thread A — stamped under the request)
   ├─ insert_chunk        JdbcInsertEvent  CLIENT      (pool thread B)
   └─ chunk.parse         jeffrey.TraceSpan INTERNAL   (pool thread C — the fork-wrapped task)

plus one jeffrey.TraceScope per plain task activation, naming the pool thread —
propagating re-enters the submitting span, it does not mint children.`;

const errorExample = `IllegalStateException thrown = assertThrows(IllegalStateException.class,
    () -> Tracer.run("payment.charge", SpanKind.CLIENT, () -> {
        throw new IllegalStateException("card declined");
    }));

// The span is still recorded:
//   name      = "payment.charge"
//   status    = ERROR
//   errorType = "java.lang.IllegalStateException"
// and the exception is rethrown unchanged — same instance, no wrapping.`;

const composedTree = `trace a3f9c1d4…                                         event type
└─ POST /api/internal/recordings/upload   SERVER       HttpServerExchangeEvent   ← inSpanOf
   └─ profile.initialize                  INTERNAL     jeffrey.TraceSpan         ← call
      ├─ profile-info.insert              INTERNAL     jeffrey.TraceSpan         ← run
      │  └─ insert_profile                CLIENT       JdbcInsertEvent           ← commitSpan (stamp)
      ├─ recording.parse                  INTERNAL     jeffrey.TraceSpan         ← run
      │  ├─ chunk.parse                   INTERNAL     jeffrey.TraceSpan         ← fork (pool)
      │  └─ chunk.parse                   INTERNAL     jeffrey.TraceSpan         ← fork (pool)
      ├─ events.flush                     INTERNAL     jeffrey.TraceSpan         ← run
      │  └─ insert_events                 CLIENT       JdbcInsertEvent           ← commitSpan (stamp)
      └─ profile.data-init                INTERNAL     jeffrey.TraceSpan         ← run
         ├─ eventviewer.tree              INTERNAL     jeffrey.TraceSpan         ← fork (pool)
         ├─ guardian.results              INTERNAL     jeffrey.TraceSpan         ← fork (pool)
         └─ threads.rows                  INTERNAL     jeffrey.TraceSpan         ← fork (pool)`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Tracer API"
      icon="bi bi-code-square"
    />

    <div class="docs-content">
      <p>The <code>Tracer</code> class (<code>cafe.jeffrey.jfr.events.trace.Tracer</code>) is the whole hand-written tracing API: a small set of static methods that record nested spans into the JFR recording. This page is the method-by-method reference — for every method: when to reach for it, a realistic example, and the span tree that exists in the recording after it runs. For how Jeffrey then renders those trees, see <router-link to="/docs/tracing/analysis">Analyzing Traces</router-link>.</p>

      <h2 id="overview">Overview</h2>

      <p>The span currently in progress is published through a <code>ScopedValue</code>. A nested call discovers its parent by itself — nothing is threaded through the call chain, and the binding is bounded by the lambda, so it cannot outlive the span and never needs clearing:</p>

      <DocsCodeBlock :code="quickStart" language="java" />

      <p>Those three calls produce this tree:</p>

      <DocsCodeBlock :code="quickStartTree" language="text" />

      <p>and this is what the recording itself holds — three <code>jeffrey.TraceSpan</code> events whose ids encode the tree:</p>

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
            <td>A hand-written span — one named interval of work. Emitted by <code>run</code>, <code>call</code>, <code>continueIn</code> (and therefore <code>fork</code>), and by <code>@Traced</code>. Declares no fields of its own; everything lives on <code>AbstractTracedEvent</code>.</td>
          </tr>
          <tr>
            <td><code>jeffrey.TraceScope</code></td>
            <td>One stretch of time during which a span was active on one thread. Emitted only by <code>reenter</code> (and by <code>propagating</code>'s task activations) — a span event answers <em>what happened</em>, a scope answers <em>where and when it was running</em>, and the two come apart as soon as a span crosses threads.</td>
          </tr>
          <tr>
            <td>Any <code>AbstractTracedEvent</code></td>
            <td>An instrumented event — HTTP, gRPC, JDBC, or your own — <em>is</em> a span. <code>inSpanOf</code>, <code>openSpanOf</code> and <code>stamp</code> operate on these directly, and no <code>jeffrey.TraceSpan</code> is emitted for them: the event carries the ids itself.</td>
          </tr>
        </tbody>
      </table>

      <h2 id="api">API Reference</h2>

      <h3 id="api-run-call">run / call — record a span around a block</h3>

      <p><strong>Use it when:</strong> a block of in-process work is worth timing — a pipeline stage, a domain operation, a computation. This is the workhorse; if in doubt, start here.</p>

      <DocsCodeBlock :code="runCallSignatures" language="java" />

      <p>Both open a span whose parent is whatever span is bound on the current thread — or a fresh root when none is — run the body with the new context bound, and emit one <code>jeffrey.TraceSpan</code> when it completes. <code>run</code> is the <code>Runnable</code> form; <code>call</code> returns the body's result and lets checked exceptions flow through with their real type, because <code>ScopedValue.CallableOp</code> infers the thrown type instead of wrapping it.</p>

      <DocsCodeBlock :code="runUseCases" language="java" />

      <p>A real pipeline, and the tree it produces:</p>

      <DocsCodeBlock :code="callExample" language="java" />

      <DocsCodeBlock :code="callTree" language="text" />

      <DocsCallout type="warning">
        <strong>Instrument operations, not methods.</strong> <code>order.checkout</code>, <code>inventory.reserve</code>, <code>report.render</code> — a handful of meaningful spans per request beats hundreds of one-per-method spans. And span names must be stable and low-cardinality: name the operation, never the instance (<code>order.checkout</code>, not <code>order.checkout.a3f9c1</code>).
      </DocsCallout>

      <h3 id="api-current">current — read the span in progress</h3>

      <p><strong>Use it when:</strong> something outside the tracing tree needs the identity of the span in progress — a log correlation id, or a context that must travel further than a <code>fork</code> wrap can reach.</p>

      <DocsCodeBlock :code="currentSignature" language="java" />

      <p>Returns the <code>SpanContext</code> bound on this thread, or empty when none is:</p>

      <DocsCodeBlock :code="currentExamples" language="java" />

      <p>For the common executor case, <a href="#api-fork">fork</a> does the capture itself — reach for <code>current()</code> + <a href="#api-continuein">continueIn</a> only when the captured context has to travel further than the wrapping site.</p>

      <h3 id="api-inspanof">inSpanOf — the event is the span</h3>

      <p><strong>Use it when:</strong> an event of your own already describes the interval — an HTTP exchange, a gRPC call, a pipeline run, a Kafka publish — and traced work nests <em>inside</em> it. This is how every trace root is opened.</p>

      <DocsCodeBlock :code="inSpanOfSignatures" language="java" />

      <p>Emitting a <code>jeffrey.TraceSpan</code> alongside such an event would record the same interval twice, so none is emitted: the event carries the ids, and everything traced inside the body nests underneath it. The ids are stamped when the span <em>opens</em>, not when the event commits — they are known up front, and the binding is gone by the time a caller's <code>finally</code> block runs, so stamping there would silently do nothing.</p>

      <DocsCodeBlock :code="inSpanOfExample" language="java" />

      <DocsCodeBlock :code="inSpanOfTree" language="text" />

      <p>The same shape works for an event type of your own — extend <code>AbstractTracedEvent</code> and hand it to <code>inSpanOf</code>, which stamps the ids, marks the span <code>ERROR</code> if the body throws, and commits through <code>commitSpan()</code>:</p>

      <DocsCodeBlock :code="customSpanUsage" language="java" />

      <p>The full recipe — <code>@Span</code> naming templates, <code>describeSpan()</code>, and what plain <code>commit()</code> keeps and loses — is on <router-link to="/docs/tracing/custom-events">Custom Traced Events</router-link>.</p>

      <p>Unlike <code>call</code>, <code>inSpanOf</code> always establishes the binding, even with nothing recording: whether an interval is recorded at all is the caller's event's decision, not this method's.</p>

      <h3 id="api-stamp">stamp — make an event a leaf of the current span</h3>

      <p><strong>Use it when:</strong> an event describes a self-contained action with nothing nesting inside — a statement, a message publish — <em>and</em> its commit is deferred past the enclosing binding. For the ordinary leaf committed in its own <code>finally</code>, you never call <code>stamp</code> directly: <code>commitSpan()</code> folds the stamp into the commit.</p>

      <DocsCodeBlock :code="stampSignature" language="java" />

      <p>Gives the event a span of its own, nested inside the span currently in progress. Nothing is bound to the minted id — that is what makes a stamped event a <strong>leaf</strong>: the work it describes is the event itself, not a scope other spans can nest inside. Outside any span it does nothing, leaving the ids at <code>0</code> — the encoding for "not part of a trace" — so the same instrumentation works traced and untraced.</p>

      <DocsCodeBlock :code="stampExample" language="java" />

      <DocsCodeBlock :code="stampTree" language="text" />

      <p>Each stamped event gets a span id of its own rather than a copy of the enclosing one — every statement issued inside one request would otherwise carry the same span id, and a span id has to identify exactly one span.</p>

      <h3 id="api-openspanof-reenter">openSpanOf / reenter — a span the work arrives back into</h3>

      <p><strong>Use it when:</strong> one operation arrives in pieces on threads you don't control — a gRPC call's listener callbacks, an async HTTP client's completion handlers. There is no single block for <code>inSpanOf</code> to enclose.</p>

      <DocsCodeBlock :code="openReenterSignatures" language="java" />

      <p><code>openSpanOf</code> stamps the event and hands back the context <em>without binding anything</em>; the caller keeps it and re-establishes it per callback with <code>reenter</code>. <code>reenter</code> resumes the <em>same</em> span — not a child of it — because a protocol's callbacks are not separate operations, they are one operation arriving in pieces.</p>

      <DocsCodeBlock :code="openReenterExample" language="java" />

      <DocsCodeBlock :code="openReenterTree" language="text" />

      <p>Each re-entry emits one <code>jeffrey.TraceScope</code> recording which thread the span ran on and for how long. That matters because JFR attributes a duration event to the thread that <em>commits</em> it: a re-entered span can be closed on a thread it barely ran on, and the scopes are then the only record of where the work actually happened — they are what the span drill-down and the span-scoped flamegraph read.</p>

      <p>A caller that forgets to re-enter loses the nesting, not the span — the event still carries its identity and still appears in the trace; anything traced in an un-reentered callback starts a fresh trace instead of nesting.</p>

      <h3 id="api-continuein">continueIn — carry a trace across an executor, explicitly</h3>

      <p><strong>Use it when:</strong> the parent context comes from somewhere other than the wrapping site — stored on a request object, handed over by a protocol, or read from a queue message. For the common submit-site case, use <a href="#api-fork">fork</a> instead, which packages the capture.</p>

      <DocsCodeBlock :code="continueInSignatures" language="java" />

      <p><code>ScopedValue</code> propagates to child threads only through structured concurrency; work submitted to a plain executor does not inherit the current span. <code>continueIn</code> is the bridge: it records a span whose parent is the <em>given</em> context rather than whatever the receiving thread has bound. It mints a <strong>child</strong> and emits a <code>jeffrey.TraceSpan</code> for it — the receiving thread is doing a separate piece of work, which is exactly what distinguishes it from <code>reenter</code>.</p>

      <DocsCodeBlock :code="continueInExample" language="java" />

      <DocsCodeBlock :code="continueInTree" language="text" />

      <h3 id="api-fork">fork / forkCallable — wrap a task for an executor</h3>

      <p><strong>Use it when:</strong> work handed to a plain executor is a separate operation deserving its own named span — parallel chunk parsing, a report rendered on a pool, an <code>@Async</code>-style hand-off.</p>

      <DocsCodeBlock :code="forkSignatures" language="java" />

      <p><code>fork</code> captures the span in progress <em>when it is called</em> — on the submitting thread — and returns a task that, wherever it eventually runs, records its work as a child of that span via <code>continueIn</code>. Wrap on the thread whose span the work belongs to, submit the result. Called outside any span, the task starts a fresh trace.</p>

      <DocsCodeBlock :code="forkExample" language="java" />

      <p>The resulting tree is exactly the one shown for <code>continueIn</code> above — <code>fork</code> is that call, made later, with the capture already done. It exists because the manual two-step had a silent failure mode: forgetting the capture didn't break anything visibly — the forked work just fell out of the trace and became a root of its own. With <code>fork</code> the capture cannot be forgotten, because it <em>is</em> the wrap.</p>

      <h3 id="api-propagating">propagating — an executor that carries the span for you</h3>

      <p><strong>Use it when:</strong> a whole pool serves traced requests and per-call-site wrapping would be noise — the task is the same operation continuing elsewhere, not a separate one worth naming.</p>

      <DocsCodeBlock :code="propagatingSignature" language="java" />

      <p>Wraps the delegate so that every task submitted to it (through <code>execute</code>, <code>submit</code>, <code>invokeAll</code>, <code>invokeAny</code>) runs inside the span in progress on the <em>submitting</em> thread. Distinct from <code>fork</code>: no child span is minted and no name is needed — the context is re-established with <code>reenter</code>, so leaf events emitted inside the task stamp under the submitting span, and each task activation records a <code>jeffrey.TraceScope</code> naming the thread it actually ran on.</p>

      <DocsCodeBlock :code="propagatingExample" language="java" />

      <DocsCodeBlock :code="propagatingTree" language="text" />

      <p>The capture happens per submission, not when the executor is wrapped, so one wrapped pool serves many requests. A task submitted outside any span is handed to the delegate untouched and runs exactly as it would have unwrapped. Spring's <code>@Async</code> methods and scheduled tasks want the same treatment — a <code>TaskDecorator</code> or a wrapped executor is the <code>propagating</code> shape.</p>

      <h2 id="errors">Error Handling</h2>

      <p>An exception escaping any span body marks the span <code>ERROR</code>, records the exception's class name in <code>errorType</code>, and is <strong>rethrown unchanged</strong> — same instance, no wrapping:</p>

      <DocsCodeBlock :code="errorExample" language="java" />

      <p>Three rules worth internalizing:</p>

      <ul>
        <li><strong>Catch inside the body</strong> if a handled failure should not mark the operation failed — a span only turns red for exceptions that <em>escape</em> it.</li>
        <li>Only the exception's <strong>class name</strong> is recorded on the span — never the message or the stack trace. The class name is the one part of an exception worth indexing; the full throw (message, stack, instant) is correlated back into the trace at analysis time from <code>jdk.JavaExceptionThrow</code> — see <router-link to="/docs/tracing/notifications-exceptions">Notifications &amp; Exceptions</router-link>.</li>
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
            <td><code>inSpanOf</code></td>
            <td>Nothing (the event is the span)</td>
            <td>Yes — always</td>
            <td>Still binds: recording is the event's decision</td>
          </tr>
          <tr>
            <td><code>stamp</code></td>
            <td>Nothing (fills the event's ids)</td>
            <td>No</td>
            <td>Stamps whenever a span is bound; no-op otherwise</td>
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
            <td><code>fork</code> / <code>forkCallable</code></td>
            <td><code>jeffrey.TraceSpan</code> (via <code>continueIn</code>, when the task runs)</td>
            <td>Captures at wrap time; binds inside the task</td>
            <td>Same as <code>continueIn</code>: the task still binds, no event</td>
          </tr>
          <tr>
            <td><code>propagating</code></td>
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
            <td><code>run</code> / <code>call</code></td>
          </tr>
          <tr>
            <td>An event of your own already describes the interval, and work nests inside it</td>
            <td><code>inSpanOf</code></td>
          </tr>
          <tr>
            <td>An event describes a self-contained action with nothing nesting inside — a statement, a message</td>
            <td><code>commitSpan()</code> on the event (fold the stamp into the commit); <code>stamp</code> only for a deferred commit</td>
          </tr>
          <tr>
            <td>Work handed to a plain executor — a separate operation on the receiving thread</td>
            <td><code>fork</code> / <code>forkCallable</code> around the task, on the submitting thread</td>
          </tr>
          <tr>
            <td>A whole pool serves traced requests; tasks are the same operation continuing elsewhere</td>
            <td><code>propagating</code> around the pool, once</td>
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

      <h2 id="annotation">Without the Lambda: <code>@Traced</code></h2>

      <p>Every method on this page wraps work in a lambda, which is precise and visible in the code. <code>@Traced</code> declares the same span on a method instead, and the Jeffrey Agent weaves it in — it emits the same <code>jeffrey.TraceSpan</code> as <code>Tracer.call</code>, nests identically, and fails identically. The full reference — every annotation attribute with its output, the agent setup, and the weaving mechanics — is on <router-link to="/docs/tracing/traced-annotation">@Traced &amp; the Jeffrey Agent</router-link>.</p>

      <h2 id="composed-tree">A Complete Tree</h2>

      <p>Jeffrey's own recording-upload flow composes almost every method on this page. An HTTP request roots the trace through <code>inSpanOf</code>, the pipeline stages are <code>call</code>/<code>run</code> spans, chunk parsing and profile-data branches are handed to pools with <code>fork</code>, and every SQL statement along the way is a leaf stamped by <code>commitSpan()</code>:</p>

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
