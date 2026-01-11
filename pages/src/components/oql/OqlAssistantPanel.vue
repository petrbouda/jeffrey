<template>
  <div class="assistant-panel" :class="{ open: isOpen }">
    <div class="panel-backdrop" @click="$emit('close')"></div>
    <div class="panel-content">
      <!-- Header -->
      <div class="panel-header">
        <div class="header-title">
          <i class="bi bi-stars me-2"></i>
          OQL Assistant
        </div>
        <div class="header-actions">
          <button class="btn-icon" @click="clearHistory" title="Clear conversation" :disabled="!hasMessages">
            <i class="bi bi-trash3"></i>
          </button>
          <button class="btn-icon" @click="$emit('close')" title="Close panel">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>
      </div>

      <!-- Status Badge -->
      <div v-if="status" class="status-badge" :class="isAvailable ? 'available' : 'unavailable'">
        <i :class="isAvailable ? 'bi-check-circle-fill' : 'bi-exclamation-circle-fill'"></i>
        <span v-if="isAvailable">Connected to {{ status.provider }}</span>
        <span v-else>AI not configured</span>
      </div>

      <!-- Messages -->
      <div class="messages-container" ref="messagesContainer">
        <!-- Welcome Message -->
        <div v-if="!hasMessages && !isLoading" class="welcome-message">
          <div class="welcome-icon">
            <i class="bi bi-robot"></i>
          </div>
          <h6>How can I help?</h6>
          <p>Describe what you want to find in the heap dump, and I'll generate the OQL query for you.</p>
          <div class="example-prompts">
            <button class="example-prompt" @click="useSuggestion('Find strings containing Exception')">
              Find strings containing "Exception"
            </button>
            <button class="example-prompt" @click="useSuggestion('Show large HashMaps with more than 100 entries')">
              Large HashMaps > 100 entries
            </button>
            <button class="example-prompt" @click="useSuggestion('Find byte arrays larger than 1MB')">
              Byte arrays > 1MB
            </button>
          </div>
        </div>

        <!-- Chat Messages -->
        <OqlChatMessage
            v-for="(msg, index) in messages"
            :key="index"
            :message="msg"
            @apply="(oql) => $emit('apply', oql)"
            @run="(oql) => $emit('run', oql)"
            @suggestion="useSuggestion"
        />

        <!-- Loading indicator -->
        <div v-if="isLoading" class="loading-message">
          <div class="message-avatar">
            <i class="bi bi-stars"></i>
          </div>
          <div class="typing-indicator">
            <span></span>
            <span></span>
            <span></span>
          </div>
        </div>

        <!-- Error message -->
        <div v-if="error" class="error-message">
          <i class="bi bi-exclamation-triangle-fill me-2"></i>
          {{ error }}
          <button class="btn btn-sm btn-outline-danger ms-2" @click="clearError">
            Dismiss
          </button>
        </div>
      </div>

      <!-- Input Area -->
      <div class="input-area">
        <textarea
            v-model="currentInput"
            class="message-input"
            placeholder="Describe what you want to find..."
            rows="2"
            :disabled="!isAvailable || isLoading"
            @keydown.enter.exact.prevent="handleSend"
        ></textarea>
        <button
            class="send-button"
            :disabled="!isAvailable || isLoading || !currentInput.trim()"
            @click="handleSend"
        >
          <i class="bi bi-send-fill"></i>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted } from 'vue';
import OqlChatMessage from '@/components/oql/OqlChatMessage.vue';
import { useOqlAssistant, type ChatMessageWithOql } from '@/composables/useOqlAssistant';

const props = defineProps<{
  isOpen: boolean;
  workspaceId: string;
  projectId: string;
  profileId: string;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'apply', oql: string): void;
  (e: 'run', oql: string): void;
}>();

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
} = useOqlAssistant(props.workspaceId, props.projectId, props.profileId);

const messagesContainer = ref<HTMLElement | null>(null);

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
    }
  });
};

const handleSend = async () => {
  if (!currentInput.value.trim() || isLoading.value) return;

  const message = currentInput.value;
  currentInput.value = '';
  await sendMessage(message);
  scrollToBottom();
};

// Scroll to bottom when messages change
watch(messages, () => {
  scrollToBottom();
}, { deep: true });

// Check status when panel opens
watch(() => props.isOpen, async (open) => {
  if (open && !status.value) {
    await checkStatus();
  }
});

onMounted(async () => {
  if (props.isOpen) {
    await checkStatus();
  }
});
</script>

<style scoped>
.assistant-panel {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1050;
  pointer-events: none;
}

.assistant-panel.open {
  pointer-events: auto;
}

.panel-backdrop {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.3);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.assistant-panel.open .panel-backdrop {
  opacity: 1;
}

.panel-content {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 560px;
  max-width: 100%;
  background: white;
  box-shadow: -4px 0 20px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  transform: translateX(100%);
  transition: transform 0.3s ease;
}

.assistant-panel.open .panel-content {
  transform: translateX(0);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
  border-bottom: 1px solid #e9ecef;
  background-color: #f8f9fa;
}

.header-title {
  font-weight: 600;
  font-size: 1rem;
  color: #7c3aed;
}

.header-actions {
  display: flex;
  gap: 0.5rem;
}

.btn-icon {
  background: none;
  border: none;
  color: #6c757d;
  padding: 0.375rem;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.btn-icon:hover:not(:disabled) {
  background-color: #e9ecef;
  color: #212529;
}

.btn-icon:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  font-size: 0.75rem;
  font-weight: 500;
}

.status-badge.available {
  background-color: #d4edda;
  color: #155724;
}

.status-badge.unavailable {
  background-color: #f8d7da;
  color: #721c24;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 0;
}

.welcome-message {
  padding: 2rem 1.5rem;
  text-align: center;
}

.welcome-icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background-color: #f3e8ff;
  color: #7c3aed;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.75rem;
  margin: 0 auto 1rem;
}

.welcome-message h6 {
  font-weight: 600;
  margin-bottom: 0.5rem;
}

.welcome-message p {
  font-size: 0.875rem;
  color: #6c757d;
  margin-bottom: 1.5rem;
}

.example-prompts {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.example-prompt {
  font-size: 0.8rem;
  padding: 0.625rem 1rem;
  background-color: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 8px;
  color: #495057;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s ease;
}

.example-prompt:hover {
  background-color: #f3e8ff;
  border-color: #d8b4fe;
  color: #7c3aed;
}

.loading-message {
  display: flex;
  gap: 0.75rem;
  padding: 1rem;
  background-color: #fff;
}

.loading-message .message-avatar {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.9rem;
  background-color: #f3e8ff;
  color: #7c3aed;
}

.typing-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 0;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #c4b5fd;
  animation: typing 1.4s infinite ease-in-out;
}

.typing-indicator span:nth-child(1) {
  animation-delay: 0s;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 100% {
    transform: scale(1);
    opacity: 0.5;
  }
  50% {
    transform: scale(1.2);
    opacity: 1;
  }
}

.error-message {
  display: flex;
  align-items: center;
  padding: 1rem;
  background-color: #fff5f5;
  color: #c53030;
  font-size: 0.875rem;
  border-bottom: 1px solid #fed7d7;
}

.input-area {
  display: flex;
  gap: 0.5rem;
  padding: 1rem;
  border-top: 1px solid #e9ecef;
  background-color: #f8f9fa;
}

.message-input {
  flex: 1;
  padding: 0.75rem;
  border: 1px solid #dee2e6;
  border-radius: 8px;
  font-size: 0.875rem;
  resize: none;
  font-family: inherit;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.message-input:focus {
  outline: none;
  border-color: #7c3aed;
  box-shadow: 0 0 0 3px rgba(124, 58, 237, 0.1);
}

.message-input:disabled {
  background-color: #e9ecef;
  cursor: not-allowed;
}

.send-button {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 8px;
  background-color: #7c3aed;
  color: white;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.send-button:hover:not(:disabled) {
  background-color: #6d28d9;
}

.send-button:disabled {
  background-color: #d1d5db;
  cursor: not-allowed;
}
</style>
