<!--
  ~ Jeffrey
  ~ Copyright (C) 2025 Petr Bouda
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <div class="init-required-card">
    <div class="init-icon">
      <i :class="`bi bi-${icon}`"></i>
    </div>
    <h5>Heap Dump Not Initialized</h5>
    <p>{{ message }}</p>
    <router-link
        :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/heap-dump/settings`"
        class="btn btn-primary"
    >
      <i class="bi bi-arrow-right me-2"></i>
      Go to Heap Dump Overview
    </router-link>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';

withDefaults(defineProps<{
  icon?: string;
  message?: string;
}>(), {
  icon: 'cpu',
  message: 'The heap dump needs to be initialized before you can use this feature.'
});

const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const profileId = route.params.profileId as string;
</script>

<style scoped>
.init-required-card {
  background: white;
  border: 1px solid #dee2e6;
  border-radius: 8px;
  padding: 3rem 2rem;
  text-align: center;
  max-width: 480px;
  margin: 2rem auto;
}

.init-icon {
  width: 56px;
  height: 56px;
  margin: 0 auto 1.25rem;
  background-color: rgba(111, 66, 193, 0.1);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.init-icon i {
  font-size: 1.5rem;
  color: #6f42c1;
}

.init-required-card h5 {
  font-size: 1.125rem;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 0.5rem;
}

.init-required-card p {
  color: #64748b;
  font-size: 0.875rem;
  line-height: 1.6;
  margin-bottom: 1.5rem;
  max-width: 360px;
  margin-left: auto;
  margin-right: auto;
}
</style>
