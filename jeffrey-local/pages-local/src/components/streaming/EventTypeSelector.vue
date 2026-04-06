<template>
  <div class="ets-root" ref="rootRef">
    <!-- Tag input area -->
    <div
      class="ets-tags-input"
      :class="{ focused: dropdownOpen }"
      @click="openDropdown"
    >
      <span
        v-for="eventType in modelValue"
        :key="eventType"
        class="ets-tag"
        :class="'ets-tag--' + getEventTypePrefix(eventType)"
      >
        {{ eventType }}
        <span class="ets-tag-remove" @click.stop="removeEvent(eventType)">&times;</span>
      </span>
      <input
        ref="searchInputRef"
        v-model="searchQuery"
        type="text"
        class="ets-search-input"
        placeholder="Search or type custom event..."
        @focus="openDropdown"
        @keydown.enter.prevent="onEnterKey"
        @keydown.backspace="onBackspace"
      />
    </div>
    <div class="ets-hint">Select at least one event type. Press Enter to add a custom event name.</div>

    <!-- Dropdown -->
    <Teleport to="body">
      <div
        v-if="dropdownOpen"
        class="ets-dropdown"
        :style="dropdownStyle"
        ref="dropdownRef"
      >
        <!-- Search -->
        <div class="ets-dropdown-search">
          <i class="bi bi-search ets-search-icon"></i>
          <input
            ref="dropdownSearchRef"
            v-model="searchQuery"
            type="text"
            placeholder="Filter events..."
            @keydown.enter.prevent="onEnterKey"
          />
        </div>

        <!-- Categorized list -->
        <div class="ets-dropdown-list">
          <template v-for="category in filteredCategories" :key="category.label">
            <div class="ets-cat-header">
              <span class="ets-cat-badge" :class="'ets-cat-badge--' + category.badge">
                {{ category.badge.toUpperCase() }}
              </span>
              {{ category.label }}
            </div>
            <div
              v-for="event in category.events"
              :key="event.name"
              class="ets-event-item"
              :class="{ selected: isSelected(event.name) }"
              @click="toggleEvent(event.name)"
            >
              <div class="ets-event-check">
                <i v-if="isSelected(event.name)" class="bi bi-check"></i>
              </div>
              <span class="ets-event-name">{{ event.name }}</span>
              <span class="ets-event-desc">{{ event.description }}</span>
            </div>
          </template>

          <div v-if="filteredCategories.length === 0 && searchQuery" class="ets-no-results">
            No events matching "{{ searchQuery }}"
          </div>
        </div>

        <!-- Custom event input -->
        <div class="ets-dropdown-footer">
          <span class="ets-footer-label">Custom:</span>
          <input
            v-model="customInput"
            type="text"
            class="ets-custom-input"
            placeholder="com.myapp.MyEvent"
            @keydown.enter.prevent="addCustomEvent"
          />
          <button
            class="ets-add-btn"
            :disabled="!customInput.trim()"
            @click="addCustomEvent"
          >
            + Add
          </button>
        </div>

        <!-- Done button -->
        <div class="ets-dropdown-done">
          <span class="ets-done-count">{{ modelValue.length }} selected</span>
          <button class="ets-done-btn" @click="closeDropdown">
            <i class="bi bi-check-lg"></i> Done
          </button>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import EVENT_TYPE_CATALOG, { getEventTypePrefix } from '@/services/EventTypeCatalog'

const props = defineProps<{
  modelValue: string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
}>()

const rootRef = ref<HTMLElement | null>(null)
const dropdownRef = ref<HTMLElement | null>(null)
const dropdownSearchRef = ref<HTMLInputElement | null>(null)

const searchQuery = ref('')
const customInput = ref('')
const dropdownOpen = ref(false)
const dropdownStyle = ref<Record<string, string>>({})

const filteredCategories = computed(() => {
  const q = searchQuery.value.toLowerCase()
  if (!q) return EVENT_TYPE_CATALOG

  return EVENT_TYPE_CATALOG
    .map((cat) => ({
      ...cat,
      events: cat.events.filter(
        (e) => e.name.toLowerCase().includes(q) || e.description.toLowerCase().includes(q)
      )
    }))
    .filter((cat) => cat.events.length > 0)
})

function isSelected(name: string): boolean {
  return props.modelValue.includes(name)
}

function toggleEvent(name: string) {
  if (isSelected(name)) {
    emit('update:modelValue', props.modelValue.filter((e) => e !== name))
  } else {
    emit('update:modelValue', [...props.modelValue, name])
  }
}

function removeEvent(name: string) {
  emit('update:modelValue', props.modelValue.filter((e) => e !== name))
}

function onEnterKey() {
  const value = searchQuery.value.trim()
  if (value && !isSelected(value)) {
    emit('update:modelValue', [...props.modelValue, value])
    searchQuery.value = ''
  }
}

function onBackspace() {
  if (!searchQuery.value && props.modelValue.length > 0) {
    emit('update:modelValue', props.modelValue.slice(0, -1))
  }
}

function addCustomEvent() {
  const value = customInput.value.trim()
  if (value && !isSelected(value)) {
    emit('update:modelValue', [...props.modelValue, value])
    customInput.value = ''
  }
}

function closeDropdown() {
  dropdownOpen.value = false
  searchQuery.value = ''
}

function openDropdown() {
  dropdownOpen.value = true
  updateDropdownPosition()
  nextTick(() => {
    dropdownSearchRef.value?.focus()
  })
}

function updateDropdownPosition() {
  if (!rootRef.value) return
  const rect = rootRef.value.getBoundingClientRect()
  dropdownStyle.value = {
    position: 'fixed',
    top: `${rect.bottom + 4}px`,
    left: `${rect.left}px`,
    width: `${rect.width}px`,
    zIndex: '1060'
  }
}

function onClickOutside(e: MouseEvent) {
  const target = e.target as Node
  if (
    rootRef.value?.contains(target) ||
    dropdownRef.value?.contains(target)
  ) {
    return
  }
  dropdownOpen.value = false
  searchQuery.value = ''
}

onMounted(() => {
  document.addEventListener('mousedown', onClickOutside)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onClickOutside)
})
</script>

<style scoped>
.ets-root {
  position: relative;
}

.ets-tags-input {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  min-height: 36px;
  padding: 4px 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-white);
  cursor: text;
  transition: var(--transition-fast);
}

.ets-tags-input.focused {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-light);
}

.ets-search-input {
  border: none;
  outline: none;
  font-family: var(--font-monospace);
  font-size: 0.85rem;
  flex: 1;
  min-width: 100px;
  background: transparent;
  color: var(--color-body);
  height: 28px;
}

.ets-search-input::placeholder {
  font-family: var(--font-base);
  color: var(--color-text-light);
  font-style: italic;
}

/* Tags */
.ets-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 10px;
  border-radius: var(--radius-sm);
  font-size: 0.78rem;
  font-weight: 500;
  font-family: var(--font-monospace);
  white-space: nowrap;
}

.ets-tag--jdk { background: var(--color-blue-bg); color: var(--color-blue-text); }
.ets-tag--jeffrey { background: var(--color-violet-light); color: var(--color-violet); }
.ets-tag--profiler { background: var(--color-teal-light); color: var(--color-teal); }
.ets-tag--custom { background: var(--color-amber-light); color: var(--color-amber-text); }

.ets-tag-remove {
  margin-left: 2px;
  cursor: pointer;
  opacity: 0.5;
  font-size: 0.75rem;
  line-height: 1;
}

.ets-tag-remove:hover {
  opacity: 1;
}

.ets-hint {
  font-size: 0.75rem;
  color: var(--color-muted);
  margin-top: 4px;
  font-style: italic;
}

/* Dropdown */
.ets-dropdown {
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  max-height: 380px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.ets-dropdown-search {
  padding: 8px 12px;
  border-bottom: 1px solid var(--color-border-light);
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.ets-search-icon {
  color: var(--color-text-light);
  font-size: 0.8rem;
  flex-shrink: 0;
}

.ets-dropdown-search input {
  width: 100%;
  border: none;
  outline: none;
  font-size: 0.85rem;
  color: var(--color-body);
  background: transparent;
  font-family: var(--font-base);
}

.ets-dropdown-search input::placeholder {
  color: var(--color-text-light);
}

.ets-dropdown-list {
  flex: 1;
  overflow-y: auto;
}

.ets-cat-header {
  padding: 7px 12px;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-muted);
  background: var(--color-light);
  border-bottom: 1px solid var(--color-border-light);
  border-top: 1px solid var(--color-border-light);
  display: flex;
  align-items: center;
  gap: 6px;
  position: sticky;
  top: 0;
  z-index: 1;
}

.ets-cat-badge {
  padding: 2px 7px;
  border-radius: 100px;
  font-size: 0.65rem;
  font-weight: 600;
}

.ets-cat-badge--jdk { background: var(--color-blue-bg); color: var(--color-blue-text); }
.ets-cat-badge--jeffrey { background: var(--color-violet-light); color: var(--color-violet); }
.ets-cat-badge--profiler { background: var(--color-teal-light); color: var(--color-teal); }

.ets-event-item {
  padding: 6px 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: background var(--transition-fast);
}

.ets-event-item:hover {
  background: var(--color-light);
}

.ets-event-item.selected {
  background: var(--color-primary-light);
}

.ets-event-check {
  width: 16px;
  height: 16px;
  border: 2px solid var(--color-border);
  border-radius: 3px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.65rem;
  transition: all var(--transition-fast);
  flex-shrink: 0;
}

.ets-event-item.selected .ets-event-check {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: var(--color-white);
}

.ets-event-name {
  font-family: var(--font-monospace);
  font-size: 0.84rem;
  color: var(--color-body);
}

.ets-event-desc {
  font-size: 0.75rem;
  color: var(--color-muted);
  margin-left: auto;
  white-space: nowrap;
}

.ets-no-results {
  padding: 16px;
  text-align: center;
  color: var(--color-muted);
  font-size: 0.8rem;
}

/* Footer with custom input */
.ets-dropdown-footer {
  padding: 8px 12px;
  border-top: 1px solid var(--color-border);
  background: var(--color-light);
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.ets-footer-label {
  font-size: 0.8rem;
  color: var(--color-muted);
  flex-shrink: 0;
}

.ets-custom-input {
  height: 26px;
  padding: 0 8px;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-sm);
  font-family: var(--font-monospace);
  font-size: 0.82rem;
  flex: 1;
  outline: none;
  background: var(--color-white);
  color: var(--color-body);
}

.ets-custom-input:focus {
  border-color: var(--color-primary);
  border-style: solid;
}

.ets-add-btn {
  height: 26px;
  padding: 0 10px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: var(--color-white);
  font-size: 0.7rem;
  font-family: var(--font-base);
  font-weight: 500;
  cursor: pointer;
  flex-shrink: 0;
  transition: background var(--transition-fast);
}

.ets-add-btn:hover:not(:disabled) {
  background: var(--color-primary-hover);
}

.ets-add-btn:disabled {
  opacity: 0.4;
  cursor: default;
}

/* Done button */
.ets-dropdown-done {
  padding: 8px 12px;
  border-top: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-shrink: 0;
}

.ets-done-count {
  font-size: 0.75rem;
  color: var(--color-muted);
}

.ets-done-btn {
  height: 30px;
  padding: 0 14px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: var(--color-white);
  font-size: 0.78rem;
  font-family: var(--font-base);
  font-weight: 600;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  transition: background var(--transition-fast);
}

.ets-done-btn:hover {
  background: var(--color-primary-hover);
}
</style>
