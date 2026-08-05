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
  Picks the folder the Advisor reads. The folders saved under Settings → Advisor are the list, named
  by whoever saved them, with a filter for when the list outgrows a glance. A path that was never
  saved is still typeable through "Custom path…", which is also all this offers when nothing is
  saved yet.
-->
<template>
  <div ref="rootRef" class="sfs">
    <!-- A path that belongs to no saved folder: the plain field, as it was before the list existed. -->
    <div v-if="customMode" class="sfs-custom">
      <span class="sfs-fic"><i class="bi bi-folder2-open"></i></span>
      <input
        ref="customInputRef"
        :value="modelValue"
        type="text"
        spellcheck="false"
        autocomplete="off"
        placeholder="/home/you/projects/order-service"
        @input="onCustomInput"
      />
      <button
        v-if="folders.length > 0"
        type="button"
        class="sfs-back"
        title="Pick a saved folder instead"
        @click="useSavedList"
      >
        <i class="bi bi-list-ul"></i>
      </button>
    </div>

    <template v-else>
      <button
        ref="controlRef"
        type="button"
        class="sfs-control"
        :class="{ open: isOpen }"
        :aria-expanded="isOpen"
        @click="toggle"
      >
        <span class="sfs-fic"><i class="bi bi-folder2-open"></i></span>
        <span v-if="selected" class="sfs-chosen">
          <span class="sfs-name">{{ selected.name }}</span>
          <span class="sfs-path">{{ selected.path }}</span>
        </span>
        <span v-else class="sfs-placeholder">
          {{ loading ? 'Loading folders…' : 'Choose a folder' }}
        </span>
        <i class="bi bi-chevron-down sfs-caret"></i>
      </button>

      <!--
        Teleported and positioned against the control: the profile detail page draws this card inside
        a container with overflow:hidden, which would otherwise cut the list off at the card's edge.
      -->
      <Teleport to="body">
        <div v-if="isOpen" ref="popRef" class="sfs-pop" :style="popStyle">
          <div v-if="folders.length > 0" class="sfs-filter">
            <i class="bi bi-search"></i>
            <input
              ref="filterInputRef"
              v-model="query"
              type="text"
              spellcheck="false"
              autocomplete="off"
              placeholder="Filter folders"
              @keydown.enter.prevent="selectFirstMatch"
            />
          </div>

          <div class="sfs-list" :style="listStyle">
            <button
              v-for="folder in filtered"
              :key="folder.folderId"
              type="button"
              class="sfs-opt"
              :class="{
                selected: folder.folderId === selected?.folderId,
                missing: !folder.present
              }"
              :disabled="!folder.present"
              :title="folder.present ? folder.path : 'No folder at this path'"
              @click="select(folder)"
            >
              <span class="sfs-dot" :class="{ bad: !folder.present }"></span>
              <span class="sfs-opt-body">
                <span class="sfs-name">{{ folder.name }}</span>
                <span class="sfs-path">{{ folder.path }}</span>
              </span>
              <span v-if="!folder.present" class="sfs-missing">Not found</span>
              <i
                v-else-if="folder.folderId === selected?.folderId"
                class="bi bi-check-lg sfs-tick"
              ></i>
            </button>

            <p v-if="folders.length > 0 && filtered.length === 0" class="sfs-none">
              No folders match “{{ query.trim() }}”.
            </p>
            <p v-else-if="folders.length === 0 && !loading" class="sfs-none">
              No saved folders yet — add them in Settings → Advisor.
            </p>
          </div>

          <div class="sfs-sep"></div>
          <button type="button" class="sfs-opt custom" @click="useCustomPath">
            <i class="bi bi-pencil"></i>
            Custom path…
          </button>
        </div>
      </Teleport>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import AdvisorProjectFoldersClient from '@/services/api/AdvisorProjectFoldersClient';
import type AdvisorProjectFolder from '@/services/api/model/AdvisorProjectFolder';
import {
  filterFolders,
  findByPath,
  firstSelectable
} from '@/views/profiles/detail/advisor/sourceFolderPicker';

const props = defineProps<{ modelValue: string }>();
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();

const client = new AdvisorProjectFoldersClient();

const folders = ref<AdvisorProjectFolder[]>([]);
const loading = ref(true);
const isOpen = ref(false);
const query = ref('');

/**
 * Which face the control shows. {@code auto} follows the stored path — it is the mode until someone
 * says otherwise, because the page hands the stored path down after this component has mounted, so
 * a decision taken at mount time would be taken against an empty value.
 */
const mode = ref<'auto' | 'saved' | 'custom'>('auto');

const rootRef = ref<HTMLElement | null>(null);
const popRef = ref<HTMLElement | null>(null);
const controlRef = ref<HTMLElement | null>(null);
const filterInputRef = ref<HTMLInputElement | null>(null);
const customInputRef = ref<HTMLInputElement | null>(null);

const popStyle = ref<Record<string, string>>({});
const listStyle = ref<Record<string, string>>({});

/** How much of the viewport the list may take before it starts scrolling instead of growing. */
const LIST_MAX_HEIGHT_PX = 260;
const LIST_MIN_HEIGHT_PX = 120;
/** Room the filter row, the separator and the custom-path row need under the list. */
const POP_CHROME_HEIGHT_PX = 110;

const selected = computed(() => findByPath(folders.value, props.modelValue));
const filtered = computed(() => filterFolders(folders.value, query.value));

/**
 * A path belonging to no saved folder can only be shown as the text it was typed as — and with
 * nothing saved yet, typing is the only thing left to offer.
 */
const customMode = computed(() => {
  if (mode.value !== 'auto') {
    return mode.value === 'custom';
  }
  if (loading.value) {
    return false;
  }
  if (props.modelValue.trim().length > 0) {
    return selected.value === null;
  }
  return folders.value.length === 0;
});

function toggle(): void {
  if (isOpen.value) {
    close();
    return;
  }
  isOpen.value = true;
  query.value = '';
  position();
  nextTick(() => filterInputRef.value?.focus());
}

/**
 * Pins the teleported list to the control. The list is also capped to the room left under it, so a
 * long list scrolls rather than running off the bottom of the window.
 */
function position(): void {
  const control = controlRef.value;
  if (!control) {
    return;
  }
  const rect = control.getBoundingClientRect();
  const roomBelow = window.innerHeight - rect.bottom - POP_CHROME_HEIGHT_PX;

  popStyle.value = {
    top: `${rect.bottom + 5}px`,
    left: `${rect.left}px`,
    width: `${rect.width}px`
  };
  listStyle.value = {
    maxHeight: `${Math.max(LIST_MIN_HEIGHT_PX, Math.min(LIST_MAX_HEIGHT_PX, roomBelow))}px`
  };
}

function close(): void {
  isOpen.value = false;
}

function select(folder: AdvisorProjectFolder): void {
  emit('update:modelValue', folder.path);
  close();
}

/** Enter picks what the filter narrowed to, so a folder can be chosen without leaving the keyboard. */
function selectFirstMatch(): void {
  const match = firstSelectable(filtered.value);
  if (match) {
    select(match);
  }
}

/** Keeps the current path as the starting point — switching modes should not wipe what was chosen. */
function useCustomPath(): void {
  mode.value = 'custom';
  close();
  nextTick(() => customInputRef.value?.focus());
}

function useSavedList(): void {
  mode.value = 'saved';
  nextTick(() => toggle());
}

function onCustomInput(event: Event): void {
  emit('update:modelValue', (event.target as HTMLInputElement).value);
}

function onClickOutside(event: MouseEvent): void {
  if (!isOpen.value) {
    return;
  }
  // The list lives on <body>, so "inside" is the control or the list — not the control's subtree.
  const target = event.target as Node;
  const inside =
    rootRef.value?.contains(target) === true || popRef.value?.contains(target) === true;
  if (!inside) {
    close();
  }
}

/** The list is pinned to a position, so anything that moves the control has to close or re-pin it. */
function onViewportChange(): void {
  if (isOpen.value) {
    position();
  }
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && isOpen.value) {
    close();
  }
}

onMounted(async () => {
  try {
    folders.value = await client.list();
  } catch {
    // A folder list that cannot be loaded must not block a run: fall back to typing a path.
    folders.value = [];
  } finally {
    loading.value = false;
  }
});

document.addEventListener('click', onClickOutside, true);
document.addEventListener('keydown', onKeydown);
// Capture: the control sits inside a scrollable page container, not the window.
window.addEventListener('scroll', onViewportChange, true);
window.addEventListener('resize', onViewportChange);
onBeforeUnmount(() => {
  document.removeEventListener('click', onClickOutside, true);
  document.removeEventListener('keydown', onKeydown);
  window.removeEventListener('scroll', onViewportChange, true);
  window.removeEventListener('resize', onViewportChange);
});
</script>

<style scoped>
.sfs {
  position: relative;
  flex: 1 1 320px;
  min-width: 0;
}

/* --- the closed control --- */
.sfs-control,
.sfs-custom {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  width: 100%;
  min-height: 38px;
  padding: 0.34rem 0.7rem;
  border: 1px solid var(--color-border-input);
  border-radius: var(--radius-base);
  background: var(--color-bg-card);
  text-align: left;
  font-family: var(--font-family-base);
}

.sfs-control {
  cursor: pointer;
}

.sfs-control:hover {
  border-color: var(--color-primary-border-light);
}

.sfs-control:focus-visible,
.sfs-control.open,
.sfs-custom:focus-within {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: var(--focus-ring);
}

.sfs-fic {
  color: var(--color-text-light);
  font-size: 0.85rem;
  line-height: 1;
  flex: none;
}

.sfs-caret {
  margin-left: auto;
  color: var(--color-text-light);
  font-size: 0.72rem;
}

.sfs-placeholder {
  font-size: 0.84rem;
  color: var(--color-text-light);
}

.sfs-chosen,
.sfs-opt-body {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.sfs-name {
  font-size: 0.84rem;
  font-weight: 600;
  color: var(--color-heading-dark);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sfs-path {
  font-family: var(--font-family-monospace);
  font-size: 0.78rem;
  color: var(--color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* --- custom path field --- */
.sfs-custom input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-family: var(--font-family-monospace);
  font-size: 0.84rem;
  color: var(--color-heading-dark);
  padding: 0.2rem 0;
}

.sfs-custom input::placeholder {
  color: var(--color-text-light);
}

.sfs-back {
  flex: none;
  border: none;
  background: transparent;
  color: var(--color-text-light);
  cursor: pointer;
  padding: 2px 4px;
  font-size: 0.85rem;
  line-height: 1;
}

.sfs-back:hover {
  color: var(--color-primary);
}

/* --- the popover --- */
.sfs-pop {
  position: fixed;
  z-index: var(--z-dropdown);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  box-shadow: var(--shadow-lg);
  padding: 5px;
}

.sfs-filter {
  position: relative;
  padding: 2px 2px 6px;
}

.sfs-filter i {
  position: absolute;
  left: 0.6rem;
  top: 50%;
  transform: translateY(-60%);
  color: var(--color-text-light);
  font-size: 0.75rem;
}

.sfs-filter input {
  width: 100%;
  border: 1px solid var(--color-border-input);
  border-radius: var(--radius-sm);
  background: var(--color-light);
  padding: 0.35rem 0.5rem 0.35rem 1.9rem;
  font-family: var(--font-family-base);
  font-size: 0.8rem;
  color: var(--color-heading-dark);
}

.sfs-filter input:focus {
  outline: none;
  border-color: var(--color-primary);
  background: var(--color-bg-card);
}

.sfs-list {
  max-height: 260px;
  overflow-y: auto;
}

.sfs-opt {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  padding: 7px 9px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-family: var(--font-family-base);
}

.sfs-opt:hover:not(:disabled) {
  background: var(--color-bg-hover-alt);
}

.sfs-opt.selected {
  background: var(--color-primary-light);
}

.sfs-opt:disabled {
  cursor: not-allowed;
}

.sfs-opt.missing .sfs-name,
.sfs-opt.missing .sfs-path {
  color: var(--color-text-light);
}

.sfs-dot {
  width: 6px;
  height: 6px;
  flex: none;
  border-radius: var(--radius-circle);
  background: var(--color-success);
}

.sfs-dot.bad {
  background: var(--color-danger);
}

.sfs-missing {
  margin-left: auto;
  flex: none;
  font-size: 0.66rem;
  font-weight: 650;
  color: var(--color-red-text);
  background: var(--color-red-bg);
  border-radius: var(--radius-pill);
  padding: 1px 7px;
}

.sfs-tick {
  margin-left: auto;
  color: var(--color-primary);
  font-size: 0.8rem;
}

.sfs-none {
  margin: 0;
  padding: 10px 9px;
  font-size: 0.78rem;
  color: var(--color-text-muted);
}

.sfs-sep {
  height: 1px;
  background: var(--color-border);
  margin: 5px 2px;
}

.sfs-opt.custom {
  color: var(--color-primary);
  font-size: 0.82rem;
  font-weight: 600;
}

.sfs-opt.custom:hover {
  background: var(--color-primary-lighter);
}
</style>
