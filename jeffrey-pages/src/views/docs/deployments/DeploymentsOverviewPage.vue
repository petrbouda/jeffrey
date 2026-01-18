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
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'deployment-options', text: 'Deployment Options', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Local Deployments Overview"
        icon="bi bi-cloud-upload"
      />

      <div class="docs-content">
        <p>Jeffrey can be deployed locally in various ways. Choose the deployment option that best fits your use case.</p>

        <h2 id="deployment-options">Deployment Options</h2>

        <div class="options-grid">
          <router-link to="/docs/deployments/simple-jar" class="option-card">
            <div class="option-icon jar">
              <i class="bi bi-file-earmark-zip"></i>
            </div>
            <h4>Simple JAR Execution</h4>
            <p>Run Jeffrey directly as a JAR file. Ideal for local development and quick testing.</p>
          </router-link>

          <router-link to="/docs/deployments/simple-container" class="option-card">
            <div class="option-icon docker">
              <i class="bi bi-box-seam"></i>
            </div>
            <h4>Simple as a Container</h4>
            <p>Docker container deployment for isolated, reproducible environments.</p>
          </router-link>

          <router-link to="/docs/deployments/container-examples" class="option-card">
            <div class="option-icon examples">
              <i class="bi bi-collection"></i>
            </div>
            <h4>Container with Examples</h4>
            <p>Pre-loaded with sample profiles for exploring Jeffrey's features.</p>
          </router-link>
        </div>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

.options-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin: 1.5rem 0;
}

.option-card {
  display: block;
  padding: 1.25rem;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #fff;
  text-decoration: none !important;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.option-card:hover {
  border-color: #5e64ff;
  box-shadow: 0 2px 8px rgba(94, 100, 255, 0.1);
}

.option-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.2rem;
  color: #fff;
  margin-bottom: 0.75rem;
}

.option-icon.jar {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
}

.option-icon.docker {
  background: linear-gradient(135deg, #2496ed 0%, #1a7bc9 100%);
}

.option-icon.examples {
  background: linear-gradient(135deg, #8b5cf6 0%, #6d28d9 100%);
}

.option-card h4 {
  margin: 0 0 0.5rem 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: #343a40;
}

.option-card p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
}

@media (max-width: 992px) {
  .options-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 576px) {
  .options-grid {
    grid-template-columns: 1fr;
  }
}
</style>
