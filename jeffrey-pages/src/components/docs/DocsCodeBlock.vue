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
import hljs from 'highlight.js/lib/core';
import bash from 'highlight.js/lib/languages/bash';
import ini from 'highlight.js/lib/languages/ini';
import java from 'highlight.js/lib/languages/java';
import javascript from 'highlight.js/lib/languages/javascript';
import json from 'highlight.js/lib/languages/json';
import properties from 'highlight.js/lib/languages/properties';
import sql from 'highlight.js/lib/languages/sql';
import typescript from 'highlight.js/lib/languages/typescript';
import xml from 'highlight.js/lib/languages/xml';
import yaml from 'highlight.js/lib/languages/yaml';

// Only the grammars the docs actually use are registered — highlight.js is
// imported through its core entry point so the rest is never bundled.
hljs.registerLanguage('bash', bash);
hljs.registerLanguage('ini', ini);
hljs.registerLanguage('java', java);
hljs.registerLanguage('javascript', javascript);
hljs.registerLanguage('json', json);
hljs.registerLanguage('properties', properties);
hljs.registerLanguage('sql', sql);
hljs.registerLanguage('typescript', typescript);
hljs.registerLanguage('xml', xml);
hljs.registerLanguage('yaml', yaml);

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

const LANGUAGE_LABELS: Record<string, string> = {
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
  'hocon': 'HOCON',
  'properties': 'Properties',
  'text': 'Text',
  'plaintext': 'Text'
};

// The grammar each documented language is highlighted with. A language absent
// from this map renders verbatim — which is what `text` wants, since those
// blocks are span trees and recorded output rather than source.
const GRAMMARS: Record<string, string> = {
  'js': 'javascript',
  'javascript': 'javascript',
  'ts': 'typescript',
  'typescript': 'typescript',
  'java': 'java',
  'bash': 'bash',
  'shell': 'bash',
  'sh': 'bash',
  'sql': 'sql',
  'json': 'json',
  'yaml': 'yaml',
  'yml': 'yaml',
  'xml': 'xml',
  'html': 'xml',
  'vue': 'xml',
  'hocon': 'ini',
  'properties': 'properties'
};

const displayLanguage = computed(() => {
  return LANGUAGE_LABELS[props.language] || props.language.toUpperCase();
});

// Highlighted markup, or null when the language has no grammar and the code
// should be rendered as plain text. `ignoreIllegals` keeps the samples that
// carry elisions (`…`) or pseudo-code from throwing.
const highlightedCode = computed(() => {
  const grammar = GRAMMARS[props.language];
  if (!grammar) {
    return null;
  }
  return hljs.highlight(props.code, { language: grammar, ignoreIllegals: true }).value;
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
    <pre v-if="highlightedCode" class="code-content"><code v-html="highlightedCode"></code></pre>
    <pre v-else class="code-content"><code>{{ code }}</code></pre>
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

/*
 * Syntax-highlighting palette. The markup comes from highlight.js through
 * v-html, so it carries no scoped attribute of its own — `:deep()` reaches it
 * from the `<pre>` that does. Colours follow GitHub's light theme, which sits
 * on the same near-white ground the block already uses.
 */
.code-content :deep(.hljs-keyword),
.code-content :deep(.hljs-selector-tag),
.code-content :deep(.hljs-literal),
.code-content :deep(.hljs-section),
.code-content :deep(.hljs-doctag),
.code-content :deep(.hljs-name) {
  color: #d73a49;
}

.code-content :deep(.hljs-string),
.code-content :deep(.hljs-regexp),
.code-content :deep(.hljs-addition),
.code-content :deep(.hljs-attribute),
.code-content :deep(.hljs-meta .hljs-string) {
  color: #032f62;
}

.code-content :deep(.hljs-comment),
.code-content :deep(.hljs-quote) {
  color: #6a737d;
  font-style: italic;
}

.code-content :deep(.hljs-number),
.code-content :deep(.hljs-variable),
.code-content :deep(.hljs-template-variable),
.code-content :deep(.hljs-attr),
.code-content :deep(.hljs-selector-attr),
.code-content :deep(.hljs-selector-pseudo) {
  color: #005cc5;
}

.code-content :deep(.hljs-title),
.code-content :deep(.hljs-title.function_),
.code-content :deep(.hljs-title.class_) {
  color: #6f42c1;
}

.code-content :deep(.hljs-type),
.code-content :deep(.hljs-built_in),
.code-content :deep(.hljs-class .hljs-title),
.code-content :deep(.hljs-params) {
  color: #e36209;
}

.code-content :deep(.hljs-meta),
.code-content :deep(.hljs-symbol),
.code-content :deep(.hljs-bullet),
.code-content :deep(.hljs-link) {
  color: #735c0f;
}

.code-content :deep(.hljs-emphasis) {
  font-style: italic;
}

.code-content :deep(.hljs-strong) {
  font-weight: 600;
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
