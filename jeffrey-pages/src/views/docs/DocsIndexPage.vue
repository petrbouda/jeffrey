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
const serverComponents = ['Continuous recording', 'Scheduler', 'gRPC API', 'CLI', 'Agent', 'JIB'];

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
      <p class="docs-lede">Jeffrey ships as two products. Pick the one that matches how you use it.</p>

      <div class="product-grid">
        <DocsProductCard
          to="/docs/microscope"
          title="Jeffrey Microscope"
          role="Standalone · Single-user"
          description="The standalone analyst. Open JFR recordings and heap dumps locally with full visualization, guardian, and AI features."
          icon="bi bi-pc-display"
          :components="microscopeComponents"
          cta-text="Open Microscope docs"
        />
        <DocsProductCard
          to="/docs/server"
          title="Jeffrey Server"
          role="Headless · Multi-workspace"
          description="The headless multi-workspace server. Continuously collect JFR recordings from running applications and serve them to Microscope clients."
          icon="bi bi-cloud"
          :components="serverComponents"
          cta-text="Open Server docs"
          variant="secondary"
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
