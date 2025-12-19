<template>
  <BaseModal
    :modal-id="modalId"
    title="Create a Projects Synchronization Job"
    icon="bi-arrow-repeat"
    primary-button-text="Save Job"
    primary-button-icon="bi-save"
    :show-description-card="true"
    :enable-enter-key="true"
    :loading="isLoading"
    @submit="createJob"
    @cancel="closeModal"
    @shown="handleShown"
    @hidden="handleHidden"
    ref="modalRef"
  >
    <template #description>
      <p class="mb-2">
        Synchronizes <strong class="text-primary">workspace directories</strong> containing projects and its recordings with the projects created and maintained in Jeffrey.
      </p>
      <p class="mb-0 text-muted">
        When an application creates its folder within a workspace directory and starts producing recordings, this job automatically handles the project initialization in Jeffrey.
      </p>
    </template>
    
    <template #body>
      <FormTemplateSelector
        v-model="selectedTemplate"
        :templates="projectTemplates"
        label="Project Template"
        help-text="Templates provide pre-configured settings for new projects"
      />
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import BaseModal from '@/components/BaseModal.vue';
import FormTemplateSelector from '@/components/form/FormTemplateSelector.vue';
import ProjectsClient from '@/services/ProjectsClient';
import ProjectTemplateInfo from '@/services/project/model/ProjectTemplateInfo';
import TemplateTarget from '@/services/model/TemplateTarget';
import { useModal } from '@/composables/useModal';

interface Props {
  modalId: string;
}

interface Emits {
  (e: 'job-created', params: any): void;
  (e: 'modal-closed'): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

// Modal reference and composable
const modalRef = ref<InstanceType<typeof BaseModal>>();
const { 
  isLoading, 
  showModal, 
  hideModal, 
  handleModalShown, 
  handleModalHidden,
  handleAsyncSubmit 
} = useModal(modalRef);

// Form data
const projectTemplates = ref<ProjectTemplateInfo[]>([]);
const selectedTemplate = ref<string | null>(null);

// Load project templates
const loadTemplates = async () => {
  try {
    projectTemplates.value = await ProjectsClient.templates(TemplateTarget.GLOBAL_SCHEDULER);
  } catch (error) {
    console.error('Failed to load project templates:', error);
  }
};

// Reset form
const resetForm = () => {
  selectedTemplate.value = projectTemplates.value.length > 0 ? projectTemplates.value[0].id : null;
};

// Close modal
const closeModal = () => {
  hideModal();
  emit('modal-closed');
};

// Create job
const createJob = async () => {
  await handleAsyncSubmit(
    async () => {
      const params: any = {};

      // Add templateId to params if a template is selected
      if (selectedTemplate.value) {
        params.templateId = selectedTemplate.value;
      }

      emit('job-created', params);
    },
    () => {
      // On success, don't auto-hide modal - let parent handle it
      resetForm();
    }
  );
};

// Handle modal shown
const handleShown = () => {
  resetForm();
  handleModalShown();
};

// Handle modal hidden
const handleHidden = () => {
  handleModalHidden();
  emit('modal-closed');
};

// Initialize
onMounted(() => {
  loadTemplates();
});

// Expose methods for parent component
defineExpose({
  showModal,
  closeModal: hideModal,
  setValidationErrors: (errors: string[]) => {
    if (modalRef.value) {
      modalRef.value.setValidationErrors(errors);
    }
  }
});
</script>

<style scoped>
/* Styles now handled by BaseModal and FormTemplateSelector components */
</style>