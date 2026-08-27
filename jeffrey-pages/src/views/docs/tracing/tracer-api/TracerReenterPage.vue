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

const signatures = `static <R, X extends Throwable>
R reenter(SpanContext context, ScopedValue.CallableOp<R, X> body) throws X

static void reenter(SpanContext context, Runnable body)`;

const grpcExample = `// Every listener callback of a gRPC call re-enters the call's span:
SpanContext span = Tracer.openSpanOf(event);

return new SimpleForwardingServerCallListener<>(listener) {
    @Override
    public void onMessage(ReqT message) {
        Tracer.reenter(span, () -> super.onMessage(message));
    }

    @Override
    public void onHalfClose() {
        Tracer.reenter(span, () -> super.onHalfClose());
    }
};

// Work the handler does inside a re-entered callback nests under the call:
// a JDBC statement issued from onHalfClose stamps under the exchange's span.`;

const outputExample = `// Each re-entry emits one jeffrey.TraceScope — not part of the span tree,
// but the only honest record of where the span actually ran:

jeffrey.TraceScope {
  duration = 0.4 ms
  traceId = 5039859549689708600
  scopedSpanId = 6533423119469147918     // the re-entered span
  eventThread = "grpc-default-executor-0"   (onMessage)
}
jeffrey.TraceScope {
  duration = 17.8 ms
  traceId = 5039859549689708600
  scopedSpanId = 6533423119469147918
  eventThread = "grpc-default-executor-2"   (onHalfClose — the handler ran here)
}`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Tracer.reenter"
      icon="bi bi-arrow-repeat"
    />

    <div class="docs-content">
      <p>Re-establishes an already-open span around a block of work — the <em>same</em> span, not a child. The completing half of the callback pattern opened by <router-link to="/docs/tracing/tracer-api/open-span-of">openSpanOf</router-link>.</p>

      <h2 id="when">Use It When</h2>

      <p>A span's work arrives back in pieces — protocol callbacks, async completions — and each piece belongs to the <em>same operation</em>, not a separate one. That is the whole distinction from <router-link to="/docs/tracing/tracer-api/continue-in">continueIn</router-link>: <code>reenter</code> resumes, <code>continueIn</code> mints a child. Pick by whether the receiving thread is doing a separate piece of work.</p>

      <h2 id="signature">Signatures</h2>

      <DocsCodeBlock :code="signatures" language="java" />

      <h2 id="behavior">Behavior</h2>

      <ul>
        <li>Binds the given context for the duration of the body — work traced inside nests under that span, and leaf events stamp under it.</li>
        <li><strong>Emits one <code>jeffrey.TraceScope</code> per re-entry</strong>, recording which thread the span ran on and for how long. That matters because JFR attributes a duration event to the thread that <em>commits</em> it: a re-entered span can be closed on a thread it barely ran on, and the scopes are then the only record of where the work actually happened — they are what the span drill-down and the span-scoped flamegraph read.</li>
        <li>Spans that are never re-entered emit no scopes — <code>call</code> and <code>inSpanOf</code> are thread-confined already, so their span is its own single scope and existing instrumentation pays nothing.</li>
        <li>Still binds when nothing is recording (no scope emitted) — the caller handed over a context precisely because this thread has none of its own.</li>
      </ul>

      <h2 id="examples">Examples</h2>

      <DocsCodeBlock :code="grpcExample" language="java" />

      <h2 id="output">Output</h2>

      <DocsCodeBlock :code="outputExample" language="text" />

      <h2 id="notes">Notes &amp; Pitfalls</h2>

      <ul>
        <li><strong>Wrap <em>every</em> callback.</strong> A forgotten re-entry loses the nesting, not the span — the event still carries its identity, but anything traced in that callback starts a fresh trace instead of nesting.</li>
        <li><strong><code>reenter</code> is not for separate operations.</strong> Work that deserves its own named bar in the waterfall goes through <router-link to="/docs/tracing/tracer-api/fork">fork</router-link>/<router-link to="/docs/tracing/tracer-api/continue-in">continueIn</router-link>; re-entering it would fold it invisibly into the parent.</li>
        <li><strong>Scope volume is controllable per recording</strong>: <code>cafe.jeffrey.jfr.events.trace.TraceScopeEvent#enabled=false</code> keeps the nesting but stops recording where re-entered spans ran — see <router-link to="/docs/tracing/configuration">Configuration</router-link>.</li>
        <li><strong><code>jeffrey.TraceScope</code> is deliberately not a span</strong> — its field is <code>scopedSpanId</code>, not <code>spanId</code>, so structural span discovery leaves it alone. Never emit one by hand.</li>
      </ul>

      <DocsCallout type="tip">
        <router-link to="/docs/tracing/tracer-api/propagating">propagating</router-link> is this method applied executor-wide: every task submitted to a wrapped pool re-enters the submitting span automatically.
      </DocsCallout>

      <h2 id="related">Related</h2>

      <ul>
        <li><router-link to="/docs/tracing/tracer-api/open-span-of">openSpanOf</router-link> — opens the span this method re-enters.</li>
        <li><router-link to="/docs/tracing/tracer-api/continue-in">continueIn</router-link> — mints a <em>child</em> instead; for separate operations.</li>
        <li><router-link to="/docs/tracing/grpc-events">gRPC Events</router-link> — the shipped interceptors built on this pattern.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
