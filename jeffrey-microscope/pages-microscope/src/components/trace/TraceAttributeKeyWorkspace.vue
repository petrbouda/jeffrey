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
  Everything Attribute Values and Latency by Attributes have in common: the key rail, the catalog
  fetch behind it, the breadcrumb, and the states a page has before it has a key to break down.
  The page itself supplies only the breakdown, through the slot.

  Search Traces deliberately does not use this. Its subject is traces rather than a key, it works
  with no key selected at all, and its condition builder picks its own keys — a rail there would be
  a second navigation competing with the sidebar.
-->
<template>
  <div>
    <TracesDisabledFeatureAlert v-if="featureDisabled" />

    <LoadingState v-else-if="loading" message="Loading attributes..." />

    <ErrorState v-else-if="error" :message="error" @retry="loadCatalog" />

    <EmptyState
      v-else-if="keys.length === 0"
      icon="bi-tags"
      title="No Attributes"
      :description="emptyCatalogDescription"
    />

    <div v-else class="dashboard-container attributes-layout">
      <TraceAttributeCatalog
        :keys="keys"
        :selected="selectedKey"
        :total-traces="totalTraces"
        @select="openKey"
      />

      <div class="attributes-workspace">
        <EmptyState
          v-if="selectedKey === null"
          icon="bi-tag"
          title="No key selected"
          :description="noKeyDescription"
        />

        <template v-else>
          <DetailBreadcrumb root-label="Attributes" icon="bi-tags" @back="clearKey">
            {{ keyLabel(selectedKey) }}
          </DetailBreadcrumb>

          <slot :attribute-key="selectedKey" />
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import DetailBreadcrumb from '@shared/components/DetailBreadcrumb.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import TracesDisabledFeatureAlert from '@/components/alerts/TracesDisabledFeatureAlert.vue';
import TraceAttributeCatalog from '@/components/trace/TraceAttributeCatalog.vue';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import FeatureType from '@/services/api/model/FeatureType';
import {
  keyFromQuery,
  keyLabel,
  type TraceAttributeKeyRow
} from '@/services/api/model/trace/TraceAttributeModels';

/**
 * A key past the cardinality cap has no breakdown to open, so the rail — which exists to open one —
 * leaves it out. It stays reachable where it is useful: the Search Traces condition builder lists
 * every key, marked, because searching a high-cardinality key is exactly what it is for.
 */
const props = defineProps<{
  disabledFeatures: FeatureType[];
  /** What this page would show for the chosen key, said in the empty state before one is chosen. */
  noKeyDescription: string;
  emptyCatalogDescription: string;
}>();

const route = useRoute();
const router = useRouter();
const profileId = route.params.profileId as string;
const client = new ProfileTracesClient(profileId);

const featureDisabled = computed(() => props.disabledFeatures.includes(FeatureType.TRACES));

const loading = ref(true);
const error = ref<string | null>(null);
const keys = ref<TraceAttributeKeyRow[]>([]);
const totalTraces = ref(0);

/*
 * The selection lives in the URL, so a breakdown is a link, and it is the same triple every
 * attribute page reads — switching between Values and Latency in the sidebar keeps the key.
 */
const selectedKey = computed(() => keyFromQuery(route.query));

// Stays on this page rather than switching view: which reading of the key is on screen is the
// sidebar's business now, and the rail's is which key.
function openKey(key: TraceAttributeKeyRow): void {
  router.push({
    query: { ...route.query, source: key.source, owner: key.owner ?? undefined, key: key.key }
  });
}

function clearKey(): void {
  const query = { ...route.query };
  delete query.key;
  delete query.owner;
  delete query.source;
  router.push({ query });
}

async function loadCatalog(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    const [catalog, overview] = await Promise.all([
      client.getAttributeKeys(),
      client.getOverview()
    ]);
    keys.value = catalog.filter(key => !key.searchOnly);
    totalTraces.value = overview.totalTraces;
  } catch (e: unknown) {
    console.error('Failed to load the attribute catalog:', e);
    error.value = 'Failed to load attributes. Please try again.';
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  if (featureDisabled.value) {
    loading.value = false;
    return;
  }
  loadCatalog();
});
</script>

<style scoped>
.attributes-layout {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-3);
}

.attributes-workspace {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-3);
}

@media (max-width: 992px) {
  .attributes-layout {
    flex-direction: column;
  }

  .attributes-workspace {
    width: 100%;
  }
}
</style>
