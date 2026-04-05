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
import DocsLinkCard from '@/components/docs/DocsLinkCard.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'ai-features', text: 'AI Features', level: 2 },
  { id: 'configuration', text: 'Configuration', level: 2 },
  { id: 'supported-providers', text: 'Supported Providers', level: 2 },
  { id: 'how-it-works', text: 'How It Works', level: 2 },
  { id: 'get-started', text: 'Get Started', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="AI Analysis"
      icon="bi bi-robot"
    />

    <div class="docs-content">
      <p>Jeffrey integrates AI-powered analysis to help you understand JFR recordings and heap dumps faster. Ask questions in natural language and get insights powered by Claude or ChatGPT.</p>

      <h2 id="ai-features">AI Features</h2>
      <div class="docs-grid docs-grid-2">
        <DocsFeatureCard
          icon="bi bi-cpu"
          title="JFR Analysis"
          description="Ask questions about your JFR profiling data in natural language. The AI queries the underlying database to find answers about CPU usage, memory allocation, threading, GC behavior, and more."
        />
        <DocsFeatureCard
          icon="bi bi-memory"
          title="Heap Dump Analysis"
          description="Comprehensive AI-driven heap dump analysis. The AI explores class histograms, dominator trees, leak suspects, and object details to identify memory issues."
        />
        <DocsFeatureCard
          icon="bi bi-chat-square-text"
          title="OQL Assistant"
          description="Generate Object Query Language (OQL) queries from natural language descriptions. Useful for complex heap dump investigations."
        />
      </div>

      <h2 id="configuration">Configuration</h2>
      <p>AI analysis is <strong>disabled by default</strong>. To enable it, configure an AI provider through <strong>Settings</strong> in the Jeffrey Local UI.</p>

      <table>
        <thead>
          <tr>
            <th>Setting</th>
            <th>Description</th>
            <th>Default</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><strong>Provider</strong></td>
            <td>AI service provider (Claude or ChatGPT)</td>
            <td>None (disabled)</td>
          </tr>
          <tr>
            <td><strong>Model</strong></td>
            <td>Model name to use</td>
            <td>claude-opus-4-6</td>
          </tr>
          <tr>
            <td><strong>API Key</strong></td>
            <td>Your provider API key</td>
            <td>&mdash;</td>
          </tr>
          <tr>
            <td><strong>Max Tokens</strong></td>
            <td>Maximum tokens per request</td>
            <td>128000</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="info">
        <strong>Security:</strong> API keys are encrypted at rest. They are never exposed in logs or API responses.
      </DocsCallout>

      <h2 id="supported-providers">Supported Providers</h2>

      <h3>Claude (Anthropic)</h3>
      <p>Recommended provider. Available models include <code>claude-opus-4-6</code> and <code>claude-sonnet-4-6</code>.</p>

      <h3>ChatGPT (OpenAI)</h3>
      <p>Alternative provider. Available models include <code>gpt-4o</code>, <code>gpt-4-turbo</code>, and others.</p>

      <h2 id="how-it-works">How It Works</h2>
      <p>Jeffrey uses <strong>tool-calling</strong> (MCP-style) to provide accurate, data-driven answers:</p>
      <ul>
        <li>The AI does not see raw data directly &mdash; it calls tools to query databases, browse objects, and execute queries</li>
        <li>This approach produces accurate answers grounded in your actual data rather than hallucinated guesses</li>
        <li>All database queries are <strong>read-only by default</strong> for safety</li>
        <li>The AI can reference specific events, stack traces, and metrics from your recordings</li>
      </ul>

      <h2 id="get-started">Get Started</h2>

      <DocsLinkCard
        to="/docs/ai/jfr-analysis"
        icon="bi bi-cpu"
        title="JFR Analysis"
        description="Explore your profiling data through natural language conversations about CPU, memory, GC, and threading."
      />

      <DocsLinkCard
        to="/docs/ai/heap-dump-analysis"
        icon="bi bi-memory"
        title="Heap Dump Analysis"
        description="AI-powered investigation of Java heap dumps — leak detection, object browsing, and retention analysis."
      />

      <DocsLinkCard
        to="/docs/ai/oql-assistant"
        icon="bi bi-chat-square-text"
        title="OQL Assistant"
        description="Generate Object Query Language queries from natural language descriptions."
      />
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
