<template>
  <div class="project-card" @click="moveToProject(project.id)">
    <div class="project-header">
      <div class="d-flex justify-content-between align-items-center">
        <h5 class="project-title">{{ project.name }}</h5>
        <Badge v-if="project.status" :value="formatStatus(project.status)" :variant="getStatusVariant(project.status)" size="s" />
      </div>
      <div class="project-created">
        {{ project.createdAt }}
      </div>
    </div>

    <div class="project-badges">
      <Badge :value="`${project.profileCount} profiles`" variant="orange" size="xs" />
      <Badge :value="`${project.recordingCount || 0} recordings`" variant="cyan" size="xs" />
      <Badge v-if="project.sourceType" :value="project.sourceType" :variant="project.sourceType === 'JDK' ? 'info' : 'purple'" size="xs" :title="'Type of the latest profile in the project'" />
      <Badge v-if="project.alertCount && project.alertCount > 0" :value="`${project.alertCount} alert${project.alertCount > 1 ? 's' : ''}`" variant="danger" size="xs" title="Number of alerts" />
    </div>

    <div class="project-details">
      <div class="detail-item">
        <div class="detail-icon">
          <i class="bi bi-person-vcard"></i>
        </div>
        <div class="detail-content">
          <div class="detail-label">Latest Profile</div>
          <div class="detail-value">{{ project.latestProfileAt || 'None' }}</div>
        </div>
      </div>

      <div class="detail-item">
        <div class="detail-icon">
          <i class="bi bi-record-circle"></i>
        </div>
        <div class="detail-content">
          <div class="detail-label">Latest Recording</div>
          <div class="detail-value">{{ project.latestRecordingAt || 'None' }}</div>
        </div>
      </div>
      
    </div>

  </div>
</template>

<script setup lang="ts">
import {defineProps} from 'vue';
import Project from "@/services/model/Project.ts";
import RecordingStatus from "@/services/model/data/RecordingStatus.ts";
import router from "@/router";
import Badge from '@/components/Badge.vue';

defineProps<{
  project: Project
}>();

const moveToProject = (projectId: string) => {
  router.push({
    name: "project-profiles",
    params: {projectId: projectId},
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

const getStatusVariant = (status: RecordingStatus): string => {
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

.project-created {
  font-size: 0.7rem;
  color: #8b95a7;
  margin-top: 0.5rem;
  font-weight: 400;
  opacity: 0.8;
  letter-spacing: 0.2px;
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

</style>
