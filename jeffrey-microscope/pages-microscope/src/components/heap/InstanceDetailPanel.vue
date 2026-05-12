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
    <div class="panel-backdrop" @click="closePanel"></div>
    <div class="panel-content">
      <!-- Header -->
      <div class="panel-header">
        <div class="header-title">
          <i class="bi bi-info-circle me-2"></i>
          Instance Details
        </div>
        <button class="btn-icon" @click="closePanel" title="Close panel">
          <i class="bi bi-x-lg"></i>
        </button>
      </div>

      <!-- Navigation Breadcrumb -->
      <div v-if="navigationHistory.length > 0" class="instance-breadcrumb">
        <button class="btn btn-sm btn-outline-secondary me-2" @click="navigateBack" title="Go back">
          <i class="bi bi-arrow-left"></i>
        </button>
        <nav aria-label="Instance navigation">
          <ol class="breadcrumb mb-0">
            <li v-for="(entry, index) in navigationHistory" :key="index" class="breadcrumb-item">
              <a
                href="#"
                @click.prevent="navigateToHistoryEntry(index)"
                class="text-decoration-none"
                :title="entry.className"
              >
                {{ simpleClassName(entry.className) }}
              </a>
            </li>
            <li class="breadcrumb-item active">
              {{ currentClassName }}
            </li>
          </ol>
        </nav>
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
              <span v-if="packageName(instance.className)" class="class-package-name">{{
                packageName(instance.className)
              }}</span>
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
            <code class="value-text">{{
              truncateValue(instance.stringValue ?? instance.value, 200)
            }}</code>
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
            <table class="table table-sm table-hover mb-0">
              <thead>
                <tr>
                  <th>Name</th>
                  <th class="nav-col"></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="field in instance.fields" :key="field.name">
                  <td class="field-name-cell">
                    <div class="field-name-line">
                      <span class="field-name">{{ field.name }}</span>
                      <template v-if="field.referencedObjectId">
                        <span class="field-identity-sep">&middot;</span>
                        <span class="field-object-id">{{
                          FormattingService.formatObjectId(field.referencedObjectId)
                        }}</span>
                      </template>
                    </div>
                    <div class="field-type-line">
                      <code class="field-type-simple">{{
                        simpleType(field.referencedClassName ?? field.type)
                      }}</code>
                      <span
                        v-if="typePackage(field.referencedClassName ?? field.type)"
                        class="field-type-package"
                        >{{ typePackage(field.referencedClassName ?? field.type) }}</span
                      >
                    </div>
                    <div v-if="fieldDisplayValue(field)" class="field-identity-line">
                      <span
                        class="field-value-inline"
                        :class="fieldValueClass(field)"
                        >{{ fieldDisplayValue(field) }}</span
                      >
                    </div>
                  </td>
                  <td class="field-nav-cell">
                    <a
                      v-if="field.referencedObjectId"
                      href="#"
                      class="nav-icon-link"
                      @click.prevent="navigateToInstance(field.referencedObjectId)"
                      title="Navigate to instance"
                    >
                      <i class="bi bi-box-arrow-up-right"></i>
                    </a>
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
            <table class="table table-sm table-hover mb-0">
              <thead>
                <tr>
                  <th>Name</th>
                  <th class="nav-col"></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="field in instance.staticFields" :key="field.name">
                  <td class="field-name-cell">
                    <div class="field-name-line">
                      <span class="field-name">{{ field.name }}</span>
                      <template v-if="field.referencedObjectId">
                        <span class="field-identity-sep">&middot;</span>
                        <span class="field-object-id">{{
                          FormattingService.formatObjectId(field.referencedObjectId)
                        }}</span>
                      </template>
                    </div>
                    <div class="field-type-line">
                      <code class="field-type-simple">{{
                        simpleType(field.referencedClassName ?? field.type)
                      }}</code>
                      <span
                        v-if="typePackage(field.referencedClassName ?? field.type)"
                        class="field-type-package"
                        >{{ typePackage(field.referencedClassName ?? field.type) }}</span
                      >
                    </div>
                    <div v-if="fieldDisplayValue(field)" class="field-identity-line">
                      <span
                        class="field-value-inline"
                        :class="fieldValueClass(field)"
                        >{{ fieldDisplayValue(field) }}</span
                      >
                    </div>
                  </td>
                  <td class="field-nav-cell">
                    <a
                      v-if="field.referencedObjectId"
                      href="#"
                      class="nav-icon-link"
                      @click.prevent="navigateToInstance(field.referencedObjectId)"
                      title="Navigate to instance"
                    >
                      <i class="bi bi-box-arrow-up-right"></i>
                    </a>
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
import type InstanceField from '@/services/api/model/InstanceField';

interface HistoryEntry {
  objectId: number;
  className: string;
}

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

// Navigation history
const navigationHistory = ref<HistoryEntry[]>([]);
const internalObjectId = ref<number | null>(null);

const currentClassName = computed(() => {
  if (instance.value) {
    return simpleClassName(instance.value.className);
  }
  return '';
});

function navigateToInstance(newObjectId: number) {
  if (instance.value) {
    navigationHistory.value.push({
      objectId: instance.value.objectId,
      className: instance.value.className
    });
  }
  internalObjectId.value = newObjectId;
  loadInstanceDetail();
}

function navigateBack() {
  if (navigationHistory.value.length > 0) {
    const previous = navigationHistory.value.pop()!;
    internalObjectId.value = previous.objectId;
    loadInstanceDetail();
  }
}

function navigateToHistoryEntry(index: number) {
  const entry = navigationHistory.value[index];
  navigationHistory.value = navigationHistory.value.slice(0, index);
  internalObjectId.value = entry.objectId;
  loadInstanceDetail();
}

function closePanel() {
  navigationHistory.value = [];
  internalObjectId.value = null;
  emit('close');
}

const simpleType = (fullType: string): string => {
  const lastDot = fullType.lastIndexOf('.');
  return lastDot > 0 ? fullType.substring(lastDot + 1) : fullType;
};

const typePackage = (fullType: string): string => {
  const lastDot = fullType.lastIndexOf('.');
  return lastDot >= 0 ? fullType.substring(0, lastDot) : '';
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

const fieldDisplayValue = (field: InstanceField): string => {
  if (field.isPrimitive) return field.value;
  if (!field.referencedObjectId) return 'null';
  // Object refs: the backend intentionally returns no value (the class
  // name + object id are already rendered on their own rows). If a value
  // is ever present, render it.
  return field.value ? truncateValue(field.value, 60) : '';
};

const fieldValueClass = (field: InstanceField): string => {
  if (field.isPrimitive) return 'primitive-value';
  if (!field.referencedObjectId) return 'null-value';
  return 'reference-value';
};

const isValueTruncated = computed(() => {
  if (!instance.value) return false;
  const fullValue = instance.value.stringValue ?? instance.value.value ?? '';
  return fullValue.length > 200;
});

const copyFullValue = async () => {
  if (!instance.value) return;
  // Prefer stringValue (full value) over the potentially truncated display value
  const valueToCopy = instance.value.stringValue || instance.value.value;
  if (valueToCopy) {
    await navigator.clipboard.writeText(valueToCopy);
    ToastService.success('Copied!', 'Value copied to clipboard');
  }
};

const loadInstanceDetail = async () => {
  const activeObjectId = internalObjectId.value;
  if (!props.client || !activeObjectId) return;

  loading.value = true;
  error.value = null;

  try {
    instance.value = await props.client.getInstanceDetail(activeObjectId, true);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load instance details';
    console.error('Error loading instance details:', err);
  } finally {
    loading.value = false;
  }
};

// When the prop objectId changes from outside, reset history and sync internal state
watch(
  [() => props.isOpen, () => props.objectId],
  async ([isOpen, objectId]) => {
    if (isOpen && objectId) {
      navigationHistory.value = [];
      internalObjectId.value = objectId;
      await loadInstanceDetail();
    } else if (!isOpen) {
      instance.value = null;
      navigationHistory.value = [];
      internalObjectId.value = null;
    }
  },
  { immediate: true }
);
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
  width: 680px;
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
  border-bottom: 1px solid var(--color-border);
  background-color: var(--color-light);
}

.header-title {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--color-text);
}

.btn-icon {
  background: none;
  border: none;
  color: var(--color-text-muted);
  padding: 0.375rem;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.btn-icon:hover {
  background-color: var(--color-border);
  color: var(--color-dark);
}

.instance-breadcrumb {
  display: flex;
  align-items: center;
  padding: 0.5rem 0.75rem;
  background-color: var(--color-light);
  border-bottom: 1px solid var(--color-border);
  font-size: 0.8rem;
  min-height: 0;
}

.instance-breadcrumb .btn {
  padding: 0.15rem 0.4rem;
  font-size: 0.7rem;
  line-height: 1;
  flex-shrink: 0;
}

.instance-breadcrumb .breadcrumb {
  font-size: 0.75rem;
  flex-wrap: nowrap;
  overflow: hidden;
}

.instance-breadcrumb .breadcrumb-item {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 140px;
}

.instance-breadcrumb .breadcrumb-item a {
  color: var(--color-primary);
}

.instance-breadcrumb .breadcrumb-item a:hover {
  color: var(--color-primary-hover);
  text-decoration: underline !important;
}

.instance-breadcrumb .breadcrumb-item.active {
  color: var(--color-text);
  font-weight: 600;
}

.loading-state,
.error-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  color: var(--color-text-muted);
}

.error-state {
  color: var(--color-danger);
}

.panel-body {
  flex: 1;
  overflow-y: auto;
}

.instance-info {
  padding: 1rem;
  background-color: var(--color-light);
  border-bottom: 1px solid var(--color-border);
}

.info-row {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  padding: 0.25rem 0;
}

.info-row .label {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  min-width: 90px;
}

.info-row .value {
  font-size: 0.8rem;
  color: var(--color-dark);
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
  color: var(--color-dark);
  word-break: break-all;
}

.class-package-name {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.info-row .value.monospace {
}

.value-section {
  border-bottom: 1px solid var(--color-border);
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
  background-color: var(--color-light);
}

.value-text {
  font-size: 0.75rem;
  color: var(--color-dark);
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
  color: var(--color-warning-text);
  background-color: var(--color-warning-bg);
  padding: 0.1rem 0.35rem;
  border-radius: 3px;
  vertical-align: middle;
}

.fields-section,
.static-fields-section {
  border-bottom: 1px solid var(--color-border);
}

.section-header {
  display: flex;
  align-items: center;
  padding: 0.75rem 1rem;
  background-color: var(--color-light);
  border-bottom: 1px solid var(--color-border);
}

.section-header h6 {
  margin: 0;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-text);
}

.table-container {
}

.table {
  margin: 0;
}

.table th {
  position: sticky;
  top: 0;
  z-index: 1;
}

.table td {
  font-size: 0.75rem;
  padding: 0.4rem 0.75rem;
  vertical-align: top;
  border-bottom: 1px solid var(--color-border-row);
}

.field-name-cell {
  color: var(--color-text);
}

.field-type-line {
  display: flex;
  align-items: baseline;
  gap: 0.35rem;
  font-size: 0.75rem;
  margin-top: 1px;
}

.field-identity-line {
  display: flex;
  align-items: baseline;
  gap: 0.25rem;
  margin-top: 1px;
}

.field-object-id {
  font-size: 0.7rem;
  font-family: monospace;
  color: var(--color-text-muted);
}

.field-identity-sep {
  color: var(--color-text-light);
  font-size: 0.85rem;
}

.field-name-line {
  display: flex;
  align-items: baseline;
  gap: 0.3rem;
}

.field-name {
  font-size: 0.75rem;
  color: var(--color-purple);
  font-style: italic;
  white-space: nowrap;
}

.field-type-simple {
  font-size: 0.75rem;
  font-weight: 600;
  background-color: transparent;
  color: var(--color-text);
  white-space: nowrap;
}

.field-type-package {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.field-value-inline {
  font-size: 0.75rem;
  word-break: break-word;
}

.primitive-value {
  color: var(--color-dark);
}

.reference-value {
  color: var(--color-text);
}

.null-value {
  color: var(--color-text-muted);
  font-style: italic;
}

.nav-col {
  width: 28px;
}

.field-nav-cell {
  width: 28px;
  text-align: center;
  vertical-align: middle;
}

.nav-icon-link {
  color: var(--color-text-muted);
  text-decoration: none;
  font-size: 0.7rem;
  transition: color 0.2s ease;
}

.nav-icon-link:hover {
  color: var(--color-accent-blue);
}

.empty-section {
  padding: 1rem;
  text-align: center;
  color: var(--color-text-muted);
  font-size: 0.8rem;
}
</style>
