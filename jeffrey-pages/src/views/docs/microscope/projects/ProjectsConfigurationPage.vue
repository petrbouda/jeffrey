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

onMounted(() => {
  setHeadings([]);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Configuration"
      icon="bi bi-sliders"
    />

    <div class="docs-content">
      <DocsCallout type="info">
        <strong>Documentation merged.</strong> Scopes, the merge order and the full builder field
        reference are on a single page:
        <router-link to="/docs/microscope/scoped-configuration">Configuration</router-link>.
      </DocsCallout>

      <p>
        A project's <em>Configuration</em> tab sets values at the <strong>Project</strong> scope,
        which overrides its workspace and the global scope. The same tab shows what the project
        inherits, so you can see what a value is replacing before you set it.
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
