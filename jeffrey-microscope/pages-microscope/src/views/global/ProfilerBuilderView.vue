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
      <MainCardHeader icon="bi bi-cpu" title="Profiler Builder" />
    </template>

    <CommandBuilder />
  </MainCard>
</template>

<script setup lang="ts">
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import CommandBuilder from '@/components/settings/CommandBuilder.vue';
</script>
