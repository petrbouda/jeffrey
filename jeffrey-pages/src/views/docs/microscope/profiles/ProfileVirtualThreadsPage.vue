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
  { id: 'pinning', text: 'Pinning', level: 2 },
  { id: 'submit-failures', text: 'Submit Failures', level: 2 },
  { id: 'lifecycle', text: 'Lifecycle', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader title="Virtual Threads" icon="bi bi-pin-angle" />

    <div class="docs-content">
      <p>The Virtual Threads page is a Project Loom dashboard built from the virtual-thread JFR events. It turns carrier pinning, submit failures, and (optionally) thread lifecycle into actionable performance and functionality signals. It shows an empty state when the recording contains no virtual-thread events.</p>

      <h2 id="overview">Overview</h2>
      <p>A header strip surfaces the headline numbers — pinning events with total and max pinned time, submit-failure count, and the peak live virtual-thread count (with started/ended totals when lifecycle events are present).</p>

      <DocsCallout type="tip">
        <strong>Where to start:</strong> open <em>Pinning</em> first. Pinning is the single biggest scalability footgun for virtual threads.
      </DocsCallout>

      <h2 id="pinning">Pinning</h2>
      <p>When a virtual thread cannot unmount from its carrier (for example inside a native frame), it <strong>pins</strong> the carrier and serializes work — defeating Loom's scalability benefit. This tab plots pinning occurrences and total pinned time per second (<code>jdk.VirtualThreadPinned</code>), a duration distribution, and the virtual threads that pin the most. To see <em>where</em> in the code the pin happens, open the weighted <code>jdk.VirtualThreadPinned</code> flamegraph in the Visualization → Flamegraphs section.</p>

      <DocsCallout type="info">
        Since JDK 24 (JEP 491) <code>synchronized</code> no longer pins, so remaining pins typically come from native frames or older libraries — the flamegraph pinpoints the exact call site.
      </DocsCallout>

      <h2 id="submit-failures">Submit Failures</h2>
      <p>A <code>jdk.VirtualThreadSubmitFailed</code> event fires when a virtual thread cannot be submitted to its carrier pool — usually carrier-pool rejection, executor shutdown, or a scheduling bug. The tab lists each failure with its timestamp, thread, and exception message; a non-empty list is a strong functionality signal worth investigating.</p>

      <h2 id="lifecycle">Lifecycle</h2>
      <p>When <code>jdk.VirtualThreadStart</code> and <code>jdk.VirtualThreadEnd</code> are enabled, this tab shows creation and completion rates plus a derived <strong>live count</strong> (cumulative starts minus ends). A live count that climbs without bound indicates a virtual-thread <strong>leak</strong>; sharp spikes indicate runaway spawning. These events are disabled by default and very high-volume, so the tab shows an empty state unless the recording explicitly enabled them.</p>

      <DocsCallout type="info">
        <strong>Read the JFR canonical event list:</strong> the
        <a href="https://sap.github.io/jfrevents/" target="_blank" rel="noopener">SAP JFR Events catalog</a>
        documents every virtual-thread event the JDK emits.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
