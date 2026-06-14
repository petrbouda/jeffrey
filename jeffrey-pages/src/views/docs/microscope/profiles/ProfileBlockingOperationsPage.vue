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
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'occurrences', text: 'Occurrences', level: 2 },
  { id: 'monitors', text: 'Lock Contention', level: 2 },
  { id: 'waits', text: 'Monitor Waits', level: 2 },
  { id: 'parks', text: 'Thread Parks', level: 2 },
  { id: 'sleeps', text: 'Thread Sleeps', level: 2 },
  { id: 'virtual-threads', text: 'Virtual Threads', level: 2 },
  { id: 'events', text: 'Source Events', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader title="Blocking Operations" icon="bi bi-lock" />

    <div class="docs-content">
      <p>The Blocking Operations page shows where application threads <em>wait</em> — contending for a lock, waiting to be notified, parking on a <code>java.util.concurrent</code> primitive, sleeping, or (for virtual threads) pinning their carrier. A blocked thread uses no CPU, so it's invisible to a flame graph but is often where latency hides.</p>

      <DocsCallout type="tip">
        <strong>Reading it:</strong> a single monitor class dominating <em>Total Blocked</em> time is the thing to make lock-free or shard; a high <em>timed-out</em> count on Monitor Waits often means a missed <code>notify</code>.
      </DocsCallout>

      <h2 id="overview">Overview</h2>
      <p>The header strip shows total monitor-blocked time with the number of contended classes, plus the monitor-wait, thread-park, sleep and virtual-thread-pinning counts.</p>

      <h2 id="occurrences">Occurrences</h2>
      <p>The default tab — a time-series chart of blocking-event occurrences per second, with one line per blocking type (lock contention, monitor waits, parks, sleeps, virtual-thread pinning). It is the place to start: spot <em>when</em> blocking spikes, <em>which</em> kind spikes, and whether different blocking types correlate (for example a contention spike that lines up with a park spike points at the same hot path). Drill into the per-type tabs below once the timeline tells you what to chase.</p>

      <h2 id="monitors">Lock Contention</h2>
      <p>Contended <code>synchronized</code> acquisition (<code>jdk.JavaMonitorEnter</code>) grouped by monitor class — count, total/max blocked time and distinct threads. Uncontended locks are a cheap header CAS and aren't recorded; contention inflates the lock to a heavyweight <code>ObjectMonitor</code>. The classic finds: connection pools, logging frameworks, synchronized singletons.</p>

      <h2 id="waits">Monitor Waits</h2>
      <p><code>Object.wait()</code> (<code>jdk.JavaMonitorWait</code>) grouped by monitor class, with a <em>timed-out</em> count. Waits release the monitor and park until <code>notify()</code> or a timeout — a high timed-out count flags a missed signal or a too-short timeout.</p>

      <h2 id="parks">Thread Parks</h2>
      <p><code>LockSupport.park</code> (<code>jdk.ThreadPark</code>) grouped by blocker class — the primitive behind <code>ReentrantLock</code>, semaphores, latches and blocking queues via <code>AbstractQueuedSynchronizer</code>. High park totals on one blocker class show where threads queue.</p>

      <h2 id="sleeps">Thread Sleeps</h2>
      <p><code>Thread.sleep()</code> (<code>jdk.ThreadSleep</code>) grouped by thread, with requested vs actual time. Large totals usually mean a polling loop that could be event-driven.</p>

      <h2 id="virtual-threads">Virtual Threads</h2>
      <p>The longest <code>jdk.VirtualThreadPinned</code> incidents. A virtual thread normally unmounts from its carrier while blocked; inside a <code>synchronized</code> block or native frame it can't, so it <em>pins</em> the carrier — defeating virtual-thread scalability. Replace such <code>synchronized</code> with <code>ReentrantLock</code> (JDK 24+ also removes most <code>synchronized</code> pinning).</p>

      <h2 id="events">Source Events</h2>
      <p>All are threshold-gated — recorded only when the block exceeds the configured duration:</p>
      <ul>
        <li><code>jdk.JavaMonitorEnter</code> — contended monitor acquisition.</li>
        <li><code>jdk.JavaMonitorWait</code> — <code>Object.wait()</code> with a timed-out flag.</li>
        <li><code>jdk.ThreadPark</code> — <code>LockSupport.park</code> blocking.</li>
        <li><code>jdk.ThreadSleep</code> — <code>Thread.sleep()</code> with requested vs actual time.</li>
        <li><code>jdk.VirtualThreadPinned</code> — virtual threads pinned to their carrier.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
