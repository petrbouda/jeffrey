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
  <div class="step-header">
    <div class="step-header-status header-primary clickable-header" @click="expanded = !expanded">
      <div class="step-type-info">
        <i class="bi bi-ui-checks-grid"></i>
        <span>ASYNC-PROFILER AGENT BUILDER</span>
        <span class="header-description"
          >Open to get more information about Async-Profiler Agent Configuration</span
        >
      </div>
      <div class="help-toggle">
        <i :class="expanded ? 'bi bi-chevron-up' : 'bi bi-chevron-down'"></i>
      </div>
    </div>

    <div v-if="expanded" class="step-header-content">
      <div class="help-content">
        <h4>AsyncProfiler Documentation &amp; Resources</h4>
        <p>
          Learn more about AsyncProfiler configuration and profiling techniques from the official
          documentation:
        </p>

        <div class="help-sections">
          <a
            href="https://github.com/async-profiler/async-profiler/blob/master/docs/ProfilingModes.md"
            target="_blank"
            class="help-section help-link-card clickable-card"
          >
            <h5><i class="bi bi-book"></i> Profiling Modes</h5>
            <p>Information about profiling modes</p>
            <div class="source-type">
              <i class="bi bi-file-text"></i>
              <span>Documentation</span>
            </div>
          </a>

          <a
            href="https://github.com/async-profiler/async-profiler/blob/master/docs/ProfilerOptions.md"
            target="_blank"
            class="help-section help-link-card clickable-card"
          >
            <h5><i class="bi bi-gear"></i> Profiler Options</h5>
            <p>Information about Profiling Options</p>
            <div class="source-type">
              <i class="bi bi-file-text"></i>
              <span>Documentation</span>
            </div>
          </a>

          <a
            href="https://github.com/async-profiler/async-profiler/pull/1435"
            target="_blank"
            class="help-section help-link-card clickable-card"
          >
            <h5><i class="bi bi-search"></i> Method Tracing Instrumentation</h5>
            <p>Information about Instrumentation for Method Tracing</p>
            <div class="source-type">
              <i class="bi bi-git"></i>
              <span>Pull Request</span>
            </div>
          </a>

          <a
            href="https://github.com/async-profiler/async-profiler/discussions/1497"
            target="_blank"
            class="help-section help-link-card clickable-card"
          >
            <h5><i class="bi bi-chat-dots"></i> Method Tracing Blog</h5>
            <p>Blog about Method Tracing</p>
            <div class="source-type">
              <i class="bi bi-chat-square-text"></i>
              <span>Discussion</span>
            </div>
          </a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';

const expanded = ref(false);
</script>

<style scoped>
.step-header {
  background: var(--color-white);
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  box-shadow: none;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.step-header-status {
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--color-text-muted);
  transition: all 0.2s ease;
}

.step-header-status.clickable-header {
  cursor: pointer;
  user-select: none;
}

.step-header-status.clickable-header:hover {
  background: var(--color-bg-hover);
  color: var(--color-text);
}

.header-primary {
  background: var(--color-light);
  border-bottom: 1px solid var(--color-border);
}

.step-header-status .step-type-info i {
  color: var(--color-primary);
  opacity: 0.7;
}

.step-type-info {
  display: flex;
  align-items: center;
  gap: 4px;
}

.step-type-info i {
  font-size: 10px;
}

.header-description {
  font-weight: 400;
  font-style: italic;
  opacity: 0.8;
  font-size: 9px;
  margin-left: 8px;
  letter-spacing: 0.3px;
}

.help-toggle {
  display: flex;
  align-items: center;
  font-size: 12px;
  pointer-events: none;
}

.help-toggle i {
  font-size: 10px;
  transition: transform 0.2s ease;
}

.step-header-content {
  padding: 20px 24px;
}

.help-content {
  padding: 0;
}

.help-content h4 {
  color: var(--color-dark);
  font-weight: 700;
  margin-bottom: 12px;
  font-size: 1.1rem;
}

.help-content p {
  color: var(--color-text-muted);
  margin-bottom: 16px;
  line-height: 1.5;
}

.help-sections {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
}

.help-section {
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(94, 100, 255, 0.1);
  border-radius: 8px;
  padding: 16px 16px 40px;
  position: relative;
}

.help-section h5 {
  color: var(--color-text);
  font-weight: 600;
  margin-bottom: 8px;
  font-size: 0.9rem;
  display: flex;
  align-items: center;
  gap: 8px;
}

.help-section h5 i {
  color: var(--color-primary);
}

.help-section p {
  color: var(--color-text-muted);
  font-size: 0.85rem;
  margin: 0;
  line-height: 1.4;
}

.help-link-card {
  transition: all 0.2s ease;
}

.clickable-card {
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  display: block;
}

.clickable-card:hover {
  text-decoration: none;
  color: inherit;
  border-color: rgba(94, 100, 255, 0.25);
  box-shadow: 0 4px 12px rgba(94, 100, 255, 0.15);
}

.clickable-card:hover h5 {
  color: var(--color-primary);
}

.source-type {
  position: absolute;
  bottom: 12px;
  left: 16px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.75rem;
  color: var(--color-text-muted);
  font-weight: 500;
}

.source-type i {
  color: var(--color-text-light);
  font-size: 0.7rem;
}
</style>
