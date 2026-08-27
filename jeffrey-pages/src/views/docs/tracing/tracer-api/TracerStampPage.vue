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

const signature = `static void stamp(AbstractTracedEvent event)`;

const commitSpanExample = `// The ORDINARY leaf does not call stamp at all — commitSpan() folds the
// stamp into the commit (from Jeffrey's DatabaseClient):
JdbcInsertEvent event = new JdbcInsertEvent("insert_recording", "microscope");
event.begin();
int rows = delegate.update(sql, paramSource);
event.end();
if (event.shouldCommit()) {
    event.sql = sql;
    event.rows = rows;
    event.commitSpan();       // stamps child ids under the span in progress, then commits
}`;

const deferredExample = `// stamp() itself is for the DEFERRED commit — the event outlives the
// enclosing binding, e.g. a streamed query committed from close().
// Capture identity eagerly, inside the span; commit later.
JdbcStreamEvent event = new JdbcStreamEvent("stream_events", "profile");
Tracer.stamp(event);                    // capture identity NOW, inside the span
event.begin();
Stream<EventRow> rows = runStreamingQuery();
return rows.onClose(() -> {
    event.end();
    if (event.shouldCommit()) {
        event.sql = sql;
        event.commitSpan();             // runs after the binding is gone — ids already
    }                                   // set, and commitSpan() never re-stamps
});`;

const stampedSpans = [
  { name: 'GET /api/internal/recordings', kind: 'SERVER' as const, event: 'the span in progress',
    traceId: '8c1d33f0…', spanId: '5518…9241', parentSpanId: '0' },
  { name: 'insert_recording', kind: 'CLIENT' as const, event: 'JdbcInsertEvent',
    traceId: '8c1d33f0…', spanId: '2044…7715', parentSpanId: '5518…9241' },
  { name: 'select_recordings', kind: 'CLIENT' as const, event: 'JdbcQueryEvent',
    traceId: '8c1d33f0…', spanId: '7761…3082', parentSpanId: '5518…9241' }
];
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Tracer.stamp"
      icon="bi bi-pin-angle"
    />

    <div class="docs-content">
      <p>Gives an event a span of its own, nested inside the span currently in progress — making the event a <strong>leaf</strong> of the trace.</p>

      <h2 id="when">Use It When</h2>

      <p>An event describes a self-contained action with nothing nesting inside — a statement, a message publish — <em>and</em> its commit is deferred past the enclosing binding (a streamed result committed from <code>close()</code>, an async completion). For the ordinary leaf committed in its own <code>finally</code>, you never call <code>stamp</code> directly: <code>event.commitSpan()</code> folds the stamp into the commit, so forgetting it stops being possible.</p>

      <h2 id="signature">Signature</h2>

      <DocsCodeBlock :code="signature" language="java" />

      <h2 id="behavior">Behavior</h2>

      <ul>
        <li>Mints a child span id under the span bound on this thread and writes the three ids onto the event. <strong>Nothing is bound</strong> to the minted id — that is what makes a stamped event a leaf: the work it describes is the event itself, not a scope other spans can nest inside.</li>
        <li>Outside any span it does nothing, leaving the ids at <code>0</code> — the encoding for "not part of a trace" — so the same instrumentation works traced and untraced.</li>
        <li>Each stamped event gets a span id of its own rather than a copy of the enclosing one — every statement issued inside one request would otherwise carry the same span id, and a span id has to identify exactly one span.</li>
        <li>Never emits anything itself; the event's own <code>commitSpan()</code> does the committing.</li>
      </ul>

      <h2 id="examples">Examples</h2>

      <DocsCodeBlock :code="commitSpanExample" language="java" />
      <DocsCodeBlock :code="deferredExample" language="java" />

      <h2 id="output">Output</h2>

      <DocsSpanTree
        variant="cards"
        :spans="stampedSpans"
        caption="Both statements share the trace id and the parent id — and neither shares a span id, because a span id identifies exactly one span."
      />

      <h2 id="notes">Notes &amp; Pitfalls</h2>

      <ul>
        <li><strong>Prefer <code>commitSpan()</code>.</strong> An emitter that commits in its own <code>finally</code> should not call <code>stamp</code> — <code>commitSpan()</code> stamps an event that does not yet carry identity and leaves one that does untouched. Reserve the explicit call for the deferred-commit case.</li>
        <li><strong>Why the deferred case needs it:</strong> a <code>commitSpan()</code> that runs after the binding is gone would find no span and record ids of <code>0</code> — or worse, run inside <em>someone else's</em> binding and stamp the event into the wrong trace. Eager stamping pins the identity while it is still knowable.</li>
        <li><strong>Never stamp an <router-link to="/docs/tracing/tracer-api/in-span-of">inSpanOf</router-link> event</strong> — it already carries its identity as the root/interior span; re-stamping would orphan its children.</li>
        <li><strong>A leaf cannot have children.</strong> If traced work needs to nest inside the event's interval, it is not a leaf — open it with <router-link to="/docs/tracing/tracer-api/in-span-of">inSpanOf</router-link> instead.</li>
      </ul>

      <DocsCallout type="warning">
        Committing with a bare <code>commit()</code> skips the stamp entirely and silently drops the event from every trace — the single most common instrumentation mistake. See <router-link to="/docs/tracing/custom-events">Custom Traced Events → commitSpan() vs commit()</router-link>.
      </DocsCallout>

      <h2 id="related">Related</h2>

      <ul>
        <li><router-link to="/docs/tracing/tracer-api/in-span-of">inSpanOf</router-link> — when work nests inside the event's interval.</li>
        <li><router-link to="/docs/tracing/tracer-api/open-span-of">openSpanOf</router-link> — an unbound span that callbacks re-enter; also stamps eagerly.</li>
        <li><router-link to="/docs/tracing/jdbc-events">JDBC Events</router-link> — the statement leaves built on this mechanism, including <code>jeffrey.JdbcStream</code>'s deferred commit.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
