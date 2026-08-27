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

const signature = `static SpanContext openSpanOf(AbstractTracedEvent event)`;

const grpcExample = `// The gRPC server interceptor (what jeffrey-tracing-grpc ships): a call runs
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

const asyncClientExample = `// An async HTTP client: the call starts on the request thread (so the span
// nests under the request being served), the response arrives on an I/O
// thread — after the request thread's binding may already be gone.
HttpClientExchangeEvent event = new HttpClientExchangeEvent();
event.begin();
SpanContext span = Tracer.openSpanOf(event);   // identity captured eagerly, HERE

asyncClient.send(request).whenComplete((response, failure) ->
        Tracer.reenter(span, () -> {
            event.end();
            if (failure != null) {
                event.failed(failure);
            }
            if (event.shouldCommit()) {
                event.method = request.method();
                event.uri = template;
                event.statusCode = response != null ? response.statusCode() : 0;
                event.commitSpan();            // ids already set — never re-stamped
            }
        }));`;

const outputTree = `trace 4e11d5b8…
└─ jeffrey.api.v1.WorkspaceService/List  GrpcServerExchangeEvent  SERVER  parentSpanId=0
   └─ select_workspaces                  JdbcQueryEvent  CLIENT   (stamped inside a re-entry)

The event carries its ids from the moment openSpanOf returned — whichever
thread eventually commits it, the identity is already right.`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Tracer.openSpanOf"
      icon="bi bi-door-open"
    />

    <div class="docs-content">
      <p>Stamps an event with a fresh span and hands back its context <strong>without binding anything</strong> — the opening half of the callback pattern, completed by <router-link to="/docs/tracing/tracer-api/reenter">reenter</router-link>.</p>

      <h2 id="when">Use It When</h2>

      <p>One operation arrives in pieces on threads you don't control — a gRPC call's listener callbacks, an async HTTP client's completion handlers. There is no single block for <router-link to="/docs/tracing/tracer-api/in-span-of">inSpanOf</router-link> to enclose, so the span is opened where its identity is known, kept as a plain <code>SpanContext</code> value, and re-established around every callback.</p>

      <h2 id="signature">Signature</h2>

      <DocsCodeBlock :code="signature" language="java" />

      <h2 id="behavior">Behavior</h2>

      <ul>
        <li>Stamps the event with a fresh span — a child of the span in progress on the calling thread, or a <strong>root</strong> when none is. On a client that is the calling thread, so the outbound call nests under the request being served; on a server there is nothing above it, so it roots the trace.</li>
        <li><strong>Deliberately binds nothing.</strong> The caller keeps the returned context and re-establishes it per callback with <code>reenter</code> — an eager binding would leak past the method's return, which is exactly what <code>ScopedValue</code> forbids.</li>
        <li>Still stamps with nothing recording — like <code>inSpanOf</code>, whether the interval is recorded is the event's decision.</li>
        <li>The event's identity is fixed from this moment: a later <code>commitSpan()</code>, on any thread, never re-stamps it.</li>
      </ul>

      <h2 id="examples">Examples</h2>

      <DocsCodeBlock :code="grpcExample" language="java" />
      <DocsCodeBlock :code="asyncClientExample" language="java" />

      <h2 id="output">Output</h2>

      <DocsCodeBlock :code="outputTree" language="text" />

      <h2 id="notes">Notes &amp; Pitfalls</h2>

      <ul>
        <li><strong>It is one half of a pair.</strong> A span opened here and never re-entered still appears in the trace with correct identity — but nothing done in its callbacks nests under it; work traced there starts fresh traces instead. Wrap <em>every</em> callback in <router-link to="/docs/tracing/tracer-api/reenter">reenter</router-link>.</li>
        <li><strong>Open on the thread the span belongs to.</strong> The parent is read from the <em>calling</em> thread's binding — opening on an I/O thread would parent the span to whatever happens to run there.</li>
        <li><strong>Commit from the callback that always arrives</strong> — <code>onClose</code> for gRPC, the completion handler for an async client — so successes and failures alike close the interval.</li>
        <li><strong>Do not also call <code>inSpanOf</code></strong> on the same event; each stamps the same identity fields, and the operation needs exactly one open.</li>
      </ul>

      <DocsCallout type="tip">
        The gRPC and async-HTTP shapes above are shipped ready-made — <router-link to="/docs/tracing/grpc-events">jeffrey-tracing-grpc</router-link>'s interceptors implement exactly this pattern, which is the reason to use them rather than write your own.
      </DocsCallout>

      <h2 id="related">Related</h2>

      <ul>
        <li><router-link to="/docs/tracing/tracer-api/reenter">reenter</router-link> — the other half: re-establishes this span around each callback.</li>
        <li><router-link to="/docs/tracing/tracer-api/in-span-of">inSpanOf</router-link> — when one block <em>can</em> enclose the work.</li>
        <li><router-link to="/docs/tracing/tracer-api/stamp">stamp</router-link> — eager identity for a deferred <em>leaf</em>, with no re-entry involved.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
