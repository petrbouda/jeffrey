<!--
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
