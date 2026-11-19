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
