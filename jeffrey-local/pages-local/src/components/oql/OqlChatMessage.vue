<template>
  <div class="chat-message" :class="message.role">
    <div class="message-avatar">
      <i :class="message.role === 'user' ? 'bi-person-fill' : 'bi-stars'"></i>
    </div>
    <div class="message-content">
      <div class="message-text" v-html="formattedContent"></div>

      <!-- OQL Query Block -->
      <OqlQueryBlock
        v-if="message.oql"
        :query="message.oql"
        @apply="$emit('apply', message.oql)"
        @run="$emit('run', message.oql)"
      />

      <!-- Follow-up Suggestions -->
      <div
        v-if="message.suggestedFollowups && message.suggestedFollowups.length > 0"
        class="suggestions"
      >
        <span class="suggestions-label">Try asking:</span>
        <div class="suggestion-chips">
          <button
            v-for="(suggestion, index) in message.suggestedFollowups"
            :key="index"
            class="suggestion-chip"
            @click="$emit('suggestion', suggestion)"
          >
            {{ suggestion }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import OqlQueryBlock from '@/components/oql/OqlQueryBlock.vue';
import type { ChatMessageWithOql } from '@/composables/useOqlAssistant';

const props = defineProps<{
  message: ChatMessageWithOql;
}>();

defineEmits<{
  (e: 'apply', oql: string): void;
  (e: 'run', oql: string): void;
  (e: 'suggestion', text: string): void;
}>();

const formattedContent = computed(() => {
  // Convert markdown-like formatting to HTML
  let text = props.message.content;

  // Escape HTML entities
  text = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

  // Convert inline code
  text = text.replace(/`([^`]+)`/g, '<code>$1</code>');

  // Convert bold
  text = text.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');

  // Convert line breaks
  text = text.replace(/\n/g, '<br>');

  return text;
});
</script>

<style scoped>
.chat-message {
  display: flex;
  gap: 0.75rem;
  padding: 1rem;
  border-bottom: 1px solid var(--color-border-row);
}

.chat-message:last-child {
  border-bottom: none;
}

.chat-message.user {
  background-color: var(--color-light);
}

.chat-message.assistant {
  background-color: var(--bs-white);
}

.message-avatar {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.9rem;
}

.chat-message.user .message-avatar {
  background-color: var(--color-lighter);
  color: var(--color-text);
}

.chat-message.assistant .message-avatar {
  background-color: var(--color-violet-lighter-bg);
  color: var(--color-violet-dark);
}

.message-content {
  flex: 1;
  min-width: 0;
}

.message-text {
  font-size: 0.875rem;
  line-height: 1.6;
  color: var(--color-dark);
  word-wrap: break-word;
}

.message-text :deep(code) {
  background-color: var(--color-code-bg);
  padding: 0.125rem 0.375rem;
  border-radius: 3px;
  font-size: 0.8rem;
  color: var(--bs-purple);
}

.message-text :deep(strong) {
  font-weight: 600;
}

.suggestions {
  margin-top: 1rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--color-border);
}

.suggestions-label {
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  display: block;
  margin-bottom: 0.5rem;
}

.suggestion-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
}

.suggestion-chip {
  font-size: 0.75rem;
  padding: 0.375rem 0.75rem;
  background-color: var(--color-violet-lighter-bg);
  border: 1px solid var(--color-violet-border);
  border-radius: 20px;
  color: var(--color-violet-dark);
  cursor: pointer;
  transition: all 0.2s ease;
}

.suggestion-chip:hover {
  background-color: var(--color-violet-dark);
  border-color: var(--color-violet-dark);
  color: var(--bs-white);
}
</style>
