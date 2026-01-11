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
      <div v-if="message.suggestedFollowups && message.suggestedFollowups.length > 0" class="suggestions">
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
  border-bottom: 1px solid #f0f0f0;
}

.chat-message:last-child {
  border-bottom: none;
}

.chat-message.user {
  background-color: #f8f9fa;
}

.chat-message.assistant {
  background-color: #fff;
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
  background-color: #e3e8ef;
  color: #495057;
}

.chat-message.assistant .message-avatar {
  background-color: #f3e8ff;
  color: #7c3aed;
}

.message-content {
  flex: 1;
  min-width: 0;
}

.message-text {
  font-size: 0.875rem;
  line-height: 1.6;
  color: #212529;
  word-wrap: break-word;
}

.message-text :deep(code) {
  background-color: #f1f3f5;
  padding: 0.125rem 0.375rem;
  border-radius: 3px;
  font-family: 'SF Mono', Monaco, 'Cascadia Code', monospace;
  font-size: 0.8rem;
  color: #6f42c1;
}

.message-text :deep(strong) {
  font-weight: 600;
}

.suggestions {
  margin-top: 1rem;
  padding-top: 0.75rem;
  border-top: 1px solid #e9ecef;
}

.suggestions-label {
  font-size: 0.7rem;
  font-weight: 600;
  color: #6c757d;
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
  background-color: #f3e8ff;
  border: 1px solid #d8b4fe;
  border-radius: 20px;
  color: #7c3aed;
  cursor: pointer;
  transition: all 0.2s ease;
}

.suggestion-chip:hover {
  background-color: #7c3aed;
  border-color: #7c3aed;
  color: white;
}
</style>
