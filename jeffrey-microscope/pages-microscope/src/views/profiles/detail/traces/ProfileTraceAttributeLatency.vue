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
  Latency by Attributes: each of a key's values spread over log-spaced duration buckets. A sibling
  of Attribute Values rather than a variant of it — percentiles hide bimodality, and a value whose
  traces are either fast or catastrophic has the same median as one that is uniformly mediocre.
-->
<template>
  <TraceAttributeKeyWorkspace
    :disabled-features="disabledFeatures"
    no-key-description="Choose an event type, then one of the attributes its spans carried."
  >
    <template #default="{ attributeKey, eventType }">
      <LoadingState v-if="loading" message="Loading distribution..." />

      <ErrorState v-else-if="error" :message="error" @retry="load(attributeKey, eventType)" />

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
  eventTypeFromQuery,
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
const selectedEventType = computed(() => eventTypeFromQuery(route.query));

/*
 * Guards against an out-of-order response: a slow fetch for a previously selected key must not land
 * on top of the grid of the one selected after it.
 */
let generation = 0;

async function load(key: TraceAttributeKeyId, eventType: string): Promise<void> {
  const current = ++generation;
  loading.value = true;
  error.value = null;
  try {
    const loaded = await client.getAttributeLatency(key, eventType);
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
  () => [selectedEventType.value, selectedKey.value === null ? null : keyToken(selectedKey.value)],
  () => {
    const key = selectedKey.value;
    const eventType = selectedEventType.value;
    if (key === null || eventType === null || props.disabledFeatures.includes(FeatureType.TRACES)) {
      latency.value = null;
      return;
    }
    load(key, eventType);
  },
  { immediate: true }
);
</script>
