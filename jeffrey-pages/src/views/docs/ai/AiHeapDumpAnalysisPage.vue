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
import DocsFeatureCard from '@/components/docs/DocsFeatureCard.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'how-to-access', text: 'How to Access', level: 2 },
  { id: 'analysis-capabilities', text: 'Analysis Capabilities', level: 2 },
  { id: 'example-questions', text: 'Example Questions', level: 2 },
  { id: 'how-the-ai-analyzes-heap-dumps', text: 'How the AI Analyzes Heap Dumps', level: 2 },
  { id: 'tips', text: 'Tips', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Heap Dump Analysis"
      icon="bi bi-memory"
    />

    <div class="docs-content">
      <p>The Heap Dump AI Analysis provides an AI-powered assistant for investigating Java heap dumps. It can browse objects, analyze memory usage patterns, identify leaks, and navigate the object graph.</p>

      <h2 id="how-to-access">How to Access</h2>
      <p>Navigate to any heap dump profile, then click <strong>AI Heap Dump Analysis</strong> in the sidebar menu.</p>

      <DocsCallout type="info">
        <strong>Prerequisites:</strong> AI must be enabled and configured with a valid provider and API key in Settings. The profile must contain a heap dump.
      </DocsCallout>

      <h2 id="analysis-capabilities">Analysis Capabilities</h2>
      <div class="docs-grid docs-grid-2">
        <DocsFeatureCard
          icon="bi bi-pie-chart"
          title="Memory Overview"
          description="Get a summary of total memory, object counts, and class distribution across the heap."
        />
        <DocsFeatureCard
          icon="bi bi-exclamation-triangle"
          title="Leak Detection"
          description="Identify memory leak suspects and analyze retained sizes to find objects that hold excessive memory."
          color="orange"
        />
        <DocsFeatureCard
          icon="bi bi-bar-chart"
          title="Class Histogram"
          description="Find which classes consume the most memory, sorted by shallow or retained size."
          color="blue"
        />
        <DocsFeatureCard
          icon="bi bi-diagram-3"
          title="Dominator Tree"
          description="Understand object retention — which objects keep others alive and prevent garbage collection."
          color="green"
        />
        <DocsFeatureCard
          icon="bi bi-search"
          title="Object Inspection"
          description="Browse individual object instances, their fields, and references to understand data structures."
          color="neutral"
        />
        <DocsFeatureCard
          icon="bi bi-fonts"
          title="String & Collection Analysis"
          description="Find string duplication opportunities and collections with poor fill ratios for optimization."
          color="red"
        />
      </div>

      <h2 id="example-questions">Example Questions</h2>
      <ul>
        <li>"What are the biggest objects in the heap?"</li>
        <li>"Are there any memory leak suspects?"</li>
        <li>"Show me the class histogram sorted by retained size"</li>
        <li>"What keeps this object alive? Trace the path to GC root"</li>
        <li>"Are there duplicate strings that could be deduplicated?"</li>
        <li>"Which collections have poor fill ratios?"</li>
      </ul>

      <h2 id="how-the-ai-analyzes-heap-dumps">How the AI Analyzes Heap Dumps</h2>
      <p>The AI follows a systematic strategy when investigating heap dumps:</p>
      <ol>
        <li><strong>Overview</strong> &mdash; starts with a heap summary including total size and object counts</li>
        <li><strong>Memory hotspots</strong> &mdash; identifies the largest consumers via the class histogram</li>
        <li><strong>Leak suspects</strong> &mdash; checks for potential memory leaks and unusually large objects</li>
        <li><strong>Instance inspection</strong> &mdash; drills down into specific object instances</li>
        <li><strong>Reference tracing</strong> &mdash; traces reference chains to understand why objects are retained</li>
        <li><strong>Optimization opportunities</strong> &mdash; checks collections and strings for inefficiencies</li>
      </ol>

      <DocsCallout type="tip">
        You do not have to follow this order yourself. Ask any question at any time and the AI will use the appropriate tools to find the answer.
      </DocsCallout>

      <h2 id="tips">Tips</h2>
      <ul>
        <li><strong>Start broad</strong> &mdash; ask for an overview first, then drill down into specific areas</li>
        <li><strong>Name specific classes</strong> &mdash; if you have suspicions about a particular class, mention it directly</li>
        <li><strong>Trace retention</strong> &mdash; use "trace path to GC root" to understand why objects are not collected</li>
        <li><strong>Follow suggestions</strong> &mdash; the AI suggests follow-up analyses after each response to guide deeper investigation</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
