<template>
  <div class="w-100 bg-light index-root">
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
        </div>
      </div>
    </div>

    <MainNavigation />

    <!-- Main Content -->
    <div class="container-fluid px-4">
      <router-view />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import MainNavigation from '@/components/MainNavigation.vue';
import VersionClient from '@/services/api/VersionClient';

const version = ref('');
const versionClient = new VersionClient();

onMounted(async () => {
  try {
    version.value = await versionClient.getVersion();
  } catch {
    // Gracefully hide version badge if API unavailable
    version.value = '';
  }
});
</script>

<style scoped>
/* Fill the viewport so the `bg-light` surface extends under the entire page, otherwise the
   slightly darker AppLayout body color (`--color-bg-body`) shows through below the last card. */
.index-root {
  min-height: 100vh;
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
