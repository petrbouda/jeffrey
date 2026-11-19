<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
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
  <Teleport to="body">
    <div id="assistant-minimized-container" class="assistant-minimized-container">
      <!-- Minimized buttons will be teleported here -->
    </div>
  </Teleport>
</template>

<script setup lang="ts">
// This component creates a fixed container at the bottom-right corner
// where all minimized assistant buttons are rendered in a row
</script>

<style>
/* Global styles (not scoped) so teleported children can use the container */
.assistant-minimized-container {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 1040;
  display: flex;
  flex-direction: row-reverse;
  gap: 12px;
  align-items: flex-end;
  pointer-events: none;
}

.assistant-minimized-container > * {
  pointer-events: auto;
}
</style>
