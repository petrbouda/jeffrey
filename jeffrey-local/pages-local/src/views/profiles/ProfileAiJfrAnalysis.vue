<template>
  <PageHeader
    title="JFR Analysis"
    description="Ask questions about your JFR profile and get AI-powered insights"
    icon="bi-activity"
  >
    <AiDisabledFeatureAlert v-if="status && !isAvailable" />
    <AiAnalysisPanel
      v-else
      ref="panelRef"
      :is-loading="isLoading"
      :error="error"
      :status="status"
      :messages="messages"
      :is-available="isAvailable"
      :has-messages="hasMessages"
      placeholder="Ask about this JFR profile..."
      welcome-title="Ask anything about your JFR profile"
      :prompt-sections="promptSections"
      @send="sendMessage"
      @prompt-click="handlePromptClick"
      @suggestion="useSuggestion"
      @clear-history="clearHistory"
      @clear-error="clearError"
    >
      <template #panel-actions>
        <div class="modify-toggle" :class="{ active: canModify }">
          <div
            class="modify-toggle-inner"
            :title="
              canModify
                ? 'Data modification is enabled'
                : 'Enable to allow AI to modify profile data'
            "
          >
            <label class="toggle-switch">
              <input
                type="checkbox"
                class="toggle-input"
                v-model="canModify"
                :disabled="!isAvailable"
              />
              <span class="toggle-slider"></span>
            </label>
            <span class="toggle-text">
              <i :class="canModify ? 'bi-pencil-fill' : 'bi-pencil'"></i>
              Allow Modifications
            </span>
          </div>
        </div>
      </template>
    </AiAnalysisPanel>
  </PageHeader>
</template>

<script setup lang="ts">
import '@/styles/shared-components.css';
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { useAiAnalysis } from '@/composables/useAiAnalysis';
import AiAnalysisPanel from '@/components/ai-analysis/AiAnalysisPanel.vue';
import type { PromptSection } from '@/components/ai-analysis/AiAnalysisPanel.vue';
import AiDisabledFeatureAlert from '@/components/alerts/AiDisabledFeatureAlert.vue';
import PageHeader from '@/components/layout/PageHeader.vue';

const route = useRoute();
const profileId = route.params.profileId as string;

const {
  isLoading,
  error,
  status,
  messages,
  canModify,
  isAvailable,
  hasMessages,
  checkStatus,
  sendMessage,
  clearHistory,
  clearError,
  useSuggestion
} = useAiAnalysis(profileId);

const panelRef = ref<InstanceType<typeof AiAnalysisPanel> | null>(null);

const promptSections: PromptSection[] = [
  {
    label: 'Read-only suggestions',
    icon: 'eye',
    variant: 'default',
    prompts: [
      'What JFR events are available in this profile?',
      'Show me the CPU hotspots',
      'Analyze GC pause times',
      'Are there any performance issues?',
      'Show memory allocation patterns'
    ]
  },
  {
    label: 'Modifying suggestions',
    icon: 'pencil',
    variant: 'modify',
    prompts: [
      'Remove all events of <event-type>',
      'Replace package name <old> by <new>',
      'Remove all events before/after a timestamp',
      'Strip events from a specific thread'
    ]
  }
];

const handlePromptClick = (prompt: string, variant: 'default' | 'modify') => {
  if (variant === 'modify') {
    canModify.value = true;
    panelRef.value?.prefillInput(prompt);
  } else {
    canModify.value = false;
    useSuggestion(prompt);
  }
};

onMounted(() => {
  checkStatus();
});
</script>

<style scoped>
.modify-toggle {
  display: flex;
  align-items: center;
}

.modify-toggle-inner {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.75rem;
  color: #656d76;
  user-select: none;
}

/* Amber on-color override for the modifications toggle */
.modify-toggle .toggle-input:checked + .toggle-slider {
  background: var(--color-retained);
}

.modify-toggle .toggle-input:disabled + .toggle-slider {
  opacity: 0.5;
  cursor: not-allowed;
}

.toggle-text {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.modify-toggle.active .toggle-text {
  color: #9a6700;
  font-weight: 500;
}

.modify-toggle.active .toggle-text i {
  color: var(--color-retained);
}
</style>
