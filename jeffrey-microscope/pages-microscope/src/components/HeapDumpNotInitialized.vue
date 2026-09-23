<!--
  ~ Jeffrey
  ~ Copyright (C) 2025 Petr Bouda
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     https://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<template>
  <div class="init-required-card">
    <div class="init-icon">
      <i :class="`bi bi-${icon}`"></i>
    </div>
    <h5>Heap Dump Not Initialized</h5>
    <p>{{ message }}</p>
    <router-link :to="`/profiles/${profileId}/heap-dump/overview`" class="btn btn-primary">
      <i class="bi bi-arrow-right me-2"></i>
      Go to Heap Dump Overview
    </router-link>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router';

withDefaults(
  defineProps<{
    icon?: string;
    message?: string;
  }>(),
  {
    icon: 'cpu',
    message: 'The heap dump needs to be initialized before you can use this feature.'
  }
);

const route = useRoute();
const profileId = route.params.profileId as string;
</script>

<style scoped>
.init-required-card {
  background: white;
  border: 1px solid var(--color-border);
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
  color: var(--color-purple);
}

.init-required-card h5 {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--color-heading-dark);
  margin-bottom: 0.5rem;
}

.init-required-card p {
  color: var(--color-text-muted);
  font-size: 0.875rem;
  line-height: 1.6;
  margin-bottom: 1.5rem;
  max-width: 360px;
  margin-left: auto;
  margin-right: auto;
}
</style>
