<template>
  <div class="w-100 index-root">
    <!-- Hero Header -->
    <div class="hero-header">
      <!-- Decorative circles -->
      <div class="hero-decoration hero-decoration-1"></div>
      <div class="hero-decoration hero-decoration-2"></div>
      <div class="hero-decoration hero-decoration-3"></div>

      <div class="container-fluid">
        <div class="hero-content">
          <div class="hero-logo-container">
            <img src="/jeffrey-icon.svg" alt="Jeffrey Logo" class="hero-logo" />
          </div>
          <div>
            <div class="hero-title-row">
              <h1 class="hero-title">Jeffrey</h1>
              <span v-if="version" class="hero-version">{{ version }}</span>
            </div>
            <p class="hero-subtitle">JDK Flight Recorder Analysis Tool</p>
          </div>

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
        </div>
      </div>
    </div>

    <MainNavigation :items="navItems" />

    <!-- Main Content -->
    <div class="container-fluid px-4">
      <router-view />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import MainNavigation from '@shared/components/MainNavigation.vue';
import type { NavItem } from '@shared/types/ui';
import VersionClient from '@/services/api/VersionClient';
import { useRestartRequired, refreshRestartRequired } from '@/stores/restartStore';

const navItems: NavItem[] = [
  { to: '/recordings', icon: 'bi bi-record-circle', label: 'Recordings' },
  { to: '/workspaces', icon: 'bi bi-folder', label: 'Workspaces' },
  { to: '/settings', icon: 'bi bi-sliders', label: 'Settings' },
  { to: '/guardian-guards', icon: 'bi bi-shield-check', label: 'Guardians' }
];

const version = ref('');
const versionClient = new VersionClient();

const restartRequired = useRestartRequired();

onMounted(async () => {
  try {
    version.value = await versionClient.getVersion();
  } catch {
    // Gracefully hide version badge if API unavailable
    version.value = '';
  }
  // Reflect a pending restart (e.g. user enabled Claude Code or changed settings).
  refreshRestartRequired();
});
</script>

<style scoped>
/* Fill the viewport with the same surface as MainCard so no darker bg shows below the
   last card on short pages. */
.index-root {
  min-height: 100vh;
  background-color: var(--color-bg-card);
}

.hero-header {
  background: linear-gradient(
    135deg,
    var(--color-indigo-dark) 0%,
    var(--color-indigo) 40%,
    var(--color-indigo-light) 70%,
    var(--color-primary) 100%
  );
  padding: 16px 0;
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
  padding: 4px;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 14px;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
  flex-shrink: 0;
}

.hero-logo {
  height: 52px;
  width: 52px;
  display: block;
  border-radius: 10px;
  object-fit: cover;
}

.hero-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 2px;
}

.hero-title {
  font-size: 1.6rem;
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
  font-size: 0.78rem;
  color: rgba(255, 255, 255, 0.6);
  font-weight: 400;
  letter-spacing: 0.5px;
  margin: 0;
}

/* Restart-required indicator — solid amber card with dark text (high-contrast), pushed to the right */
.restart-indicator {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px 8px 9px;
  border-radius: 11px;
  background: linear-gradient(135deg, var(--color-amber-badge-border), var(--color-amber));
  border: 1px solid rgba(120, 53, 15, 0.45);
  box-shadow: 0 6px 20px rgba(245, 158, 11, 0.45);
}

.restart-tile {
  width: 36px;
  height: 36px;
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
  font-size: 0.88rem;
  font-weight: 800;
  color: var(--color-amber-dark);
  line-height: 1.15;
}

.restart-sub {
  font-size: 0.7rem;
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

/* Responsive */
@media (max-width: 576px) {
  .hero-header {
    padding: 20px 0;
  }

  .hero-logo-container {
    width: 44px;
    height: 44px;
  }

  .hero-logo {
    height: 30px;
    width: 30px;
  }

  .hero-title {
    font-size: 1.3rem;
  }
}
</style>
