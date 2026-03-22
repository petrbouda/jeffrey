<template>
  <PageHeader
    title="Heap Dump AI Analysis"
    description="Ask questions about your heap dump and get AI-powered insights"
    icon="bi-memory"
  >
    <AiDisabledFeatureAlert v-if="status && !isAvailable" />
    <AiAnalysisPanel
        v-else
        :is-loading="isLoading"
        :error="error"
        :status="status"
        :messages="messages"
        :is-available="isAvailable"
        :has-messages="hasMessages"
        placeholder="Ask about this heap dump..."
        welcome-title="Ask anything about your heap dump"
        :prompt-sections="promptSections"
        @send="sendMessage"
        @prompt-click="(prompt: string) => useSuggestion(prompt)"
        @suggestion="useSuggestion"
        @clear-history="clearHistory"
        @clear-error="clearError"
    />
  </PageHeader>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { useHeapDumpAiAnalysis } from '@/composables/useHeapDumpAiAnalysis';
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
  isAvailable,
  hasMessages,
  checkStatus,
  sendMessage,
  clearHistory,
  clearError,
  useSuggestion
} = useHeapDumpAiAnalysis(profileId);

const promptSections: PromptSection[] = [
  {
    label: 'Suggested prompts',
    icon: 'lightbulb',
    variant: 'default',
    prompts: [
      'Give me an overview of this heap dump',
      'What are the biggest objects consuming memory?',
      'Are there any memory leak suspects?',
      'Analyze string duplication in this heap',
      'Show the class histogram for top memory consumers',
      'What threads are present in the heap dump?',
      'Analyze collection efficiency and wasted space'
    ]
  }
];

onMounted(() => {
  checkStatus();
});
</script>
