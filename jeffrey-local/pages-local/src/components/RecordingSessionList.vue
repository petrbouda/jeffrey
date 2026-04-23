<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useNavigation } from '@/composables/useNavigation';
import { downloadAssistantStore } from '@/stores/assistants';
import ProjectRepositoryClient from '@/services/api/ProjectRepositoryClient.ts';
import Utils from '@/services/Utils';
import { ToastService } from '@/services/ToastService';
import RecordingSession from '@/services/api/model/RecordingSession.ts';
import RecordingStatus from '@/services/api/model/RecordingStatus.ts';
import RecordingFileType from '@/services/api/model/RecordingFileType.ts';
import RepositoryFile from '@/services/api/model/RepositoryFile.ts';
import ConfirmationDialog from '@/components/ConfirmationDialog.vue';
import Badge from '@/components/Badge.vue';
import RecordingFileRow from '@/components/RecordingFileRow.vue';
import SectionHeaderBar from '@/components/SectionHeaderBar.vue';
import type { Variant } from '@/types/ui';
import FormattingService from '@/services/FormattingService.ts';
import TimelineBar from '@/components/TimelineBar.vue';

interface Props {
  sessions: RecordingSession[];
  workspaceId: string;
  projectId: string;
  isRemoteWorkspace: boolean;
  showInstanceLink?: boolean;
  headerText?: string;
  isCollectorOnly?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  showInstanceLink: true,
  headerText: 'Recording Sessions',
  isCollectorOnly: false
});

const emit = defineEmits<{
  (e: 'refresh'): void;
}>();

const toast = ToastService;
const { generateInstanceUrl } = useNavigation();

const repositoryService = computed(
  () => new ProjectRepositoryClient(props.workspaceId, props.projectId)
);

// --- Session UI state ---
const expandedSessions = ref<{ [key: string]: boolean }>({});
const showMultiSelectActions = ref<{ [sessionId: string]: boolean }>({});
const selectedRepositoryFile = ref<{ [sessionId: string]: { [sourceId: string]: boolean } }>({});
const visibleFilesCount = ref<{ [key: string]: number }>({});
const expandedTypePanels = ref<{ [key: string]: boolean }>({});
const expandedRotatedGroups = ref<{ [key: string]: boolean }>({});

// Delete dialog state
const deleteSessionDialog = ref(false);
const sessionToDelete = ref<RecordingSession | null>(null);
const deletingSession = ref(false);
const deleteSelectedFilesDialog = ref(false);
const sessionIdWithFilesToDelete = ref('');
const deletingSelectedFiles = ref(false);

// --- Constants ---
const DEFAULT_FILES_LIMIT = 15;

// --- Type definitions ---
interface FileGroupEntry {
  primary: RepositoryFile;
  children: RepositoryFile[];
  totalSize: number;
}

type ArtifactTypeGroup =
  | 'JFR_RECORDING'
  | 'HEAP_DUMP'
  | 'PERF_COUNTERS'
  | 'JVM_LOG'
  | 'APP_LOG'
  | 'HS_JVM_ERROR_LOG'
  | 'UNKNOWN';

const TYPE_GROUP_ORDER: ArtifactTypeGroup[] = [
  'JFR_RECORDING',
  'HEAP_DUMP',
  'PERF_COUNTERS',
  'JVM_LOG',
  'APP_LOG',
  'HS_JVM_ERROR_LOG',
  'UNKNOWN'
];

const FILE_TYPE_TO_GROUP: Record<string, ArtifactTypeGroup> = {
  [RecordingFileType.JFR]: 'JFR_RECORDING',
  [RecordingFileType.JFR_LZ4]: 'JFR_RECORDING',
  [RecordingFileType.ASPROF]: 'JFR_RECORDING',
  [RecordingFileType.HEAP_DUMP]: 'HEAP_DUMP',
  [RecordingFileType.HEAP_DUMP_GZ]: 'HEAP_DUMP',
  [RecordingFileType.PERF_COUNTERS]: 'PERF_COUNTERS',
  [RecordingFileType.JVM_LOG]: 'JVM_LOG',
  [RecordingFileType.APP_LOG]: 'APP_LOG',
  [RecordingFileType.HS_JVM_ERROR_LOG]: 'HS_JVM_ERROR_LOG',
  [RecordingFileType.UNKNOWN]: 'UNKNOWN'
};

const TYPE_GROUP_DISPLAY: Record<
  ArtifactTypeGroup,
  { name: string; variant: Variant; fileType: string }
> = {
  JFR_RECORDING: { name: 'JFR Recordings', variant: 'primary', fileType: 'JFR' },
  HEAP_DUMP: { name: 'Heap Dumps', variant: 'purple', fileType: 'HEAP_DUMP' },
  PERF_COUNTERS: { name: 'Perf Counters', variant: 'blue', fileType: 'PERF_COUNTERS' },
  JVM_LOG: { name: 'JVM Logs', variant: 'green', fileType: 'JVM_LOG' },
  APP_LOG: { name: 'Application Logs', variant: 'brown', fileType: 'APP_LOG' },
  HS_JVM_ERROR_LOG: { name: 'HotSpot Error Logs', variant: 'red', fileType: 'HS_JVM_ERROR_LOG' },
  UNKNOWN: { name: 'Other Files', variant: 'grey', fileType: 'UNKNOWN' }
};

interface TypeGroupPanel {
  groupKey: ArtifactTypeGroup;
  display: { name: string; variant: string; fileType: string };
  files: RepositoryFile[];
  fileCount: number;
  totalSize: number;
}

// --- Utility functions ---
const parseSessionName = (name: string): { sessionInstance: string; sessionId: string } => {
  const slashIndex = name.indexOf('/');
  if (slashIndex === -1) return { sessionInstance: name, sessionId: '' };
  return {
    sessionInstance: name.substring(0, slashIndex),
    sessionId: name.substring(slashIndex + 1)
  };
};

// --- Computed ---
const sortedSessions = computed(() => {
  return [...props.sessions].sort((a, b) => {
    return b.createdAt - a.createdAt;
  });
});

// --- Sorting ---
const getSortedRecordings = (session: RecordingSession) => {
  const getSortPriority = (file: RepositoryFile): number => {
    if (file.isRecording || file.fileType === RecordingFileType.ASPROF)
      return 1;
    return 0;
  };

  return [...session.files].sort((a, b) => {
    const priorityA = getSortPriority(a);
    const priorityB = getSortPriority(b);

    if (priorityA !== priorityB) {
      return priorityA - priorityB;
    }

    if (priorityA === 1) {
      return b.name.localeCompare(a.name);
    }

    return b.createdAt - a.createdAt;
  });
};

// --- Session expand/collapse ---
const initializeExpandedState = () => {
  const firstSessionId = sortedSessions.value.length > 0 ? sortedSessions.value[0].id : null;

  props.sessions.forEach(session => {
    expandedSessions.value[session.id] =
      session.status === RecordingStatus.ACTIVE || session.id === firstSessionId;

    if (visibleFilesCount.value[session.id] === undefined) {
      visibleFilesCount.value[session.id] = DEFAULT_FILES_LIMIT;
    }

    if (!selectedRepositoryFile.value[session.id]) {
      selectedRepositoryFile.value[session.id] = {};
      session.files.forEach(source => {
        selectedRepositoryFile.value[session.id][source.id] = false;
      });
    }

    if (showMultiSelectActions.value[session.id] === undefined) {
      showMultiSelectActions.value[session.id] = false;
    }
  });
};

watch(
  () => props.sessions,
  () => {
    initializeExpandedState();
  },
  { immediate: true }
);

const toggleSession = (sessionId: string) => {
  expandedSessions.value[sessionId] = !expandedSessions.value[sessionId];

  if (!expandedSessions.value[sessionId]) {
    visibleFilesCount.value[sessionId] = DEFAULT_FILES_LIMIT;
    for (const key of Object.keys(visibleFilesCount.value)) {
      if (key.startsWith(sessionId + ':')) {
        visibleFilesCount.value[key] = DEFAULT_FILES_LIMIT;
      }
    }
  }
};

// --- Status helpers ---
const getSourcesCount = (session: RecordingSession): number => {
  return session.files.length;
};

const getSessionIconClass = (session: RecordingSession) => {
  if (session.status === RecordingStatus.ACTIVE) return 'session-icon-active';
  if (session.status === RecordingStatus.FINISHED) return 'session-icon-finished';
  if (session.status === RecordingStatus.UNKNOWN) return 'session-icon-unknown';
  return 'session-icon-unknown';
};

const getSessionStatusClass = (session: RecordingSession) => {
  if (session.status === RecordingStatus.ACTIVE) return 'session-active';
  if (session.status === RecordingStatus.FINISHED) return 'session-finished';
  if (session.status === RecordingStatus.UNKNOWN) return 'session-unknown';
  return `status-unknown session-${String(session.status).toLowerCase()}`;
};

// --- Selection ---
const toggleSelectionMode = (sessionId: string) => {
  showMultiSelectActions.value[sessionId] = !showMultiSelectActions.value[sessionId];

  if (showMultiSelectActions.value[sessionId]) {
    expandedSessions.value[sessionId] = true;
  } else {
    clearAllSelections(sessionId);
  }
};

const clearAllSelections = (sessionId: string) => {
  if (!selectedRepositoryFile.value[sessionId]) {
    selectedRepositoryFile.value[sessionId] = {};
  }

  const session = props.sessions.find(s => s.id === sessionId);
  if (session) {
    session.files.forEach(source => {
      selectedRepositoryFile.value[sessionId][source.id] = false;
    });
  }
};

const getSelectedCount = (sessionId: string): number => {
  if (!selectedRepositoryFile.value[sessionId]) return 0;
  return Object.values(selectedRepositoryFile.value[sessionId]).filter(Boolean).length;
};

const toggleSourceSelection = (sessionId: string, sourceId: string) => {
  if (!selectedRepositoryFile.value[sessionId]) {
    selectedRepositoryFile.value[sessionId] = {};
  }
  selectedRepositoryFile.value[sessionId][sourceId] =
    !selectedRepositoryFile.value[sessionId][sourceId];
};

const toggleSelectAllSources = (sessionId: string, selectAll: boolean) => {
  const session = props.sessions.find(s => s.id === sessionId);
  if (!session) return;

  if (!selectedRepositoryFile.value[sessionId]) {
    selectedRepositoryFile.value[sessionId] = {};
  }

  session.files.forEach(source => {
    if (!isCheckboxDisabled(source)) {
      selectedRepositoryFile.value[sessionId][source.id] = selectAll;
    }
  });
};

const isAllGroupFilesSelected = (sessionId: string, panel: TypeGroupPanel): boolean => {
  if (!selectedRepositoryFile.value[sessionId]) return false;
  const selectableFiles = panel.files.filter(f => !isCheckboxDisabled(f));
  if (selectableFiles.length === 0) return false;
  return selectableFiles.every(f => selectedRepositoryFile.value[sessionId][f.id]);
};

const toggleGroupSelection = (sessionId: string, panel: TypeGroupPanel) => {
  if (!selectedRepositoryFile.value[sessionId]) {
    selectedRepositoryFile.value[sessionId] = {};
  }
  const allSelected = isAllGroupFilesSelected(sessionId, panel);
  panel.files.forEach(file => {
    if (!isCheckboxDisabled(file)) {
      selectedRepositoryFile.value[sessionId][file.id] = !allSelected;
    }
  });
};

const isCheckboxDisabled = (source: RepositoryFile): boolean => {
  return (
    source.status === RecordingStatus.ACTIVE ||
    source.fileType === RecordingFileType.ASPROF ||
    source.size === 0
  );
};

const isDownloadAllowed = (file: RepositoryFile): boolean => {
  return (
    file.status !== RecordingStatus.ACTIVE &&
    file.fileType !== RecordingFileType.ASPROF &&
    file.size > 0
  );
};

const hasDownloadableRecordings = (session: RecordingSession): boolean => {
  return session.files.some(f => f.isRecording && f.size > 0);
};

const downloadFile = async (sessionId: string, fileId: string) => {
  try {
    await repositoryService.value.downloadFile(sessionId, fileId);
  } catch (error: any) {
    toast.error('Download File', error.message || 'Failed to download file');
  }
};

// --- Operations ---
const downloadSession = async (sessionId: string) => {
  try {
    const session = props.sessions.find(s => s.id === sessionId);
    if (!session) {
      toast.error('Download', 'Session not found');
      return;
    }

    if (!hasDownloadableRecordings(session)) {
      toast.warn(
        'No recording data available',
        'The session may have been terminated before any data was written.'
      );
      return;
    }

    if (props.isRemoteWorkspace) {
      const fileIds = session.files.map(f => f.id);
      await downloadAssistantStore.startDownload(
        props.workspaceId,
        props.projectId,
        sessionId,
        fileIds,
        async () => {
          emit('refresh');
        }
      );
    } else {
      await repositoryService.value.copyRecordingSession(session, true);
      toast.success('Download', `Successfully downloaded session ${session.id}`);
      emit('refresh');
    }
  } catch (error: any) {
    console.error('Error downloading session:', error);
    toast.error('Download', error.message || 'Failed to download recording session');
  }
};

const downloadSelectedSources = async (sessionId: string) => {
  try {
    const session = props.sessions.find(s => s.id === sessionId);
    if (!session) return;

    const selectedSources = session.files.filter(
      source => selectedRepositoryFile.value[sessionId][source.id]
    );

    if (selectedSources.length === 0) {
      toast.info('Download', 'No recordings selected');
      return;
    }

    const downloadableSources = selectedSources.filter(f => f.size > 0);
    if (downloadableSources.length === 0) {
      toast.warn('Download', 'No recording data available — the selected files contain no data.');
      return;
    }

    if (props.isRemoteWorkspace) {
      const fileIds = selectedSources.map(f => f.id);
      await downloadAssistantStore.startDownload(
        props.workspaceId,
        props.projectId,
        sessionId,
        fileIds,
        async () => {
          emit('refresh');
          toggleSelectAllSources(sessionId, false);
        }
      );
    } else {
      await repositoryService.value.copySelectedRepositoryFile(session.id, selectedSources, true);
      toast.success('Download', `Successfully downloaded ${selectedSources.length} recording(s)`);
      emit('refresh');
      toggleSelectAllSources(sessionId, false);
    }
  } catch (error: any) {
    console.error('Error downloading selected recordings:', error);
    toast.error('Download', error.message || 'Failed to download selected recordings');
  }
};

const deleteSelectedSources = async (sessionId: string) => {
  const session = props.sessions.find(s => s.id === sessionId);
  if (!session) return;

  const selectedSources = session.files.filter(
    source => selectedRepositoryFile.value[sessionId][source.id]
  );

  if (selectedSources.length === 0) {
    toast.info('Delete Selected', 'No recordings selected');
    return;
  }

  sessionIdWithFilesToDelete.value = sessionId;
  deleteSelectedFilesDialog.value = true;
};

const confirmDeleteSelectedFiles = async () => {
  if (!sessionIdWithFilesToDelete.value) return;

  const sessionId = sessionIdWithFilesToDelete.value;
  const session = props.sessions.find(s => s.id === sessionId);
  if (!session) return;

  deletingSelectedFiles.value = true;

  try {
    const selectedSources = session.files.filter(
      source => selectedRepositoryFile.value[sessionId][source.id]
    );

    await repositoryService.value.deleteSelectedRepositoryFile(sessionId, selectedSources);
    toast.success('Delete Selected', `Successfully deleted ${selectedSources.length} recording(s)`);

    emit('refresh');
    toggleSelectAllSources(sessionId, false);
    deleteSelectedFilesDialog.value = false;
  } catch (error: any) {
    console.error('Error deleting selected recordings:', error);
    toast.error('Delete Selected', error.message || 'Failed to delete selected recordings');
  } finally {
    deletingSelectedFiles.value = false;
    sessionIdWithFilesToDelete.value = '';
  }
};

const deleteAll = async (sessionId: string) => {
  const session = props.sessions.find(s => s.id === sessionId);
  if (!session) {
    toast.error('Delete All', 'Session not found');
    return;
  }

  sessionToDelete.value = session;
  deleteSessionDialog.value = true;
};

const confirmDeleteSession = async () => {
  if (!sessionToDelete.value) return;

  deletingSession.value = true;

  try {
    await repositoryService.value.deleteRecordingSession(sessionToDelete.value);
    toast.success('Delete All', 'Successfully deleted all recordings in the session');

    emit('refresh');
    deleteSessionDialog.value = false;
  } catch (error: any) {
    console.error('Error deleting recording session:', error);
    toast.error('Delete All', error.message || 'Failed to delete recording session');
  } finally {
    deletingSession.value = false;
    sessionToDelete.value = null;
  }
};

// --- Rotation group logic ---
const parseRotatedFilename = (filename: string): { baseName: string; suffix: number } | null => {
  const match = filename.match(/^(.+)\.(\d+)$/);
  if (!match) return null;
  return { baseName: match[1], suffix: parseInt(match[2], 10) };
};

const groupRotatedFiles = (sortedFiles: RepositoryFile[]): FileGroupEntry[] => {
  const nameSet = new Set(sortedFiles.map(f => f.name));
  const grouped = new Map<string, FileGroupEntry>();
  const assigned = new Set<string>();

  for (const file of sortedFiles) {
    const parsed = parseRotatedFilename(file.name);
    if (parsed && nameSet.has(parsed.baseName)) {
      if (!grouped.has(parsed.baseName)) {
        const primary = sortedFiles.find(f => f.name === parsed.baseName);
        if (primary) {
          grouped.set(parsed.baseName, { primary, children: [], totalSize: primary.size });
          assigned.add(primary.id);
        }
      }
      const group = grouped.get(parsed.baseName);
      if (group) {
        group.children.push(file);
        group.totalSize += file.size;
        assigned.add(file.id);
      }
    }
  }

  for (const group of grouped.values()) {
    group.children.sort((a, b) => {
      const pa = parseRotatedFilename(a.name);
      const pb = parseRotatedFilename(b.name);
      return (pa?.suffix ?? 0) - (pb?.suffix ?? 0);
    });
  }

  const result: FileGroupEntry[] = [];
  for (const file of sortedFiles) {
    if (assigned.has(file.id)) {
      if (grouped.has(file.name)) {
        result.push(grouped.get(file.name)!);
        grouped.delete(file.name);
      }
    } else {
      result.push({ primary: file, children: [], totalSize: file.size });
    }
  }

  return result;
};

const isRecordingOrTempFile = (file: RepositoryFile): boolean => {
  return file.isRecording || file.fileType === RecordingFileType.ASPROF;
};

const getRecordingGroupedFiles = (session: RecordingSession): FileGroupEntry[] => {
  const sortedFiles = getSortedRecordings(session);
  const recordingFiles = sortedFiles.filter(f => isRecordingOrTempFile(f));
  return groupRotatedFiles(recordingFiles);
};

const getVisibleRecordingGroups = (session: RecordingSession): FileGroupEntry[] => {
  const allGroups = getRecordingGroupedFiles(session);
  const limit = visibleFilesCount.value[session.id] || DEFAULT_FILES_LIMIT;
  return allGroups.slice(0, limit);
};

const hasMoreRecordingGroups = (session: RecordingSession): boolean => {
  const limit = visibleFilesCount.value[session.id] || DEFAULT_FILES_LIMIT;
  return getRecordingGroupedFiles(session).length > limit;
};

const showMoreRecordingGroups = (sessionId: string) => {
  const session = props.sessions.find(s => s.id === sessionId);
  if (!session) return;
  visibleFilesCount.value[sessionId] = getRecordingGroupedFiles(session).length;
};

const getRemainingRecordingGroupCount = (session: RecordingSession): number => {
  const limit = visibleFilesCount.value[session.id] || DEFAULT_FILES_LIMIT;
  return getRecordingGroupedFiles(session).length - limit;
};

const getArtifactGroupMap = (
  session: RecordingSession
): Map<ArtifactTypeGroup, RepositoryFile[]> => {
  const sortedFiles = getSortedRecordings(session);
  const groupMap = new Map<ArtifactTypeGroup, RepositoryFile[]>();
  for (const file of sortedFiles) {
    if (isRecordingOrTempFile(file)) continue;
    const groupKey = FILE_TYPE_TO_GROUP[file.fileType] || 'UNKNOWN';
    if (!groupMap.has(groupKey)) groupMap.set(groupKey, []);
    groupMap.get(groupKey)!.push(file);
  }
  return groupMap;
};

// Groups that always show as a panel, even with a single file
const ALWAYS_GROUPED: Set<ArtifactTypeGroup> = new Set(['JVM_LOG', 'APP_LOG']);

const getTypeGroupPanels = (session: RecordingSession): TypeGroupPanel[] => {
  const groupMap = getArtifactGroupMap(session);
  const panels: TypeGroupPanel[] = [];
  for (const groupKey of TYPE_GROUP_ORDER) {
    if (groupKey === 'JFR_RECORDING') continue;
    const files = groupMap.get(groupKey);
    if (!files) continue;
    if (files.length <= 1 && !ALWAYS_GROUPED.has(groupKey)) continue;
    panels.push({
      groupKey,
      display: TYPE_GROUP_DISPLAY[groupKey],
      files,
      fileCount: files.length,
      totalSize: files.reduce((sum, f) => sum + f.size, 0)
    });
  }
  return panels;
};

const getStandaloneArtifactFiles = (session: RecordingSession): RepositoryFile[] => {
  const groupMap = getArtifactGroupMap(session);
  const standalone: RepositoryFile[] = [];
  for (const groupKey of TYPE_GROUP_ORDER) {
    if (groupKey === 'JFR_RECORDING') continue;
    const files = groupMap.get(groupKey);
    if (files && files.length === 1 && !ALWAYS_GROUPED.has(groupKey)) {
      standalone.push(files[0]);
    }
  }
  return standalone;
};

const toggleTypePanel = (sessionId: string, groupKey: ArtifactTypeGroup) => {
  const key = `${sessionId}:${groupKey}`;
  expandedTypePanels.value[key] = !expandedTypePanels.value[key];
};

const isTypePanelExpanded = (sessionId: string, groupKey: ArtifactTypeGroup): boolean => {
  return !!expandedTypePanels.value[`${sessionId}:${groupKey}`];
};

const getVisiblePanelFiles = (panel: TypeGroupPanel, sessionId: string): RepositoryFile[] => {
  const key = `${sessionId}:${panel.groupKey}`;
  const limit = visibleFilesCount.value[key] || DEFAULT_FILES_LIMIT;
  return panel.files.slice(0, limit);
};

const hasMoreInPanel = (panel: TypeGroupPanel, sessionId: string): boolean => {
  const key = `${sessionId}:${panel.groupKey}`;
  const limit = visibleFilesCount.value[key] || DEFAULT_FILES_LIMIT;
  return panel.files.length > limit;
};

const showMoreInPanel = (sessionId: string, groupKey: ArtifactTypeGroup, total: number) => {
  visibleFilesCount.value[`${sessionId}:${groupKey}`] = total;
};

const getRemainingInPanel = (panel: TypeGroupPanel, sessionId: string): number => {
  const key = `${sessionId}:${panel.groupKey}`;
  const limit = visibleFilesCount.value[key] || DEFAULT_FILES_LIMIT;
  return panel.files.length - limit;
};

const toggleRotatedGroup = (sessionId: string, primaryName: string) => {
  const key = `${sessionId}:${primaryName}`;
  expandedRotatedGroups.value[key] = !expandedRotatedGroups.value[key];
};

const isRotatedGroupExpanded = (sessionId: string, primaryName: string): boolean => {
  return !!expandedRotatedGroups.value[`${sessionId}:${primaryName}`];
};

const getSourceStatusWrapperClass = (source: RepositoryFile, sessionId: string) => {
  const classes = [];

  if (
    selectedRepositoryFile.value[sessionId] &&
    selectedRepositoryFile.value[sessionId][source.id]
  ) {
    classes.push('source-selected');
  }

  if (source.status === RecordingStatus.ACTIVE) classes.push('source-active');
  else if (source.status === RecordingStatus.FINISHED) classes.push('source-finished');
  else if (source.status === RecordingStatus.UNKNOWN) classes.push('source-unknown');

  return classes.join(' ');
};
</script>

<template>
  <!-- Recording Sessions Header Bar -->
  <div class="col-12" v-if="sessions.length > 0">
    <SectionHeaderBar :text="`${headerText} (${sessions.length})`" />
  </div>

  <!-- Recording Sessions List -->
  <div class="col-12" v-if="sessions.length > 0">
    <div v-for="session in sortedSessions" :key="session.id" class="mb-3">
      <!-- Session header -->
      <div
        class="folder-row rounded"
        :class="getSessionStatusClass(session)"
        @click="toggleSession(session.id)"
      >
        <!-- Identity section -->
        <div class="session-identity">
          <div class="d-flex justify-content-between align-items-center">
            <div class="d-flex align-items-center">
              <div class="session-icon-square me-3" :class="getSessionIconClass(session)">
                <i
                  class="bi"
                  :class="expandedSessions[session.id] ? 'bi-folder2-open' : 'bi-folder2'"
                ></i>
              </div>
              <div>
                <div class="d-flex align-items-center gap-2">
                  <template v-if="showInstanceLink">
                    <span class="session-name-label">Instance:</span>
                    <router-link
                      :to="generateInstanceUrl(session.instanceId)"
                      class="instance-link"
                      @click.stop
                    >
                      {{ parseSessionName(session.name).sessionInstance }}
                    </router-link>
                    <span class="session-separator">/</span>
                    <span class="session-name-label">Session:</span>
                  </template>
                  <template v-else>
                    <span class="session-name-label">Session:</span>
                  </template>
                  <span class="session-id-part">{{ session.id }}</span>
                </div>
                <div class="session-meta">
                  <span
                    ><i class="bi bi-files me-1"></i>{{ getSourcesCount(session) }} sources</span
                  >
                </div>
              </div>
            </div>
            <div class="d-flex align-items-center gap-1">
              <button
                v-if="!isCollectorOnly"
                class="btn btn-sm btn-outline-primary"
                type="button"
                title="Download Recordings"
                @click.stop="downloadSession(session.id)"
              >
                <i class="bi bi-folder-symlink me-1"></i>Download
              </button>
              <button
                v-if="session.status === RecordingStatus.FINISHED"
                class="btn btn-sm btn-outline-danger"
                type="button"
                title="Delete Session"
                @click.stop="deleteAll(session.id)"
              >
                <i class="bi bi-trash"></i>
              </button>
              <button
                class="action-btn action-menu-btn ms-1"
                :class="{ active: showMultiSelectActions[session.id] }"
                type="button"
                title="Toggle multi-select mode"
                @click.stop="toggleSelectionMode(session.id)"
              >
                <i
                  class="bi"
                  :class="{
                    'bi-check2-square': showMultiSelectActions[session.id],
                    'bi-square': !showMultiSelectActions[session.id]
                  }"
                ></i>
              </button>
            </div>
          </div>
        </div>

        <!-- Timeline section -->
        <TimelineBar
          :createdAt="session.createdAt"
          :finishedAt="session.finishedAt"
          :duration="session.duration"
        />
      </div>

      <!-- Session recordings (shown when expanded) -->
      <div v-if="expandedSessions[session.id]" class="ps-4 pt-2">
        <!-- Multi-select controls -->
        <div
          v-if="showMultiSelectActions[session.id]"
          class="multi-select-controls d-flex justify-content-between align-items-center mb-2"
        >
          <div class="d-flex align-items-center">
            <button
              class="btn btn-sm select-all-btn me-2"
              @click.stop="toggleSelectAllSources(session.id, true)"
              title="Select all recordings"
            >
              <i class="bi bi-check2-square me-1"></i>Select All
            </button>
            <button
              class="btn btn-sm clear-btn"
              @click.stop="toggleSelectAllSources(session.id, false)"
              :disabled="getSelectedCount(session.id) === 0"
              title="Clear selection"
            >
              <i class="bi bi-x-lg me-1"></i>Clear
            </button>
          </div>

          <div class="d-flex align-items-center gap-2">
            <Badge
              v-if="getSelectedCount(session.id) > 0"
              :value="`${getSelectedCount(session.id)} selected`"
              variant="secondary"
              size="xs"
              class="me-2"
            />
            <button
              v-if="!isCollectorOnly"
              class="btn btn-sm btn-outline-primary"
              @click.stop="downloadSelectedSources(session.id)"
              :disabled="getSelectedCount(session.id) === 0"
              title="Download selected recordings"
            >
              <i class="bi bi-folder-symlink me-1"></i>Download
            </button>
            <button
              v-if="!isRemoteWorkspace"
              class="btn btn-sm btn-outline-danger"
              @click.stop="deleteSelectedSources(session.id)"
              :disabled="getSelectedCount(session.id) === 0"
              title="Delete selected recordings"
            >
              <i class="bi bi-trash me-1"></i>Delete Selected
            </button>
          </div>
        </div>

        <!-- Artifact type panels (non-recording types with > 1 file) -->
        <div
          v-for="panel in getTypeGroupPanels(session)"
          :key="panel.groupKey"
          class="type-panel mb-2"
        >
          <!-- Type panel header -->
          <div
            class="type-panel-header-wrapper"
            @click="toggleTypePanel(session.id, panel.groupKey)"
          >
            <RecordingFileRow
              :filename="panel.display.name"
              :fileType="panel.display.fileType"
              :sizeInBytes="panel.totalSize"
            >
              <template #before>
                <i
                  class="bi me-1 type-panel-chevron"
                  :class="
                    isTypePanelExpanded(session.id, panel.groupKey)
                      ? 'bi-chevron-down'
                      : 'bi-chevron-right'
                  "
                ></i>
                <div
                  class="form-check file-form-check me-2"
                  v-if="showMultiSelectActions[session.id]"
                >
                  <input
                    class="form-check-input file-checkbox"
                    type="checkbox"
                    :checked="isAllGroupFilesSelected(session.id, panel)"
                    @change="toggleGroupSelection(session.id, panel)"
                    @click.stop
                  />
                </div>
              </template>
              <template #extra-badges>
                <span class="recording-file-size ms-2">
                  <i class="bi bi-files me-1"></i>{{ panel.fileCount }} files
                </span>
              </template>
            </RecordingFileRow>
          </div>

          <!-- Type panel body (flat file list) -->
          <div v-if="isTypePanelExpanded(session.id, panel.groupKey)" class="type-panel-body">
            <template v-for="file in getVisiblePanelFiles(panel, session.id)" :key="file.id">
              <div
                class="source-status-wrapper mb-2 rounded"
                :class="getSourceStatusWrapperClass(file, session.id)"
              >
                <RecordingFileRow
                  :filename="file.name"
                  :fileType="file.fileType"
                  :sizeInBytes="file.size"
                  :timestamp="file.createdAt"
                  :status="file.status"
                >
                  <template #before>
                    <div
                      class="form-check file-form-check me-2"
                      v-if="showMultiSelectActions[session.id] && !isCheckboxDisabled(file)"
                    >
                      <input
                        class="form-check-input file-checkbox"
                        type="checkbox"
                        :id="'source-' + file.id"
                        :checked="
                          selectedRepositoryFile[session.id] &&
                          selectedRepositoryFile[session.id][file.id]
                        "
                        @change="() => toggleSourceSelection(session.id, file.id)"
                        @click.stop
                      />
                    </div>
                  </template>
                  <template #extra-badges>
                    <Badge
                      v-if="file.status === RecordingStatus.UNKNOWN"
                      :value="Utils.capitalize(file.status.toLowerCase())"
                      variant="purple"
                      size="xxs"
                      class="ms-1"
                    />
                  </template>
                  <template #actions>
                    <button
                      v-if="isDownloadAllowed(file)"
                      class="btn btn-sm btn-outline-secondary download-file-btn"
                      @click.stop="downloadFile(session.id, file.id)"
                      title="Download file"
                    >
                      <i class="bi bi-download"></i>
                    </button>
                  </template>
                </RecordingFileRow>
              </div>
            </template>

            <!-- Show More per panel -->
            <div v-if="hasMoreInPanel(panel, session.id)" class="text-center mt-1 mb-2">
              <button
                class="btn btn-sm btn-outline-primary show-more-btn"
                @click.stop="showMoreInPanel(session.id, panel.groupKey, panel.fileCount)"
              >
                <i class="bi bi-chevron-down me-1"></i>Show
                {{ getRemainingInPanel(panel, session.id) }} more
              </button>
            </div>
          </div>
        </div>

        <!-- Standalone artifact files (non-recording types with exactly 1 file) -->
        <template v-for="file in getStandaloneArtifactFiles(session)" :key="file.id">
          <div
            class="source-status-wrapper mb-2 rounded"
            :class="getSourceStatusWrapperClass(file, session.id)"
          >
            <RecordingFileRow
              :filename="file.name"
              :fileType="file.fileType"
              :sizeInBytes="file.size"
              :timestamp="file.createdAt"
              :status="file.status"
            >
              <template #before>
                <div
                  class="form-check file-form-check me-2"
                  v-if="showMultiSelectActions[session.id] && !isCheckboxDisabled(file)"
                >
                  <input
                    class="form-check-input file-checkbox"
                    type="checkbox"
                    :id="'source-' + file.id"
                    :checked="
                      selectedRepositoryFile[session.id] &&
                      selectedRepositoryFile[session.id][file.id]
                    "
                    @change="() => toggleSourceSelection(session.id, file.id)"
                    @click.stop
                  />
                </div>
              </template>
              <template #extra-badges>
                <Badge
                  v-if="file.status === RecordingStatus.UNKNOWN"
                  :value="Utils.capitalize(file.status.toLowerCase())"
                  variant="purple"
                  size="xxs"
                  class="ms-1"
                />
              </template>
              <template #actions>
                <button
                  v-if="isDownloadAllowed(file)"
                  class="btn btn-sm btn-outline-secondary download-file-btn"
                  @click.stop="downloadFile(session.id, file.id)"
                  title="Download file"
                >
                  <i class="bi bi-download"></i>
                </button>
              </template>
            </RecordingFileRow>
          </div>
        </template>

        <!-- Recording files (flat with rotation grouping) -->
        <template v-for="entry in getVisibleRecordingGroups(session)" :key="entry.primary.id">
          <!-- Primary row -->
          <div
            class="source-status-wrapper mb-2 rounded"
            :class="getSourceStatusWrapperClass(entry.primary, session.id)"
          >
            <RecordingFileRow
              :filename="entry.primary.name"
              :fileType="entry.primary.fileType"
              :sizeInBytes="entry.primary.size"
              :timestamp="entry.primary.createdAt"
              :status="entry.primary.status"
            >
              <template #before>
                <button
                  v-if="entry.children.length > 0"
                  class="rotation-toggle-btn me-1"
                  @click.stop="toggleRotatedGroup(session.id, entry.primary.name)"
                  :title="
                    isRotatedGroupExpanded(session.id, entry.primary.name)
                      ? 'Collapse rotated files'
                      : 'Expand rotated files'
                  "
                >
                  <i
                    class="bi"
                    :class="
                      isRotatedGroupExpanded(session.id, entry.primary.name)
                        ? 'bi-chevron-down'
                        : 'bi-chevron-right'
                    "
                  ></i>
                </button>
                <div
                  class="form-check file-form-check me-2"
                  v-if="showMultiSelectActions[session.id] && !isCheckboxDisabled(entry.primary)"
                >
                  <input
                    class="form-check-input file-checkbox"
                    type="checkbox"
                    :id="'source-' + entry.primary.id"
                    :checked="
                      selectedRepositoryFile[session.id] &&
                      selectedRepositoryFile[session.id][entry.primary.id]
                    "
                    @change="() => toggleSourceSelection(session.id, entry.primary.id)"
                    @click.stop
                  />
                </div>
              </template>
              <template #extra-badges>
                <Badge
                  v-if="entry.primary.status === RecordingStatus.UNKNOWN"
                  :value="Utils.capitalize(entry.primary.status.toLowerCase())"
                  variant="purple"
                  size="xxs"
                  class="ms-1"
                />
                <Badge
                  v-if="entry.children.length > 0"
                  :value="`+${entry.children.length} rotated · ${FormattingService.formatBytes(entry.totalSize)}`"
                  variant="grey"
                  size="xxs"
                  class="ms-1"
                  :uppercase="false"
                />
              </template>
              <template #actions>
                <button
                  v-if="isDownloadAllowed(entry.primary)"
                  class="btn btn-sm btn-outline-secondary download-file-btn"
                  @click.stop="downloadFile(session.id, entry.primary.id)"
                  title="Download file"
                >
                  <i class="bi bi-download"></i>
                </button>
              </template>
            </RecordingFileRow>
          </div>

          <!-- Rotated children (when expanded) -->
          <template
            v-if="
              entry.children.length > 0 && isRotatedGroupExpanded(session.id, entry.primary.name)
            "
          >
            <div
              v-for="child in entry.children"
              :key="child.id"
              class="rotated-child-row mb-2 rounded"
              :class="getSourceStatusWrapperClass(child, session.id)"
            >
              <RecordingFileRow
                :filename="child.name"
                :fileType="child.fileType"
                :sizeInBytes="child.size"
                :timestamp="child.createdAt"
                :status="child.status"
              >
                <template #before>
                  <div
                    class="form-check file-form-check me-2"
                    v-if="showMultiSelectActions[session.id] && !isCheckboxDisabled(child)"
                  >
                    <input
                      class="form-check-input file-checkbox"
                      type="checkbox"
                      :id="'source-' + child.id"
                      :checked="
                        selectedRepositoryFile[session.id] &&
                        selectedRepositoryFile[session.id][child.id]
                      "
                      @change="() => toggleSourceSelection(session.id, child.id)"
                      @click.stop
                    />
                  </div>
                </template>
                <template #extra-badges>
                  <Badge
                    v-if="child.status === RecordingStatus.UNKNOWN"
                    :value="Utils.capitalize(child.status.toLowerCase())"
                    variant="purple"
                    size="xxs"
                    class="ms-1"
                  />
                </template>
                <template #actions>
                  <button
                    v-if="isDownloadAllowed(child)"
                    class="btn btn-sm btn-outline-secondary download-file-btn"
                    @click.stop="downloadFile(session.id, child.id)"
                    title="Download file"
                  >
                    <i class="bi bi-download"></i>
                  </button>
                </template>
              </RecordingFileRow>
            </div>
          </template>
        </template>

        <!-- Show More for recordings -->
        <div v-if="hasMoreRecordingGroups(session)" class="text-center mt-1 mb-2">
          <button
            class="btn btn-sm btn-outline-primary show-more-btn"
            @click.stop="showMoreRecordingGroups(session.id)"
          >
            <i class="bi bi-chevron-down me-1"></i>Show
            {{ getRemainingRecordingGroupCount(session) }} more files
          </button>
        </div>
      </div>
    </div>
  </div>

  <!-- Delete Session Confirmation Modal -->
  <ConfirmationDialog
    v-model:show="deleteSessionDialog"
    title="Confirm Deletion"
    message="Are you sure you want to delete this recording session?"
    sub-message="This action cannot be undone."
    confirm-label="Delete Session"
    confirm-button-class="btn-danger"
    confirm-button-id="deleteSessionButton"
    modal-id="deleteSessionModal"
    @confirm="confirmDeleteSession"
  />

  <!-- Delete Selected Files Confirmation Modal -->
  <ConfirmationDialog
    v-model:show="deleteSelectedFilesDialog"
    title="Confirm Deletion"
    message="Are you sure you want to delete the selected recordings?"
    sub-message="This action cannot be undone."
    confirm-label="Delete Selected"
    confirm-button-class="btn-danger"
    confirm-button-id="deleteSelectedFilesButton"
    modal-id="deleteSelectedFilesModal"
    @confirm="confirmDeleteSelectedFiles"
  />
</template>

<style scoped>
code {
  background-color: var(--color-light);
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  font-size: 0.875rem;
  word-break: break-all;
}

.form-check-input:checked {
  background-color: var(--color-primary);
  border-color: var(--color-primary);
}

/* Larger checkbox for file selection */
.file-checkbox {
  width: 1.2em !important;
  height: 1.2em !important;
  margin-top: calc(0.15em - 2px);
  cursor: pointer;
  border-width: 1px;
  transition: all 0.15s ease;
}

.file-checkbox:hover:not(:disabled) {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 0.1rem rgba(94, 100, 255, 0.2);
}

.file-form-check {
  min-height: auto;
  margin-bottom: 0;
  padding-left: 1.8em;
}

.btn-primary {
  background-color: var(--color-primary);
  border-color: var(--color-primary);
}

.btn-primary:hover {
  background-color: var(--color-primary-hover);
  border-color: var(--color-primary-hover);
}

.btn-outline-danger:hover {
  background-color: var(--color-danger);
  border-color: var(--color-danger);
}

.folder-row {
  background-color: white;
  cursor: pointer;
  transition: all 0.15s ease;
  border: 1px solid rgba(94, 100, 255, 0.08);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.folder-row:hover {
  transform: translateY(-1px);
  box-shadow: 0 3px 8px rgba(0, 0, 0, 0.08);
  border-color: rgba(94, 100, 255, 0.15);
}

.folder-row.session-active {
  border-left: 3px solid var(--color-amber);
}

.folder-row.session-finished {
  border-left: 3px solid var(--color-success);
}

.folder-row.session-unknown {
  border-left: 3px solid var(--color-purple);
}

/* Rotation group styles */
.rotation-toggle-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  min-width: 20px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 0.7rem;
  cursor: pointer;
  border-radius: 3px;
  transition: all 0.15s ease;
}

.rotation-toggle-btn:hover {
  background-color: rgba(94, 100, 255, 0.1);
  color: var(--color-primary);
}

.rotated-child-row {
  margin-left: 28px;
  border-left: 2px solid var(--color-border);
  padding-left: 10px;
  position: relative;
}

.rotated-child-row::before {
  content: '';
  position: absolute;
  left: -2px;
  top: 50%;
  width: 10px;
  height: 0;
  border-top: 1px solid var(--color-border);
}

.text-primary {
  color: var(--color-primary) !important;
}

/* Action button styling */
.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background-color: transparent;
  border: none;
  border-radius: 4px;
  height: 28px;
  width: 28px;
  padding: 0;
  font-size: 0.85rem;
  transition: all 0.15s ease;
}

.action-menu-btn {
  color: var(--color-primary);
  background-color: rgba(94, 100, 255, 0.1);
  border-radius: 4px;
  height: 30px;
  width: 30px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  transition: all 0.2s ease;
}

.action-menu-btn:hover {
  background-color: rgba(94, 100, 255, 0.18);
  transform: translateY(-1px);
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.15);
}

.action-menu-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.action-menu-btn.active {
  background-color: var(--color-primary);
  color: var(--color-white);
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.15);
  transform: translateY(-1px);
}

.multi-select-controls {
  background-color: var(--color-light);
  padding: 8px 12px;
  border-radius: 6px;
  margin-bottom: 12px;
  border: 1px solid rgba(94, 100, 255, 0.2);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.select-all-btn {
  background-color: rgba(94, 100, 255, 0.1);
  color: var(--color-primary);
  border: 1px solid rgba(94, 100, 255, 0.2);
  font-size: 0.85rem;
  font-weight: 500;
  transition: all 0.2s ease;
}

.select-all-btn:hover {
  background-color: rgba(94, 100, 255, 0.2);
  border-color: rgba(94, 100, 255, 0.3);
}

.clear-btn {
  background-color: rgba(108, 117, 125, 0.1);
  color: var(--color-text-muted);
  border: 1px solid rgba(108, 117, 125, 0.2);
  font-size: 0.85rem;
  font-weight: 500;
  transition: all 0.2s ease;
}

.clear-btn:hover:not(:disabled) {
  background-color: rgba(108, 117, 125, 0.2);
  border-color: rgba(108, 117, 125, 0.3);
}

/* Session identity section */
.session-identity {
  padding: 12px 16px;
}

.folder-row.session-active .session-identity {
  background-color: rgba(255, 193, 7, 0.06);
}

.folder-row.session-finished .session-identity {
  background-color: rgba(40, 167, 69, 0.04);
}

.folder-row.session-unknown .session-identity {
  background-color: rgba(111, 66, 193, 0.04);
}

.session-name-label {
  font-size: 0.75rem;
  color: var(--color-text-light);
  font-weight: 500;
  margin-right: 4px;
}

.instance-link {
  color: var(--color-primary);
  text-decoration: none;
  font-weight: 600;
  font-size: 0.95rem;
  transition: all 0.15s ease;
}

.instance-link:hover {
  text-decoration: underline;
  color: var(--color-primary-hover);
}

.session-separator {
  color: var(--color-muted-separator);
  margin: 0 8px;
  font-weight: 400;
}

.session-id-part {
  color: var(--color-text-muted);
  font-weight: 500;
  font-size: 0.88rem;
}

/* Session icon square (matching instance-icon-square) */
.session-icon-square {
  width: 36px;
  height: 36px;
  min-width: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1rem;
}

.session-icon-active {
  background-color: rgba(255, 193, 7, 0.12);
  color: var(--color-amber-highlight);
}

.session-icon-finished {
  background-color: rgba(40, 167, 69, 0.12);
  color: var(--color-success-hover);
}

.session-icon-unknown {
  background-color: rgba(111, 66, 193, 0.12);
  color: var(--color-purple);
}

/* Session meta (matching instance-meta) */
.session-meta {
  display: flex;
  gap: 12px;
  font-size: 0.75rem;
  color: var(--color-text-muted);
  margin-top: 2px;
}

/* Show More button styling */
.show-more-btn {
  background-color: rgba(94, 100, 255, 0.05);
  color: var(--color-primary);
  border: 1px solid rgba(94, 100, 255, 0.2);
  font-size: 0.75rem;
  font-weight: 500;
  transition: all 0.2s ease;
  border-radius: 4px;
  padding: 0.25rem 0.75rem;
}

.show-more-btn:hover {
  background-color: rgba(94, 100, 255, 0.1);
  border-color: rgba(94, 100, 255, 0.3);
  color: var(--color-primary-hover);
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

/* Artifact type panel styles */
.type-panel-header-wrapper {
  cursor: pointer;
  user-select: none;
}

.type-panel-chevron {
  font-size: 0.65rem;
  color: var(--color-text-muted);
  width: 16px;
  text-align: center;
}

.type-panel-body {
  border-left: 1px solid var(--color-border);
  margin-left: 32px;
  padding-left: 20px;
  padding-top: 6px;
}

.download-file-btn {
  padding: 0.25rem 0.5rem;
  font-size: 0.75rem;
  opacity: 0.6;
  transition: opacity 0.15s ease;
}

.download-file-btn:hover {
  opacity: 1;
}
</style>
