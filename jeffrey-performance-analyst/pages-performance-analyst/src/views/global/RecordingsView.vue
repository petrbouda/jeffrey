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
  <MainCard>
      <template #header>
        <MainCardHeader
          icon="bi-record-circle"
          title="Recordings"
          :badge="recordings.length"
        />
      </template>

      <LoadingState v-if="loading" message="Loading recordings..." />
      <ErrorState v-else-if="error" :message="error" />
      <template v-else>
        <EmptyState
          v-if="recordings.length === 0"
          icon="bi-inbox"
          title="No recordings yet"
          message="Recordings downloaded from a project's sessions will appear here."
        />
        <div v-else class="recording-list">
          <div v-for="recording in recordings" :key="recording.id" class="recording-item">
            <div class="recording-row" @click="toggleExpand(recording.id)">
              <i
                class="bi expand-caret"
                :class="isExpanded(recording.id) ? 'bi-chevron-down' : 'bi-chevron-right'"
              ></i>
              <div class="recording-main">
                <div class="recording-name" :title="recording.filename">
                  {{ recording.filename }}
                </div>
                <div class="recording-meta">
                  <span class="meta-sep">{{ recording.files.length }} file(s)</span>
                  <span class="meta-sep">{{ formatBytes(recording.sizeInBytes) }}</span>
                  <span v-if="recording.durationInMillis > 0" class="meta-sep">
                    {{ formatDuration(recording.durationInMillis) }}
                  </span>
                  <span class="meta-sep">{{ formatTimestamp(recording.uploadedAt) }}</span>
                  <span v-if="ai(recording.id).loading" class="ai-meta gen">
                    <i class="bi bi-arrow-repeat spin"></i> generating prompts…
                  </span>
                  <span v-else-if="isGenerated(recording.id)" class="ai-meta">
                    <i class="bi bi-check-circle-fill"></i> AI prompts ready · <b>{{ promptLabels(recording.id) }}</b>
                  </span>
                </div>
              </div>
              <button
                v-if="hasJfr(recording)"
                class="ai-row-btn"
                :class="isGenerated(recording.id) ? 'outline' : 'primary'"
                :disabled="ai(recording.id).loading"
                @click.stop="isGenerated(recording.id) ? viewPrompts(recording) : generatePrompts(recording)"
              >
                <i class="bi" :class="rowBtnIcon(recording.id)"></i>
                {{ rowBtnLabel(recording.id) }}
              </button>
            </div>

            <div v-if="isExpanded(recording.id)" class="recording-body">
              <div class="rec-tabs">
                <template v-if="ai(recording.id).prompts">
                  <button
                    v-for="prompt in ai(recording.id).prompts"
                    :key="prompt.eventType"
                    class="rec-tab"
                    :class="{ active: tabOf(recording.id) === prompt.eventType }"
                    @click="setTab(recording.id, prompt.eventType)"
                  >
                    <i class="bi bi-robot"></i> {{ prompt.label }} Prompt
                  </button>
                </template>
                <button
                  class="rec-tab"
                  :class="{ active: tabOf(recording.id) === 'files' }"
                  @click="setTab(recording.id, 'files')"
                >
                  <i class="bi bi-folder2"></i> Files
                </button>
              </div>

              <div class="rec-tab-body">
                <RecordingFileGroupList
                  v-if="tabOf(recording.id) === 'files'"
                  :recording-id="recording.id"
                  :files="recording.files"
                  @download="downloadFile"
                />
                <template v-else>
                  <LoadingState v-if="ai(recording.id).loading" message="Parsing JFR & building prompt…" />
                  <div v-else-if="ai(recording.id).error" class="ai-error">
                    <i class="bi bi-exclamation-triangle"></i> {{ ai(recording.id).error }}
                  </div>
                  <EmptyState
                    v-else-if="ai(recording.id).prompts && ai(recording.id).prompts.length === 0"
                    icon="bi-robot"
                    title="No CPU or wall-clock samples"
                    message="This recording has no jdk.ExecutionSample or profiler.WallClockSample events."
                  />
                  <div v-else-if="activePrompt(recording.id)" class="ai-pane">
                    <div class="ai-toolbar">
                      <span v-if="activeSamples(recording.id) > 0" class="ai-chip">
                        {{ formatCount(activeSamples(recording.id)) }} samples
                      </span>
                      <span class="ai-spacer"></span>
                      <button class="ai-action solid" @click="copyActive(recording.id)">
                        <i class="bi bi-clipboard"></i> Copy for AI
                      </button>
                      <button class="ai-action" @click="downloadActive(recording)">
                        <i class="bi bi-download"></i> Download .md
                      </button>
                    </div>
                    <div class="ai-markdown" v-html="activeMarkdownHtml(recording.id)"></div>
                  </div>
                </template>
              </div>
            </div>
          </div>
        </div>
      </template>
  </MainCard>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { marked } from 'marked';
import MainCard from '@/components/MainCard.vue';
import MainCardHeader from '@/components/MainCardHeader.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import RecordingFileGroupList from '@workspaces/components/RecordingFileGroupList.vue';
import RecordingsClient from '@workspaces/services/api/RecordingsClient';
import type Recording from '@workspaces/services/api/model/Recording';
import RecordingFileType from '@workspaces/services/api/model/RecordingFileType';
import FormattingService from '@shared/services/FormattingService';
import ToastService from '@shared/services/ToastService';
import FlamegraphAiExportClient from '@/services/api/FlamegraphAiExportClient';
import type AiPrompt from '@/services/api/model/AiPrompt';

interface AiState {
  loading: boolean;
  error: string | null;
  prompts: AiPrompt[] | null;
}

const FILES_TAB = 'files';
const AI_PENDING_TAB = 'ai';
const EMPTY_AI_STATE: AiState = { loading: false, error: null, prompts: null };

const recordingsClient = new RecordingsClient();
const aiExportClient = new FlamegraphAiExportClient();

const recordings = ref<Recording[]>([]);
const loading = ref(true);
const error = ref<string | null>(null);
const expanded = ref<Record<string, boolean>>({});
const activeTab = ref<Record<string, string>>({});
const aiStates = ref<Record<string, AiState>>({});

const formatBytes = (bytes: number) => FormattingService.formatBytes(bytes);
const formatTimestamp = (millis: number) => FormattingService.formatTimestamp(millis);
const formatDuration = (millis: number) => FormattingService.formatDurationMillisCompact(millis);
const formatCount = (n: number) => n.toLocaleString('en-US');

const isExpanded = (recordingId: string): boolean => !!expanded.value[recordingId];

const toggleExpand = (recordingId: string) => {
  expanded.value[recordingId] = !expanded.value[recordingId];
};

const downloadFile = (recordingId: string, fileId: string) => {
  recordingsClient.downloadFile(recordingId, fileId);
};

const hasJfr = (recording: Recording): boolean =>
  recording.files.some(
    file => file.type === RecordingFileType.JFR || file.type === RecordingFileType.JFR_LZ4
  );

// --- AI prompt tabs ---

const ai = (recordingId: string): AiState => aiStates.value[recordingId] ?? EMPTY_AI_STATE;

const tabOf = (recordingId: string): string => {
  const explicit = activeTab.value[recordingId];
  if (explicit) {
    return explicit;
  }
  // When prompts are present, default to the first prompt (Files is the de-emphasized, rightmost tab).
  const prompts = aiStates.value[recordingId]?.prompts;
  if (prompts && prompts.length > 0) {
    return prompts[0].eventType;
  }
  return FILES_TAB;
};
const setTab = (recordingId: string, tab: string) => {
  activeTab.value[recordingId] = tab;
};

const activePrompt = (recordingId: string): AiPrompt | null => {
  const prompts = aiStates.value[recordingId]?.prompts;
  if (!prompts) {
    return null;
  }
  return prompts.find(prompt => prompt.eventType === tabOf(recordingId)) ?? null;
};

const activeSamples = (recordingId: string): number => activePrompt(recordingId)?.samples ?? 0;

const activeMarkdownHtml = (recordingId: string): string => {
  const prompt = activePrompt(recordingId);
  return prompt ? renderMarkdown(prompt.markdown) : '';
};

const renderMarkdown = (markdown: string): string => marked.parse(markdown, { breaks: true }) as string;

const loadPrompts = async (recording: Recording) => {
  const id = recording.id;
  activeTab.value[id] = AI_PENDING_TAB;
  if (aiStates.value[id]?.prompts) {
    return;
  }

  aiStates.value[id] = { loading: true, error: null, prompts: null };
  try {
    const prompts = await aiExportClient.generate(id);
    aiStates.value[id] = { loading: false, error: null, prompts };
    if (prompts.length > 0) {
      activeTab.value[id] = prompts[0].eventType;
    }
  } catch (e) {
    console.error('Failed to generate AI prompt:', e);
    aiStates.value[id] = {
      loading: false,
      error: 'Could not generate the AI flamegraph prompt.',
      prompts: null,
    };
  }
};

const copyActive = (recordingId: string) => {
  const prompt = activePrompt(recordingId);
  if (!prompt) {
    return;
  }
  navigator.clipboard?.writeText(prompt.markdown);
  ToastService.success('Copied for AI', 'Paste it into Claude.');
};

const downloadActive = (recording: Recording) => {
  const prompt = activePrompt(recording.id);
  if (!prompt) {
    return;
  }
  const blob = new Blob([prompt.markdown], { type: 'text/markdown' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `${recording.filename}-${prompt.label}.md`;
  anchor.click();
  URL.revokeObjectURL(url);
};

// --- recording-row status & actions ---

const isGenerated = (recordingId: string): boolean =>
  (aiStates.value[recordingId]?.prompts?.length ?? 0) > 0;

const promptLabels = (recordingId: string): string =>
  (aiStates.value[recordingId]?.prompts ?? []).map(prompt => prompt.label).join(' · ');

const rowBtnLabel = (recordingId: string): string =>
  ai(recordingId).loading ? 'Generating…' : isGenerated(recordingId) ? 'View AI Prompts' : 'Generate AI Prompt';

const rowBtnIcon = (recordingId: string): string =>
  ai(recordingId).loading ? 'bi-arrow-repeat spin' : isGenerated(recordingId) ? 'bi-eye' : 'bi-robot';

const generatePrompts = (recording: Recording) => {
  expanded.value[recording.id] = true;
  loadPrompts(recording);
};

const viewPrompts = (recording: Recording) => {
  expanded.value[recording.id] = true;
  const prompts = aiStates.value[recording.id]?.prompts;
  if (prompts && prompts.length > 0) {
    activeTab.value[recording.id] = prompts[0].eventType;
  } else {
    loadPrompts(recording);
  }
};

const peekPrompts = async (recordingId: string) => {
  try {
    const cached = await aiExportClient.peek(recordingId);
    if (cached.length > 0) {
      aiStates.value[recordingId] = { loading: false, error: null, prompts: cached };
    }
  } catch {
    // best-effort: a failed peek just leaves the row in the "not generated" state
  }
};

const loadRecordings = async () => {
  loading.value = true;
  error.value = null;
  try {
    recordings.value = await recordingsClient.listRecordings();
    recordings.value.filter(hasJfr).forEach(recording => peekPrompts(recording.id));
  } catch (e) {
    console.error('Failed to load recordings:', e);
    error.value = 'Failed to load recordings.';
  } finally {
    loading.value = false;
  }
};

onMounted(loadRecordings);
</script>

<style scoped>
.recording-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.recording-item {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.recording-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  cursor: pointer;
  transition: background-color 0.15s;
}

.recording-row:hover {
  background-color: var(--color-light);
}

.expand-caret {
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.recording-main {
  flex: 1;
  min-width: 0;
}

.recording-name {
  font-weight: 600;
  color: var(--color-dark);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.recording-meta {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-top: 0.25rem;
  font-size: 0.8rem;
  color: var(--color-text-muted);
}

.ai-meta {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-weight: 400;
  color: var(--color-success-dark);
}

.ai-meta b {
  font-weight: 600;
}

.ai-meta.gen {
  color: var(--color-primary);
}

.ai-row-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  flex-shrink: 0;
  white-space: nowrap;
  font-size: 0.8rem;
  font-weight: 500;
  padding: 0.45rem 0.8rem;
  border: 1px solid transparent;
  border-radius: var(--radius-base);
  cursor: pointer;
  transition: all 0.15s;
}

.ai-row-btn.primary {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-white);
}

.ai-row-btn.primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
}

.ai-row-btn.outline {
  background: transparent;
  border-color: var(--color-primary-border);
  color: var(--color-primary);
}

.ai-row-btn.outline:hover:not(:disabled) {
  background: var(--color-primary);
  color: var(--color-white);
}

.ai-row-btn:disabled {
  opacity: 0.7;
  cursor: default;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* --- expanded body: tabs --- */
.recording-body {
  border-top: 1px solid var(--color-border);
  background-color: var(--color-light);
}

.rec-tabs {
  display: flex;
  gap: 0.125rem;
  padding: 0 0.75rem;
  border-bottom: 1px solid var(--color-border);
}

.rec-tab {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.625rem 0.875rem;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-text-muted);
  background: transparent;
  border: 0;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  transition: color 0.15s;
}

.rec-tab:hover {
  color: var(--color-dark);
}

.rec-tab.active {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
}

.rec-tab-ai {
  color: var(--color-primary);
}

.rec-tab:disabled {
  opacity: 0.7;
  cursor: default;
}

.rec-tab-body {
  padding: 0.75rem 1rem 1rem;
}

/* --- prompt pane --- */
.ai-toolbar {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  margin-bottom: 0.625rem;
}

.ai-chip {
  font-size: 0.7rem;
  font-weight: 600;
  padding: 0.1875rem 0.625rem;
  border-radius: 999px;
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.ai-spacer {
  flex: 1;
}

.ai-action {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  font-size: 0.78rem;
  font-weight: 500;
  color: var(--color-primary);
  background: transparent;
  border: 1px solid var(--color-primary-border);
  padding: 0.375rem 0.75rem;
  border-radius: var(--radius-base);
  cursor: pointer;
  transition: all 0.15s;
}

.ai-action:hover {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-white);
}

.ai-action.solid {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-white);
}

.ai-action.solid:hover {
  background: var(--color-primary-hover);
}

.ai-error {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--color-danger);
  font-size: 0.85rem;
  padding: 0.75rem;
}

.ai-markdown {
  max-height: 28rem;
  overflow: auto;
  padding: 0.875rem 1rem;
  font-size: 0.82rem;
  line-height: 1.6;
  color: var(--color-text);
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
}

.ai-markdown :deep(h1),
.ai-markdown :deep(h2),
.ai-markdown :deep(h3) {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--color-dark);
  margin: 0.875rem 0 0.5rem;
}

.ai-markdown :deep(p) {
  margin: 0.375rem 0;
}

.ai-markdown :deep(ul) {
  margin: 0.25rem 0;
  padding-left: 1.125rem;
}

.ai-markdown :deep(li) {
  margin: 0.0625rem 0;
}

.ai-markdown :deep(code) {
  font-family: ui-monospace, 'JetBrains Mono', Menlo, Consolas, monospace;
  font-size: 0.78rem;
  background: var(--color-code-bg);
  color: var(--color-code-text);
  padding: 0.0625rem 0.3125rem;
  border-radius: var(--radius-sm);
}

.ai-markdown :deep(pre) {
  background: var(--color-lighter);
  padding: 0.625rem 0.75rem;
  border-radius: var(--radius-base);
  overflow: auto;
}

.ai-markdown :deep(pre code) {
  background: transparent;
  color: var(--color-text);
  padding: 0;
}
</style>
