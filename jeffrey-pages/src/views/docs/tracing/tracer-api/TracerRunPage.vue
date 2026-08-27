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
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
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

const signatures = `static void run(String name, Runnable body)                    // kind = INTERNAL
static void run(String name, SpanKind kind, Runnable body)`;

const examples = `// Use-case 1: time a domain operation inside a request — the span nests
// under whatever span is in progress on this thread (the HTTP request,
// a job, a parent run), with nothing threaded through the call chain
Tracer.run("cart.validate", () -> validator.validate(cart));

// Use-case 2: an outbound wait that has no dedicated event type yet
Tracer.run("payment.charge", SpanKind.CLIENT, () -> paymentGateway.charge(order));

// Use-case 3: a pipeline — one bar per stage in the waterfall
Tracer.run("report.generate", () -> {
    Tracer.run("report.load-data", this::loadData);
    Tracer.run("report.render", this::render);
    Tracer.run("report.store", this::store);
});

// Use-case 4: catch INSIDE the body when a handled failure should NOT
// mark the operation as failed — a span only turns red for exceptions
// that escape it
Tracer.run("cache.warm", () -> {
    try {
        cache.preload();
    } catch (CacheUnavailableException e) {
        LOG.warn("Cache warmup skipped", e);   // span stays UNSET — this is fine
    }
});`;

const outputTree = `trace 7c01ba58…
└─ report.generate       INTERNAL  parentSpanId=0    jeffrey.TraceSpan   412 ms
   ├─ report.load-data   INTERNAL                    jeffrey.TraceSpan   181 ms
   ├─ report.render      INTERNAL                    jeffrey.TraceSpan   204 ms
   └─ report.store       INTERNAL                    jeffrey.TraceSpan    22 ms`;

const outputJfr = `jfr print --events jeffrey.TraceSpan app.jfr

jeffrey.TraceSpan {
  duration = 204 ms
  traceId = 8964370214175523801
  spanId = 2411087615529001143
  parentSpanId = 6105938220997569912   // nested under report.generate
  name = "report.render"
  kind = "INTERNAL"
  status = "UNSET"
}`;

const errorExample = `Tracer.run("payment.charge", SpanKind.CLIENT, () -> {
    throw new IllegalStateException("card declined");
});

// The span is still recorded:
//   status    = ERROR
//   errorType = "java.lang.IllegalStateException"
// and the exception reaches the caller unchanged — same instance, no wrapping.`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Tracer.run"
      icon="bi bi-play-circle"
    />

    <div class="docs-content">
      <p>Records a span around a side-effecting block of work. The workhorse of the API — if in doubt, start here.</p>

      <h2 id="when">Use It When</h2>

      <p>A block of in-process work is worth timing — a pipeline stage, a domain operation, a computation — and the body returns nothing. For a body that returns a value (or throws a checked exception you want to keep typed), use <router-link to="/docs/tracing/tracer-api/call">call</router-link>, which is otherwise identical.</p>

      <h2 id="signature">Signatures</h2>

      <DocsCodeBlock :code="signatures" language="java" />

      <h2 id="behavior">Behavior</h2>

      <ul>
        <li>Opens a span whose parent is whatever span is bound on the current thread — or a <strong>fresh root</strong> when none is.</li>
        <li>Runs the body with the new <code>SpanContext</code> bound through the <code>ScopedValue</code>, so anything traced inside nests under it.</li>
        <li>Emits one <code>jeffrey.TraceSpan</code> when the body completes.</li>
        <li>An exception escaping the body marks the span <code>ERROR</code> with the exception's class name and is <strong>rethrown unchanged</strong>.</li>
        <li>With the event type disabled (nothing recording), the body runs directly — no binding, no event, no allocation that survives escape analysis.</li>
      </ul>

      <h2 id="examples">Examples</h2>

      <DocsCodeBlock :code="examples" language="java" />

      <p>The failure path:</p>

      <DocsCodeBlock :code="errorExample" language="java" />

      <h2 id="output">Output</h2>

      <DocsCodeBlock :code="outputTree" language="text" />
      <DocsCodeBlock :code="outputJfr" language="text" />

      <h2 id="notes">Notes &amp; Pitfalls</h2>

      <ul>
        <li><strong>Instrument operations, not methods.</strong> <code>order.checkout</code>, <code>inventory.reserve</code>, <code>report.render</code> — a handful of meaningful spans per request beats hundreds of one-per-method spans.</li>
        <li><strong>Names must be stable and low-cardinality</strong>: name the operation, never the instance (<code>order.checkout</code>, not <code>order.checkout.a3f9c1</code>). Every distinct name enters the JFR per-chunk constant pool, and high-cardinality names shatter <router-link to="/docs/tracing/analysis">Traces by Operation</router-link>.</li>
        <li><strong>Do not wrap an inbound request with <code>run</code></strong> when a request event already describes the interval — that records the same interval twice. Root the request with <router-link to="/docs/tracing/tracer-api/in-span-of">inSpanOf</router-link> instead.</li>
        <li><strong>The span does not cross a plain executor.</strong> Work submitted from inside the body to a pool falls out of the trace unless handed over with <router-link to="/docs/tracing/tracer-api/fork">fork</router-link>, <router-link to="/docs/tracing/tracer-api/continue-in">continueIn</router-link> or a <router-link to="/docs/tracing/tracer-api/propagating">propagating</router-link> executor.</li>
      </ul>

      <h2 id="related">Related</h2>

      <ul>
        <li><router-link to="/docs/tracing/tracer-api/call">call</router-link> — the value-returning twin, with typed checked exceptions.</li>
        <li><router-link to="/docs/tracing/tracer-api/in-span-of">inSpanOf</router-link> — when an event of your own already describes the interval.</li>
        <li><router-link to="/docs/tracing/instrumentation">Instrumentation Overview</router-link> — semantics table and the method-choosing guide.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
