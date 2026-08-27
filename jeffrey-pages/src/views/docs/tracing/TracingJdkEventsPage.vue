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

const exampleSpans = [
  { depth: 0, name: 'GET /api/orders/{id}', kind: 'SERVER' as const, start: 0, duration: 43.8 },
  { depth: 1, name: 'OrderMapper.selectById', kind: 'CLIENT' as const, start: 0.3, duration: 43.1,
    event: 'JdbcQueryEvent' },
  { depth: 2, name: 'Socket read', kind: 'CLIENT' as const, start: 1.2, duration: 39.2,
    event: 'jdk.SocketRead', note: 'promoted leaf span', color: 'var(--span-socket-io)' }
];

const payloadExample = `Socket read          39.2 ms       (synthesized from jdk.SocketRead)
  host        db-primary.internal
  address     10.0.8.4
  port        5432
  bytesRead   18 234

Lock wait            12.0 ms       (synthesized from jdk.JavaMonitorEnter)
  monitorClass   com.acme.InventoryCache
  previousOwner  worker-7`;

const thresholdsExample = `# What the Provisioner generates with tracing.enabled = true — a second
# recording whose only job is to out-vote the profiler's settings. Line
# breaks added for reading; it is emitted as one unbroken option.
-XX:StartFlightRecording:name=jeffrey-tracing-thresholds,maxage=30m,\\
jdk.SocketRead#enabled=true,jdk.SocketRead#threshold=0ms,jdk.SocketRead#throttle=1000000/s,\\
jdk.SocketWrite#enabled=true,jdk.SocketWrite#threshold=0ms,jdk.SocketWrite#throttle=1000000/s,\\
jdk.FileRead#enabled=true,jdk.FileRead#threshold=0ms,jdk.FileRead#throttle=1000000/s,\\
jdk.FileWrite#enabled=true,jdk.FileWrite#threshold=0ms,jdk.FileWrite#throttle=1000000/s,\\
jdk.FileForce#enabled=true,jdk.FileForce#threshold=0ms,\\
jdk.JavaMonitorEnter#enabled=true,jdk.JavaMonitorEnter#threshold=1ms,\\
jdk.JavaMonitorWait#enabled=true,jdk.JavaMonitorWait#threshold=1ms,\\
jdk.ThreadPark#enabled=true,jdk.ThreadPark#threshold=1ms,\\
jdk.ThreadSleep#enabled=true,jdk.ThreadSleep#threshold=1ms,\\
jdk.VirtualThreadPinned#enabled=true,jdk.VirtualThreadPinned#threshold=1ms,\\
jdk.ZAllocationStall#enabled=true,jdk.ZAllocationStall#threshold=0ms`;
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

      <DocsSpanTree
        trace="3fa8d1c0…"
        :spans="exampleSpans"
        caption="The JDBC span spent 39 of its 43 ms on the database socket — and the parent's self time shrinks by exactly that stretch."
      />

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

      <p>Promotion can only work with what the recording holds, and the stock configuration holds far less than a trace needs. Three separate things have to be overridden per event, which is exactly what the <router-link to="/docs/provisioner">Provisioner</router-link> emits for you:</p>

      <ul>
        <li><strong><code>threshold</code></strong> — I/O events are recorded from <code>0ms</code>, so no socket or file wait is too short to become a bar; the blocking events (locks, park, sleep, pinning) are recorded from <code>1ms</code>, which keeps the volume sane without losing anything a reader would look for.</li>
        <li><strong><code>throttle</code></strong> — Java&nbsp;25's <code>default.jfc</code> rate-limits socket and file I/O to <strong>100 events a second</strong>, and a threshold does not lift that. Without the lift, a busy JVM silently drops the very events a trace hangs its leaf spans on. The lift has to be a numeric rate rather than <code>off</code>, because JFR resolves <code>throttle</code> to the highest <em>parseable</em> rate across active recordings and <code>off</code> parses as no rate at all.</li>
        <li><strong><code>enabled</code></strong> — named alongside the threshold, since a threshold alone is ignored for an event a custom profiler configuration had switched off.</li>
      </ul>

      <DocsCodeBlock :code="thresholdsExample" language="bash" />

      <DocsCallout type="tip">
        <code>jdk.FileForce</code> and <code>jdk.ZAllocationStall</code> carry no <code>throttle</code> to lift — naming one they do not have costs a JFR warning at startup. Everything here is the Provisioner's built-in list; override it with <code>tracing.jfr-event-settings</code>, or set that to <code>none</code> to drop the recording while leaving method tracing on. See <router-link to="/docs/tracing/configuration">Configuration</router-link>.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
