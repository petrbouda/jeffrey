<template>
  <div
    class="profiler-selection-card"
    :class="{ 'selected': selected }"
    @click="$emit('select')"
  >
    <span v-if="badge" class="selection-badge" :class="`badge-${badge.toLowerCase()}`">
      {{ badge }}
    </span>
    <button
      v-if="deletable && badge === 'CUSTOM'"
      class="delete-button"
      @click.stop="$emit('delete')"
      title="Delete custom settings"
    >
      <i class="bi bi-trash"></i>
    </button>
    <div class="selection-header">
      <input
        :type="selectionType"
        :checked="selected"
        @click.stop
        @change="selectionType === 'checkbox' ? $emit('select') : undefined"
      />
      <div class="selection-info">
        <i :class="`bi ${icon}`"></i>
        <h6 class="selection-name">{{ name }}</h6>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  name: string;
  icon: string;
  selected: boolean;
  selectionType?: 'checkbox' | 'radio';
  badge?: 'CUSTOM' | 'GLOBAL' | null;
  deletable?: boolean;
}

withDefaults(defineProps<Props>(), {
  selectionType: 'checkbox',
  badge: null,
  deletable: false
});

defineEmits<{
  select: [];
  delete: [];
}>();
</script>

<style scoped>
.profiler-selection-card {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border: 2px solid rgba(94, 100, 255, 0.15);
  border-radius: 10px;
  padding: 14px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  position: relative;
}

.profiler-selection-card:hover {
  border-color: rgba(94, 100, 255, 0.3);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(94, 100, 255, 0.1);
}

.profiler-selection-card.selected {
  background: linear-gradient(135deg, #f3f4ff, #e8eaf6);
  border-color: #5e64ff;
  box-shadow: 0 4px 16px rgba(94, 100, 255, 0.2);
}

.selection-header {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.selection-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
  padding-right: 60px;
}

.selection-info i {
  font-size: 0.95rem;
  color: #5e64ff;
  flex-shrink: 0;
}

.selection-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: #374151;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selection-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  font-size: 0.65rem;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 12px;
  text-transform: uppercase;
  letter-spacing: 0.02em;
  z-index: 1;
}

.badge-custom {
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  color: white;
}

.badge-global {
  background: linear-gradient(135deg, #10b981, #059669);
  color: white;
}

.delete-button {
  position: absolute;
  top: 8px;
  left: 8px;
  background: linear-gradient(135deg, #ef4444, #dc2626);
  border: none;
  border-radius: 6px;
  color: white;
  padding: 4px 8px;
  font-size: 0.75rem;
  cursor: pointer;
  opacity: 0;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
}

.profiler-selection-card:hover .delete-button {
  opacity: 1;
}

.delete-button:hover {
  background: linear-gradient(135deg, #dc2626, #b91c1c);
  transform: scale(1.05);
}

.delete-button:active {
  transform: scale(0.95);
}

.delete-button i {
  font-size: 0.85rem;
}
</style>
