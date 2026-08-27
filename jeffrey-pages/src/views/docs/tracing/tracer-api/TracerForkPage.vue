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

const signatures = `static Runnable fork(String name, SpanKind kind, Runnable body)
static Runnable fork(String name, Runnable body)                       // INTERNAL

static <T> Supplier<T> fork(String name, SpanKind kind, Supplier<T> body)
static <T> Supplier<T> fork(String name, Supplier<T> body)             // INTERNAL`;

const examples = `// Use-case 1: CompletableFuture — the Supplier form hands straight to
// supplyAsync (from Jeffrey's parallel JFR-chunk parsing)
return CompletableFuture.supplyAsync(
        Tracer.fork("chunk.parse",
                () -> singleFileIterator.apply(recording).partialCollect(collector)),
        Schedulers.sharedBulkParallel());

// Use-case 2: fire-and-forget — the Runnable form
CompletableFuture.runAsync(
        Tracer.fork("guardian.results",
                () -> profileManager.guardianManager().guardResults()),
        executor);

// Use-case 3: fan-out — wrap each part on the submitting thread, submit them all
List<CompletableFuture<Void>> parts = chunks.stream()
        .map(chunk -> CompletableFuture.runAsync(
                Tracer.fork("chunk.parse", () -> parseChunk(chunk)),
                executor))
        .toList();`;

const outputTree = `trace 9d02f7c3…
└─ POST /api/internal/recordings   SERVER              (request thread)
   └─ recording.parse              INTERNAL            (request thread)
      ├─ chunk.parse               INTERNAL            (pool thread A)  ← the fork-wrapped task
      └─ chunk.parse               INTERNAL            (pool thread B)

Each task emits one jeffrey.TraceSpan when it runs, parented to the span
that was in progress where fork() was CALLED — not where the task ran.`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Tracer.fork"
      icon="bi bi-sign-intersection"
    />

    <div class="docs-content">
      <p>Wraps a task so that, wherever it eventually runs, it is recorded as a named child of the span in progress <em>here</em> — the packaged form of capture-then-<router-link to="/docs/tracing/tracer-api/continue-in">continueIn</router-link> for the executor hand-off.</p>

      <h2 id="when">Use It When</h2>

      <p>Work handed to a plain executor is a <em>separate operation</em> deserving its own named span — parallel chunk parsing, a report rendered on a pool, an <code>@Async</code>-style hand-off. For a typed <code>Future</code> via <code>ExecutorService.submit</code>, use <router-link to="/docs/tracing/tracer-api/fork-callable">forkCallable</router-link>; for tasks that are the <em>same</em> operation continuing elsewhere, wrap the pool once with <router-link to="/docs/tracing/tracer-api/propagating">propagating</router-link> instead.</p>

      <h2 id="signature">Signatures</h2>

      <DocsCodeBlock :code="signatures" language="java" />

      <h2 id="behavior">Behavior</h2>

      <ul>
        <li><strong>Captures the span in progress when <code>fork</code> is called</strong> — on the submitting thread — and returns a task that runs the body through <code>continueIn</code> with that captured parent.</li>
        <li>The task emits one <code>jeffrey.TraceSpan</code> when it runs; the <code>Supplier</code> form hands straight to <code>CompletableFuture.supplyAsync</code>.</li>
        <li>Called outside any span, the returned task starts a fresh trace — the same fallback <code>continueIn</code> has for a <code>null</code> parent.</li>
        <li>Kind defaults to <code>INTERNAL</code> — forked work is in-process work unless declared otherwise.</li>
      </ul>

      <p>This method exists because the manual two-step had a silent failure mode: forgetting the capture didn't break anything visibly — the forked work just fell out of the trace and became a root of its own. With <code>fork</code> the capture cannot be forgotten, because it <em>is</em> the wrap.</p>

      <h2 id="examples">Examples</h2>

      <DocsCodeBlock :code="examples" language="java" />

      <h2 id="output">Output</h2>

      <DocsCodeBlock :code="outputTree" language="text" />

      <h2 id="notes">Notes &amp; Pitfalls</h2>

      <ul>
        <li><strong>Wrap on the thread whose span the work belongs to, then submit the result.</strong> Wrapping inside the task — or in a lambda that only runs on the pool — captures the wrong (usually empty) binding.</li>
        <li><strong>A wrapped task is reusable but its parent is fixed</strong>: the capture happened at wrap time. Re-submitting the same wrapped task from a different request still parents it to the original request. Wrap per submission.</li>
        <li><strong><code>@Async</code> methods and scheduled tasks</strong> need the same treatment — a Spring <code>TaskDecorator</code> or a wrapped executor is the <router-link to="/docs/tracing/tracer-api/propagating">propagating</router-link> shape; a per-call wrap is this method.</li>
        <li><strong>Composes with <code>propagating</code></strong>: a fork-wrapped task submitted through a propagating executor gets its own named child span — the outer re-entry and the inner child do not conflict.</li>
      </ul>

      <DocsCallout type="tip">
        The waterfall draws forked children with their own thread — which is also what gives them working per-span flamegraphs even when the parent ran on a virtual thread. See <router-link to="/docs/tracing/analysis">Analyzing Traces → Spans on Virtual Threads</router-link>.
      </DocsCallout>

      <h2 id="related">Related</h2>

      <ul>
        <li><router-link to="/docs/tracing/tracer-api/fork-callable">forkCallable</router-link> — the <code>Callable</code> form for <code>ExecutorService.submit</code>.</li>
        <li><router-link to="/docs/tracing/tracer-api/continue-in">continueIn</router-link> — the underlying primitive, for contexts that travel further than the wrapping site.</li>
        <li><router-link to="/docs/tracing/tracer-api/propagating">propagating</router-link> — executor-wide propagation without child spans.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
