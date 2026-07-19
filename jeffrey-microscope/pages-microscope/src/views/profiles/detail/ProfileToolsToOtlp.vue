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
  <PageHeader
    icon="bi-box-arrow-down"
    title="Convert to OTLP"
    description="Export a single stack-based event type as a standard OpenTelemetry profiles (.otlp) file — download it or add it back to Recordings. Each frame keeps its profile.frame.type so Java, native and kernel frames stay distinguishable."
  >
    <LoadingState v-if="loading" message="Loading event types..." />
    <ErrorState v-else-if="loadError" :message="loadError" />

    <div v-else-if="eventTypes.length === 0" class="text-muted small py-2">
      <i class="bi bi-info-circle me-1"></i>
      This profile has no stack-based event types to export.
    </div>

    <div v-else class="row g-4">
      <!-- Left: form -->
      <div class="col-lg-6">
        <label class="form-label mb-1 small text-uppercase fw-semibold text-muted">Event type</label>
        <SearchableSelect
          v-model="selectedLabel"
          :items="selectItems"
          placeholder="Select an event type"
          search-placeholder="Search events..."
        >
          <template #item="{ item }">
            <div class="item-label">{{ item.label }}</div>
            <div class="item-sub">
              <span class="mono">{{ item.code }}</span> ·
              {{ FormattingService.formatNumber(item.samples) }} samples
            </div>
          </template>
        </SearchableSelect>

        <label
          class="weight-option mt-4"
          :class="{ disabled: !selectedEvent || !selectedEvent.hasWeight }"
        >
          <input
            type="checkbox"
            class="weight-option__box"
            v-model="includeWeight"
            :disabled="!selectedEvent || !selectedEvent.hasWeight || busy"
          />
          <span class="weight-option__body">
            <span class="weight-option__title">Export the weight metric instead of sample count</span>
            <span class="weight-option__hint">
              <template v-if="selectedEvent && selectedEvent.hasWeight">
                Uses the event's weight ({{ selectedEvent.category }}) as the OTLP sample type instead of the sample count.
              </template>
              <template v-else>This event has no weight dimension.</template>
            </span>
          </span>
        </label>

        <div class="mt-4">
          <label class="form-label mb-1 small text-uppercase fw-semibold text-muted">Filename</label>
          <div class="filename-box mono">{{ filename }}</div>
        </div>
      </div>

      <!-- Right: live summary -->
      <div class="col-lg-6">
        <div class="summary-card">
          <div class="summary-title">Selected event</div>
          <template v-if="selectedEvent">
            <div class="summary-row">
              <span class="text-muted">Event</span><span class="fw-semibold">{{ selectedEvent.label }}</span>
            </div>
            <div class="summary-row">
              <span class="text-muted">Code</span><span class="mono">{{ selectedEvent.code }}</span>
            </div>
            <div class="summary-row">
              <span class="text-muted">Samples</span>
              <span class="fw-semibold">{{ FormattingService.formatNumber(selectedEvent.samples) }}</span>
            </div>
            <div class="summary-row">
              <span class="text-muted">Weight</span>
              <span>{{ weightText }}</span>
            </div>
            <div class="summary-row">
              <span class="text-muted">Category</span>
              <Badge :value="selectedEvent.category" :variant="categoryVariant" size="xs" />
            </div>
            <div class="summary-kv">
              <span>OTLP sample_type</span>
              <span class="mono">{{ sampleTypeText }}</span>
            </div>
          </template>
          <div v-else class="text-muted small py-3">
            Select an event type to see its details and OTLP mapping.
          </div>
        </div>

        <div class="d-flex gap-2 mt-3">
          <button
            class="btn btn-sm btn-primary"
            :disabled="!selectedEvent || busy"
            @click="convertAndDownload"
          >
            <span v-if="downloading" class="spinner-border spinner-border-sm me-1"></span>
            <i v-else class="bi bi-download me-1"></i>
            Convert &amp; Download
          </button>
          <button
            class="btn btn-sm btn-outline-primary"
            :disabled="!selectedEvent || busy"
            @click="addToRecordings"
          >
            <span v-if="adding" class="spinner-border spinner-border-sm me-1"></span>
            <i v-else class="bi bi-collection me-1"></i>
            Add to Recordings
          </button>
        </div>

        <div
          v-if="addedRecordingId"
          class="alert alert-success d-flex align-items-center justify-content-between mt-3 py-2 small"
        >
          <span><i class="bi bi-check-circle me-2"></i>Added to Recordings.</span>
          <router-link class="btn btn-sm btn-outline-success" :to="{ name: 'recordings' }">
            Open Recordings
          </router-link>
        </div>
      </div>
    </div>
  </PageHeader>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import Badge from '@shared/components/Badge.vue';
import PageHeader from '@shared/components/layout/PageHeader.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import SearchableSelect from '@shared/components/form/SearchableSelect.vue';
import FormattingService from '@shared/services/FormattingService';
import ToastService from '@shared/services/ToastService';
import ProfileToolsClient from '@/services/api/ProfileToolsClient';
import type OtlpExportEventType from '@/services/api/model/OtlpExportEventType';
import '@shared/styles/shared-components.css';

const route = useRoute();
const profileId = route.params.profileId as string;

const loading = ref(true);
const loadError = ref<string | null>(null);
const eventTypes = ref<OtlpExportEventType[]>([]);

const selectedLabel = ref<string | null>(null);
const includeWeight = ref(false);
const downloading = ref(false);
const adding = ref(false);
const addedRecordingId = ref<string | null>(null);

const busy = computed(() => downloading.value || adding.value);

const selectItems = computed(() => {
  return eventTypes.value.map(event => ({
    label: event.label,
    code: event.code,
    samples: event.samples
  }));
});

const selectedEvent = computed<OtlpExportEventType | null>(() => {
  if (selectedLabel.value == null) {
    return null;
  }
  return eventTypes.value.find(event => event.label === selectedLabel.value) ?? null;
});

const CATEGORY_VARIANTS: Record<string, string> = {
  CPU: 'blue',
  Wall: 'info',
  Allocation: 'teal',
  Blocking: 'purple'
};

const categoryVariant = computed(() => {
  if (!selectedEvent.value) {
    return 'primary';
  }
  return CATEGORY_VARIANTS[selectedEvent.value.category] ?? 'primary';
});

const sampleTypeText = computed(() => {
  if (!selectedEvent.value) {
    return '';
  }
  // OTLP carries a single sample_type: the weight metric when requested, otherwise the sample count.
  if (includeWeight.value && selectedEvent.value.hasWeight && selectedEvent.value.weightSampleType) {
    return selectedEvent.value.weightSampleType;
  }
  return selectedEvent.value.sampleType;
});

const WEIGHT_UNIT_BYTES = 'bytes';
const WEIGHT_UNIT_NANOSECONDS = 'nanoseconds';

// Format the weight in the same units the rest of the app uses: bytes for allocation events,
// a duration for time-based events, plain number otherwise.
const weightText = computed(() => {
  const event = selectedEvent.value;
  if (!event || event.weight == null) {
    return '—';
  }
  // weightSampleType is `type/unit` (e.g. `alloc/bytes`); the unit drives the formatter.
  const unit = event.weightSampleType?.split('/')[1];
  if (unit === WEIGHT_UNIT_BYTES) {
    return FormattingService.formatBytes(event.weight);
  }
  if (unit === WEIGHT_UNIT_NANOSECONDS) {
    return FormattingService.formatDuration2Units(event.weight);
  }
  return FormattingService.formatNumber(event.weight);
});

const filename = computed(() => {
  const safeProfileId = profileId.replace(/[^A-Za-z0-9_-]/g, '_').slice(0, 24);
  const eventShort = selectedEvent.value
    ? selectedEvent.value.code.replace(/^[a-z]+\./, '').toLowerCase()
    : 'event';
  return `jeffrey-${safeProfileId}-${eventShort}.otlp`;
});

// Selecting a different event resets the weight toggle so it can never be stale for an event
// that has no weight dimension.
function resetToggleForSelection(): void {
  includeWeight.value = false;
  addedRecordingId.value = null;
}

async function convertAndDownload(): Promise<void> {
  if (!selectedEvent.value || busy.value) {
    return;
  }
  downloading.value = true;
  try {
    const client = new ProfileToolsClient(profileId);
    const blob = await client.downloadOtlp(selectedEvent.value.code, includeWeight.value);
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename.value;
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    URL.revokeObjectURL(url);
    ToastService.success('Downloaded', 'OTLP file saved.');
  } catch (error) {
    console.error('OTLP download failed:', error);
    ToastService.error('Download Failed', 'Could not generate the OTLP file.');
  } finally {
    downloading.value = false;
  }
}

async function addToRecordings(): Promise<void> {
  if (!selectedEvent.value || busy.value) {
    return;
  }
  adding.value = true;
  try {
    const client = new ProfileToolsClient(profileId);
    const result = await client.addOtlpToRecordings(
      selectedEvent.value.code,
      includeWeight.value
    );
    addedRecordingId.value = result.recordingId;
    ToastService.success('Added to Recordings', 'The OTLP file is available in Recordings.');
  } catch (error) {
    console.error('Add to recordings failed:', error);
    ToastService.error('Failed', 'Could not add the OTLP file to Recordings.');
  } finally {
    adding.value = false;
  }
}

onMounted(async () => {
  try {
    const client = new ProfileToolsClient(profileId);
    eventTypes.value = await client.otlpEventTypes();
  } catch (error) {
    console.error('Failed to load OTLP event types:', error);
    loadError.value = 'Failed to load event types.';
  } finally {
    loading.value = false;
  }
});

// Re-run the reset whenever the selection changes.
watch(selectedLabel, resetToggleForSelection);
</script>

<style scoped>
.item-label {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-dark);
}

.item-sub {
  font-size: 0.7rem;
  color: var(--color-text-muted);
  margin-top: 1px;
}

.mono {
  font-family: var(--font-family-monospace, monospace);
}

.weight-option {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  background: var(--color-light);
  cursor: pointer;
  transition:
    border-color var(--transition-fast) ease,
    background var(--transition-fast) ease;
}

.weight-option:hover:not(.disabled) {
  border-color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 4%, transparent);
}

.weight-option__box {
  width: 1.15rem;
  height: 1.15rem;
  margin: 1px 0 0;
  accent-color: var(--color-primary);
  cursor: pointer;
  flex-shrink: 0;
}

.weight-option__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.weight-option__title {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--color-dark);
  line-height: 1.2;
}

.weight-option__hint {
  font-size: 0.78rem;
  color: var(--color-text-muted);
}

.weight-option.disabled {
  opacity: 0.6;
  cursor: default;
}

.weight-option.disabled .weight-option__box {
  cursor: default;
}

.filename-box {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  padding: 8px 12px;
  background: var(--color-light);
  color: var(--color-text-secondary, var(--color-dark));
  font-size: 0.8rem;
  word-break: break-all;
}

.summary-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-light);
  padding: 14px 16px;
}

.summary-title {
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--color-dark);
  margin-bottom: 10px;
}

.summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 0;
  border-bottom: 1px dashed var(--color-border-light);
  font-size: 0.82rem;
}

.summary-row:last-of-type {
  border-bottom: none;
}

.summary-kv {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--color-border);
  font-size: 0.78rem;
  color: var(--color-text-muted);
}
</style>
