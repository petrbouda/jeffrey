<template>
  <div class="project-card">
    <div class="project-header">
      <h5 class="project-title">{{ project.name }}</h5>
    </div>

    <div class="project-badges">
      <span class="project-badge profile-badge">
        {{ project.profileCount }} profiles
      </span>
      <span class="project-badge recording-badge">
        {{ project.recordingCount || 0 }} recordings
      </span>
      <span class="project-badge" :class="project.sourceType === 'JDK' ? 'jdk-source' : 'source-badge'">
        {{ project.sourceType || 'Unknown' }}
      </span>
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
      
      <div class="detail-item">
        <div class="detail-icon">
          <i class="bi bi-calendar-plus"></i>
        </div>
        <div class="detail-content">
          <div class="detail-label">Created</div>
          <div class="detail-value">{{ project.createdAt }}</div>
        </div>
      </div>
    </div>

    <div class="project-footer">
      <button @click="moveToProject(project.id)" class="project-button">
        <i class="bi bi-arrow-right me-1"></i>Open Project
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {defineProps} from 'vue';
import Project from "@/services/model/Project.ts";
import router from "@/router";

defineProps<{
  project: Project
}>();

const moveToProject = (projectId: string) => {
  router.push({
    name: "project-profiles",
    params: {projectId: projectId},
  });
};

const formatDate = (dateString: string): string => {
  const date = new Date(dateString);
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  });
};
</script>

<style scoped>
.project-card {
  background-color: white;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #eef0f7;
  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.03);
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  padding: 1.25rem;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.project-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 15px 30px rgba(0, 0, 0, 0.1);
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

.project-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.project-badge {
  display: inline-flex;
  align-items: center;
  padding: 0.35rem 0.75rem;
  border-radius: 6px;
  font-size: 0.7rem;
  font-weight: 500;
  white-space: nowrap;
}

.profile-badge {
  background-color: rgba(230, 126, 34, 0.1);
  color: #e67e22;
}

.recording-badge {
  background-color: rgba(75, 192, 192, 0.1);
  color: #4bc0c0;
}

.jdk-source {
  background-color: rgba(75, 192, 119, 0.1);
  color: #4bc077;
}

.source-badge {
  background-color: rgba(94, 100, 255, 0.1);
  color: #5e64ff;
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

.project-footer {
  padding-top: 0.75rem;
  border-top: 1px solid #f0f2f8;
}

.project-button {
  width: 100%;
  padding: 0.5rem;
  border-radius: 8px;
  border: none;
  background-color: #5e64ff;
  color: white;
  font-weight: 500;
  font-size: 0.875rem;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.project-button:hover {
  background-color: #4a50e3;
  box-shadow: 0 4px 10px rgba(94, 100, 255, 0.25);
}
</style>
