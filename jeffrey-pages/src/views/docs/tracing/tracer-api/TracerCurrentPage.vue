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
  { id: 'when', text: 'Use It When', level: 2 },
  { id: 'signature', text: 'Signature', level: 2 },
  { id: 'behavior', text: 'Behavior', level: 2 },
  { id: 'examples', text: 'Examples', level: 2 },
  { id: 'output', text: 'Output', level: 2 },
  { id: 'notes', text: 'Notes & Pitfalls', level: 2 },
  { id: 'related', text: 'Related', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const signature = `static Optional<SpanContext> current()

// SpanContext is the immutable value the ScopedValue carries:
public record SpanContext(long traceId, long spanId, long parentSpanId) { … }`;

const examples = `// Use-case 1: correlation ids in logs — print the trace id beside a log line,
// so a log entry can be matched to the trace it belongs to
Tracer.current().ifPresent(ctx ->
        MDC.put("traceId", Long.toHexString(ctx.traceId())));

// Use-case 2: hand the context somewhere the wrapping site cannot reach —
// stored on a request object, picked up later by continueIn
SpanContext parent = Tracer.current().orElse(null);
request.attachTraceContext(parent);
// … elsewhere, later, on another thread:
Tracer.continueIn(request.traceContext(), "request.finalize", () -> {
    finalize(request);
    return null;
});

// Use-case 3: pin an instant to the span that owns the work, before
// handing the work to a pool thread (ids already set are never overwritten
// by emit())
SpanContext owner = Tracer.current().orElse(null);
pool.submit(() -> {
    NotificationEvent n = new NotificationEvent();
    if (owner != null) {
        n.traceId = owner.traceId();
        n.enclosingSpanId = owner.spanId();
    }
    n.type = "RETRY_SCHEDULED";
    n.emit();
});`;

const outputExample = `// current() emits nothing — it only reads. Inside a span:
Tracer.run("order.checkout", () ->
        System.out.println(Tracer.current()));
// Optional[SpanContext[traceId=6872570733206835563,
//                      spanId=4444722480460712002, parentSpanId=0]]

// Outside any span:
System.out.println(Tracer.current());
// Optional.empty`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Tracer.current"
      icon="bi bi-crosshair"
    />

    <div class="docs-content">
      <p>Reads the span in progress on this thread — the one method that observes the trace without touching it.</p>

      <h2 id="when">Use It When</h2>

      <p>Something outside the tracing tree needs the identity of the span in progress: a correlation id in logs, a context stored for a later <router-link to="/docs/tracing/tracer-api/continue-in">continueIn</router-link>, or an instant that must be pinned to the owning span before the work moves to a foreign thread. For the common executor hand-off, prefer <router-link to="/docs/tracing/tracer-api/fork">fork</router-link>, which does the capture itself.</p>

      <h2 id="signature">Signature</h2>

      <DocsCodeBlock :code="signature" language="java" />

      <h2 id="behavior">Behavior</h2>

      <ul>
        <li>Returns the <code>SpanContext</code> bound on this thread, or <code>Optional.empty()</code> when none is.</li>
        <li>Never binds, never emits — purely a read.</li>
        <li>The returned record is immutable and safe to store or carry across threads; deriving children from it never mutates it.</li>
      </ul>

      <h2 id="examples">Examples</h2>

      <DocsCodeBlock :code="examples" language="java" />

      <h2 id="output">Output</h2>

      <DocsCodeBlock :code="outputExample" language="java" />

      <h2 id="notes">Notes &amp; Pitfalls</h2>

      <ul>
        <li><strong>Empty is normal, not an error</strong> — code paths run traced and untraced alike. Treat the empty case as "no correlation available", never as something to throw on.</li>
        <li><strong>A stored context does not keep the span alive.</strong> The context is just three ids; the span event commits when its own lifecycle says so. Nesting under a context whose span has already ended is legal and renders correctly — the parent's bar simply ends earlier.</li>
        <li><strong>Capture on the right thread.</strong> <code>current()</code> reads <em>this</em> thread's binding — calling it inside the submitted task (on the pool thread) reads the pool thread's binding, which is exactly the mistake <router-link to="/docs/tracing/tracer-api/fork">fork</router-link> exists to prevent.</li>
      </ul>

      <DocsCallout type="tip">
        Ids are 64-bit longs; render them as hex (<code>Long.toHexString</code>) when logging — that matches how Jeffrey's UI displays them, so a log line and a waterfall can be eyeballed against each other.
      </DocsCallout>

      <h2 id="related">Related</h2>

      <ul>
        <li><router-link to="/docs/tracing/tracer-api/continue-in">continueIn</router-link> — consumes a captured context to mint a child on another thread.</li>
        <li><router-link to="/docs/tracing/tracer-api/fork">fork</router-link> — packages capture-then-continueIn for the common submit-site case.</li>
        <li><router-link to="/docs/tracing/instrumentation">Instrumentation Overview</router-link> — semantics table and the method-choosing guide.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
