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
  { id: 'what-are-projects', text: 'What are Projects?', level: 2 },
  { id: 'project-page', text: 'The Project Page', level: 2 },
  { id: 'project-settings', text: 'Project Settings', level: 2 },
  { id: 'creating-projects', text: 'Creating Projects', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Projects"
      icon="bi bi-kanban"
    />

    <div class="docs-content">
      <p>A <strong>project</strong> is a folder inside a workspace that groups everything related to a single application or service — its JVM instances, recording sessions, live event streams, and profiler configuration.</p>

      <h2 id="what-are-projects">What are Projects?</h2>
      <p>You typically create one project per application or environment so the data stays organised:</p>
      <ul>
        <li>One project per microservice in your architecture.</li>
        <li>One project per environment — <em>dev</em>, <em>staging</em>, <em>production</em>.</li>
        <li>One project per profiling scenario — <em>perf-test</em>, <em>baseline</em>, <em>incident-2026-04</em>.</li>
      </ul>

      <DocsCallout type="tip">
        <strong>Just want to analyse a JFR file?</strong> Skip projects entirely — drop the file on the global
        <router-link to="/docs/microscope/recordings">Recordings</router-link> page.
        Projects are for staying connected to live applications via Jeffrey Server.
      </DocsCallout>

      <h2 id="project-page">The Project Page</h2>
      <p>Open a project and the page is organised into six tabs. Each one is a focused view; pick the one that matches what you're doing right now.</p>

      <div class="tab-grid">
        <router-link to="/docs/microscope/projects/instances" class="tab-card">
          <div class="tab-icon"><i class="bi bi-bar-chart-steps"></i></div>
          <h4>Timeline</h4>
          <p>Swimlane view of every instance and session over time.</p>
        </router-link>
        <router-link to="/docs/microscope/projects/instances" class="tab-card">
          <div class="tab-icon"><i class="bi bi-grid"></i></div>
          <h4>Instances</h4>
          <p>JVM processes connected to the project, with status, storage, and file-type breakdown.</p>
        </router-link>
        <router-link to="/docs/microscope/projects/event-streaming#live-stream" class="tab-card">
          <div class="tab-icon"><i class="bi bi-broadcast"></i></div>
          <h4>Live Stream</h4>
          <p>Real-time JFR events flowing from a running JVM.</p>
        </router-link>
        <router-link to="/docs/microscope/projects/event-streaming#replay-stream" class="tab-card">
          <div class="tab-icon"><i class="bi bi-collection-play"></i></div>
          <h4>Replay Stream</h4>
          <p>Walk historical events from a finished session — same UI, time-travel mode.</p>
        </router-link>
        <router-link to="/docs/microscope/projects/profiler-settings" class="tab-card">
          <div class="tab-icon"><i class="bi bi-cpu"></i></div>
          <h4>Profiler Settings</h4>
          <p>Async-Profiler configuration for the agents that feed this project.</p>
        </router-link>
        <a href="#project-settings" class="tab-card">
          <div class="tab-icon"><i class="bi bi-sliders"></i></div>
          <h4>Settings</h4>
          <p>Rename or delete the project.</p>
        </a>
      </div>

      <DocsCallout type="info">
        <strong>Where did "Profiles", "Recordings", and "Repository" go?</strong>
        Profiles and recordings now live globally on the
        <router-link to="/docs/microscope/recordings">Recordings</router-link> page, regardless of project.
        Repository totals (storage size, file count, biggest session) are now a stat card on the
        <router-link to="/docs/microscope/projects/instances">Instances</router-link> tab.
      </DocsCallout>

      <h2 id="project-settings">Project Settings</h2>
      <p>The <strong>Settings</strong> tab is intentionally minimal. It exposes two things:</p>
      <ul>
        <li><strong>Rename</strong> — change the project's display name.</li>
        <li><strong>Delete</strong> — remove the project (a confirmation step protects against accidents).</li>
      </ul>

      <h2 id="creating-projects">Creating Projects</h2>
      <p>To create a new project:</p>
      <ol>
        <li>Open <strong>Workspaces</strong> in the top navigation and pick a workspace.</li>
        <li>Click <strong>Add Project</strong>.</li>
        <li>Enter a project name.</li>
        <li>Click <strong>Create</strong>.</li>
      </ol>
      <p>The new project shows up in the workspace immediately. From there you can wire up profiler agents, watch live streams, and review past sessions.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

.tab-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.85rem;
  margin: 1.25rem 0 1.5rem;
}

.tab-grid .tab-card {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  padding: 1rem 1rem 0.95rem;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  text-decoration: none;
  color: inherit;
  transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
}

.tab-grid .tab-card:hover {
  transform: translateY(-2px);
  border-color: #c7d2fe;
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.06);
  text-decoration: none;
  color: inherit;
}

.tab-grid .tab-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 9px;
  background: #ede9fe;
  color: #5e64ff;
  font-size: 1rem;
}

.tab-grid h4 {
  margin: 0.4rem 0 0.1rem;
  font-size: 0.95rem;
  font-weight: 650;
  color: #0f172a;
  letter-spacing: -0.01em;
}

.tab-grid p {
  margin: 0;
  font-size: 0.83rem;
  line-height: 1.45;
  color: #475569;
}

@media (max-width: 992px) {
  .tab-grid { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 576px) {
  .tab-grid { grid-template-columns: 1fr; }
}
</style>
