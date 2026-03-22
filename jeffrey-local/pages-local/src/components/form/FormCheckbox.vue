<template>
  <div class="mb-2">
    <div class="form-check">
      <input 
        class="form-check-input" 
        type="checkbox" 
        :id="checkboxId"
        :checked="modelValue"
        @change="handleChange"
      >
      <label class="form-check-label small" :for="checkboxId">
        <span v-html="label"></span>
      </label>
    </div>
    <div v-if="helpText" class="text-muted small mt-1 ms-3">
      <span v-html="helpText"></span>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  modelValue: boolean;
  label: string;
  helpText?: string;
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

// Generate unique ID (stable - only generated once per component instance)
const checkboxId = `checkbox-${Math.random().toString(36).substring(2, 9)}`;

// Handle checkbox change
const handleChange = (event: Event) => {
  const target = event.target as HTMLInputElement;
  emit('update:modelValue', target.checked);
};
</script>

<style scoped>
/* Checkbox styling inherited from Bootstrap */
</style>