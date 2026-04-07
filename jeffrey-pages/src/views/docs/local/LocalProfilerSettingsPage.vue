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
  { id: 'tabs', text: 'Tabs', level: 2 },
  { id: 'visual-builder', text: 'Visual Builder', level: 2 },
  { id: 'manual-mode', text: 'Manual Mode', level: 2 },
  { id: 'view-hierarchy', text: 'View & Hierarchy', level: 2 },
  { id: 'related', text: 'Related', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Profiler Settings"
      icon="bi bi-cpu"
    />

    <div class="docs-content">
      <p>The standalone <strong>Profiler Settings</strong> page provides a top-level entry point for managing Async-Profiler configurations across all hierarchy levels (global, workspace, project). It is designed for browsing existing settings and creating new ones independently of any specific project.</p>

      <DocsCallout type="info">
        <strong>Where to find it:</strong> The Profiler Settings page is accessible from the main Jeffrey Local navigation, separate from project-level Profiler Settings.
      </DocsCallout>

      <h2 id="tabs">Tabs</h2>
      <p>The page is organized into three tabs:</p>

      <div class="tab-list">
        <div class="tab-item-doc">
          <div class="tab-header">
            <i class="bi bi-pencil"></i>
            <strong>Manual</strong>
          </div>
          <p>Write the Async-Profiler command directly. Useful when you already know the exact options you want.</p>
        </div>
        <div class="tab-item-doc">
          <div class="tab-header">
            <i class="bi bi-grid-3x3-gap"></i>
            <strong>Visual Builder</strong>
          </div>
          <p>Step-through wizard that generates the profiler command from form inputs. Validates options and shows the generated command in real time.</p>
        </div>
        <div class="tab-item-doc">
          <div class="tab-header">
            <i class="bi bi-eye"></i>
            <strong>View</strong>
          </div>
          <p>Browse all profiler settings across the hierarchy. See which workspace or project overrides global defaults and inspect resolved values.</p>
        </div>
      </div>

      <h2 id="visual-builder">Visual Builder</h2>
      <p>The Visual Builder generates Async-Profiler command arguments from a structured form, so you don't need to memorize options. It covers:</p>
      <ul>
        <li><strong>Mandatory options</strong> — agent path, output file pattern, loop duration</li>
        <li><strong>Event toggles</strong> — CPU, allocation, locks, wall-clock, method tracing, native memory</li>
        <li><strong>Per-event options</strong> — sampling intervals, thresholds, modes</li>
        <li><strong>Advanced options</strong> — JFR synchronization (jfrsync), chunk size, chunk time</li>
      </ul>

      <DocsCallout type="tip">
        <strong>Generated command:</strong> The builder displays the active Async-Profiler arguments in real time, so you can copy them or fine-tune by switching to Manual mode.
      </DocsCallout>

      <h2 id="manual-mode">Manual Mode</h2>
      <p>Manual mode lets you type the profiler command directly. Use it when you have an existing command from another source or want full control over the configuration.</p>

      <h2 id="view-hierarchy">View & Hierarchy</h2>
      <p>Profiler settings follow a three-level inheritance model:</p>

      <div class="hierarchy-diagram">
        <div class="hierarchy-level global">
          <span class="level-label">Global</span>
          <span class="level-desc">Defaults for all workspaces and projects</span>
        </div>
        <div class="hierarchy-arrow"><i class="bi bi-arrow-down"></i></div>
        <div class="hierarchy-level workspace">
          <span class="level-label">Workspace</span>
          <span class="level-desc">Override defaults for a specific workspace</span>
        </div>
        <div class="hierarchy-arrow"><i class="bi bi-arrow-down"></i></div>
        <div class="hierarchy-level project">
          <span class="level-label">Project</span>
          <span class="level-desc">Override for a specific project (highest priority)</span>
        </div>
      </div>

      <p>The View tab shows all configured settings across this hierarchy, making it easy to spot inconsistencies and understand which configuration applies where.</p>

      <h2 id="related">Related</h2>
      <p>For project-specific profiler settings inside a Remote workspace, see <router-link to="/docs/local/projects/profiler-settings">Projects → Profiler Settings</router-link>. The same options are available there, scoped to a single project.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

.tab-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 1.5rem 0;
}

.tab-item-doc {
  padding: 1rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.tab-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.tab-header i {
  font-size: 1rem;
  color: #5e64ff;
}

.tab-header strong {
  font-size: 0.9rem;
  color: #343a40;
}

.tab-item-doc p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
  padding-left: 1.5rem;
}

/* Hierarchy Diagram */
.hierarchy-diagram {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  margin: 1.5rem 0;
  padding: 1.5rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.hierarchy-level {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0.75rem 2rem;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e2e8f0;
  min-width: 280px;
}

.hierarchy-level .level-label {
  font-weight: 600;
  font-size: 0.9rem;
  color: #343a40;
}

.hierarchy-level .level-desc {
  font-size: 0.75rem;
  color: #6c757d;
  margin-top: 0.25rem;
  text-align: center;
}

.hierarchy-level.global {
  border-color: rgba(107, 114, 128, 0.3);
}

.hierarchy-level.global .level-label {
  color: #6b7280;
}

.hierarchy-level.workspace {
  border-color: rgba(94, 100, 255, 0.3);
}

.hierarchy-level.workspace .level-label {
  color: #5e64ff;
}

.hierarchy-level.project {
  border-color: rgba(16, 185, 129, 0.3);
}

.hierarchy-level.project .level-label {
  color: #10b981;
}

.hierarchy-arrow {
  color: #94a3b8;
  font-size: 1.25rem;
}
</style>
