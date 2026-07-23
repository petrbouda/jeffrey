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
  { id: 'initialization', text: 'Initialization', level: 2 },
  { id: 'memory-analysis', text: 'Memory Analysis', level: 2 },
  { id: 'retention', text: 'Retention & GC Roots', level: 2 },
  { id: 'waste', text: 'Wasted Memory', level: 2 },
  { id: 'runtime', text: 'Runtime', level: 2 },
  { id: 'query-compare', text: 'Query & Compare', level: 2 },
  { id: 'ai-analysis', text: 'AI Analysis', level: 2 }
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
      <p>Jeffrey analyzes Java heap dump snapshots (<code>.hprof</code> files, plain or gzipped) to show what is on the heap, which objects retain the most memory, where leaks hide, and how much memory is wasted on duplicates and over-allocated collections. Attach a heap dump to a profile, then open the <strong>Heap Dump</strong> mode from the profile sidebar.</p>

      <DocsCallout type="info">
        <strong>Prerequisites:</strong> a heap dump (<code>.hprof</code> or <code>.hprof.gz</code>) associated with the profile. Everything below is available once the dump has been initialized.
      </DocsCallout>

      <h2 id="initialization">Initialization</h2>
      <p>A heap dump does not parse into the per-profile database like a JFR recording. Instead, a one-time <strong>initialization pipeline</strong> builds a compact index in a sibling file next to the <code>.hprof</code>, and every analysis then reads from that index instantly. The Heap Dump Overview shows the pipeline as staged progress across three phases:</p>

      <div class="docs-grid docs-grid-3">
        <DocsFeatureCard
          icon="bi bi-hdd-stack"
          title="1 · Heap Indexing"
          description="Reads and indexes the dump in three sequential sub-stages — Loading heap dump, Parsing heap structure, and Building indexes — advancing one at a time with per-stage timings."
          color="blue"
        />
        <DocsFeatureCard
          icon="bi bi-diagram-3"
          title="2 · Memory Analysis"
          description="Precomputes the heavyweight analyses: string content, the dominator tree and retained sizes, threads, biggest objects, collection analysis and leak suspects."
          color="green"
        />
        <DocsFeatureCard
          icon="bi bi-fire"
          title="3 · Hotspots"
          description="Finishes class-loader analysis, memory consumers, duplicate-data detection and biggest collections."
          color="orange"
        />
      </div>

      <DocsCallout type="tip">
        Initialization runs in the background — you can watch each stage complete with its elapsed time. If the page is reopened mid-run, it resumes showing live progress. Once finished, opening any feature is instant because it reads the prebuilt index.
      </DocsCallout>

      <h2 id="memory-analysis">Memory Analysis</h2>
      <div class="docs-grid docs-grid-2">
        <DocsFeatureCard
          icon="bi bi-list-ol"
          title="Class Histogram"
          description="Object counts and memory sizes grouped by class, with per-class instance drill-down. Opaque arrays (byte[], char[]) show inferred referrer-class hint pills so anonymous storage is attributable to an owner."
        />
        <DocsFeatureCard
          icon="bi bi-diagram-3"
          title="Dominator Tree"
          description="Hierarchical retained-size view — which objects keep others alive. Expand from the largest retainers down to understand who really owns the memory."
        />
        <DocsFeatureCard
          icon="bi bi-search"
          title="Leak Suspects"
          description="Automated leak detection from retained-size analysis and reference-chain patterns, surfacing the objects and structures most likely to be holding memory unnecessarily."
        />
        <DocsFeatureCard
          icon="bi bi-box-seam-fill"
          title="Biggest Objects"
          description="Single objects retaining the most memory — the dominator-tree roots — ranked by retained size."
        />
        <DocsFeatureCard
          icon="bi bi-pie-chart"
          title="Memory Consumers"
          description="Per-package and per-classloader rollups of retained memory, showing which subsystem or dependency owns the heap."
        />
      </div>

      <h2 id="retention">Retention &amp; GC Roots</h2>
      <div class="docs-grid docs-grid-2">
        <DocsFeatureCard
          icon="bi bi-diagram-2"
          title="GC Roots"
          description="Browse GC roots across five tabs — Top Retainers, By Class, By ClassLoader, Native / JNI, and Leak Hints — to see what anchors the live set."
        />
        <DocsFeatureCard
          icon="bi bi-signpost-split"
          title="Path to GC Root"
          description="Trace the reference chain from any object back to a GC root to understand exactly why it is retained and cannot be collected."
        />
      </div>

      <h2 id="waste">Wasted Memory</h2>
      <div class="docs-grid docs-grid-2">
        <DocsFeatureCard
          icon="bi bi-fonts"
          title="String Analysis"
          description="Find string duplication opportunities — identical string values held in many separate objects that could be deduplicated to reclaim memory."
        />
        <DocsFeatureCard
          icon="bi bi-files"
          title="Duplicate Data"
          description="Byte-identical primitive arrays that could be shared as a single copy — the raw storage behind buffers, caches and deserialized payloads — grouped with the reclaimable savings per group."
        />
        <DocsFeatureCard
          icon="bi bi-collection"
          title="Collection Analysis"
          description="Over-allocated and empty collections — poor fill ratios and wasted capacity across array-backed collections plus ArrayDeque, ConcurrentHashMap, TreeMap, LinkedList and the Set family."
        />
        <DocsFeatureCard
          icon="bi bi-bar-chart-steps"
          title="Biggest Collections"
          description="The largest collection instances ranked by element count and retained size — the individual maps, lists and sets that hold the most memory."
        />
      </div>

      <h2 id="runtime">Runtime</h2>
      <div class="docs-grid docs-grid-2">
        <DocsFeatureCard
          icon="bi bi-list-task"
          title="Threads"
          description="Reconstruct the threads captured in the dump — their stack frames and the objects they retain — straight from the snapshot, no live recording required."
        />
        <DocsFeatureCard
          icon="bi bi-layers"
          title="Class Loaders"
          description="Loader hierarchy with a per-loader unloadability verdict — unloadable, rooted (effectively rooted via the parent chain), or pinned (the classic redeploy-leak signature). Suspicious-loader and duplicate-class tabs surface common metaspace leaks."
        />
      </div>

      <h2 id="query-compare">Query &amp; Compare</h2>
      <div class="docs-grid docs-grid-2">
        <DocsFeatureCard
          icon="bi bi-terminal"
          title="OQL Query"
          description="Object Query Language — SQL-like queries over the heap for custom investigations that the prebuilt views don't cover."
        />
        <DocsFeatureCard
          icon="bi bi-layers-half"
          title="Heap Diff"
          description="Compare the current heap dump against a baseline profile's dump: per-class growth, new and removed classes — the classic before/after leak workflow. Pick the baseline with the Secondary Profile switcher."
        />
      </div>

      <h2 id="ai-analysis">AI Analysis</h2>
      <p>The <strong>AI Heap Dump Analysis</strong> assistant investigates the dump conversationally — ask about retained sizes, suspected leaks, dominator chains, string duplication and class-loader unloadability in natural language, and it drives the same tools described above.</p>

      <p class="docs-read-more">
        <router-link to="/docs/ai/heap-dump-analysis">Read the AI Heap Dump Analysis reference &rarr;</router-link>
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
