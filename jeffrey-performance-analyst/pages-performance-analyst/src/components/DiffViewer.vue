<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
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
  <div class="diff-viewer">
    <div v-for="(line, i) in lines" :key="i" class="diff-line" :class="`dl-${line.type}`">
      <span class="dl-gutter">{{ line.marker }}</span>
      <span v-if="line.html !== null" class="dl-code" v-html="line.html"></span>
      <span v-else class="dl-code">{{ line.text }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import hljs from 'highlight.js/lib/core';
import java from 'highlight.js/lib/languages/java';
import typescript from 'highlight.js/lib/languages/typescript';
import javascript from 'highlight.js/lib/languages/javascript';
import xml from 'highlight.js/lib/languages/xml';
import sql from 'highlight.js/lib/languages/sql';
import kotlin from 'highlight.js/lib/languages/kotlin';
import python from 'highlight.js/lib/languages/python';
import properties from 'highlight.js/lib/languages/properties';
import bash from 'highlight.js/lib/languages/bash';
import json from 'highlight.js/lib/languages/json';
import yaml from 'highlight.js/lib/languages/yaml';
import 'highlight.js/styles/github.css';

hljs.registerLanguage('java', java);
hljs.registerLanguage('typescript', typescript);
hljs.registerLanguage('javascript', javascript);
hljs.registerLanguage('xml', xml);
hljs.registerLanguage('sql', sql);
hljs.registerLanguage('kotlin', kotlin);
hljs.registerLanguage('python', python);
hljs.registerLanguage('properties', properties);
hljs.registerLanguage('bash', bash);
hljs.registerLanguage('json', json);
hljs.registerLanguage('yaml', yaml);

const props = defineProps<{ patch: string }>();

type LineType = 'add' | 'del' | 'ctx' | 'hunk' | 'meta';

interface DiffLine {
  type: LineType;
  marker: string;
  text: string;
  // Highlighted HTML for code lines (escaped by highlight.js → safe to v-html); null for header lines
  // which are rendered as auto-escaped text instead.
  html: string | null;
}

// File extension → highlight.js language id. Unknown extensions fall back to auto-detection.
const EXT_LANG: Record<string, string> = {
  java: 'java',
  kt: 'kotlin',
  kts: 'kotlin',
  ts: 'typescript',
  tsx: 'typescript',
  js: 'javascript',
  jsx: 'javascript',
  mjs: 'javascript',
  vue: 'xml',
  html: 'xml',
  xml: 'xml',
  sql: 'sql',
  py: 'python',
  properties: 'properties',
  sh: 'bash',
  bash: 'bash',
  json: 'json',
  yml: 'yaml',
  yaml: 'yaml'
};

const language = computed<string | null>(() => {
  let path: string | null = null;
  for (const line of props.patch.split('\n')) {
    if (line.startsWith('+++ ') || line.startsWith('--- ')) {
      const candidate = line.slice(4).trim();
      if (candidate && candidate !== '/dev/null') {
        path = candidate;
      }
    }
  }
  if (!path) {
    return null;
  }
  const dot = path.lastIndexOf('.');
  if (dot < 0) {
    return null;
  }
  return EXT_LANG[path.slice(dot + 1).toLowerCase()] ?? null;
});

const highlightCode = (code: string): string => {
  if (code === '') {
    return '';
  }
  try {
    const lang = language.value;
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value;
    }
    return hljs.highlightAuto(code).value;
  } catch {
    return code.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }
};

const isMeta = (line: string): boolean =>
  line.startsWith('+++ ') ||
  line.startsWith('--- ') ||
  line.startsWith('diff ') ||
  line.startsWith('index ') ||
  line.startsWith('\\');

const lines = computed<DiffLine[]>(() =>
  props.patch.split('\n').map((raw): DiffLine => {
    if (raw.startsWith('@@')) {
      return { type: 'hunk', marker: '', text: raw, html: null };
    }
    if (isMeta(raw)) {
      return { type: 'meta', marker: '', text: raw, html: null };
    }
    if (raw.startsWith('+')) {
      const code = raw.slice(1);
      return { type: 'add', marker: '+', text: code, html: highlightCode(code) };
    }
    if (raw.startsWith('-')) {
      const code = raw.slice(1);
      return { type: 'del', marker: '-', text: code, html: highlightCode(code) };
    }
    const code = raw.startsWith(' ') ? raw.slice(1) : raw;
    return { type: 'ctx', marker: ' ', text: code, html: highlightCode(code) };
  })
);
</script>

<style scoped>
.diff-viewer {
  overflow-x: auto;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  font-family: ui-monospace, 'JetBrains Mono', Menlo, Consolas, monospace;
  font-size: 0.76rem;
  line-height: 1.5;
}

.diff-line {
  display: flex;
  min-width: max-content;
}

.dl-gutter {
  flex: none;
  width: 1.6rem;
  padding-right: 0.25rem;
  text-align: center;
  user-select: none;
  color: var(--color-text-light);
}

.dl-code {
  white-space: pre;
  padding-right: 1rem;
}

.dl-add {
  background: var(--color-success-light);
}

.dl-add .dl-gutter {
  color: var(--color-success-hover);
}

.dl-del {
  background: var(--color-danger-light);
}

.dl-del .dl-gutter {
  color: var(--color-danger);
}

.dl-ctx {
  color: var(--color-text);
}

.dl-hunk {
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-weight: 600;
}

.dl-meta {
  background: var(--color-light);
  color: var(--color-text-muted);
}
</style>
