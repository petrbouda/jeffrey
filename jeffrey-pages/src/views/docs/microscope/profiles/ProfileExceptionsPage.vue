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
  { id: 'rate-timeline', text: 'Rate Timeline', level: 2 },
  { id: 'top-types', text: 'Exceptions', level: 2 },
  { id: 'errors', text: 'Errors', level: 2 },
  { id: 'events', text: 'Source Events', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Exceptions"
      icon="bi bi-exclamation-octagon"
    />

    <div class="docs-content">
      <p>The Exceptions page surfaces how many throwables the application created during the recording, when it created them, and — when per-throw events are enabled — which exception classes dominate and where they were thrown from. It is the place to investigate exception storms and the hidden cost of exceptions used as control flow.</p>

      <h2 id="overview">Overview</h2>
      <p>The header strip shows <strong>Total Throwables</strong> (every throwable constructed since JVM start, from the periodic statistics gauge), <strong>Sampled Throws</strong> and <strong>Errors</strong> (individual throw events JFR recorded), and the number of distinct thrown classes. An info icon next to the title opens a <em>Total Throwables vs Sampled Throws</em> explainer modal describing why the two counts differ.</p>

      <DocsCallout type="tip">
        <strong>Reading the numbers:</strong> Total Throwables is typically far larger than the sampled counts — it counts every constructed exception, including ones that are caught immediately. A high construction rate with few visible throws still means real allocation and stack-walk cost.
      </DocsCallout>

      <h2 id="rate-timeline">Rate Timeline</h2>
      <p>Exceptions created per second, computed as the per-sample delta of the cumulative <code>jdk.ExceptionStatistics</code> gauge. Sustained plateaus reveal exception-driven control flow; sharp spikes usually correlate with failures, retries, or restarts of downstream dependencies. This tab always has data — the statistics event is part of every default JFR configuration.</p>

      <h2 id="top-types">Exceptions</h2>
      <p>Sampled throws grouped by exception class, with throw counts and the number of distinct throwing threads. Click any row to expand a searchable list of the sample messages captured for that class. The classic pattern to look for: one exception class with a huge count and a small set of messages — that is an exception being used as a branch.</p>

      <h2 id="errors">Errors</h2>
      <p>The same breakdown filtered to <code>java.lang.Error</code> subclasses (<code>NoSuchMethodError</code>, <code>OutOfMemoryError</code>, <code>NoClassDefFoundError</code>, …), with the same expandable per-class message list. Errors are rarely benign — even ones the framework swallows during startup are worth understanding.</p>

      <h2 id="events">Source Events</h2>
      <ul>
        <li><code>jdk.ExceptionStatistics</code> — periodic cumulative throwable count (Rate Timeline, Overview; always enabled).</li>
        <li><code>jdk.JavaExceptionThrow</code> — individual exception throws (Exceptions tab; often disabled in the JFR configuration, in which case the tab shows an empty notice).</li>
        <li><code>jdk.JavaErrorThrow</code> — individual Error throws (Errors tab; like the exception-throw event, may be disabled in the JFR configuration).</li>
      </ul>

      <DocsCallout type="info">
        <strong>Read the JFR canonical event list:</strong> the
        <a href="https://sap.github.io/jfrevents/" target="_blank" rel="noopener">SAP JFR Events catalog</a>
        documents the exception events and the exact fields each one carries.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
