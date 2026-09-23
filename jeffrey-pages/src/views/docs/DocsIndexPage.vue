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
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import DocsProductCard from '@/components/docs/DocsProductCard.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const microscopeComponents = ['Flamegraph', 'Timeseries', 'Heap dump', 'MCP server', 'Events'];
const serverComponents = ['Continuous recording', 'Scheduler', 'gRPC API', 'Heartbeat'];
const provisionerComponents = ['HOCON config', 'JVM argfile', 'Session layout', 'Workspace events', 'Native binary'];
const jibComponents = ['Gradle/Maven', 'Entrypoint wrapper', 'Baked payloads', 'Kill switch'];
const pluginComponents = ['Open in IDE', 'Inline source', 'Java & Kotlin', 'Auto-pairing'];
const mcpComponents = ['102 read-only tools', 'Claude Code, Codex & Gemini', 'DuckDB SQL', 'Flamegraph exports', 'Heap analysis'];
const tracingComponents = ['Tracer API', 'HTTP/gRPC/JDBC', 'JFR-native', 'Waterfall'];

onMounted(() => {
  setHeadings([]);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Documentation"
      icon="bi bi-book"
    />

    <div class="docs-content">
      <p class="docs-lede">Jeffrey ships as several products. Pick the one that matches how you use it.</p>

      <div class="product-grid">
        <DocsProductCard
          to="/docs/microscope"
          title="Microscope"
          role="Standalone · Single-user"
          description="The standalone analyst. Open JFR recordings and heap dumps locally with full visualization, and an MCP server for your coding agent."
          icon="bi bi-pc-display"
          :components="microscopeComponents"
          cta-text="Open Microscope docs"
        />
        <DocsProductCard
          to="/docs/hub"
          title="Hub"
          role="Headless · Multi-workspace"
          description="The headless multi-workspace server. Continuously collect JFR recordings from running applications and serve them to Microscope clients."
          icon="bi bi-cloud"
          :components="serverComponents"
          cta-text="Open Server docs"
          variant="secondary"
        />
        <DocsProductCard
          to="/docs/provisioner"
          title="Provisioner"
          role="Standalone · Session bootstrap"
          description="The profiling-session bootstrap tool. Reads a HOCON config to lay out the workspace/project/session tree, register sessions with the Hub, and generate the JVM argfile that starts your app under the profiler."
          icon="bi bi-terminal"
          :components="provisionerComponents"
          cta-text="Open Provisioner docs"
          variant="quaternary"
        />
        <DocsProductCard
          to="/docs/jib"
          title="Jeffrey JIB"
          role="Standalone · Build-time"
          description="A Jib (Gradle/Maven) plugin extension that wraps the container entrypoint so Jeffrey profiling initialises before your app starts — no Kubernetes command: override, and the provisioner and async-profiler ride along in their own image layer."
          icon="bi bi-box-seam"
          :components="jibComponents"
          cta-text="Open JIB docs"
          variant="quinary"
        />
        <DocsProductCard
          to="/docs/tracing"
          title="Tracing"
          role="Library · In-process traces"
          description="JFR-native tracing for a single JVM. Instrument with the zero-dependency events library, and read requests as span waterfalls correlated with I/O, locks, GC pauses and per-span flamegraphs."
          icon="bi bi-bezier2"
          :components="tracingComponents"
          cta-text="Open Tracing docs"
          variant="septenary"
        />
        <DocsProductCard
          to="/docs/intellij-plugin"
          title="IntelliJ Plugin"
          role="IDE · Companion"
          description="The IDE bridge. Jump from a Microscope flame-graph frame straight to the source line in your open IntelliJ window, and view inline source pulled live from the IDE — Java and Kotlin."
          icon="bi bi-window-stack"
          :components="pluginComponents"
          cta-text="Open IntelliJ Plugin docs"
          variant="senary"
        />
        <DocsProductCard
          to="/docs/microscope-mcp"
          title="Microscope MCP"
          role="Server · Coding agents"
          description="The MCP server inside Microscope. Connect an interactive coding agent — Claude Code, Codex, Gemini CLI, anything that speaks MCP — in your own repository and let it read every profile you have analysed: flamegraphs, traces, heap dumps and the DuckDB tables behind them, alongside your source code."
          icon="bi bi-plugin"
          :components="mcpComponents"
          cta-text="Open Microscope MCP docs"
          variant="tertiary"
        />
      </div>
    </div>
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

.docs-lede {
  font-size: 16px;
  color: #5e6e82;
  margin-bottom: 28px;
}

.product-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 26px;
}

@media (max-width: 768px) {
  .product-grid {
    grid-template-columns: 1fr;
  }
}
</style>
