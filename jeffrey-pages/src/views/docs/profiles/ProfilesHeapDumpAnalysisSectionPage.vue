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
import DocsFeatureItem from '@/components/docs/DocsFeatureItem.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'initialization', text: 'Heap Dump Initialization', level: 2 },
  { id: 'analysis', text: 'Analysis Features', level: 2 },
  { id: 'details', text: 'Details', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Heap Dump Analysis Section"
        icon="bi bi-database"
      />

      <div class="docs-content">
        <p>The Heap Dump Analysis section provides <strong>memory analysis from heap dumps</strong>. This enables deep inspection of the Java heap at a specific point in time, including object instances, memory consumption, and reference chains.</p>

        <h2 id="overview">Overview</h2>
        <p>Heap dump analysis differs from other profile analysis in that it examines a snapshot of memory rather than events over time:</p>
        <ul>
          <li><strong>Object inspection</strong> - See all objects in memory by class</li>
          <li><strong>Memory sizing</strong> - Understand shallow vs retained sizes</li>
          <li><strong>Reference analysis</strong> - Find what's keeping objects alive</li>
          <li><strong>Custom queries</strong> - Use OQL for advanced investigation</li>
        </ul>

        <DocsCallout type="info">
          <strong>Heap dump required:</strong> This section requires a heap dump file (.hprof) to be associated with the profile. Features remain disabled until a heap dump is loaded and its cache is initialized.
        </DocsCallout>

        <h2 id="initialization">Heap Dump Initialization</h2>
        <p>Before heap dump analysis features become available, the heap dump cache must be initialized:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-memory" title="Heap Dump Overview">
            Shows the heap dump file information and initialization status. This page is always accessible and displays whether the cache is being built, ready, or if initialization failed.
          </DocsFeatureItem>
        </div>

        <h3>Initialization Process</h3>
        <p>When a heap dump is first accessed, Jeffrey builds an analysis cache:</p>
        <ol>
          <li><strong>Parsing</strong> - The .hprof file is read and parsed</li>
          <li><strong>Indexing</strong> - Objects and references are indexed for fast lookup</li>
          <li><strong>Size calculation</strong> - Retained sizes are computed</li>
          <li><strong>Cache storage</strong> - Results are stored for subsequent queries</li>
        </ol>

        <DocsCallout type="warning">
          <strong>Resource intensive:</strong> Heap dump initialization can be memory and CPU intensive, especially for large heaps. The initialization runs in the background and progress is shown on the overview page.
        </DocsCallout>

        <h2 id="analysis">Analysis Features</h2>
        <p>Once initialization completes, powerful analysis features become available:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-list-ol" title="Class Histogram">
            Object counts and memory sizes grouped by class. See which classes consume the most memory and how many instances exist. Sort by instance count, shallow size, or retained size to find memory-heavy classes.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-fonts" title="String Analysis">
            Specialized analysis for String objects, which often consume significant heap space. Find duplicate strings, identify long strings, and discover opportunities to reduce memory usage through string deduplication or interning.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-terminal" title="OQL Query">
            Object Query Language for custom heap analysis. Write SQL-like queries to find specific objects, filter by field values, and navigate reference chains. Essential for targeted investigation of memory issues.
          </DocsFeatureItem>
        </div>

        <h3>Understanding Memory Sizes</h3>
        <table>
          <thead>
            <tr>
              <th>Size Type</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><strong>Shallow Size</strong></td>
              <td>Memory consumed by the object itself (header + fields)</td>
            </tr>
            <tr>
              <td><strong>Retained Size</strong></td>
              <td>Memory that would be freed if this object were garbage collected (includes referenced objects that would become unreachable)</td>
            </tr>
          </tbody>
        </table>

        <DocsCallout type="tip">
          <strong>Finding memory leaks:</strong> Look for objects with high retained size that shouldn't exist. String Analysis is particularly useful for finding wasteful string duplication.
        </DocsCallout>

        <h2 id="details">Details</h2>
        <p>Advanced heap analysis features for deep investigation:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-diagram-3" title="GC Roots">
            Browse garbage collection root objects - the starting points for object reachability. Understand what's keeping objects alive by examining the reference chains from GC roots. Essential for diagnosing memory leaks.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-cpu" title="Threads">
            Thread stack traces captured at the time of the heap dump, including local variables and their object references. See what each thread was doing and what objects it was holding.
          </DocsFeatureItem>
        </div>

        <h3>GC Roots Categories</h3>
        <p>Objects can be GC roots for different reasons:</p>
        <ul>
          <li><strong>Thread</strong> - Local variables on active thread stacks</li>
          <li><strong>Static Field</strong> - Static fields in loaded classes</li>
          <li><strong>JNI Global</strong> - Global references from native code</li>
          <li><strong>Monitor</strong> - Objects used as synchronization monitors</li>
          <li><strong>System Class</strong> - Core Java classes loaded by the bootstrap classloader</li>
        </ul>

        <DocsCallout type="info">
          <strong>Memory leak pattern:</strong> A common memory leak pattern is objects held by static fields or thread-local variables that are never cleared. Check GC Roots to identify unexpected references keeping objects alive.
        </DocsCallout>

        <p>Heap dump analysis complements JFR-based profiling by providing point-in-time memory snapshots. Use JFR for understanding allocation patterns over time, and heap dumps for detailed investigation of memory contents.</p>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
