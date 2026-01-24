<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
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
  <Teleport to="body">
    <div
        v-if="isOpen && isExpanded"
        class="assistant-panel"
        :style="panelStyles"
    >
      <!-- Optional Backdrop -->
      <div
          v-if="showBackdrop"
          class="panel-backdrop"
          :class="{ visible: isExpanded }"
          @click="$emit('close')"
          @dragover.prevent
          @drop.prevent
      ></div>

      <div class="panel-content" :style="contentStyles">
        <!-- Header -->
        <div class="panel-header" :style="headerStyles">
          <div class="header-title">
            <slot name="header-icon"></slot>
            <slot name="header-title"></slot>
          </div>
          <div class="header-actions">
            <slot name="header-actions"></slot>
          </div>
        </div>

        <!-- Body -->
        <div class="panel-body">
          <slot name="body"></slot>
        </div>

        <!-- Footer (optional) -->
        <div v-if="$slots.footer" class="panel-footer">
          <slot name="footer"></slot>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed } from 'vue';

interface Props {
  isOpen: boolean;
  isExpanded: boolean;
  width?: string;
  headerGradient?: string;
  showBackdrop?: boolean;
  zIndex?: number;
}

const props = withDefaults(defineProps<Props>(), {
  width: '480px',
  headerGradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  showBackdrop: false,
  zIndex: 1040
});

defineEmits<{
  (e: 'close'): void;
}>();

const panelStyles = computed(() => ({
  zIndex: props.zIndex
}));

const contentStyles = computed(() => ({
  width: props.width,
  maxWidth: '100%'
}));

const headerStyles = computed(() => ({
  background: props.headerGradient
}));
</script>

<style scoped>
/* Panel Container */
.assistant-panel {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
}

/* Backdrop */
.panel-backdrop {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.3);
  opacity: 0;
  transition: opacity 0.3s ease;
  pointer-events: auto;
}

.panel-backdrop.visible {
  opacity: 1;
}

/* Panel Content */
.panel-content {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  background: white;
  box-shadow: -4px 0 20px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  transform: translateX(0);
  animation: slideIn 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  pointer-events: auto;
}

@keyframes slideIn {
  from {
    transform: translateX(100%);
  }
  to {
    transform: translateX(0);
  }
}

/* Header */
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  color: white;
  flex-shrink: 0;
}

.header-title {
  font-weight: 600;
  font-size: 0.95rem;
  display: flex;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 0.25rem;
}

/* Shared button styles for header actions */
.panel-header :deep(.btn-icon) {
  background: rgba(255, 255, 255, 0.2);
  border: none;
  color: white;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.panel-header :deep(.btn-icon:hover:not(:disabled)) {
  background: rgba(255, 255, 255, 0.3);
}

.panel-header :deep(.btn-icon:disabled) {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Body */
.panel-body {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

/* Footer */
.panel-footer {
  flex-shrink: 0;
  border-top: 1px solid #e9ecef;
  background-color: #f8f9fa;
}
</style>
