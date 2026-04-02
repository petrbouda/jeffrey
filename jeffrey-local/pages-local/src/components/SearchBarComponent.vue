<!--
  - Jeffrey
  - Copyright (C) 2024 Petr Bouda
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
import { onMounted, ref } from 'vue';
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater';
import Utils from '@/services/Utils';
import '@/styles/shared-components.css';

const props = withDefaults(
  defineProps<{
    graphUpdater: GraphUpdater;
    withTimeseries: boolean;
    showModeControls?: boolean;
    threadModeLabel?: string;
    weightModeLabel?: string;
    initialThreadMode?: boolean;
    initialUseWeight?: boolean;
  }>(),
  {
    showModeControls: false,
    threadModeLabel: 'Thread Mode',
    weightModeLabel: 'Use Weight',
    initialThreadMode: false,
    initialUseWeight: true
  }
);

const emit = defineEmits<{
  (e: 'modeChange', useThreadMode: boolean, useWeight: boolean): void;
}>();

const searchValue = ref<string | null>(null);
const searchMatched = ref<string | null>(null);
const isLoading = ref(false);

// Mode toggles
const useThreadMode = ref(props.initialThreadMode);
const useWeight = ref(props.initialUseWeight);

function onModeChange() {
  props.graphUpdater.updateModes(useThreadMode.value, useWeight.value);
  emit('modeChange', useThreadMode.value, useWeight.value);
}

onMounted(() => {
  props.graphUpdater.registerSearchBarCallbacks(
    () => (isLoading.value = true),
    () => (isLoading.value = false),
    (matched: string | null) => (searchMatched.value = matched)
  );
});

function search() {
  if (Utils.isNotBlank(searchValue.value)) {
    props.graphUpdater.updateWithSearch(searchValue.value!.trim());
  }
}

function resetSearch() {
  searchValue.value = null;
  props.graphUpdater.resetSearch();
}

function resetTimeseriesZoom() {
  props.graphUpdater.resetTimeseriesZoom();
}
</script>

<template>
  <div class="search-panel mb-2">
    <div class="row align-items-center">
      <div class="col-6 d-flex align-items-center" style="padding-right: 0">
        <!-- Timeseries controls -->
        <template v-if="withTimeseries">
          <button class="icon-btn me-2" title="Reset Zoom" @click="resetTimeseriesZoom()">
            <i class="bi bi-arrows-angle-expand"></i>
          </button>
        </template>

        <!-- Mode controls -->
        <template v-if="showModeControls">
          <div class="mode-controls">
            <div class="mode-toggle">
              <span class="mode-label">{{ threadModeLabel }}</span>
              <label class="toggle-switch">
                <input
                  type="checkbox"
                  class="toggle-input"
                  v-model="useThreadMode"
                  @change="onModeChange()"
                />
                <span class="toggle-slider"></span>
              </label>
            </div>
            <div class="mode-toggle">
              <span class="mode-label">{{ weightModeLabel }}</span>
              <label class="toggle-switch">
                <input
                  type="checkbox"
                  class="toggle-input"
                  v-model="useWeight"
                  @change="onModeChange()"
                />
                <span class="toggle-slider"></span>
              </label>
            </div>
          </div>
        </template>

        <!-- Spacer to push loading and matched to the right -->
        <div class="flex-grow-1"></div>

        <div class="d-flex align-items-center me-3" v-if="isLoading">
          <div
            class="spinner-border spinner-border-sm text-primary"
            style="height: 18px; width: 18px"
            role="status"
          >
            <span class="visually-hidden">Loading...</span>
          </div>
        </div>
        <span
          class="matched-badge"
          @click="resetSearch()"
          v-if="searchMatched != null"
          title="Click to reset search"
        >
          {{ searchMatched }}%
        </span>
      </div>

      <div class="col-6 d-flex">
        <div class="input-group">
          <input
            type="text"
            class="form-control"
            v-model="searchValue"
            @keydown.enter="search"
            placeholder="Search"
          />
          <button class="btn btn-primary d-flex align-items-center" @click="search()">
            <i class="bi bi-arrow-right"></i>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Search panel styling - glass morphism */
.search-panel {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
  padding: 5px 16px;
  padding-bottom: 10px;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
}

.panel-label {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--color-text);
}

.search-panel .form-control {
  background: rgba(255, 255, 255, 0.8);
  border-color: rgba(0, 0, 0, 0.15);
}

.search-panel .form-control:focus {
  background: rgba(255, 255, 255, 0.95);
  border-color: rgba(0, 0, 0, 0.2) !important;
  box-shadow: none !important;
}

/* Fix for equal height of button and input */
.input-group {
  display: flex;
  align-items: stretch;
}

.input-group .btn,
.input-group .form-control,
.input-group .input-group-text {
  height: var(--input-height-sm);
  line-height: 1.5;
  border-radius: 5px;
}

.input-group .btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 0;
  padding-bottom: 0;
}

/* Matched percentage badge */
.matched-badge {
  background-color: #fad4fa;
  color: #af00af;
  border: 1px solid #cc00cc;
  border-radius: 5px;
  cursor: pointer;
  font-weight: 500;
  font-size: 0.85rem;
  height: var(--input-height-sm);
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  margin-right: 2px;
}

.matched-badge:hover {
  background-color: #a800a8;
  border-color: #a800a8;
  color: #ffffff;
}

/* Remove blue border and shadow from search input on focus */
.input-group .form-control:focus {
  border-color: var(--input-border-color) !important;
  box-shadow: none !important;
}

/* Timeseries control buttons */
.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--card-border-color);
  background-color: var(--input-bg);
  color: var(--color-text-muted);
  border-radius: 4px;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.icon-btn:hover {
  background-color: var(--color-light);
  border-color: var(--card-border-color);
  color: var(--color-text);
}

/* Mode controls - Modern toggle switches */
.mode-controls {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-left: 12px;
  padding-left: 12px;
  border-left: 1px solid rgba(0, 0, 0, 0.08);
}

.mode-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.mode-label {
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--color-text-muted);
  white-space: nowrap;
}
</style>
