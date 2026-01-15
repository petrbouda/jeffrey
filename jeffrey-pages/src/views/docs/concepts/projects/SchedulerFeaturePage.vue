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
  { id: 'what-is-scheduler', text: 'What is Scheduler?', level: 2 },
  { id: 'job-types', text: 'Job Types', level: 2 },
  { id: 'job-management', text: 'Job Management', level: 2 },
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
        <span class="breadcrumb-item active">Scheduler</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-calendar-check"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Scheduler</h1>
          <p class="docs-section-badge">Projects</p>
        </div>
      </header>

      <div class="docs-content">
        <p>The Scheduler runs <strong>automated background jobs</strong> for continuous profiling scenarios, handling housekeeping tasks and synchronization.</p>

        <DocsCallout type="warning">
          <strong>Workspace Availability:</strong> Scheduler is only available in <strong>Live</strong> workspaces. It manages server-side automation for continuous profiling scenarios.
        </DocsCallout>

        <h2 id="what-is-scheduler">What is Scheduler?</h2>
        <p>In a Live workspace running on a server, various background tasks need to run automatically:</p>
        <ul>
          <li>Detecting new recording sessions from applications</li>
          <li>Cleaning up old recordings to free disk space</li>
          <li>Synchronizing profiler settings to agents</li>
          <li>Database maintenance and optimization</li>
        </ul>

        <p>The Scheduler manages these jobs, running them on configurable schedules to keep your profiling infrastructure healthy.</p>

        <h2 id="job-types">Job Types</h2>
        <p>The scheduler executes several categories of jobs:</p>

        <h3>Detection Jobs</h3>
        <p>Automatically discover new data:</p>
        <ul>
          <li><strong>Session Detection</strong> - Find new recording sessions in the repository</li>
          <li><strong>Recording Detection</strong> - Discover new JFR files</li>
          <li><strong>Artifact Detection</strong> - Find associated logs, heap dumps, etc.</li>
        </ul>

        <h3>Cleanup Jobs</h3>
        <p>Manage disk space and remove old data:</p>
        <ul>
          <li><strong>Recording Cleanup</strong> - Remove old recordings based on retention policy</li>
          <li><strong>Session Cleanup</strong> - Clean up completed sessions</li>
          <li><strong>Temporary File Cleanup</strong> - Remove temporary processing files</li>
        </ul>

        <h3>Sync Jobs</h3>
        <p>Keep configuration synchronized:</p>
        <ul>
          <li><strong>Profiler Settings Sync</strong> - Push settings to connected agents</li>
          <li><strong>Workspace Status Sync</strong> - Update workspace availability status</li>
        </ul>

        <h3>Maintenance Jobs</h3>
        <p>Keep the system healthy:</p>
        <ul>
          <li><strong>Database Optimization</strong> - Vacuum and optimize storage</li>
          <li><strong>Index Maintenance</strong> - Update database indexes</li>
        </ul>

        <h2 id="job-management">Job Management</h2>
        <p>In the Scheduler view, you can manage background jobs:</p>

        <h3>Job Information</h3>
        <p>For each job, you can see:</p>
        <table>
          <thead>
            <tr>
              <th>Field</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Job name</td>
              <td>Descriptive name of the job</td>
            </tr>
            <tr>
              <td>Schedule</td>
              <td>CRON expression for when it runs</td>
            </tr>
            <tr>
              <td>Last run</td>
              <td>When it last executed</td>
            </tr>
            <tr>
              <td>Next run</td>
              <td>When it will run next</td>
            </tr>
            <tr>
              <td>Status</td>
              <td>Success, failed, or running</td>
            </tr>
          </tbody>
        </table>

        <h3>Actions</h3>
        <ul>
          <li><strong>Run Now</strong> - Manually trigger a job immediately</li>
          <li><strong>View History</strong> - See past execution results</li>
          <li><strong>Edit Schedule</strong> - Change when the job runs</li>
          <li><strong>Enable/Disable</strong> - Turn jobs on or off</li>
        </ul>

        <DocsCallout type="tip">
          <strong>Manual triggers:</strong> Use "Run Now" when you want immediate results without waiting for the next scheduled run. This is useful after uploading new recordings or changing settings.
        </DocsCallout>

        <h3>CRON Schedules</h3>
        <p>Jobs use CRON expressions to define their schedules. Common patterns:</p>
        <table>
          <thead>
            <tr>
              <th>Expression</th>
              <th>Meaning</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><code>0 * * * *</code></td>
              <td>Every hour</td>
            </tr>
            <tr>
              <td><code>*/5 * * * *</code></td>
              <td>Every 5 minutes</td>
            </tr>
            <tr>
              <td><code>0 0 * * *</code></td>
              <td>Daily at midnight</td>
            </tr>
            <tr>
              <td><code>0 0 * * 0</code></td>
              <td>Weekly on Sunday</td>
            </tr>
          </tbody>
        </table>

        <h2 id="workspace-availability">Workspace Availability</h2>
        <p>Scheduler availability depends on workspace type:</p>

        <table>
          <thead>
            <tr>
              <th>Workspace</th>
              <th>Scheduler</th>
              <th>Reason</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><strong>Sandbox</strong></td>
              <td><i class="bi bi-x-lg text-muted"></i> Not available</td>
              <td>Manual uploads only - no automation needed</td>
            </tr>
            <tr>
              <td><strong>Live</strong></td>
              <td><i class="bi bi-check-lg text-success"></i> Available</td>
              <td>Server-side automation for continuous profiling</td>
            </tr>
            <tr>
              <td><strong>Remote</strong></td>
              <td><i class="bi bi-x-lg text-muted"></i> Not available</td>
              <td>Jobs run on the remote server, not locally</td>
            </tr>
          </tbody>
        </table>

        <DocsCallout type="info">
          <strong>Remote workspaces:</strong> When using Remote workspaces, the scheduler runs on the remote Live workspace's server. You don't need local scheduling - you're just mirroring and downloading data.
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
