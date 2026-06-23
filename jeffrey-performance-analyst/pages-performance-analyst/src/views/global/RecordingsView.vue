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
  <MainCard no-padding>
      <template #header>
        <MainCardHeader
          icon="bi-record-circle"
          :title="props.title ?? 'Recordings'"
          :badge="recordings.length"
        />
      </template>

      <LoadingState v-if="loading" message="Loading recordings..." />
      <ErrorState v-else-if="error" :message="error" />
      <EmptyState
        v-else-if="recordings.length === 0"
        icon="bi-inbox"
        title="No recordings yet"
        message="Recordings downloaded from a project's sessions will appear here."
      />

      <!-- Master–detail: recordings list (left) + selected recording (right) -->
      <div v-else class="md" :class="{ 'show-detail': showDetailMobile }">
        <aside class="md-list">
          <div class="md-list-search">
            <div class="search-box">
              <i class="bi bi-search"></i>
              <input v-model="listFilter" type="text" placeholder="Filter recordings…" />
            </div>
          </div>
          <div class="md-list-scroll">
            <button
              v-for="recording in filteredRecordings"
              :key="recording.id"
              class="rec-li"
              :class="{ active: recording.id === selectedRecordingId }"
              @click="selectRecording(recording)"
            >
              <span class="rec-tile" :class="{ heap: isHeapDump(recording) }">
                <i class="bi" :class="recordingIcon(recording)"></i>
              </span>
              <span class="rec-li-main">
                <span class="rec-li-name" :title="recording.filename">{{ recording.filename }}</span>
                <span class="rec-li-sub">
                  <span>{{ formatTimestamp(recording.uploadedAt) }}</span>
                  <span v-if="recording.durationInMillis > 0">{{ formatDuration(recording.durationInMillis) }}</span>
                </span>
                <span class="rec-li-status">
                  <span v-if="ai(recording.id).loading" class="status-chip gen">
                    <i class="bi bi-arrow-repeat spin"></i> Generating…
                  </span>
                  <template v-else-if="isGenerated(recording.id)">
                    <Badge
                      v-for="prompt in availablePrompts(recording.id)"
                      :key="prompt.eventType"
                      :value="prompt.label"
                      :icon="`bi ${eventIcon(prompt.eventType)}`"
                      :variant="eventBadgeVariant(recording.id, prompt.eventType)"
                      size="xs"
                      :uppercase="false"
                    />
                    <Badge
                      v-if="recordingTopSeverity(recording.id)"
                      :value="recordingTopSeverity(recording.id)!"
                      :variant="severityVariant(recordingTopSeverity(recording.id)!)"
                      size="xs"
                      :uppercase="false"
                    />
                  </template>
                  <span v-else-if="hasJfr(recording)" class="status-chip none">Not analyzed</span>
                </span>
              </span>
            </button>
            <EmptyState
              v-if="filteredRecordings.length === 0"
              icon="bi-search"
              title="No matches"
              message="No recordings match your filter."
            />
          </div>
        </aside>

        <section v-if="selectedRecording" class="md-detail">
          <div class="md-detail-head">
            <button class="back-btn" @click="backToList">
              <i class="bi bi-chevron-left"></i> Recordings
            </button>
            <div class="d-head-row">
              <span class="rec-tile lg" :class="{ heap: isHeapDump(selectedRecording) }">
                <i class="bi" :class="recordingIcon(selectedRecording)"></i>
              </span>
              <div class="d-head-main">
                <div class="d-head-name" :title="selectedRecording.filename">{{ selectedRecording.filename }}</div>
                <div class="d-head-meta">
                  <span class="badge-muted">{{ formatBytes(selectedRecording.sizeInBytes) }}</span>
                  <span v-if="selectedRecording.durationInMillis > 0" class="badge-muted">
                    {{ formatDuration(selectedRecording.durationInMillis) }}
                  </span>
                  <span class="badge-muted">{{ formatTimestamp(selectedRecording.uploadedAt) }}</span>
                  <span class="badge-muted">
                    <i class="bi bi-files"></i>
                    {{ selectedRecording.files.length }} file{{ selectedRecording.files.length !== 1 ? 's' : '' }}
                  </span>
                  <Badge
                    v-for="prompt in availablePrompts(selectedRecording.id)"
                    :key="prompt.eventType"
                    :value="prompt.label"
                    :icon="`bi ${eventIcon(prompt.eventType)}`"
                    :variant="eventBadgeVariant(selectedRecording.id, prompt.eventType)"
                    size="s"
                    :uppercase="false"
                  />
                </div>
              </div>
              <button
                v-if="hasJfr(selectedRecording) && !isGenerated(selectedRecording.id)"
                class="ai-row-btn primary"
                :disabled="ai(selectedRecording.id).loading"
                @click="generatePrompts(selectedRecording)"
              >
                <i class="bi" :class="ai(selectedRecording.id).loading ? 'bi-arrow-repeat spin' : 'bi-robot'"></i>
                {{ ai(selectedRecording.id).loading ? 'Generating…' : 'Generate AI Prompt' }}
              </button>
            </div>
          </div>

          <div class="md-detail-body">
            <div class="rec-tabs">
              <template v-if="ai(selectedRecording.id).prompts">
                <button
                  v-for="prompt in ai(selectedRecording.id).prompts"
                  :key="prompt.eventType"
                  class="rec-tab"
                  :class="{ active: tabOf(selectedRecording.id) === prompt.eventType }"
                  @click="setTab(selectedRecording.id, prompt.eventType)"
                >
                  <i class="bi" :class="eventIcon(prompt.eventType)"></i> {{ prompt.label }}
                </button>
              </template>
              <button
                class="rec-tab"
                :class="{ active: tabOf(selectedRecording.id) === 'files' }"
                @click="setTab(selectedRecording.id, 'files')"
              >
                <i class="bi bi-folder2"></i> Files
              </button>
            </div>

            <div class="rec-tab-body">
              <RecordingFileGroupList
                v-if="tabOf(selectedRecording.id) === 'files'"
                :recording-id="selectedRecording.id"
                :files="selectedRecording.files"
                @download="downloadFile"
              />
              <template v-else>
                <LoadingState v-if="ai(selectedRecording.id).loading" message="Parsing JFR & building prompt…" />
                <div v-else-if="ai(selectedRecording.id).error" class="ai-error">
                  <i class="bi bi-exclamation-triangle"></i> {{ ai(selectedRecording.id).error }}
                </div>
                <EmptyState
                  v-else-if="ai(selectedRecording.id).prompts && ai(selectedRecording.id).prompts.length === 0"
                  icon="bi-robot"
                  title="No CPU or wall-clock samples"
                  message="This recording has no jdk.ExecutionSample or profiler.WallClockSample events."
                />
                <div v-else-if="activePrompt(selectedRecording.id)" class="ai-pane">
                  <div class="ai-toolbar">
                    <button
                      class="recs-subtab"
                      :class="{ active: recsViewOf(selectedRecording.id) === 'prompt' }"
                      @click="setRecsView(selectedRecording.id, 'prompt')"
                    >
                      <i class="bi bi-file-text"></i> Prompt
                    </button>
                    <button
                      class="recs-subtab"
                      :class="{ active: recsViewOf(selectedRecording.id) === 'recommendations' }"
                      @click="setRecsView(selectedRecording.id, 'recommendations')"
                    >
                      <i class="bi bi-stars"></i> Recommendations
                    </button>
                    <button
                      class="recs-subtab"
                      :class="{ active: recsViewOf(selectedRecording.id) === 'patch' }"
                      :disabled="!activeRecs(selectedRecording.id).patch"
                      :title="patchTabTitle(selectedRecording.id)"
                      @click="setRecsView(selectedRecording.id, 'patch')"
                    >
                      <i class="bi bi-file-earmark-diff"></i> Patch
                    </button>
                    <Badge
                      v-if="activeSamples(selectedRecording.id) > 0"
                      :value="`${formatCount(activeSamples(selectedRecording.id))} samples`"
                      variant="primary"
                      size="s"
                      :uppercase="false"
                      borderless
                    />
                    <span class="ai-spacer"></span>
                    <button
                      v-if="recsViewOf(selectedRecording.id) === 'prompt'"
                      class="ai-action solid"
                      @click="copyActive(selectedRecording.id)"
                    >
                      <i class="bi bi-clipboard"></i> Copy for AI
                    </button>
                    <button
                      class="ai-action"
                      :disabled="!canDownloadActiveView(selectedRecording.id)"
                      @click="downloadActiveView(selectedRecording)"
                    >
                      <i class="bi bi-download"></i> {{ downloadLabel(selectedRecording.id) }}
                    </button>
                    <button
                      v-if="projectId"
                      class="recs-cta"
                      :disabled="activeRecs(selectedRecording.id).running || !recommendationsAvailable"
                      :title="recommendationsDisabledReason"
                      @click="generateRecommendations(selectedRecording)"
                    >
                      <i :class="activeRecs(selectedRecording.id).running ? 'bi bi-arrow-repeat spin' : 'bi bi-stars'"></i>
                      {{ activeRecs(selectedRecording.id).running ? 'Generating…' : 'Generate AI Recommendations' }}
                    </button>
                  </div>

                  <!-- Prompt -->
                  <div
                    v-if="recsViewOf(selectedRecording.id) === 'prompt'"
                    class="ai-markdown"
                    v-html="activeMarkdownHtml(selectedRecording.id)"
                  ></div>

                  <!-- Recommendations -->
                  <template v-else-if="recsViewOf(selectedRecording.id) === 'recommendations'">
                    <LoadingState
                      v-if="activeRecs(selectedRecording.id).running"
                      :message="activeRecs(selectedRecording.id).message"
                    />
                    <div v-else-if="activeRecs(selectedRecording.id).error" class="ai-error">
                      <i class="bi bi-exclamation-triangle"></i> {{ activeRecs(selectedRecording.id).error }}
                    </div>
                    <div v-else-if="activeRecs(selectedRecording.id).recommendations" class="recs-result">
                      <div v-if="activeRecs(selectedRecording.id).severity" class="recs-severity">
                        <span class="recs-severity-label">Priority</span>
                        <Badge
                          :value="activeRecs(selectedRecording.id).severity!"
                          :variant="severityVariant(activeRecs(selectedRecording.id).severity!)"
                          size="s"
                          :uppercase="false"
                        />
                      </div>
                      <div class="ai-markdown" v-html="activeRecsHtml(selectedRecording.id)"></div>
                    </div>
                    <EmptyState
                      v-else
                      icon="bi-stars"
                      title="No recommendations yet"
                      :message="recommendationsAvailable
                        ? 'Click “Generate AI Recommendations” to analyze the repository against this profile.'
                        : recommendationsDisabledReason"
                    />
                  </template>

                  <!-- Patch -->
                  <template v-else>
                    <DiffViewer
                      v-if="activeRecs(selectedRecording.id).patch"
                      :patch="activeRecs(selectedRecording.id).patch!"
                    />
                    <EmptyState
                      v-else
                      icon="bi-file-earmark-diff"
                      title="No applicable patch"
                      message="The model did not propose a concrete code change for this profile."
                    />
                  </template>
                </div>
              </template>
            </div>
          </div>
        </section>
      </div>
  </MainCard>
</template>

<script setup lang="ts">
import { computed, inject, onMounted, onUnmounted, ref, type ComputedRef } from 'vue';
import { useRoute } from 'vue-router';
import { marked } from 'marked';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import Badge from '@shared/components/Badge.vue';
import DiffViewer from '@/components/DiffViewer.vue';
import RecordingFileGroupList from '@workspaces/components/RecordingFileGroupList.vue';
import RecordingsClient from '@workspaces/services/api/RecordingsClient';
import type Recording from '@workspaces/services/api/model/Recording';
import RecordingFileType from '@workspaces/services/api/model/RecordingFileType';
import FormattingService from '@shared/services/FormattingService';
import ToastService from '@shared/services/ToastService';
import FlamegraphAiExportClient from '@/services/api/FlamegraphAiExportClient';
import ProjectRecordingsClient from '@/services/api/ProjectRecordingsClient';
import RecentRecordingsClient from '@/services/api/RecentRecordingsClient';
import RecommendationsClient from '@/services/api/RecommendationsClient';
import AiCapabilitiesClient from '@/services/api/AiCapabilitiesClient';
import VersionControlSystemClient from '@/services/api/VersionControlSystemClient';
import type AiPrompt from '@/services/api/model/AiPrompt';
import type Severity from '@/services/api/model/Severity';
import { severityRank, severityVariant } from '@/services/severityDisplay';

// Optional project scope. When projectId is set the list shows that project's recordings; otherwise the
// global view shows the latest recordings across all projects. Per-recording actions (download, AI) use
// the global by-id endpoints regardless.
const props = defineProps<{
  hubId?: string;
  workspaceId?: string;
  projectId?: string;
  title?: string;
}>();

const route = useRoute();

// The human project name, provided by ProjectDetail when this view is project-scoped. Denormalized onto
// generated recommendations so the global Overview can label the recording without resolving the hub.
const projectName = inject<ComputedRef<string | null>>('projectName');

interface AiState {
  loading: boolean;
  error: string | null;
  prompts: AiPrompt[] | null;
}

const FILES_TAB = 'files';
const AI_PENDING_TAB = 'ai';
const EMPTY_AI_STATE: AiState = { loading: false, error: null, prompts: null };

// Sample event types, used to pick a per-tab icon (CPU vs wall-clock).
const CPU_EVENT_TYPE = 'jdk.ExecutionSample';
const WALL_CLOCK_EVENT_TYPE = 'profiler.WallClockSample';

const recordingsClient = new RecordingsClient();
const aiExportClient = new FlamegraphAiExportClient();
const recentRecordingsClient = new RecentRecordingsClient();

const recordings = ref<Recording[]>([]);
const loading = ref(true);
const error = ref<string | null>(null);
const activeTab = ref<Record<string, string>>({});
const aiStates = ref<Record<string, AiState>>({});

// Master–detail selection. The newest recording is selected on load; on narrow screens the detail
// pane replaces the list (drill-in) and `showDetailMobile` drives that via a CSS class.
const selectedRecordingId = ref<string | null>(null);
const showDetailMobile = ref(false);
const listFilter = ref('');

// Repository-aware AI recommendations are generated per recording + sample event type (the active
// prompt tab), so the state is keyed by both. The clients are kept so we can unsubscribe on unmount.
// The prompt pane is a three-way switch: the flamegraph Prompt (default) and, once generated, the AI
// Recommendations and its applicable Patch. The selected sub-view is per recording + event type.
type RecsView = 'prompt' | 'recommendations' | 'patch';
interface RecsState {
  running: boolean;
  message: string;
  severity: Severity | null;
  recommendations: string | null;
  patch: string | null;
  error: string | null;
}
const EMPTY_RECS: RecsState = {
  running: false,
  message: '',
  severity: null,
  recommendations: null,
  patch: null,
  error: null,
};
const recsStates = ref<Record<string, RecsState>>({});
const recsViews = ref<Record<string, RecsView>>({});
const recsClients: Record<string, RecommendationsClient> = {};
const recsKey = (recordingId: string, eventType: string): string => `${recordingId}::${eventType}`;

// Recommendations need both a configured AI provider (deployment-global) and a Git repository connected
// to the project (so the cloned sources can be analysed). Both are resolved once on mount; until then the
// action stays disabled. Generating AI *prompts* needs neither and is always available.
const aiRecommendationsEnabled = ref(false);
const gitConfigured = ref(false);
const recommendationsAvailable = computed(
  () => aiRecommendationsEnabled.value && gitConfigured.value
);
const recommendationsDisabledReason = computed((): string => {
  if (recommendationsAvailable.value) {
    return '';
  }
  const missing: string[] = [];
  if (!gitConfigured.value) {
    missing.push('connect a Git repository');
  }
  if (!aiRecommendationsEnabled.value) {
    missing.push('configure an AI provider');
  }
  return `To generate recommendations, ${missing.join(' and ')}.`;
});

const formatBytes = (bytes: number) => FormattingService.formatBytes(bytes);
const formatTimestamp = (millis: number) => FormattingService.formatTimestamp(millis);
const formatDuration = (millis: number) => FormattingService.formatDurationMillisCompact(millis);
const formatCount = (n: number) => n.toLocaleString('en-US');

const filteredRecordings = computed((): Recording[] => {
  const query = listFilter.value.trim().toLowerCase();
  const matched = query
    ? recordings.value.filter(recording => recording.filename.toLowerCase().includes(query))
    : recordings.value;
  // Newest first.
  return [...matched].sort((a, b) => b.uploadedAt - a.uploadedAt);
});

const selectedRecording = computed((): Recording | null =>
  recordings.value.find(recording => recording.id === selectedRecordingId.value) ?? null);

const selectRecording = (recording: Recording) => {
  selectedRecordingId.value = recording.id;
  showDetailMobile.value = true;
};

const backToList = () => {
  showDetailMobile.value = false;
};

const downloadFile = (recordingId: string, fileId: string) => {
  recordingsClient.downloadFile(recordingId, fileId);
};

const hasJfr = (recording: Recording): boolean =>
  recording.files.some(
    file => file.type === RecordingFileType.JFR || file.type === RecordingFileType.JFR_LZ4
  );

const isHeapDump = (recording: Recording): boolean =>
  recording.files.some(
    file => file.type === RecordingFileType.HEAP_DUMP || file.type === RecordingFileType.HEAP_DUMP_GZ
  );

const recordingIcon = (recording: Recording): string =>
  isHeapDump(recording) ? 'bi-pie-chart-fill' : 'bi-activity';

const eventIcon = (eventType: string): string => {
  if (eventType === CPU_EVENT_TYPE) {
    return 'bi-cpu';
  }
  if (eventType === WALL_CLOCK_EVENT_TYPE) {
    return 'bi-clock-history';
  }
  return 'bi-robot';
};

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

// --- AI recommendations (repository-aware) ---

const activeRecs = (recordingId: string): RecsState => {
  const prompt = activePrompt(recordingId);
  if (!prompt) {
    return EMPTY_RECS;
  }
  return recsStates.value[recsKey(recordingId, prompt.eventType)] ?? EMPTY_RECS;
};

const hasRecs = (recordingId: string): boolean => {
  const state = activeRecs(recordingId);
  return state.running || state.error !== null || state.recommendations !== null;
};

const activeRecsHtml = (recordingId: string): string => {
  const recommendations = activeRecs(recordingId).recommendations;
  return recommendations ? renderMarkdown(recommendations) : '';
};

const recsViewOf = (recordingId: string): RecsView => {
  const prompt = activePrompt(recordingId);
  if (!prompt) {
    return 'prompt';
  }
  return recsViews.value[recsKey(recordingId, prompt.eventType)] ?? 'prompt';
};

const setRecsView = (recordingId: string, view: RecsView) => {
  const prompt = activePrompt(recordingId);
  if (!prompt) {
    return;
  }
  recsViews.value[recsKey(recordingId, prompt.eventType)] = view;
};

const patchTabTitle = (recordingId: string): string => {
  if (activeRecs(recordingId).patch) {
    return '';
  }
  return hasRecs(recordingId)
    ? 'No applicable patch was produced for this profile'
    : 'Generate recommendations to produce a patch';
};

// --- context-aware download (current sub-view) ---

const downloadLabel = (recordingId: string): string =>
  recsViewOf(recordingId) === 'patch' ? 'Download .patch' : 'Download .md';

const canDownloadActiveView = (recordingId: string): boolean => {
  switch (recsViewOf(recordingId)) {
    case 'prompt':
      return activePrompt(recordingId) !== null;
    case 'recommendations':
      return activeRecs(recordingId).recommendations !== null;
    case 'patch':
      return activeRecs(recordingId).patch !== null;
  }
};

const downloadActiveView = (recording: Recording) => {
  const prompt = activePrompt(recording.id);
  if (!prompt) {
    return;
  }
  const view = recsViewOf(recording.id);
  const state = activeRecs(recording.id);
  let content: string | null;
  let suffix: string;
  let mime: string;
  if (view === 'patch') {
    content = state.patch;
    suffix = '.patch';
    mime = 'text/x-patch';
  } else if (view === 'recommendations') {
    content = state.recommendations;
    suffix = `-${prompt.label}-recommendations.md`;
    mime = 'text/markdown';
  } else {
    content = prompt.markdown;
    suffix = `-${prompt.label}.md`;
    mime = 'text/markdown';
  }
  if (!content) {
    return;
  }
  const filename = view === 'patch' ? `${recording.filename}-${prompt.label}.patch` : `${recording.filename}${suffix}`;
  downloadBlob(content, filename, mime);
};

const downloadBlob = (content: string, filename: string, mime: string) => {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
};

const generateRecommendations = async (recording: Recording) => {
  const prompt = activePrompt(recording.id);
  if (!prompt || !props.projectId || !recommendationsAvailable.value) {
    return;
  }

  const key = recsKey(recording.id, prompt.eventType);
  recsStates.value[key] = {
    running: true,
    message: 'Starting…',
    severity: null,
    recommendations: null,
    patch: null,
    error: null,
  };
  // Switch to the Recommendations tab so the user watches progress where the result will appear.
  recsViews.value[key] = 'recommendations';

  recsClients[key]?.unsubscribe();
  const client = new RecommendationsClient(
    props.hubId!,
    props.workspaceId!,
    props.projectId,
    recording.id
  );
  recsClients[key] = client;

  try {
    const { taskId } = await client.start(prompt.eventType, projectName?.value ?? null);
    client.subscribeToProgress(taskId, {
      onProgress: progress => {
        const state = recsStates.value[key];
        if (state && state.running) {
          state.message = progress.message;
        }
      },
      onComplete: result => {
        recsStates.value[key] = {
          running: false,
          message: '',
          severity: result.severity,
          recommendations: result.recommendations,
          patch: result.patch,
          error: null,
        };
      },
      onError: message => {
        recsStates.value[key] = {
          running: false,
          message: '',
          severity: null,
          recommendations: null,
          patch: null,
          error: message,
        };
      },
    });
  } catch (e: any) {
    console.error('Failed to start recommendations:', e);
    recsStates.value[key] = {
      running: false,
      message: '',
      severity: null,
      recommendations: null,
      patch: null,
      error: e?.response?.data?.message ?? 'Could not start recommendation generation.',
    };
  }
};

// Restore previously generated (stored) recommendations for a recording on page load.
const peekRecommendations = async (recording: Recording) => {
  if (!props.projectId) {
    return;
  }
  try {
    const client = new RecommendationsClient(
      props.hubId!,
      props.workspaceId!,
      props.projectId,
      recording.id
    );
    const stored = await client.peek();
    stored.forEach(artifacts => {
      recsStates.value[recsKey(recording.id, artifacts.eventType)] = {
        running: false,
        message: '',
        severity: artifacts.severity,
        recommendations: artifacts.recommendations,
        patch: artifacts.patch,
        error: null,
      };
    });
  } catch {
    // best-effort: a failed peek just leaves the recording without stored recommendations
  }
};

// --- recording status & actions ---

const isGenerated = (recordingId: string): boolean =>
  (aiStates.value[recordingId]?.prompts?.length ?? 0) > 0;

// The sample event types a recording has prompts for (e.g. CPU, Wall-Clock) — rendered as badges.
const availablePrompts = (recordingId: string): AiPrompt[] => aiStates.value[recordingId]?.prompts ?? [];

// The worst severity across a recording's generated recommendations, or null when none exist yet —
// surfaced as a badge in the list row so high-impact recordings stand out at a glance.
const recordingTopSeverity = (recordingId: string): Severity | null => {
  let top: Severity | null = null;
  for (const prompt of availablePrompts(recordingId)) {
    const recs = recsStates.value[recsKey(recordingId, prompt.eventType)];
    const sev = recs?.recommendations ? recs.severity : null;
    if (sev && (top === null || severityRank(sev) > severityRank(top))) {
      top = sev;
    }
  }
  return top;
};

// Per event-type badge colour by processing stage: prompt generated (blue) → recommendations
// generated (green). The "no prompt" stage (grey) is the separate "Not analyzed" chip, since the
// event types are unknown until a prompt exists.
const eventBadgeVariant = (recordingId: string, eventType: string): string => {
  const recs = recsStates.value[recsKey(recordingId, eventType)];
  return recs && recs.recommendations ? 'green' : 'blue';
};

const generatePrompts = (recording: Recording) => {
  loadPrompts(recording);
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
    if (props.projectId) {
      const projectRecordingsClient = new ProjectRecordingsClient(
        props.hubId!,
        props.workspaceId!,
        props.projectId
      );
      recordings.value = await projectRecordingsClient.listRecordings();
    } else {
      recordings.value = await recentRecordingsClient.listRecordings();
    }
    // Preselect the recording named in the ?recording= query (e.g. deep-linked from the Overview),
    // otherwise the newest recording so the detail pane is populated immediately.
    const requestedId = typeof route.query.recording === 'string' ? route.query.recording : null;
    const requested = requestedId
      ? recordings.value.find(recording => recording.id === requestedId)
      : undefined;
    if (requested) {
      selectedRecordingId.value = requested.id;
    } else if (recordings.value.length > 0) {
      const newest = recordings.value.reduce((latest, candidate) =>
        candidate.uploadedAt > latest.uploadedAt ? candidate : latest
      );
      selectedRecordingId.value = newest.id;
    }
    recordings.value.filter(hasJfr).forEach(recording => {
      peekPrompts(recording.id);
      peekRecommendations(recording);
    });
  } catch (e) {
    console.error('Failed to load recordings:', e);
    error.value = 'Failed to load recordings.';
  } finally {
    loading.value = false;
  }
};

// Resolve recommendation availability for the project-scoped view (AI provider + Git integration).
const loadRecommendationCapabilities = async () => {
  if (!props.projectId) {
    return;
  }
  try {
    const capabilities = await new AiCapabilitiesClient().load();
    aiRecommendationsEnabled.value = capabilities.recommendationsEnabled;
  } catch (e) {
    console.error('Failed to load AI capabilities:', e);
  }
  try {
    const vcs = await new VersionControlSystemClient(
      props.hubId!,
      props.workspaceId!,
      props.projectId
    ).load();
    gitConfigured.value = vcs.configured;
  } catch (e) {
    console.error('Failed to load version control system config:', e);
  }
};

onMounted(() => {
  loadRecordings();
  loadRecommendationCapabilities();
});

onUnmounted(() => {
  Object.values(recsClients).forEach(client => client.unsubscribe());
});
</script>

<style scoped>
/* ===== Master–detail layout ===== */
.md {
  display: grid;
  grid-template-columns: 400px 1fr;
  align-items: start;
}

/* --- left: recordings list --- */
.md-list {
  border-right: 1px solid var(--color-border);
  background: var(--color-light);
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.md-list-search {
  padding: 0.75rem;
  border-bottom: 1px solid var(--color-border);
}

.search-box {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  padding: 0.45rem 0.6rem;
  color: var(--color-text-muted);
  box-shadow: var(--search-shadow);
}

.search-box input {
  border: 0;
  outline: 0;
  background: transparent;
  font: inherit;
  color: var(--color-text);
  width: 100%;
}

.md-list-scroll {
  display: flex;
  flex-direction: column;
}

.rec-li {
  display: flex;
  align-items: flex-start;
  gap: 0.7rem;
  width: 100%;
  text-align: left;
  padding: 0.75rem 0.9rem;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--color-border);
  border-left: 3px solid transparent;
  cursor: pointer;
  transition: background-color 0.15s;
}

.rec-li:hover {
  background: var(--color-white);
}

.rec-li.active {
  background: var(--color-white);
  border-left-color: var(--color-primary);
}

.rec-tile {
  width: 34px;
  height: 34px;
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 1rem;
  border-radius: var(--radius-md);
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.rec-tile.heap {
  background: var(--color-violet-lighter-bg);
  color: var(--color-purple);
}

.rec-tile.lg {
  width: 40px;
  height: 40px;
  font-size: 1.15rem;
}

.rec-li-main {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  min-width: 0;
  flex: 1;
}

.rec-li-name {
  font-weight: 600;
  font-size: 0.82rem;
  color: var(--color-dark);
  line-height: 1.3;
  word-break: break-all;
}

.rec-li-sub {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  font-size: 0.72rem;
  color: var(--color-text-muted);
}

.rec-li-status {
  margin-top: 0.1rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.7rem;
  font-weight: 600;
  padding: 0.15rem 0.5rem;
  border-radius: var(--radius-base);
  border: 1px solid transparent;
}

.status-chip.ready {
  background: var(--color-success-light);
  color: var(--color-success-hover);
  border-color: rgba(0, 210, 122, 0.25);
}

.status-chip.gen {
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.status-chip.none {
  background: var(--color-lighter);
  color: var(--color-text-muted);
}

/* --- right: selected recording detail --- */
.md-detail {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.md-detail-head {
  padding: 1rem 1.1rem;
  border-bottom: 1px solid var(--color-border);
}

.back-btn {
  display: none;
  align-items: center;
  gap: 0.3rem;
  background: transparent;
  border: 0;
  color: var(--color-primary);
  font-weight: 600;
  font-size: 0.8rem;
  padding: 0 0 0.6rem;
  cursor: pointer;
}

.d-head-row {
  display: flex;
  align-items: flex-start;
  gap: 0.8rem;
}

.d-head-main {
  flex: 1;
  min-width: 0;
}

.d-head-name {
  font-weight: 700;
  font-size: 0.95rem;
  color: var(--color-dark);
  word-break: break-all;
}

.d-head-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
  margin-top: 0.45rem;
}

.badge-muted {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.72rem;
  font-weight: 600;
  padding: 0.15rem 0.5rem;
  border-radius: var(--radius-base);
  background: var(--color-lighter);
  color: var(--color-text-muted);
}

.md-detail-body {
  display: flex;
  flex-direction: column;
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

/* --- detail body: tabs --- */
.rec-tabs {
  display: flex;
  align-items: center;
  gap: 0.125rem;
  padding: 0 1.1rem;
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
  padding: 1rem 1.1rem 1.1rem;
}

/* --- prompt pane --- */
.ai-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.625rem;
  margin-bottom: 0.625rem;
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

/* Prompt / Recommendations / Patch switcher, sat next to the samples chip in the prompt toolbar. */
.recs-subtab {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.74rem;
  font-weight: 600;
  color: var(--color-text-muted);
  background: var(--color-lighter);
  border: 1px solid var(--color-border);
  padding: 0.25rem 0.625rem;
  border-radius: var(--radius-base);
  cursor: pointer;
  transition: all 0.15s;
}

.recs-subtab:hover:not(:disabled) {
  color: var(--color-dark);
}

.recs-subtab.active {
  color: var(--color-white);
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.recs-subtab:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.recs-severity {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.625rem;
}

.recs-severity-label {
  font-size: 0.72rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-muted);
}

.ai-action:disabled {
  opacity: 0.6;
  cursor: default;
}

/* Recommendations call-to-action — purple gradient (AI accent) with a diagonal light "shimmer" that
   sweeps across on hover. Corner rounding matches the toolbar's other buttons; no vertical movement. */
.recs-cta {
  position: relative;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.78rem;
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--color-white);
  background: linear-gradient(135deg, var(--color-violet), var(--color-purple));
  border: 0;
  padding: 0.45rem 0.95rem;
  border-radius: var(--radius-base);
  cursor: pointer;
  box-shadow: var(--shadow-md);
  transition:
    box-shadow 0.15s ease,
    filter 0.15s ease;
}

/* The sweeping shine, parked off the left edge until hover. */
.recs-cta::after {
  content: '';
  position: absolute;
  top: 0;
  left: -60%;
  width: 45%;
  height: 100%;
  background: linear-gradient(100deg, transparent, rgba(255, 255, 255, 0.5), transparent);
  transform: skewX(-18deg);
  transition: left 0.6s ease;
  pointer-events: none;
}

.recs-cta:hover:not(:disabled) {
  filter: brightness(1.05);
  box-shadow: var(--shadow-lg);
}

.recs-cta:hover:not(:disabled)::after {
  left: 120%;
}

.recs-cta i {
  font-size: 0.85rem;
}

.recs-cta:disabled {
  background: var(--color-lighter);
  color: var(--color-text-muted);
  box-shadow: none;
  cursor: not-allowed;
}

.ai-markdown {
  overflow-x: auto;
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

/* ===== Narrow screens: collapse the two panes to a list → detail drill-in ===== */
@media (max-width: 820px) {
  .md {
    grid-template-columns: 1fr;
  }

  .md-list {
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .md.show-detail .md-list {
    display: none;
  }

  .md:not(.show-detail) .md-detail {
    display: none;
  }

  .back-btn {
    display: inline-flex;
  }
}
</style>
