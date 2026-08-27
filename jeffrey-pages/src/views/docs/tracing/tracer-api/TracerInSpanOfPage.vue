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
R inSpanOf(AbstractTracedEvent event, ScopedValue.CallableOp<R, X> body) throws X

static void inSpanOf(AbstractTracedEvent event, Runnable body)`;

const filterExample = `// The canonical use: the HTTP filter (what jeffrey-tracing-servlet ships).
// The exchange event IS the root span — no separate jeffrey.TraceSpan is
// emitted for the same interval.
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

const customExample = `// The same shape with an event type of your own — a custom event with
// traced work nesting INSIDE its interval
KafkaPublishEvent event = new KafkaPublishEvent();
event.topic = "orders";
Tracer.inSpanOf(event, () -> {                 // stamps traceId/spanId/parentSpanId
    byte[] payload = Tracer.call("order.serialize", () -> serialize(order));
    event.payloadSize = payload.length;
    send(payload);
});                                            // inSpanOf commits via commitSpan()

// Background jobs: called outside any span, inSpanOf opens a fresh ROOT —
// which is exactly right for a pipeline run whose lifetime is unrelated
// to whatever request triggered it
TraceSpanEvent job = new TraceSpanEvent();
Tracer.inSpanOf(job, () -> pipeline.execute());`;

const outputSpans = [
  { depth: 0, name: 'GET /api/internal/profiles/{profileId}', kind: 'SERVER' as const,
    start: 0, duration: 128, event: 'HttpServerExchangeEvent', note: 'root' },
  { depth: 1, name: 'flamegraph.generate', kind: 'INTERNAL' as const,
    start: 8, duration: 96, event: 'jeffrey.TraceSpan' },
  { depth: 2, name: 'select_frames', kind: 'CLIENT' as const,
    start: 12, duration: 41, event: 'JdbcQueryEvent', note: 'leaf' },
  { depth: 1, name: 'flamegraph.marshalling', kind: 'INTERNAL' as const,
    start: 106, duration: 20, event: 'jeffrey.TraceSpan' }
];
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Tracer.inSpanOf"
      icon="bi bi-box-arrow-in-down"
    />

    <div class="docs-content">
      <p>Makes an instrumented event <em>be</em> the span it opens. This is how every trace root is opened — an HTTP exchange, a gRPC call, a pipeline run.</p>

      <h2 id="when">Use It When</h2>

      <p>An event of your own already describes the interval, and traced work nests <em>inside</em> it. Emitting a <code>jeffrey.TraceSpan</code> alongside such an event would record the same interval twice, so none is emitted: the event carries the ids, and everything traced inside the body nests underneath it.</p>

      <h2 id="signature">Signatures</h2>

      <DocsCodeBlock :code="signatures" language="java" />

      <h2 id="behavior">Behavior</h2>

      <ul>
        <li>Stamps the event with a fresh span — a child of the span in progress, or a <strong>root</strong> when none is — <em>when the span opens</em>, not when the event commits. The ids are known up front, and the binding is gone by the time a caller's <code>finally</code> runs, so stamping there would silently do nothing.</li>
        <li>Binds the new context for the duration of the body, so everything traced inside nests under the event.</li>
        <li>If the body throws, marks the event <code>ERROR</code> with the exception's class name (via <code>failed()</code>) and rethrows unchanged.</li>
        <li>The event itself is committed by <em>your</em> lifecycle — <code>end()</code> + <code>commitSpan()</code> in the <code>finally</code> for the filter shape, or by <code>inSpanOf</code>'s own commit in the simple <code>Runnable</code> shape.</li>
        <li><strong>Always establishes the binding, even with nothing recording</strong> — unlike <router-link to="/docs/tracing/tracer-api/call">call</router-link>. Whether an interval is recorded at all is the caller's event's decision, not this method's.</li>
      </ul>

      <h2 id="examples">Examples</h2>

      <DocsCodeBlock :code="filterExample" language="java" />
      <DocsCodeBlock :code="customExample" language="java" />

      <h2 id="output">Output</h2>

      <DocsSpanTree
        trace="2b7fe410…"
        :spans="outputSpans"
        caption="No jeffrey.TraceSpan is emitted for the root — the exchange event carries the ids itself. Emitting one would record the same interval twice."
      />

      <h2 id="notes">Notes &amp; Pitfalls</h2>

      <ul>
        <li><strong>Never re-stamp an <code>inSpanOf</code> event.</strong> Calling <router-link to="/docs/tracing/tracer-api/stamp">stamp</router-link> on it afterwards would replace the root identity and orphan every child. <code>commitSpan()</code> is safe — it leaves an event that already carries identity untouched.</li>
        <li><strong>One root per request.</strong> Do not also wrap the request in <code>Tracer.run</code> — that is the same interval twice.</li>
        <li><strong>The body must enclose the work.</strong> For an operation that arrives in callback pieces on threads you don't control, there is no single block to enclose — use <router-link to="/docs/tracing/tracer-api/open-span-of">openSpanOf</router-link> + <router-link to="/docs/tracing/tracer-api/reenter">reenter</router-link> instead.</li>
        <li><strong>An event with nothing nesting inside</strong> — a statement, a message — does not need <code>inSpanOf</code> at all: commit it with <code>commitSpan()</code> and it becomes a leaf. See <router-link to="/docs/tracing/tracer-api/stamp">stamp</router-link>.</li>
      </ul>

      <DocsCallout type="tip">
        The full recipe for span-capable event types of your own — <code>@Span</code> naming templates, <code>describeSpan()</code>, and what plain <code>commit()</code> keeps and loses — is on <router-link to="/docs/tracing/custom-events">Custom Traced Events</router-link>.
      </DocsCallout>

      <h2 id="related">Related</h2>

      <ul>
        <li><router-link to="/docs/tracing/tracer-api/open-span-of">openSpanOf</router-link> — the same stamp without the binding, for callback-driven protocols.</li>
        <li><router-link to="/docs/tracing/tracer-api/stamp">stamp</router-link> — for leaf events with nothing nesting inside.</li>
        <li><router-link to="/docs/tracing/http-events">HTTP Events</router-link> / <router-link to="/docs/tracing/grpc-events">gRPC Events</router-link> — the shipped root-span instrumentation built on this method.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
