<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
-->

<script setup lang="ts">
import { computed } from 'vue';
import { replyMarkdown } from '@/composables/replyMarkdown';
import { REPLY_FORMATS, useReplyFormat, type ReplyFormat } from '@/composables/useReplyFormat';

// A run's final reply, as the text the agent wrote or rendered as the Markdown it is written in.

const props = defineProps<{ reply: string; replyChars: number | null }>();

const FORMAT_LABELS: Record<ReplyFormat, string> = { text: 'Text', markdown: 'Markdown' };
const PANEL_ID = 'replay-reply-panel';

const { format, choose } = useReplyFormat();

const total = computed(() => Math.max(props.replyChars ?? 0, props.reply.length));
const size = computed(() => {
  const kept = props.reply.length.toLocaleString('en-US');
  return total.value > props.reply.length
    ? `the first ${kept} of ${total.value.toLocaleString('en-US')} characters`
    : `${kept} characters`;
});
const html = computed(() => (format.value === 'markdown' ? replyMarkdown(props.reply) : ''));

function tabId(option: ReplyFormat): string {
  return `replay-reply-tab-${option}`;
}

// Arrow keys move between the two tabs, as in any tab list.
function onKey(event: KeyboardEvent): void {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') {
    return;
  }
  event.preventDefault();
  const step = event.key === 'ArrowRight' ? 1 : -1;
  const index = REPLY_FORMATS.indexOf(format.value);
  const next = REPLY_FORMATS[(index + step + REPLY_FORMATS.length) % REPLY_FORMATS.length];
  choose(next);
  document.getElementById(tabId(next))?.focus();
}
</script>

<template>
  <div class="reply-card">
    <div class="reply-head">
      <span class="reply-size">final message · {{ size }}</span>
      <div class="reply-tabs" role="tablist" aria-label="Reply format" @keydown="onKey">
        <button
          v-for="option in REPLY_FORMATS"
          :id="tabId(option)"
          :key="option"
          type="button"
          role="tab"
          class="reply-tab"
          :class="{ 'reply-tab--on': option === format }"
          :aria-selected="option === format"
          :aria-controls="PANEL_ID"
          :tabindex="option === format ? 0 : -1"
          @click="choose(option)"
        >
          {{ FORMAT_LABELS[option] }}
        </button>
      </div>
    </div>
    <div :id="PANEL_ID" class="reply-body" role="tabpanel" :aria-labelledby="tabId(format)">
      <p v-if="format === 'text'" class="reply-text">{{ reply }}</p>
      <!-- eslint-disable-next-line vue/no-v-html -- replyMarkdown escapes the reply's own HTML and unsafe links -->
      <div v-else class="reply-md" v-html="html"></div>
    </div>
  </div>
</template>

<style scoped>
.reply-card {
  border: 1px solid var(--site-line);
  border-radius: var(--site-radius-sm);
  background: var(--site-surface);
  overflow: hidden;
  white-space: normal;
}

.reply-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 4px 12px;
  padding: 0 12px;
  border-bottom: 1px solid var(--site-line);
}

.reply-size {
  padding: 9px 0;
  font-family: var(--site-font-mono);
  font-size: 12px;
  color: var(--site-faint);
}

.reply-tabs {
  display: flex;
  gap: 4px;
}

/* The page's own tabs, smaller: the active one changes colour and gets the flame underline. */
.reply-tab {
  position: relative;
  padding: 9px 6px 8px;
  border: 0;
  background: transparent;
  color: var(--site-muted);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.reply-tab:hover {
  color: var(--site-ink);
}

.reply-tab:focus-visible {
  outline: 2px solid var(--site-flame);
  outline-offset: -2px;
  border-radius: var(--ap-radius-xs);
}

.reply-tab--on {
  color: var(--site-ink);
}

.reply-tab--on::after {
  content: '';
  position: absolute;
  left: 4px;
  right: 4px;
  bottom: 0;
  height: 2px;
  border-radius: var(--ap-radius-xs);
  background: linear-gradient(90deg, var(--ap-tab-start), var(--site-flame));
}

.reply-body {
  max-height: 420px;
  overflow-y: auto;
}

.reply-text {
  margin: 0;
  padding: 10px 12px;
  font-family: var(--site-font-mono);
  font-size: 12.5px;
  line-height: 1.55;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

/* The rendered reply, in the site's own type; v-html content is reached through :deep. */
.reply-md {
  padding: 12px 16px 14px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--site-ink);
  overflow-wrap: break-word;
}

.reply-md :deep(> :first-child) {
  margin-top: 0;
}

.reply-md :deep(> :last-child) {
  margin-bottom: 0;
}

.reply-md :deep(p),
.reply-md :deep(ul),
.reply-md :deep(ol),
.reply-md :deep(blockquote) {
  margin: 0 0 10px;
}

.reply-md :deep(ul),
.reply-md :deep(ol) {
  padding-left: 20px;
}

.reply-md :deep(li + li) {
  margin-top: 4px;
}

.reply-md :deep(h1),
.reply-md :deep(h2),
.reply-md :deep(h3),
.reply-md :deep(h4) {
  margin: 16px 0 8px;
  font-size: 15px;
  font-weight: 600;
}

.reply-md :deep(blockquote) {
  padding-left: 12px;
  border-left: 2px solid var(--site-line-strong);
  color: var(--site-muted);
}

/* The ink, not Bootstrap's pink for every code element. */
.reply-md :deep(code) {
  padding: 1px 4px;
  border-radius: var(--ap-radius-xs);
  background: var(--ap-hover);
  color: var(--site-ink);
  font-family: var(--site-font-mono);
  font-size: 12px;
}

.reply-md :deep(pre) {
  margin: 0 0 10px;
  padding: 10px 12px;
  border-radius: var(--ap-radius-xs);
  background: var(--ap-hover);
  overflow-x: auto;
}

.reply-md :deep(pre code) {
  padding: 0;
  background: none;
}

.reply-md :deep(a) {
  color: var(--site-ember);
  text-decoration: none;
  border-bottom: 1px solid currentColor;
}

.reply-md :deep(a:hover) {
  border-bottom-width: 2px;
}

.reply-md :deep(strong) {
  font-weight: 600;
}

.reply-md :deep(hr) {
  margin: 14px 0;
  border: 0;
  border-top: 1px solid var(--site-line);
}

.reply-md :deep(.reply-md-table) {
  margin: 0 0 12px;
  overflow-x: auto;
  border: 1px solid var(--site-line);
  border-radius: var(--ap-radius-xs);
}

.reply-md :deep(table) {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.reply-md :deep(th) {
  padding: 8px 10px;
  border-bottom: 1px solid var(--site-line-strong);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-align: left;
  text-transform: uppercase;
  color: var(--site-faint);
  white-space: nowrap;
}

.reply-md :deep(td) {
  padding: 7px 10px;
  border-bottom: 1px solid var(--site-line);
  vertical-align: top;
}

.reply-md :deep(tr:last-child td) {
  border-bottom: 0;
}
</style>
