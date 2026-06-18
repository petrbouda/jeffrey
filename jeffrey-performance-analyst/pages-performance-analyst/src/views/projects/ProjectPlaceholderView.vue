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
import { useRoute } from 'vue-router';

const route = useRoute();
const projectId = route.params.projectId as string;
</script>

<template>
  <div class="placeholder-card">
    <div class="placeholder-icon"><i class="bi bi-rocket-takeoff"></i></div>
    <h1 class="h4 mb-2">Project analysis — coming soon</h1>
    <p class="text-muted mb-4">
      This is the Performance Analyst's project page. It will diverge from Microscope and host the
      analyst-specific analysis. Project: <code>{{ projectId }}</code>
    </p>
    <router-link to="/workspaces" class="btn btn-primary">
      <i class="bi bi-arrow-left me-1"></i> Back to Workspaces
    </router-link>
  </div>
</template>

<style scoped>
.placeholder-card {
  background-color: var(--color-bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-base);
  padding: 3rem 2rem;
  text-align: center;
  max-width: 640px;
  margin: 2rem auto;
}

.placeholder-icon {
  font-size: 2.5rem;
  color: var(--color-primary);
  margin-bottom: 1rem;
}
</style>
