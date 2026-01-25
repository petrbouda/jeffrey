<template>
  <div class="chat-message" :class="message.role">
    <div class="message-avatar">
      <i :class="message.role === 'user' ? 'bi-person-fill' : 'bi-cpu-fill'"></i>
    </div>
    <div class="message-content">
      <div class="message-text" v-html="formattedContent"></div>

      <!-- Tools Used Badge -->
      <div v-if="message.toolsUsed && message.toolsUsed.length > 0" class="tools-used">
        <span class="tools-label">Tools used:</span>
        <span v-for="(tool, index) in message.toolsUsed" :key="index" class="tool-badge">
          {{ formatToolName(tool) }}
        </span>
      </div>

      <!-- Follow-up Suggestions -->
      <div v-if="message.suggestions && message.suggestions.length > 0" class="suggestions">
        <span class="suggestions-label">Try asking:</span>
        <div class="suggestion-chips">
          <button
              v-for="(suggestion, index) in message.suggestions"
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
import type { AiAnalysisChatMessage } from '@/composables/useAiAnalysis';

const props = defineProps<{
  message: AiAnalysisChatMessage;
}>();

defineEmits<{
  (e: 'suggestion', text: string): void;
}>();

const formatToolName = (tool: string) => {
  // Convert snake_case to Title Case
  return tool.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase());
};

const formattedContent = computed(() => {
  let text = props.message.content;

  // Escape HTML entities
  text = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

  // Convert code blocks (triple backticks)
  text = text.replace(/```(\w*)\n([\s\S]*?)```/g, (match, lang, code) => {
    return `<pre class="code-block"><code>${code.trim()}</code></pre>`;
  });

  // Convert inline code
  text = text.replace(/`([^`]+)`/g, '<code>$1</code>');

  // Convert bold
  text = text.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');

  // Convert headers
  text = text.replace(/^### (.+)$/gm, '<h4 class="content-header">$1</h4>');
  text = text.replace(/^## (.+)$/gm, '<h3 class="content-header">$1</h3>');

  // Convert line breaks (but not inside code blocks)
  text = text.replace(/\n/g, '<br>');

  // Fix double line breaks in pre blocks
  text = text.replace(/<pre([^>]*)>([\s\S]*?)<\/pre>/g, (match, attrs, content) => {
    return `<pre${attrs}>${content.replace(/<br>/g, '\n')}</pre>`;
  });

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
  background-color: #e8f4fd;
  color: #0969da;
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
  font-size: 0.8rem;
  color: #0969da;
  font-family: ui-monospace, SFMono-Regular, SF Mono, Menlo, Consolas, Liberation Mono, monospace;
}

.message-text :deep(.code-block) {
  background-color: #f6f8fa;
  border: 1px solid #d0d7de;
  border-radius: 6px;
  padding: 0.75rem 1rem;
  margin: 0.5rem 0;
  overflow-x: auto;
  font-size: 0.8rem;
  line-height: 1.45;
}

.message-text :deep(.code-block code) {
  background: none;
  padding: 0;
  white-space: pre;
  display: block;
}

.message-text :deep(strong) {
  font-weight: 600;
}

.message-text :deep(.content-header) {
  margin: 1rem 0 0.5rem 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: #1f2328;
}

.tools-used {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.375rem;
  margin-top: 0.75rem;
  padding-top: 0.5rem;
}

.tools-label {
  font-size: 0.7rem;
  color: #6c757d;
  font-weight: 500;
}

.tool-badge {
  font-size: 0.65rem;
  padding: 0.2rem 0.5rem;
  background-color: #e8f4fd;
  border-radius: 4px;
  color: #0969da;
  font-weight: 500;
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
  background-color: #e8f4fd;
  border: 1px solid #b6d4f8;
  border-radius: 20px;
  color: #0969da;
  cursor: pointer;
  transition: all 0.2s ease;
}

.suggestion-chip:hover {
  background-color: #0969da;
  border-color: #0969da;
  color: white;
}
</style>
