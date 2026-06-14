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
  { id: 'timeline', text: 'Timeline', level: 2 },
  { id: 'class-loaders', text: 'Class Loaders', level: 2 },
  { id: 'class-load-activity', text: 'Class Load Activity', level: 2 },
  { id: 'redefinitions', text: 'Redefinitions', level: 2 },
  { id: 'events', text: 'Source Events', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Class Loading"
      icon="bi bi-box-seam"
    />

    <div class="docs-content">
      <p>The Class Loading page surfaces how the JVM's class-loading subsystem behaved during the recording — how many classes were loaded and unloaded over time, which class loaders hold the most metaspace, and what bytecode-instrumentation activity took place. It is the place to investigate metaspace leaks, class-loader leaks, hidden-class explosions, and agent overhead. The view is split into four analysis tabs (plus a <em>How It Works</em> explainer) sitting under a headline metrics strip.</p>

      <h2 id="overview">Overview</h2>
      <p>The header strip shows the currently-loaded class count (loaded minus unloaded), the cumulative loaded and unloaded totals, the number of distinct class loaders, total metaspace reserved across loaders, hidden-class count, and the number of class redefinitions.</p>

      <DocsCallout type="tip">
        <strong>Leak signal:</strong> if <em>Currently Loaded Classes</em> grows without bound across the <em>Timeline</em>, the application is generating classes faster than they are unloaded — a classic metaspace-leak symptom (runaway lambda, proxy, or scripting-engine class generation).
      </DocsCallout>

      <h2 id="timeline">Timeline</h2>
      <p>A time-series chart of currently-loaded vs. unloaded classes built from periodic <code>jdk.ClassLoadingStatistics</code> events. A steadily climbing loaded line points to a leak; a flat line means class loading reached steady state. Unloading activity (visible as a rising unloaded line) usually coincides with class-loader collection.</p>

      <h2 id="class-loaders">Class Loaders</h2>
      <p>A sortable table of the latest per-loader snapshot from <code>jdk.ClassLoaderStatistics</code> — loader identity, parent loader, number of classes, metaspace reserved and used, and hidden classes. Sort by metaspace to find the heaviest loaders, or by hidden classes to find lambda/proxy hotspots. A large number of distinct loaders for the same type is a sign of a class-loader leak (for example repeated redeploys).</p>

      <h2 id="class-load-activity">Class Load Activity</h2>
      <p>The slowest individual class loads from <code>jdk.ClassLoad</code>, with the class name, load duration, and defining loader. These events are <strong>disabled by default</strong> because of their overhead, so this tab shows an empty notice unless class-loading events were explicitly enabled in the recording configuration.</p>

      <h2 id="redefinitions">Redefinitions</h2>
      <p>Bytecode-instrumentation activity, built from <code>jdk.RetransformClasses</code> batches and the <code>jdk.ClassRedefinition</code> entries they produce. Each redefinition is driven by an instrumentation agent — the JFR retransformation at startup, APM agents, or mocking frameworks. The batch table shows how many classes each retransformation touched and how long it took; the per-class table lists every redefined class and its modification count.</p>

      <h2 id="events">Source Events</h2>
      <ul>
        <li><code>jdk.ClassLoadingStatistics</code> — periodic loaded/unloaded counts (Timeline, Overview).</li>
        <li><code>jdk.ClassLoaderStatistics</code> — per-loader class count, metaspace, and hidden classes (Class Loaders, Overview).</li>
        <li><code>jdk.ClassLoad</code> — per-class load timing (Class Load Activity; off by default).</li>
        <li><code>jdk.ClassRedefinition</code> / <code>jdk.RetransformClasses</code> — instrumentation activity (Redefinitions).</li>
      </ul>

      <DocsCallout type="info">
        <strong>Read the JFR canonical event list:</strong> the
        <a href="https://sap.github.io/jfrevents/" target="_blank" rel="noopener">SAP JFR Events catalog</a>
        documents every class-loading event the JDK emits and the exact fields each one carries.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
