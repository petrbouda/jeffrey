<template>
  <div class="mb-4 row">
    <label v-if="label" :for="inputId" class="col-sm-3 col-form-label fw-medium">{{ label }}</label>
    <div class="col-sm-9">
      <div v-if="helpText" class="mb-2">
        <div class="form-check">
          <input 
            v-if="showCheckbox"
            class="form-check-input" 
            type="checkbox" 
            :id="checkboxId"
            :checked="checkboxValue"
            @change="handleCheckboxChange"
          >
          <label v-if="showCheckbox" class="form-check-label small" :for="checkboxId">
            <span v-html="helpText"></span>
          </label>
          <div v-else class="text-muted small">
            <span v-html="helpText"></span>
          </div>
        </div>
      </div>
      <div class="input-group">
        <span v-if="icon" class="input-group-text border-end-0">
          <i :class="['bi', icon]"></i>
        </span>
        <input 
          :id="inputId"
          :type="type"
          :class="inputClasses"
          :value="modelValue"
          :disabled="disabled"
          :placeholder="placeholder"
          :autocomplete="autocomplete"
          @input="handleInput"
          @keyup.enter="$emit('enter')"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

interface Props {
  modelValue: string;
  label?: string;
  icon?: string;
  placeholder?: string;
  type?: 'text' | 'password' | 'email';
  disabled?: boolean;
  autocomplete?: string;
  helpText?: string;
  showCheckbox?: boolean;
  checkboxValue?: boolean;
}

interface Emits {
  (e: 'update:modelValue', value: string): void;
  (e: 'update:checkboxValue', value: boolean): void;
  (e: 'enter'): void;
  (e: 'input', value: string): void;
}

const props = withDefaults(defineProps<Props>(), {
  type: 'text',
  disabled: false,
  autocomplete: 'off',
  showCheckbox: false,
  checkboxValue: false
});

const emit = defineEmits<Emits>();

// Generate unique IDs
const inputId = computed(() => `input-${Math.random().toString(36).substring(2, 9)}`);
const checkboxId = computed(() => `checkbox-${Math.random().toString(36).substring(2, 9)}`);

// Computed classes for input
const inputClasses = computed(() => [
  'form-control',
  {
    'border-start-0': props.icon,
    'border-start': !props.icon
  }
]);

// Handle input change
const handleInput = (event: Event) => {
  const target = event.target as HTMLInputElement;
  emit('update:modelValue', target.value);
  emit('input', target.value);
};

// Handle checkbox change
const handleCheckboxChange = (event: Event) => {
  const target = event.target as HTMLInputElement;
  emit('update:checkboxValue', target.checked);
};
</script>

<style scoped>
.fw-medium {
  font-weight: 500;
}

/* Form input styling */
.input-group-text {
  background-color: #fff;
  color: #6c757d;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  border: 1px solid #ced4da;
}

.form-control {
  border: 1px solid #ced4da;
  height: 38px;
}

.form-control:focus {
  box-shadow: none;
  border-color: #ced4da;
}

.input-group {
  flex-wrap: nowrap;
}
</style>