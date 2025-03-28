<template>
  <div class="card h-100 overflow-hidden">
    <div class="card-body p-0">
      <div class="p-3 bg-soft-primary d-flex justify-content-between align-items-center">
        <div class="d-flex align-items-center">
          <div class="avatar rounded-circle bg-primary text-white d-flex align-items-center justify-content-center me-2">
            <span>{{ project.name.substring(0, 1).toUpperCase() }}</span>
          </div>
          <h5 class="card-title mb-0 text-primary fw-bold">{{ project.name }}</h5>
        </div>
        <div class="dropdown">
          <button class="btn btn-sm btn-phoenix-primary" 
                  type="button" 
                  id="projectActions" 
                  data-bs-toggle="dropdown" 
                  aria-expanded="false">
            <i class="bi bi-three-dots-vertical"></i>
          </button>
          <ul class="dropdown-menu dropdown-menu-end" aria-labelledby="projectActions">
            <li><a class="dropdown-item" href="#" @click.prevent="editProject"><i class="bi bi-pencil me-2"></i>Edit</a></li>
            <li><a class="dropdown-item text-danger" href="#" @click.prevent="onDelete"><i class="bi bi-trash me-2"></i>Delete</a></li>
          </ul>
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
            <i class="bi bi-code-square me-1" :class="project.sourceType === 'JDK' ? 'text-success' : 'text-primary'"></i>
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
        <router-link :to="`/projects/${project.id}`" class="btn btn-phoenix-primary w-100">
          <i class="bi bi-box-arrow-in-right me-2"></i>Open Project
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { defineProps, defineEmits } from 'vue';

interface Project {
  id: string;
  name: string;
  createdAt: string;
  profileCount: number;
  recordingCount?: number;
  alertCount?: number;
  sourceType?: 'JDK' | 'ASPROF';
  latestRecordingAt?: string;
  latestProfileAt?: string;
}

const props = defineProps<{
  project: Project
}>();

const emit = defineEmits(['delete']);

const formatDate = (dateString: string): string => {
  const date = new Date(dateString);
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  });
};

const editProject = () => {
  // In a real app, this would show an edit modal
  console.log('Edit project:', props.project.id);
};

const onDelete = () => {
  emit('delete', props.project.id);
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
