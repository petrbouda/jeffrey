<template>
  <div class="sidebar">
    <div class="scrollbar" style="height: 100%;">
      <div class="p-3 border-bottom d-flex align-items-center">
        <div class="d-flex align-items-center">
          <div class="avatar avatar-l rounded-circle bg-soft-primary me-2 d-flex align-items-center justify-content-center">
            <span class="text-primary fw-bold">P</span>
          </div>
          <div>
            <h5 class="fs-6 fw-bold mb-0 text-truncate" style="max-width: 180px;">{{ projectName }}</h5>
            <p class="text-muted mb-0 fs-7">Project dashboard</p>
          </div>
        </div>
      </div>
      
      <div class="py-3">
        <div class="nav-category px-3">Navigation</div>
        <ul class="nav flex-column">
          <li class="nav-item px-3 py-1" v-for="(item, index) in menuItems" :key="index">
            <router-link 
              :to="getItemRoute(item.path)" 
              class="nav-link d-flex align-items-center py-2"
              :class="{ 'active': isActive(item.path) }"
            >
              <i :class="item.icon" class="me-2"></i>
              <span>{{ item.label }}</span>
              <span v-if="item.badge" class="badge rounded-pill ms-auto" :class="'bg-' + item.badge.type">{{ item.badge.text }}</span>
            </router-link>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';
import ProjectSchedulerService from "@/services/project/ProjectSchedulerService";
import MessageBus from "@/services/MessageBus";

const route = useRoute();

const projectId = computed(() => route.params.projectId);
const projectName = ref('My Project'); // This would come from an API
const jobCount = ref(0);

// Create scheduler service
const schedulerService = new ProjectSchedulerService(projectId.value as string);

// Fetch active jobs count
async function fetchJobCount() {
  try {
    const jobs = await schedulerService.all();
    jobCount.value = jobs.length;
  } catch (error) {
    console.error('Failed to fetch job count:', error);
    jobCount.value = 0;
  }
}

// Set up message bus listener for job count updates
function handleJobCountChange(count: number) {
  jobCount.value = count;
}

// Component lifecycle hooks
onMounted(() => {
  fetchJobCount();
  MessageBus.on(MessageBus.JOBS_COUNT_CHANGED, handleJobCountChange);
});

onUnmounted(() => {
  MessageBus.off(MessageBus.JOBS_COUNT_CHANGED);
});

const menuItems = computed(() => [
  { label: 'Profiles', icon: 'bi bi-person-vcard', path: 'profiles', badge: { type: 'primary', text: '4' } },
  { label: 'Recordings', icon: 'bi bi-record-circle', path: 'recordings', badge: { type: 'info', text: '12' } },
  { label: 'Repository', icon: 'bi bi-link-45deg', path: 'repository' },
  { label: 'Scheduler', icon: 'bi bi-clock-history', path: 'scheduler', 
    badge: jobCount.value > 0 ? { type: 'warning', text: jobCount.value.toString() } : null },
  { label: 'Settings', icon: 'bi bi-gear', path: 'settings' }
]);

const getItemRoute = (path: string) => {
  return `/projects/${projectId.value}/${path}`;
};

const isActive = (path: string) => {
  return route.path.includes(path);
};
</script>

<style lang="scss" scoped>
.sidebar {
  height: 100%;
  width: 280px;
  min-width: 280px;
  max-width: 280px;
  overflow: hidden;
  background-color: #fff;
}

.scrollbar {
  overflow-y: auto;
  
  &::-webkit-scrollbar {
    width: 5px;
    height: 5px;
  }
  
  &::-webkit-scrollbar-thumb {
    background-color: rgba(0, 0, 0, 0.1);
    border-radius: 4px;
  }
  
  &:hover::-webkit-scrollbar-thumb {
    background-color: rgba(0, 0, 0, 0.2);
  }
}

.nav-category {
  color: #748194;
  font-weight: 700;
  text-transform: uppercase;
  font-size: 0.7rem;
  letter-spacing: 0.02em;
}

.nav-link {
  color: #5e6e82;
  font-weight: 500;
  font-size: 0.9rem;
  border-radius: 0.25rem;
  
  &:hover {
    color: #5e64ff;
    background-color: #edf2f9;
  }
  
  &.active {
    color: #5e64ff;
    background-color: #eaebff;
    
    i {
      color: #5e64ff;
    }
  }
  
  i {
    color: #7d899b;
    font-size: 0.9rem;
    width: 1rem;
    text-align: center;
  }
}

.avatar {
  width: 2rem;
  height: 2rem;
  position: relative;
  display: inline-block;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-l {
  width: 2.5rem;
  height: 2.5rem;
  font-size: 1rem;
}

.bg-soft-primary {
  background-color: rgba(94, 100, 255, 0.1) !important;
}

.fs-7 {
  font-size: 0.75rem !important;
}
</style>