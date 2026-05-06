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
      title="Profiler Settings"
      icon="bi bi-cpu"
    />

    <div class="docs-content">
      <DocsCallout type="info">
        <strong>Documentation merged.</strong> Profiler Settings — concepts, scopes, and the full builder
        field reference — are now on a single page:
        <router-link to="/docs/microscope/profiler-settings">Profiler Settings</router-link>.
      </DocsCallout>

      <p>
        The <em>Profiler Settings</em> tab on a project page saves the same settings at the
        <strong>Project</strong> scope, overriding Workspace and Global. See the linked page for the
        full resolution order and the builder reference.
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
