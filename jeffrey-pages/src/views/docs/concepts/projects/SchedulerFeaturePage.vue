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
  { id: 'available-jobs', text: 'Available Jobs', level: 2 },
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
        </div>
      </header>

      <div class="docs-content">
        <p>The Scheduler runs <strong>automated background jobs</strong> for continuous profiling scenarios, handling housekeeping tasks and synchronization.</p>

        <DocsCallout type="warning">
          <strong>Workspace Availability:</strong> Scheduler is only available in <strong>Live</strong> workspaces. It manages server-side automation for continuous profiling scenarios.
        </DocsCallout>

        <h2 id="what-is-scheduler">What is Scheduler?</h2>
        <p>In a Live workspace running on a server, various background tasks need to run automatically to manage repository data, compress recordings, and detect session completions. The Scheduler manages these jobs, running them on configurable schedules to keep your profiling infrastructure healthy.</p>

        <h2 id="available-jobs">Available Jobs</h2>

        <DocsCallout type="danger">
          <strong>Advanced Users Only!</strong> Jobs are created automatically with sensible defaults when a new project is set up. You can remove, add, or pause jobs from this page, but be aware that incorrect changes may disrupt recording collection or system behavior. If you need to customize the defaults, you can override them in the properties file at Jeffrey startup — these settings will then apply to all newly created projects.
        </DocsCallout>

        <p>The following jobs can be configured for each project:</p>

        <div class="job-cards">
          <div class="job-card">
            <div class="job-icon teal"><i class="bi bi-trash"></i></div>
            <div class="job-content">
              <h4>Repository Session Cleaner</h4>
              <p>Removes Repository Sessions older than the configured duration. When a session is removed, all associated recordings and artifacts (HeapDump, PerfCounters, etc.) are deleted as well.</p>
            </div>
          </div>

          <div class="job-card">
            <div class="job-icon teal"><i class="bi bi-trash"></i></div>
            <div class="job-content">
              <h4>Repository Recording Cleaner</h4>
              <p>Removes recordings only in the active (latest) Repository Session. Ensures that rolling recordings in the latest session are limited without affecting older sessions.</p>
            </div>
          </div>

          <div class="job-card">
            <div class="job-icon orange"><i class="bi bi-file-zip"></i></div>
            <div class="job-content">
              <h4>JFR Compression</h4>
              <p>Compresses finished JFR recording files using LZ4 compression to save storage space. Automatically processes active and latest finished sessions.</p>
            </div>
          </div>

          <div class="job-card">
            <div class="job-icon purple"><i class="bi bi-arrow-repeat"></i></div>
            <div class="job-content">
              <h4>Recording Storage Synchronizer</h4>
              <p>Synchronizes recording storage with the database by removing orphaned recordings that no longer exist in the database.</p>
            </div>
          </div>

          <div class="job-card">
            <div class="job-icon cyan"><i class="bi bi-check-circle"></i></div>
            <div class="job-content">
              <h4>Session Finished Detector</h4>
              <p>Detects when repository sessions become finished and emits SESSION_FINISHED workspace events. Uses detection file or timeout-based detection strategy.</p>
            </div>
          </div>

          <div class="job-card coming-soon">
            <div class="job-icon blue"><i class="bi bi-clock-history"></i></div>
            <div class="job-content">
              <h4>Download Recording Generator <span class="badge">Coming Soon</span></h4>
              <p>Creates a new recording from the repository by merging the last configured number of recordings and placing them in the Recordings section.</p>
            </div>
          </div>

          <div class="job-card coming-soon">
            <div class="job-icon blue"><i class="bi bi-arrow-repeat"></i></div>
            <div class="job-content">
              <h4>Periodic Recording Generator <span class="badge">Coming Soon</span></h4>
              <p>Creates recordings from the repository based on specified periods (e.g., every 15 minutes). Generated recordings are available in the Recordings section.</p>
            </div>
          </div>
        </div>

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

/* Job Cards */
.job-cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin: 1.5rem 0;
}

@media (max-width: 768px) {
  .job-cards {
    grid-template-columns: 1fr;
  }
}

.job-card {
  display: flex;
  gap: 1rem;
  padding: 1.25rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.job-card.coming-soon {
  opacity: 0.7;
}

.job-icon {
  width: 48px;
  height: 48px;
  min-width: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.job-icon i {
  font-size: 1.25rem;
  color: white;
}

.job-icon.teal { background: linear-gradient(135deg, #14b8a6, #0d9488); }
.job-icon.orange { background: linear-gradient(135deg, #f97316, #ea580c); }
.job-icon.purple { background: linear-gradient(135deg, #a855f7, #9333ea); }
.job-icon.cyan { background: linear-gradient(135deg, #06b6d4, #0891b2); }
.job-icon.blue { background: linear-gradient(135deg, #3b82f6, #2563eb); }

.job-content {
  flex: 1;
}

.job-content h4 {
  margin: 0 0 0.5rem 0;
  font-size: 1rem;
  font-weight: 600;
  color: #343a40;
}

.job-content h4 .badge {
  display: inline-block;
  padding: 0.2rem 0.5rem;
  font-size: 0.65rem;
  font-weight: 600;
  text-transform: uppercase;
  background: #fef3c7;
  color: #92400e;
  border-radius: 4px;
  margin-left: 0.5rem;
  vertical-align: middle;
}

.job-content p {
  margin: 0;
  font-size: 0.875rem;
  color: #5e6e82;
  line-height: 1.5;
}
</style>
