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
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'classes', text: 'Classes Table', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader title="Finalizers" icon="bi bi-hourglass-split" />

    <div class="docs-content">
      <p>This page reports per-class finalization activity from the periodic <code>jdk.FinalizerStatistics</code> event (one record per class that overrides <code>finalize()</code>). Finalization is deprecated and a well-known source of stalls and retained memory: objects with finalizers survive an extra GC cycle and are processed serially by a single finalizer thread. The page shows an empty state when no class uses finalizers.</p>

      <h2 id="overview">Overview</h2>
      <p>A header strip shows the number of finalizable classes, the total pending finalizable objects, and the total finalizers run across the recording.</p>

      <DocsCallout type="tip">
        <strong>Leak signal:</strong> a class with a high or growing <em>peak pending objects</em> count means finalizable instances are piling up faster than the finalizer thread can drain them — a finalizer leak or a slow <code>finalize()</code>.
      </DocsCallout>

      <h2 id="classes">Classes Table</h2>
      <p>Each finalizable class with its code source, peak pending finalizable objects, and total finalizers run, ranked by peak pending objects. Prefer <code>java.lang.ref.Cleaner</code> or explicit <code>close()</code> / try-with-resources over <code>finalize()</code> for the classes that show up here.</p>

      <DocsCallout type="info">
        <strong>Read the JFR canonical event list:</strong> the
        <a href="https://sap.github.io/jfrevents/" target="_blank" rel="noopener">SAP JFR Events catalog</a>
        documents <code>jdk.FinalizerStatistics</code>.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
