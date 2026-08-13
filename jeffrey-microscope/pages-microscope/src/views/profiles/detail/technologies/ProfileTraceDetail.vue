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

<template>
  <div>
    <LoadingState v-if="loading" message="Loading trace..." />

    <ErrorState v-else-if="error" :message="error" @retry="loadData" />

    <div v-else-if="detail" class="dashboard-container">
      <DetailBreadcrumb root-label="Traces" icon="bi-diagram-3" @back="backToList">
        {{ detail.trace.rootName }}
      </DetailBreadcrumb>

      <MainCard>
        <MainCardHeader
          icon="bi-diagram-3"
          :title="detail.trace.rootName"
          :badge="detail.trace.spanCount"
        >
          <template #actions>
            <span class="trace-total">
              {{ FormattingService.formatDuration2Units(detail.trace.durationNanos) }}
            </span>
          </template>
        </MainCardHeader>

        <div class="trace-body" :class="{ 'with-drawer': selected !== null }">
          <div class="waterfall-pane">
            <TraceWaterfall
              :spans="detail.spans"
              :selected-span-id="selected?.spanId ?? null"
              @select="select"
            />
          </div>

          <TraceSpanDrawer
            v-if="selected"
            :profile-id="profileId"
            :trace-id="traceId"
            :span="selected"
          />
        </div>
      </MainCard>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import DetailBreadcrumb from '@shared/components/DetailBreadcrumb.vue';
import FormattingService from '@shared/services/FormattingService';
import TraceWaterfall from '@/components/trace/TraceWaterfall.vue';
import TraceSpanDrawer from '@/components/trace/TraceSpanDrawer.vue';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import type { TraceDetail, TraceSpanRow } from '@/services/api/model/trace/TraceModels';

const route = useRoute();
const router = useRouter();

const detail = ref<TraceDetail | null>(null);
const selected = ref<TraceSpanRow | null>(null);
const loading = ref(true);
const error = ref<string | null>(null);

const profileId = computed(() => route.params.profileId as string);
const traceId = computed(() => route.params.traceId as string);

function backToList(): void {
  router.push({ name: 'profile-traces', params: { profileId: profileId.value } });
}

function select(span: TraceSpanRow): void {
  // Clicking the selected row again closes the drawer, giving the waterfall the full width back.
  selected.value = selected.value?.spanId === span.spanId ? null : span;
}

async function loadData(): Promise<void> {
  loading.value = true;
  error.value = null;
  selected.value = null;
  try {
    detail.value = await new ProfileTracesClient(profileId.value).getTrace(traceId.value);
  } catch {
    error.value = 'Failed to load this trace.';
  } finally {
    loading.value = false;
  }
}

watch(traceId, loadData);
onMounted(loadData);
</script>

<style scoped>
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.trace-total {
  font-family: var(--font-family-monospace);
  font-variant-numeric: tabular-nums;
  color: var(--color-heading);
  font-size: var(--font-size-md);
}

.trace-body {
  display: grid;
  grid-template-columns: 1fr;
}

.trace-body.with-drawer {
  grid-template-columns: 1fr 20rem;
}

/* The waterfall scrolls on its own so a wide trace never scrolls the page sideways. */
.waterfall-pane {
  overflow-x: auto;
  min-width: 0;
}

@media (max-width: 900px) {
  .trace-body.with-drawer {
    grid-template-columns: 1fr;
  }
}
</style>
