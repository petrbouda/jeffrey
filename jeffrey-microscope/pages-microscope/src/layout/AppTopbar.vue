<template>
  <div class="hero-header">
    <!-- Decorative circles -->
    <div class="hero-decoration hero-decoration-1"></div>
    <div class="hero-decoration hero-decoration-2"></div>
    <div class="hero-decoration hero-decoration-3"></div>

    <div class="container-fluid">
      <div class="hero-content">
        <a class="hero-logo-container" href="/">
          <img src="/jeffrey-icon.svg" alt="Jeffrey Logo" class="hero-logo" />
        </a>

        <div class="hero-text">
          <div class="hero-title-row">
            <h1 class="hero-title">Jeffrey</h1>
            <span v-if="version" class="hero-version">{{ version }}</span>
          </div>
          <p class="hero-subtitle">JDK Flight Recorder Analysis Tool</p>
        </div>

        <div class="hero-actions">
          <!-- Back to Recordings button (only shown on profile pages) -->
          <button
            v-if="isProfilePage"
            class="topbar-back-btn"
            @click="$router.push('/recordings')"
            title="Back to Recordings"
          >
            <i class="bi bi-arrow-return-left"></i>
            <span>Recordings</span>
          </button>

          <!-- Back to Workspaces button (only shown on project pages) -->
          <button
            v-if="isProjectPage"
            class="topbar-back-btn"
            @click="$router.push('/workspaces')"
            title="Back to workspaces"
          >
            <i class="bi bi-arrow-return-left"></i>
            <span>Workspaces</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import VersionClient from '@/services/api/VersionClient';

const route = useRoute();

const version = ref('');
const versionClient = new VersionClient();

const isProfilePage = computed(() => route.meta.layout === 'profile');

const isProjectPage = computed(() => {
  return route.meta.layout === 'project' || route.path.includes('/projects/');
});

onMounted(async () => {
  try {
    version.value = await versionClient.getVersion();
  } catch {
    version.value = '';
  }
});
</script>

<style scoped>
.hero-header {
  background: linear-gradient(
    135deg,
    var(--color-indigo-dark) 0%,
    var(--color-indigo) 40%,
    var(--color-indigo-light) 70%,
    var(--color-primary) 100%
  );
  padding: 10px 0;
  position: relative;
  overflow: hidden;
}

.hero-content {
  display: flex;
  align-items: center;
  gap: 16px;
  position: relative;
  z-index: 1;
}

.hero-logo-container {
  padding: 3px;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 10px;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
  flex-shrink: 0;
  text-decoration: none;
  display: inline-flex;
}

.hero-logo {
  height: 38px;
  width: 38px;
  display: block;
  border-radius: 7px;
  object-fit: cover;
}

.hero-text {
  flex: 1;
  min-width: 0;
}

.hero-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 2px;
}

.hero-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: white;
  letter-spacing: -0.02em;
  margin: 0;
  line-height: 1.2;
}

.hero-version {
  padding: 3px 10px;
  background: rgba(0, 210, 122, 0.2);
  border: 1px solid rgba(0, 210, 122, 0.3);
  border-radius: 12px;
  font-size: 0.6rem;
  font-weight: 600;
  color: var(--color-emerald-text);
  letter-spacing: 0.3px;
  white-space: nowrap;
}

.hero-subtitle {
  font-size: 0.7rem;
  color: rgba(255, 255, 255, 0.6);
  font-weight: 400;
  letter-spacing: 0.5px;
  margin: 0;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

/* Decorative circles */
.hero-decoration {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.hero-decoration-1 {
  top: -40px;
  right: -40px;
  width: 140px;
  height: 140px;
  background: rgba(255, 255, 255, 0.04);
}

.hero-decoration-2 {
  bottom: -25px;
  left: 35%;
  width: 90px;
  height: 90px;
  background: rgba(255, 255, 255, 0.03);
}

.hero-decoration-3 {
  top: 50%;
  right: 20%;
  width: 60px;
  height: 60px;
  background: rgba(255, 255, 255, 0.02);
}

/* Back buttons — glassmorphic style matching the original topbar */
.topbar-back-btn {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 0.78rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  text-decoration: none;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(10px);
  white-space: nowrap;
}

.topbar-back-btn:hover {
  background: rgba(255, 255, 255, 0.22);
  color: white;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.topbar-back-btn:active {
  transform: translateY(0);
}

.topbar-back-btn i {
  font-size: 0.72rem;
}

/* Responsive */
@media (max-width: 576px) {
  .hero-header {
    padding: 20px 0;
  }

  .hero-logo-container {
    padding: 2px;
  }

  .hero-logo {
    height: 38px;
    width: 38px;
  }

  .hero-title {
    font-size: 1.3rem;
  }
}
</style>

