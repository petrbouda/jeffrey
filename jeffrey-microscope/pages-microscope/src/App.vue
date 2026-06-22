<template>
  <div class="app-container">
    <router-view />

    <!-- Container for minimized assistant buttons (rendered in a row) -->
    <AssistantMinimizedContainer />

    <!-- Global source viewer (opened from flamegraph "View Source") -->
    <SourceViewerModal />

    <!-- Global IDE target picker (shown when a profile's IDE window is ambiguous) -->
    <IdeTargetPickerModal />

    <!-- Global Download Assistant -->
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
import { onMounted } from 'vue';
import { DownloadAssistant, AssistantMinimizedContainer } from '@/components/assistants';
import SourceViewerModal from '@/components/SourceViewerModal.vue';
import IdeTargetPickerModal from '@/components/IdeTargetPickerModal.vue';
import { downloadAssistantStore as downloadStore } from '@/stores/assistants';
import VersionClient from '@/services/api/VersionClient';
import { showUpdateCheckToast } from '@/services/UpdateCheckToast';
import AiProviderDetectClient from '@/services/api/AiProviderDetectClient';
import { showClaudeCodeEnableToast } from '@/services/ClaudeCodeEnableToast';

onMounted(async () => {
  // Initialize Bootstrap tooltips
  if (window.bootstrap && window.bootstrap.Tooltip) {
    const tooltipTriggerList = document.querySelectorAll('[data-bs-toggle="tooltip"]');
    [...tooltipTriggerList].map(tooltipTriggerEl => new window.bootstrap.Tooltip(tooltipTriggerEl));
  }

  // Version update check
  try {
    const result = await new VersionClient().checkForUpdate();
    if (result?.updateAvailable) {
      showUpdateCheckToast(result);
    }
  } catch {
    // silently ignore update check failures
  }

  // Offer to enable Claude Code when its CLI is installed but no AI provider is configured.
  try {
    const detect = await new AiProviderDetectClient().detect();
    if (detect?.claudeCodeDetected && !detect.aiConfigured) {
      showClaudeCodeEnableToast();
    }
  } catch {
    // silently ignore detection failures
  }
});
</script>

<style>
/* Global styles */
.app-container {
  min-height: 100vh;
  background-color: var(--color-bg-body);
}

/* Custom scrollbar */
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-track {
  background: var(--color-light);
}

::-webkit-scrollbar-thumb {
  background: var(--color-text-light);
  border-radius: 4px;
}

::-webkit-scrollbar-thumb:hover {
  background: var(--color-text-muted);
}
</style>
