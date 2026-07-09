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
          <p class="hero-subtitle">Performance Analyst</p>
        </div>

        <div class="hero-actions">
          <!-- Restart-required indicator (a setting changed that only applies after a restart) -->
          <span
            v-if="restartRequired"
            class="restart-indicator"
            title="Restart Jeffrey to apply configuration changes"
          >
            <span class="restart-tile"><i class="bi bi-arrow-repeat"></i></span>
            <span class="restart-text">
              <span class="restart-title">Restart required</span>
              <span class="restart-sub">to apply configuration changes</span>
            </span>
          </span>

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
import { useRestartRequired, refreshRestartRequired } from '@/stores/restartStore';

const route = useRoute();

const version = ref('');
const versionClient = new VersionClient();

const restartRequired = useRestartRequired();

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
  // Reflect a pending restart (e.g. user changed settings in a previous session).
  refreshRestartRequired();
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
  gap: 14px;
  margin-left: auto;
}

/* Restart-required indicator — solid amber card with dark text (high-contrast) */
.restart-indicator {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 6px 14px 6px 8px;
  border-radius: 11px;
  background: linear-gradient(135deg, var(--color-amber-badge-border), var(--color-amber));
  border: 1px solid rgba(120, 53, 15, 0.45);
  box-shadow: 0 6px 20px rgba(245, 158, 11, 0.45);
}

.restart-tile {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: rgba(120, 53, 15, 0.16);
  color: var(--color-amber-dark);
  font-size: 1.05rem;
}

.restart-text {
  display: flex;
  flex-direction: column;
}

.restart-title {
  font-size: 0.84rem;
  font-weight: 800;
  color: var(--color-amber-dark);
  line-height: 1.15;
}

.restart-sub {
  font-size: 0.68rem;
  font-weight: 600;
  color: var(--color-amber-message);
  margin-top: 1px;
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
