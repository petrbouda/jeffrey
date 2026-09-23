<!--
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 -->

<!--
  Top-level Profiler Builder. It assembles an async-profiler agent command and nothing
  else: the command is copied out and pasted into someone else's JVM arguments, so the
  page stores nothing and has no notion of a hub, workspace or project.
-->

<template>
  <MainCard>
    <template #header>
      <MainCardHeader icon="bi bi-cpu" title="Profiler Builder">
        <template #actions>
          <nav class="docs-links" aria-label="async-profiler documentation">
            <span class="docs-links-label">async-profiler docs</span>
            <a
              v-for="link in DOC_LINKS"
              :key="link.href"
              :href="link.href"
              target="_blank"
              rel="noopener noreferrer"
              class="docs-link"
            >
              {{ link.label }}
              <i class="bi bi-arrow-up-right"></i>
            </a>
          </nav>
        </template>
      </MainCardHeader>
    </template>

    <CommandBuilder />
  </MainCard>
</template>

<script setup lang="ts">
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import CommandBuilder from '@/components/settings/CommandBuilder.vue';

interface DocLink {
  label: string;
  href: string;
}

// The async-profiler pages that explain what the builder's options do
const DOC_LINKS: DocLink[] = [
  {
    label: 'Profiling modes',
    href: 'https://github.com/async-profiler/async-profiler/blob/master/docs/ProfilingModes.md'
  },
  {
    label: 'Profiler options',
    href: 'https://github.com/async-profiler/async-profiler/blob/master/docs/ProfilerOptions.md'
  },
  {
    label: 'Method tracing',
    href: 'https://github.com/async-profiler/async-profiler/pull/1435'
  },
  {
    label: 'Method tracing blog',
    href: 'https://github.com/async-profiler/async-profiler/discussions/1497'
  }
];
</script>

<style scoped>
.docs-links {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--spacing-2) var(--spacing-4);
  font-size: 0.8rem;
}

.docs-links-label {
  color: var(--color-text);
}

.docs-link {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-1);
  color: var(--color-primary);
  text-decoration: none;
}

.docs-link:hover {
  color: var(--color-primary-hover);
  text-decoration: underline;
}

.docs-link i {
  font-size: 0.7rem;
}
</style>
