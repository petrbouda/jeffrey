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
  { id: 'pause-phases', text: 'Pause Phases', level: 2 },
  { id: 'heap-regions', text: 'Heap Regions', level: 2 },
  { id: 'evacuation', text: 'Evacuation', level: 2 },
  { id: 'marking', text: 'IHOP & Marking', level: 2 },
  { id: 'anomalies', text: 'Anomalies', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader title="G1 Analysis" icon="bi bi-diagram-3" />

    <div class="docs-content">
      <p>The G1 Analysis page is a collector-specific deep dive that reconstructs exactly how the G1 garbage collector behaved during the recording. It complements the general Garbage Collection page by reading the full set of G1-specific JFR events. The page shows an empty state when the recording was not produced with G1.</p>

      <h2 id="overview">Overview</h2>
      <p>A header strip surfaces the headline numbers — young / mixed / full collection counts, total / average / p99 / max pause time, the number of evacuation failures, and the heap-region count — so you can judge G1 health before drilling into a tab.</p>

      <DocsCallout type="tip">
        <strong>Where to start:</strong> open <em>Pause Phases</em> to see what dominates pause time, then <em>Evacuation</em> if you see evacuation failures — they are the usual cause of surprise Full GCs.
      </DocsCallout>

      <h2 id="pause-phases">Pause Phases</h2>
      <p>Every G1 pause is split into sub-phases (External Root Scan, Update/Scan Remembered Sets, Object Copy, Termination, Reference Processing, Choose CSet, …) from <code>jdk.GCPhasePause</code> and its nested <code>GCPhasePauseLevel1–4</code> events, plus per-worker <code>jdk.GCPhaseParallel</code>. The table aggregates each phase by total, average and max time so you can tell whether copy cost, remembered-set work, or reference processing dominates.</p>

      <h2 id="heap-regions">Heap Regions</h2>
      <p>A stacked timeseries shows Eden / Survivor / Old used bytes over time from <code>jdk.G1HeapSummary</code>. Below it, a per-region <strong>heatmap</strong> built from <code>jdk.G1HeapRegionInformation</code> renders every region as a coloured cell (Eden, Survivor, Old, Humongous, Archive, Free), with a snapshot scrubber to step through time. Humongous regions stand out immediately — a common G1 footgun for objects larger than half a region.</p>

      <DocsCallout type="info">
        The per-region heatmap requires <code>jdk.G1HeapRegionInformation</code>, which is verbose and disabled in most default settings. When it is absent the page falls back to the aggregate composition timeseries.
      </DocsCallout>

      <h2 id="evacuation">Evacuation</h2>
      <p>Per-collection evacuation cost from <code>jdk.EvacuationInformation</code> — collection-set regions, used-before / used-after, bytes copied, and regions freed. <strong>Evacuation failures</strong> (to-space exhaustion) from <code>jdk.EvacuationFailed</code> are counted per collection and flagged with a banner; they explain surprise Full GCs and pause spikes.</p>

      <h2 id="marking">IHOP &amp; Marking</h2>
      <p>The adaptive IHOP threshold vs. current old-generation occupancy over time (<code>jdk.G1AdaptiveIHOP</code>) — when occupancy crosses the threshold, G1 starts a concurrent marking cycle. A pause-target adherence table (<code>jdk.G1MMU</code>) flags collections whose GC time exceeded the configured pause target, telling you whether G1 is meeting its <code>MaxGCPauseMillis</code> goal.</p>

      <h2 id="anomalies">Anomalies</h2>
      <p>Two tables surface behaviour that usually indicates a problem: explicit <code>System.gc()</code> calls (<code>jdk.SystemGC</code>, including whether they ran concurrently) and GC-locker stalls (<code>jdk.GCLocker</code>), where threads sitting in JNI critical sections delayed collection.</p>

      <DocsCallout type="info">
        <strong>Read the JFR canonical event list:</strong> the
        <a href="https://sap.github.io/jfrevents/" target="_blank" rel="noopener">SAP JFR Events catalog</a>
        documents every G1 event the JDK emits, including the exact field names used here.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
