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
  { id: 'what-are-projects', text: 'What are Projects?', level: 2 },
  { id: 'project-features', text: 'Project Features', level: 2 },
  { id: 'feature-availability', text: 'Feature Availability', level: 2 },
  { id: 'creating-projects', text: 'Creating Projects', level: 2 }
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
        <span class="breadcrumb-item active">Projects</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-kanban"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Projects</h1>
        </div>
      </header>

      <div class="docs-content">
        <p>Projects are containers within workspaces that organize your recordings, profiles, and analysis settings for a specific application or service.</p>

        <h2 id="what-are-projects">What are Projects?</h2>
        <p>A project represents a specific application or service you want to profile. Each project provides:</p>
        <ul>
          <li>Organized storage for JFR recordings</li>
          <li>Generated profiles for analysis</li>
          <li>Configuration settings for profiler agents</li>
          <li>Automated scheduling for continuous profiling scenarios</li>
        </ul>

        <p>You might create separate projects for:</p>
        <ul>
          <li>Different microservices in your architecture</li>
          <li>Different environments (dev, staging, production)</li>
          <li>Different profiling scenarios (performance testing, production monitoring)</li>
        </ul>

        <h2 id="project-features">Project Features</h2>
        <p>Each project includes several features accessible from the sidebar. Click on any feature to learn more:</p>

        <div class="features-grid">
          <router-link to="/docs/concepts/projects/profiles" class="feature-card profiles">
            <div class="feature-icon"><i class="bi bi-file-earmark-text"></i></div>
            <h4>Profiles</h4>
            <p>Analyzed recordings ready for investigation</p>
          </router-link>
          <router-link to="/docs/concepts/projects/recordings" class="feature-card recordings">
            <div class="feature-icon"><i class="bi bi-record-circle"></i></div>
            <h4>Recordings</h4>
            <p>Raw JFR files uploaded to Jeffrey</p>
          </router-link>
          <router-link to="/docs/concepts/projects/repository" class="feature-card repository">
            <div class="feature-icon"><i class="bi bi-folder"></i></div>
            <h4>Repository</h4>
            <p>Recording sessions from live applications</p>
          </router-link>
          <router-link to="/docs/concepts/projects/profiler-settings" class="feature-card settings">
            <div class="feature-icon"><i class="bi bi-cpu"></i></div>
            <h4>Profiler Settings</h4>
            <p>JVM agent configuration</p>
          </router-link>
          <router-link to="/docs/concepts/projects/scheduler" class="feature-card scheduler">
            <div class="feature-icon"><i class="bi bi-calendar-check"></i></div>
            <h4>Scheduler</h4>
            <p>Automated background jobs</p>
          </router-link>
          <router-link to="/docs/concepts/projects/alerts-messages" class="feature-card alerts">
            <div class="feature-icon"><i class="bi bi-bell"></i></div>
            <h4>Alerts & Messages</h4>
            <p>Important events from recordings</p>
          </router-link>
        </div>

        <h2 id="feature-availability">Feature Availability</h2>
        <p>Not all features are available in every workspace type:</p>

        <table>
          <thead>
            <tr>
              <th>Feature</th>
              <th>Sandbox</th>
              <th>Live</th>
              <th>Remote</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><i class="bi bi-file-earmark-text me-2"></i>Profiles</td>
              <td><i class="bi bi-check-lg text-success"></i></td>
              <td><i class="bi bi-check-lg text-success"></i></td>
              <td><i class="bi bi-check-lg text-success"></i></td>
            </tr>
            <tr>
              <td><i class="bi bi-record-circle me-2"></i>Recordings</td>
              <td><i class="bi bi-check-lg text-success"></i></td>
              <td><i class="bi bi-check-lg text-success"></i></td>
              <td><i class="bi bi-check-lg text-success"></i></td>
            </tr>
            <tr>
              <td><i class="bi bi-folder me-2"></i>Repository</td>
              <td><i class="bi bi-x-lg text-muted"></i></td>
              <td><i class="bi bi-check-lg text-success"></i></td>
              <td><i class="bi bi-check-lg text-success"></i></td>
            </tr>
            <tr>
              <td><i class="bi bi-cpu me-2"></i>Profiler Settings</td>
              <td><i class="bi bi-x-lg text-muted"></i></td>
              <td><i class="bi bi-check-lg text-success"></i></td>
              <td><i class="bi bi-check-lg text-success"></i></td>
            </tr>
            <tr>
              <td><i class="bi bi-calendar-check me-2"></i>Scheduler</td>
              <td><i class="bi bi-x-lg text-muted"></i></td>
              <td><i class="bi bi-check-lg text-success"></i></td>
              <td><i class="bi bi-x-lg text-muted"></i></td>
            </tr>
            <tr>
              <td><i class="bi bi-bell me-2"></i>Alerts & Messages</td>
              <td><i class="bi bi-x-lg text-muted"></i></td>
              <td><i class="bi bi-check-lg text-success"></i></td>
              <td><i class="bi bi-check-lg text-success"></i></td>
            </tr>
          </tbody>
        </table>

        <DocsCallout type="tip">
          <strong>Sandbox is simplest:</strong> If you just want to upload and analyze JFR files locally, Sandbox provides everything you need with Profiles and Recordings.
        </DocsCallout>

        <h2 id="creating-projects">Creating Projects</h2>
        <p>To create a new project:</p>
        <ol>
          <li>Navigate to your workspace</li>
          <li>Click <strong>"Add Project"</strong></li>
          <li>Enter a project name</li>
          <li>Click <strong>"Create"</strong></li>
        </ol>

        <p>Your project is now ready. Navigate into it to start uploading recordings or configuring profiler settings.</p>
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

/* Features Grid */
.features-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin: 1.5rem 0;
}

.feature-card {
  display: block;
  padding: 1.25rem;
  border-radius: 8px;
  text-align: center;
  border: 1px solid #e2e8f0;
  background: #fff;
  text-decoration: none !important;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.feature-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  text-decoration: none !important;
}

.feature-card .feature-icon {
  width: 44px;
  height: 44px;
  margin: 0 auto 0.75rem;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.25rem;
  color: #fff;
}

.feature-card h4 {
  margin: 0 0 0.5rem 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: #343a40;
  text-decoration: none;
}

.feature-card:hover h4,
.feature-card:hover p {
  text-decoration: none;
}

.feature-card p {
  margin: 0;
  font-size: 0.8rem;
  color: #6c757d;
}

/* Feature card themes */
.feature-card.profiles {
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.06) 0%, rgba(94, 100, 255, 0.02) 100%);
  border-color: rgba(94, 100, 255, 0.25);
}

.feature-card.profiles .feature-icon {
  background: linear-gradient(135deg, #5e64ff 0%, #4338ca 100%);
}

.feature-card.recordings {
  background: linear-gradient(135deg, rgba(239, 68, 68, 0.06) 0%, rgba(239, 68, 68, 0.02) 100%);
  border-color: rgba(239, 68, 68, 0.25);
}

.feature-card.recordings .feature-icon {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
}

.feature-card.repository {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.06) 0%, rgba(16, 185, 129, 0.02) 100%);
  border-color: rgba(16, 185, 129, 0.25);
}

.feature-card.repository .feature-icon {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
}

.feature-card.settings {
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.06) 0%, rgba(245, 158, 11, 0.02) 100%);
  border-color: rgba(245, 158, 11, 0.25);
}

.feature-card.settings .feature-icon {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
}

.feature-card.scheduler {
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.06) 0%, rgba(139, 92, 246, 0.02) 100%);
  border-color: rgba(139, 92, 246, 0.25);
}

.feature-card.scheduler .feature-icon {
  background: linear-gradient(135deg, #8b5cf6 0%, #7c3aed 100%);
}

.feature-card.alerts {
  background: linear-gradient(135deg, rgba(236, 72, 153, 0.06) 0%, rgba(236, 72, 153, 0.02) 100%);
  border-color: rgba(236, 72, 153, 0.25);
}

.feature-card.alerts .feature-icon {
  background: linear-gradient(135deg, #ec4899 0%, #db2777 100%);
}

/* Responsive */
@media (max-width: 768px) {
  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 480px) {
  .features-grid {
    grid-template-columns: 1fr;
  }
}
</style>
