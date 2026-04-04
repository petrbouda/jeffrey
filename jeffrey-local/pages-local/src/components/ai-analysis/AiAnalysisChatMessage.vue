<template>
  <div class="chat-message" :class="message.role">
    <div class="message-avatar">
      <i :class="message.role === 'user' ? 'bi-person-fill' : 'bi-stars'"></i>
    </div>
    <div class="message-content">
      <div class="message-text" v-html="formattedContent"></div>

      <!-- Message Metadata (duration + tools) -->
      <div v-if="message.toolsUsed?.length || message.durationSeconds" class="message-metadata">
        <div v-if="message.durationSeconds" class="response-duration">
          <i class="bi-clock"></i>
          <span>{{ message.durationSeconds }}s</span>
        </div>
        <div v-if="message.toolsUsed?.length" class="tools-used">
          <span class="tools-label">Tools used:</span>
          <span v-for="(tool, index) in message.toolsUsed" :key="index" class="tool-badge">
            {{ formatToolName(tool) }}
          </span>
        </div>
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
  border-bottom: 1px solid var(--color-border-row);
}

.chat-message.user {
  align-items: center;
  padding: 0.625rem 1rem;
  background-color: var(--color-light);
  border-left: 2px solid var(--color-accent-blue);
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
  color: var(--color-text);
}

.chat-message.assistant {
  background-color: var(--color-white);
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
  background-color: var(--color-border);
  color: var(--color-text-muted);
}

.chat-message.assistant .message-avatar {
  background-color: var(--color-sky-bg);
  color: var(--color-accent-blue);
}

.message-content {
  flex: 1;
  min-width: 0;
}

.message-text {
  font-size: 0.8rem;
  line-height: 1.5;
  color: var(--color-dark);
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
  color: var(--color-heading-dark);
}

.message-text :deep(h1) {
  font-size: 1rem;
}
.message-text :deep(h2) {
  font-size: 0.925rem;
}
.message-text :deep(h3) {
  font-size: 0.875rem;
}
.message-text :deep(h4) {
  font-size: 0.8rem;
}

.message-text :deep(h1:first-child),
.message-text :deep(h2:first-child),
.message-text :deep(h3:first-child),
.message-text :deep(h4:first-child) {
  margin-top: 0;
}

/* Inline code */
.message-text :deep(code) {
  background-color: var(--color-code-bg);
  padding: 0.1rem 0.3rem;
  border-radius: 3px;
  font-size: 0.75rem;
  color: var(--color-accent-blue);
  font-family:
    ui-monospace,
    SFMono-Regular,
    SF Mono,
    Menlo,
    Consolas,
    monospace;
}

/* Code blocks */
.message-text :deep(pre) {
  background-color: var(--color-neutral-bg);
  border: 1px solid var(--color-slate-light);
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
  color: var(--color-heading-dark);
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
  border: 1px solid var(--color-slate-light);
  padding: 0.35rem 0.5rem;
  text-align: left;
}

.message-text :deep(th) {
  background-color: var(--color-neutral-bg);
  font-weight: 600;
}

.message-text :deep(tr:nth-child(even)) {
  background-color: var(--color-light);
}

/* Blockquotes */
.message-text :deep(blockquote) {
  margin: 0.5rem 0;
  padding: 0.25rem 0.75rem;
  border-left: 3px solid var(--color-slate-light);
  color: var(--color-grey-muted);
}

/* Horizontal rules */
.message-text :deep(hr) {
  border: none;
  border-top: 1px solid var(--color-slate-light);
  margin: 0.75rem 0;
}

.message-metadata {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  margin-top: 0.75rem;
  padding-top: 0.5rem;
}

.response-duration {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.2rem 0.5rem;
  font-size: 0.65rem;
  color: var(--color-slate-muted);
  background-color: var(--color-neutral-bg);
  border-radius: 4px;
  border: 1px solid var(--color-slate-lighter);
}

.response-duration i {
  font-size: 0.6rem;
}

.tools-used {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.375rem;
}

.tools-label {
  font-size: 0.7rem;
  color: var(--color-text-muted);
  font-weight: 500;
}

.tool-badge {
  font-size: 0.65rem;
  padding: 0.2rem 0.5rem;
  background-color: var(--color-sky-bg);
  border-radius: 4px;
  color: var(--color-accent-blue);
  font-weight: 500;
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
  background-color: var(--color-sky-bg);
  border: 1px solid var(--color-sky-border);
  border-radius: 20px;
  color: var(--color-accent-blue);
  cursor: pointer;
  transition: all 0.2s ease;
}

.suggestion-chip:hover {
  background-color: var(--color-accent-blue);
  border-color: var(--color-accent-blue);
  color: white;
}
</style>
