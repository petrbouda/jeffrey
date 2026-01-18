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
  { id: 'what-are-alerts-messages', text: 'What are Alerts & Messages?', level: 2 },
  { id: 'severity-levels', text: 'Severity Levels', level: 2 },
  { id: 'alerts-vs-messages', text: 'Alerts vs Messages', level: 2 },
  { id: 'workspace-availability', text: 'Workspace Availability', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Alerts & Messages"
        icon="bi bi-bell"
      />

      <div class="docs-content">
        <p>Alerts and Messages surface <strong>important events</strong> from your JFR recordings, helping you stay informed about critical issues in your applications.</p>

        <DocsCallout type="warning">
          <strong>Workspace Availability:</strong> Alerts and Messages are only available in <strong>Live</strong> and <strong>Remote</strong> workspaces where continuous monitoring is relevant.
        </DocsCallout>

        <h2 id="what-are-alerts-messages">What are Alerts & Messages?</h2>
        <p><code>ImportantMessage</code> is a custom JFR event type (<code>jdk.ImportantMessage</code>) from the <router-link to="/docs/jeffrey-jfr-events/overview">Jeffrey JFR Events</router-link> library. It allows you to emit messages from critical parts of your application - whether to signal important milestones (e.g., "Report generated", "Batch job completed") or alert about critical situations (e.g., "Connection pool exhausted", "Rate limit exceeded").</p>

        <DocsCallout type="info">
          <strong>Real-time processing:</strong> Unlike regular JFR events that are collected via chunked recordings with Async-Profiler, ImportantMessages use the <strong>JFR Repository</strong> mechanism and are immediately consumed by the project as they're emitted. To enable this, configure your application using <router-link to="/docs/cli/overview">Jeffrey CLI</router-link> when setting up recording collection.
        </DocsCallout>

        <p>Jeffrey displays these events in two dedicated views:</p>
        <ul>
          <li><strong>Alerts</strong> - High priority messages marked as alerts, requiring immediate attention</li>
          <li><strong>Messages</strong> - Complete list of all important messages for full context</li>
        </ul>

        <h2 id="severity-levels">Severity Levels</h2>
        <p>Each message is assigned a severity level that determines its visual prominence and filtering:</p>

        <div class="severity-cards">
          <div class="severity-card critical">
            <div class="severity-indicator"></div>
            <div class="severity-content">
              <h4>Critical</h4>
              <p>Immediate attention required - system failures, data loss risks, security breaches</p>
            </div>
          </div>
          <div class="severity-card high">
            <div class="severity-indicator"></div>
            <div class="severity-content">
              <h4>High</h4>
              <p>Important issues that need prompt action - resource exhaustion, performance degradation</p>
            </div>
          </div>
          <div class="severity-card medium">
            <div class="severity-indicator"></div>
            <div class="severity-content">
              <h4>Medium</h4>
              <p>Noteworthy conditions to monitor - elevated metrics, unusual patterns</p>
            </div>
          </div>
          <div class="severity-card low">
            <div class="severity-indicator"></div>
            <div class="severity-content">
              <h4>Low</h4>
              <p>Informational messages - status updates, configuration changes, normal events</p>
            </div>
          </div>
        </div>

        <h2 id="alerts-vs-messages">Alerts vs Messages</h2>
        <p>While both views display ImportantMessage events, they serve different purposes:</p>

        <table>
          <thead>
            <tr>
              <th>Aspect</th>
              <th>Alerts</th>
              <th>Messages</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><strong>Filter</strong></td>
              <td>Only messages marked as alerts</td>
              <td>All messages (with optional alert filter)</td>
            </tr>
            <tr>
              <td><strong>Purpose</strong></td>
              <td>Critical issues requiring attention</td>
              <td>Complete history for context</td>
            </tr>
            <tr>
              <td><strong>UI Indicator</strong></td>
              <td>Badge count in sidebar</td>
              <td>Full list with time series chart</td>
            </tr>
          </tbody>
        </table>

        <p>Both views support filtering by severity, session, and search. The Alerts view shows a severity summary bar with counts for each level, while the Messages view includes a time series chart showing message distribution over time.</p>

        <h2 id="workspace-availability">Workspace Availability</h2>
        <p>Alerts and Messages availability depends on workspace type:</p>

        <table>
          <thead>
            <tr>
              <th>Workspace</th>
              <th>Alerts & Messages</th>
              <th>Reason</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><strong>Sandbox</strong></td>
              <td><i class="bi bi-x-lg text-muted"></i> Not available</td>
              <td>Designed for one-time analysis, not monitoring</td>
            </tr>
            <tr>
              <td><strong>Live</strong></td>
              <td><i class="bi bi-check-lg text-success"></i> Available</td>
              <td>Continuous monitoring of running applications</td>
            </tr>
            <tr>
              <td><strong>Remote</strong></td>
              <td><i class="bi bi-check-lg text-success"></i> Available</td>
              <td>View alerts from remote server</td>
            </tr>
          </tbody>
        </table>

        <DocsCallout type="tip">
          <strong>Sandbox alternative:</strong> When analyzing recordings in Sandbox, you can still see important events by browsing the JFR Event Viewer - they just won't appear in dedicated Alerts/Messages views.
        </DocsCallout>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Severity Cards */
.severity-cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin: 1.5rem 0;
}

@media (max-width: 768px) {
  .severity-cards {
    grid-template-columns: 1fr;
  }
}

.severity-card {
  display: flex;
  gap: 1rem;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.severity-indicator {
  width: 6px;
  min-width: 6px;
  border-radius: 3px;
}

.severity-card.critical .severity-indicator {
  background-color: #dc3545;
}

.severity-card.high .severity-indicator {
  background-color: #fd7e14;
}

.severity-card.medium .severity-indicator {
  background-color: #eab308;
}

.severity-card.low .severity-indicator {
  background-color: #0891b2;
}

.severity-content {
  flex: 1;
}

.severity-content h4 {
  margin: 0 0 0.25rem 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: #343a40;
}

.severity-content p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
  line-height: 1.4;
}
</style>
