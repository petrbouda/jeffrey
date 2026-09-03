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
  A unified diff, rendered with line numbers and the files it touches, plus the two actions a reader
  of a proposed change actually wants: copy it, or save it as a .patch that `git apply` accepts.
-->

<template>
  <div class="diff">
    <div class="dv-bar">
      <span class="dv-files">
        <span v-for="file in parsed.files" :key="file.path" class="dv-file" :title="file.path">
          <i class="bi bi-file-earmark-diff"></i>
          {{ shortPath(file.path) }}
        </span>
      </span>
      <span class="dv-stat">
        <span class="plus">+{{ parsed.added }}</span>
        <span class="minus">−{{ parsed.removed }}</span>
      </span>
      <button type="button" class="dv-btn" @click="copy">
        <i class="bi" :class="copied ? 'bi-check-lg' : 'bi-clipboard'"></i>
        {{ copied ? 'Copied' : 'Copy' }}
      </button>
      <button type="button" class="dv-btn primary" @click="download">
        <i class="bi bi-download"></i>
        Save .patch
      </button>
    </div>

    <div class="dv-body">
      <div v-for="(line, index) in parsed.lines" :key="index" class="dl" :class="line.type">
        <span class="dl-num">{{ lineNumberOf(line) }}</span>
        <span class="dl-mark">{{ markerOf(line.type) }}</span>
        <span class="dl-code">{{ line.text }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import ToastService from '@shared/services/ToastService';
import { parseUnifiedDiff, type DiffLine } from '@shared/services/unifiedDiff';

const props = withDefaults(
  defineProps<{
    patch: string;
    /** Basename for the downloaded file, without the .patch suffix. */
    fileName?: string;
  }>(),
  { fileName: 'changes' }
);

const PATH_SEGMENTS_SHOWN = 2;
const COPIED_FEEDBACK_MS = 1500;

const copied = ref(false);

const parsed = computed(() => parseUnifiedDiff(props.patch));

function lineNumberOf(line: DiffLine): string {
  if (line.type === 'hunk' || line.type === 'meta') {
    return '';
  }
  const number = line.type === 'del' ? line.oldLine : line.newLine;
  return number === null ? '' : String(number);
}

function markerOf(type: DiffLine['type']): string {
  if (type === 'add') {
    return '+';
  }
  if (type === 'del') {
    return '−';
  }
  return ' ';
}

function shortPath(path: string): string {
  const segments = path.split('/');
  return segments.slice(-PATH_SEGMENTS_SHOWN).join('/');
}

async function copy(): Promise<void> {
  try {
    await navigator.clipboard.writeText(props.patch);
    copied.value = true;
    setTimeout(() => {
      copied.value = false;
    }, COPIED_FEEDBACK_MS);
  } catch {
    ToastService.error('Patch', 'The browser blocked access to the clipboard');
  }
}

function download(): void {
  const blob = new Blob([props.patch], { type: 'text/x-patch' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `${props.fileName}.patch`;
  anchor.click();
  URL.revokeObjectURL(url);
}
</script>

<style scoped>
.diff {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--color-bg-card);
}

.dv-bar {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
  padding: 0.6rem 0.7rem;
  background: var(--color-light);
  border-bottom: 1px solid var(--color-border);
}

.dv-files {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex: 1;
  min-width: 0;
  flex-wrap: wrap;
}

.dv-file {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-family: var(--font-family-monospace);
  font-size: 0.73rem;
  color: var(--color-primary);
}

.dv-stat {
  font-family: var(--font-family-monospace);
  font-size: 0.73rem;
  display: inline-flex;
  gap: 0.35rem;
}

.dv-stat .plus {
  color: var(--color-success-dark);
  font-weight: 600;
}

.dv-stat .minus {
  color: var(--color-danger-dark);
  font-weight: 600;
}

.dv-btn {
  appearance: none;
  cursor: pointer;
  font-family: inherit;
  font-size: 0.74rem;
  font-weight: 600;
  border-radius: var(--radius-base);
  border: 1px solid var(--color-primary-border-light);
  background: transparent;
  color: var(--color-primary);
  padding: 0.28rem 0.6rem;
  display: inline-flex;
  align-items: center;
  gap: 0.32rem;
  white-space: nowrap;
  transition: background 0.15s;
}

.dv-btn:hover {
  background: var(--color-primary-light);
}

.dv-btn.primary {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-white);
}

.dv-btn.primary:hover {
  background: var(--color-primary-hover);
}

.dv-body {
  font-family: var(--font-family-monospace);
  font-size: 0.76rem;
  line-height: 1.65;
  overflow-x: auto;
}

.dl {
  display: flex;
  align-items: stretch;
  white-space: pre;
}

.dl-num {
  width: 3.1rem;
  flex: none;
  text-align: right;
  padding: 0 0.5rem;
  color: var(--color-text-muted);
  background: var(--color-light);
  border-right: 1px solid var(--color-border-light);
  user-select: none;
  font-size: 0.7rem;
  opacity: 0.75;
}

.dl-mark {
  width: 1.15rem;
  flex: none;
  text-align: center;
  user-select: none;
  font-weight: 700;
}

.dl-code {
  flex: 1;
  padding-right: 0.75rem;
}

.dl.add {
  background: var(--color-success-light);
}

.dl.add .dl-mark {
  color: var(--color-success-dark);
}

.dl.del {
  background: var(--color-danger-light);
}

.dl.del .dl-mark {
  color: var(--color-danger-dark);
}

.dl.hunk,
.dl.meta {
  background: var(--color-light);
  color: var(--color-text-muted);
  font-size: 0.7rem;
}

.dl.hunk {
  border-top: 1px solid var(--color-border-light);
  border-bottom: 1px solid var(--color-border-light);
}
</style>
