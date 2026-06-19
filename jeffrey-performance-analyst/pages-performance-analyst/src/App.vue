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
  <div class="app-container">
    <router-view />

    <!-- Global Download Assistant (floating progress panel for remote recording downloads) -->
    <DownloadAssistant
      :is-open="downloadStore.isOpen.value"
      :is-expanded="downloadStore.isExpanded.value"
      :downloads="downloadStore.allDownloads.value"
      :aggregate-progress="downloadStore.aggregateProgress.value"
      :aggregate-status="downloadStore.aggregateStatus.value"
      :has-active-downloads="downloadStore.hasActiveDownloads.value"
      @expand="downloadStore.expand"
      @minimize="downloadStore.minimize"
      @close="downloadStore.closePanel"
      @cancel-download="downloadStore.cancelDownload"
      @close-download="downloadStore.closeDownload"
    />
  </div>
</template>

<script setup lang="ts">
import DownloadAssistant from '@workspaces/components/assistants/global/DownloadAssistant.vue';
import { downloadAssistantStore as downloadStore } from '@workspaces/stores/assistants/downloadAssistantStore';
</script>

<style>
.app-container {
  min-height: 100vh;
  background-color: var(--color-bg-body);
}
</style>
