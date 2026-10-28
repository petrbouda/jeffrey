<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
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
  { id: 'compressed-oops', text: 'Compressed oops and re-initialization', level: 3 },
  { id: 'memory-analysis', text: 'Memory Analysis', level: 2 },
  { id: 'retention', text: 'Retention & GC Roots', level: 2 },
  { id: 'waste', text: 'Wasted Memory', level: 2 },
  { id: 'runtime', text: 'Runtime', level: 2 },
  { id: 'query-compare', text: 'Query & Compare', level: 2 },
  { id: 'from-an-agent', text: 'From a Coding Agent', level: 2 }
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
      <p>Jeffrey analyzes Java heap dump snapshots (<code>.hprof</code> files, plain or gzipped) to show what is on the heap, which objects retain the most memory, where leaks hide, and how much memory is wasted on duplicates and over-allocated collections. A heap dump is a <strong>recording of its own</strong> and becomes a <strong>profile of its own</strong>: initialize it from its row on the <router-link to="/docs/microscope/recordings">Recordings</router-link> page and its profile opens on the Heap Dump Overview, with <strong>Heap Dump</strong> as its only mode.</p>

      <DocsCallout type="info">
        <strong>Where a heap dump comes from:</strong> a <code>.hprof</code> or <code>.hprof.gz</code> uploaded on the Recordings page, or one taken by a JVM whose recordings the remote holds &mdash; a downloaded session lands as a <router-link to="/docs/microscope/recordings#bundles">bundle</router-link> in which every heap dump is an item beside the session&rsquo;s JFR recording, initialized separately. A heap dump is never part of a JFR profile. Everything below is available once the dump has been initialized.
      </DocsCallout>

      <h2 id="initialization">Initialization</h2>
      <p>A heap dump does not parse into the per-profile database like a JFR recording. Instead, a one-time <strong>initialization pipeline</strong> copies the dump into the profile&rsquo;s own <code>heap-dump/</code> directory and builds a compact index next to it — a <code>.idx.duckdb</code> sibling, plus a scratch database and a <code>.idx.staging</code> directory that exist only while the build runs — and every analysis then reads from that index instantly. Deleting the heap dump&rsquo;s recording or profile takes the copy and its siblings with it. A heap dump is initialized from its row on the <router-link to="/docs/microscope/recordings">Recordings</router-link> page — standalone, or inside its expanded bundle — which shows the run as one chip per phase; the Heap Dump Overview shows the same run in full, across three phases:</p>

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
        Initialization runs in the background — you can watch each stage complete with its elapsed time, on the recording's row or on the Overview. If the Overview is opened mid-run, it picks up the live progress. Once finished, opening any feature is instant because it reads the prebuilt index.
      </DocsCallout>

      <h3 id="compressed-oops">Compressed oops and re-initialization</h3>
      <p>Initialization auto-detects whether the dumped JVM used compressed object pointers, which decides how big every reference and object header is. When object sizes look wrong, auto-detection guessed incorrectly: <strong>Re-initialize</strong> on the Heap Dump Overview drops the index and every cached analysis and builds them again, either auto-detecting again or forcing compressed oops <strong>enabled</strong> (4-byte references, heaps under 32&nbsp;GiB) or <strong>disabled</strong> (8-byte references). The <strong>Compressed Oops</strong> figure in the summary shows what the last run used.</p>
      <p>The Overview neither uploads nor deletes a heap dump: a dump arrives through the Recordings page and is removed there, with its row. When a run failed or never finished, the Overview offers <strong>Initialize</strong> (or <strong>Try again</strong>) to start it on the profile&rsquo;s copy without going back to the list.</p>

      <DocsCallout type="info">
        <strong>A failed build leaves nothing behind.</strong> The index is assembled in a scratch
        file and only renamed into place once the build has finished, so a build that fails — or a
        process killed midway — never publishes a half-written index. The dump simply looks
        uninitialized again and the next attempt rebuilds it from scratch; there is no cache to
        clear by hand.
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

      <h2 id="from-an-agent">From a Coding Agent</h2>
      <p>Every view above is also a tool of the <strong>Microscope MCP</strong> server, called with the heap-dump profile&rsquo;s id, so a coding-agent session in your repository that speaks MCP <code>2026-07-28</code> (Claude Code on its v2 MCP runtime, or Codex v0.147.0+ with its <code>mcp_2026_07_28</code> flag on) can ask about retained sizes, suspected leaks, dominator chains, string duplication and class-loader unloadability in natural language and drive the same analysis — with the <code>analyze-heap</code> skill as its method and <code>heap_oql</code> for the questions the reports do not answer.</p>

      <p>A dump downloaded from a remote session is the bundle&rsquo;s heap-dump item: <code>remote_download</code> lists it under <code>items</code>, <code>recordings_analyzeRecording</code> on its <code>recordingId</code> builds the profile, and the <code>heap_</code> tools take that profile. Called on a JFR profile, they are refused with exactly that pointer.</p>

      <p class="docs-read-more">
        <router-link to="/docs/microscope-mcp/tools#heap">Read the heap tool reference &rarr;</router-link>
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
