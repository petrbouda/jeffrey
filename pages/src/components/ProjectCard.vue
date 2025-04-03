<template>
  <div class="project-card card h-100 overflow-hidden">
    <div class="card-header bg-soft-primary border-0 d-flex align-items-center py-2 px-3">
      <h5 class="card-title mb-0 text-primary fw-bold fs-6">{{ project.name }}</h5>
    </div>

    <div class="card-body p-2">
      <div class="d-flex gap-2 flex-wrap mb-2">
        <span class="badge bg-primary rounded-pill">{{ project.profileCount }} profiles</span>
        <span class="badge bg-info rounded-pill">{{ project.recordingCount || 0 }} recordings</span>
        <span class="badge rounded-pill" :class="project.sourceType === 'JDK' ? 'bg-success' : 'bg-primary'">
          {{ project.sourceType || 'Unknown' }}
        </span>
      </div>

      <div class="info-grid">
        <div class="info-item">
          <div class="info-content">
            <div class="info-label">Guardian Alerts</div>
            <div class="info-value">
              <span class="badge bg-warning rounded-pill">{{ project.alertCount || 0 }}</span>
            </div>
          </div>
        </div>


        <div class="info-item">
          <div class="info-content">
            <div class="info-label">Latest Recording</div>
            <div class="info-value text-monospace">
              {{ project.latestRecordingAt || 'None' }}
            </div>
          </div>
        </div>

        <div class="info-item">
          <div class="info-content">
            <div class="info-label">Latest Profile</div>
            <div class="info-value text-monospace">
              {{ project.latestProfileAt || 'None' }}
            </div>
          </div>
        </div>
        
        <div class="info-item">
          <div class="info-content">
            <div class="info-label">Created At</div>
            <div class="info-value text-monospace">
              {{ project.createdAt }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="card-footer border-0 p-2 bg-white">
      <button @click="moveToProject(project.id)" class="btn btn-primary btn-sm w-100">
        <i class="bi bi-box-arrow-in-right me-1"></i>Open
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
  transition: all 0.3s ease;
  border-radius: 0.5rem;
  overflow: hidden;
  border: none;
  box-shadow: 0 0.25rem 0.5rem rgba(0, 0, 0, 0.05);

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 0.35rem 1rem rgba(0, 0, 0, 0.1);
  }
}

.card-header {
  border-bottom: none;
  min-height: 40px;
}


.info-grid {
  display: grid;
  grid-gap: 0.5rem;
}

.info-item {
  display: flex;
  align-items: center;
  padding: 0.5rem 0.75rem;
  border-radius: 0.375rem;
  background-color: #f9fafd;
  transition: background-color 0.2s ease;

  &:hover {
    background-color: #f2f5fc;
  }
}

.info-content {
  flex: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-label {
  font-size: 0.75rem;
  font-weight: 500;
  color: #5e6e82;
}

.info-value {
  font-size: 0.75rem;
}

.text-monospace {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.75rem;
  color: #5e6e82;
}

.card-footer {
  border-top: none;
  background-color: transparent;
}

.btn-primary {
  background-color: #5e64ff;
  border-color: #5e64ff;
  transition: all 0.2s ease;
  font-weight: 500;

  &:hover {
    background-color: darken(#5e64ff, 10%);
    border-color: darken(#5e64ff, 10%);
    box-shadow: 0 0.25rem 0.5rem rgba(94, 100, 255, 0.25);
  }
}
</style>
