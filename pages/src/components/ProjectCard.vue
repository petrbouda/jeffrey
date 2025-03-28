<template>
  <div class="card h-100 overflow-hidden">
    <div class="card-body p-0">
      <div class="p-3 bg-soft-primary d-flex justify-content-between align-items-center">
        <div class="d-flex align-items-center">
          <h5 class="card-title mb-0 text-primary fw-bold">{{ project.name }}</h5>
        </div>
      </div>

      <div class="px-3 pt-3">
        <div class="d-flex justify-content-between mb-2">
          <div class="d-flex gap-2">
            <span class="badge bg-primary rounded-pill fs-7">{{ project.profileCount }} profiles</span>
            <span class="badge bg-info rounded-pill fs-7">{{ project.recordingCount || 0 }} recordings</span>
          </div>
          <div class="text-muted small">
            <i class="bi bi-calendar3 me-1"></i>
            {{ formatDate(project.createdAt) }}
          </div>
        </div>

        <div class="d-flex justify-content-between mb-2 align-items-center p-2 bg-light rounded">
          <div class="text-muted small">
            <i class="bi bi-shield-exclamation me-1 text-warning"></i>
            Guardian Alerts
          </div>
          <span class="badge bg-warning rounded-pill">{{ project.alertCount || 0 }}</span>
        </div>

        <div class="d-flex justify-content-between mb-2 align-items-center p-2 bg-light rounded">
          <div class="text-muted small">
            <i class="bi bi-code-square me-1"
               :class="project.sourceType === 'JDK' ? 'text-success' : 'text-primary'"></i>
            Source Type
          </div>
          <span class="badge rounded-pill" :class="project.sourceType === 'JDK' ? 'bg-success' : 'bg-primary'">
            {{ project.sourceType || 'Unknown' }}
          </span>
        </div>

        <div class="d-flex justify-content-between mb-2 align-items-center p-2 bg-light rounded">
          <div class="text-muted small">
            <i class="bi bi-clock-history me-1 text-secondary"></i>
            Latest Recording
          </div>
          <div class="text-monospace small">
            {{ project.latestRecordingAt || 'None' }}
          </div>
        </div>

        <div class="d-flex justify-content-between mb-2 align-items-center p-2 bg-light rounded">
          <div class="text-muted small">
            <i class="bi bi-person-vcard me-1 text-secondary"></i>
            Latest Profile
          </div>
          <div class="text-monospace small">
            {{ project.latestProfileAt || 'None' }}
          </div>
        </div>
      </div>

      <div class="mt-auto p-3 pb-0 pt-0">
        <button @click="moveToProject(project.id)" class="btn btn-phoenix-primary w-100">
          <i class="bi bi-box-arrow-in-right me-2"></i>Open Project
        </button>
      </div>
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
.card {
  transition: transform 0.2s, box-shadow 0.2s;
  border-radius: 0.5rem;
  overflow: hidden;
  border: 1px solid #c2c8d1; /* Darker border color */

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 0.5rem 1.125rem -0.5rem rgba(0, 0, 0, 0.2);
    border-color: #9aa0aa; /* Even darker on hover */
  }
}

.avatar {
  width: 2rem;
  height: 2rem;
  position: relative;
  display: inline-block;
}

.bg-soft-primary {
  background-color: rgba(94, 100, 255, 0.1) !important;
}

.fs-7 {
  font-size: 0.75rem !important;
}

.text-monospace {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.75rem;
}

.btn-phoenix-primary {
  color: #5e64ff;
  background-color: rgba(94, 100, 255, 0.1);
  border-color: transparent;

  &:hover {
    color: #fff;
    background-color: #5e64ff;
  }
}
</style>
