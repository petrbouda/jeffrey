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
import DocsFeatureItem from '@/components/docs/DocsFeatureItem.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'flamegraph-enhancements', text: 'Flamegraph Enhancements', level: 2 },
  { id: 'version-update-check', text: 'Version Update Check', level: 2 },
  { id: 'workspace-streaming', text: 'Workspace Streaming Control', level: 2 },
  { id: 'jdbc-dashboard', text: 'JDBC Dashboard', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Highlighted Features"
        icon="bi bi-stars"
      />

      <div class="docs-content">
        <p>Recent additions and improvements to Jeffrey.</p>

        <h2 id="flamegraph-enhancements">Flamegraph Enhancements</h2>
        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-scissors" title="Frame Pruning">
            Small frames below a configurable threshold (default 0.05%) are automatically hidden to reduce visual clutter. Adjust the threshold per flamegraph to control the level of detail.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-fonts" title="Text Rendering Modes">
            Choose between single-line and two-line frame labels. Two-line mode shows class/package and method name separately, improving readability in wide flamegraphs.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-chat-square-text" title="Enhanced Tooltips">
            Richer tooltips with frame metadata including sample counts, percentages, and full method signatures.
          </DocsFeatureItem>
        </div>

        <h2 id="version-update-check">Version Update Check</h2>
        <p>Jeffrey automatically checks for new releases on GitHub and shows a toast notification when a newer version is available. Minor updates appear in blue, major updates in orange with a breaking-changes warning. A download button lets you grab <code>jeffrey.jar</code> directly from the release.</p>

        <h2 id="workspace-streaming">Workspace Streaming Control</h2>
        <p>The workspace context bar now includes a settings popover (gear icon) with a segmented toggle to control JFR streaming per workspace. Choose between <strong>On</strong>, <strong>Off</strong>, or <strong>Inherited</strong> (from the global server setting).</p>

        <h2 id="jdbc-dashboard">JDBC Dashboard</h2>
        <p>The Database section features a redesigned dashboard with a <strong>searchable filter bar</strong> for quickly finding specific SQL statement groups. Statement Groups and Connection Pools views include improved filtering and sorting capabilities.</p>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
