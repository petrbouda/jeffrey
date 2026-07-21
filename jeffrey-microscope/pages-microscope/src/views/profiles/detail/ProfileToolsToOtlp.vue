<template>
  <PageHeader
    icon="bi-box-arrow-down"
    title="Convert to OTLP"
    description="Export one or more stack-based event types as a single OpenTelemetry profiles (.otlp) file — download it or add it back to Recordings. Each type exports its sample count; enable Weight to export its weight metric (bytes/nanoseconds) instead — the flamegraph still lets you switch back to sample count. Every frame keeps its profile.frame.type so Java, native and kernel frames stay distinguishable."
  >
    <LoadingState v-if="loading" message="Loading event types..." />
    <ErrorState v-else-if="loadError" :message="loadError" />

    <div v-else-if="eventTypes.length === 0" class="text-muted small py-2">
      <i class="bi bi-info-circle me-1"></i>
      This profile has no stack-based event types to export.
    </div>

    <template v-else>
      <DualPanel :left-title="availableTitle" :right-title="selectedTitle">
        <!-- Left: searchable pool of available event types -->
        <template #left>
          <SearchInput v-model="search" placeholder="Search event types..." class="otlp-search" />
          <div v-if="availableEvents.length === 0" class="empty-hint">
            {{ search ? 'No event types match your search.' : 'All event types are selected.' }}
          </div>
          <div v-else class="evt-list">
            <button
              v-for="event in availableEvents"
              :key="event.code"
              type="button"
              class="evt evt-available"
              @click="addEvent(event)"
            >
              <i class="bi bi-plus-lg evt-icon"></i>
              <span class="evt-main">
                <span class="evt-label">{{ event.label }}</span>
                <span class="evt-sub">
                  <span class="mono">{{ event.code }}</span> ·
                  {{ FormattingService.formatNumber(event.samples) }} samples
                </span>
              </span>
              <Badge :value="event.category" :variant="categoryVariant(event)" size="xxs" />
            </button>
          </div>
        </template>

        <!-- Right: chosen event types, each with its Count/Weight choice -->
        <template #right>
          <div v-if="selected.length === 0" class="empty-hint">
            Select event types on the left to include them in the export.
          </div>
          <div v-else class="evt-list">
            <div v-for="item in selected" :key="item.event.code" class="evt evt-selected">
              <span class="evt-main">
                <span class="evt-label">{{ item.event.label }}</span>
                <span class="evt-sub">
                  <span class="mono">{{ item.event.code }}</span> ·
                  {{ FormattingService.formatNumber(item.event.samples) }} samples
                </span>
              </span>
              <span class="count-tag">Count</span>
              <span class="weight-toggle" :class="{ disabled: !item.event.hasWeight }">
                <button
                  type="button"
                  class="switch"
                  role="switch"
                  :class="{ on: item.includeWeight && item.event.hasWeight }"
                  :aria-checked="item.includeWeight && item.event.hasWeight"
                  :disabled="!item.event.hasWeight"
                  :title="item.event.hasWeight ? weightSampleTypeText(item.event) : 'This event has no weight dimension'"
                  @click="toggleWeight(item)"
                ></button>
                <span class="weight-label" :class="{ on: item.includeWeight && item.event.hasWeight }">
                  Weight
                </span>
              </span>
              <button
                type="button"
                class="evt-remove"
                aria-label="Remove from export"
                @click="removeEvent(item.event.code)"
              >
                <i class="bi bi-x-lg"></i>
              </button>
            </div>
          </div>
        </template>
      </DualPanel>

      <div class="row g-4 mt-1 align-items-end">
        <div class="col-lg-6">
          <label class="form-label mb-1 small text-uppercase fw-semibold text-muted">Filename</label>
          <div class="filename-box mono">{{ filename }}</div>
        </div>
        <div class="col-lg-6">
          <div class="d-flex gap-2">
            <button
              class="btn btn-sm btn-primary"
              :disabled="selected.length === 0 || busy"
              @click="convertAndDownload"
            >
              <span v-if="downloading" class="spinner-border spinner-border-sm me-1"></span>
              <i v-else class="bi bi-download me-1"></i>
              Convert &amp; Download
            </button>
            <button
              class="btn btn-sm btn-outline-primary"
              :disabled="selected.length === 0 || busy"
              @click="addToRecordings"
            >
              <span v-if="adding" class="spinner-border spinner-border-sm me-1"></span>
              <i v-else class="bi bi-collection me-1"></i>
              Add to Recordings
            </button>
          </div>
        </div>
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
    </template>
  </PageHeader>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import Badge from '@shared/components/Badge.vue';
import PageHeader from '@shared/components/layout/PageHeader.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import DualPanel from '@shared/components/DualPanel.vue';
import SearchInput from '@shared/components/form/SearchInput.vue';
import FormattingService from '@shared/services/FormattingService';
import ToastService from '@shared/services/ToastService';
import ProfileToolsClient from '@/services/api/ProfileToolsClient';
import type OtlpExportEventType from '@/services/api/model/OtlpExportEventType';
import type OtlpExportSelection from '@/services/api/model/OtlpExportSelection';

// One chosen event type plus whether to export its weight metric instead of the sample count.
interface SelectedEvent {
  event: OtlpExportEventType;
  includeWeight: boolean;
}

const route = useRoute();
const profileId = route.params.profileId as string;

const loading = ref(true);
const loadError = ref<string | null>(null);
const eventTypes = ref<OtlpExportEventType[]>([]);

const search = ref('');
const selected = ref<SelectedEvent[]>([]);
const downloading = ref(false);
const adding = ref(false);
const addedRecordingId = ref<string | null>(null);

const busy = computed(() => downloading.value || adding.value);

const selectedCodes = computed(() => new Set(selected.value.map(item => item.event.code)));

// Available = every event type not yet selected, filtered by the search box (label or code).
const availableEvents = computed(() => {
  const query = search.value.trim().toLowerCase();
  return eventTypes.value.filter(event => {
    if (selectedCodes.value.has(event.code)) {
      return false;
    }
    if (query === '') {
      return true;
    }
    return event.label.toLowerCase().includes(query) || event.code.toLowerCase().includes(query);
  });
});

const availableTitle = computed(() => `Available · ${availableEvents.value.length}`);
const selectedTitle = computed(() => `Selected for export · ${selected.value.length}`);

const CATEGORY_VARIANTS: Record<string, string> = {
  CPU: 'blue',
  Wall: 'info',
  Allocation: 'teal',
  Blocking: 'purple'
};

function categoryVariant(event: OtlpExportEventType): string {
  return CATEGORY_VARIANTS[event.category] ?? 'primary';
}

function weightSampleTypeText(event: OtlpExportEventType): string {
  return event.weightSampleType
    ? `Export weight as ${event.weightSampleType}`
    : 'Export the weight metric';
}

// Mirrors the backend filename: single type keeps the short event name, several become "<n>types".
const filename = computed(() => {
  const safeProfileId = profileId.replace(/[^A-Za-z0-9_-]/g, '_').slice(0, 24);
  if (selected.value.length === 1) {
    const short = selected.value[0].event.code.replace(/^[a-z]+\./, '').toLowerCase();
    return `jeffrey-${safeProfileId}-${short}.otlp`;
  }
  if (selected.value.length === 0) {
    return `jeffrey-${safeProfileId}-event.otlp`;
  }
  return `jeffrey-${safeProfileId}-${selected.value.length}types.otlp`;
});

function addEvent(event: OtlpExportEventType): void {
  if (selectedCodes.value.has(event.code)) {
    return;
  }
  selected.value.push({ event, includeWeight: false });
  addedRecordingId.value = null;
}

function removeEvent(code: string): void {
  selected.value = selected.value.filter(item => item.event.code !== code);
  addedRecordingId.value = null;
}

// Count is always exported; the toggle only adds/removes the optional weight profile.
function toggleWeight(item: SelectedEvent): void {
  if (!item.event.hasWeight) {
    return;
  }
  item.includeWeight = !item.includeWeight;
  addedRecordingId.value = null;
}

function toSelections(): OtlpExportSelection[] {
  return selected.value.map(item => ({
    eventType: item.event.code,
    includeWeight: item.includeWeight
  }));
}

async function convertAndDownload(): Promise<void> {
  if (selected.value.length === 0 || busy.value) {
    return;
  }
  downloading.value = true;
  try {
    const client = new ProfileToolsClient(profileId);
    const blob = await client.downloadOtlp(toSelections());
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
  if (selected.value.length === 0 || busy.value) {
    return;
  }
  adding.value = true;
  try {
    const client = new ProfileToolsClient(profileId);
    const result = await client.addOtlpToRecordings(toSelections());
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
</script>

<style scoped>
.otlp-search {
  margin-bottom: 0.75rem;
}

.mono {
  font-family: var(--font-family-monospace, monospace);
}

.empty-hint {
  font-size: 0.8rem;
  color: var(--color-text-muted);
  padding: 14px 4px;
}

.evt-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 320px;
  overflow-y: auto;
  padding-right: 4px;
}

.evt {
  display: flex;
  align-items: center;
  gap: 11px;
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  background: var(--color-light);
  text-align: left;
  transition:
    border-color var(--transition-fast) ease,
    background var(--transition-fast) ease;
}

.evt-available {
  cursor: pointer;
}

.evt-available:hover {
  border-color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 4%, transparent);
}

.evt-selected {
  border-color: var(--color-primary-border-light);
  background: var(--color-primary-lighter);
}

.evt-icon {
  color: var(--color-primary);
  font-size: 0.85rem;
  flex-shrink: 0;
}

.evt-main {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  flex: 1;
}

.evt-label {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-dark);
  line-height: 1.2;
}

.evt-sub {
  font-size: 0.72rem;
  color: var(--color-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Count is always exported (static marker); Weight is an optional on/off toggle switch. */
.count-tag {
  flex-shrink: 0;
  font-size: 0.66rem;
  font-weight: 600;
  color: var(--color-text-muted);
  padding: 2px 8px;
  border: 1px dashed var(--color-border-input);
  border-radius: 999px;
  white-space: nowrap;
}

.weight-toggle {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  flex-shrink: 0;
}

.weight-label {
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--color-text-muted);
  transition: color var(--transition-fast) ease;
}

.weight-label.on {
  color: var(--color-primary);
}

.switch {
  position: relative;
  width: 34px;
  height: 19px;
  border: none;
  padding: 0;
  border-radius: 999px;
  background: var(--color-border-input);
  cursor: pointer;
  transition: background var(--transition-fast) ease;
}

.switch::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 15px;
  height: 15px;
  border-radius: 50%;
  background: var(--color-white);
  box-shadow: var(--shadow-sm);
  transition: left var(--transition-fast) ease;
}

.switch.on {
  background: var(--color-primary);
}

.switch.on::after {
  left: 17px;
}

.switch:disabled {
  cursor: default;
}

.weight-toggle.disabled {
  opacity: 0.4;
}

.evt-remove {
  flex-shrink: 0;
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 0.75rem;
  line-height: 1;
  padding: 4px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition:
    color var(--transition-fast) ease,
    background var(--transition-fast) ease;
}

.evt-remove:hover {
  color: var(--color-danger);
  background: var(--color-danger-light);
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
</style>
