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
  { id: 'problem', text: 'Three Problems Wearing the Same Bar', level: 2 },
  { id: 'categories', text: 'Global vs Thread-Scoped Context', level: 2 },
  { id: 'gc-events', text: 'GC Pauses: One Band per Stoppage', level: 2 },
  { id: 'safepoints', text: 'Safepoints', level: 2 },
  { id: 'cpu-throttling', text: 'CPU Throttling: A Window, Not a Pause', level: 2 },
  { id: 'lanes', text: 'The Lanes in the Trace Detail', level: 2 },
  { id: 'why-slow', text: 'The "Why Was This Trace Slow?" Panel', level: 2 },
  { id: 'matching', text: 'How Pauses Are Matched to a Trace', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const exampleSummary = `Why was this trace slow?               (a 412 ms trace)

  GC pause          118 ms   ██████████░░░░░░░░░░░░░░   → Garbage Collection view
  Socket I/O         96 ms   ████████░░░░░░░░░░░░░░░░   → Socket I/O view
  Monitor blocked    41 ms   ███░░░░░░░░░░░░░░░░░░░░░   → Blocking Operations view
  Safepoint           8 ms   █░░░░░░░░░░░░░░░░░░░░░░░   → VM Operations view
  Own work          149 ms   ████████████░░░░░░░░░░░░

The promoted categories' figures are summed from the promoted spans
themselves, so the panel's numbers and the bars in the waterfall are one
source of truth by construction. The remainder is named "own work"
rather than left as an unexplained gap.`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="GC Pauses &amp; Safepoints"
      icon="bi bi-pause-circle"
    />

    <div class="docs-content">
      <p>A span's bar says it took 200&nbsp;ms; it does not say whether that was 200&nbsp;ms of computing, of waiting on a lock, or of standing still inside a GC pause — three different problems wearing the same bar. The Trace Detail view overlays the answer: stop-the-world pauses are drawn as lanes across the whole waterfall, and a panel beneath it sums up where the trace's time actually went.</p>

      <h2 id="problem">Three Problems Wearing the Same Bar</h2>

      <p>Per-thread waits — socket reads, lock contention, parking — are handled by <router-link to="/docs/tracing/jdk-events">promotion into leaf spans</router-link>: they belong to one thread, so they can be a child bar of the span that waited. A GC pause or a safepoint is different: it stopped <em>every</em> thread at once, on a VM thread no span's thread hash will ever match. So global pauses get their own treatment — lanes, not bars — and their own attribution rule — window overlap, not thread matching.</p>

      <h2 id="categories">Global vs Thread-Scoped Context</h2>

      <table>
        <thead>
          <tr>
            <th>Category</th>
            <th>Scope</th>
            <th>JFR events</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>GC pause</td>
            <td><strong>Global</strong></td>
            <td><code>jdk.GCPhasePause</code> (primary); <code>jdk.GCPhasePauseLevel1</code>–<code>4</code> as nested detail</td>
          </tr>
          <tr>
            <td>Safepoint</td>
            <td><strong>Global</strong></td>
            <td><code>jdk.ExecuteVMOperation</code>, <code>jdk.SafepointStateSynchronization</code></td>
          </tr>
          <tr>
            <td>Socket I/O, File I/O, Monitor blocked, Monitor wait, Parked, Sleeping, Allocation stall, VT pinned</td>
            <td>Thread</td>
            <td>The <router-link to="/docs/tracing/jdk-events">promoted blocking events</router-link> — drawn as child bars, not lanes</td>
          </tr>
          <tr>
            <td>Deoptimization</td>
            <td>Thread</td>
            <td>An instant, not a stretch of blocked time — the one thread-scoped category that stays pure context</td>
          </tr>
          <tr>
            <td>CPU throttled</td>
            <td><strong>Global</strong></td>
            <td><code>jdk.ContainerCPUThrottling</code> — a sampling <em>window</em> rather than a measured pause, see below</td>
          </tr>
        </tbody>
      </table>

      <h2 id="gc-events">GC Pauses: One Band per Stoppage</h2>

      <p>A collection pause is recorded as a <code>jdk.GCPhasePause</code> plus the levelled phases that ran inside it (<code>GCPhasePauseLevel1..4</code>). The phases are a <em>breakdown</em>, not pauses of their own — every one of them overlaps the band above it, so drawing them would redraw the same stopped world up to five times without adding a single stretch of it. The lanes draw only the pauses themselves, and every total — a lane's figure, the why-slow panel's — <strong>merges overlapping intervals before summing</strong>: an instant the JVM was stopped counts once, however many phases describe it.</p>

      <DocsCallout type="info">
        <strong>Durations are carried in nanoseconds, and unmeasured pauses are not drawn.</strong> A GC phase is routinely shorter than a microsecond, so a pause's duration is taken from the recording as written rather than rebuilt from a coarser end timestamp. JFR also writes some phases with no duration at all; those are dropped rather than defaulted to zero — a pause of unknown length is not a stretch of stopped time, and drawing it at the minimum band width would claim one the recording never made.
      </DocsCallout>

      <h2 id="safepoints">Safepoints</h2>

      <p>Safepoints cover the non-GC stop-the-world work: <code>jdk.ExecuteVMOperation</code> (the operation itself — a deoptimization sweep, a thread dump, revoking biased locks) and <code>jdk.SafepointStateSynchronization</code> (the time spent bringing every thread to the safepoint before the operation could run). A safepoint that ran <em>inside</em> a GC pause sits inside that pause's window — which is why GC and safepoints get separate lanes: they cannot share one without drawing the same stoppage twice. Each band is labeled by the operation's own name (from the event's <code>name</code>/<code>operation</code>/<code>cause</code> fields), so a lane hover reads "G1CollectForAllocation" or "ThreadDump", not just "safepoint".</p>

      <h2 id="cpu-throttling">CPU Throttling: A Window, Not a Pause</h2>

      <p>In a container with a CPU limit, the CFS scheduler parks the whole cgroup once it exhausts its quota. It is the stoppage nothing records: no thread emits an event, because no thread is running to emit one. A span simply takes longer, with no blocking child, no GC band and no safepoint to explain it — which makes it the single most misread cause of latency in containerised JVMs.</p>

      <p><code>jdk.ContainerCPUThrottling</code> is the only thing that sees it, and it sees it indirectly. It is a <em>periodic sample</em> (every JFR chunk, 30&nbsp;s by default) of three cgroup <code>cpu.stat</code> counters that are cumulative since the cgroup was created: periods elapsed, periods throttled, and nanoseconds parked. Nothing in the recording describes a stretch of throttling; a stretch is recovered only by differencing consecutive samples, and what that yields is the <strong>sampling window that contained the throttling</strong> — never the throttling itself.</p>

      <DocsCallout type="warning">
        <strong>A throttle band is an approximation, and the view says so three ways.</strong> The band spans the 30&nbsp;s window, not the parked time inside it; nothing recovers <em>when</em> during that window the container was stopped, or which of its threads wore it. So the lane draws the band <strong>hatched</strong> rather than solid, its stat reads <code>window · N×</code> instead of a share of the trace, and its figure carries a <strong>tilde</strong> (<code>~430 ms</code>). Each of those marks the same thing: this is the only lane whose width is not a duration.
      </DocsCallout>

      <p>Two consequences follow from that, and both are deliberate:</p>

      <ul>
        <li><strong>It is never added to the why-slow panel.</strong> The panel's percentages are taken against the trace's own window, and a window-derived total summed beside measured pauses would push the accounted time past what actually elapsed — silently shrinking "own work" to cover the difference. Throttling explains a trace; it does not account for it.</li>
        <li><strong>The parked time is not scaled down by overlap.</strong> When a window only partly covers the trace, the figure is still the window's whole throttled time. Throttling is not spread evenly through a window, so apportioning it by time would invent a number the counters never supported.</li>
      </ul>

      <p>Two further rules keep the reading honest. A window whose counters came back <em>lower</em> than the sample before it — a restarted container — is <strong>dropped rather than clamped to zero</strong>: how much throttling preceded the reset cannot be recovered, and a clamped window would report a number that is merely wrong instead of honestly absent. And a container with no CFS quota cannot be throttled at all; the JVM writes its counters as null, so it produces no windows without its <code>jdk.ContainerConfiguration</code> needing to be consulted.</p>

      <p>The lane links out to the <router-link to="/docs/microscope/profiles">Containers view</router-link>, which carries the same reading over the whole recording — the verdict, the throttle ratio over time, and the worst windows.</p>

      <h2 id="lanes">The Lanes in the Trace Detail</h2>

      <p>In the <router-link to="/docs/tracing/analysis">Trace Detail view</router-link>, each global category gets one lane above the span rows: a coloured dot, a share meter, an occurrence count, and one positioned band per pause. A striped wash is drawn behind the span rows for the same windows, so a bar that straddles a pause visibly straddles it. The <strong>JVM context</strong> toolbar toggle switches the overlay off; toggles reset per trace, so one trace's tuning does not silently reshape the next.</p>

      <p><strong>A band cannot be read as a duration.</strong> Below about half a percent of the trace a pause is thinner than a pixel, so every short one is drawn at the same minimum width — the lanes say <em>where</em> the JVM stopped, never <em>for how long</em>. Moving the pointer across the waterfall draws a time cursor that reads out the instant under it: how far into the trace it is and, when it falls inside a pause, that pause's name and its true duration.</p>

      <p>Two runs of the same operation show what the lanes buy you:</p>

      <figure class="docs-figure">
        <img src="/images/docs/tracing/gc-impacted.webp" alt="A trace hit by a GC pause, with the pause band and striped wash across the span rows" />
        <figcaption>Hit: a single 101&nbsp;ms collection owns 70% of this 146&nbsp;ms request &mdash; the band and the wash make the spans that straddled it unmissable.</figcaption>
      </figure>

      <figure class="docs-figure">
        <img src="/images/docs/tracing/no-gc.webp" alt="The same operation without any GC impact" />
        <figcaption>Untouched: the same operation at 48&nbsp;ms &mdash; no lanes to draw, and the panel says so out loud instead of showing an empty chart.</figcaption>
      </figure>

      <h2 id="why-slow">The "Why Was This Trace Slow?" Panel</h2>

      <p>Under the waterfall, the panel ranks where the trace's wall-clock time went, category by category, with the remainder named <strong>own work</strong> rather than left as an unexplained gap — a breakdown that only names the waiting invites the reader to assume the rest is mystery:</p>

      <DocsCodeBlock :code="exampleSummary" language="text" />

      <p>Each category links out to the profile view that explains it: a GC finding leads to the <router-link to="/docs/microscope/profiles/garbage-collection">Garbage Collection</router-link> view, a safepoint finding to <router-link to="/docs/microscope/profiles/vm-operations">VM Operations</router-link>, a lock finding to <router-link to="/docs/microscope/profiles/blocking-operations">Blocking Operations</router-link>, an I/O finding to the <router-link to="/docs/microscope/profiles/socket-io">Socket</router-link> or <router-link to="/docs/microscope/profiles/file-io">File I/O</router-link> view.</p>

      <p>Individual spans get the same treatment in miniature: a span's inline detail shows per-span <strong>wait chips</strong> — the thread-scoped waits attributed to that span's own window — beside its self/children timing meter.</p>

      <h2 id="matching">How Pauses Are Matched to a Trace</h2>

      <ul>
        <li><strong>Overlap, not starts-inside.</strong> A pause is attributed to a trace when their windows <em>overlap</em>: a 40&nbsp;ms collection that began 5&nbsp;ms before a span started is exactly the pause that explains it, and a starts-inside match would miss it.</li>
        <li><strong>Thread-agnostic.</strong> GC pauses and safepoints are emitted on VM threads, so no thread predicate is applied at all — a VM-thread event could never match a span's thread.</li>
        <li><strong>Sums can exceed the trace.</strong> Because global pauses stop every thread at once, summing a trace's categories can legitimately exceed its duration when spans ran concurrently — the panel measures against the trace's own window instead.</li>
        <li><strong>Retroactive.</strong> Like the promoted blocking spans, this is pure analysis over events every recording already contains — it needs no instrumentation and applies to existing profiles.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
