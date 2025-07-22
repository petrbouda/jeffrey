<template>
  <div class="job-card h-100 p-4 shadow-sm d-flex flex-column border" 
       :class="{'coming-soon-card': comingSoon}">
    <div class="d-flex align-items-center mb-3">
      <div class="job-icon me-3 d-flex align-items-center justify-content-center"
           :class="iconBg">
        <i class="fs-4" :class="[icon, iconColor]"></i>
      </div>
      <div>
        <h5 class="mb-0 fw-semibold">{{ title }}</h5>
        <div class="d-flex mt-1" v-if="badges && badges.length > 0">
          <span v-for="badge in visibleBadges" :key="badge.text"
                class="badge rounded-pill" :class="badge.color">
            {{ badge.text }}
          </span>
        </div>
      </div>
    </div>
    <p class="text-muted mb-3">{{ description }}</p>
    <div class="mt-auto d-flex justify-content-end">
      <button 
        class="btn btn-primary" 
        @click="handleCreateJob"
        :disabled="disabled">
        <i class="bi bi-plus-lg me-1"></i>{{ buttonText }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

interface Badge {
  text: string;
  color: string;
  condition: boolean;
}

interface Props {
  jobType: string;
  title: string;
  description: string;
  icon: string;
  iconColor: string;
  iconBg: string;
  disabled?: boolean;
  comingSoon?: boolean;
  badges?: Badge[];
  buttonText?: string;
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  comingSoon: false,
  badges: () => [],
  buttonText: 'Create Job'
});

const emit = defineEmits<{
  createJob: [jobType: string];
}>();

const visibleBadges = computed(() => {
  return props.badges.filter(badge => badge.condition);
});

const handleCreateJob = () => {
  if (!props.disabled) {
    emit('createJob', props.jobType);
  }
};
</script>

<style scoped>
/* Job Card Styles */
.job-card {
  transition: all 0.2s ease;
  border-color: #e9ecef !important;
  border-radius: 0.25rem;
}

.job-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 0.5rem 1rem rgba(0, 0, 0, 0.08) !important;
  border-color: #dee2e6 !important;
}

/* Coming Soon job cards */
.coming-soon-card {
  background-color: #f8f9fa;
  opacity: 0.7;
  border-color: #dee2e6 !important;
}

/* Job icons */
.job-icon {
  width: 56px;
  height: 56px;
  min-width: 56px;
  font-size: 1.5rem;
  border-radius: 0.45rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Colors */
.bg-teal-soft {
  background-color: rgba(32, 201, 151, 0.15);
}

.text-teal {
  color: #20C997;
}

.bg-blue-soft {
  background-color: rgba(13, 110, 253, 0.15);
}

.text-blue {
  color: #0d6efd;
}

/* Button styling */
.btn-primary {
  background-color: #5e64ff;
  border-color: #5e64ff;
  box-shadow: 0 0.125rem 0.25rem rgba(94, 100, 255, 0.15);
}

.btn-primary:hover, .btn-primary:active {
  background-color: #4a51eb !important;
  border-color: #4a51eb !important;
}

/* Badge styling */
.badge {
  font-weight: 500;
  padding: 0.4em 0.65em;
}

.badge.rounded-pill {
  font-size: 0.75rem;
}

/* Typography utilities */
.fw-semibold {
  font-weight: 600 !important;
}

.text-muted {
  color: #6c757d !important;
}

/* Shadow utilities */
.shadow-sm {
  box-shadow: 0 0.125rem 0.375rem rgba(0, 0, 0, 0.05) !important;
}

.border {
  border-color: #e9ecef !important;
}
</style>