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
  { id: 'how-to-access', text: 'How to Access', level: 2 },
  { id: 'what-you-can-ask', text: 'What You Can Ask', level: 2 },
  { id: 'available-tools', text: 'Available Tools', level: 2 },
  { id: 'data-modification-mode', text: 'Data Modification Mode', level: 2 },
  { id: 'tips-for-best-results', text: 'Tips for Best Results', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="JFR Analysis"
      icon="bi bi-cpu"
    />

    <div class="docs-content">
      <p>The JFR Analysis Assistant lets you explore your profiling data through natural language conversations. It queries the profile database to answer questions about CPU usage, memory allocation, garbage collection, threading, and more.</p>

      <h2 id="how-to-access">How to Access</h2>
      <p>Navigate to any profile, then click <strong>AI JFR Analysis</strong> in the sidebar menu. Requires AI to be configured in Settings.</p>

      <DocsCallout type="info">
        <strong>Prerequisites:</strong> AI must be enabled and configured with a valid provider and API key in Settings before this feature is available.
      </DocsCallout>

      <h2 id="what-you-can-ask">What You Can Ask</h2>
      <p>You can ask questions about any aspect of your JFR recording. Here are examples organized by category:</p>

      <h3>CPU Profiling</h3>
      <ul>
        <li>"Which methods consume the most CPU time?"</li>
        <li>"Show me the hottest call paths"</li>
        <li>"Are there any methods that look like infinite loops?"</li>
      </ul>

      <h3>Memory</h3>
      <ul>
        <li>"What are the top allocation sites?"</li>
        <li>"Which methods allocate the most memory?"</li>
        <li>"Show TLAB allocation patterns"</li>
      </ul>

      <h3>Garbage Collection</h3>
      <ul>
        <li>"How many GC pauses occurred?"</li>
        <li>"What's the longest GC pause?"</li>
        <li>"Compare young vs old generation collection times"</li>
      </ul>

      <h3>Threading</h3>
      <ul>
        <li>"Which threads spend the most time waiting?"</li>
        <li>"Show contention hotspots"</li>
        <li>"Are there threads blocked on monitors?"</li>
      </ul>

      <h3>I/O</h3>
      <ul>
        <li>"Show file I/O patterns"</li>
        <li>"Which socket operations are slowest?"</li>
      </ul>

      <h2 id="available-tools">Available Tools</h2>
      <p>The AI has access to a set of tools for querying your profile data:</p>
      <ul>
        <li><strong>List event types</strong> &mdash; discover which event types are available in the recording</li>
        <li><strong>Query events</strong> &mdash; query specific event types with filtering and sorting</li>
        <li><strong>Execute SQL</strong> &mdash; run SQL queries against the profile database</li>
        <li><strong>Profile metadata</strong> &mdash; retrieve profile configuration and recording details</li>
      </ul>

      <DocsCallout type="tip">
        All queries are <strong>read-only by default</strong>, ensuring your profile data stays safe during analysis.
      </DocsCallout>

      <h2 id="data-modification-mode">Data Modification Mode</h2>
      <p>An optional mode that can be enabled per conversation. When active, the AI can modify profile data to help with cleanup tasks:</p>
      <ul>
        <li>Rename threads for better readability</li>
        <li>Anonymize frame names</li>
        <li>Clean up and normalize data</li>
      </ul>

      <DocsCallout type="warning">
        <strong>Use with caution:</strong> Modifications change the profile permanently. Consider creating a backup or working with a copy of the profile before enabling data modification mode.
      </DocsCallout>

      <h2 id="tips-for-best-results">Tips for Best Results</h2>
      <ul>
        <li><strong>Be specific</strong> &mdash; ask about particular event types, methods, or time ranges for more targeted answers</li>
        <li><strong>Ask follow-up questions</strong> &mdash; drill deeper into interesting findings</li>
        <li><strong>Use suggested prompts</strong> &mdash; the AI provides follow-up suggestions after each response to guide exploration</li>
        <li><strong>Start broad, then narrow</strong> &mdash; begin with overview questions and progressively focus on areas of interest</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
