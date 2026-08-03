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
  <LoadingState v-if="loading" message="Loading prompts..." />

  <ErrorState v-else-if="error" :message="error" />

  <PageHeader
    v-else
    title="Prompts"
    description="The exact message the Advisor sends to the model for each event type"
    icon="bi-chat-square-text"
  >
    <AiDisabledFeatureAlert v-if="aiDisabled" />

    <template v-else-if="prompts.length > 0">
      <div class="docket">
        <div class="docket-list">
          <button
            v-for="prompt in orderedPrompts"
            :key="prompt.eventType"
            type="button"
            class="docket-item"
            :class="{ selected: selected?.eventType === prompt.eventType }"
            :style="eventTypeVars(prompt.eventType)"
            @click="selectedType = prompt.eventType"
          >
            <span class="docket-name">
              <i class="bi" :class="eventTypeStyle(prompt.eventType).icon"></i>
              {{ prompt.label }}
            </span>
            <span class="docket-verdict">
              {{ FormattingService.formatNumber(prompt.samples) }} samples
            </span>
          </button>
        </div>
      </div>

      <MainCard v-if="selected">
        <template #header>
          <MainCardHeader icon="bi-chat-square-text" :title="`${selected.label} prompt`">
            <template #actions>
              <span class="generated">
                Generated {{ FormattingService.formatRelativeTime(selected.generatedAt) }}
              </span>
              <button type="button" class="copy-btn" @click="copy">
                <i class="bi" :class="copied ? 'bi-check-lg' : 'bi-clipboard'"></i>
                {{ copied ? 'Copied' : 'Copy' }}
              </button>
            </template>
          </MainCardHeader>
        </template>

        <!-- Deliberately not rendered as markdown. The point of this page is what was literally sent,
             and a rendered call tree is no longer the text the model read. -->
        <pre class="prompt">{{ selected.prompt }}</pre>
      </MainCard>
    </template>

    <!-- Nothing is cached until the Advisor has run at least once for this profile. -->
    <EmptyState
      v-else
      icon="bi-chat-square-text"
      :title="isRunning ? 'A run is in progress' : 'No prompts yet'"
      :description="
        isRunning
          ? 'The Advisor is building its prompts now — watch it on the Overview.'
          : 'Run the Advisor to build a prompt from each profile in this recording.'
      "
    >
      <template #action>
        <router-link :to="overviewPath" class="guard-btn">
          <i class="bi bi-arrow-right"></i>
          {{ isRunning ? 'Watch the run' : 'Go to the Advisor Overview' }}
        </router-link>
      </template>
    </EmptyState>
  </PageHeader>
</template>

<script setup lang="ts">
import '@shared/styles/shared-components.css';
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import PageHeader from '@shared/components/layout/PageHeader.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import FormattingService from '@shared/services/FormattingService';
import ToastService from '@shared/services/ToastService';
import AdvisorClient from '@/services/api/AdvisorClient';
import type { AdvisorPrompt } from '@/services/api/model/Advisor';
import AiDisabledFeatureAlert from '@/components/alerts/AiDisabledFeatureAlert.vue';
import {
  compareEventTypes,
  eventTypeStyle,
  eventTypeVars
} from '@/views/profiles/detail/advisor/eventTypeStyle';
import { useAdvisor } from '@/composables/useAdvisor';
import FeatureType from '@/services/api/model/FeatureType';

const props = defineProps<{ disabledFeatures?: FeatureType[] }>();

const COPIED_FEEDBACK_MS = 1500;

const route = useRoute();
const profileId = route.params.profileId as string;

/**
 * The prompts are fetched here rather than added to `useAdvisor`: their bodies carry a whole call tree
 * each and are the largest thing the Advisor stores, so folding them into the shared load would make
 * the Overview, Recommendations and Patches pages pay for a payload none of them render. The
 * composable is
 * still used for the run state, which is what tells an empty page whether to say "not yet" or "wait".
 */
const { isRunning, load: loadRunState } = useAdvisor(profileId);

const client = new AdvisorClient(profileId);

const loading = ref(true);
const error = ref<string | null>(null);
const prompts = ref<AdvisorPrompt[]>([]);
const selectedType = ref<string | null>(null);
const copied = ref(false);

const aiDisabled = computed(
  () => props.disabledFeatures?.includes(FeatureType.AI_ANALYSIS) === true
);

const overviewPath = computed(() => `/profiles/${profileId}/advisor`);

/** CPU, Wall-Clock, Allocation, Blocking — the order Recommendations and Patches list them in too. */
const orderedPrompts = computed(() =>
  [...prompts.value].sort((left, right) => compareEventTypes(left.eventType, right.eventType))
);

const selected = computed(
  () =>
    orderedPrompts.value.find(prompt => prompt.eventType === selectedType.value) ??
    orderedPrompts.value[0]
);

async function load(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    const [cached] = await Promise.all([client.prompts(), loadRunState()]);
    prompts.value = cached;
  } catch (e: any) {
    error.value = e?.errorResponse?.message ?? e?.message ?? 'Failed to load the Advisor prompts';
  } finally {
    loading.value = false;
  }
}

async function copy(): Promise<void> {
  if (!selected.value) {
    return;
  }
  try {
    await navigator.clipboard.writeText(selected.value.prompt);
    copied.value = true;
    setTimeout(() => {
      copied.value = false;
    }, COPIED_FEEDBACK_MS);
  } catch {
    ToastService.error('Prompt', 'The browser blocked access to the clipboard');
  }
}

onMounted(load);
</script>

<style scoped>
/* The docket mirrors Recommendations and Patches, so the three artifact pages read as one family. */
.docket {
  margin-bottom: 1.1rem;
}

.docket-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 0.5rem;
}

.docket-item {
  appearance: none;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--et);
  border-radius: var(--radius-base);
  background: var(--color-bg-card);
  padding: 0.5rem 0.7rem;
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  transition: background 0.16s, border-color 0.16s;
}

.docket-item:hover {
  background: var(--color-light);
}

.docket-item.selected {
  background: var(--et-light);
  border-color: var(--et);
}

.docket-name {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--color-heading-dark);
}

.docket-name i {
  color: var(--et);
}

.docket-verdict {
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.docket-item.selected .docket-verdict {
  color: var(--et);
}

.generated {
  font-size: 0.72rem;
  color: var(--color-text-muted);
}

.copy-btn {
  appearance: none;
  cursor: pointer;
  font-family: inherit;
  font-size: 0.74rem;
  font-weight: 600;
  border-radius: var(--radius-base);
  border: 1px solid var(--color-primary-border-light);
  background: transparent;
  color: var(--color-primary);
  padding: 0.28rem 0.6rem;
  display: inline-flex;
  align-items: center;
  gap: 0.32rem;
  white-space: nowrap;
  transition: background 0.15s;
}

.copy-btn:hover {
  background: var(--color-primary-light);
}

/* The prompt is long — it carries a whole call tree — so the body scrolls rather than the page. */
.prompt {
  margin: 0;
  padding: 1rem;
  max-height: 70vh;
  overflow: auto;
  font-family: var(--font-family-monospace);
  font-size: 0.76rem;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--color-text);
}
</style>
