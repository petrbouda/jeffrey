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
  { id: 'configuration-overview', text: 'Configuration Overview', level: 2 },
  { id: 'jeffrey-cli-setup', text: 'Jeffrey CLI Setup', level: 2 }
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
        <span class="breadcrumb-item">Platform</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item">Recording Sessions</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item active">Configuration</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-gear"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Configuration</h1>
        </div>
      </header>

      <div class="docs-content">
        <p>Recording sessions are configured using <router-link to="/docs/cli/overview">Jeffrey CLI</router-link>, which generates the necessary JVM arguments to start Async-Profiler with proper output directories and settings.</p>

        <h2 id="configuration-overview">Configuration Overview</h2>
        <p>To create recording sessions, you need to configure your Java application with:</p>

        <div class="config-steps">
          <div class="config-step">
            <div class="step-number">1</div>
            <div class="step-content">
              <h4>Profiler Settings</h4>
              <p>Define what events to capture (CPU, allocations, locks) and profiling parameters using <router-link to="/docs/concepts/projects/profiler-settings">Profiler Settings</router-link> in your project.</p>
            </div>
          </div>
          <div class="config-step">
            <div class="step-number">2</div>
            <div class="step-content">
              <h4>Jeffrey CLI</h4>
              <p>Use Jeffrey CLI to generate JVM arguments that configure Async-Profiler with the correct output paths and settings.</p>
            </div>
          </div>
          <div class="config-step">
            <div class="step-number">3</div>
            <div class="step-content">
              <h4>Application Startup</h4>
              <p>Start your Java application with the generated arguments. Recording sessions are created automatically as profiling begins.</p>
            </div>
          </div>
        </div>

        <h2 id="jeffrey-cli-setup">Jeffrey CLI Setup</h2>
        <p>Jeffrey CLI connects to your Jeffrey server and generates the JVM arguments needed to start profiling.</p>

        <router-link to="/docs/cli/overview" class="cli-link-card">
          <div class="cli-link-icon">
            <i class="bi bi-terminal"></i>
          </div>
          <div class="cli-link-content">
            <h4>Jeffrey CLI</h4>
            <p>Learn how to use Jeffrey CLI to generate JVM arguments, configure profiling options, and set up recording sessions for your applications.</p>
          </div>
          <i class="bi bi-arrow-right cli-link-arrow"></i>
        </router-link>

        <p>The generated configuration includes:</p>
        <ul>
          <li><strong>Agent path</strong> - Location of the Async-Profiler library</li>
          <li><strong>Output directory</strong> - Where session files are written (monitored by Jeffrey)</li>
          <li><strong>Chunk settings</strong> - Duration and size limits for JFR file rotation</li>
          <li><strong>Event configuration</strong> - Which profiling events to capture</li>
          <li><strong>JFR repository path</strong> - For real-time ImportantMessage consumption</li>
        </ul>

        <router-link to="/docs/cli/directory-structure" class="directory-link-card">
          <div class="directory-link-icon">
            <i class="bi bi-folder-fill"></i>
          </div>
          <div class="directory-link-content">
            <h4>Directory Structure</h4>
            <p>Learn about the file and directory structure created by Jeffrey CLI for workspaces, projects, and recording sessions.</p>
          </div>
          <i class="bi bi-arrow-right directory-link-arrow"></i>
        </router-link>
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

/* CLI Link Card */
.cli-link-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem 1.25rem;
  margin: 1.5rem 0;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-left: 4px solid #5e64ff;
  border-radius: 8px;
  text-decoration: none !important;
  color: inherit;
  transition: all 0.2s ease;
}

.cli-link-card:hover {
  background: linear-gradient(135deg, rgba(94,100,255,0.08) 0%, rgba(124,58,237,0.08) 100%);
  border-color: #5e64ff;
  transform: translateX(4px);
  text-decoration: none !important;
}

.cli-link-icon {
  width: 48px;
  height: 48px;
  min-width: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #5e64ff 0%, #7c3aed 100%);
  border-radius: 10px;
}

.cli-link-icon i {
  font-size: 1.25rem;
  color: #fff;
}

.cli-link-content {
  flex: 1;
}

.cli-link-content h4 {
  margin: 0 0 0.25rem 0;
  font-size: 1rem;
  font-weight: 600;
  color: #343a40;
  text-decoration: none !important;
}

.cli-link-content p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
  line-height: 1.4;
  text-decoration: none !important;
}

.cli-link-arrow {
  font-size: 1.25rem;
  color: #5e64ff;
  transition: transform 0.2s ease;
}

.cli-link-card:hover .cli-link-arrow {
  transform: translateX(4px);
}

/* Directory Structure Link */
.directory-link-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem 1.25rem;
  margin: 1.5rem 0;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-left: 4px solid #10b981;
  border-radius: 8px;
  text-decoration: none !important;
  color: inherit;
  transition: all 0.2s ease;
}

.directory-link-card:hover {
  background: linear-gradient(135deg, rgba(16,185,129,0.08) 0%, rgba(5,150,105,0.08) 100%);
  border-color: #10b981;
  transform: translateX(4px);
  text-decoration: none !important;
}

.directory-link-icon {
  width: 48px;
  height: 48px;
  min-width: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  border-radius: 10px;
}

.directory-link-icon i {
  font-size: 1.25rem;
  color: #fff;
}

.directory-link-content {
  flex: 1;
}

.directory-link-content h4 {
  margin: 0 0 0.25rem 0;
  font-size: 1rem;
  font-weight: 600;
  color: #343a40;
  text-decoration: none !important;
}

.directory-link-content p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
  line-height: 1.4;
  text-decoration: none !important;
}

.directory-link-arrow {
  font-size: 1.25rem;
  color: #10b981;
  transition: transform 0.2s ease;
}

.directory-link-card:hover .directory-link-arrow {
  transform: translateX(4px);
}

/* Config Steps */
.config-steps {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin: 1.5rem 0;
}

.config-step {
  display: flex;
  gap: 1rem;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.step-number {
  width: 32px;
  height: 32px;
  min-width: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #5e64ff 0%, #7c3aed 100%);
  color: #fff;
  font-weight: 700;
  font-size: 0.9rem;
  border-radius: 50%;
}

.step-content {
  flex: 1;
}

.step-content h4 {
  margin: 0 0 0.25rem 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: #343a40;
}

.step-content p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
  line-height: 1.4;
}

</style>
