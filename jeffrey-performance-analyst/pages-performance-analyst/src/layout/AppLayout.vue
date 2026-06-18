<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <div class="w-100 app-root">
    <!-- Hero Header -->
    <div class="hero-header">
      <div class="container-fluid">
        <div class="hero-content">
          <a class="hero-logo-container" href="/">
            <img src="/jeffrey-icon.svg" alt="Jeffrey Logo" class="hero-logo" />
          </a>
          <div>
            <div class="hero-title-row">
              <h1 class="hero-title">Jeffrey</h1>
              <span v-if="version" class="hero-version">{{ version }}</span>
            </div>
            <p class="hero-subtitle">Performance Analyst</p>
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
/* Fill the viewport with the same surface as the cards so no darker bg shows below the
   last card on short pages. */
.app-root {
  min-height: 100vh;
  background-color: var(--color-bg-card);
}

/* "Aurora Teal" — performance-teal identity, distinct from Microscope's indigo header.
   The emerald radial highlight is baked into the background (no separate decoration circles). */
.hero-header {
  background:
    radial-gradient(120% 140% at 88% 10%, rgba(110, 231, 183, 0.2) 0%, transparent 45%),
    linear-gradient(120deg, #0b3b4f 0%, #0e6e6e 45%, #13a08a 78%, #1fc7a0 100%);
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
  padding: 6px;
  background: rgba(255, 255, 255, 0.13);
  border-radius: 14px;
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.22);
  flex-shrink: 0;
  text-decoration: none;
  display: inline-flex;
}

.hero-logo {
  height: 48px;
  width: 48px;
  display: block;
  border-radius: 10px;
  object-fit: cover;
}

.hero-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 3px;
}

.hero-title {
  font-family: 'Sora', sans-serif;
  font-size: 1.5rem;
  font-weight: 800;
  color: white;
  letter-spacing: -0.02em;
  margin: 0;
  line-height: 1.1;
}

/* Version badge — kept inline next to the title, styled as an emerald monospace pill */
.hero-version {
  font-family: 'JetBrains Mono', ui-monospace, 'SFMono-Regular', Menlo, monospace;
  padding: 3px 9px;
  background: rgba(110, 231, 183, 0.16);
  border: 1px solid rgba(110, 231, 183, 0.4);
  border-radius: 999px;
  font-size: 0.64rem;
  font-weight: 500;
  color: #bdffe6;
  letter-spacing: 0;
  white-space: nowrap;
}

.hero-subtitle {
  font-family: 'Sora', sans-serif;
  font-size: 0.74rem;
  color: rgba(255, 255, 255, 0.72);
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  margin: 0;
}

/* Responsive */
@media (max-width: 576px) {
  .hero-header {
    padding: 20px 0;
  }

  .hero-logo {
    height: 40px;
    width: 40px;
  }

  .hero-title {
    font-size: 1.3rem;
  }
}
</style>
