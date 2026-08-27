<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
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
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsArchDiagram from '@/components/docs/DocsArchDiagram.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'high-level-architecture', text: 'High-Level Architecture', level: 2 },
  { id: 'staged-pipelines', text: 'Staged Pipelines', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Architecture Overview"
        icon="bi bi-diagram-3"
      />

      <div class="docs-content">
        <p>Jeffrey consists of two applications: <strong>Jeffrey Microscope</strong> (an analysis tool for visualizing and exploring JFR profiles) and <strong>Jeffrey Hub</strong> (a recording collection service that manages workspaces, sessions, and live recordings). Jeffrey Microscope connects to Jeffrey Hub via gRPC to access remote workspaces, and also offers <strong>Recordings</strong> for analyzing JFR files directly without a server connection.</p>

        <h2 id="high-level-architecture">High-Level Architecture</h2>
        <p>Jeffrey runs as two separate applications. Jeffrey Hub collects and stores recordings from Java applications, while Jeffrey Microscope provides the analysis UI and connects to the server via gRPC:</p>

        <DocsArchDiagram />

        <h2 id="staged-pipelines">Staged Pipelines</h2>
        <p>Some work in Microscope takes minutes rather than milliseconds — turning a recording into a profile, initializing a heap dump, or asking the Advisor to read your source. Those requests return immediately and the frontend polls a stage timeline instead of holding a request open.</p>
        <p>The machinery behind that timeline is shared, not copied per feature: a run is tracked by a registry that keeps at most one run per key and remembers the current or most recent one, while each stage records its own duration and exactly one stage at a time reports a live clock. A pipeline supplies only its stage ids and two policy choices — whether runs are capped by a concurrency ceiling, and whether a finished run is evicted after a while. Heap-dump initialization is uncapped and keeps its last run indefinitely, because the work is local CPU and IO and the settings page displays that run; an Advisor run holds a model call open for minutes, so it is capped and expires once its recommendations are stored.</p>
        <p>Most runs are queued onto the registry's own executor, which is what lets the request return. Profile initialization is the exception: it is already scheduled by whoever created the profile, and the profile can only be enabled once the run is done, so it runs on that caller's thread and the registry does the bookkeeping only. The difference is visible in one more place — a queued run has nobody waiting on it, so a failure is recorded and swallowed, while an inline one has a caller who must not go on to enable a profile that never finished building.</p>
        <p>A stage can also say it did not run. Profile initialization derives trace tables only when the recording declares span-carrying event types; for an ordinary JFR recording that stage reports itself skipped rather than instantaneous, which is the difference between "there was nothing to do" and "it was fast".</p>
        <p>What a run is keyed by is the feature's own business. The heap dump runs once per profile; the Advisor launches a <em>batch</em> that analyses every event type at once, so it files one run per type and the types drain through the shared ceiling a few at a time. The batch itself — the aggregate the Overview renders, and the rule that a profile may only have one in flight — belongs to the Advisor, not to the pipeline.</p>
        <p>Live progress deliberately lives only in memory. A run interrupted by a restart is gone, which is truthful — the work died with the process. What survives is the terminal outcome: when a run finishes or fails, its stage timings are written to a <code>pipeline_runs</code> row in the profile database, keyed by pipeline and by what the run targeted. A batch is then simply every row of its pipeline, aggregated on read, so a page reopened later still shows what happened.</p>
        <p>The recordings list reads live progress and nothing else, which is why a recording being imported shows its stages as chips on its own row. Reaching for the stored rows there would mean opening every listed profile's database on every request, to answer a question the list is not asking: what it shows is a profile being built now.</p>

      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* ===== HIGH-LEVEL ARCHITECTURE DIAGRAM ===== */
.arch-diagram {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  margin: 1.5rem 0;
  padding: 2rem 1.5rem;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  border-radius: 12px;
  border: 1px solid #e2e8f0;
}

</style>
