<template>
  <div class="d-flex gap-3">
    <div 
      v-for="option in options" 
      :key="option.value"
      class="option-card" 
      :class="{'selected': modelValue === option.value}"
      @click="selectOption(option.value)"
    >
      <div class="option-content">
        <div class="option-header">
          <i v-if="option.icon" :class="['bi', option.icon, option.iconColor || 'text-primary', 'me-2']"></i>
          <span class="option-title">{{ option.label }}</span>
        </div>
        <div v-if="option.description" class="option-description">
          {{ option.description }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface OptionItem {
  value: string;
  label: string;
  description?: string;
  icon?: string;
  iconColor?: string;
}

interface Props {
  options: OptionItem[];
  modelValue: string;
}

interface Emits {
  (e: 'update:modelValue', value: string): void;
}

defineProps<Props>();
const emit = defineEmits<Emits>();

const selectOption = (value: string) => {
  emit('update:modelValue', value);
};
</script>

<style scoped>
.option-card {
  flex: 1;
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 12px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 
    0 2px 8px rgba(0, 0, 0, 0.04),
    0 1px 2px rgba(0, 0, 0, 0.02);

  &:hover:not(.selected) {
    transform: translateY(-2px);
    box-shadow: 
      0 6px 16px rgba(0, 0, 0, 0.06),
      0 2px 8px rgba(94, 100, 255, 0.1);
    border-color: rgba(94, 100, 255, 0.2);
  }

  &.selected {
    background: linear-gradient(135deg, #eef2ff, #f8faff);
    border-color: #5e64ff;
    transform: translateY(-1px);
    box-shadow: 
      0 6px 20px rgba(94, 100, 255, 0.15),
      0 2px 8px rgba(94, 100, 255, 0.1);
  }
}

.option-content {
  text-align: center;
}

.option-header {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
}

.option-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: #374151;
}

.option-description {
  font-size: 0.75rem;
  color: #6b7280;
  line-height: 1.4;
}

.option-card.selected .option-title {
  color: #5e64ff;
}

.option-card.selected .option-description {
  color: #4338ca;
}
</style>