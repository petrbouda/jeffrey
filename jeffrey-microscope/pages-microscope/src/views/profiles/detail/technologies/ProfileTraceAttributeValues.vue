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
  Attribute Values: one key broken into every value it took, ranked by what each cost. Picking a
  value hands the reader to Search Traces with that value as a condition — the breakdown says which
  value is expensive, the search says which traces made it so.
-->
<template>
  <TraceAttributeKeyWorkspace
    :disabled-features="disabledFeatures"
    no-key-description="Pick a key on the left to break it down into the values it took."
    empty-catalog-description="No span in this profile recorded an attribute, a declared field or a shape worth breaking down."
  >
    <template #default="{ attributeKey }">
      <LoadingState v-if="loading" message="Loading values..." />

      <ErrorState v-else-if="error" :message="error" @retry="load(attributeKey)" />

      <TraceAttributeValues
        v-else-if="values"
        :values="values"
        :sort="sort"
        :descending="true"
        @sort="applySort(attributeKey, $event)"
        @pick="filterByValue(attributeKey, $event)"
      />
    </template>
  </TraceAttributeKeyWorkspace>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import ErrorState from '@shared/components/ErrorState.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import TraceAttributeKeyWorkspace from '@/components/trace/TraceAttributeKeyWorkspace.vue';
import TraceAttributeValues from '@/components/trace/TraceAttributeValues.vue';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import FeatureType from '@/services/api/model/FeatureType';
import {
  encodeCondition,
  keyFromQuery,
  keyToken,
  type TraceAttributeKeyId,
  type TraceAttributeValues as Values,
  type TraceAttributeValueRow,
  type TraceAttributeValueSortField
} from '@/services/api/model/trace/TraceAttributeModels';

const props = defineProps<{ disabledFeatures: FeatureType[] }>();

const route = useRoute();
const router = useRouter();
const profileId = route.params.profileId as string;
const client = new ProfileTracesClient(profileId);

const loading = ref(false);
const error = ref<string | null>(null);
const values = ref<Values | null>(null);
const sort = ref<TraceAttributeValueSortField>('TOTAL_TIME');

const selectedKey = computed(() => keyFromQuery(route.query));

/*
 * Guards against an out-of-order response: a slow fetch for a previously selected key must not land
 * on top of the table of the one selected after it.
 */
let generation = 0;

async function load(key: TraceAttributeKeyId): Promise<void> {
  const current = ++generation;
  loading.value = true;
  error.value = null;
  try {
    const loaded = await client.getAttributeValues(key, sort.value);
    if (current !== generation) {
      return;
    }
    values.value = loaded;
  } catch (e: unknown) {
    if (current !== generation) {
      return;
    }
    console.error('Failed to break down the attribute key:', e);
    error.value = 'Failed to load the values of this key. Please try again.';
    values.value = null;
  } finally {
    if (current === generation) {
      loading.value = false;
    }
  }
}

function applySort(key: TraceAttributeKeyId, field: TraceAttributeValueSortField): void {
  sort.value = field;
  load(key);
}

/**
 * Turns a row of the breakdown into the search that produced it. A push rather than a shared query:
 * the condition replaces whatever the search was last showing, which is what picking a value means.
 */
function filterByValue(key: TraceAttributeKeyId, row: TraceAttributeValueRow): void {
  router.push({
    path: `/profiles/${profileId}/technologies/traces/attributes/search`,
    query: { where: [encodeCondition({ ...key, operator: 'EQ', value: row.value })] }
  });
}

// Watched by identity rather than by object: the key travels in the URL, and an unrelated change to
// the query — a leftover condition, say — is not a change of key. Immediate, so a deep link loads
// on arrival.
watch(
  () => (selectedKey.value === null ? null : keyToken(selectedKey.value)),
  () => {
    const key = selectedKey.value;
    if (key === null || props.disabledFeatures.includes(FeatureType.TRACES)) {
      values.value = null;
      return;
    }
    load(key);
  },
  { immediate: true }
);
</script>
