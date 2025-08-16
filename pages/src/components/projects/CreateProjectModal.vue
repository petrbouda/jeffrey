<template>
  <BaseModal
    modal-id="createProjectModal"
    title="Create a New Project"
    icon="bi-folder-plus"
    primary-button-text="Create Project"
    primary-button-icon="bi-save"
    :enable-enter-key="true"
    :loading="isLoading"
    @submit="createProject"
    @cancel="closeModal"
    @shown="handleShown"
    @hidden="handleHidden"
    ref="modalRef"
  >
    <template #description>
      <p class="text-muted mb-0">
        Enter a name for your new project and optionally select a project template to get started quickly.
      </p>
    </template>
    
    <template #body>
      <FormInput
        v-model="projectName"
        label="Project Name"
        icon="bi-folder"
        placeholder="Enter project name"
        ref="projectNameInputRef"
      />

      <FormTemplateSelector
        v-if="projectTemplates.length > 0"
        v-model="selectedTemplate"
        :templates="projectTemplates"
        label="Project Template"
        help-text="Templates provide pre-configured settings"
      />
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import BaseModal from '@/components/BaseModal.vue';
import FormInput from '@/components/form/FormInput.vue';
import FormTemplateSelector from '@/components/form/FormTemplateSelector.vue';
import ProjectsClient from '@/services/ProjectsClient';
import ProjectTemplateInfo from '@/services/project/model/ProjectTemplateInfo';
import TemplateTarget from '@/services/model/TemplateTarget';
import ToastService from '@/services/ToastService';
import { useModal } from '@/composables/useModal';

interface Props {
  selectedWorkspace: string;
}

interface Emits {
  (e: 'project-created'): void;
  (e: 'modal-closed'): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

// Modal reference and composable
const modalRef = ref<InstanceType<typeof BaseModal>>();
const projectNameInputRef = ref<InstanceType<typeof FormInput>>();
const { 
  isLoading, 
  showModal, 
  hideModal, 
  handleModalShown, 
  handleModalHidden,
  handleAsyncSubmit,
  setValidationErrors
} = useModal(modalRef);

// Form data
const projectName = ref('');
const projectTemplates = ref<ProjectTemplateInfo[]>([]);
const selectedTemplate = ref<string | null>(null);

// Load project templates
const loadTemplates = async () => {
  try {
    projectTemplates.value = await ProjectsClient.templates(TemplateTarget.PROJECT);
    // Set default template if available
    if (projectTemplates.value.length > 0) {
      selectedTemplate.value = projectTemplates.value[0].id;
    }
  } catch (error) {
    console.error('Failed to load project templates:', error);
  }
};

// Reset form
const resetForm = () => {
  projectName.value = '';
  selectedTemplate.value = projectTemplates.value.length > 0 ? projectTemplates.value[0].id : null;
};

// Close modal
const closeModal = () => {
  hideModal();
  emit('modal-closed');
};

// Create project
const createProject = async () => {
  await handleAsyncSubmit(
    async () => {
      // Validation
      if (!projectName.value.trim()) {
        throw new Error('Project name cannot be empty');
      }

      // Pass workspace ID (undefined for local workspace, actual ID for other workspaces)
      const workspaceId = props.selectedWorkspace === 'local' ? undefined : props.selectedWorkspace;
      
      // Pass the selected template ID if one is selected, and workspace ID
      await ProjectsClient.create(
        projectName.value.trim(), 
        selectedTemplate.value || undefined, 
        workspaceId
      );

      ToastService.success('New Project Created!', `Project "${projectName.value.trim()}" successfully created`);
      
      emit('project-created');
      resetForm();
    }
  );
};

// Handle modal shown
const handleShown = () => {
  resetForm();
  handleModalShown();
  
  // Focus the project name input after modal is shown
  setTimeout(() => {
    if (projectNameInputRef.value) {
      // Focus the underlying input element
      const inputElement = (projectNameInputRef.value.$el as HTMLElement)?.querySelector('input');
      inputElement?.focus();
    }
  }, 300);
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
  setValidationErrors
});
</script>

<style scoped>
/* Styles handled by BaseModal and form components */
</style>