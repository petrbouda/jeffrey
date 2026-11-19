<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
  <div class="main-container">
    <AppTopbar />
    <div class="content-container">
      <div class="w-100">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import AppTopbar from './AppTopbar.vue';
</script>

<style lang="scss" scoped>
.main-container {
  display: flex;
  flex-direction: column;
}

.content-container {
  min-height: calc(100vh - 72px); // Hero topbar ≈ 66px tall + 6px buffer
  background-color: var(--color-bg-body);
  padding: 1rem;
}
</style>
