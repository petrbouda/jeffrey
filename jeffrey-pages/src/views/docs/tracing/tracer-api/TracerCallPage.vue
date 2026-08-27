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
  { id: 'when', text: 'Use It When', level: 2 },
  { id: 'signature', text: 'Signatures', level: 2 },
  { id: 'behavior', text: 'Behavior', level: 2 },
  { id: 'examples', text: 'Examples', level: 2 },
  { id: 'output', text: 'Output', level: 2 },
  { id: 'notes', text: 'Notes & Pitfalls', level: 2 },
  { id: 'related', text: 'Related', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const signatures = `// The body may throw a checked exception, and the thrown type X is
// inferred from the lambda — not wrapped, not erased to Exception
static <R, X extends Throwable>
R call(String name, ScopedValue.CallableOp<? extends R, X> body) throws X

static <R, X extends Throwable>
R call(String name, SpanKind kind, ScopedValue.CallableOp<? extends R, X> body) throws X`;

const examples = `// Use-case 1: a value-returning load — the span times it, the result flows out
Order order = Tracer.call("order.load", () -> repository.load(id));

// Use-case 2: checked exceptions carry through TYPED — a body throwing
// IOException makes this call throw IOException, not a wrapper
byte[] payload = Tracer.call("payload.read", () -> Files.readAllBytes(path));  // throws IOException

// Use-case 3: a pipeline that returns its product (Jeffrey's own profile
// creation) — one call() around the whole operation, one run() per stage
return Tracer.call("profile.initialize", SpanKind.INTERNAL, () -> {
    Tracer.run("profile-info.insert", () -> { … });
    Tracer.run("recording.parse", () -> recordingEventParser.start(eventWriter, recordingPath));
    Tracer.run("events.flush", eventWriter::onComplete);
    Tracer.run("traces.derive", () -> { … });
    Tracer.run("profile.data-init", () -> profileDataInitializer.initialize(profileManager));
    return profileInfo;
});`;

const outputSpans = [
  { depth: 0, name: 'profile.initialize', kind: 'INTERNAL' as const, start: 0, duration: 4102,
    event: 'jeffrey.TraceSpan', note: 'root' },
  { depth: 1, name: 'profile-info.insert', kind: 'INTERNAL' as const, start: 4, duration: 2,
    event: 'jeffrey.TraceSpan' },
  { depth: 1, name: 'recording.parse', kind: 'INTERNAL' as const, start: 10, duration: 2797,
    event: 'jeffrey.TraceSpan' },
  { depth: 1, name: 'events.flush', kind: 'INTERNAL' as const, start: 2812, duration: 212,
    event: 'jeffrey.TraceSpan' },
  { depth: 1, name: 'traces.derive', kind: 'INTERNAL' as const, start: 3026, duration: 158,
    event: 'jeffrey.TraceSpan' },
  { depth: 1, name: 'profile.data-init', kind: 'INTERNAL' as const, start: 3186, duration: 933,
    event: 'jeffrey.TraceSpan' }
];

const errorExample = `IllegalStateException thrown = assertThrows(IllegalStateException.class,
    () -> Tracer.call("payment.charge", SpanKind.CLIENT, () -> {
        throw new IllegalStateException("card declined");
    }));

// The span is still recorded:
//   name      = "payment.charge"
//   status    = ERROR
//   errorType = "java.lang.IllegalStateException"
// and the exception is rethrown unchanged — same instance, no wrapping.`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Tracer.call"
      icon="bi bi-arrow-return-left"
    />

    <div class="docs-content">
      <p>Records a span around a value-returning block of work. Identical to <router-link to="/docs/tracing/tracer-api/run">run</router-link> in every span semantic — it only adds the result, and lets checked exceptions flow through with their real type.</p>

      <h2 id="when">Use It When</h2>

      <p>The block being timed produces a value the caller needs, or throws a checked exception you want to keep typed. For pure side effects, <router-link to="/docs/tracing/tracer-api/run">run</router-link> reads better.</p>

      <h2 id="signature">Signatures</h2>

      <DocsCodeBlock :code="signatures" language="java" />

      <p>The body type is <code>ScopedValue.CallableOp</code> rather than <code>Callable</code> on purpose: <code>CallableOp</code> carries the thrown type as a type variable, so a body that throws <code>IOException</code> makes the whole <code>call</code> throw <code>IOException</code> — no wrapping into <code>Exception</code>, no unchecked rethrow tricks.</p>

      <h2 id="behavior">Behavior</h2>

      <ul>
        <li>Opens a span whose parent is whatever span is bound on the current thread — or a <strong>fresh root</strong> when none is.</li>
        <li>Runs the body with the new context bound; returns the body's result.</li>
        <li>Emits one <code>jeffrey.TraceSpan</code> when the body completes.</li>
        <li>An exception escaping the body — checked or unchecked — marks the span <code>ERROR</code> with the exception's class name and is <strong>rethrown unchanged</strong>.</li>
        <li>With the event type disabled, the body runs directly and its result is returned — no binding, no event.</li>
      </ul>

      <h2 id="examples">Examples</h2>

      <DocsCodeBlock :code="examples" language="java" />

      <p>The failure path:</p>

      <DocsCodeBlock :code="errorExample" language="java" />

      <h2 id="output">Output</h2>

      <DocsSpanTree trace="c81d02aa…" :spans="outputSpans" />

      <h2 id="notes">Notes &amp; Pitfalls</h2>

      <ul>
        <li><strong>A body throwing two checked exception types</strong> (say <code>IOException</code> and <code>ServletException</code>) infers their common supertype — often <code>Exception</code>. Narrow it back at the call site with a multi-catch that rethrows the concrete types (the <router-link to="/docs/tracing/http-events">hand-written HTTP filter</router-link> shows the pattern).</li>
        <li><strong>Everything on the <router-link to="/docs/tracing/tracer-api/run">run</router-link> page applies here too</strong>: operations not methods, low-cardinality names, no wrapping of inbound requests, no free executor crossing.</li>
      </ul>

      <DocsCallout type="tip">
        <code>@Traced</code> on a method emits byte-for-byte the same <code>jeffrey.TraceSpan</code> as <code>Tracer.call</code> — pick by whether you prefer the span visible in the code or the method left untouched. See <router-link to="/docs/tracing/traced-annotation">@Traced &amp; the Agent</router-link>.
      </DocsCallout>

      <h2 id="related">Related</h2>

      <ul>
        <li><router-link to="/docs/tracing/tracer-api/run">run</router-link> — the side-effecting twin.</li>
        <li><router-link to="/docs/tracing/tracer-api/fork-callable">forkCallable</router-link> — a value-returning task handed to an executor.</li>
        <li><router-link to="/docs/tracing/instrumentation">Instrumentation Overview</router-link> — semantics table and the method-choosing guide.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
