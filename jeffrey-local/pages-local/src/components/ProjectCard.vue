<template>
  <div class="project-card-compact" :class="getBorderClass" @click="handleClick">
    <!-- Project Name Row -->
    <div class="name-row">
      <span class="project-name">{{ Project.displayName(project) }}</span>
      <button
        v-if="project.isDeleted"
        class="restore-btn"
        title="Restore project"
        @click.stop="$emit('restore', project.id)"
      >
        <i class="bi bi-arrow-counterclockwise"></i>
      </button>
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
        <Badge v-if="project.isDeleted" value="Deleted" variant="grey" size="xs" />
        <Badge
          v-else-if="project.status"
          :value="formatStatus(project.status)"
          :variant="getStatusVariant"
          size="xs"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, defineProps, defineEmits } from 'vue';
import Project from '@/services/api/model/Project.ts';
import RecordingStatus from '@/services/api/model/RecordingStatus.ts';
import { useNavigation } from '@/composables/useNavigation';
import Badge from '@/components/Badge.vue';
import type { Variant } from '@/types/ui';

const props = defineProps<{
  project: Project;
  serverId: string;
  workspaceId: string;
}>();

defineEmits<{
  restore: [projectId: string];
}>();

const { navigateToProject } = useNavigation();

const handleClick = () => {
  if (!props.project.isDeleted) {
    navigateToProject(props.serverId, props.project.id, props.workspaceId);
  }
};

// Border color class
const getBorderClass = computed(() => {
  if (props.project.isDeleted) return 'border-deleted';
  return 'border-default';
});

// Status badge variant
const getStatusVariant = computed((): Variant => {
  if (!props.project.status) return 'status-unknown';
  return `status-${props.project.status.toLowerCase()}` as Variant;
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
  background: var(--color-white);
  border-radius: 8px;
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-primary);
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
  border-left-color: var(--color-primary-hover);
}

/* Border Color Classes */
.border-default {
  border-left-color: var(--color-primary);
}

.border-deleted {
  border-left-color: var(--color-text-light);
  opacity: 0.65;
  cursor: default;
}

.border-deleted:hover {
  transform: none;
  box-shadow: none;
  border-color: var(--color-border);
  border-left-color: var(--color-text-light);
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
  color: var(--color-dark);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

/* Restore Button */
.restore-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: var(--color-white);
  color: var(--color-text-light);
  cursor: pointer;
  font-size: 0.8rem;
  flex-shrink: 0;
  transition: all 0.2s ease;
}

.restore-btn:hover {
  background: var(--color-success-bg);
  border-color: var(--color-emerald);
  color: var(--color-emerald);
}

/* Metrics Row */
.metrics-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.75rem;
  color: var(--color-text-muted);
  flex-wrap: wrap;
}

.metrics-row .dot {
  color: var(--color-muted-separator);
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
  color: var(--color-text-muted);
}

.footer-row .date i {
  font-size: 0.7rem;
}

.footer-row .badges {
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
