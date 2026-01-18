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
  { id: 'session-states', text: 'Session States', level: 2 },
  { id: 'session-detection', text: 'Session Detection', level: 2 },
  { id: 'session-cleanup', text: 'Session Cleanup', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Lifecycle"
        icon="bi bi-arrow-repeat"
      />

      <div class="docs-content">
        <p>Recording sessions go through distinct lifecycle states, from active profiling to completion and eventual cleanup.</p>

        <h2 id="session-states">Session States</h2>
        <p>Each recording session transitions through the following states:</p>

        <div class="lifecycle-cards">
          <div class="lifecycle-card active">
            <div class="lifecycle-icon"><i class="bi bi-record-circle"></i></div>
            <div class="lifecycle-content">
              <h4>Active</h4>
              <p>Profiling is in progress. New JFR chunks are being created as time passes. The session remains active until the application stops or profiling is terminated.</p>
              <ul class="lifecycle-details">
                <li>View files as they're created in real-time</li>
                <li>Merge available chunks for preliminary analysis</li>
                <li>Monitor session progress and duration</li>
              </ul>
            </div>
          </div>
          <div class="lifecycle-card finished">
            <div class="lifecycle-icon"><i class="bi bi-check-circle"></i></div>
            <div class="lifecycle-content">
              <h4>Finished</h4>
              <p>Profiling has completed. All JFR files are finalized and ready for analysis via <router-link to="/docs/concepts/projects/repository">Repository</router-link>.</p>
              <ul class="lifecycle-details">
                <li>Select specific JFR chunks to merge</li>
                <li>Download all session artifacts</li>
                <li>Create recordings for profile analysis</li>
              </ul>
            </div>
          </div>
        </div>

        <DocsCallout type="tip">
          <strong>Selective analysis:</strong> You don't need to merge all chunks. Select specific time periods to analyze - for example, only the startup phase or a specific incident window.
        </DocsCallout>

        <h2 id="session-detection">Session Detection</h2>
        <p>Jeffrey needs to detect when a recording session has finished. This is configured using <router-link to="/docs/cli/overview">Jeffrey CLI</router-link> when setting up your application.</p>

        <div class="detection-methods">
          <div class="detection-card recommended">
            <div class="detection-icon"><i class="bi bi-speedometer2"></i></div>
            <div class="detection-content">
              <h4>Perf-Counters Detection <span class="badge-recommended">Recommended</span></h4>
              <p>Configure Async-Profiler to dump Perf-Counters when profiling stops. When Jeffrey detects the Perf-Counters file in a session directory, it marks the session as finished.</p>
            </div>
          </div>
          <div class="detection-card">
            <div class="detection-icon"><i class="bi bi-clock-history"></i></div>
            <div class="detection-content">
              <h4>Timeout Detection</h4>
              <p>Fallback method: if no new files appear within a configured timeout period, Jeffrey assumes the session has finished.</p>
            </div>
          </div>
        </div>

        <DocsCallout type="tip">
          <strong>Best practice:</strong> Use Perf-Counters detection for reliable and immediate session completion detection. Configure this through Jeffrey CLI when generating JVM arguments for your application.
        </DocsCallout>

        <DocsCallout type="info">
          <strong>Scheduler job:</strong> The <router-link to="/docs/concepts/projects/scheduler">Session Finished Detector</router-link> job runs periodically to check for finished sessions and emit workspace events.
        </DocsCallout>

        <h2 id="session-cleanup">Session Cleanup</h2>
        <p>Old sessions are automatically cleaned up by <router-link to="/docs/concepts/projects/scheduler">Scheduler jobs</router-link>:</p>
        <ul>
          <li><strong>Repository Session Cleaner</strong> - Removes sessions older than configured retention period</li>
          <li><strong>Repository Recording Cleaner</strong> - Limits the number of recordings in active sessions</li>
          <li><strong>JFR Compression</strong> - Compresses finished JFR files to save storage space</li>
        </ul>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Lifecycle Cards */
.lifecycle-cards {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin: 1.5rem 0;
}

.lifecycle-card {
  display: flex;
  gap: 1rem;
  padding: 1.25rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.lifecycle-icon {
  width: 48px;
  height: 48px;
  min-width: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.lifecycle-icon i {
  font-size: 1.5rem;
}

.lifecycle-card.active .lifecycle-icon {
  background: rgba(239, 68, 68, 0.1);
}

.lifecycle-card.active .lifecycle-icon i {
  color: #ef4444;
}

.lifecycle-card.finished .lifecycle-icon {
  background: rgba(16, 185, 129, 0.1);
}

.lifecycle-card.finished .lifecycle-icon i {
  color: #10b981;
}

.lifecycle-content {
  flex: 1;
}

.lifecycle-content h4 {
  margin: 0 0 0.5rem 0;
  font-size: 1.1rem;
  font-weight: 600;
  color: #343a40;
}

.lifecycle-content p {
  margin: 0 0 0.75rem 0;
  font-size: 0.9rem;
  color: #5e6e82;
  line-height: 1.5;
}

.lifecycle-details {
  margin: 0;
  padding-left: 1.25rem;
}

.lifecycle-details li {
  font-size: 0.85rem;
  color: #6b7280;
  margin-bottom: 0.25rem;
}

/* Detection Methods */
.detection-methods {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin: 1.5rem 0;
}

.detection-card {
  display: flex;
  gap: 1rem;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.detection-icon {
  width: 40px;
  height: 40px;
  min-width: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #5e64ff 0%, #7c3aed 100%);
  border-radius: 10px;
}

.detection-icon i {
  font-size: 1.1rem;
  color: #fff;
}

.detection-content {
  flex: 1;
}

.detection-content h4 {
  margin: 0 0 0.25rem 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: #343a40;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.badge-recommended {
  display: inline-block;
  padding: 0.15rem 0.4rem;
  font-size: 0.65rem;
  font-weight: 600;
  text-transform: uppercase;
  background: #10b981;
  color: #fff;
  border-radius: 4px;
}

.detection-card.recommended {
  border-color: #10b981;
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.05) 0%, #f8fafc 100%);
}

.detection-content p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
  line-height: 1.4;
}

@media (max-width: 768px) {
  .detection-methods {
    grid-template-columns: 1fr;
  }
}
</style>
