<template>
  <div class="searchable-select" ref="containerRef">
    <div
      class="searchable-select-trigger"
      :class="{ open: isOpen, 'has-value': modelValue != null }"
      @click="toggleDropdown"
    >
      <i class="bi bi-search trigger-icon"></i>
      <span v-if="modelValue != null" class="trigger-value">
        <slot name="selected" :item="selectedItem">{{ modelValue }}</slot>
      </span>
      <span v-else class="trigger-placeholder">{{ placeholder }}</span>
      <i
        class="bi"
        :class="isOpen ? 'bi-chevron-up' : 'bi-chevron-down'"
        style="margin-left: auto; font-size: 0.7rem; color: var(--color-text-muted)"
      ></i>
    </div>

    <div
      v-if="isOpen"
      class="searchable-select-dropdown"
      ref="dropdownRef"
    >
      <div class="dropdown-search">
        <i class="bi bi-search"></i>
        <input
          ref="searchInputRef"
          v-model="searchQuery"
          :placeholder="searchPlaceholder"
          @keydown.escape="closeDropdown"
        />
      </div>
      <div v-if="filteredItems.length > 0" class="dropdown-hint">
        {{ filteredItems.length }} of {{ items.length }}
      </div>
      <div class="dropdown-list">
        <div
          v-for="item in filteredItems"
          :key="item.label"
          class="dropdown-item"
          :class="{ active: item.label === modelValue }"
          @click="selectItem(item)"
        >
          <div class="item-check">
            <i v-if="item.label === modelValue" class="bi bi-check"></i>
          </div>
          <div class="item-content">
            <slot name="item" :item="item" :highlight="highlightMatch">
              <div class="item-label" v-html="highlightMatch(item.label)"></div>
            </slot>
          </div>
        </div>
        <div v-if="filteredItems.length === 0" class="dropdown-empty">No matches found</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue';

interface SelectItem {
  label: string;
  [key: string]: any;
}

interface Props {
  modelValue: string | null;
  items: SelectItem[];
  placeholder?: string;
  searchPlaceholder?: string;
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: 'All',
  searchPlaceholder: 'Search...'
});

const emit = defineEmits<{
  'update:modelValue': [value: string | null];
  clear: [];
}>();

const containerRef = ref<HTMLElement | null>(null);
const dropdownRef = ref<HTMLElement | null>(null);
const searchInputRef = ref<HTMLInputElement | null>(null);
const isOpen = ref(false);
const searchQuery = ref('');

const selectedItem = computed(() => {
  if (props.modelValue == null) return null;
  return props.items.find(item => item.label === props.modelValue) || null;
});

const filteredItems = computed(() => {
  if (!searchQuery.value) return props.items;
  const query = searchQuery.value.toLowerCase();
  return props.items.filter(item => item.label.toLowerCase().includes(query));
});

const highlightMatch = (text: string): string => {
  if (!searchQuery.value) return text;
  const query = searchQuery.value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const regex = new RegExp(`(${query})`, 'gi');
  return text.replace(regex, '<strong class="highlight">$1</strong>');
};

const toggleDropdown = () => {
  if (isOpen.value) {
    closeDropdown();
  } else {
    openDropdown();
  }
};

const openDropdown = () => {
  isOpen.value = true;
  searchQuery.value = '';
  nextTick(() => {
    searchInputRef.value?.focus();
  });
};

const closeDropdown = () => {
  isOpen.value = false;
  searchQuery.value = '';
};

const selectItem = (item: SelectItem) => {
  if (item.label === props.modelValue) {
    emit('update:modelValue', null);
    emit('clear');
  } else {
    emit('update:modelValue', item.label);
  }
  closeDropdown();
};

const onClickOutside = (event: MouseEvent) => {
  if (!isOpen.value) return;
  const target = event.target as Node;
  if (containerRef.value?.contains(target)) {
    return;
  }
  closeDropdown();
};

watch(isOpen, open => {
  if (open) {
    document.addEventListener('click', onClickOutside, true);
  } else {
    document.removeEventListener('click', onClickOutside, true);
  }
});

onBeforeUnmount(() => {
  document.removeEventListener('click', onClickOutside, true);
});
</script>

<style scoped>
.searchable-select {
  position: relative;
}

.searchable-select-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  border-radius: var(--radius-base, 6px);
  border: 1px solid rgba(94, 100, 255, 0.2);
  background: var(--color-light);
  cursor: pointer;
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--color-dark);
  min-width: 200px;
  transition: all var(--transition-fast, 0.15s) ease;
}

.searchable-select-trigger:hover {
  border-color: rgba(94, 100, 255, 0.4);
  background: var(--card-bg, #fff);
}

.searchable-select-trigger.open {
  border-color: rgba(94, 100, 255, 0.4);
  background: var(--card-bg, #fff);
  box-shadow: 0 0 0 3px rgba(94, 100, 255, 0.08);
}

.searchable-select-trigger.has-value {
  border-color: var(--color-primary);
  background: rgba(94, 100, 255, 0.04);
}

.trigger-icon {
  color: var(--color-text-muted);
  font-size: 0.75rem;
}

.trigger-placeholder {
  color: var(--color-text-muted);
  font-weight: 400;
}

.trigger-value {
  color: var(--color-dark);
  font-weight: 600;
}

.searchable-select-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  min-width: 300px;
  background: var(--card-bg, #fff);
  border-radius: var(--radius-md, 8px);
  border: 1px solid var(--card-border-color, #eaedf1);
  box-shadow: var(--shadow-lg, 0 8px 24px rgba(0, 0, 0, 0.12));
  z-index: var(--z-index-dropdown, 1000);
  overflow: hidden;
}

.searchable-select-dropdown .dropdown-search {
  padding: 10px 12px;
  border-bottom: 1px solid var(--card-border-color, #eaedf1);
  display: flex;
  align-items: center;
  gap: 8px;
}

.searchable-select-dropdown .dropdown-search i {
  color: var(--color-text-muted, #748194);
  font-size: 0.8rem;
}

.searchable-select-dropdown .dropdown-search input {
  width: 100%;
  font-size: 0.8rem;
  padding: 0;
  border: none;
  outline: none;
  font-family: var(--font-family-base, 'Poppins', sans-serif);
  color: var(--color-dark, #0b1727);
  background: transparent;
}

.searchable-select-dropdown .dropdown-search input::placeholder {
  color: var(--color-text-muted, #748194);
}

.searchable-select-dropdown .dropdown-hint {
  font-size: 0.65rem;
  color: var(--color-text-muted, #748194);
  padding: 4px 12px;
  background: var(--color-light, #f9fafd);
  border-bottom: 1px solid var(--card-border-color, #eaedf1);
}

.searchable-select-dropdown .dropdown-list {
  max-height: 280px;
  overflow-y: auto;
}

.searchable-select-dropdown .dropdown-item {
  padding: 8px 12px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  border-bottom: 1px solid var(--color-border-lighter, #edf2f9);
  cursor: pointer;
  transition: background 0.1s;
}

.searchable-select-dropdown .dropdown-item:last-child {
  border-bottom: none;
}

.searchable-select-dropdown .dropdown-item:hover {
  background: var(--color-light, #f9fafd);
}

.searchable-select-dropdown .dropdown-item.active {
  background: rgba(94, 100, 255, 0.06);
}

.searchable-select-dropdown .item-check {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2px solid var(--card-border-color, #eaedf1);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 0.6rem;
  color: white;
  margin-top: 1px;
}

.searchable-select-dropdown .dropdown-item.active .item-check {
  background: var(--color-primary, #5e64ff);
  border-color: var(--color-primary, #5e64ff);
}

.searchable-select-dropdown .item-content {
  flex: 1;
  min-width: 0;
}

.searchable-select-dropdown .item-label {
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--color-dark, #0b1727);
}

:deep(.highlight) {
  color: var(--color-primary, #5e64ff);
  font-weight: 700;
}

.searchable-select-dropdown .dropdown-empty {
  padding: 16px 12px;
  text-align: center;
  color: var(--color-text-muted, #748194);
  font-size: 0.8rem;
}
</style>
