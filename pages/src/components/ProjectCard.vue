<template>
  <div class="project-card" @click="moveToProject(project.id)">
    <!-- Status Header -->
    <div class="status-header" :class="getHeaderClass">
      <div class="project-header-name">
        <span>{{ project.name }}</span>
      </div>
      <div v-if="showCriticalWarning" class="critical-warning" :title="getCriticalWarningTooltip">
        <i :class="getCriticalWarningIcon"></i>
      </div>
    </div>

    <!-- Card Body -->
    <div class="card-body">

      <!-- Status -->
      <div v-if="project.status" class="status-row">
        <div class="metric status-metric" :class="`status-${project.status.toLowerCase()}`">
          <i class="bi bi-activity"></i>
          <span class="metric-label">Status:</span>
          <span class="metric-value">{{ formatStatus(project.status) }}</span>
        </div>
      </div>

      <!-- Alert Badge -->
      <div v-if="project.alertCount > 0" class="alert-section">
        <Badge
            :value="`${project.alertCount} Alert${project.alertCount > 1 ? 's' : ''}`"
            variant="red"
            size="s"
            icon="bi bi-exclamation-triangle-fill"
            :uppercase="false"
        />
      </div>

      <!-- Date Row -->
      <div class="date-row">
        <div class="metric date">
          <i class="bi bi-calendar3"></i>
          <span>{{ formatDate(project.createdAt) }}</span>
        </div>
      </div>

      <!-- Counts Row -->
      <div class="counts-row">
        <div class="metric">
          <i class="bi bi-people-fill"></i>
          <span class="metric-label">Profiles:</span>
          <span class="metric-value">{{ project.profileCount }}</span>
        </div>
        <div class="metric">
          <i class="bi bi-camera-video-fill"></i>
          <span class="metric-label">Recordings:</span>
          <span class="metric-value">{{ project.recordingCount || 0 }}</span>
        </div>
        <div class="metric">
          <i class="bi bi-layers-fill"></i>
          <span class="metric-label">Sessions:</span>
          <span class="metric-value">{{ project.sessionCount || 0 }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, defineProps} from 'vue';
import Project from "@/services/model/Project.ts";
import RecordingStatus from "@/services/model/data/RecordingStatus.ts";
import Badge from '@/components/Badge.vue';
import WorkspaceType from "@/services/workspace/model/WorkspaceType.ts";
import {useNavigation} from '@/composables/useNavigation';
import ProjectsClient from "@/services/ProjectsClient.ts";
import ToastService from "@/services/ToastService.ts";

const props = defineProps<{
  project: Project;
  workspaceId: string;
  isOrphaned?: boolean;
}>();

const {navigateToProject} = useNavigation();

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
  return 'header-live';
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
    year: 'numeric'
  }) + ' ' + date.toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
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
</script>

<style scoped>
/* Modern Project Card */
.project-card {
  background: #ffffff;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1),
  0 1px 2px rgba(0, 0, 0, 0.06);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  height: 100%;
  display: flex;
  flex-direction: column;
  cursor: pointer;
}

.project-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1),
  0 2px 4px rgba(0, 0, 0, 0.06);
  border-color: #d1d5db;
}

/* Status Header */
.status-header {
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 14px;
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.02em;
  color: white;
}

.project-header-name {
  display: flex;
  align-items: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.project-header-name span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  background: linear-gradient(135deg, #38b2ac, #319795);
}

.header-remote-physical {
  background: linear-gradient(135deg, #38b2ac, #319795);
}

.header-orphaned {
  background: linear-gradient(135deg, #f97316, #ea580c);
}

.header-sandbox {
  background: linear-gradient(135deg, #f59e0b, #d97706);
}

.header-live {
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

/* Status Row */
.status-row {
  display: flex;
}

/* Status Metric with Color Indicators */
.status-metric {
  padding: 2px 8px;
  border-radius: 6px;
  background: #f9fafb;
}

.status-metric.status-active .metric-value {
  color: #d97706;
  font-weight: 700;
}

.status-metric.status-active i {
  color: #f59e0b;
}

.status-metric.status-finished .metric-value {
  color: #059669;
  font-weight: 700;
}

.status-metric.status-finished i {
  color: #10b981;
}

.status-metric.status-unknown .metric-value {
  color: #6b7280;
  font-weight: 700;
}

.status-metric.status-unknown i {
  color: #9ca3af;
}

/* Alert Section */
.alert-section {
  margin-top: 8px;
  margin-bottom: 8px;
}

/* Date Row */
.date-row {
  display: flex;
  align-items: center;
}

/* Counts Row */
.counts-row {
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

.metric.date {
  font-weight: 500;
  color: #374151;
}

.metric-label {
  font-weight: 500;
  color: #6b7280;
  margin-left: 2px;
}

.metric-value {
  font-weight: 600;
  color: #374151;
}

</style>
