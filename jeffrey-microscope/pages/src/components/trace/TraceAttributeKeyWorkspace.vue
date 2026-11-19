<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     https://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<!--
  What Attribute Values and Latency by Attributes have in common: the two-step picker above, and the
  states a page has before both steps are answered. The page supplies only the breakdown, through the
  slot, and only once there is something to break down.

  Search Traces deliberately does not use this. Its subject is traces rather than one key of one
  event type, it works with nothing selected at all, and its condition builder picks its own keys.
-->
<template>
  <div class="attributes-layout">
    <TracesDisabledFeatureAlert v-if="featureDisabled" />

    <template v-else>
      <TraceAttributeSelector />

      <EmptyState
        v-if="selectedKey === null || selectedEventType === null"
        icon="bi-tag"
        title="Nothing selected yet"
        :description="noKeyDescription"
      />

      <slot v-else :attribute-key="selectedKey" :event-type="selectedEventType" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

import EmptyState from '@shared/components/EmptyState.vue';
import TracesDisabledFeatureAlert from '@/components/alerts/TracesDisabledFeatureAlert.vue';
import TraceAttributeSelector from '@/components/trace/TraceAttributeSelector.vue';
import FeatureType from '@/services/api/model/FeatureType';
import { eventTypeFromQuery, keyFromQuery } from '@/services/api/model/trace/TraceAttributeModels';

const props = defineProps<{
  disabledFeatures: FeatureType[];
  /** What this page would show once both steps are answered, said while they are not. */
  noKeyDescription: string;
}>();

const route = useRoute();

const featureDisabled = computed(() => props.disabledFeatures.includes(FeatureType.TRACES));

const selectedEventType = computed(() => eventTypeFromQuery(route.query));
const selectedKey = computed(() => keyFromQuery(route.query));
</script>

<style scoped>
.attributes-layout {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-3);
}
</style>
