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
  { id: 'how-it-works', text: 'How It Works', level: 2 },
  { id: 'configuration', text: 'Configuration', level: 2 },
  { id: 'server-side-detection', text: 'Server-Side Detection', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Jeffrey Agent"
        icon="bi bi-heart-pulse"
      />

      <div class="docs-content">
        <h2 id="overview">Overview</h2>
        <p><strong>Jeffrey Agent</strong> is a lightweight Java agent (~11 KB, zero external dependencies) that runs inside your Java application's JVM process. Its sole purpose is <strong>heartbeat monitoring</strong> — it periodically writes a timestamp to the filesystem so Jeffrey Server can detect when a profiled application has stopped.</p>

        <DocsCallout type="info">
          Jeffrey Agent is automatically configured by <router-link to="/docs/cli/overview">Jeffrey CLI</router-link> when initializing a profiling session. You do not need to set up the agent manually.
        </DocsCallout>

        <div class="agent-cards">
          <div class="agent-card">
            <div class="agent-card-icon"><i class="bi bi-heart-pulse"></i></div>
            <div class="agent-card-content">
              <h4>Heartbeat Signal</h4>
              <p>Writes the current epoch timestamp to a file every 5 seconds (configurable). Jeffrey Server polls this file to determine whether the application is still running.</p>
            </div>
          </div>
          <div class="agent-card">
            <div class="agent-card-icon"><i class="bi bi-feather"></i></div>
            <div class="agent-card-content">
              <h4>Minimal Footprint</h4>
              <p>Zero external dependencies, single daemon thread, ~11 KB JAR. Designed to add negligible overhead to the profiled application.</p>
            </div>
          </div>
          <div class="agent-card">
            <div class="agent-card-icon"><i class="bi bi-hdd-stack"></i></div>
            <div class="agent-card-content">
              <h4>Filesystem-Based</h4>
              <p>Communicates via the shared filesystem only — no network connections, no gRPC, no HTTP. The agent writes, the server reads.</p>
            </div>
          </div>
          <div class="agent-card">
            <div class="agent-card-icon"><i class="bi bi-power"></i></div>
            <div class="agent-card-content">
              <h4>Graceful Shutdown Detection</h4>
              <p>When the JVM shuts down, the heartbeat stops. The server detects the stale timestamp and automatically finishes the session, distinguishing clean exits from crashes.</p>
            </div>
          </div>
        </div>

        <h2 id="how-it-works">How It Works</h2>
        <p>Jeffrey Agent is loaded via the standard <code>-javaagent</code> JVM option. Jeffrey CLI automatically constructs this flag when starting a profiled application:</p>
        <pre class="agent-code"><code>-javaagent:/path/to/jeffrey-agent.jar=heartbeat.dir=/sessions/session-123/.heartbeat</code></pre>

        <div class="agent-lifecycle">
          <div class="lifecycle-step">
            <div class="lifecycle-number">1</div>
            <div class="lifecycle-content">
              <strong>Startup</strong> — The JVM calls <code>premain()</code> before the application's <code>main()</code> method. The agent parses its arguments, creates the heartbeat directory, and starts a scheduled task on a daemon thread.
            </div>
          </div>
          <div class="lifecycle-step">
            <div class="lifecycle-number">2</div>
            <div class="lifecycle-content">
              <strong>Runtime</strong> — Every 5 seconds, the agent atomically writes the current timestamp to the heartbeat file. Write failures are logged but never crash the application.
            </div>
          </div>
          <div class="lifecycle-step">
            <div class="lifecycle-number">3</div>
            <div class="lifecycle-content">
              <strong>Shutdown</strong> — A JVM shutdown hook stops the heartbeat thread and cleans up temporary files. The final heartbeat timestamp remains on disk for the server to read.
            </div>
          </div>
        </div>

        <h2 id="configuration">Configuration</h2>
        <p>Agent arguments are passed as comma-separated key-value pairs:</p>
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th>Parameter</th>
                <th>Default</th>
                <th>Description</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><code>heartbeat.dir</code></td>
                <td>—</td>
                <td>Directory where the heartbeat file is written (required)</td>
              </tr>
              <tr>
                <td><code>heartbeat.interval</code></td>
                <td><code>5000</code></td>
                <td>Heartbeat interval in milliseconds</td>
              </tr>
              <tr>
                <td><code>heartbeat.enabled</code></td>
                <td><code>true</code></td>
                <td>Set to <code>false</code> to disable heartbeating</td>
              </tr>
            </tbody>
          </table>
        </div>

        <h2 id="server-side-detection">Server-Side Detection</h2>
        <p>Jeffrey Server runs a periodic job that polls heartbeat files for all active sessions. When a heartbeat becomes stale (older than a configured threshold, typically ~5 minutes), the server marks the session as finished and uses the last heartbeat timestamp as the session end time. If no heartbeat file exists and the session is old enough, a fallback finish time is used instead.</p>

      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* ===== AGENT CARDS ===== */
.agent-cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin: 1.25rem 0;
}

.agent-card {
  display: flex;
  gap: 0.75rem;
  padding: 1rem;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.agent-card-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  min-width: 36px;
  background: #fef3c7;
  border-radius: 8px;
  color: #d97706;
  font-size: 1.1rem;
}

.agent-card-content h4 {
  margin: 0 0 0.25rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: #1e293b;
}

.agent-card-content p {
  margin: 0;
  font-size: 0.8rem;
  color: #64748b;
  line-height: 1.45;
}

/* ===== CODE BLOCK ===== */
.agent-code {
  background: #1e293b;
  color: #e2e8f0;
  padding: 0.75rem 1rem;
  border-radius: 6px;
  font-size: 0.78rem;
  overflow-x: auto;
  margin: 0.75rem 0 1.25rem;
}

.agent-code code {
  color: inherit;
  background: none;
}

/* ===== LIFECYCLE STEPS ===== */
.agent-lifecycle {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 1rem 0 1.5rem;
}

.lifecycle-step {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
}

.lifecycle-number {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  min-width: 28px;
  background: #4338ca;
  color: #fff;
  border-radius: 50%;
  font-size: 0.8rem;
  font-weight: 700;
}

.lifecycle-content {
  font-size: 0.85rem;
  color: #374151;
  line-height: 1.5;
  padding-top: 0.2rem;
}

.lifecycle-content strong {
  color: #1e293b;
}

.lifecycle-content code {
  font-size: 0.78rem;
  background: #f1f5f9;
  padding: 0.1rem 0.3rem;
  border-radius: 3px;
  color: #4338ca;
}

/* ===== RESPONSIVE ===== */
@media (max-width: 768px) {
  .agent-cards {
    grid-template-columns: 1fr;
  }
}
</style>
