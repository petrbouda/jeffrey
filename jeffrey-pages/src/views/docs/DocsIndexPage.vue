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
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import DocsProductCard from '@/components/docs/DocsProductCard.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const microscopeComponents = ['Flamegraph', 'Timeseries', 'Guardian', 'Heap dump', 'AI assistant', 'Events'];
const serverComponents = ['Continuous recording', 'Scheduler', 'gRPC API', 'Agent'];
const perfAnalystComponents = ['Hub-connected', 'AI recommendations', 'Severity grading', 'Patches'];
const provisionerComponents = ['HOCON config', 'JVM argfile', 'Session layout', 'Workspace events', 'Native binary'];
const jibComponents = ['Gradle/Maven', 'Entrypoint wrapper', 'Shared volume', 'Kill switch'];
const pluginComponents = ['Open in IDE', 'Inline source', 'Java & Kotlin', 'Auto-pairing'];

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
      <p class="docs-lede">Jeffrey ships as six products. Pick the one that matches how you use it.</p>

      <div class="product-grid">
        <DocsProductCard
          to="/docs/microscope"
          title="Microscope"
          role="Standalone · Single-user"
          description="The standalone analyst. Open JFR recordings and heap dumps locally with full visualization, guardian, and AI features."
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
          to="/docs/perf-analyst"
          title="Performance Analyst"
          role="Incubating · AI analyst"
          description="The AI-driven companion. Pulls recordings from a Hub and turns each profile into source-code-level performance recommendations with severity grades and patches."
          icon="bi bi-robot"
          :components="perfAnalystComponents"
          cta-text="Open Performance Analyst docs"
          variant="tertiary"
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
          description="A Jib (Gradle/Maven) plugin extension that wraps the container entrypoint so Jeffrey profiling initialises before your app starts — no Kubernetes command: override and no binaries baked into your image."
          icon="bi bi-box-seam"
          :components="jibComponents"
          cta-text="Open JIB docs"
          variant="quinary"
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
