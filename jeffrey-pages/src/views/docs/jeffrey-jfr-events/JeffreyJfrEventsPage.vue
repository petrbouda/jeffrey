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
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'event-types', text: 'Event Types', level: 2 },
  { id: 'integration', text: 'Integration', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Jeffrey JFR Events"
        icon="bi bi-broadcast"
      />

      <div class="docs-content">
        <p><strong>Jeffrey JFR Events</strong> is a library that provides custom JFR event definitions for emitting application-level events that can be captured and analyzed in Jeffrey.</p>

        <DocsCallout type="info">
          <strong>Open Source Library:</strong> Jeffrey Events is available at <a href="https://github.com/petrbouda/jeffrey-events" target="_blank" rel="noopener">github.com/petrbouda/jeffrey-events</a>. Add it to your project to emit custom events that Jeffrey can process.
        </DocsCallout>

        <h2 id="event-types">Event Types</h2>
        <p>While standard JFR captures JVM-level events (GC, threads, CPU sampling), Jeffrey Events allows you to emit <strong>application-level events</strong> that provide insight into your business logic. These events are recorded into the same JFR file alongside standard JVM events, providing a complete picture of both infrastructure and application behavior.</p>

        <div class="event-cards">
          <div class="event-card">
            <div class="event-icon"><i class="bi bi-globe"></i></div>
            <div class="event-content">
              <h4>HTTP Events</h4>
              <p>Capture HTTP server requests including method, URI, status code, and response time. Useful for analyzing request patterns and identifying slow endpoints.</p>
            </div>
          </div>
          <div class="event-card">
            <div class="event-icon"><i class="bi bi-database"></i></div>
            <div class="event-content">
              <h4>Database Events</h4>
              <p>Track SQL query execution with query text, execution time, and row counts. Identify slow queries and database bottlenecks.</p>
            </div>
          </div>
          <div class="event-card">
            <div class="event-icon"><i class="bi bi-layers"></i></div>
            <div class="event-content">
              <h4>Connection Pool Events</h4>
              <p>Monitor connection pool activity including connection acquisition time, pool utilization, and timeouts. Detect pool exhaustion issues.</p>
            </div>
          </div>
        </div>

        <h2 id="integration">Integration</h2>
        <p>Add Jeffrey Events to your project and instrument your code to emit events:</p>

        <h3>Maven Dependency</h3>
        <div class="code-block">
          <pre><code>&lt;dependency&gt;
    &lt;groupId&gt;cafe.jeffrey-analyst&lt;/groupId&gt;
    &lt;artifactId&gt;jeffrey-events&lt;/artifactId&gt;
    &lt;version&gt;0.2.3&lt;/version&gt;
&lt;/dependency&gt;</code></pre>
        </div>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Event Cards */
.event-cards {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin: 1.5rem 0;
}

.event-card {
  display: flex;
  gap: 1rem;
  padding: 1.25rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.event-icon {
  width: 48px;
  height: 48px;
  min-width: 48px;
  border-radius: 10px;
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  display: flex;
  align-items: center;
  justify-content: center;
}

.event-icon i {
  font-size: 1.25rem;
  color: white;
}

.event-content {
  flex: 1;
}

.event-content h4 {
  margin: 0 0 0.5rem 0;
  font-size: 1rem;
  font-weight: 600;
  color: #343a40;
}

.event-content p {
  margin: 0;
  font-size: 0.875rem;
  color: #5e6e82;
  line-height: 1.5;
}

/* Code Block */
.code-block {
  margin: 1rem 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.code-block pre {
  margin: 0;
  padding: 1rem;
  background: #1e293b;
  overflow-x: auto;
}

.code-block code {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.85rem;
  color: #e2e8f0;
  line-height: 1.5;
}

/* Feature List */
.feature-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 1.5rem 0;
}

.feature-item {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  background: #f0fdf4;
  border-radius: 8px;
  border: 1px solid #bbf7d0;
}

.feature-item i {
  color: #10b981;
  font-size: 1rem;
  margin-top: 0.125rem;
}

.feature-item div {
  font-size: 0.875rem;
  color: #374151;
  line-height: 1.5;
}

.feature-item strong {
  color: #1f2937;
}
</style>
