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

const signatures = `static <R, X extends Throwable>
R continueIn(SpanContext parent, String name, SpanKind kind,
             ScopedValue.CallableOp<R, X> body) throws X

static <R, X extends Throwable>
R continueIn(SpanContext parent, String name,
             ScopedValue.CallableOp<R, X> body) throws X          // INTERNAL

static void continueIn(SpanContext parent, String name, SpanKind kind, Runnable body)
static void continueIn(SpanContext parent, String name, Runnable body)   // INTERNAL

// Passing null as the parent starts a fresh trace.`;

const manualExample = `// The manual executor form: capture the context on the submitting thread,
// re-establish it inside the task. fork packages exactly this pattern.
SpanContext parent = Tracer.current().orElse(null);

executor.submit(() ->
    Tracer.continueIn(parent, "chunk.parse", SpanKind.INTERNAL, () -> parseChunk(chunk)));`;

const storedExample = `// The case fork cannot cover: the context arrives from somewhere other
// than the submitting site — stored on a message, read back by a consumer
record IndexRequest(String documentId, SpanContext trace) { }

// producer, inside the request's span:
queue.add(new IndexRequest(doc.id(), Tracer.current().orElse(null)));

// consumer, on its own thread, possibly much later:
IndexRequest req = queue.take();
Tracer.continueIn(req.trace(), "document.index", () -> {
    index(req.documentId());
    return null;
});`;

const outputSpans = [
  { depth: 0, name: 'POST /api/internal/recordings', kind: 'SERVER' as const,
    start: 0, duration: 4400, note: 'request thread' },
  { depth: 1, name: 'profile.initialize', kind: 'INTERNAL' as const,
    start: 150, duration: 4102, note: 'request thread' },
  { depth: 2, name: 'recording.parse', kind: 'INTERNAL' as const,
    start: 175, duration: 2797, note: 'request thread' },
  { depth: 3, name: 'chunk.parse', kind: 'INTERNAL' as const,
    start: 200, duration: 2730, note: 'pool thread A — continueIn' },
  { depth: 3, name: 'chunk.parse', kind: 'INTERNAL' as const,
    start: 215, duration: 2571, note: 'pool thread B — continueIn' }
];
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Tracer.continueIn"
      icon="bi bi-signpost-split"
    />

    <div class="docs-content">
      <p>Records a span whose parent is an <em>explicitly given</em> context rather than whatever the current thread has bound — the primitive that carries a trace across an executor.</p>

      <h2 id="when">Use It When</h2>

      <p>The parent context comes from somewhere other than the wrapping site — stored on a request object, carried by a queue message, handed over by a protocol. For the common case where you capture and submit in the same place, use <router-link to="/docs/tracing/tracer-api/fork">fork</router-link>/<router-link to="/docs/tracing/tracer-api/fork-callable">forkCallable</router-link>, which package the capture and cannot forget it.</p>

      <h2 id="signature">Signatures</h2>

      <DocsCodeBlock :code="signatures" language="java" />

      <h2 id="behavior">Behavior</h2>

      <ul>
        <li><code>ScopedValue</code> propagates only through structured concurrency; work submitted to a plain executor does not inherit the current span. <code>continueIn</code> is the bridge: it parents the new span to the <em>given</em> context, whatever the receiving thread has bound.</li>
        <li>Mints a <strong>child</strong> span and emits one <code>jeffrey.TraceSpan</code> for it — the receiving thread is doing a separate piece of work, which is exactly what distinguishes it from <router-link to="/docs/tracing/tracer-api/reenter">reenter</router-link>.</li>
        <li><code>null</code> parent → a fresh root in a fresh trace — the untraced-caller fallback.</li>
        <li>Failure semantics are <router-link to="/docs/tracing/tracer-api/run">run</router-link>'s: an escaping exception marks the span <code>ERROR</code> and is rethrown unchanged.</li>
        <li><strong>Still binds even when nothing is recording</strong> — the handed-over context is the only link to the trace, and dropping it would orphan every leaf stamped underneath.</li>
      </ul>

      <h2 id="examples">Examples</h2>

      <DocsCodeBlock :code="manualExample" language="java" />
      <DocsCodeBlock :code="storedExample" language="java" />

      <h2 id="output">Output</h2>

      <DocsSpanTree
        trace="9d02f7c3…"
        :spans="outputSpans"
        caption="Passing null instead of a parent context starts a fresh trace: chunk.parse becomes a root of its own, unrelated to the request."
      />

      <h2 id="notes">Notes &amp; Pitfalls</h2>

      <ul>
        <li><strong>Capture on the owning thread.</strong> The classic bug is calling <code>Tracer.current()</code> <em>inside</em> the submitted task — that reads the pool thread's (empty) binding and every task becomes its own root. Capture before submitting; or use <code>fork</code>, where the wrap <em>is</em> the capture.</li>
        <li><strong>Same span or child?</strong> If the receiving thread continues the <em>same</em> operation (a callback of one call), <router-link to="/docs/tracing/tracer-api/reenter">reenter</router-link> is right; if it performs a separate operation worth its own bar, <code>continueIn</code> is.</li>
        <li><strong>A deliberately fresh trace is sometimes correct</strong>: a background job whose lifetime is unrelated to the triggering request should pass <code>null</code> (or root itself with <code>inSpanOf</code>) rather than nest under a request that will finish long before it.</li>
        <li><strong>The stored context is plain data</strong> — three longs. It serializes trivially if a hand-off crosses a queue, but remember Jeffrey traces are single-JVM: a context carried to another process yields ids the receiving recording knows nothing about.</li>
      </ul>

      <DocsCallout type="tip">
        For a whole pool that serves traced requests where the tasks are <em>not</em> separate operations, wrap the pool once with <router-link to="/docs/tracing/tracer-api/propagating">propagating</router-link> instead of naming a child span per task.
      </DocsCallout>

      <h2 id="related">Related</h2>

      <ul>
        <li><router-link to="/docs/tracing/tracer-api/fork">fork</router-link> / <router-link to="/docs/tracing/tracer-api/fork-callable">forkCallable</router-link> — this call with the capture packaged in.</li>
        <li><router-link to="/docs/tracing/tracer-api/current">current</router-link> — how the parent context is captured.</li>
        <li><router-link to="/docs/tracing/tracer-api/reenter">reenter</router-link> — resumes the same span instead of minting a child.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
