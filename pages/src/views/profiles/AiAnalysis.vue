<template>
  <div class="ai-analysis-container">
    <!-- Header -->
    <div class="ai-analysis-header">
      <div class="header-title">
        <i class="bi-cpu-fill"></i>
        <span>AI Analysis</span>
      </div>
      <div class="header-actions">
        <span v-if="status" class="status-badge" :class="{ available: isAvailable }">
          <i :class="isAvailable ? 'bi-check-circle-fill' : 'bi-x-circle-fill'"></i>
          {{ isAvailable ? status.provider || 'AI' : 'Not configured' }}
        </span>
        <button
            v-if="hasMessages"
            class="btn btn-sm btn-outline-secondary"
            @click="clearHistory"
            title="Clear conversation"
        >
          <i class="bi-trash"></i>
        </button>
      </div>
    </div>

    <!-- Chat Area -->
    <div class="chat-area" ref="chatAreaRef">
      <!-- Welcome Message -->
      <div v-if="!hasMessages && !isLoading" class="welcome-message">
        <div class="welcome-icon">
          <i class="bi-cpu-fill"></i>
        </div>
        <h3>AI-Powered JFR Analysis</h3>
        <p>Ask questions about your JFR profile and get AI-powered insights about performance, events, threads, and more.</p>

        <div class="example-prompts">
          <span class="prompts-label">Try asking:</span>
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
        <AiAnalysisChatMessage
            v-for="(message, index) in messages"
            :key="index"
            :message="message"
            @suggestion="useSuggestion"
        />

        <!-- Loading Indicator -->
        <div v-if="isLoading" class="chat-message assistant loading">
          <div class="message-avatar">
            <i class="bi-cpu-fill"></i>
          </div>
          <div class="message-content">
            <div class="typing-indicator">
              <span></span>
              <span></span>
              <span></span>
            </div>
          </div>
        </div>
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
      <div class="input-hint">
        Press <kbd>Ctrl</kbd>+<kbd>Enter</kbd> to send
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useAiAnalysis } from '@/composables/useAiAnalysis';
import AiAnalysisChatMessage from '@/components/ai-analysis/AiAnalysisChatMessage.vue';

const route = useRoute();
const profileId = route.params.profileId as string;

const {
  isLoading,
  error,
  status,
  messages,
  currentInput,
  isAvailable,
  hasMessages,
  checkStatus,
  sendMessage,
  clearHistory,
  clearError,
  useSuggestion
} = useAiAnalysis(profileId);

const chatAreaRef = ref<HTMLElement | null>(null);
const inputRef = ref<HTMLTextAreaElement | null>(null);

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

// Auto-scroll to bottom when new messages arrive
watch(messages, async () => {
  await nextTick();
  if (chatAreaRef.value) {
    chatAreaRef.value.scrollTop = chatAreaRef.value.scrollHeight;
  }
}, { deep: true });

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
  height: 100%;
  background-color: #fff;
  border-radius: 8px;
  border: 1px solid #e1e4e8;
  overflow: hidden;
}

.ai-analysis-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1rem;
  background-color: #f6f8fa;
  border-bottom: 1px solid #e1e4e8;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
  color: #1f2328;
}

.header-title i {
  color: #0969da;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.75rem;
  padding: 0.25rem 0.5rem;
  border-radius: 20px;
  background-color: #ffebe9;
  color: #cf222e;
}

.status-badge.available {
  background-color: #dafbe1;
  color: #1a7f37;
}

.chat-area {
  flex: 1;
  overflow-y: auto;
  padding: 1rem;
}

.welcome-message {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 2rem;
  height: 100%;
}

.welcome-icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background-color: #e8f4fd;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.75rem;
  color: #0969da;
  margin-bottom: 1rem;
}

.welcome-message h3 {
  margin: 0 0 0.5rem 0;
  font-size: 1.25rem;
  color: #1f2328;
}

.welcome-message p {
  margin: 0 0 1.5rem 0;
  color: #656d76;
  max-width: 400px;
}

.example-prompts {
  width: 100%;
  max-width: 500px;
}

.prompts-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: #656d76;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  display: block;
  margin-bottom: 0.75rem;
}

.prompt-chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.5rem;
}

.prompt-chip {
  font-size: 0.8rem;
  padding: 0.5rem 0.875rem;
  background-color: #f6f8fa;
  border: 1px solid #d0d7de;
  border-radius: 20px;
  color: #1f2328;
  cursor: pointer;
  transition: all 0.2s ease;
}

.prompt-chip:hover {
  background-color: #e8f4fd;
  border-color: #0969da;
  color: #0969da;
}

.messages-container {
  display: flex;
  flex-direction: column;
}

.chat-message.loading {
  display: flex;
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
  padding: 1rem;
  border-top: 1px solid #e1e4e8;
  background-color: #f6f8fa;
}

.input-wrapper {
  display: flex;
  gap: 0.5rem;
  background-color: #fff;
  border: 1px solid #d0d7de;
  border-radius: 8px;
  padding: 0.5rem;
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
  font-size: 0.875rem;
  line-height: 1.5;
  padding: 0.25rem 0.5rem;
  min-height: 24px;
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
  align-self: flex-end;
  width: 36px;
  height: 36px;
  border-radius: 6px;
  background-color: #0969da;
  border: none;
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
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

.input-hint {
  font-size: 0.7rem;
  color: #8c959f;
  margin-top: 0.375rem;
  text-align: right;
}

.input-hint kbd {
  background-color: #eaeef2;
  border: 1px solid #d0d7de;
  border-radius: 3px;
  padding: 0.1rem 0.3rem;
  font-family: inherit;
  font-size: 0.65rem;
}
</style>
