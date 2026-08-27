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
  { id: 'idea', text: 'The Idea: Promotion, Not Instrumentation', level: 2 },
  { id: 'promoted-set', text: 'Which JDK Events Become Spans', level: 2 },
  { id: 'attribution', text: 'How an Event Finds Its Span', level: 2 },
  { id: 'payload', text: 'The Event Payload Survives', level: 2 },
  { id: 'reading', text: 'Reading Promoted Spans in the Waterfall', level: 2 },
  { id: 'drill-down', text: 'Everything Else: Events in Span', level: 2 },
  { id: 'thresholds', text: 'Recording Thresholds', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const exampleTree = `trace 3fa8d1c0…
└─ GET /api/orders/{id}            SERVER            43.8 ms
   └─ OrderMapper.selectById       JdbcQueryEvent    43.1 ms
      └─ Socket read               jdk.SocketRead    39.2 ms   ← promoted leaf span

The JDBC span spent 39 of its 43 ms on the database socket — the socket
read is a child bar with a real position and duration, and the parent's
self time shrinks by the same stretch.`;

const payloadExample = `Socket read          39.2 ms       (synthesized from jdk.SocketRead)
  host        db-primary.internal
  address     10.0.8.4
  port        5432
  bytesRead   18 234

Lock wait            12.0 ms       (synthesized from jdk.JavaMonitorEnter)
  monitorClass   com.acme.InventoryCache
  previousOwner  worker-7`;

const thresholdsExample = `# The JDK blocking events carry defaults of 10–20 ms — only waits long
# enough to be recorded are long enough to become bars. Lower them for
# request-grade traces:
-XX:StartFlightRecording=filename=app.jfr,settings=profile,\\
jdk.SocketRead#threshold=1ms,jdk.SocketWrite#threshold=1ms,\\
jdk.FileRead#threshold=1ms,jdk.FileWrite#threshold=1ms,jdk.FileForce#threshold=1ms,\\
jdk.JavaMonitorEnter#threshold=1ms,jdk.ThreadPark#threshold=1ms

# The Jeffrey Provisioner does this for you: with tracing enabled it starts
# a second recording that only lowers these thresholds (JFR takes the most
# verbose setting across active recordings).`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="JDK Events in Traces"
      icon="bi bi-cpu"
    />

    <div class="docs-content">
      <p>The JVM already records where a thread stood still — socket and file I/O, lock contention, parking, sleeping. When Jeffrey derives a profile's traces, it <strong>promotes</strong> each of those events into a leaf span of its own, hanging under the span that was waiting. Your instrumentation says a JDBC call took 43&nbsp;ms; the promoted <code>jdk.SocketRead</code> under it says 39&nbsp;ms of that was the database socket.</p>

      <h2 id="idea">The Idea: Promotion, Not Instrumentation</h2>

      <p>Nothing is instrumented for this and nothing new is recorded — the promotion is pure analysis over events every recording already contains, so it applies <strong>retroactively to existing profiles</strong>. What bounds it is the recording itself: the JDK events carry thresholds (typically 10–20&nbsp;ms by default), so only waits long enough to be recorded are long enough to become bars.</p>

      <DocsCodeBlock :code="exampleTree" language="text" />

      <h2 id="promoted-set">Which JDK Events Become Spans</h2>

      <table>
        <thead>
          <tr>
            <th>JDK event</th>
            <th>Span name</th>
            <th>Kind</th>
            <th>Category (toolbar toggle)</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jdk.SocketRead</code></td>
            <td>Socket read</td>
            <td><code>CLIENT</code></td>
            <td>Socket I/O (<em>I/O ops</em>)</td>
          </tr>
          <tr>
            <td><code>jdk.SocketWrite</code></td>
            <td>Socket write</td>
            <td><code>CLIENT</code></td>
            <td>Socket I/O (<em>I/O ops</em>)</td>
          </tr>
          <tr>
            <td><code>jdk.FileRead</code></td>
            <td>File read</td>
            <td><code>INTERNAL</code></td>
            <td>File I/O (<em>I/O ops</em>)</td>
          </tr>
          <tr>
            <td><code>jdk.FileWrite</code></td>
            <td>File write</td>
            <td><code>INTERNAL</code></td>
            <td>File I/O (<em>I/O ops</em>)</td>
          </tr>
          <tr>
            <td><code>jdk.FileForce</code></td>
            <td>File force</td>
            <td><code>INTERNAL</code></td>
            <td>File I/O (<em>I/O ops</em>)</td>
          </tr>
          <tr>
            <td><code>jdk.JavaMonitorEnter</code></td>
            <td>Lock wait</td>
            <td><code>INTERNAL</code></td>
            <td>Monitor blocked (<em>Blocking ops</em>)</td>
          </tr>
          <tr>
            <td><code>jdk.JavaMonitorWait</code></td>
            <td>Object.wait</td>
            <td><code>INTERNAL</code></td>
            <td>Monitor wait (<em>Blocking ops</em>)</td>
          </tr>
          <tr>
            <td><code>jdk.ThreadPark</code></td>
            <td>Parked</td>
            <td><code>INTERNAL</code></td>
            <td>Parked (<em>Blocking ops</em>)</td>
          </tr>
          <tr>
            <td><code>jdk.ThreadSleep</code></td>
            <td>Sleeping</td>
            <td><code>INTERNAL</code></td>
            <td>Sleeping (<em>Blocking ops</em>)</td>
          </tr>
          <tr>
            <td><code>jdk.ZAllocationStall</code></td>
            <td>Allocation stall</td>
            <td><code>INTERNAL</code></td>
            <td>Allocation stall (<em>Blocking ops</em>)</td>
          </tr>
          <tr>
            <td><code>jdk.VirtualThreadPinned</code></td>
            <td>VT pinned</td>
            <td><code>INTERNAL</code></td>
            <td>VT pinned (<em>Blocking ops</em>)</td>
          </tr>
        </tbody>
      </table>

      <p>Global stop-the-world events — GC pauses and safepoints — are <em>not</em> promoted into spans; they stopped every thread at once, so they are drawn as lanes across the whole waterfall instead. See <router-link to="/docs/tracing/gc-safepoints">GC Pauses &amp; Safepoints</router-link>.</p>

      <h2 id="attribution">How an Event Finds Its Span</h2>

      <p>The attribution rule is: <strong>same thread, innermost open span</strong>. A JDK event is attached to the span that (a) ran on the same thread and (b) was open when the event began, choosing the innermost such span. An event that began outside every span stays an ordinary event and is not promoted. The synthesized span's id is minted deterministically (and guarded against colliding with a recorded id), so promoted spans are ordinary spans everywhere it matters:</p>

      <ul>
        <li>they count in the trace's span total,</li>
        <li>they appear in the operation's span breakdown — "Socket read" ranks against your own operations,</li>
        <li>they subtract from their parent's <strong>self time</strong>, so a parent that was "mostly self time" before promotion becomes honest about being mostly I/O.</li>
      </ul>

      <h2 id="payload">The Event Payload Survives</h2>

      <p>The JDK event's own fields ride along into the span's detail panel — host and port for a socket, path for a file, monitor class for a lock, parked class for a park (only the plumbing fields — start time, duration, thread — are stripped, since the span already carries them):</p>

      <DocsCodeBlock :code="payloadExample" language="text" />

      <h2 id="reading">Reading Promoted Spans in the Waterfall</h2>

      <p>Promoted spans are marked apart from recorded ones: drawn solid in their wait category's colour rather than a span-kind pastel, and their detail names the JDK event they came from. Two toolbar toggles govern them along the reader's question — <strong>Blocking ops</strong> for the lock, park, sleep and stall rows, and <strong>I/O ops</strong> for the file and socket rows — so hiding lock noise never hides the socket read that explains the trace. Switching both off reads the recorded span structure alone; a toggle whose family recorded nothing stays visible but disabled.</p>

      <!-- TODO screenshot: /images/docs/tracing/promoted-blocking-spans.png — a waterfall with promoted Socket read / Lock wait bars in category colours, detail panel open on one -->

      <h2 id="drill-down">Everything Else: Events in Span</h2>

      <p>Promotion covers the blocking events. Everything else the JVM recorded inside a span's window — CPU samples, allocations, class loading, compilation — is one click away: a span's <strong>Events in span</strong> drill-down opens the JVM events that occurred on that thread inside the span's window, as a timeline with one lane per event type, a draggable mini-map, and a per-type breakdown offering a flamegraph per type. Other spans are excluded, and so are the promoted blocking events — they are already child bars of the very span being opened — so this is JVM activity, not a restatement of the tree.</p>

      <h2 id="thresholds">Recording Thresholds</h2>

      <p>Promotion can only work with what the recording holds. The JDK blocking events default to thresholds of 10–20&nbsp;ms — fine for profiling, coarse for request-grade traces:</p>

      <DocsCodeBlock :code="thresholdsExample" language="bash" />

      <DocsCallout type="tip">
        With <router-link to="/docs/provisioner">Provisioner</router-link>-managed sessions, enabling <code>tracing&nbsp;{ enabled = true }</code> automatically starts the thresholds side-recording — see <router-link to="/docs/tracing/configuration">Configuration</router-link>.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
