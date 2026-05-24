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
  <GenericModal
    modal-id="sourceViewerModal"
    :show="show"
    :title="title || 'Source'"
    icon="bi bi-file-earmark-code"
    size="xl"
    :show-footer="false"
    @update:show="onShowChange"
    @shown="scrollToLine"
    @hidden="reset"
  >
    <div v-if="fqn" class="source-subtitle">
      <span class="source-fqn">{{ fqn }}</span>
      <span v-if="line > 0" class="source-line">line {{ line }}</span>
    </div>

    <LoadingState v-if="loading" message="Fetching source…" />
    <ErrorState v-else-if="error" :message="error" />
    <div v-else ref="viewerRef" class="source-viewer">
      <div v-if="line > 0" class="line-highlight" :style="{ top: highlightTop }"></div>
      <pre class="gutter">{{ gutterText }}</pre>
      <pre class="code"><code v-html="highlightedHtml"></code></pre>
    </div>
  </GenericModal>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import hljs from 'highlight.js/lib/core';
import java from 'highlight.js/lib/languages/java';
import 'highlight.js/styles/github.css';
import GenericModal from '@/components/GenericModal.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import MessageBus from '@/services/MessageBus';
import IdeClient from '@/services/api/IdeClient';
import IdeTargetService from '@/services/IdeTargetService';

hljs.registerLanguage('java', java);

const LINE_HEIGHT_PX = 20;
const VIEWER_PADDING_Y_PX = 8;

interface ViewSourcePayload {
  profileId: string;
  fqn: string;
  method: string;
  line: number;
  title: string;
}

const show = ref(false);
const loading = ref(false);
const error = ref<string | null>(null);
const fqn = ref('');
const title = ref('');
const line = ref(-1);
const highlightedHtml = ref('');
const lineCount = ref(0);
const viewerRef = ref<HTMLElement | null>(null);

const gutterText = computed(() =>
  Array.from({ length: lineCount.value }, (_, i) => i + 1).join('\n')
);

const highlightTop = computed(
  () => `${VIEWER_PADDING_Y_PX + (line.value - 1) * LINE_HEIGHT_PX}px`
);

async function openSource(payload: ViewSourcePayload): Promise<void> {
  fqn.value = payload.fqn;
  title.value = payload.title;
  line.value = payload.line;
  highlightedHtml.value = '';
  lineCount.value = 0;
  error.value = null;
  loading.value = true;
  show.value = true;

  try {
    const { target, reason } = await IdeTargetService.resolve(payload.profileId, payload.fqn);
    if (!target) {
      error.value = reason === 'no-ide'
        ? 'No running IDE was found. Open your project in IntelliJ with the Jeffrey plugin installed.'
        : 'No IDE window selected.';
      return;
    }
    const response = await new IdeClient().fetchSource(payload.profileId, payload.fqn, payload.method);
    if (!response.success || !response.content) {
      error.value = response.message ?? 'Source is not available for this class';
      return;
    }
    highlightedHtml.value = hljs.highlight(response.content, { language: 'java' }).value;
    lineCount.value = response.content.split('\n').length;
    await nextTick();
    scrollToLine();
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err);
  } finally {
    loading.value = false;
  }
}

function scrollToLine(): void {
  const viewer = viewerRef.value;
  if (!viewer || line.value <= 0) {
    return;
  }
  const lineCenter = VIEWER_PADDING_Y_PX + (line.value - 1) * LINE_HEIGHT_PX + LINE_HEIGHT_PX / 2;
  viewer.scrollTop = Math.max(0, lineCenter - viewer.clientHeight / 2);
}

function onShowChange(value: boolean): void {
  show.value = value;
}

function reset(): void {
  highlightedHtml.value = '';
  lineCount.value = 0;
  error.value = null;
  fqn.value = '';
  title.value = '';
  line.value = -1;
}

onMounted(() => {
  MessageBus.on(MessageBus.IDE_VIEW_SOURCE, openSource);
});

onUnmounted(() => {
  MessageBus.off(MessageBus.IDE_VIEW_SOURCE, openSource);
});
</script>

<style scoped>
.source-subtitle {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  font-size: 12px;
}

.source-fqn {
  font-family: var(--font-mono, monospace);
  color: var(--color-text-muted);
  word-break: break-all;
}

.source-line {
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: var(--radius-sm);
  background: var(--color-warning-bg);
  color: var(--color-warning-text);
  font-weight: 600;
}

.source-viewer {
  position: relative;
  max-height: 70vh;
  overflow: auto;
  padding: 8px 0;
  display: flex;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  font-family: var(--font-mono, monospace);
  font-size: 13px;
  line-height: 20px;
}

.line-highlight {
  position: absolute;
  left: 0;
  right: 0;
  height: 20px;
  background: var(--color-warning-bg);
  z-index: 0;
}

.gutter,
.code {
  position: relative;
  z-index: 1;
  margin: 0;
  padding: 0;
  background: transparent;
  font-family: inherit;
  font-size: inherit;
  line-height: inherit;
}

.gutter {
  flex-shrink: 0;
  padding: 0 12px;
  text-align: right;
  color: var(--color-text-light);
  user-select: none;
  border-right: 1px solid var(--color-border);
}

.code {
  flex: 1;
  padding: 0 14px;
  color: var(--color-dark);
  white-space: pre;
  overflow: visible;
}
</style>
