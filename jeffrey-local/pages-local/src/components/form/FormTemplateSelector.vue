<template>
  <div v-if="templates.length > 0" class="mb-4 row">
    <label class="col-sm-3 col-form-label fw-medium">{{ label }}</label>
    <div class="col-sm-9">
      <div class="d-flex flex-wrap gap-2">
        <div 
          v-for="template in templates" 
          :key="template.id"
          class="template-option p-2 rounded-3 border"
          :class="{'selected': modelValue === template.id}"
          @click="selectTemplate(template.id)"
        >
          <div class="d-flex align-items-center">
            <i :class="['bi', templateIcon, 'text-primary', 'me-2']"></i>
            <span>{{ template.name }}</span>
          </div>
        </div>
      </div>
      <div class="text-muted small mt-2">
        <i class="bi bi-info-circle me-1"></i>{{ helpText }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type ProjectTemplateInfo from '@/services/api/model/ProjectTemplateInfo';

interface Props {
  modelValue: string | null;
  templates: ProjectTemplateInfo[];
  label?: string;
  helpText?: string;
  templateIcon?: string;
}

interface Emits {
  (e: 'update:modelValue', value: string | null): void;
}

const props = withDefaults(defineProps<Props>(), {
  label: 'Project Template',
  helpText: 'Templates provide pre-configured settings for new projects',
  templateIcon: 'bi-file-earmark-code'
});

const emit = defineEmits<Emits>();

// Select template (toggle behavior)
const selectTemplate = (templateId: string) => {
  const newValue = props.modelValue === templateId ? null : templateId;
  emit('update:modelValue', newValue);
};
</script>

<style scoped>
.fw-medium {
  font-weight: 500;
}

.template-option {
  cursor: pointer;
  transition: all 0.2s ease;
  background-color: #f8f9fa;

  &:hover {
    background-color: #eef2ff;
    border-color: #d1d9ff !important;
  }

  &.selected {
    background-color: #eef2ff;
    border-color: #6f42c1 !important;
    box-shadow: 0 0 0 1px rgba(111, 66, 193, 0.15);
  }
}
</style>