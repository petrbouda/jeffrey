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

<!--
  Pretty-prints and syntax-highlights a JSON value (string or object). Invalid JSON is shown
  verbatim; an empty value renders a muted placeholder.
-->
<template>
  <pre v-if="!isEmpty" class="json-highlight" v-html="rendered"></pre>
  <div v-else class="json-empty">{{ emptyText }}</div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

interface Props {
  value: string | object | null | undefined;
  emptyText?: string;
}

const props = withDefaults(defineProps<Props>(), {
  emptyText: 'None'
});

const isEmpty = computed(() => {
  if (props.value == null) {
    return true;
  }
  return typeof props.value === 'string' && props.value.trim() === '';
});

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

// Tokenizer based on the well-known library-free JSON highlighter: classify strings (keys vs
// values), numbers, booleans and null, then wrap each token in a span.
const TOKEN_PATTERN =
  /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false)\b|\bnull\b|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)/g;

const rendered = computed<string>(() => {
  let parsed: unknown;
  try {
    parsed = typeof props.value === 'string' ? JSON.parse(props.value) : props.value;
  } catch {
    return escapeHtml(String(props.value));
  }
  const pretty = escapeHtml(JSON.stringify(parsed, null, 2));
  return pretty
    .replace(TOKEN_PATTERN, match => {
      let cls = 'j-num';
      if (/^"/.test(match)) {
        cls = /:$/.test(match) ? 'j-key' : 'j-str';
      } else if (/true|false/.test(match)) {
        cls = 'j-bool';
      } else if (/null/.test(match)) {
        cls = 'j-null';
      }
      return `<span class="${cls}">${match}</span>`;
    })
    .replace(/([{}[\],])/g, '<span class="j-punct">$1</span>');
});
</script>

<style scoped>
.json-highlight {
  margin: 0;
  padding: 11px 14px;
  background: var(--color-neutral-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  font-family: 'SF Mono', Monaco, Menlo, Consolas, monospace;
  font-size: 0.76rem;
  line-height: 1.6;
  color: var(--color-text);
  white-space: pre;
  overflow: auto;
  tab-size: 2;
}

.json-highlight :deep(.j-key) {
  color: var(--color-primary-hover);
  font-weight: 500;
}

.json-highlight :deep(.j-str) {
  color: var(--color-teal);
}

.json-highlight :deep(.j-num) {
  color: var(--color-warning);
}

.json-highlight :deep(.j-bool) {
  color: var(--color-purple);
}

.json-highlight :deep(.j-null) {
  color: var(--color-text-muted);
  font-style: italic;
}

.json-highlight :deep(.j-punct) {
  color: var(--color-text-light);
}

.json-empty {
  padding: 10px 14px;
  background: var(--color-neutral-bg);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-base);
  font-size: 0.78rem;
  font-style: italic;
  color: var(--color-text-muted);
}
</style>
