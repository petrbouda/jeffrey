<template>
  <div class="project-card-compact" :class="getBorderClass" @click="moveToProject(project.id)">
    <!-- Project Name Row -->
    <div class="name-row">
      <span class="project-name">{{ Project.displayName(project) }}</span>
      <span v-if="showCriticalWarning" class="warning-icon" :title="getCriticalWarningTooltip">
        <i :class="getCriticalWarningIcon"></i>
      </span>
    </div>

    <!-- Metrics Row -->
    <div class="metrics-row">
      <span>{{ project.profileCount }} profiles</span>
      <span class="dot">•</span>
      <span>{{ project.recordingCount || 0 }} recordings</span>
      <span class="dot">•</span>
      <span>{{ project.sessionCount || 0 }} sessions</span>
    </div>

    <!-- Footer Row: Date + Status/Alerts -->
    <div class="footer-row">
      <span class="date"><i class="bi bi-clock"></i>{{ formatDate(project.createdAt) }}</span>
      <div class="badges">
        <span v-if="project.status" class="status-badge" :class="getStatusClass">
          {{ formatStatus(project.status) }}
        </span>
        <span v-if="project.alertCount > 0" class="alert-badge">
          <i class="bi bi-exclamation-triangle-fill"></i>
          {{ project.alertCount }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, defineProps} from 'vue';
import Project from "@/services/api/model/Project.ts";
import RecordingStatus from "@/services/api/model/RecordingStatus.ts";
import WorkspaceType from "@/services/api/model/WorkspaceType.ts";
import {useNavigation} from '@/composables/useNavigation';
import ProjectsClient from "@/services/api/ProjectsClient.ts";
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
          Project.displayName(props.project),
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

// Border color class based on workspace type
const getBorderClass = computed(() => {
  if (props.isOrphaned) return 'border-orphaned';
  if (props.project.workspaceType === WorkspaceType.REMOTE) return 'border-remote';
  if (props.project.workspaceType === WorkspaceType.SANDBOX) return 'border-sandbox';
  return 'border-live';
});

// Show warning icon for orphaned or virtual projects
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

// Status badge class
const getStatusClass = computed(() => {
  if (!props.project.status) return '';
  return `status-${props.project.status.toLowerCase()}`;
});

const formatDate = (dateString: string): string => {
  const date = new Date(dateString);
  return date.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric'
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
/* Compact Project Card */
.project-card-compact {
  background: #ffffff;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  border-left: 3px solid #5e64ff;
  padding: 12px 14px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  gap: 6px;
  height: 100%;
}

.project-card-compact:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  border-color: #d1d5db;
}

/* Border Color Classes */
.border-live {
  border-left-color: #5e64ff;
}

.border-remote {
  border-left-color: #38b2ac;
}

.border-sandbox {
  border-left-color: #f59e0b;
}

.border-orphaned {
  border-left-color: #f97316;
}

/* Name Row */
.name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

/* Project Name */
.project-name {
  font-weight: 600;
  font-size: 0.95rem;
  color: #1f2937;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

/* Warning Icon */
.warning-icon {
  display: flex;
  align-items: center;
  cursor: help;
  color: #f97316;
  font-size: 0.8rem;
  flex-shrink: 0;
}

/* Metrics Row */
.metrics-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.75rem;
  color: #6b7280;
  flex-wrap: wrap;
}

.metrics-row .dot {
  color: #d1d5db;
  font-weight: bold;
}

/* Footer Row */
.footer-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 2px;
}

.footer-row .date {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.75rem;
  color: #6b7280;
}

.footer-row .date i {
  font-size: 0.7rem;
}

.footer-row .badges {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* Status Badge */
.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.65rem;
  font-weight: 600;
  text-transform: uppercase;
}

.status-badge.status-active {
  background: #fef3c7;
  color: #d97706;
}

.status-badge.status-finished {
  background: #d1fae5;
  color: #059669;
}

.status-badge.status-unknown {
  background: #f3f4f6;
  color: #6b7280;
}

/* Alert Badge */
.alert-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.65rem;
  font-weight: 600;
  background: #fef2f2;
  color: #dc2626;
}

.alert-badge i {
  font-size: 0.6rem;
}
</style>
