<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <div class="overview">
    <!-- Hero -->
    <div class="hero">
      <span class="hero-chip"><i class="bi bi-stars"></i> AI-graded</span>
      <h2>Performance Overview</h2>
      <p>Where your services are losing the most time — ranked by AI severity across all projects.</p>
    </div>

    <LoadingState v-if="loading" message="Loading impact overview..." />
    <ErrorState v-else-if="error" :message="error" />

    <template v-else>
      <!-- Severity count tiles -->
      <div class="stat-row">
        <div v-for="tile in tiles" :key="tile.severity" class="stat" :style="{ '--sev': tile.color }">
          <div>
            <div class="stat-n">{{ tile.count }}</div>
            <div class="stat-l">{{ tile.severity }}</div>
          </div>
          <span class="stat-ic"><i class="bi" :class="tile.icon"></i></span>
        </div>
      </div>

      <!-- Highest Impact list -->
      <MainCard no-padding>
        <template #header>
          <MainCardHeader icon="bi-fire" title="Highest Impact" :badge="overview.items.length" />
        </template>

        <EmptyState
          v-if="overview.items.length === 0"
          icon="bi-stars"
          title="No recommendations yet"
          message="Generate AI recommendations for a project's recordings and the highest-impact ones will surface here, ranked by severity."
        />
        <div v-else class="feed">
          <button
            v-for="item in overview.items"
            :key="item.recordingId"
            class="feed-row"
            :style="{ '--sev': severityColor(item.severity) }"
            @click="openRecording(item)"
          >
            <span class="rec-icon"><i class="bi bi-fire"></i></span>
            <span class="feed-main">
              <span class="feed-name" :title="item.recordingName">{{ item.recordingName }}</span>
              <span class="feed-bc">
                <b>{{ item.projectName ?? item.projectId }}</b>
                <template v-if="item.headline"> · {{ item.headline }}</template>
              </span>
            </span>
            <Badge
              :value="item.severity"
              :variant="severityVariant(item.severity)"
              size="s"
              :uppercase="false"
            />
            <span class="feed-time">{{ formatRelativeTime(item.generatedAt) }}</span>
          </button>
        </div>
      </MainCard>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import Badge from '@shared/components/Badge.vue';
import FormattingService from '@shared/services/FormattingService';
import TopRecommendationsClient from '@/services/api/TopRecommendationsClient';
import type TopSeverityOverview from '@/services/api/model/TopSeverityOverview';
import type { TopSeverityRecommendation } from '@/services/api/model/TopSeverityOverview';
import type Severity from '@/services/api/model/Severity';
import { SEVERITY_ORDER, severityColor, severityIcon, severityVariant } from '@/services/severityDisplay';

const router = useRouter();
const topRecommendationsClient = new TopRecommendationsClient();

const EMPTY_OVERVIEW: TopSeverityOverview = {
  counts: { critical: 0, high: 0, medium: 0, low: 0 },
  items: []
};

const overview = ref<TopSeverityOverview>(EMPTY_OVERVIEW);
const loading = ref(true);
const error = ref<string | null>(null);

const formatRelativeTime = (millis: number) => FormattingService.formatRelativeTime(millis);

const countFor = (severity: Severity): number => {
  switch (severity) {
    case 'CRITICAL':
      return overview.value.counts.critical;
    case 'HIGH':
      return overview.value.counts.high;
    case 'MEDIUM':
      return overview.value.counts.medium;
    case 'LOW':
      return overview.value.counts.low;
  }
};

const tiles = computed(() =>
  SEVERITY_ORDER.map(severity => ({
    severity,
    count: countFor(severity),
    color: severityColor(severity),
    icon: severityIcon(severity)
  }))
);

const openRecording = (item: TopSeverityRecommendation) => {
  router.push({
    name: 'project-recordings',
    params: { hubId: item.hubId, workspaceId: item.workspaceId, projectId: item.projectId },
    query: { recording: item.recordingId }
  });
};

const loadOverview = async () => {
  loading.value = true;
  error.value = null;
  try {
    overview.value = await topRecommendationsClient.loadTopSeverity();
  } catch (e) {
    console.error('Failed to load impact overview:', e);
    error.value = 'Failed to load the impact overview.';
  } finally {
    loading.value = false;
  }
};

onMounted(loadOverview);
</script>

<style scoped>
.overview {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* Hero */
.hero {
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-lg);
  padding: 22px 24px;
  color: var(--color-white);
  background: linear-gradient(120deg, var(--color-primary-hover), var(--color-primary) 55%, var(--color-violet));
}

.hero h2 {
  margin: 0 0 4px;
  font-size: 1.3rem;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.hero p {
  margin: 0;
  font-size: 0.82rem;
  opacity: 0.85;
}

.hero-chip {
  position: absolute;
  top: 18px;
  right: 22px;
  font-size: 0.7rem;
  opacity: 0.85;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

/* Severity tiles */
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.stat {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 16px;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-top: 3px solid var(--sev);
  border-radius: var(--radius-md);
}

.stat-n {
  font-size: 1.6rem;
  font-weight: 800;
  line-height: 1;
  color: var(--sev);
  font-variant-numeric: tabular-nums;
}

.stat-l {
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-muted);
  margin-top: 3px;
}

.stat-ic {
  margin-left: auto;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--sev);
  background: color-mix(in srgb, var(--sev) 14%, transparent);
}

/* Highest Impact feed */
.feed {
  display: flex;
  flex-direction: column;
}

.feed-row {
  display: flex;
  align-items: center;
  gap: 13px;
  width: 100%;
  text-align: left;
  padding: 13px 16px;
  background: transparent;
  border: 0;
  border-left: 3px solid var(--sev);
  border-bottom: 1px solid var(--color-border-light);
  cursor: pointer;
  font: inherit;
  transition: background 0.15s ease;
}

.feed-row:last-child {
  border-bottom: 0;
}

.feed-row:hover {
  background: var(--color-light);
}

.rec-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  flex-shrink: 0;
  font-size: 0.95rem;
  color: var(--color-primary);
  background: var(--color-primary-light);
}

.feed-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.feed-name {
  font-weight: 600;
  font-size: 0.86rem;
  color: var(--color-dark);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.feed-bc {
  font-size: 0.73rem;
  color: var(--color-text-muted);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.feed-bc b {
  color: var(--color-text);
  font-weight: 600;
}

.feed-time {
  font-size: 0.72rem;
  color: var(--color-text-light);
  white-space: nowrap;
  flex-shrink: 0;
}

@media (max-width: 760px) {
  .stat-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
