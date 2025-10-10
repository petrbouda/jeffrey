<template>
  <div class="project-card" @click="moveToProject(project.id)">
    <!-- Status Header -->
    <div class="status-header" :class="getHeaderClass">
      <div class="workspace-type">
        <i :class="getWorkspaceIcon"></i>
        <span>{{ getWorkspaceLabel }}</span>
      </div>
      <div v-if="showCriticalWarning" class="critical-warning" :title="getCriticalWarningTooltip">
        <i :class="getCriticalWarningIcon"></i>
      </div>
    </div>

    <!-- Card Body -->
    <div class="card-body">
      <!-- Project Name -->
      <h3 class="project-name">{{ project.name }}</h3>

      <!-- Metrics Row -->
      <div class="metrics-row">
        <div class="metric">
          <i class="bi bi-calendar3"></i>
          <span>{{ formatDate(project.createdAt) }}</span>
        </div>
        <div class="metric">
          <i class="bi bi-people-fill"></i>
          <span>{{ project.profileCount }}</span>
        </div>
        <div class="metric">
          <i class="bi bi-camera-video-fill"></i>
          <span>{{ project.recordingCount || 0 }}</span>
        </div>
        <div v-if="project.alertCount > 0" class="metric alert">
          <i class="bi bi-exclamation-triangle-fill"></i>
          <span>{{ project.alertCount }}</span>
        </div>
      </div>

      <!-- Additional Info -->
      <div class="additional-info">
        <div v-if="project.sessionCount" class="info-item">
          <i class="bi bi-layers-fill"></i>
          <span>{{ project.sessionCount }} session{{ project.sessionCount > 1 ? 's' : '' }}</span>
        </div>
        <div v-if="project.status" class="info-item">
          <i class="bi bi-activity"></i>
          <span>{{ formatStatus(project.status) }}</span>
        </div>
      </div>
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

// Header styling and content
const getHeaderClass = computed(() => {
  if (props.isOrphaned) return 'header-orphaned';
  if (props.project.workspaceType === WorkspaceType.REMOTE) {
    return props.project.isVirtual ? 'header-remote-virtual' : 'header-remote-physical';
  }
  if (props.project.workspaceType === WorkspaceType.SANDBOX) return 'header-sandbox';
  return 'header-local';
});

const getWorkspaceIcon = computed(() => {
  if (props.project.workspaceType === WorkspaceType.REMOTE) return 'bi bi-cloud-fill';
  if (props.project.workspaceType === WorkspaceType.SANDBOX) return 'bi bi-house-fill';
  return 'bi bi-folder-fill';
});

const getWorkspaceLabel = computed(() => {
  if (props.isOrphaned) return 'ORPHANED PROJECT';
  if (props.project.workspaceType === WorkspaceType.REMOTE) {
    return props.project.isVirtual ? 'REMOTE VIRTUAL' : 'REMOTE PROJECT';
  }
  if (props.project.workspaceType === WorkspaceType.SANDBOX) return 'SANDBOX PROJECT';
  return 'LOCAL PROJECT';
});

const showCriticalWarning = computed(() => {
  return props.isOrphaned || (props.project.workspaceType === WorkspaceType.REMOTE && props.project.isVirtual);
});

const getCriticalWarningIcon = computed(() => {
  if (props.isOrphaned) return 'bi bi-exclamation-triangle-fill';
  if (props.project.workspaceType === WorkspaceType.REMOTE && props.project.isVirtual) return 'bi bi-cloud-arrow-down-fill';
  return '';
});

const getCriticalWarningTooltip = computed(() => {
  if (props.isOrphaned) return 'Project is orphaned - original remote project was removed';
  if (props.project.workspaceType === WorkspaceType.REMOTE && props.project.isVirtual)
    return 'Virtual project - click to create local copy';
  return '';
});

const formatDate = (dateString: string): string => {
  const date = new Date(dateString);
  return date.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: date.getFullYear() !== new Date().getFullYear() ? 'numeric' : undefined
  });
};

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
/* Modern Project Card */
.project-card {
  background: #ffffff;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.1),
    0 1px 2px rgba(0, 0, 0, 0.06);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  height: 100%;
  display: flex;
  flex-direction: column;
  cursor: pointer;
}

.project-card:hover {
  transform: translateY(-2px);
  box-shadow:
    0 4px 6px rgba(0, 0, 0, 0.1),
    0 2px 4px rgba(0, 0, 0, 0.06);
  border-color: #d1d5db;
}

/* Status Header */
.status-header {
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: white;
}

.workspace-type {
  display: flex;
  align-items: center;
  gap: 4px;
}

.workspace-type i {
  font-size: 10px;
}

.critical-warning {
  display: flex;
  align-items: center;
  cursor: help;
}

.critical-warning i {
  font-size: 12px;
}

/* Header Color Classes */
.header-remote-virtual {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
}

.header-remote-physical {
  background: linear-gradient(135deg, #10b981, #059669);
}

.header-orphaned {
  background: linear-gradient(135deg, #f97316, #ea580c);
}

.header-sandbox {
  background: linear-gradient(135deg, #f59e0b, #d97706);
}

.header-local {
  background: linear-gradient(135deg, #4f46e5, #3730a3);
}

/* Card Body */
.card-body {
  padding: 16px;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.project-name {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
  margin: 0;
  line-height: 1.2;
}

/* Metrics Row */
.metrics-row {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.metric {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #6b7280;
}

.metric i {
  font-size: 12px;
  color: #9ca3af;
}

.metric.alert {
  color: #dc2626;
}

.metric.alert i {
  color: #dc2626;
}

/* Additional Info */
.additional-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: auto;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #9ca3af;
  padding: 2px 6px;
  background: #f9fafb;
  border-radius: 4px;
}

.info-item i {
  font-size: 10px;
}
</style>
