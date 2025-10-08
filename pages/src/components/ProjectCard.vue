<template>
  <div class="project-card" :class="{ 'orphaned-project': isOrphaned }" @click="moveToProject(project.id)">
    <div class="project-header">
      <div class="d-flex justify-content-between align-items-center">
        <h5 class="project-title">{{ project.name }}</h5>

        <div class="status-indicators">
          <!-- Orphaned status indicator -->
          <div v-if="isOrphaned" class="orphaned-status-indicator" title="Project is orphaned - original remote project was removed">
            <i class="bi bi-exclamation-triangle-fill"></i>
          </div>

          <!-- Virtual status indicator (only for remote workspaces) -->
          <div v-if="showVirtualStatus" class="virtual-status-dot">
            <div
              class="status-dot"
              :class="{ 'virtual-dot': project.isVirtual, 'physical-dot': !project.isVirtual }"
              :title="getVirtualStatusConfig.tooltip"
            ></div>
          </div>
        </div>
      </div>
      <div class="project-created-line">
        <span class="project-created">{{ project.createdAt }}</span>
      </div>
    </div>

    <div class="project-badges">
      <Badge :value="`${project.profileCount} profiles`" variant="orange" size="xs" />
      <Badge :value="`${project.recordingCount || 0} recordings`" variant="cyan" size="xs" />
      <Badge v-if="project.sessionCount" :value="`${project.sessionCount} session${project.sessionCount > 1 ? 's' : ''}`" variant="info" size="xs" />
      <Badge v-if="project.eventSource && project.eventSource != RecordingEventSource.UNKNOWN" :value="project.eventSource" :variant="project.eventSource === RecordingEventSource.JDK ? 'info' : 'purple'" size="xs" :title="'Type of the latest profile in the project'" />
      <Badge v-if="project.alertCount && project.alertCount > 0" :value="`${project.alertCount} alert${project.alertCount > 1 ? 's' : ''}`" variant="danger" size="xs" title="Number of alerts" />
      <Badge v-if="project.status" :value="formatStatus(project.status)" :variant="getStatusVariant(project.status)" size="xs" />
    </div>

    <div class="project-details">
      
    </div>

  </div>
</template>

<script setup lang="ts">
import {defineProps, computed} from 'vue';
import Project from "@/services/model/Project.ts";
import RecordingStatus from "@/services/model/data/RecordingStatus.ts";
import Badge from '@/components/Badge.vue';
import RecordingEventSource from "@/services/model/data/RecordingEventSource.ts";
import WorkspaceType from "@/services/workspace/model/WorkspaceType.ts";
import { useNavigation } from '@/composables/useNavigation';
import type {Variant} from "@/types/ui.ts";
import ProjectsClient from "@/services/ProjectsClient.ts";
import ToastService from "@/services/ToastService.ts";

const props = defineProps<{
  project: Project;
  workspaceId: string;
  isOrphaned?: boolean;
}>();

const { navigateToProject } = useNavigation();

const moveToProject = async (projectId: string) => {
  // Check if this is a REMOTE workspace with a virtual project
  if (props.project.workspaceType === WorkspaceType.REMOTE && props.project.isVirtual) {
    try {
      // Create project in the same remote workspace with originProjectId set to remote project's originId
      const createdProject = await ProjectsClient.create(
        props.project.name,
        props.workspaceId,  // Use the current REMOTE workspace ID
        undefined, // templateId
        props.project.originId   // originProjectId - use the originId from the remote project
      );

      // Navigate to the newly created project using the returned project's workspace ID
      navigateToProject(createdProject.id, createdProject.workspaceId);

    } catch (error) {
      ToastService.error('Failed to create project', 'Could not create project in remote workspace.');
    }
  } else {
    // Direct navigation for non-virtual projects or non-remote workspaces
    navigateToProject(projectId, props.workspaceId);
  }
};

// Virtual status visualization logic
const showVirtualStatus = computed(() => {
  return props.project.workspaceType === WorkspaceType.REMOTE;
});

const getVirtualStatusConfig = computed(() => {
  if (props.project.isVirtual) {
    return {
      label: 'Virtual',
      variant: 'red',
      icon: 'bi bi-cloud',
      tooltip: 'Virtual project - available only in the remote workspace, not created locally'
    };
  } else {
    return {
      label: 'Local',
      variant: 'green',
      icon: 'bi bi-hdd',
      tooltip: 'Local project - already created locally in Jeffrey'
    };
  }
});

const formatStatus = (status: RecordingStatus): string => {
  switch (status) {
    case RecordingStatus.ACTIVE:
      return 'Active';
    case RecordingStatus.FINISHED:
      return 'Finished';
    case RecordingStatus.UNKNOWN:
    default:
      return 'Unknown';
  }
};

const getStatusVariant = (status: RecordingStatus): Variant => {
  switch (status) {
    case RecordingStatus.ACTIVE:
      return 'warning';
    case RecordingStatus.FINISHED:
      return 'green';
    case RecordingStatus.UNKNOWN:
    default:
      return 'purple';
  }
};
</script>

<style scoped>
.project-card {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid rgba(94, 100, 255, 0.08);
  box-shadow: 
    0 4px 20px rgba(0, 0, 0, 0.04),
    0 1px 3px rgba(0, 0, 0, 0.02);
  backdrop-filter: blur(10px);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  padding: 1.5rem 1.5rem 1rem 1.5rem;
  height: 100%;
  display: flex;
  flex-direction: column;
  cursor: pointer;
}

.project-card:hover {
  transform: translateY(-3px);
  box-shadow: 
    0 8px 24px rgba(0, 0, 0, 0.08),
    0 4px 12px rgba(94, 100, 255, 0.15);
  border-color: rgba(94, 100, 255, 0.2);
}

.project-header {
  margin-bottom: 1rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid #f0f2f8;
}

.project-title {
  font-size: 1rem;
  font-weight: 600;
  color: #5e64ff;
  margin: 0;
}

.project-created-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 0.5rem;
}

.project-created {
  font-size: 0.7rem;
  color: #8b95a7;
  font-weight: 400;
  opacity: 0.8;
  letter-spacing: 0.2px;
}

.virtual-status-dot {
  display: flex;
  align-items: center;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  transition: all 0.2s ease;
  cursor: help;
}

.virtual-dot {
  background-color: #dc2626;
  box-shadow: 0 0 0 2px rgba(220, 38, 38, 0.2);
}

.physical-dot {
  background-color: #059669;
  box-shadow: 0 0 0 2px rgba(5, 150, 105, 0.2);
}

.status-dot:hover {
  transform: scale(1.2);
  box-shadow: 0 0 0 3px rgba(220, 38, 38, 0.3);
}

.physical-dot:hover {
  box-shadow: 0 0 0 3px rgba(5, 150, 105, 0.3);
}

.project-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
  margin-bottom: 0.5rem;
}

.project-details {
  display: flex;
  flex-direction: column;
  flex-grow: 1;
}

.detail-item {
  display: flex;
  align-items: center;
  padding: 0.5rem;
  border-bottom: 1px solid #f0f2f8;
}

.detail-item:last-child {
  border-bottom: none;
}

.detail-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background-color: rgba(94, 100, 255, 0.1);
  color: #5e64ff;
  margin-right: 0.75rem;
  font-size: 0.8rem;
}

.detail-content {
  flex: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.detail-label {
  font-size: 0.75rem;
  font-weight: 500;
  color: #5e6e82;
}

.detail-value {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.75rem;
  color: #5e6e82;
}

/* Orphaned project styling */
.orphaned-project {
  border: 2px dashed #f59e0b !important;
  background: linear-gradient(135deg, #fefbf3, #fef7ed) !important;
}

.orphaned-project:hover {
  border-color: #d97706 !important;
  box-shadow:
    0 8px 24px rgba(245, 158, 11, 0.15),
    0 4px 12px rgba(245, 158, 11, 0.25) !important;
}

/* Status indicators container */
.status-indicators {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

/* Orphaned status indicator */
.orphaned-status-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  background: rgba(245, 158, 11, 0.1);
  border: 1px solid rgba(245, 158, 11, 0.3);
  border-radius: 6px;
  color: #d97706;
  font-size: 10px;
  cursor: help;
  transition: all 0.2s ease;
}

.orphaned-status-indicator:hover {
  background: rgba(245, 158, 11, 0.2);
  border-color: rgba(245, 158, 11, 0.5);
  transform: scale(1.1);
}


</style>
