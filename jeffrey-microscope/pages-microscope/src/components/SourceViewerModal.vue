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
    size="fullscreen"
    modal-dialog-class="source-viewer-dialog"
    :show-footer="false"
    @update:show="onShowChange"
    @shown="scheduleScroll"
    @hidden="reset"
  >
    <template #title>
      <span v-if="fqnPackage" class="hdr-pkg">{{ fqnPackage }}</span><span class="hdr-cls">{{
        fqnClass
      }}</span>
    </template>

    <div v-if="hasValidLine || decompiled" class="source-subtitle">
      <Badge
        v-if="hasValidLine"
        key-label="Line"
        :value="line"
        variant="secondary"
        size="s"
        :uppercase="false"
      />
      <Badge v-if="decompiled" variant="danger" value="Decompiled" size="s" />
    </div>

    <div ref="viewerRef" class="source-viewer">
      <div v-if="hasValidLine" class="line-highlight" :style="{ top: highlightTop }"></div>
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
import GenericModal from '@shared/components/GenericModal.vue';
import Badge from '@shared/components/Badge.vue';
import MessageBus from '@/services/MessageBus';
import IdeClient from '@/services/api/IdeClient';
import IdeTargetService from '@/services/IdeTargetService';
import { ToastService } from '@shared/services/ToastService';

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

const SOURCE_UNAVAILABLE_TITLE = 'Source unavailable';
const NO_IDE_MESSAGE =
  'No running IDE was found. Open your project in IntelliJ with the Jeffrey plugin installed.';

const show = ref(false);
const fqn = ref('');
const title = ref('');
const line = ref(-1);
const decompiled = ref(false);
const highlightedHtml = ref('');
const lineCount = ref(0);
const viewerRef = ref<HTMLElement | null>(null);

// Split the FQN so the header can show the package muted and the class name emphasized.
const lastDotIndex = computed(() => fqn.value.lastIndexOf('.'));
const fqnPackage = computed(() =>
  lastDotIndex.value > 0 ? fqn.value.slice(0, lastDotIndex.value + 1) : ''
);
const fqnClass = computed(() =>
  lastDotIndex.value > 0 ? fqn.value.slice(lastDotIndex.value + 1) : fqn.value
);

const gutterText = computed(() =>
  Array.from({ length: lineCount.value }, (_, i) => i + 1).join('\n')
);

// The selected line is only valid when it falls within the fetched content and the source is real.
// Lines past the last line (stale/mismatched info) or decompiled bytecode (line numbers don't match
// the original) are ignored — no highlight, no scroll.
const hasValidLine = computed(
  () => line.value > 0 && line.value <= lineCount.value && !decompiled.value
);

const highlightTop = computed(
  () => `${VIEWER_PADDING_Y_PX + (line.value - 1) * LINE_HEIGHT_PX}px`
);

async function openSource(payload: ViewSourcePayload): Promise<void> {
  let content: string;
  let decompiledFlag = false;
  try {
    const { target, reason } = await IdeTargetService.resolve(payload.profileId, payload.fqn);
    if (!target) {
      if (reason === 'no-ide') {
        ToastService.warn(SOURCE_UNAVAILABLE_TITLE, NO_IDE_MESSAGE);
      }
      // 'cancelled' — user dismissed the picker; stay silent.
      return;
    }
    const response = await new IdeClient().fetchSource(payload.profileId, payload.fqn, payload.method);
    if (!response.success || !response.content) {
      ToastService.warn(SOURCE_UNAVAILABLE_TITLE, response.message ?? 'Source is not available for this class');
      return;
    }
    content = response.content;
    decompiledFlag = response.decompiled === true;
  } catch (err) {
    ToastService.warn(SOURCE_UNAVAILABLE_TITLE, err instanceof Error ? err.message : String(err));
    return;
  }

  // Only open the modal once the source is actually available.
  fqn.value = payload.fqn;
  title.value = payload.title;
  line.value = payload.line;
  decompiled.value = decompiledFlag;
  highlightedHtml.value = hljs.highlight(content, { language: 'java' }).value;
  lineCount.value = content.split('\n').length;
  show.value = true;
  scheduleScroll();
}

/** Scroll to the selected line once the modal content has been laid out and painted. */
function scheduleScroll(): void {
  void nextTick(() => requestAnimationFrame(scrollToLine));
}

function scrollToLine(): void {
  const viewer = viewerRef.value;
  if (!viewer) {
    return;
  }
  if (!hasValidLine.value) {
    // No proper line to jump to — keep the view at the beginning.
    viewer.scrollTop = 0;
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
  fqn.value = '';
  title.value = '';
  line.value = -1;
  decompiled.value = false;
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

/* Make the "LINE" key label bold (Badge renders it muted/medium by default). */
.source-subtitle :deep(.badge-key) {
  font-weight: 700;
  text-transform: uppercase;
}

/* Header FQN: package de-emphasized, class name emphasized. */
.hdr-pkg {
  font-weight: 400;
  color: var(--color-text-muted);
}

.hdr-cls {
  font-weight: 700;
  color: var(--color-dark);
}

.source-viewer {
  position: relative;
  max-height: 70vh;
  overflow: auto;
  /* Stop scroll from chaining to the page behind when the viewer hits its top/bottom. */
  overscroll-behavior: contain;
  padding: 8px 0;
  display: flex;
  /* Let the gutter/code size to their full content height instead of being stretched to the
     container — only .source-viewer scrolls, gutter + code scroll together as one unit. */
  align-items: flex-start;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  font-family: var(--font-mono, monospace);
  font-size: 13px;
  line-height: 20px;
  tab-size: 4;
  -moz-tab-size: 4;
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
  /* Bootstrap's reboot sets `pre { overflow: auto }`, which would make each pre its own
     scroll container (two scrollbars + independent clipping). Keep scrolling on .source-viewer. */
  overflow: visible;
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

/* Force the inner <code> to use the exact same font metrics as the gutter so every row lines up
   (the UA stylesheet's `code { font-family: monospace }` otherwise overrides inheritance). */
.code code {
  display: block;
  font: inherit;
  white-space: pre;
}
</style>

<!-- Global: the dialog lives inside GenericModal, so it can't be reached by scoped styles.
     Vertically centre this modal so the top and bottom gaps from the window are equal,
     mirroring the 95vw width's even side margins. -->
<style>
.modal-dialog.source-viewer-dialog {
  display: flex;
  align-items: center;
  min-height: calc(100% - 5vh);
  margin: 2.5vh auto;
}
</style>
