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
  <div class="ide-target-bar">
    <!-- Jeffrey IntelliJ Plugin, linked: single-row card with the cached window + actions -->
    <div v-if="status.linked && status.selectable" class="ide-card">
      <span class="ide-tile"><i class="bi bi-window-stack"></i></span>
      <div class="ide-line">
        <span class="ide-name">{{ status.ideName }}</span>
        <Badge
          variant="success"
          icon="bi bi-circle-fill"
          :value="status.projectName ?? ''"
          :uppercase="false"
          size="s"
        />
        <span v-if="location" class="ide-where">{{ location }}</span>
      </div>
      <div class="ide-actions">
        <button type="button" class="ide-ghost" @click="onChange">
          <i class="bi bi-arrow-repeat"></i> Change
        </button>
        <button type="button" class="ide-ghost danger" @click="onDisconnect">
          <i class="bi bi-x-lg"></i> Disconnect
        </button>
      </div>
    </div>

    <!-- JFR Profiler Plugin: single-URL connection — auto-linked, nothing to pick or disconnect -->
    <div v-else-if="status.linked" class="ide-card">
      <span class="ide-tile"><i class="bi bi-window-stack"></i></span>
      <div class="ide-stack">
        <span class="ide-name">IntelliJ IDEA</span>
        <span class="ide-detail">Connected using JFR Profiler Plugin</span>
      </div>
    </div>

    <!-- Not linked: onboarding (detect / install plugin) -->
    <div v-else class="ide-card onboarding">
      <span class="ide-tile muted"><i class="bi bi-window-stack"></i></span>
      <div class="onb-main">
        <div class="onb-title">Jump from Jeffrey straight into your code</div>
        <div class="onb-desc">
          Link a running IntelliJ window to open frames &amp; classes in your editor. Requires the
          Jeffrey IntelliJ Plugin.
        </div>
      </div>
      <div class="ide-actions">
        <button type="button" class="ide-detect" @click="onChange">
          <i class="bi bi-search"></i> Detect IntelliJ
        </button>
        <a
          class="ide-install"
          :href="PLUGIN_MARKETPLACE_URL"
          target="_blank"
          rel="noopener noreferrer"
        >
          <i class="bi bi-box-arrow-up-right"></i> Install the plugin
        </a>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import Badge from '@shared/components/Badge.vue';
import ideProfileTargetStore from '@/stores/ideProfileTargetStore';

const props = defineProps<{ profileId: string }>();

// TODO: replace with the published JetBrains Marketplace URL for the Jeffrey IntelliJ Plugin.
const PLUGIN_MARKETPLACE_URL = 'https://plugins.jetbrains.com/';

const status = ideProfileTargetStore.status;

const location = computed(() => {
  const parts: string[] = [];
  if (status.value.port) {
    parts.push(`localhost:${status.value.port}`);
  }
  if (status.value.pid) {
    parts.push(`PID ${status.value.pid}`);
  }
  return parts.join(' · ');
});

function onChange(): void {
  void ideProfileTargetStore.selectOrChange(props.profileId);
}

function onDisconnect(): void {
  void ideProfileTargetStore.disconnect(props.profileId);
}
</script>

<style scoped>
.ide-target-bar {
  padding: 0 1rem;
  margin-bottom: var(--spacing-3);
}

.ide-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  padding: var(--spacing-3) var(--spacing-4);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-primary);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-base);
}

.ide-card.onboarding {
  border-left: 3px dashed var(--color-text-muted);
  background: var(--color-light);
}

.ide-tile {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: var(--radius-md);
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: var(--font-size-md);
}

.ide-tile.muted {
  background: var(--color-light);
  color: var(--color-text-muted);
}

.ide-line {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  min-width: 0;
}

.ide-name {
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-base);
  color: var(--color-dark);
  white-space: nowrap;
}

.ide-stack {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.ide-detail {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  margin-top: 2px;
}

.ide-where {
  font-family: var(--font-mono, monospace);
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.onb-main {
  flex: 1;
  min-width: 0;
}

.onb-title {
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-base);
  color: var(--color-dark);
}

.onb-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  margin-top: 2px;
}

.ide-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  margin-left: auto;
}

.ide-ghost {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-1);
  padding: var(--spacing-2) var(--spacing-3);
  border: none;
  background: transparent;
  border-radius: var(--radius-base);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-muted);
  cursor: pointer;
  transition: all var(--transition-fast);
  white-space: nowrap;
}

.ide-ghost:hover {
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.ide-ghost.danger:hover {
  background: var(--color-danger-light);
  color: var(--color-danger);
}

.ide-detect {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-1);
  padding: var(--spacing-2) var(--spacing-4);
  border: 1px solid var(--color-primary);
  background: var(--color-primary-light);
  color: var(--color-primary);
  border-radius: var(--radius-base);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  cursor: pointer;
  transition: all var(--transition-fast);
  white-space: nowrap;
}

.ide-detect:hover {
  background: var(--color-primary);
  color: var(--color-white);
}

.ide-install {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-1);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
  text-decoration: none;
  white-space: nowrap;
}

.ide-install:hover {
  text-decoration: underline;
}
</style>
