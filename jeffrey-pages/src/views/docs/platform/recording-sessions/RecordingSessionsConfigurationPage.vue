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
import DocsLinkCard from '@/components/docs/DocsLinkCard.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

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
      <DocsPageHeader
        title="Configuration"
        icon="bi bi-gear"
      />

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

        <DocsLinkCard
          to="/docs/cli/overview"
          icon="bi bi-terminal"
          title="Jeffrey CLI"
          description="Learn how to use Jeffrey CLI to generate JVM arguments, configure profiling options, and set up recording sessions for your applications."
        />

        <p>The generated configuration includes:</p>
        <ul>
          <li><strong>Agent path</strong> - Location of the Async-Profiler library</li>
          <li><strong>Output directory</strong> - Where session files are written (monitored by Jeffrey)</li>
          <li><strong>Chunk settings</strong> - Duration and size limits for JFR file rotation</li>
          <li><strong>Event configuration</strong> - Which profiling events to capture</li>
          <li><strong>JFR repository path</strong> - For real-time ImportantMessage consumption</li>
        </ul>

        <DocsLinkCard
          to="/docs/cli/directory-structure"
          icon="bi bi-folder-fill"
          title="Directory Structure"
          description="Learn about the file and directory structure created by Jeffrey CLI for workspaces, projects, and recording sessions."
          variant="secondary"
        />
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

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
