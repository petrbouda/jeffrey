<template>
  <div class="table-toolbar">
    <div class="toolbar-left">
      <slot />
    </div>
    <div class="toolbar-right">
      <slot name="filters" />
      <div v-if="showSearch" class="toolbar-search">
        <i class="bi bi-search"></i>
        <input
          type="text"
          class="form-control form-control-sm"
          :placeholder="searchPlaceholder"
          :value="modelValue"
          @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
        />
        <button
          v-if="modelValue"
          class="btn-clear"
          @click="$emit('update:modelValue', '')"
        >
          <i class="bi bi-x-lg"></i>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  modelValue?: string;
  searchPlaceholder?: string;
  showSearch?: boolean;
}>(), {
  modelValue: '',
  searchPlaceholder: 'Filter...',
  showSearch: true
});

defineEmits<{
  'update:modelValue': [value: string];
}>();
</script>

<style scoped>
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 1rem;
  background-color: var(--color-light);
  border-bottom: 1px solid var(--color-border);
  gap: 0.75rem;
  min-height: 42px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.toolbar-search {
  position: relative;
}

.toolbar-search i.bi-search {
  position: absolute;
  left: 0.6rem;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-text-light);
  font-size: 0.75rem;
  pointer-events: none;
}

.toolbar-search .form-control {
  padding-left: 1.8rem;
  padding-right: 1.8rem;
  font-size: 0.8rem;
  min-width: 160px;
}

.btn-clear {
  position: absolute;
  right: 0.4rem;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  color: var(--color-text-light);
  font-size: 0.6rem;
  cursor: pointer;
  padding: 2px;
  line-height: 1;
}

.btn-clear:hover {
  color: var(--color-text);
}
</style>
