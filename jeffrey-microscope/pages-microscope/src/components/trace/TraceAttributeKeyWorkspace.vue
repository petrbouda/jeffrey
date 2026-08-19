<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
import {
  eventTypeFromQuery,
  keyFromQuery
} from '@/services/api/model/trace/TraceAttributeModels';

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
