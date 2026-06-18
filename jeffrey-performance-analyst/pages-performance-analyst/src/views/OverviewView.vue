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
  <div class="main-card">
    <div class="page-header">
      <div class="page-header-info">
        <i class="page-header-icon bi bi-speedometer2"></i>
        <span class="page-header-title">Overview</span>
      </div>
    </div>

    <div class="main-card-content">
      <p class="text-muted mb-4">
        Welcome to the Jeffrey Performance Analyst. This is the starting point — features will be
        added here.
      </p>

      <div class="feature-grid">
        <div class="feature-tile">
          <i class="bi bi-fire"></i>
          <h6>Flamegraphs</h6>
          <p>Visualize where CPU time and allocations go.</p>
        </div>
        <div class="feature-tile">
          <i class="bi bi-graph-up"></i>
          <h6>Timeseries</h6>
          <p>Track metrics across the recording timeline.</p>
        </div>
        <div class="feature-tile">
          <i class="bi bi-shield-check"></i>
          <h6>Guardians</h6>
          <p>Automated checks that flag performance issues.</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts"></script>

<style scoped>
.main-card {
  background-color: var(--color-bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-base);
  margin-bottom: var(--spacing-6);
  overflow: hidden;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid var(--color-border);
}

.page-header-info {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.page-header-icon {
  font-size: 1.25rem;
  color: var(--color-primary);
}

.page-header-title {
  font-size: 1.05rem;
  font-weight: 600;
  color: var(--color-dark);
}

.main-card-content {
  padding: 1.5rem;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1rem;
}

.feature-tile {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 1.25rem;
  background-color: var(--color-light);
  transition: all 0.2s ease;
}

.feature-tile:hover {
  border-color: var(--color-primary-border);
  box-shadow: var(--shadow-base);
  transform: translateY(-2px);
}

.feature-tile i {
  font-size: 1.5rem;
  color: var(--color-primary);
}

.feature-tile h6 {
  margin: 0.75rem 0 0.35rem;
  font-weight: 600;
  color: var(--color-dark);
}

.feature-tile p {
  margin: 0;
  font-size: 0.85rem;
  color: var(--color-text-muted);
}
</style>
