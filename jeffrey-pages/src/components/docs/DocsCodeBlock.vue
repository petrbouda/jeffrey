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

<script setup lang="ts">
import { ref, computed } from 'vue';

interface Props {
  code: string;
  language?: string;
  filename?: string;
}

const props = withDefaults(defineProps<Props>(), {
  language: 'plaintext',
  filename: ''
});

const copied = ref(false);

const displayLanguage = computed(() => {
  const langMap: Record<string, string> = {
    'js': 'JavaScript',
    'javascript': 'JavaScript',
    'ts': 'TypeScript',
    'typescript': 'TypeScript',
    'java': 'Java',
    'bash': 'Bash',
    'shell': 'Shell',
    'sh': 'Shell',
    'sql': 'SQL',
    'json': 'JSON',
    'yaml': 'YAML',
    'yml': 'YAML',
    'xml': 'XML',
    'html': 'HTML',
    'vue': 'Vue',
    'properties': 'Properties',
    'plaintext': 'Text'
  };
  return langMap[props.language] || props.language.toUpperCase();
});

const copyCode = async () => {
  try {
    await navigator.clipboard.writeText(props.code);
    copied.value = true;
    setTimeout(() => {
      copied.value = false;
    }, 2000);
  } catch (err) {
    console.error('Failed to copy:', err);
  }
};
</script>

<template>
  <div class="code-block">
    <div class="code-header">
      <div class="code-meta">
        <span class="code-lang">{{ displayLanguage }}</span>
        <span v-if="filename" class="code-filename">{{ filename }}</span>
      </div>
      <button class="copy-btn" :class="{ 'copied': copied }" @click="copyCode">
        <i class="bi" :class="copied ? 'bi-check' : 'bi-clipboard'"></i>
        <span>{{ copied ? 'Copied!' : 'Copy' }}</span>
      </button>
    </div>
    <pre class="code-content"><code>{{ code }}</code></pre>
  </div>
</template>

<style scoped>
.code-block {
  background-color: #f8fafc;
  border-radius: 8px;
  overflow: hidden;
  margin: 1.5rem 0;
  border: 1px solid #e2e8f0;
}

.code-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 1rem;
  background-color: #f1f5f9;
  border-bottom: 1px solid #e2e8f0;
}

.code-meta {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.code-lang {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  font-size: 0.65rem;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  text-transform: uppercase;
  font-weight: 600;
  letter-spacing: 0.03em;
}

.code-filename {
  color: #64748b;
  font-size: 0.8rem;
  font-family: 'Monaco', 'Consolas', monospace;
}

.copy-btn {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.375rem 0.75rem;
  background: transparent;
  border: 1px solid #cbd5e1;
  border-radius: 5px;
  color: #64748b;
  font-size: 0.75rem;
  cursor: pointer;
  transition: all 0.2s ease;
}

.copy-btn:hover {
  background-color: #e2e8f0;
  color: #334155;
}

.copy-btn.copied {
  background-color: #10b981;
  border-color: #10b981;
  color: #fff;
}

.code-content {
  margin: 0;
  padding: 1rem 1.25rem;
  overflow-x: auto;
  background: none;
  border: none;
}

.code-content code {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', monospace;
  font-size: 0.875rem;
  line-height: 1.6;
  color: #334155;
  background: none;
}

/* Custom scrollbar for code */
.code-content::-webkit-scrollbar {
  height: 6px;
}

.code-content::-webkit-scrollbar-track {
  background: #f1f5f9;
}

.code-content::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
}
</style>
