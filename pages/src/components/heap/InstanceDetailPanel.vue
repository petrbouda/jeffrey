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
  <div class="detail-panel" :class="{ open: isOpen }">
    <div class="panel-backdrop" @click="$emit('close')"></div>
    <div class="panel-content">
      <!-- Header -->
      <div class="panel-header">
        <div class="header-title">
          <i class="bi bi-info-circle me-2"></i>
          Instance Details
        </div>
        <button class="btn-icon" @click="$emit('close')" title="Close panel">
          <i class="bi bi-x-lg"></i>
        </button>
      </div>

      <!-- Loading State -->
      <div v-if="loading" class="loading-state">
        <span class="spinner-border spinner-border-sm me-2"></span>
        Loading instance details...
      </div>

      <!-- Error State -->
      <div v-else-if="error" class="error-state">
        <i class="bi bi-exclamation-triangle me-2"></i>
        {{ error }}
      </div>

      <!-- Content -->
      <div v-else-if="instance" class="panel-body">
        <!-- Instance Info -->
        <div class="instance-info">
          <div class="info-row info-row-class">
            <span class="label">Class:</span>
            <div class="class-name-block">
              <span class="class-simple-name">{{ simpleClassName(instance.className) }}</span>
              <span v-if="packageName(instance.className)" class="class-package-name">{{ packageName(instance.className) }}</span>
            </div>
          </div>
          <div class="info-row">
            <span class="label">Object ID:</span>
            <span class="value monospace">{{ instance.objectId }}</span>
          </div>
          <div class="info-row">
            <span class="label">Shallow Size:</span>
            <span class="value monospace">{{
              FormattingService.formatBytes(instance.shallowSize)
            }}</span>
          </div>
          <div v-if="instance.retainedSize" class="info-row">
            <span class="label">Retained Size:</span>
            <span class="value monospace">{{
              FormattingService.formatBytes(instance.retainedSize)
            }}</span>
          </div>
        </div>

        <!-- Value Section -->
        <div v-if="instance.value || instance.stringValue" class="value-section">
          <div class="section-header">
            <h6>
              <i class="bi bi-card-text me-1"></i>
              Value
              <span v-if="isValueTruncated" class="truncated-badge ms-2">truncated</span>
            </h6>
            <div class="value-actions">
              <button
                class="btn btn-sm btn-outline-secondary"
                @click="copyFullValue"
                title="Copy full value to clipboard"
              >
                <i class="bi bi-clipboard me-1"></i>
                Copy
              </button>
            </div>
          </div>
          <div class="value-content">
            <code class="value-text">{{ instance.value }}</code>
          </div>
        </div>

        <!-- Fields Section -->
        <div class="fields-section">
          <div class="section-header">
            <h6>
              <i class="bi bi-list-ul me-1"></i>
              Fields ({{ instance.fields.length }})
            </h6>
          </div>
          <div class="table-container" v-if="instance.fields.length > 0">
            <table class="table table-sm">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Type</th>
                  <th>Value</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="field in instance.fields" :key="field.name">
                  <td class="field-name-cell">{{ field.name }}</td>
                  <td class="field-type-cell">
                    <code>{{ simpleType(field.type) }}</code>
                  </td>
                  <td class="field-value-cell">
                    <span v-if="field.isPrimitive" class="primitive-value">{{ field.value }}</span>
                    <a
                      v-else-if="field.referencedObjectId"
                      href="#"
                      class="reference-link"
                      @click.prevent="$emit('navigate', field.referencedObjectId)"
                      :title="'Navigate to ' + field.type"
                    >
                      {{ truncateValue(field.value, 60) }}
                    </a>
                    <span v-else class="null-value">null</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="empty-section">No instance fields</div>
        </div>

        <!-- Static Fields Section -->
        <div v-if="instance.staticFields.length > 0" class="static-fields-section">
          <div class="section-header">
            <h6>
              <i class="bi bi-lock me-1"></i>
              Static Fields ({{ instance.staticFields.length }})
            </h6>
          </div>
          <div class="table-container">
            <table class="table table-sm">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Type</th>
                  <th>Value</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="field in instance.staticFields" :key="field.name">
                  <td class="field-name-cell">{{ field.name }}</td>
                  <td class="field-type-cell">
                    <code>{{ simpleType(field.type) }}</code>
                  </td>
                  <td class="field-value-cell">
                    <span v-if="field.isPrimitive" class="primitive-value">{{ field.value }}</span>
                    <a
                      v-else-if="field.referencedObjectId"
                      href="#"
                      class="reference-link"
                      @click.prevent="$emit('navigate', field.referencedObjectId)"
                    >
                      {{ truncateValue(field.value, 60) }}
                    </a>
                    <span v-else class="null-value">null</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import FormattingService from '@/services/FormattingService';
import ToastService from '@/services/ToastService';
import type HeapDumpClient from '@/services/api/HeapDumpClient';
import type InstanceDetail from '@/services/api/model/InstanceDetail';

interface Props {
  isOpen: boolean;
  objectId: number | null;
  client: HeapDumpClient | null;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'navigate', objectId: number): void;
}>();

const loading = ref(false);
const error = ref<string | null>(null);
const instance = ref<InstanceDetail | null>(null);

const simpleType = (fullType: string): string => {
  const lastDot = fullType.lastIndexOf('.');
  return lastDot > 0 ? fullType.substring(lastDot + 1) : fullType;
};

const simpleClassName = (name: string): string => {
  const lastDot = name.lastIndexOf('.');
  return lastDot >= 0 ? name.substring(lastDot + 1) : name;
};

const packageName = (name: string): string => {
  const lastDot = name.lastIndexOf('.');
  return lastDot >= 0 ? name.substring(0, lastDot) : '';
};

const truncateValue = (value: string, maxLen: number): string => {
  if (!value || value.length <= maxLen) return value;
  return value.substring(0, maxLen) + '...';
};

const isValueTruncated = computed(() => {
  if (!instance.value) return false;
  const displayValue = instance.value.value || '';
  const fullValue = instance.value.stringValue || '';
  // Check if stringValue is longer than the display value (accounting for quotes)
  return fullValue.length > 0 && fullValue.length > displayValue.length - 2;
});

const copyFullValue = async () => {
  if (!instance.value) return;
  // Prefer stringValue (full value) over the potentially truncated display value
  const valueToCopy = instance.value.stringValue || instance.value.value;
  if (valueToCopy) {
    await navigator.clipboard.writeText(valueToCopy);
    const preview =
      valueToCopy.length > 50
        ? valueToCopy.substring(0, 50) + '...'
        : valueToCopy;
    ToastService.success(`Copied ${valueToCopy.length} chars: ${preview}`);
  }
};

const loadInstanceDetail = async () => {
  if (!props.client || !props.objectId) return;

  loading.value = true;
  error.value = null;

  try {
    instance.value = await props.client.getInstanceDetail(props.objectId, false);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load instance details';
    console.error('Error loading instance details:', err);
  } finally {
    loading.value = false;
  }
};

// Load instance details when panel opens or objectId changes
watch([() => props.isOpen, () => props.objectId], async ([isOpen, objectId]) => {
  if (isOpen && objectId) {
    await loadInstanceDetail();
  } else if (!isOpen) {
    instance.value = null;
  }
}, { immediate: true });
</script>

<style scoped>
.detail-panel {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1060;
  pointer-events: none;
}

.detail-panel.open {
  pointer-events: auto;
}

.panel-backdrop {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.2);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.detail-panel.open .panel-backdrop {
  opacity: 1;
}

.panel-content {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 480px;
  max-width: 100%;
  background: white;
  box-shadow: -4px 0 20px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  transform: translateX(100%);
  transition: transform 0.3s ease;
}

.detail-panel.open .panel-content {
  transform: translateX(0);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #e9ecef;
  background-color: #f8f9fa;
}

.header-title {
  font-weight: 600;
  font-size: 0.9rem;
  color: #495057;
}

.btn-icon {
  background: none;
  border: none;
  color: #6c757d;
  padding: 0.375rem;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.btn-icon:hover {
  background-color: #e9ecef;
  color: #212529;
}

.loading-state,
.error-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  color: #6c757d;
}

.error-state {
  color: #dc3545;
}

.panel-body {
  flex: 1;
  overflow-y: auto;
}

.instance-info {
  padding: 1rem;
  background-color: #f8f9fa;
  border-bottom: 1px solid #e9ecef;
}

.info-row {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  padding: 0.25rem 0;
}

.info-row .label {
  font-size: 0.75rem;
  color: #6c757d;
  min-width: 90px;
}

.info-row .value {
  font-size: 0.8rem;
  color: #212529;
}

.info-row-class {
  align-items: flex-start;
}

.class-name-block {
  display: flex;
  flex-direction: column;
}

.class-simple-name {
  font-size: 0.85rem;
  font-weight: 600;
  color: #343a40;
  word-break: break-all;
}

.class-package-name {
  font-size: 0.8rem;
  color: #adb5bd;
  word-break: break-all;
}

.info-row .value.monospace {
}

.value-section {
  border-bottom: 1px solid #e9ecef;
}

.value-section .section-header {
  justify-content: space-between;
}

.value-actions {
  display: flex;
  gap: 0.5rem;
}

.value-actions .btn {
  padding: 0.2rem 0.5rem;
  font-size: 0.7rem;
}

.value-content {
  padding: 0.75rem 1rem;
  background-color: #fafbfc;
}

.value-text {
  font-size: 0.75rem;
  color: #212529;
  background: transparent;
  word-break: break-all;
  white-space: pre-wrap;
  display: block;
}

.truncated-badge {
  font-size: 0.6rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  color: #856404;
  background-color: #fff3cd;
  padding: 0.1rem 0.35rem;
  border-radius: 3px;
  vertical-align: middle;
}

.fields-section,
.static-fields-section {
  border-bottom: 1px solid #e9ecef;
}

.section-header {
  display: flex;
  align-items: center;
  padding: 0.75rem 1rem;
  background-color: #f8f9fa;
  border-bottom: 1px solid #e9ecef;
}

.section-header h6 {
  margin: 0;
  font-size: 0.8rem;
  font-weight: 600;
  color: #495057;
}

.table-container {
  max-height: 400px;
  overflow-y: auto;
}

.table {
  margin: 0;
}

.table th {
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  color: #6c757d;
  background-color: #fafbfc;
  padding: 0.5rem 0.75rem;
  border-bottom: 1px solid #e9ecef;
  position: sticky;
  top: 0;
}

.table td {
  font-size: 0.75rem;
  padding: 0.4rem 0.75rem;
  vertical-align: top;
  border-bottom: 1px solid #f0f0f0;
}

.field-name-cell {
  font-weight: 500;
  color: #495057;
}

.field-type-cell code {
  font-size: 0.7rem;
  color: #6c757d;
  background: transparent;
}

.field-value-cell {
  word-break: break-word;
}

.primitive-value {
  color: #212529;
}

.reference-link {
  color: #0d6efd;
  text-decoration: none;
}

.reference-link:hover {
  text-decoration: underline;
}

.null-value {
  color: #6c757d;
  font-style: italic;
}

.empty-section {
  padding: 1rem;
  text-align: center;
  color: #6c757d;
  font-size: 0.8rem;
}
</style>
