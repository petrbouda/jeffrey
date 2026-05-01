<template>
  <div class="oql-query-block">
    <div class="query-header">
      <span class="query-label">OQL Query</span>
      <button class="btn-icon" @click="copyToClipboard" title="Copy to clipboard">
        <i :class="copied ? 'bi-check-lg text-success' : 'bi-clipboard'"></i>
      </button>
    </div>
    <pre class="query-code"><code>{{ query }}</code></pre>
    <div class="query-actions">
      <button class="btn btn-sm btn-outline-purple" @click="$emit('apply')">
        <i class="bi bi-pencil-square me-1"></i>
        Apply to Editor
      </button>
      <button class="btn btn-sm btn-primary" @click="$emit('run')">
        <i class="bi bi-play-fill me-1"></i>
        Run Query
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';

const props = defineProps<{
  query: string;
}>();

defineEmits<{
  (e: 'apply'): void;
  (e: 'run'): void;
}>();

const copied = ref(false);

const copyToClipboard = async () => {
  try {
    await navigator.clipboard.writeText(props.query);
    copied.value = true;
    setTimeout(() => {
      copied.value = false;
    }, 2000);
  } catch (err) {
    console.error('Failed to copy to clipboard:', err);
  }
};
</script>

<style scoped>
.oql-query-block {
  background-color: var(--color-editor-bg);
  border-radius: 6px;
  overflow: hidden;
  margin: 0.75rem 0;
}

.query-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 0.75rem;
  background-color: var(--color-editor-surface);
  border-bottom: 1px solid var(--color-editor-border);
}

.query-label {
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--color-editor-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.btn-icon {
  background: none;
  border: none;
  color: var(--color-editor-muted);
  padding: 0.25rem;
  cursor: pointer;
  transition: color 0.2s ease;
}

.btn-icon:hover {
  color: var(--color-white);
}

.query-code {
  margin: 0;
  padding: 1rem;
  background-color: var(--color-editor-bg);
  color: var(--color-editor-text);
  font-size: 0.8rem;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-x: auto;
}

.query-code code {
  color: var(--color-editor-variable);
}

.query-actions {
  display: flex;
  gap: 0.5rem;
  padding: 0.75rem;
  background-color: var(--color-editor-surface);
  border-top: 1px solid var(--color-editor-border);
}

.query-actions .btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.btn-outline-purple {
  border-color: var(--color-editor-keyword);
  color: var(--color-editor-keyword);
  font-size: 0.75rem;
}

.btn-outline-purple:hover {
  background-color: var(--color-editor-keyword);
  border-color: var(--color-editor-keyword);
  color: var(--color-white);
}

.query-actions .btn-primary {
  font-size: 0.75rem;
}
</style>
