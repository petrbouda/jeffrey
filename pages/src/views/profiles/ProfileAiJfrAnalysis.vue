<template>
  <PageHeader
    title="JFR Analysis"
    description="Ask questions about your JFR profile and get AI-powered insights"
    icon="bi-activity"
  >
    <div class="ai-analysis-container">
      <!-- Panel Header -->
      <div class="panel-header">
        <div v-if="status" class="ai-status" :class="{ available: isAvailable }">
          <template v-if="isAvailable">
            <span class="provider-badge">
              <i class="bi-cpu"></i>
              {{ status.provider }}
            </span>
            <span v-if="status.model" class="model-badge">
              {{ status.model }}
            </span>
          </template>
          <span v-else class="status-badge unavailable">
            <i class="bi-x-circle-fill"></i>
            Not configured
          </span>
        </div>
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
        <button
            v-if="hasMessages"
            class="btn btn-sm btn-outline-secondary"
            @click="clearHistory"
            title="Clear conversation"
        >
          <i class="bi-trash"></i>
        </button>
      </div>

      <!-- Input Area -->
      <div class="input-area">
        <div class="input-wrapper">
          <textarea
              v-model="currentInput"
              :disabled="!isAvailable || isLoading"
              :placeholder="isAvailable ? 'Ask about this JFR profile...' : 'AI assistant not configured'"
              @keydown.ctrl.enter="handleSend"
              @keydown.meta.enter="handleSend"
              rows="1"
              ref="inputRef"
          ></textarea>
          <button
              class="send-button"
              :disabled="!isAvailable || isLoading || !currentInput.trim()"
              @click="handleSend"
          >
            <i v-if="isLoading" class="bi-hourglass-split spinning"></i>
            <i v-else class="bi-send-fill"></i>
          </button>
        </div>
        <span v-if="isAvailable" class="input-hint">Press <kbd>Ctrl</kbd>+<kbd>Enter</kbd> to send</span>
      </div>

      <!-- Chat Area -->
      <div class="chat-area">
      <!-- Welcome Message -->
      <div v-if="!hasMessages && !isLoading" class="welcome-message">
        <div class="welcome-header">
          <div class="welcome-icon">
            <i class="bi bi-stars"></i>
          </div>
          <h3 class="welcome-title">Ask anything about your JFR profile</h3>
          <div class="welcome-features">
            <span class="feature-tag"><i class="bi bi-cpu"></i> CPU hotspots</span>
            <span class="feature-tag"><i class="bi bi-recycle"></i> GC analysis</span>
            <span class="feature-tag"><i class="bi bi-diagram-3"></i> Thread states</span>
            <span class="feature-tag"><i class="bi bi-memory"></i> Memory patterns</span>
          </div>
        </div>
        <div class="example-prompts">
          <div class="prompt-chips">
            <button
                v-for="(prompt, index) in examplePrompts"
                :key="index"
                class="prompt-chip"
                @click="useSuggestion(prompt)"
            >
              {{ prompt }}
            </button>
          </div>
        </div>
      </div>

      <!-- Chat Messages -->
      <div v-else class="messages-container">
        <template v-for="(message, index) in orderedMessages" :key="index">
          <AiAnalysisChatMessage
              :message="message"
              @suggestion="useSuggestion"
          />
          <!-- Loading Indicator appears after the newest user message -->
          <div v-if="isLoading && index === 0 && message.role === 'user'" class="chat-message assistant loading">
            <div class="message-avatar">
              <i class="bi-stars"></i>
            </div>
            <div class="message-content">
              <div class="typing-indicator">
                <span></span>
                <span></span>
                <span></span>
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- Error Message -->
      <div v-if="error" class="error-message">
        <i class="bi-exclamation-triangle-fill"></i>
        <span>{{ error }}</span>
        <button class="btn-close-error" @click="clearError">
          <i class="bi-x"></i>
        </button>
      </div>
    </div>
    </div>
  </PageHeader>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useAiAnalysis } from '@/composables/useAiAnalysis';
import AiAnalysisChatMessage from '@/components/ai-analysis/AiAnalysisChatMessage.vue';
import PageHeader from '@/components/layout/PageHeader.vue';

const route = useRoute();
const profileId = route.params.profileId as string;

const {
  isLoading,
  error,
  status,
  messages,
  currentInput,
  canModify,
  isAvailable,
  hasMessages,
  checkStatus,
  sendMessage,
  clearHistory,
  clearError,
  useSuggestion
} = useAiAnalysis(profileId);

const inputRef = ref<HTMLTextAreaElement | null>(null);

// Group messages into pairs (request + response) and reverse to show newest first
const orderedMessages = computed(() => {
  const msgs = messages.value;
  const pairs: typeof msgs = [];

  // Group in pairs of 2 (user question + assistant answer)
  for (let i = 0; i < msgs.length; i += 2) {
    const pair = msgs.slice(i, i + 2);
    pairs.unshift(...pair); // Add pair at the beginning
  }

  return pairs;
});

const examplePrompts = [
  'What JFR events are available in this profile?',
  'Show me the CPU hotspots',
  'Analyze GC pause times',
  'What threads are consuming the most resources?',
  'Are there any performance issues?',
  'Show memory allocation patterns'
];

const handleSend = async () => {
  if (!currentInput.value.trim() || isLoading.value) return;

  const message = currentInput.value;
  currentInput.value = '';
  await sendMessage(message);
};

// Auto-resize textarea
watch(currentInput, () => {
  if (inputRef.value) {
    inputRef.value.style.height = 'auto';
    inputRef.value.style.height = Math.min(inputRef.value.scrollHeight, 150) + 'px';
  }
});

onMounted(() => {
  checkStatus();
});
</script>

<style scoped>
.ai-analysis-container {
  display: flex;
  flex-direction: column;
  background-color: #fff;
  border-radius: 8px;
  border: 1px solid #e1e4e8;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  background-color: #f6f8fa;
  border-bottom: 1px solid #e1e4e8;
}

.ai-status {
  display: flex;
  align-items: center;
  gap: 0.375rem;
}

.provider-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.7rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.025em;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  color: #ffffff !important;
}

.provider-badge i {
  font-size: 0.65rem;
  color: #ffffff !important;
}

.model-badge {
  display: inline-flex;
  align-items: center;
  font-size: 0.7rem;
  font-weight: 500;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  background-color: #f3f4f6;
  color: #374151 !important;
  border: 1px solid #e5e7eb;
}

.status-badge.unavailable {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.75rem;
  padding: 0.25rem 0.5rem;
  border-radius: 20px;
  background-color: #ffebe9;
  color: #cf222e;
}

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

.chat-area {
  padding: 1rem;
}

.welcome-message {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 1.5rem;
}

.welcome-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 2rem;
}

.welcome-icon {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #8b5cf6 0%, #7c3aed 100%);
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 16px rgba(124, 58, 237, 0.25);
}

.welcome-icon i {
  font-size: 1.6rem;
  color: white;
  animation: sparkle 3s ease-in-out infinite;
}

@keyframes sparkle {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.85; transform: scale(1.08); }
}

.welcome-title {
  font-size: 1.15rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0.25rem 0 0 0;
}

.welcome-features {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.5rem;
  margin-top: 0.25rem;
}

.feature-tag {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.7rem;
  color: #6b7280;
  background: #f3f4f6;
  padding: 0.25rem 0.6rem;
  border-radius: 12px;
}

.feature-tag i {
  font-size: 0.65rem;
  color: #9ca3af;
}

.example-prompts {
  width: 100%;
  max-width: 650px;
  margin-bottom: 1rem;
}

.prompt-chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.375rem;
}

.prompt-chip {
  font-size: 0.75rem;
  padding: 0.375rem 0.75rem;
  background-color: #e8f4fd;
  border: 1px solid #b6d4f8;
  border-radius: 20px;
  color: #0969da;
  cursor: pointer;
  transition: all 0.2s ease;
}

.prompt-chip:hover {
  background-color: #0969da;
  border-color: #0969da;
  color: white;
}

.messages-container {
  display: flex;
  flex-direction: column;
}

.chat-message.loading {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem;
}

.chat-message.loading .message-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background-color: #e8f4fd;
  color: #0969da;
  display: flex;
  align-items: center;
  justify-content: center;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 0.5rem;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  background-color: #0969da;
  border-radius: 50%;
  animation: typing 1.4s infinite ease-in-out both;
}

.typing-indicator span:nth-child(1) {
  animation-delay: -0.32s;
}

.typing-indicator span:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes typing {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.error-message {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  background-color: #ffebe9;
  border: 1px solid #ff8182;
  border-radius: 6px;
  color: #cf222e;
  font-size: 0.875rem;
  margin: 0.5rem 0;
}

.error-message i {
  flex-shrink: 0;
}

.error-message span {
  flex: 1;
}

.btn-close-error {
  background: none;
  border: none;
  color: #cf222e;
  cursor: pointer;
  padding: 0.25rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-close-error:hover {
  opacity: 0.7;
}

.input-area {
  padding: 1rem 1rem 0.375rem 1rem;
  border-bottom: 1px solid #e1e4e8;
  background-color: #f6f8fa;
}

.input-hint {
  display: block;
  font-size: 0.65rem;
  color: #8c959f;
  margin-top: 0.375rem;
  text-align: right;
}

.input-hint kbd {
  display: inline-block;
  padding: 0.1rem 0.3rem;
  font-size: 0.6rem;
  font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, monospace;
  background-color: #f6f8fa;
  border: 1px solid #d0d7de;
  border-radius: 3px;
  box-shadow: inset 0 -1px 0 #d0d7de;
  color: #24292f !important;
}

.input-wrapper {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background-color: #fff;
  border: 1px solid #d0d7de;
  border-radius: 6px;
  padding: 0.375rem;
  transition: border-color 0.2s;
}

.input-wrapper:focus-within {
  border-color: #0969da;
  box-shadow: 0 0 0 3px rgba(9, 105, 218, 0.1);
}

.input-wrapper textarea {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  font-size: 0.8rem;
  line-height: 1.5;
  padding: 0.25rem 0.5rem;
  min-height: 22px;
  max-height: 150px;
  font-family: inherit;
}

.input-wrapper textarea::placeholder {
  color: #8c959f;
}

.input-wrapper textarea:disabled {
  background-color: transparent;
  cursor: not-allowed;
}

.send-button {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 4px;
  background-color: #0969da;
  border: none;
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
  transition: background-color 0.2s;
}

.send-button:hover:not(:disabled) {
  background-color: #0860c7;
}

.send-button:disabled {
  background-color: #8c959f;
  cursor: not-allowed;
}

.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

</style>
