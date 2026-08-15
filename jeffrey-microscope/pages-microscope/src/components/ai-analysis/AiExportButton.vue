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

<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import { useAiExport, type AiExportSource } from '@/composables/useAiExport';

const props = defineProps<{
  /**
   * Describes what to export, resolved at click time rather than passed as a value: the thing being
   * exported is usually still loading when the button mounts, and a host with nothing ready yet
   * returns null instead of exporting an empty document.
   */
  buildSource: () => AiExportSource | null;
  /** What the button says it will export, e.g. "Export this trace for AI analysis". */
  tooltip: string;
  disabled?: boolean;
  disabledTooltip?: string;
  /**
   * Whether to offer the AI-export settings link. That page configures a flamegraph prune threshold,
   * which means nothing to a trace, so the item is opt-in rather than always present.
   */
  showSettings?: boolean;
}>();

const router = useRouter();
const { copyToClipboard, downloadAsFile } = useAiExport();

const menuOpen = ref(false);
const busy = ref(false);

function toggleMenu() {
  if (props.disabled) {
    return;
  }
  menuOpen.value = !menuOpen.value;
  if (menuOpen.value) {
    document.addEventListener('click', handleOutsideClick);
  } else {
    document.removeEventListener('click', handleOutsideClick);
  }
}

function closeMenu() {
  menuOpen.value = false;
  document.removeEventListener('click', handleOutsideClick);
}

function handleOutsideClick(event: MouseEvent) {
  const root = wrapperRef.value;
  if (root && !root.contains(event.target as Node)) {
    closeMenu();
  }
}

onBeforeUnmount(() => {
  document.removeEventListener('click', handleOutsideClick);
});

const wrapperRef = ref<HTMLDivElement | null>(null);

async function onCopy() {
  if (props.disabled || busy.value) {
    return;
  }
  const source = props.buildSource();
  if (!source) {
    return;
  }
  busy.value = true;
  try {
    await copyToClipboard(source);
  } finally {
    busy.value = false;
    closeMenu();
  }
}

async function onDownload() {
  if (props.disabled || busy.value) {
    return;
  }
  const source = props.buildSource();
  if (!source) {
    return;
  }
  busy.value = true;
  try {
    await downloadAsFile(source);
  } finally {
    busy.value = false;
    closeMenu();
  }
}

function onOpenSettings() {
  closeMenu();
  router.push({ path: '/settings', hash: '#ai-export' });
}
</script>

<template>
  <div ref="wrapperRef" class="ai-export-wrapper">
    <div
      class="ai-export-split"
      :class="{ 'ai-export-disabled': disabled }"
      :title="disabled ? disabledTooltip : tooltip"
    >
      <button type="button" class="ai-export-main" :disabled="disabled || busy" @click="onCopy">
        <i class="bi bi-stars"></i>
        <span>Copy for AI</span>
      </button>
      <button
        type="button"
        class="ai-export-chev"
        :disabled="disabled || busy"
        :aria-expanded="menuOpen"
        aria-haspopup="menu"
        @click.stop="toggleMenu"
      >
        <i class="bi bi-chevron-down"></i>
      </button>
    </div>

    <div v-if="menuOpen" class="ai-export-menu" role="menu">
      <button class="ai-export-menu-item" role="menuitem" @click="onCopy">
        <i class="bi bi-clipboard"></i>
        <span>Copy to clipboard</span>
      </button>
      <button class="ai-export-menu-item" role="menuitem" @click="onDownload">
        <i class="bi bi-download"></i>
        <span>Download as .md</span>
      </button>
      <div v-if="showSettings" class="ai-export-menu-divider"></div>
      <button
        v-if="showSettings"
        class="ai-export-menu-item ai-export-menu-secondary"
        role="menuitem"
        @click="onOpenSettings"
      >
        <i class="bi bi-gear"></i>
        <span>AI Export settings…</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.ai-export-wrapper {
  position: relative;
  display: inline-block;
  margin-left: 8px;
}

.ai-export-split {
  display: inline-flex;
  border-radius: var(--radius-sm);
  overflow: hidden;
  border: 1px solid var(--color-primary);
}

.ai-export-main,
.ai-export-chev {
  background: var(--color-primary);
  color: var(--color-white);
  border: 0;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  height: 26px;
  transition: background var(--transition-fast);
}

.ai-export-main {
  padding: 0 10px;
  gap: 6px;
}

.ai-export-chev {
  padding: 0 6px;
  border-left: 1px solid rgba(255, 255, 255, 0.3);
}

.ai-export-main:hover:not(:disabled),
.ai-export-chev:hover:not(:disabled) {
  background: var(--color-primary-hover);
}

.ai-export-disabled {
  border-color: var(--color-border);
}

.ai-export-disabled .ai-export-main,
.ai-export-disabled .ai-export-chev {
  background: var(--color-bg-hover);
  color: var(--color-text-muted);
  cursor: not-allowed;
}

.ai-export-disabled .ai-export-chev {
  border-left-color: var(--color-border);
}

.ai-export-main:disabled,
.ai-export-chev:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.ai-export-main i,
.ai-export-chev i {
  font-size: 12px;
}

.ai-export-menu {
  position: absolute;
  right: 0;
  top: calc(100% + 4px);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  min-width: 200px;
  padding: 4px 0;
  z-index: var(--z-dropdown);
}

.ai-export-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 12px;
  background: transparent;
  border: 0;
  color: var(--color-text);
  font-size: 12px;
  text-align: left;
  cursor: pointer;
  transition: background var(--transition-fast);
}

.ai-export-menu-item:hover {
  background: var(--color-bg-hover);
}

.ai-export-menu-item i {
  color: var(--color-text-muted);
  font-size: 12px;
  width: 14px;
}

.ai-export-menu-secondary {
  color: var(--color-text-muted);
  font-size: 11px;
}

.ai-export-menu-divider {
  height: 1px;
  background: var(--color-border-light);
  margin: 4px 0;
}
</style>
