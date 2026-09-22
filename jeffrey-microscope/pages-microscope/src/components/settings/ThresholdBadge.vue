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
  States what a threshold field makes async-profiler record. "Record everything" is amber,
  because that is the setting that floods a recording.
-->

<template>
  <Badge
    :value="state.label"
    :variant="state.recordsEverything ? 'warning' : 'primary'"
    size="s"
    :uppercase="false"
  />
</template>

<script setup lang="ts">
import Badge from '@shared/components/Badge.vue';
import type { ThresholdState } from '@/components/settings/thresholdState';

defineProps<{
  state: ThresholdState;
}>();
</script>
