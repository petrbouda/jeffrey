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
  { id: 'cpu', text: 'CPU', level: 2 },
  { id: 'network', text: 'Network', level: 2 },
  { id: 'context-switches', text: 'Context Switches', level: 2 },
  { id: 'host-processes', text: 'Host Processes', level: 2 },
  { id: 'events', text: 'Source Events', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="System & Host"
      icon="bi bi-cpu"
    />

    <div class="docs-content">
      <p>The System &amp; Host page answers the triage question every performance investigation starts with: <em>is it my JVM or the box?</em> It compares JVM CPU against total machine CPU, shows network utilization and context-switch pressure, and lists the other processes competing for the same host.</p>

      <h2 id="overview">Overview</h2>
      <p>The header strip shows maximum and average machine CPU, average JVM CPU (user + system), the average CPU consumed by <em>other</em> processes, the peak context-switch rate, and the number of host processes observed.</p>

      <DocsCallout type="tip">
        <strong>The noisy-neighbor check:</strong> when <em>Machine CPU</em> is high but <em>JVM CPU</em> is low, something else on the host is stealing cycles — check the <em>Host Processes</em> tab for the culprit before tuning the application.
      </DocsCallout>

      <h2 id="cpu">CPU</h2>
      <p>Machine-total, JVM-user, and JVM-system CPU load over the recording from periodic <code>jdk.CPULoad</code> samples. High JVM-system time relative to user time points at syscall-heavy workloads (I/O, excessive GC threads, page faults); a machine-total line far above the JVM lines is contention from other processes.</p>

      <h2 id="network">Network</h2>
      <p>Read/write throughput per network interface from <code>jdk.NetworkUtilization</code>, with an interface selector. Useful to correlate latency spikes with network saturation or confirm that a "slow service" was actually saturating its NIC.</p>

      <h2 id="context-switches">Context Switches</h2>
      <p>Thread context switches per second from <code>jdk.ThreadContextSwitchRate</code>. A persistently high rate signals thread oversubscription or heavy lock churn — threads spending their quantum fighting for the scheduler rather than doing work.</p>

      <h2 id="host-processes">Host Processes</h2>
      <p>The other processes running on the host during the recording, from periodic <code>jdk.SystemProcess</code> snapshots (latest snapshot per pid). This is where the noisy neighbor gets a name.</p>

      <h2 id="events">Source Events</h2>
      <ul>
        <li><code>jdk.CPULoad</code> — periodic JVM user/system and machine-total CPU load.</li>
        <li><code>jdk.NetworkUtilization</code> — per-interface read/write rates.</li>
        <li><code>jdk.ThreadContextSwitchRate</code> — OS context switches per second.</li>
        <li><code>jdk.SystemProcess</code> — processes running on the host.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
