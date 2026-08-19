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
  Latency by Attributes: each of a key's values spread over log-spaced duration buckets. A sibling
  of Attribute Values rather than a variant of it — percentiles hide bimodality, and a value whose
  traces are either fast or catastrophic has the same median as one that is uniformly mediocre.
-->
<template>
  <TraceAttributeKeyWorkspace
    :disabled-features="disabledFeatures"
    no-key-description="Pick a key on the left to see how its values spread over trace duration."
    empty-catalog-description="No span in this profile recorded an attribute, a declared field or a shape worth breaking down."
  >
    <template #default="{ attributeKey }">
      <LoadingState v-if="loading" message="Loading distribution..." />

      <ErrorState v-else-if="error" :message="error" @retry="load(attributeKey)" />

      <TraceAttributeLatency v-else-if="latency" :latency="latency" />
    </template>
  </TraceAttributeKeyWorkspace>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import ErrorState from '@shared/components/ErrorState.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import TraceAttributeKeyWorkspace from '@/components/trace/TraceAttributeKeyWorkspace.vue';
import TraceAttributeLatency from '@/components/trace/TraceAttributeLatency.vue';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import FeatureType from '@/services/api/model/FeatureType';
import {
  keyFromQuery,
  keyToken,
  type TraceAttributeKeyId,
  type TraceAttributeLatency as Latency
} from '@/services/api/model/trace/TraceAttributeModels';

const props = defineProps<{ disabledFeatures: FeatureType[] }>();

const route = useRoute();
const profileId = route.params.profileId as string;
const client = new ProfileTracesClient(profileId);

const loading = ref(false);
const error = ref<string | null>(null);
const latency = ref<Latency | null>(null);

const selectedKey = computed(() => keyFromQuery(route.query));

/*
 * Guards against an out-of-order response: a slow fetch for a previously selected key must not land
 * on top of the grid of the one selected after it.
 */
let generation = 0;

async function load(key: TraceAttributeKeyId): Promise<void> {
  const current = ++generation;
  loading.value = true;
  error.value = null;
  try {
    const loaded = await client.getAttributeLatency(key);
    if (current !== generation) {
      return;
    }
    latency.value = loaded;
  } catch (e: unknown) {
    if (current !== generation) {
      return;
    }
    console.error('Failed to distribute the attribute key over duration:', e);
    error.value = 'Failed to load the distribution of this key. Please try again.';
    latency.value = null;
  } finally {
    if (current === generation) {
      loading.value = false;
    }
  }
}

// Watched by identity rather than by object, for the reason Attribute Values states.
watch(
  () => (selectedKey.value === null ? null : keyToken(selectedKey.value)),
  () => {
    const key = selectedKey.value;
    if (key === null || props.disabledFeatures.includes(FeatureType.TRACES)) {
      latency.value = null;
      return;
    }
    load(key);
  },
  { immediate: true }
);
</script>
