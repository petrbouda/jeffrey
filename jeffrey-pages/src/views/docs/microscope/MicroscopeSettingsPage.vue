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
  { id: 'ai-configuration', text: 'AI Configuration', level: 2 },
  { id: 'general', text: 'General', level: 2 },
  { id: 'visualization', text: 'Visualization', level: 2 },
  { id: 'persistence', text: 'Persistence', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Settings"
      icon="bi bi-sliders"
    />

    <div class="docs-content">
      <p>The <strong>Settings</strong> page provides an in-app UI for configuring Jeffrey Microscope at runtime. Settings are organized into three tabs: AI Configuration, General, and Visualization.</p>

      <DocsCallout type="info">
        <strong>Properties Files vs Settings UI:</strong> The Settings UI changes runtime configuration stored in Jeffrey's local database. For startup options like server port, AI provider keys via secrets, and bootstrap properties, see <router-link to="/docs/microscope/configuration/application-properties">Application Properties</router-link>.
      </DocsCallout>

      <h2 id="ai-configuration">AI Configuration</h2>
      <p>Enable and configure the AI provider used for JFR analysis, heap dump analysis, and OQL Assistant.</p>

      <div class="settings-list">
        <div class="setting-item">
          <div class="setting-header">
            <i class="bi bi-toggle-on"></i>
            <strong>Enable AI</strong>
          </div>
          <p>Master switch for all AI features. When disabled, AI-powered analysis is unavailable.</p>
        </div>
        <div class="setting-item">
          <div class="setting-header">
            <i class="bi bi-cloud"></i>
            <strong>Provider</strong>
          </div>
          <p>Choose between Claude (Anthropic) or ChatGPT (OpenAI). Each provider has its own model list and API key.</p>
        </div>
        <div class="setting-item">
          <div class="setting-header">
            <i class="bi bi-cpu"></i>
            <strong>Model</strong>
          </div>
          <p>Select the specific model variant to use (e.g. <code>claude-opus-4-6</code>, <code>gpt-4</code>). Available models depend on the chosen provider.</p>
        </div>
        <div class="setting-item">
          <div class="setting-header">
            <i class="bi bi-key"></i>
            <strong>API Key</strong>
          </div>
          <p>Provider API key. Stored locally in the Jeffrey database.</p>
        </div>
      </div>

      <DocsCallout type="tip">
        <strong>Default model:</strong> Jeffrey ships with <code>jeffrey.ai.provider=claude</code> and <code>jeffrey.ai.model=claude-opus-4-6</code> as defaults.
      </DocsCallout>

      <h2 id="general">General</h2>
      <p>General application settings affecting Jeffrey's runtime behavior.</p>

      <div class="settings-list">
        <div class="setting-item">
          <div class="setting-header">
            <i class="bi bi-file-text"></i>
            <strong>Log Level</strong>
          </div>
          <p>Control the verbosity of Jeffrey's application logs. Useful for troubleshooting (set to <code>DEBUG</code>) or reducing noise (set to <code>WARN</code>).</p>
        </div>
      </div>

      <h2 id="visualization">Visualization</h2>
      <p>Customize how flame graphs and other visualizations are rendered.</p>

      <div class="settings-list">
        <div class="setting-item">
          <div class="setting-header">
            <i class="bi bi-fire"></i>
            <strong>Flamegraph Frame Threshold</strong>
          </div>
          <p>Minimum sample percentage required for a frame to be displayed in flame graphs. Lower values show more detail but increase rendering complexity.</p>
        </div>
        <div class="setting-item">
          <div class="setting-header">
            <i class="bi bi-fonts"></i>
            <strong>Text Mode</strong>
          </div>
          <p>Choose how method names are rendered (full, compact, abbreviated) to balance readability and information density.</p>
        </div>
      </div>

      <h2 id="persistence">Persistence</h2>
      <p>All settings configured in this UI are persisted to the local Jeffrey database. Changes take effect immediately and persist across restarts.</p>

      <DocsCallout type="info">
        <strong>Configuration precedence:</strong> Runtime settings from this UI override defaults from <code>application.properties</code>. To reset a setting to its default, clear the value in the UI.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

.settings-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 1.5rem 0;
}

.setting-item {
  padding: 1rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.setting-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.setting-header i {
  font-size: 1rem;
  color: #5e64ff;
}

.setting-header strong {
  font-size: 0.9rem;
  color: #343a40;
}

.setting-item p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
  padding-left: 1.5rem;
}
</style>
