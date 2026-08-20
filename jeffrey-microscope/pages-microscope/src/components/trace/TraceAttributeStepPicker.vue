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
  The two-step picker of the attribute pages: which spans, then which of the things those spans
  carried. Two numbered chips, each opening a dropdown — one component, so Search Traces, Attribute
  Values and Latency by Attributes all ask the two questions the same way.

  Controlled by the caller: the chips show what the props say is chosen, and picking emits rather
  than mutates — the search page keeps the selection as a draft, the breakdown pages keep it in the
  URL, and this component does not care which. Only the key catalog is its own: keys are loaded here,
  per event type, because every caller would otherwise load the same list the same way.
-->
<template>
  <span ref="rootEl" class="step-picker">
    <span class="picker">
      <button
        type="button"
        class="picker-chip"
        :class="{ open: openPicker === 'type', active: selectedType === null }"
        aria-label="Choose an event type"
        @click="togglePicker('type')"
      >
        <span class="chip-step" :class="{ dim: selectedType === null }">1</span>
        <span class="chip-label">Event type</span>
        <span v-if="selectedType !== null" class="chip-value">
          <span class="chip-namespace">{{ namespaceOf(selectedType) }}</span
          >{{ leafOf(selectedType) }}
        </span>
        <span v-else class="chip-placeholder">choose…</span>
        <i class="bi bi-chevron-down chip-chev"></i>
      </button>

      <div v-if="openPicker === 'type'" class="picker-pop">
        <div class="pop-head">
          <span class="chip-step">1</span>
          Which spans carry the {{ lowerKeyWord }}?
        </div>
        <button
          v-for="type in eventTypes"
          :key="type.eventType"
          type="button"
          class="pop-item"
          :class="{ selected: type.eventType === selectedType }"
          @click="pickType(type)"
        >
          <span class="item-name">
            <span class="item-namespace">{{ namespaceOf(type.eventType) }}</span
            ><span class="item-leaf">{{ leafOf(type.eventType) }}</span>
          </span>
          <span class="item-meta">
            <span class="item-count">{{ FormattingService.formatNumber(type.spanCount) }}</span>
            spans ·
            <span class="item-count">{{ keyCountOf(type) }}</span>
            {{ lowerKeyWord }}s
          </span>
        </button>
      </div>
    </span>

    <span class="picker">
      <button
        type="button"
        class="picker-chip"
        :class="{
          open: openPicker === 'key',
          active: selectedType !== null && selectedKeyName === null
        }"
        :disabled="selectedType === null"
        :aria-label="`Choose a ${lowerKeyWord}`"
        @click="togglePicker('key')"
      >
        <span class="chip-step" :class="{ dim: selectedKeyName === null }">2</span>
        <span class="chip-label">{{ keyWord }}</span>
        <span v-if="selectedKeyName !== null" class="chip-value">{{ selectedKeyName }}</span>
        <span v-else class="chip-placeholder">choose…</span>
        <i class="bi bi-chevron-down chip-chev"></i>
      </button>

      <div v-if="openPicker === 'key'" class="picker-pop">
        <div class="pop-head">
          <span class="chip-step">2</span>
          {{ keyWord }}s on <span class="pop-head-type">{{ selectedType }}</span> spans
        </div>

        <p v-if="keysLoading" class="pop-note">Loading {{ lowerKeyWord }}s…</p>

        <p v-else-if="keysError !== null" class="pop-note">
          {{ keysError }}
          <button type="button" class="pop-retry" @click="loadKeys">Retry</button>
        </p>

        <p v-else-if="offeredKeys.length === 0" class="pop-note">
          Spans of this event type carried no attribute, declared field or shape to offer here.
        </p>

        <template v-else>
          <button
            v-for="key in offeredKeys"
            :key="keyToken(key)"
            type="button"
            class="pop-item"
            :class="{ selected: key.key === selectedKeyName }"
            @click="pickKey(key)"
          >
            <span class="item-name">{{ key.key }}</span>
            <span class="item-source" :class="`source-${key.source.toLowerCase()}`">
              {{ SOURCE_LABELS[key.source] }}
            </span>
            <span class="item-meta">
              <span v-if="key.searchOnly" class="pill-search-only">search only</span>
              <span class="item-count">{{ FormattingService.formatNumber(key.distinctValues) }}</span>
              values
            </span>
          </button>
        </template>

        <!--
          Said rather than left out: a key kept out of the list because it is too wide is a fact
          about the instrumentation, and silently showing four of a type's eleven keys reads as the
          type only having four.
        -->
        <p v-if="hiddenSearchOnlyCount > 0" class="pop-note pop-note-divided">
          {{ hiddenSearchOnlyCount }} more
          {{ hiddenSearchOnlyCount === 1 ? 'attribute is' : 'attributes are' }}
          too wide to break down — search for
          {{ hiddenSearchOnlyCount === 1 ? 'it' : 'them' }} instead.
        </p>
      </div>
    </span>
  </span>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import FormattingService from '@shared/services/FormattingService';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import {
  keyToken,
  type TraceAttributeKeyRow,
  type TraceAttributeSource,
  type TraceSpanTypeRow
} from '@/services/api/model/trace/TraceAttributeModels';

const props = withDefaults(
  defineProps<{
    eventTypes: TraceSpanTypeRow[];
    /** The chosen event type, or null before one is chosen. The caller owns where it lives. */
    selectedType: string | null;
    /** The chosen key's name — identity beyond the name is the caller's business. */
    selectedKeyName: string | null;
    /**
     * Whether keys too wide to break down are offered. The search page says yes — searching them is
     * what they are for; the breakdown pages say no, and the count of what was held back is stated
     * instead.
     */
    includeSearchOnly?: boolean;
    /** What step two calls its subject — "Key" on the search page, "Attribute" on the breakdowns. */
    keyWord?: string;
  }>(),
  {
    includeSearchOnly: false,
    keyWord: 'Key'
  }
);

const emit = defineEmits<{
  pickType: [type: TraceSpanTypeRow];
  pickKey: [key: TraceAttributeKeyRow];
}>();

/** How a row names where its key came from — worded for a reader, not after the enum. */
const SOURCE_LABELS: Record<TraceAttributeSource, string> = {
  ATTRIBUTE: 'attribute',
  EVENT_FIELD: 'event field',
  SPAN_SHAPE: 'span shape'
};

const route = useRoute();
const client = new ProfileTracesClient(route.params.profileId as string);

/** Which chip's dropdown is open. The two never open together — answering one opens the other. */
type OpenPicker = 'type' | 'key' | null;

const openPicker = ref<OpenPicker>(null);
const rootEl = ref<HTMLElement | null>(null);

const keys = ref<TraceAttributeKeyRow[]>([]);
const keysLoading = ref(false);
const keysError = ref<string | null>(null);

const lowerKeyWord = computed(() => props.keyWord.toLowerCase());

const offeredKeys = computed(() =>
  props.includeSearchOnly ? keys.value : keys.value.filter(key => !key.searchOnly)
);

const hiddenSearchOnlyCount = computed(() => keys.value.length - offeredKeys.value.length);

/** The step-1 meta only promises what step 2 will actually offer. */
function keyCountOf(type: TraceSpanTypeRow): number {
  return props.includeSearchOnly ? type.attributeCount : type.breakableCount;
}

/*
 * The namespace repeats down the whole list while the leaf is what tells the rows apart, so the
 * two halves are rendered apart — the prefix dimmed, the leaf carrying the weight. Split at the
 * last dot: the leaf is the part no other row shares.
 */
function namespaceOf(eventType: string): string {
  const dot = eventType.lastIndexOf('.');
  return dot === -1 ? '' : eventType.slice(0, dot + 1);
}

function leafOf(eventType: string): string {
  const dot = eventType.lastIndexOf('.');
  return dot === -1 ? eventType : eventType.slice(dot + 1);
}

function togglePicker(picker: 'type' | 'key'): void {
  openPicker.value = openPicker.value === picker ? null : picker;
}

/*
 * The key list opens by itself because step two is now the only question left. Re-picking the
 * current type is not a change and is not emitted — the caller would otherwise drop a key that is
 * still valid.
 */
function pickType(type: TraceSpanTypeRow): void {
  if (type.eventType !== props.selectedType) {
    emit('pickType', type);
  }
  openPicker.value = 'key';
}

function pickKey(key: TraceAttributeKeyRow): void {
  emit('pickKey', key);
  openPicker.value = null;
}

async function loadKeys(): Promise<void> {
  const type = props.selectedType;
  if (type === null) {
    keys.value = [];
    return;
  }
  keysLoading.value = true;
  keysError.value = null;
  try {
    keys.value = await client.getAttributeKeys(type);
  } catch (e: unknown) {
    console.error('Failed to load the attribute keys of an event type:', e);
    keysError.value = 'Failed to load the keys of this event type.';
    keys.value = [];
  } finally {
    keysLoading.value = false;
  }
}

// Loaded whenever the type changes, not only while the dropdown is open: a deep link arrives with
// both steps already answered, and the list has to be ready the moment the chip is clicked.
watch(() => props.selectedType, loadKeys, { immediate: true });

/*
 * The dropdowns close the way every dropdown closes — a click anywhere else, or Escape. Listening
 * on the document rather than a backdrop keeps the rest of the page clickable while one is open.
 */
function onDocumentClick(event: MouseEvent): void {
  if (openPicker.value === null) {
    return;
  }
  if (rootEl.value !== null && !rootEl.value.contains(event.target as Node)) {
    openPicker.value = null;
  }
}

function onDocumentKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    openPicker.value = null;
  }
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick);
  document.addEventListener('keydown', onDocumentKeydown);
});

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick);
  document.removeEventListener('keydown', onDocumentKeydown);
});
</script>

<style scoped>
.step-picker {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-2);
  flex-wrap: wrap;
}

.picker {
  position: relative;
  display: inline-flex;
}

.picker-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-2);
  border: 1px solid var(--color-border-input);
  background: var(--color-white);
  border-radius: var(--radius-sm);
  padding: var(--spacing-1) var(--spacing-3) var(--spacing-1) var(--spacing-1);
  font-size: var(--font-size-base);
  cursor: pointer;
  max-width: 340px;
}

/*
 * The next unanswered step is the loudest thing in the row — tinted ground, accent border, spoken
 * placeholder — while a step that cannot be taken yet sleeps at low opacity. The row reads as
 * "you are here", not as two grey accessories beside livelier controls.
 */
.picker-chip.active {
  border-color: var(--color-primary-border);
  background: var(--color-primary-lighter);
}

.picker-chip.active .chip-placeholder,
.picker-chip.active .chip-chev {
  color: var(--color-primary);
}

.picker-chip.active .chip-placeholder {
  font-weight: 500;
}

.picker-chip.open {
  border-color: var(--color-primary-border);
  box-shadow: 0 0 0 3px var(--color-primary-lighter);
}

/*
 * Not yet takeable, but never faded: full-contrast text on a grey ground, with the dashes carrying
 * the "not yet". Opacity made the whole step nearly invisible on the empty page, where it is the
 * one thing explaining what the flow expects next.
 */
.picker-chip:disabled {
  cursor: default;
  border-style: dashed;
  background: var(--color-lighter);
}

/* The dim badge blends into the chip's own grey ground; a shade darker keeps the numeral read. */
.picker-chip:disabled .chip-step.dim {
  background: var(--color-border-input);
}

.chip-step {
  width: 18px;
  height: 18px;
  border-radius: var(--radius-circle);
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary);
  color: var(--color-white);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.chip-step.dim {
  background: var(--color-lighter);
  color: var(--color-text-muted);
}

.chip-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.chip-value {
  font-family: var(--font-family-monospace);
  font-weight: 600;
  color: var(--color-dark);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chip-namespace {
  color: var(--color-text-light);
  font-weight: 400;
}

.chip-placeholder {
  color: var(--color-text-light);
}

.chip-chev {
  color: var(--color-text-light);
  font-size: var(--font-size-xs);
}

.picker-pop {
  position: absolute;
  top: calc(100% + var(--spacing-1));
  left: 0;
  z-index: 20;
  width: 430px;
  max-width: 82vw;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

.pop-head {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-2) var(--spacing-3);
  background: var(--color-light);
  border-bottom: 1px solid var(--color-border);
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.pop-head-type {
  font-family: var(--font-family-monospace);
  font-weight: 600;
  color: var(--color-dark);
}

.pop-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  width: 100%;
  text-align: left;
  border: 0;
  background: transparent;
  padding: var(--spacing-2) var(--spacing-3);
  font-size: var(--font-size-base);
  cursor: pointer;
}

.pop-item:hover,
.pop-item.selected {
  background: var(--color-primary-lighter);
}

.item-name {
  font-family: var(--font-family-monospace);
  font-weight: 400;
  color: var(--color-dark);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-namespace {
  color: var(--color-text-light);
}

.item-leaf {
  font-weight: 500;
}

.item-count {
  font-family: var(--font-family-monospace);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--color-dark);
}

.item-source {
  flex-shrink: 0;
  font-size: var(--font-size-xs);
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  border-radius: var(--radius-sm);
  padding: 1px var(--spacing-2);
  color: var(--color-text-muted);
  background: var(--color-lighter);
}

.item-source.source-attribute {
  color: var(--color-primary);
  background: var(--color-primary-light);
}

.item-source.source-event_field {
  color: var(--color-info-text);
  background: var(--color-info-light);
}

.item-meta {
  margin-left: auto;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-1);
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.pill-search-only {
  font-size: var(--font-size-xs);
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-warning-hover);
  background: var(--color-warning-light);
  border-radius: var(--radius-sm);
  padding: 1px var(--spacing-2);
}

.pop-note {
  margin: 0;
  padding: var(--spacing-3);
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.pop-note-divided {
  padding: var(--spacing-2) var(--spacing-3);
  border-top: 1px solid var(--color-border-light);
  color: var(--color-text-light);
}

.pop-retry {
  border: 0;
  background: transparent;
  color: var(--color-primary);
  font-size: var(--font-size-sm);
  padding: 0;
  margin-left: var(--spacing-1);
  cursor: pointer;
}
</style>
