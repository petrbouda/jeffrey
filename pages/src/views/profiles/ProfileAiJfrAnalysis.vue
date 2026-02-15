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
          <label class="toggle-label" :title="canModify ? 'Data modification is enabled' : 'Enable to allow AI to modify profile data'">
            <input
                type="checkbox"
                v-model="canModify"
                :disabled="!isAvailable"
            />
            <span class="toggle-switch"></span>
            <span class="toggle-text">
              <i :class="canModify ? 'bi-pencil-fill' : 'bi-pencil'"></i>
              Allow Modifications
            </span>
          </label>
        </div>
      </template>
    </AiAnalysisPanel>
  </PageHeader>
</template>

<script setup lang="ts">
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

.toggle-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
  font-size: 0.75rem;
  color: #656d76;
  user-select: none;
}

.toggle-label input {
  display: none;
}

.toggle-switch {
  position: relative;
  width: 32px;
  height: 18px;
  background-color: #d0d7de;
  border-radius: 9px;
  transition: background-color 0.2s;
}

.toggle-switch::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 14px;
  height: 14px;
  background-color: #fff;
  border-radius: 50%;
  transition: transform 0.2s;
}

.toggle-label input:checked + .toggle-switch {
  background-color: #d4a106;
}

.toggle-label input:checked + .toggle-switch::after {
  transform: translateX(14px);
}

.toggle-label input:disabled + .toggle-switch {
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
  color: #d4a106;
}
</style>
