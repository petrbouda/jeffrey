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
  background-color: #1e1e1e;
  border-radius: 6px;
  overflow: hidden;
  margin: 0.75rem 0;
}

.query-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 0.75rem;
  background-color: #2d2d2d;
  border-bottom: 1px solid #3d3d3d;
}

.query-label {
  font-size: 0.7rem;
  font-weight: 600;
  color: #9d9d9d;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.btn-icon {
  background: none;
  border: none;
  color: #9d9d9d;
  padding: 0.25rem;
  cursor: pointer;
  transition: color 0.2s ease;
}

.btn-icon:hover {
  color: #fff;
}

.query-code {
  margin: 0;
  padding: 1rem;
  background-color: #1e1e1e;
  color: #d4d4d4;
  font-family: 'SF Mono', Monaco, 'Cascadia Code', monospace;
  font-size: 0.8rem;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-x: auto;
}

.query-code code {
  color: #9cdcfe;
}

.query-actions {
  display: flex;
  gap: 0.5rem;
  padding: 0.75rem;
  background-color: #2d2d2d;
  border-top: 1px solid #3d3d3d;
}

.query-actions .btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.btn-outline-purple {
  border-color: #9d7ee8;
  color: #9d7ee8;
  font-size: 0.75rem;
}

.btn-outline-purple:hover {
  background-color: #9d7ee8;
  border-color: #9d7ee8;
  color: white;
}

.query-actions .btn-primary {
  font-size: 0.75rem;
}
</style>
