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
        <span v-if="project.isBlocked" class="status-badge status-blocked">
          Blocked
        </span>
        <span v-else-if="project.status" class="status-badge" :class="getStatusClass">
          {{ formatStatus(project.status) }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, defineProps} from 'vue';
import Project from "@/services/api/model/Project.ts";
import RecordingStatus from "@/services/api/model/RecordingStatus.ts";
import {useNavigation} from '@/composables/useNavigation';

const props = defineProps<{
  project: Project;
  workspaceId: string;
}>();

const {navigateToProject} = useNavigation();

const moveToProject = (projectId: string) => {
  navigateToProject(projectId, props.workspaceId);
};

// Border color class
const getBorderClass = computed(() => {
  if (props.project.isBlocked) return 'border-blocked';
  return 'border-default';
});

// Show warning icon for blocked projects
const showCriticalWarning = computed(() => {
  return props.project.isBlocked;
});

const getCriticalWarningIcon = computed(() => {
  if (props.project.isBlocked) return 'bi bi-slash-circle-fill';
  return '';
});

const getCriticalWarningTooltip = computed(() => {
  if (props.project.isBlocked) return 'Project is blocked - no events are being processed';
  return '';
});

// Status badge class
const getStatusClass = computed(() => {
  if (!props.project.status) return '';
  return `status-${props.project.status.toLowerCase()}`;
});

const formatDate = (timestamp: number): string => {
  const date = new Date(timestamp);
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
  box-shadow: 0 6px 16px rgba(94, 100, 255, 0.12);
  border-color: rgba(94, 100, 255, 0.25);
  border-left-color: #4c52ff;
}

/* Border Color Classes */
.border-default {
  border-left-color: #5e64ff;
}

.border-blocked {
  border-left-color: #9ca3af;
  opacity: 0.65;
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

.status-badge.status-blocked {
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
