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
  { id: 'heartbeat-mechanism', text: 'Heartbeat Mechanism', level: 3 },
  { id: 'finish-detection-logic', text: 'Finish Detection Logic', level: 3 },
  { id: 'jvm-crash-detection', text: 'JVM Crash Detection', level: 3 },
  { id: 'heartbeat-recovery', text: 'Heartbeat Recovery', level: 3 },
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
        <p>Jeffrey automatically detects when a recording session has finished using a <strong>heartbeat-based</strong> mechanism. The Jeffrey Agent emits periodic liveness signals that the platform monitors to determine session state.</p>

        <h3 id="heartbeat-mechanism">Heartbeat Mechanism</h3>
        <p>The Jeffrey Agent attaches to the profiled JVM and emits periodic <code>jeffrey.Heartbeat</code> JFR events into a streaming repository (<code>streaming-repo/</code>). Each heartbeat carries a sequence number and timestamp.</p>
        <p>The Jeffrey platform reads these heartbeat events in real-time via JDK's <code>EventStream</code> API. Each received heartbeat updates the <code>last_heartbeat_at</code> timestamp in the database, providing a continuous liveness signal for the session.</p>

        <h3 id="finish-detection-logic">Finish Detection Logic</h3>
        <p>A scheduled job periodically evaluates each active session and applies the following rules:</p>

        <div class="detection-cases">
          <div class="detection-case active-case">
            <div class="case-indicator"><i class="bi bi-circle-fill"></i></div>
            <div class="case-content">
              <h4>Heartbeat is recent</h4>
              <p>The last heartbeat timestamp exists in the database and is within the staleness threshold (default 10 seconds). The session remains <strong>Active</strong>.</p>
            </div>
          </div>
          <div class="detection-case finished-case">
            <div class="case-indicator"><i class="bi bi-check-circle-fill"></i></div>
            <div class="case-content">
              <h4>Heartbeat is stale</h4>
              <p>The last heartbeat timestamp exists but is older than the staleness threshold. The session is marked as <strong>Finished</strong>.</p>
            </div>
          </div>
          <div class="detection-case skip-case">
            <div class="case-indicator"><i class="bi bi-hourglass-split"></i></div>
            <div class="case-content">
              <h4>No heartbeat, session is young</h4>
              <p>No heartbeat has been recorded yet, but the session was created recently. The check is <strong>skipped</strong> because heartbeats may not have arrived yet.</p>
            </div>
          </div>
          <div class="detection-case recovery-case">
            <div class="case-indicator"><i class="bi bi-arrow-repeat"></i></div>
            <div class="case-content">
              <h4>No heartbeat, session is old</h4>
              <p>No heartbeat has been recorded and the session has been around for a while. Jeffrey attempts <strong>replay recovery</strong> from the streaming repository. If no heartbeats are found, the session is marked as <strong>Finished</strong>.</p>
            </div>
          </div>
        </div>

        <h3 id="jvm-crash-detection">JVM Crash Detection</h3>
        <p>When a JVM crashes, a HotSpot error log (<code>hs_err_pid*.log</code>) is generated in the session directory. After a session finishes, Jeffrey checks for the presence of this file and emits a JVM crash event, providing visibility into abnormal terminations.</p>

        <h3 id="heartbeat-recovery">Heartbeat Recovery</h3>
        <p>If the Jeffrey platform was down while a session was active, heartbeats written to the streaming repository can be recovered by replaying the repository files. This ensures accurate <code>finished_at</code> timestamps even after Jeffrey restarts, preventing sessions from being incorrectly marked as finished due to missing heartbeat data.</p>

        <DocsCallout type="info">
          <strong>Scheduler job:</strong> The <router-link to="/docs/concepts/projects/scheduler">Session Finished Detector</router-link> job runs periodically to evaluate heartbeat staleness and detect finished sessions.
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

/* Detection Cases */
.detection-cases {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 1.5rem 0;
}

.detection-case {
  display: flex;
  gap: 1rem;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.case-indicator {
  width: 32px;
  min-width: 32px;
  display: flex;
  align-items: flex-start;
  padding-top: 0.15rem;
}

.case-indicator i {
  font-size: 1rem;
}

.active-case .case-indicator i {
  color: #ef4444;
}

.finished-case .case-indicator i {
  color: #10b981;
}

.skip-case .case-indicator i {
  color: #f59e0b;
}

.recovery-case .case-indicator i {
  color: #5e64ff;
}

.case-content {
  flex: 1;
}

.case-content h4 {
  margin: 0 0 0.25rem 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: #343a40;
}

.case-content p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
  line-height: 1.4;
}
</style>
