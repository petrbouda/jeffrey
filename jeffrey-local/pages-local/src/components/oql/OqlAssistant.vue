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
  <!-- Minimized State - Floating Button (always visible when not expanded) -->
  <AssistantMinimizedButton
    v-if="!isExpanded"
    icon="bi bi-terminal"
    badge-text="OQL"
    badge-variant="purple"
    status="default"
    :order="2"
    @click="$emit('expand')"
    title="Click to open OQL Assistant"
  />

  <!-- Expanded State - Panel -->
  <AssistantPanel
    :is-open="isOpen"
    :is-expanded="isExpanded"
    width="480px"
    :show-backdrop="false"
    @close="$emit('close')"
  >
    <template #header-icon>
      <i class="bi bi-stars me-2"></i>
    </template>

    <template #header-title> OQL Assistant </template>

    <template #header-actions>
      <button
        class="btn-icon"
        @click="clearHistory"
        title="Clear conversation"
        :disabled="!hasMessages"
      >
        <i class="bi bi-trash3"></i>
      </button>
      <button class="btn-icon" @click="$emit('minimize')" title="Minimize">
        <i class="bi bi-dash-lg"></i>
      </button>
      <button class="btn-icon" @click="$emit('close')" title="Close panel">
        <i class="bi bi-x-lg"></i>
      </button>
    </template>

    <template #body>
      <!-- Status Badge -->
      <div v-if="status" class="status-badge-row">
        <Badge
          v-if="isAvailable"
          :value="'Connected to ' + status.provider"
          variant="status-finished"
          size="xs"
          :uppercase="false"
          icon="bi-check-circle-fill"
        />
        <Badge
          v-else
          value="AI not configured"
          variant="status-blocked"
          size="xs"
          :uppercase="false"
          icon="bi-exclamation-circle-fill"
        />
      </div>

      <!-- Messages -->
      <div class="messages-container" ref="messagesContainer">
        <!-- Welcome Message -->
        <div v-if="!hasMessages && !isLoading" class="welcome-message">
          <div class="welcome-icon">
            <i class="bi bi-robot"></i>
          </div>
          <h6>How can I help?</h6>
          <p>
            Describe what you want to find in the heap dump, and I'll generate the OQL query for
            you.
          </p>
          <div class="example-prompts">
            <button
              class="example-prompt"
              @click="useSuggestion('Find strings containing Exception')"
            >
              Find strings containing "Exception"
            </button>
            <button
              class="example-prompt"
              @click="useSuggestion('Show large HashMaps with more than 100 entries')"
            >
              Large HashMaps > 100 entries
            </button>
            <button
              class="example-prompt"
              @click="useSuggestion('Find byte arrays larger than 1MB')"
            >
              Byte arrays > 1MB
            </button>
          </div>
        </div>

        <!-- Chat Messages -->
        <OqlChatMessage
          v-for="(msg, index) in messages"
          :key="index"
          :message="msg"
          @apply="oql => $emit('apply', oql)"
          @run="oql => $emit('run', oql)"
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
          <button class="btn btn-sm btn-outline-danger ms-2" @click="clearError">Dismiss</button>
        </div>
      </div>
    </template>

    <template #footer>
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
    </template>
  </AssistantPanel>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted } from 'vue';
import OqlChatMessage from '@/components/oql/OqlChatMessage.vue';
import Badge from '@/components/Badge.vue';
import { useOqlAssistant } from '@/composables/useOqlAssistant';
import AssistantPanel from '@/components/assistants/AssistantPanel.vue';
import AssistantMinimizedButton from '@/components/assistants/AssistantMinimizedButton.vue';

const props = defineProps<{
  isOpen: boolean;
  isExpanded: boolean;
  profileId: string;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'expand'): void;
  (e: 'minimize'): void;
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
} = useOqlAssistant(props.profileId);

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
watch(
  messages,
  () => {
    scrollToBottom();
  },
  { deep: true }
);

// Check status when panel opens
watch(
  () => props.isOpen,
  async open => {
    if (open && !status.value) {
      await checkStatus();
    }
  }
);

onMounted(async () => {
  if (props.isOpen) {
    await checkStatus();
  }
});
</script>

<style scoped>
.status-badge-row {
  display: flex;
  align-items: center;
  padding: 0.5rem 1rem;
}

/* Messages Container */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 0;
}

/* Welcome Message */
.welcome-message {
  padding: 2rem 1.5rem;
  text-align: center;
}

.welcome-icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background-color: var(--color-violet-lighter-bg);
  color: var(--color-violet-dark);
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
  color: var(--color-text-muted);
  margin-bottom: 1.5rem;
}

/* Example Prompts */
.example-prompts {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.example-prompt {
  font-size: 0.8rem;
  padding: 0.625rem 1rem;
  background-color: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  color: var(--color-text);
  cursor: pointer;
  text-align: left;
  transition: all 0.2s ease;
}

.example-prompt:hover {
  background-color: var(--color-violet-lighter-bg);
  border-color: var(--color-violet-border);
  color: var(--color-violet-dark);
}

/* Loading Message */
.loading-message {
  display: flex;
  gap: 0.75rem;
  padding: 1rem;
  background-color: var(--bs-white);
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
  background-color: var(--color-violet-lighter-bg);
  color: var(--color-violet-dark);
}

/* Typing Indicator */
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
  background-color: var(--color-violet-border-light);
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
  0%,
  100% {
    transform: scale(1);
    opacity: 0.5;
  }
  50% {
    transform: scale(1.2);
    opacity: 1;
  }
}

/* Error Message */
.error-message {
  display: flex;
  align-items: center;
  padding: 1rem;
  background-color: var(--color-danger-bg-light);
  color: var(--color-danger-dark);
  font-size: 0.875rem;
  border-bottom: 1px solid var(--color-danger-border-light);
}

/* Input Area */
.input-area {
  display: flex;
  gap: 0.5rem;
  padding: 1rem;
  background-color: var(--color-light);
}

.message-input {
  flex: 1;
  padding: 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  font-size: 0.875rem;
  resize: none;
  font-family: inherit;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.message-input:focus {
  outline: none;
  border-color: var(--color-violet-dark);
  box-shadow: 0 0 0 3px rgba(124, 58, 237, 0.1);
}

.message-input:disabled {
  background-color: var(--color-border);
  cursor: not-allowed;
}

.send-button {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 8px;
  background-color: var(--color-violet-dark);
  color: var(--bs-white);
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.send-button:hover:not(:disabled) {
  background-color: var(--color-violet-deeper);
}

.send-button:disabled {
  background-color: var(--color-muted-separator);
  cursor: not-allowed;
}
</style>
