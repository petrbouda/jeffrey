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
  { id: 'candidates', text: 'Candidates', level: 2 },
  { id: 'events', text: 'Source Events', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader title="Memory Leak Candidates" icon="bi bi-bug" />

    <div class="docs-content">
      <p>JFR's old-object sampler tracks a sample of objects that survive long enough to be suspicious, and records each one's class, size, age, and the heap usage at sampling time. This page lists those candidates — the starting point for hunting a slow heap leak.</p>

      <h2 id="overview">Overview</h2>
      <p>Candidate count, the largest single candidate, the total retained size across candidates, and the age of the longest-lived object.</p>

      <DocsCallout type="tip">
        <strong>Reading it:</strong> a class that shows up repeatedly with growing age across recordings is a strong leak signal. Old, large arrays (high element counts) are classic unbounded-cache symptoms.
      </DocsCallout>

      <h2 id="candidates">Candidates</h2>
      <p>Each leak candidate with its class, shallow size, age (how long it has been alive), array element count (for arrays), and the heap usage when it was sampled. The table is ordered by size — the biggest retained objects first.</p>

      <h2 id="events">Source Events</h2>
      <ul>
        <li><code>jdk.OldObjectSample</code> — the old-object sampler. <strong>Off by default</strong>; enable it via the JFR <em>profile</em> settings or <code>-XX:+UnlockDiagnosticVMOptions -XX:+OldObjectSample</code>-style configuration to populate this page.</li>
      </ul>

      <DocsCallout type="info">
        For full retention paths (the GC-root chain holding each object), pair this with a heap dump and the Heap Dump analysis pages — the JFR sample gives the suspect, the heap dump gives the chain.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
