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
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'entries', text: 'Entries', level: 2 },
  { id: 'footprint', text: 'Footprint', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader title="String & Symbol Tables" icon="bi bi-fonts" />

    <div class="docs-content">
      <p>This page tracks the two global JVM intern tables over time, under the Garbage Collection section. The <strong>String table</strong> holds interned <code>String</code> instances (string constants and <code>String.intern()</code> results); the <strong>Symbol table</strong> holds UTF-8 symbols for class, method, and field names. Both live in native memory outside the Java heap, so their growth never shows on the heap chart. Data comes from the periodic <code>jdk.StringTableStatistics</code> and <code>jdk.SymbolTableStatistics</code> events; the page shows an empty state when they are absent.</p>

      <h2 id="overview">Overview</h2>
      <p>A header strip shows the peak entry count and peak footprint for each table.</p>

      <DocsCallout type="tip">
        <strong>When to look here:</strong> when RSS exceeds the Java heap and Native Memory Tracking points at the symbol/string areas, or when you suspect interned-string misuse.
      </DocsCallout>

      <h2 id="entries">Entries</h2>
      <p>Entry counts for the String and Symbol tables over time. A steadily climbing string-table count points to interned-string growth — frequently <code>String.intern()</code> on unbounded input. Symbol-table growth tracks class/method-name churn, e.g. heavy dynamic class generation (proxies, scripting, frequent redefinition).</p>

      <h2 id="footprint">Footprint</h2>
      <p>The memory footprint (bytes) of each table over time — the native memory consumed by the tables and their entries.</p>

      <DocsCallout type="info">
        <strong>Read the JFR canonical event list:</strong> the
        <a href="https://sap.github.io/jfrevents/" target="_blank" rel="noopener">SAP JFR Events catalog</a>
        documents <code>jdk.StringTableStatistics</code> and <code>jdk.SymbolTableStatistics</code>.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
