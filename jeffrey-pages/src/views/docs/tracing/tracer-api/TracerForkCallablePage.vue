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

const signatures = `static <T> Callable<T> forkCallable(String name, SpanKind kind, Callable<T> body)
static <T> Callable<T> forkCallable(String name, Callable<T> body)     // INTERNAL`;

const examples = `// Use-case 1: a typed Future from ExecutorService.submit
Future<Report> report = executor.submit(
        Tracer.forkCallable("report.render", () -> render(part)));

// Use-case 2: invokeAll — wrap each part on the submitting thread
List<Callable<ChunkResult>> tasks = chunks.stream()
        .map(chunk -> Tracer.forkCallable("chunk.parse", () -> parse(chunk)))
        .toList();
List<Future<ChunkResult>> results = executor.invokeAll(tasks);

// Use-case 3: a checked-exception-throwing body — Callable declares
// throws Exception, so no wrapping is needed at the wrap site
Future<byte[]> payload = executor.submit(
        Tracer.forkCallable("payload.read", () -> Files.readAllBytes(path)));`;

const outputTree = `trace 41c9ab77…
└─ GET /api/reports/{id}          SERVER               (request thread)
   ├─ report.render               INTERNAL             (pool thread A)  ← forkCallable
   ├─ report.render               INTERNAL             (pool thread B)
   └─ report.merge                INTERNAL             (request thread)

Each task emits one jeffrey.TraceSpan when it runs, parented to the span
in progress where forkCallable() was CALLED.`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Tracer.forkCallable"
      icon="bi bi-sign-intersection-y"
    />

    <div class="docs-content">
      <p>The <code>Callable</code> form of <router-link to="/docs/tracing/tracer-api/fork">fork</router-link> — shaped for <code>ExecutorService.submit</code> and <code>invokeAll</code>, the common typed-result hand-off that neither the <code>Runnable</code> nor the <code>Supplier</code> form fits.</p>

      <h2 id="when">Use It When</h2>

      <p>Exactly the <router-link to="/docs/tracing/tracer-api/fork">fork</router-link> situation — a separate operation handed to a plain executor — but the receiving API wants a <code>Callable</code>: <code>ExecutorService.submit(Callable)</code>, <code>invokeAll</code>, <code>invokeAny</code>, or a body that throws checked exceptions.</p>

      <h2 id="signature">Signatures</h2>

      <DocsCodeBlock :code="signatures" language="java" />

      <DocsCallout type="info">
        <strong>Why a distinct name instead of another <code>fork</code> overload?</strong> A result-bearing lambda matches <code>Supplier</code> and <code>Callable</code> alike — an overload would make every existing <code>fork(name, () -&gt; value)</code> call ambiguous. The separate name keeps both forms usable without casts.
      </DocsCallout>

      <h2 id="behavior">Behavior</h2>

      <ul>
        <li>Identical to <code>fork</code> in every span semantic: <strong>captures the span in progress at wrap time</strong>, on the submitting thread; the returned <code>Callable</code> runs the body through <code>continueIn</code> with that captured parent and emits one <code>jeffrey.TraceSpan</code>.</li>
        <li>The body's checked exceptions propagate out of the returned <code>Callable</code> as themselves (surfaced through the <code>Future</code> as an <code>ExecutionException</code> cause, exactly as an unwrapped task would).</li>
        <li>Called outside any span, the task starts a fresh trace. Kind defaults to <code>INTERNAL</code>.</li>
      </ul>

      <h2 id="examples">Examples</h2>

      <DocsCodeBlock :code="examples" language="java" />

      <h2 id="output">Output</h2>

      <DocsCodeBlock :code="outputTree" language="text" />

      <h2 id="notes">Notes &amp; Pitfalls</h2>

      <ul>
        <li>Every note on the <router-link to="/docs/tracing/tracer-api/fork">fork</router-link> page applies unchanged: wrap on the owning thread, wrap per submission, and compose freely with <router-link to="/docs/tracing/tracer-api/propagating">propagating</router-link>.</li>
        <li><strong>A failed task still records its span</strong>: the exception escaping the body marks the forked span <code>ERROR</code> with the exception's class name before it travels into the <code>Future</code>.</li>
      </ul>

      <h2 id="related">Related</h2>

      <ul>
        <li><router-link to="/docs/tracing/tracer-api/fork">fork</router-link> — the <code>Runnable</code>/<code>Supplier</code> forms.</li>
        <li><router-link to="/docs/tracing/tracer-api/call">call</router-link> — the in-place value-returning span, no executor involved.</li>
        <li><router-link to="/docs/tracing/tracer-api/continue-in">continueIn</router-link> — the underlying primitive.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
