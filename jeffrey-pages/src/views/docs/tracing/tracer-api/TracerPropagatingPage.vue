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

const signature = `static ExecutorService propagating(ExecutorService delegate)`;

const examples = `// Wrap the POOL once, at construction …
ExecutorService executor = Tracer.propagating(Executors.newFixedThreadPool(8));

// … and every task submitted inside a span runs inside that span — no
// per-call-site wrapping, no name, no child span:
executor.submit(() -> parseChunk(file));      // leaf events inside stamp under the request

// A task that deserves its own NAMED bar in the waterfall still wraps
// itself — the two compose:
executor.submit(Tracer.fork("chunk.parse", () -> parseChunk(file)));

// Spring @Async / scheduled tasks: hand Spring the wrapped executor, and
// every @Async method invoked inside a span runs inside it
@Bean
public Executor taskExecutor() {
    return Tracer.propagating(Executors.newFixedThreadPool(8));
}`;

const outputTree = `trace 77b2e9c1…
└─ POST /api/internal/recordings       SERVER          (request thread)
   ├─ insert_chunk        JdbcInsertEvent  CLIENT      (pool thread A — stamped under the request)
   ├─ insert_chunk        JdbcInsertEvent  CLIENT      (pool thread B)
   └─ chunk.parse         jeffrey.TraceSpan INTERNAL   (pool thread C — the fork-wrapped task)

plus one jeffrey.TraceScope per plain task activation, naming the pool
thread it ran on — propagating re-enters the submitting span, it does
not mint children.`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Tracer.propagating"
      icon="bi bi-diagram-3"
    />

    <div class="docs-content">
      <p>Wraps an <code>ExecutorService</code> so that every task submitted to it runs inside the span in progress on the <em>submitting</em> thread — the executor-wide form of capture-and-re-establish, for the pool that serves traced requests wholesale.</p>

      <h2 id="when">Use It When</h2>

      <p>A whole pool serves traced requests and per-call-site wrapping would be noise — the tasks are the <em>same operation continuing elsewhere</em>, not separate operations worth naming. When a task <em>is</em> a distinct operation, wrap that task with <router-link to="/docs/tracing/tracer-api/fork">fork</router-link>/<router-link to="/docs/tracing/tracer-api/fork-callable">forkCallable</router-link>; the two compose.</p>

      <h2 id="signature">Signature</h2>

      <DocsCodeBlock :code="signature" language="java" />

      <h2 id="behavior">Behavior</h2>

      <ul>
        <li>Intercepts every submission path — <code>execute</code>, <code>submit</code>, <code>invokeAll</code>, <code>invokeAny</code> — and captures the submitting thread's <code>SpanContext</code> <strong>per submission</strong>, not when the executor is wrapped, so one wrapped pool serves many requests.</li>
        <li>Inside the task, the context is re-established with <router-link to="/docs/tracing/tracer-api/reenter">reenter</router-link>: <strong>no child span is minted and no name is needed</strong> — leaf events emitted by the task stamp under the submitting span, and each activation records one <code>jeffrey.TraceScope</code> naming the thread it actually ran on.</li>
        <li>A task submitted <em>outside</em> any span is handed to the delegate untouched and runs exactly as it would have unwrapped.</li>
        <li>The wrapper delegates lifecycle methods (<code>shutdown</code>, <code>awaitTermination</code>, …) to the underlying pool.</li>
      </ul>

      <h2 id="examples">Examples</h2>

      <DocsCodeBlock :code="examples" language="java" />

      <h2 id="output">Output</h2>

      <DocsCodeBlock :code="outputTree" language="text" />

      <h2 id="notes">Notes &amp; Pitfalls</h2>

      <ul>
        <li><strong>No bar appears for a plain propagated task</strong> — that is the point. If you look for the task in the waterfall and want to see it, it was a separate operation after all: wrap it with <code>fork</code>.</li>
        <li><strong>Choose against <code>fork</code> by intent, not convenience</strong>: <code>propagating</code> answers "whose work was this pool doing?"; <code>fork</code> answers "what named operation ran there?".</li>
        <li><strong>Long-lived queued tasks re-enter a span that may have ended.</strong> Legal, and the scope records where the tail ran — but a task that routinely outlives its request is usually its own operation (or its own trace) rather than a continuation.</li>
        <li><strong>Scope volume</strong>: each activation emits a <code>jeffrey.TraceScope</code>; on a hot pool, control it per recording with <code>TraceScopeEvent#enabled=false</code> — see <router-link to="/docs/tracing/configuration">Configuration</router-link>.</li>
      </ul>

      <DocsCallout type="tip">
        A Spring <code>TaskDecorator</code> that captures <code>Tracer.current()</code> and re-enters in the task is the same shape when you cannot replace the executor itself.
      </DocsCallout>

      <h2 id="related">Related</h2>

      <ul>
        <li><router-link to="/docs/tracing/tracer-api/reenter">reenter</router-link> — what each task activation does under the hood.</li>
        <li><router-link to="/docs/tracing/tracer-api/fork">fork</router-link> / <router-link to="/docs/tracing/tracer-api/fork-callable">forkCallable</router-link> — per-task named child spans; compose with this executor.</li>
        <li><router-link to="/docs/tracing/instrumentation">Instrumentation Overview</router-link> — semantics table and the method-choosing guide.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
