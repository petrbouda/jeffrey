<template>
  <div class="chat-message" :class="message.role">
    <div class="message-avatar">
      <i :class="message.role === 'user' ? 'bi-person-fill' : 'bi-stars'"></i>
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
import { marked } from 'marked';
import type { AiAnalysisChatMessage } from '@/composables/useAiAnalysis';

// Configure marked for clean output
marked.setOptions({
  gfm: true,
  breaks: true
});

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
  return marked.parse(props.message.content) as string;
});
</script>

<style scoped>
.chat-message {
  display: flex;
  gap: 0.75rem;
  padding: 1rem;
  border-bottom: 1px solid #f0f0f0;
}

.chat-message.user {
  align-items: center;
  padding: 0.625rem 1rem;
  background-color: #f8f9fa;
  border-left: 2px solid #0969da;
}

.chat-message.assistant {
  align-items: flex-start;
}

.chat-message:last-child {
  border-bottom: none;
}

.chat-message.user .message-text {
  font-size: 0.8rem;
  font-weight: 500;
  color: #495057;
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
  width: 26px;
  height: 26px;
  font-size: 0.75rem;
  background-color: #e9ecef;
  color: #6c757d;
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
  font-size: 0.8rem;
  line-height: 1.5;
  color: #212529;
  word-wrap: break-word;
}

/* Paragraphs */
.message-text :deep(p) {
  margin: 0 0 0.5rem 0;
}

.message-text :deep(p:last-child) {
  margin-bottom: 0;
}

/* Headers */
.message-text :deep(h1),
.message-text :deep(h2),
.message-text :deep(h3),
.message-text :deep(h4) {
  margin: 0.75rem 0 0.25rem 0;
  font-weight: 600;
  color: #1f2328;
}

.message-text :deep(h1) { font-size: 1rem; }
.message-text :deep(h2) { font-size: 0.925rem; }
.message-text :deep(h3) { font-size: 0.875rem; }
.message-text :deep(h4) { font-size: 0.8rem; }

.message-text :deep(h1:first-child),
.message-text :deep(h2:first-child),
.message-text :deep(h3:first-child),
.message-text :deep(h4:first-child) {
  margin-top: 0;
}

/* Inline code */
.message-text :deep(code) {
  background-color: #f1f3f5;
  padding: 0.1rem 0.3rem;
  border-radius: 3px;
  font-size: 0.75rem;
  color: #0969da;
  font-family: ui-monospace, SFMono-Regular, SF Mono, Menlo, Consolas, monospace;
}

/* Code blocks */
.message-text :deep(pre) {
  background-color: #f6f8fa;
  border: 1px solid #d0d7de;
  border-radius: 4px;
  padding: 0.5rem 0.75rem;
  margin: 0.5rem 0;
  overflow-x: auto;
  font-size: 0.75rem;
  line-height: 1.4;
}

.message-text :deep(pre code) {
  background: none;
  padding: 0;
  color: #24292f;
  white-space: pre;
  display: block;
}

/* Bold/Strong */
.message-text :deep(strong) {
  font-weight: 600;
}

/* Lists */
.message-text :deep(ul),
.message-text :deep(ol) {
  margin: 0.25rem 0 0.5rem 0;
  padding-left: 1.25rem;
}

.message-text :deep(li) {
  margin: 0.125rem 0;
}

/* Tables */
.message-text :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 0.5rem 0;
  font-size: 0.75rem;
}

.message-text :deep(th),
.message-text :deep(td) {
  border: 1px solid #d0d7de;
  padding: 0.35rem 0.5rem;
  text-align: left;
}

.message-text :deep(th) {
  background-color: #f6f8fa;
  font-weight: 600;
}

.message-text :deep(tr:nth-child(even)) {
  background-color: #f9fafb;
}

/* Blockquotes */
.message-text :deep(blockquote) {
  margin: 0.5rem 0;
  padding: 0.25rem 0.75rem;
  border-left: 3px solid #d0d7de;
  color: #656d76;
}

/* Horizontal rules */
.message-text :deep(hr) {
  border: none;
  border-top: 1px solid #d0d7de;
  margin: 0.75rem 0;
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
