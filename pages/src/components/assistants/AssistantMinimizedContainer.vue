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
