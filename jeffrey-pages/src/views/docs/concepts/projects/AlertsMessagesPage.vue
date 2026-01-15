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
import { useDocsNavigation } from '@/composables/useDocsNavigation';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { adjacentPages } = useDocsNavigation();
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'what-are-alerts-messages', text: 'What are Alerts & Messages?', level: 2 },
  { id: 'alerts-vs-messages', text: 'Alerts vs Messages', level: 2 },
  { id: 'message-sources', text: 'Message Sources', level: 2 },
  { id: 'use-cases', text: 'Use Cases', level: 2 },
  { id: 'workspace-availability', text: 'Workspace Availability', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <nav class="docs-breadcrumb">
        <router-link to="/docs" class="breadcrumb-item">
          <i class="bi bi-book me-1"></i>Docs
        </router-link>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item">Concepts</span>
        <span class="breadcrumb-separator">/</span>
        <router-link to="/docs/concepts/projects" class="breadcrumb-item">Projects</router-link>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item active">Alerts & Messages</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-bell"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Alerts & Messages</h1>
          <p class="docs-section-badge">Projects</p>
        </div>
      </header>

      <div class="docs-content">
        <p>Alerts and Messages surface <strong>important events</strong> from your JFR recordings, helping you stay informed about critical issues in your applications.</p>

        <DocsCallout type="warning">
          <strong>Workspace Availability:</strong> Alerts and Messages are only available in <strong>Live</strong> and <strong>Remote</strong> workspaces where continuous monitoring is relevant.
        </DocsCallout>

        <h2 id="what-are-alerts-messages">What are Alerts & Messages?</h2>
        <p>JFR recordings can contain <code>ImportantMessage</code> events - special events that indicate something noteworthy happened in your application. Jeffrey extracts these events and displays them in two views:</p>
        <ul>
          <li><strong>Alerts</strong> - High priority messages requiring immediate attention</li>
          <li><strong>Messages</strong> - All important messages for context and history</li>
        </ul>

        <h2 id="alerts-vs-messages">Alerts vs Messages</h2>
        <p>While both come from the same source (ImportantMessage JFR events), they serve different purposes:</p>

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
              <td><strong>Priority filter</strong></td>
              <td>High priority only</td>
              <td>All priorities</td>
            </tr>
            <tr>
              <td><strong>Purpose</strong></td>
              <td>Critical issues requiring attention</td>
              <td>Complete history for context</td>
            </tr>
            <tr>
              <td><strong>UI display</strong></td>
              <td>Badge count in sidebar</td>
              <td>Full list view</td>
            </tr>
            <tr>
              <td><strong>Typical count</strong></td>
              <td>Few (only critical)</td>
              <td>Many (all events)</td>
            </tr>
          </tbody>
        </table>

        <h3>Alert Badge</h3>
        <p>When high-priority alerts exist, a badge appears on the Alerts menu item showing the count. This gives you immediate visibility into critical issues without having to open the view.</p>

        <h2 id="message-sources">Message Sources</h2>
        <p>Important messages can come from various sources:</p>

        <h3>JVM Events</h3>
        <p>The JVM automatically generates important messages for:</p>
        <ul>
          <li><strong>Memory warnings</strong> - Heap usage approaching limits</li>
          <li><strong>GC issues</strong> - Long garbage collection pauses</li>
          <li><strong>Thread problems</strong> - Deadlocks or excessive thread counts</li>
          <li><strong>Class loading issues</strong> - Failed class loading or verification</li>
        </ul>

        <h3>Application Events</h3>
        <p>Your application code can emit custom important messages using JFR APIs:</p>
        <ul>
          <li><strong>Business alerts</strong> - Critical business logic conditions</li>
          <li><strong>Performance warnings</strong> - Slow operation detection</li>
          <li><strong>Error notifications</strong> - Unexpected errors or exceptions</li>
          <li><strong>System state changes</strong> - Circuit breaker trips, failovers, etc.</li>
        </ul>

        <h3>Jeffrey Agent Events</h3>
        <p>The Jeffrey profiler agent can generate messages for:</p>
        <ul>
          <li><strong>Profiling status</strong> - Recording started/stopped</li>
          <li><strong>Configuration changes</strong> - Settings synchronized</li>
          <li><strong>Resource warnings</strong> - Disk space, memory limits</li>
        </ul>

        <h2 id="use-cases">Use Cases</h2>
        <p>Alerts and Messages help with various monitoring scenarios:</p>

        <h3>Incident Response</h3>
        <p>When investigating an issue:</p>
        <ol>
          <li>Check Alerts for high-priority events around the incident time</li>
          <li>Review Messages for complete context</li>
          <li>Correlate with profiles to understand root cause</li>
        </ol>

        <h3>Continuous Monitoring</h3>
        <p>For ongoing health monitoring:</p>
        <ul>
          <li>Monitor alert badge for new critical issues</li>
          <li>Review Messages periodically for patterns</li>
          <li>Use alerts to trigger investigation of specific profiles</li>
        </ul>

        <h3>Post-Mortem Analysis</h3>
        <p>After an incident:</p>
        <ul>
          <li>Review all messages in the timeframe</li>
          <li>Identify warning signs that preceded the issue</li>
          <li>Understand the sequence of events</li>
        </ul>

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

      <nav class="docs-nav-footer">
        <router-link
          v-if="adjacentPages.prev"
          :to="`/docs/${adjacentPages.prev.category}/${adjacentPages.prev.path}`"
          class="nav-link prev"
        >
          <i class="bi bi-arrow-left"></i>
          <div class="nav-text">
            <span class="nav-label">Previous</span>
            <span class="nav-title">{{ adjacentPages.prev.title }}</span>
          </div>
        </router-link>
        <div v-else class="nav-spacer"></div>
        <router-link
          v-if="adjacentPages.next"
          :to="`/docs/${adjacentPages.next.category}/${adjacentPages.next.path}`"
          class="nav-link next"
        >
          <div class="nav-text">
            <span class="nav-label">Next</span>
            <span class="nav-title">{{ adjacentPages.next.title }}</span>
          </div>
          <i class="bi bi-arrow-right"></i>
        </router-link>
      </nav>
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
