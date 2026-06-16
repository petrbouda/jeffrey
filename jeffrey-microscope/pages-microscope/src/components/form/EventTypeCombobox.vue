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

<!--
  Editable, searchable combobox for picking a stack-trace event type. Suggestions are grouped by
  source (JDK / async-profiler) and fed from the backend catalog; the user can also type any custom
  event type that is not in the list.
-->
<template>
  <div ref="rootRef" class="evt-combo" :class="{ open: isOpen }">
    <div class="evt-control" @click="open">
      <span class="src-dot" :class="sourceClass(currentSource)" :title="sourceLabel(currentSource)"></span>
      <input
        ref="inputRef"
        :value="modelValue"
        class="evt-input"
        type="text"
        :placeholder="placeholder"
        autocomplete="off"
        spellcheck="false"
        @focus="open"
        @input="onInput"
        @keydown="onKeydown"
      />
      <span v-if="isCustom" class="evt-custom-tag">custom</span>
      <i class="bi bi-chevron-down evt-caret"></i>
    </div>

    <div v-if="isOpen" class="evt-pop">
      <template v-for="group in filteredGroups" :key="group.source">
        <div v-if="group.items.length" class="evt-group-hdr">
          <span class="src-dot" :class="sourceClass(group.source)"></span>{{ sourceLabel(group.source) }}
        </div>
        <div
          v-for="item in group.items"
          :key="item.code"
          class="evt-opt"
          :class="{ kbd: item.code === highlightedCode }"
          @mousedown.prevent="select(item.code)"
          @mouseenter="highlightedCode = item.code"
        >
          <span class="evt-code" v-html="highlight(item.code)"></span>
          <span class="evt-label">{{ item.label }}</span>
        </div>
      </template>

      <div
        v-if="showCustomRow"
        class="evt-opt is-custom"
        :class="{ kbd: highlightedCode === CUSTOM_KEY }"
        @mousedown.prevent="select(query.trim())"
        @mouseenter="highlightedCode = CUSTOM_KEY"
      >
        <span class="evt-code"><i class="bi bi-plus-lg"></i> Use custom: “{{ query.trim() }}”</span>
      </div>

      <div v-if="isEmpty" class="evt-empty">No matching event type</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onBeforeUnmount } from 'vue';
import type GuardianEventTypeOption from '@/services/api/model/GuardianEventTypeOption';
import type { GuardianEventTypeSource } from '@/services/api/model/GuardianEventTypeOption';

interface Props {
  modelValue: string;
  options: GuardianEventTypeOption[];
  placeholder?: string;
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: 'Search JDK / async-profiler events…'
});

const emit = defineEmits<{
  'update:modelValue': [value: string];
}>();

// Display order of the source groups in the dropdown.
const SOURCE_ORDER: GuardianEventTypeSource[] = ['JDK', 'ASYNC_PROFILER'];
const SOURCE_LABELS: Record<GuardianEventTypeSource, string> = {
  JDK: 'JDK',
  ASYNC_PROFILER: 'async-profiler'
};
const SOURCE_CLASSES: Record<GuardianEventTypeSource, string> = {
  JDK: 'src-jdk',
  ASYNC_PROFILER: 'src-async'
};
// Sentinel id used to keyboard-highlight the synthetic "custom" row.
const CUSTOM_KEY = '__custom__';

const rootRef = ref<HTMLElement | null>(null);
const inputRef = ref<HTMLInputElement | null>(null);
const isOpen = ref(false);
const query = ref('');
const highlightedCode = ref<string | null>(null);

const matchedOption = computed(() => props.options.find(o => o.code === props.modelValue) || null);
const isCustom = computed(() => props.modelValue.trim() !== '' && matchedOption.value === null);
const currentSource = computed<GuardianEventTypeSource | null>(() => matchedOption.value?.source ?? null);

const filteredGroups = computed(() => {
  const term = query.value.trim().toLowerCase();
  return SOURCE_ORDER.map(source => ({
    source,
    items: props.options.filter(
      o =>
        o.source === source &&
        (term === '' ||
          o.code.toLowerCase().includes(term) ||
          o.label.toLowerCase().includes(term))
    )
  }));
});

const matchCount = computed(() => filteredGroups.value.reduce((sum, g) => sum + g.items.length, 0));
const showCustomRow = computed(() => {
  const term = query.value.trim();
  if (term === '') {
    return false;
  }
  return !props.options.some(o => o.code.toLowerCase() === term.toLowerCase());
});
const isEmpty = computed(() => matchCount.value === 0 && !showCustomRow.value);

// Flattened option codes (plus the custom sentinel) for arrow-key navigation.
const navCodes = computed(() => {
  const codes = filteredGroups.value.flatMap(g => g.items.map(i => i.code));
  if (showCustomRow.value) {
    codes.push(CUSTOM_KEY);
  }
  return codes;
});

function sourceLabel(source: GuardianEventTypeSource | null): string {
  return source ? SOURCE_LABELS[source] : 'Custom event type';
}

function sourceClass(source: GuardianEventTypeSource | null): string {
  return source ? SOURCE_CLASSES[source] : 'src-custom';
}

function highlight(code: string): string {
  const term = query.value.trim();
  if (!term) {
    return code;
  }
  const escaped = term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return code.replace(new RegExp(`(${escaped})`, 'gi'), '<strong>$1</strong>');
}

function open(): void {
  isOpen.value = true;
  query.value = props.modelValue;
  highlightedCode.value = null;
}

function close(): void {
  isOpen.value = false;
  query.value = '';
  highlightedCode.value = null;
}

function onInput(event: Event): void {
  const value = (event.target as HTMLInputElement).value;
  query.value = value;
  isOpen.value = true;
  highlightedCode.value = null;
  emit('update:modelValue', value);
}

function select(code: string): void {
  emit('update:modelValue', code);
  close();
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    close();
    return;
  }
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault();
    const codes = navCodes.value;
    if (codes.length === 0) {
      return;
    }
    const current = highlightedCode.value ? codes.indexOf(highlightedCode.value) : -1;
    const delta = event.key === 'ArrowDown' ? 1 : -1;
    const next = (current + delta + codes.length) % codes.length;
    highlightedCode.value = codes[next];
    return;
  }
  if (event.key === 'Enter' && highlightedCode.value) {
    event.preventDefault();
    select(highlightedCode.value === CUSTOM_KEY ? query.value.trim() : highlightedCode.value);
  }
}

function onClickOutside(event: MouseEvent): void {
  if (isOpen.value && rootRef.value && !rootRef.value.contains(event.target as Node)) {
    close();
  }
}

document.addEventListener('click', onClickOutside, true);
onBeforeUnmount(() => {
  document.removeEventListener('click', onClickOutside, true);
});
</script>

<style scoped>
.evt-combo {
  position: relative;
}

.evt-control {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--color-border-input);
  border-radius: var(--radius-base);
  background: var(--color-neutral-bg);
  padding: 8px 12px;
  cursor: text;
  transition: all var(--transition-base);
}

.evt-combo.open .evt-control,
.evt-control:focus-within {
  background: var(--color-white);
  border-color: var(--color-primary);
  box-shadow: var(--focus-ring);
}

.evt-input {
  flex: 1;
  min-width: 60px;
  border: none;
  outline: none;
  background: transparent;
  font-family: 'SF Mono', Monaco, Menlo, Consolas, monospace;
  font-size: var(--font-size-sm);
  color: var(--color-dark);
}

.evt-input::placeholder {
  font-family: var(--font-family-base);
  color: var(--color-text-light);
}

.src-dot {
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--sc, var(--color-text-light));
}

.src-jdk {
  --sc: var(--color-primary);
}

.src-async {
  --sc: var(--color-amber);
}

.src-custom {
  --sc: var(--color-text-light);
}

.evt-custom-tag {
  font-size: 0.6rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-muted);
  background: var(--color-lighter);
  border-radius: var(--radius-sm);
  padding: 2px 6px;
}

.evt-caret {
  color: var(--color-text-light);
  font-size: 0.75rem;
}

.evt-pop {
  position: absolute;
  left: 0;
  right: 0;
  top: calc(100% + 6px);
  z-index: var(--z-dropdown, 1050);
  max-height: 300px;
  overflow: auto;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
}

.evt-group-hdr {
  position: sticky;
  top: 0;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 6px 14px;
  background: var(--color-light);
  border-bottom: 1px solid var(--color-border);
  font-size: 0.66rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-text-muted);
}

.evt-opt {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  cursor: pointer;
}

.evt-opt:hover,
.evt-opt.kbd {
  background: var(--color-primary-lighter);
}

.evt-code {
  font-family: 'SF Mono', Monaco, Menlo, Consolas, monospace;
  font-size: 0.8rem;
  color: var(--color-dark);
}

.evt-code :deep(strong) {
  color: var(--color-primary);
  font-weight: 700;
}

.evt-label {
  margin-left: auto;
  white-space: nowrap;
  font-size: 0.72rem;
  color: var(--color-text-muted);
}

.evt-opt.is-custom .evt-code {
  font-family: var(--font-family-base);
  color: var(--color-primary-hover);
  font-weight: 500;
}

.evt-empty {
  padding: 14px;
  text-align: center;
  font-size: 0.8rem;
  color: var(--color-text-muted);
}
</style>
