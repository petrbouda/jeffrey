<template>
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
      <slot name="panel-actions" />
      <button
          v-if="hasMessages"
          class="btn btn-sm btn-outline-secondary"
          @click="emit('clear-history')"
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
            :placeholder="isAvailable ? placeholder : 'AI assistant not configured'"
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
          <h3 class="welcome-title">{{ welcomeTitle }}</h3>
        </div>
        <div class="example-prompts">
          <template v-for="(section, sIndex) in promptSections" :key="sIndex">
            <div v-if="sIndex > 0" class="prompt-divider"></div>
            <div class="prompt-section">
              <span class="prompt-section-label" :class="{ 'prompt-section-label-modify': section.variant === 'modify' }">
                <i :class="'bi bi-' + section.icon"></i> {{ section.label }}
              </span>
              <div class="prompt-chips">
                <button
                    v-for="(prompt, pIndex) in section.prompts"
                    :key="pIndex"
                    :class="section.variant === 'modify' ? 'prompt-chip-modify' : 'prompt-chip'"
                    @click="emit('prompt-click', prompt, section.variant)"
                >
                  {{ prompt }}
                </button>
              </div>
            </div>
          </template>
        </div>
      </div>

      <!-- Chat Messages -->
      <div v-else class="messages-container">
        <template v-for="(message, index) in orderedMessages" :key="index">
          <AiAnalysisChatMessage
              :message="message"
              @suggestion="handleSuggestion"
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
                <span v-if="elapsedSeconds > 0" class="elapsed-time">{{ elapsedSeconds }}s</span>
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- Error Message -->
      <div v-if="error" class="error-message">
        <i class="bi-exclamation-triangle-fill"></i>
        <span>{{ error }}</span>
        <button class="btn-close-error" @click="emit('clear-error')">
          <i class="bi-x"></i>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted, watch } from 'vue';
import type { AiAnalysisChatMessage as ChatMessageType } from '@/composables/useAiAnalysis';
import type AiStatusResponse from '@/services/api/model/AiStatusResponse';
import AiAnalysisChatMessage from '@/components/ai-analysis/AiAnalysisChatMessage.vue';

export interface PromptSection {
  label: string
  icon: string
  prompts: string[]
  variant: 'default' | 'modify'
}

const props = defineProps<{
  isLoading: boolean
  error: string | null
  status: AiStatusResponse | null
  messages: ChatMessageType[]
  isAvailable: boolean
  hasMessages: boolean
  placeholder: string
  welcomeTitle: string
  promptSections: PromptSection[]
}>();

const emit = defineEmits<{
  send: [message: string]
  'prompt-click': [prompt: string, variant: 'default' | 'modify']
  suggestion: [text: string]
  'clear-history': []
  'clear-error': []
}>();

const currentInput = ref('');
const inputRef = ref<HTMLTextAreaElement | null>(null);

// Timer for tracking AI response duration
const elapsedSeconds = ref(0);
let timerInterval: ReturnType<typeof setInterval> | null = null;

watch(() => props.isLoading, (loading) => {
  if (loading) {
    elapsedSeconds.value = 0;
    const startTime = Date.now();
    timerInterval = setInterval(() => {
      elapsedSeconds.value = Math.floor((Date.now() - startTime) / 1000);
    }, 1000);
  } else {
    if (timerInterval) {
      clearInterval(timerInterval);
      timerInterval = null;
    }
    if (elapsedSeconds.value > 0) {
      const lastMsg = props.messages[props.messages.length - 1];
      if (lastMsg?.role === 'assistant') {
        lastMsg.durationSeconds = elapsedSeconds.value;
      }
    }
  }
});

onUnmounted(() => {
  if (timerInterval) {
    clearInterval(timerInterval);
  }
});

// Group messages into pairs (request + response) and reverse to show newest first
const orderedMessages = computed(() => {
  const msgs = props.messages;
  const pairs: typeof msgs = [];

  for (let i = 0; i < msgs.length; i += 2) {
    const pair = msgs.slice(i, i + 2);
    pairs.unshift(...pair);
  }

  return pairs;
});

const handleSend = () => {
  if (!currentInput.value.trim() || props.isLoading) return;

  const message = currentInput.value;
  currentInput.value = '';
  emit('send', message);
};

const handleSuggestion = (suggestion: string) => {
  window.scrollTo({ top: 0, behavior: 'smooth' });
  emit('suggestion', suggestion);
};

const prefillInput = (text: string) => {
  currentInput.value = text;
  inputRef.value?.focus();
};

// Auto-resize textarea
watch(currentInput, () => {
  if (inputRef.value) {
    inputRef.value.style.height = 'auto';
    inputRef.value.style.height = Math.min(inputRef.value.scrollHeight, 150) + 'px';
  }
});

defineExpose({ prefillInput });
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

.example-prompts {
  display: flex;
  align-items: stretch;
  gap: 1rem;
  max-width: 800px;
  margin-bottom: 1rem;
}

.prompt-section {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}

.prompt-section-label {
  font-size: 0.8rem;
  font-weight: 500;
  color: #0969da;
  display: flex;
  align-items: center;
  gap: 0.3rem;
}

.prompt-section-label-modify {
  color: #9a6700;
}

.prompt-chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  align-content: center;
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

.prompt-divider {
  width: 1px;
  background-color: #e1e4e8;
  flex-shrink: 0;
}

.prompt-chip-modify {
  font-size: 0.75rem;
  padding: 0.375rem 0.75rem;
  background-color: #fef9c3;
  border: 1px solid #d4a106;
  border-radius: 20px;
  color: #9a6700;
  cursor: pointer;
  transition: all 0.2s ease;
}

.prompt-chip-modify:hover {
  background-color: #d4a106;
  border-color: #d4a106;
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

.typing-indicator .elapsed-time {
  font-size: 0.75rem;
  color: #8c959f;
  margin-left: 0.25rem;
  animation: none;
  width: auto;
  height: auto;
  background-color: transparent;
  border-radius: 0;
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
