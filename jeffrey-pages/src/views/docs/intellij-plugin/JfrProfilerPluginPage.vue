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
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'install', text: 'Install', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Java JFR Profiler Plugin"
      icon="bi bi-window-stack"
    />

    <div class="docs-content">
      <p>
        <strong>Java JFR Profiler</strong> is a third-party IntelliJ plugin for opening and
        analyzing Java Flight Recorder files inside the IDE. Microscope can route its
        <em>Open in IDE</em> and <em>View Source</em> requests to it as an alternative to the
        first-party
        <router-link to="/docs/intellij-plugin">Jeffrey Microscope Plugin</router-link>.
      </p>

      <h2 id="install">Install</h2>
      <p>
        Open the plugin page on the JetBrains Marketplace and click <strong>Install to IDE</strong>:
        <a href="https://plugins.jetbrains.com/plugin/20937-java-jfr-profiler" target="_blank" rel="noopener noreferrer">
          plugins.jetbrains.com/plugin/20937-java-jfr-profiler
        </a>.
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
