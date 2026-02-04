<script setup lang="ts">
import {computed, nextTick, onMounted, ref} from 'vue';
import {useNavigation} from '@/composables/useNavigation';
import {downloadAssistantStore} from '@/stores/assistants';
import ProjectRepositoryClient from "@/services/api/ProjectRepositoryClient.ts";
import Utils from "@/services/Utils";
import ProjectSettingsClient from "@/services/api/ProjectSettingsClient.ts";
import SettingsResponse from "@/services/api/model/SettingsResponse.ts";
import RepositoryStatisticsModel from "@/services/api/model/RepositoryStatistics.ts";
import {ToastService} from "@/services/ToastService";
import RecordingSession from "@/services/api/model/RecordingSession.ts";
import RecordingStatus from "@/services/api/model/RecordingStatus.ts";
import RecordingFileType from "@/services/api/model/RecordingFileType.ts";
import * as bootstrap from 'bootstrap';
import RepositoryFile from "@/services/api/model/RepositoryFile.ts";
import ConfirmationDialog from '@/components/ConfirmationDialog.vue';
import Badge from '@/components/Badge.vue';
import RecordingFileRow from '@/components/RecordingFileRow.vue';
import FormattingService from "@/services/FormattingService.ts";
import RepositoryDisabledAlert from '@/components/alerts/RepositoryDisabledAlert.vue';
import RepositoryStatistics from '@/components/RepositoryStatistics.vue';
import ProjectClient from "@/services/api/ProjectClient.ts";
import ProjectInfo from "@/services/api/model/ProjectInfo.ts";
import WorkspaceType from "@/services/api/model/WorkspaceType.ts";
import WorkspaceClient from "@/services/api/WorkspaceClient.ts";
import Workspace from "@/services/api/model/Workspace.ts";
import PageHeader from '@/components/layout/PageHeader.vue';

// Using formatFileType from Utils class

const toast = ToastService;

const currentProject = ref<SettingsResponse | null>();
const projectInfo = ref<ProjectInfo | null>(null);
const workspaceInfo = ref<Workspace | null>(null);
const repositoryStatistics = ref<RepositoryStatisticsModel | null>(null);
const isLoading = ref(false);
const recordingSessions = ref<RecordingSession[]>([]);
const selectedRepositoryFile = ref<{ [sessionId: string]: { [sourceId: string]: boolean } }>({});
const showMultiSelectActions = ref<{ [sessionId: string]: boolean }>({});
const showActions = ref<{ [sessionId: string]: boolean }>({});

const {workspaceId, projectId, generateInstanceUrl} = useNavigation();

const parseSessionName = (name: string): { hostname: string; sessionId: string } => {
  const slashIndex = name.indexOf('/');
  if (slashIndex === -1) return { hostname: name, sessionId: '' };
  return { hostname: name.substring(0, slashIndex), sessionId: name.substring(slashIndex + 1) };
};

const repositoryService = new ProjectRepositoryClient(workspaceId.value!, projectId.value!)
const settingsService = new ProjectSettingsClient(workspaceId.value!, projectId.value!)
const projectClient = new ProjectClient(workspaceId.value!, projectId.value!)

// Download progress tracking for remote workspace downloads (uses global store)

// State for delete session confirmation modal
const deleteSessionDialog = ref(false);
const sessionToDelete = ref<RecordingSession | null>(null);
const deletingSession = ref(false);

// State for delete selected files confirmation modal
const deleteSelectedFilesDialog = ref(false);
const sessionIdWithFilesToDelete = ref('');
const deletingSelectedFiles = ref(false);

// Computed property to check if project is in REMOTE workspace
const isRemoteWorkspace = computed(() => {
  return workspaceInfo.value?.type === WorkspaceType.REMOTE;
});

onMounted(() => {
  fetchRepositoryData();
  fetchProjectSettings();
  fetchProjectInfo();
  fetchWorkspaceInfo();

  // Initialize tooltips after the DOM is loaded
  nextTick(() => {
    const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
    tooltipTriggerList.map(function (tooltipTriggerEl) {
      return new bootstrap.Tooltip(tooltipTriggerEl);
    });
  });
});


// Utility functions for formatting dates
const formatDate = (dateString: string | null | undefined): string => {
  if (!dateString) return '—';
  const date = new Date(dateString);

  // Format to UTC and in format yyyy-MM-dd HH:mm
  const year = date.getUTCFullYear();
  const month = String(date.getUTCMonth() + 1).padStart(2, '0');
  const day = String(date.getUTCDate()).padStart(2, '0');
  const hours = String(date.getUTCHours()).padStart(2, '0');
  const minutes = String(date.getUTCMinutes()).padStart(2, '0');

  return `${year}-${month}-${day} ${hours}:${minutes}`;
};

// Sort sessions from latest to earliest by created date (newest first)
const sortedSessions = computed(() => {
  return [...recordingSessions.value].sort((a, b) => {
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
  });
});

// Sort recordings in a session: non-recording files first, then recording + ASPROF_TEMP files
// Recording group sorted by filename descending to naturally group ASPROF_TEMP next to parent JFR
const getSortedRecordings = (session: RecordingSession) => {
  const getSortPriority = (file: RepositoryFile): number => {
    if (file.isRecordingFile || file.fileType === 'ASPROF_TEMP') return 1;
    return 0;
  };

  return [...session.files].sort((a, b) => {
    const priorityA = getSortPriority(a);
    const priorityB = getSortPriority(b);

    if (priorityA !== priorityB) {
      return priorityA - priorityB;
    }

    // Recording files group: sort by filename descending
    // Groups ASPROF_TEMP (*.jfr.N~) right before its parent JFR file
    if (priorityA === 1) {
      return b.name.localeCompare(a.name);
    }

    // Non-recording files: sort by creation date (newest first)
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
  });
};

// Toggle profiler settings expansion
const toggleProfilerSettings = (sessionId: string) => {
  expandedProfilerSettings.value[sessionId] = !expandedProfilerSettings.value[sessionId];

  // If turning on profiler settings, close multi-select mode and expand session
  if (expandedProfilerSettings.value[sessionId]) {
    showMultiSelectActions.value[sessionId] = false;
    expandedSessions.value[sessionId] = true;
  }
};

// Copy profiler settings to clipboard
const copyProfilerSettings = async (settings: string) => {
  try {
    await navigator.clipboard.writeText(settings);
    toast.success('Copied', 'Profiler settings copied to clipboard');
  } catch (error) {
    toast.error('Copy Failed', 'Failed to copy to clipboard');
  }
};

// Track which sessions are expanded
const expandedSessions = ref<{ [key: string]: boolean }>({});

// Track which profiler settings are expanded
const expandedProfilerSettings = ref<{ [key: string]: boolean }>({});

// Track how many files to show per session (default 10)
const visibleFilesCount = ref<{ [key: string]: number }>({});
const DEFAULT_FILES_LIMIT = 15;

// Initialize expanded state for sessions - ACTIVE sessions are expanded by default
const initializeExpandedState = () => {
  recordingSessions.value.forEach(session => {
    expandedSessions.value[session.id] = session.status === RecordingStatus.ACTIVE;

    // Initialize visible files count (default 10)
    if (visibleFilesCount.value[session.id] === undefined) {
      visibleFilesCount.value[session.id] = DEFAULT_FILES_LIMIT;
    }

    // Initialize selection state for each session
    if (!selectedRepositoryFile.value[session.id]) {
      selectedRepositoryFile.value[session.id] = {};
      session.files.forEach(source => {
        selectedRepositoryFile.value[session.id][source.id] = false;
      });
    }

    // Initialize multi-select actions visibility
    if (showMultiSelectActions.value[session.id] === undefined) {
      showMultiSelectActions.value[session.id] = false;
    }

    // Initialize action buttons visibility
    if (showActions.value[session.id] === undefined) {
      showActions.value[session.id] = false;
    }
  });
};

// Toggle expanded state for a session
const toggleSession = (sessionId: string) => {
  expandedSessions.value[sessionId] = !expandedSessions.value[sessionId];

  // Reset visible files count when collapsing a session
  if (!expandedSessions.value[sessionId]) {
    visibleFilesCount.value[sessionId] = DEFAULT_FILES_LIMIT;
    for (const key of Object.keys(visibleFilesCount.value)) {
      if (key.startsWith(sessionId + ':')) {
        visibleFilesCount.value[key] = DEFAULT_FILES_LIMIT;
      }
    }
  }
};

// Computed property to get the count of recordings for each session
const getSourcesCount = (session: RecordingSession): number => {
  return session.files.length;
};

// Helper function to debug session status
const getSessionStatusClass = (session: RecordingSession) => {
  if (session.status === RecordingStatus.ACTIVE) return 'session-active';
  if (session.status === RecordingStatus.FINISHED) return 'session-finished';
  if (session.status === RecordingStatus.UNKNOWN) return 'session-unknown';

  // If none of the above match, return the string value for debugging
  return `status-unknown session-${String(session.status).toLowerCase()}`;
};

const getStatusVariant = (status: RecordingStatus): string => {
  switch (status) {
    case RecordingStatus.ACTIVE:
      return 'warning';
    case RecordingStatus.FINISHED:
      return 'green';
    case RecordingStatus.UNKNOWN:
    default:
      return 'purple';
  }
};

const getStatusDotClass = (status: RecordingStatus): string => {
  switch (status) {
    case RecordingStatus.ACTIVE:
      return 'status-dot-active';
    case RecordingStatus.FINISHED:
      return 'status-dot-finished';
    case RecordingStatus.UNKNOWN:
    default:
      return 'status-dot-unknown';
  }
};

const fetchRepositoryData = async () => {
  isLoading.value = true;
  try {
    // Try to fetch recording sessions first to determine if repository exists
    recordingSessions.value = await repositoryService.listRecordingSessions();

    // Fetch repository statistics from backend
    repositoryStatistics.value = await repositoryService.getRepositoryStatistics();

    // Initialize the expanded state for sessions
    initializeExpandedState();
  } catch (error: any) {
    if (error.response && error.response.status === 404) {
      recordingSessions.value = [];
      repositoryStatistics.value = null;
    }
  } finally {
    isLoading.value = false;
  }
};

const fetchProjectSettings = async () => {
  try {
    currentProject.value = await settingsService.get();
  } catch (error: any) {
    toast.error('Failed to load project settings', error.message);
  }
};

const fetchProjectInfo = async () => {
  try {
    projectInfo.value = await projectClient.get();
  } catch (error: any) {
    console.error('Failed to load project info:', error);
  }
};

const fetchWorkspaceInfo = async () => {
  try {
    const workspaces = await WorkspaceClient.list();
    workspaceInfo.value = workspaces.find(w => w.id === workspaceId.value) || null;
  } catch (error: any) {
    console.error('Failed to load workspace info:', error);
  }
};


const toggleSelectionMode = (sessionId: string) => {
  // Toggle the multi-select mode for this session
  showMultiSelectActions.value[sessionId] = !showMultiSelectActions.value[sessionId];

  // If turning on selection mode, close profiler settings and expand session
  if (showMultiSelectActions.value[sessionId]) {
    expandedProfilerSettings.value[sessionId] = false;
    expandedSessions.value[sessionId] = true;
  } else {
    // If turning off selection mode, clear all selections
    clearAllSelections(sessionId);
  }
};

const toggleActionButtons = (sessionId: string) => {
  // Toggle the action buttons visibility for this session
  showActions.value[sessionId] = !showActions.value[sessionId];
};

const clearAllSelections = (sessionId: string) => {
  // Ensure the session exists in the selection object
  if (!selectedRepositoryFile.value[sessionId]) {
    selectedRepositoryFile.value[sessionId] = {};
  }

  // Set all sources to unselected
  const session = recordingSessions.value.find(s => s.id === sessionId);
  if (session) {
    session.files.forEach(source => {
      selectedRepositoryFile.value[sessionId][source.id] = false;
    });
  }
};

const getSelectedCount = (sessionId: string): number => {
  if (!selectedRepositoryFile.value[sessionId]) return 0;

  // Count the number of selected sources
  return Object.values(selectedRepositoryFile.value[sessionId]).filter(Boolean).length;
};

const toggleSourceSelection = (sessionId: string, sourceId: string) => {
  // Ensure the session exists in the selection object
  if (!selectedRepositoryFile.value[sessionId]) {
    selectedRepositoryFile.value[sessionId] = {};
  }

  // Toggle the selection status of the source
  selectedRepositoryFile.value[sessionId][sourceId] = !selectedRepositoryFile.value[sessionId][sourceId];
};

const toggleSelectAllSources = (sessionId: string, selectAll: boolean) => {
  const session = recordingSessions.value.find(s => s.id === sessionId);
  if (!session) return;

  // Ensure the session exists in the selection object
  if (!selectedRepositoryFile.value[sessionId]) {
    selectedRepositoryFile.value[sessionId] = {};
  }

  // Set all selectable sources to the selected state (exclude disabled ones)
  session.files.forEach(source => {
    if (!isCheckboxDisabled(source)) {
      selectedRepositoryFile.value[sessionId][source.id] = selectAll;
    }
  });
};

const copyAndMerge = async (sessionId: string) => {
  try {
    const session = recordingSessions.value.find(s => s.id === sessionId);
    if (!session) {
      toast.error('Merge & Copy', 'Session not found');
      return;
    }

    // For remote workspaces, use progress tracking
    if (isRemoteWorkspace.value) {
      const fileIds = session.files.map(f => f.id);
      await downloadAssistantStore.startDownload(workspaceId.value!, projectId.value!, sessionId, fileIds, async () => {
        await fetchRepositoryData();
      });
    } else {
      await repositoryService.copyRecordingSession(session, true);
      toast.success('Merge & Copy', `Successfully merged and copied session ${session.id}`);
      await fetchRepositoryData();
    }

  } catch (error: any) {
    console.error("Error merging and copying session:", error);
    toast.error('Merge & Copy', error.message || 'Failed to merge and copy recording session');
  }
};

const downloadSelectedSources = async (sessionId: string, merge: boolean) => {
  try {
    const session = recordingSessions.value.find(s => s.id === sessionId);
    if (!session) return;

    // Get all selected sources
    const selectedSources = session.files.filter(source =>
        selectedRepositoryFile.value[sessionId][source.id]
    );

    if (selectedSources.length === 0) {
      toast.info(merge ? 'Merge & Copy' : 'Copy Selected', 'No recordings selected');
      return;
    }

    // For remote workspaces, use progress tracking
    if (isRemoteWorkspace.value) {
      const fileIds = selectedSources.map(f => f.id);
      await downloadAssistantStore.startDownload(workspaceId.value!, projectId.value!, sessionId, fileIds, async () => {
        await fetchRepositoryData();
        toggleSelectAllSources(sessionId, false);
      });
    } else {
      await repositoryService.copySelectedRepositoryFile(session.id, selectedSources, merge);
      toast.success(
          merge ? 'Merge & Copy' : 'Copy Selected',
          `Successfully ${merge ? 'merged and copied' : 'copied'} ${selectedSources.length} recording(s)`
      );
      await fetchRepositoryData();
      toggleSelectAllSources(sessionId, false);
    }

  } catch (error: any) {
    console.error("Error processing selected recordings:", error);
    toast.error(
        merge ? 'Merge & Copy' : 'Copy Selected',
        error.message || `Failed to ${merge ? 'merge and copy' : 'copy'} selected recordings`
    );
  }
};

const deleteSelectedSources = async (sessionId: string) => {
  const session = recordingSessions.value.find(s => s.id === sessionId);
  if (!session) return;

  // Get all selected sources
  const selectedSources = session.files.filter(source =>
      selectedRepositoryFile.value[sessionId][source.id]
  );

  if (selectedSources.length === 0) {
    toast.info('Delete Selected', 'No recordings selected');
    return;
  }

  // Store session ID for the delete confirmation
  sessionIdWithFilesToDelete.value = sessionId;

  // Show the modal
  deleteSelectedFilesDialog.value = true;
};

const confirmDeleteSelectedFiles = async () => {
  if (!sessionIdWithFilesToDelete.value) return;

  const sessionId = sessionIdWithFilesToDelete.value;
  const session = recordingSessions.value.find(s => s.id === sessionId);
  if (!session) return;

  deletingSelectedFiles.value = true;

  try {
    // Get all selected sources
    const selectedSources = session.files.filter(source =>
        selectedRepositoryFile.value[sessionId][source.id]
    );

    await repositoryService.deleteSelectedRepositoryFile(sessionId, selectedSources);
    toast.success('Delete Selected', `Successfully deleted ${selectedSources.length} recording(s)`);

    // Refresh sessions list
    await fetchRepositoryData();

    // Clear selections after deletion
    toggleSelectAllSources(sessionId, false);

    // Close the modal
    deleteSelectedFilesDialog.value = false;
  } catch (error: any) {
    console.error("Error deleting selected recordings:", error);
    toast.error('Delete Selected', error.message || 'Failed to delete selected recordings');
  } finally {
    deletingSelectedFiles.value = false;
    sessionIdWithFilesToDelete.value = '';
  }
};

const deleteAll = async (sessionId: string) => {
  // Initialize the modal confirmation
  const session = recordingSessions.value.find(s => s.id === sessionId);
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
    await repositoryService.deleteRecordingSession(sessionToDelete.value);
    toast.success('Delete All', 'Successfully deleted all recordings in the session');

    // Refresh sessions list
    await fetchRepositoryData();

    // Close the modal
    deleteSessionDialog.value = false;
  } catch (error: any) {
    console.error("Error deleting recording session:", error);
    toast.error('Delete All', error.message || 'Failed to delete recording session');
  } finally {
    deletingSession.value = false;
    sessionToDelete.value = null;
  }
};

// Check if checkbox should be disabled for a repository file
const isCheckboxDisabled = (source: RepositoryFile): boolean => {
  return source.status === RecordingStatus.ACTIVE || source.fileType === RecordingFileType.ASPROF;
};

// --- Rotation Group Logic ---

interface FileGroupEntry {
  primary: RepositoryFile;
  children: RepositoryFile[];
  totalSize: number;
}

const expandedRotatedGroups = ref<{ [key: string]: boolean }>({});

// --- Artifact Type Grouping ---

type ArtifactTypeGroup = 'JFR_RECORDING' | 'HEAP_DUMP' | 'PERF_COUNTERS' | 'JVM_LOG' | 'HS_JVM_ERROR_LOG' | 'UNKNOWN';

const TYPE_GROUP_ORDER: ArtifactTypeGroup[] = [
  'JFR_RECORDING', 'HEAP_DUMP', 'PERF_COUNTERS', 'JVM_LOG', 'HS_JVM_ERROR_LOG', 'UNKNOWN',
];

const FILE_TYPE_TO_GROUP: Record<string, ArtifactTypeGroup> = {
  [RecordingFileType.JFR]: 'JFR_RECORDING',
  [RecordingFileType.JFR_LZ4]: 'JFR_RECORDING',
  [RecordingFileType.ASPROF]: 'JFR_RECORDING',
  [RecordingFileType.HEAP_DUMP]: 'HEAP_DUMP',
  [RecordingFileType.HEAP_DUMP_GZ]: 'HEAP_DUMP',
  [RecordingFileType.PERF_COUNTERS]: 'PERF_COUNTERS',
  [RecordingFileType.JVM_LOG]: 'JVM_LOG',
  [RecordingFileType.HS_JVM_ERROR_LOG]: 'HS_JVM_ERROR_LOG',
  [RecordingFileType.UNKNOWN]: 'UNKNOWN',
};

const TYPE_GROUP_DISPLAY: Record<ArtifactTypeGroup, { name: string; variant: string; fileType: string }> = {
  'JFR_RECORDING':    { name: 'JFR Recordings',     variant: 'primary', fileType: 'JFR' },
  'HEAP_DUMP':        { name: 'Heap Dumps',          variant: 'purple',  fileType: 'HEAP_DUMP' },
  'PERF_COUNTERS':    { name: 'Perf Counters',       variant: 'blue',    fileType: 'PERF_COUNTERS' },
  'JVM_LOG':          { name: 'JVM Logs',            variant: 'teal',    fileType: 'JVM_LOG' },
  'HS_JVM_ERROR_LOG': { name: 'HotSpot Error Logs',  variant: 'red',     fileType: 'HS_JVM_ERROR_LOG' },
  'UNKNOWN':          { name: 'Other Files',          variant: 'grey',    fileType: 'UNKNOWN' },
};

interface TypeGroupPanel {
  groupKey: ArtifactTypeGroup;
  display: { name: string; variant: string; fileType: string };
  files: RepositoryFile[];
  fileCount: number;
  totalSize: number;
}

const expandedTypePanels = ref<{ [key: string]: boolean }>({});

const parseRotatedFilename = (filename: string): { baseName: string; suffix: number } | null => {
  const match = filename.match(/^(.+)\.(\d+)$/);
  if (!match) return null;
  return { baseName: match[1], suffix: parseInt(match[2], 10) };
};

const groupRotatedFiles = (sortedFiles: RepositoryFile[]): FileGroupEntry[] => {
  const nameSet = new Set(sortedFiles.map(f => f.name));
  const grouped = new Map<string, FileGroupEntry>();
  const assigned = new Set<string>();

  // First pass: identify primaries and collect children
  for (const file of sortedFiles) {
    const parsed = parseRotatedFilename(file.name);
    if (parsed && nameSet.has(parsed.baseName)) {
      // This is a rotated variant — will be added as a child
      if (!grouped.has(parsed.baseName)) {
        // Primary hasn't been seen yet, create a placeholder
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

  // Sort children within each group by suffix ascending
  for (const group of grouped.values()) {
    group.children.sort((a, b) => {
      const pa = parseRotatedFilename(a.name);
      const pb = parseRotatedFilename(b.name);
      return (pa?.suffix ?? 0) - (pb?.suffix ?? 0);
    });
  }

  // Second pass: build result preserving original order
  const result: FileGroupEntry[] = [];
  for (const file of sortedFiles) {
    if (assigned.has(file.id)) {
      // If this is a primary, emit the group
      if (grouped.has(file.name)) {
        result.push(grouped.get(file.name)!);
        grouped.delete(file.name); // prevent duplicates
      }
      // If it's a child, skip (already in a group)
    } else {
      // Standalone file
      result.push({ primary: file, children: [], totalSize: file.size });
    }
  }

  return result;
};

const isRecordingFileType = (fileType: string): boolean => {
  return fileType === RecordingFileType.JFR || fileType === RecordingFileType.JFR_LZ4 || fileType === RecordingFileType.ASPROF;
};

const getRecordingGroupedFiles = (session: RecordingSession): FileGroupEntry[] => {
  const sortedFiles = getSortedRecordings(session);
  const recordingFiles = sortedFiles.filter(f => isRecordingFileType(f.fileType));
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
  const session = recordingSessions.value.find(s => s.id === sessionId);
  if (!session) return;
  visibleFilesCount.value[sessionId] = getRecordingGroupedFiles(session).length;
};

const getRemainingRecordingGroupCount = (session: RecordingSession): number => {
  const limit = visibleFilesCount.value[session.id] || DEFAULT_FILES_LIMIT;
  return getRecordingGroupedFiles(session).length - limit;
};

const getArtifactGroupMap = (session: RecordingSession): Map<ArtifactTypeGroup, RepositoryFile[]> => {
  const sortedFiles = getSortedRecordings(session);
  const groupMap = new Map<ArtifactTypeGroup, RepositoryFile[]>();
  for (const file of sortedFiles) {
    if (isRecordingFileType(file.fileType)) continue;
    const groupKey = FILE_TYPE_TO_GROUP[file.fileType] || 'UNKNOWN';
    if (!groupMap.has(groupKey)) groupMap.set(groupKey, []);
    groupMap.get(groupKey)!.push(file);
  }
  return groupMap;
};

const getTypeGroupPanels = (session: RecordingSession): TypeGroupPanel[] => {
  const groupMap = getArtifactGroupMap(session);
  const panels: TypeGroupPanel[] = [];
  for (const groupKey of TYPE_GROUP_ORDER) {
    if (groupKey === 'JFR_RECORDING') continue;
    const files = groupMap.get(groupKey);
    if (!files || files.length <= 1) continue;
    panels.push({
      groupKey,
      display: TYPE_GROUP_DISPLAY[groupKey],
      files,
      fileCount: files.length,
      totalSize: files.reduce((sum, f) => sum + f.size, 0),
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
    if (files && files.length === 1) {
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

// Simplified status wrapper — only status classes, no file-type classes
const getSourceStatusWrapperClass = (source: RepositoryFile, sessionId: string) => {
  const classes = [];

  if (selectedRepositoryFile.value[sessionId] && selectedRepositoryFile.value[sessionId][source.id]) {
    classes.push('source-selected');
  }

  if (source.status === RecordingStatus.ACTIVE) classes.push('source-active');
  else if (source.status === RecordingStatus.FINISHED) classes.push('source-finished');
  else if (source.status === RecordingStatus.UNKNOWN) classes.push('source-unknown');

  return classes.join(' ');
};
</script>

<template>
  <PageHeader
    title="Remote Repository"
    description="View and manage recordings stored in the remote repository"
    icon="bi-folder"
  >
    <!-- Repository Statistics Cards -->
    <div class="col-12" v-if="!isLoading">
      <RepositoryStatistics :statistics="repositoryStatistics"/>
    </div>

    <!-- Recording Sessions Header Bar -->
    <div class="col-12" v-if="recordingSessions.length > 0">
      <div class="d-flex align-items-center mb-3 gap-3">
        <div class="sessions-header-bar flex-grow-1 d-flex align-items-center px-3">
          <span class="header-text">Recording Sessions ({{ recordingSessions.length }})</span>
        </div>
      </div>
    </div>

    <!-- Recording Sessions List -->
    <div class="col-12" v-if="recordingSessions.length > 0">
      <div v-for="session in sortedSessions" :key="session.id" class="mb-3">
              <!-- Session header -->
              <div class="folder-row p-3 rounded"
                   :class="getSessionStatusClass(session)"
                   @click="toggleSession(session.id)">
                <div class="d-flex justify-content-between align-items-start">
                  <div class="d-flex align-items-start">
                    <i class="bi fs-4 me-3 text-primary"
                       :class="expandedSessions[session.id] ? 'bi-folder2-open' : 'bi-folder2'"></i>
                    <div>
                      <div class="d-flex align-items-center gap-2">
                        <span class="status-dot" :class="getStatusDotClass(session.status)"></span>
                        <span class="session-name-label">Instance:</span>
                        <router-link
                            :to="generateInstanceUrl(session.instanceId)"
                            class="instance-link"
                            @click.stop>
                          {{ parseSessionName(session.name).hostname }}
                        </router-link>
                        <span class="session-separator">/</span>
                        <span class="session-name-label">Session:</span>
                        <span class="session-id-part">{{ parseSessionName(session.name).sessionId }}</span>
                      </div>
                      <div class="session-metadata">
                        {{ getSourcesCount(session) }} sources · {{ formatDate(session.createdAt) }} · {{ session.id }}
                      </div>
                    </div>
                  </div>
                  <div class="d-flex align-items-center gap-1">
                    <div v-if="showActions[session.id]"
                         class="d-flex align-items-center gap-1 action-buttons-container">
                      <button
                          class="btn btn-sm btn-outline-primary"
                          type="button"
                          title="Merge and Copy Recordings"
                          @click.stop="copyAndMerge(session.id)">
                        <i class="bi bi-folder-symlink me-1"></i>Merge &amp; Copy
                      </button>
                      <button
                          v-if="session.status === RecordingStatus.FINISHED"
                          class="btn btn-sm btn-outline-danger"
                          type="button"
                          title="Delete Session"
                          @click.stop="deleteAll(session.id)">
                        <i class="bi bi-trash"></i>
                      </button>
                    </div>
                    <button
                        class="action-btn action-menu-btn"
                        :class="{'active': showActions[session.id]}"
                        type="button"
                        title="Show/Hide Actions"
                        @click.stop="toggleActionButtons(session.id)">
                      <i class="bi bi-three-dots"></i>
                    </button>
                    <button
                        class="action-btn action-menu-btn ms-1"
                        :class="{'active': showMultiSelectActions[session.id]}"
                        type="button"
                        title="Toggle multi-select mode"
                        @click.stop="toggleSelectionMode(session.id)">
                      <i class="bi"
                         :class="{'bi-check2-square': showMultiSelectActions[session.id], 'bi-square': !showMultiSelectActions[session.id]}"></i>
                    </button>
                    <button
                        v-if="session.profilerSettings"
                        class="action-btn action-menu-btn ms-1"
                        :class="{'active': expandedProfilerSettings[session.id]}"
                        type="button"
                        title="Show profiler configuration"
                        @click.stop="toggleProfilerSettings(session.id)">
                      <i class="bi bi-gear-fill"></i>
                    </button>
                  </div>
                </div>
              </div>

              <!-- Session recordings (shown when expanded) -->
              <div v-if="expandedSessions[session.id]" class="ps-4 pt-2">
                <!-- Profiler Configuration Section -->
                <div v-if="expandedProfilerSettings[session.id] && session.profilerSettings"
                     class="profiler-config-controls d-flex align-items-center mb-2">
                  <i class="bi bi-gear-fill me-2 text-primary"></i>
                  <code
                    class="raw-command-code mb-0 clickable"
                    @click.stop="copyProfilerSettings(session.profilerSettings)"
                    title="Click to copy">
                    {{ session.profilerSettings }}
                  </code>
                </div>

                <!-- Multi-select controls -->
                <div v-if="showMultiSelectActions[session.id]"
                     class="multi-select-controls d-flex justify-content-between align-items-center mb-2">
                  <div class="d-flex align-items-center">
                    <button
                        class="btn btn-sm select-all-btn me-2"
                        @click.stop="toggleSelectAllSources(session.id, true)"
                        title="Select all recordings">
                      <i class="bi bi-check2-square me-1"></i>Select All
                    </button>
                    <button
                        class="btn btn-sm clear-btn"
                        @click.stop="toggleSelectAllSources(session.id, false)"
                        :disabled="getSelectedCount(session.id) === 0"
                        title="Clear selection">
                      <i class="bi bi-x-lg me-1"></i>Clear
                    </button>
                  </div>

                  <div class="d-flex align-items-center gap-2">
                    <Badge v-if="getSelectedCount(session.id) > 0" :value="`${getSelectedCount(session.id)} selected`"
                           variant="secondary" size="xs" class="me-2"/>
                    <button
                        class="btn btn-sm btn-outline-primary"
                        @click.stop="downloadSelectedSources(session.id, true)"
                        :disabled="getSelectedCount(session.id) === 0"
                        title="Merge and copy selected recordings">
                      <i class="bi bi-folder-symlink me-1"></i>Merge &amp; Copy
                    </button>
                    <button
                        v-if="!isRemoteWorkspace"
                        class="btn btn-sm btn-outline-danger"
                        @click.stop="deleteSelectedSources(session.id)"
                        :disabled="getSelectedCount(session.id) === 0"
                        title="Delete selected recordings">
                      <i class="bi bi-trash me-1"></i>Delete Selected
                    </button>
                  </div>
                </div>

                <!-- Artifact type panels (non-recording types with > 1 file) -->
                <div v-for="panel in getTypeGroupPanels(session)" :key="panel.groupKey" class="type-panel mb-2">
                  <!-- Type panel header -->
                  <div class="type-panel-header-wrapper" @click="toggleTypePanel(session.id, panel.groupKey)">
                    <RecordingFileRow
                      :filename="panel.display.name"
                      :fileType="panel.display.fileType"
                      :sizeInBytes="panel.totalSize"
                    >
                      <template #before>
                        <i class="bi me-1 type-panel-chevron"
                           :class="isTypePanelExpanded(session.id, panel.groupKey) ? 'bi-chevron-down' : 'bi-chevron-right'"></i>
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
                      <div class="source-status-wrapper mb-2 rounded"
                           :class="getSourceStatusWrapperClass(file, session.id)">
                        <RecordingFileRow
                          :filename="file.name"
                          :fileType="file.fileType"
                          :sizeInBytes="file.size"
                          :timestamp="file.createdAt"
                          :status="file.status"
                        >
                          <template #before>
                            <div class="form-check file-form-check me-2"
                                 v-if="showMultiSelectActions[session.id] && !isCheckboxDisabled(file)">
                              <input
                                  class="form-check-input file-checkbox"
                                  type="checkbox"
                                  :id="'source-' + file.id"
                                  :checked="selectedRepositoryFile[session.id] && selectedRepositoryFile[session.id][file.id]"
                                  @change="() => toggleSourceSelection(session.id, file.id)"
                                  @click.stop>
                            </div>
                          </template>
                          <template #extra-badges>
                            <Badge v-if="file.status === RecordingStatus.UNKNOWN"
                                   :value="Utils.capitalize(file.status.toLowerCase())" variant="purple" size="xxs"
                                   class="ms-1"/>
                            <Badge v-if="file.isFinishingFile" value="Finisher" variant="green" size="xxs" class="ms-1"
                                   title="This file indicates the session is finished"/>
                          </template>
                        </RecordingFileRow>
                      </div>
                    </template>

                    <!-- Show More per panel -->
                    <div v-if="hasMoreInPanel(panel, session.id)" class="text-center mt-1 mb-2">
                      <button class="btn btn-sm btn-outline-primary show-more-btn"
                              @click.stop="showMoreInPanel(session.id, panel.groupKey, panel.fileCount)">
                        <i class="bi bi-chevron-down me-1"></i>Show {{ getRemainingInPanel(panel, session.id) }} more
                      </button>
                    </div>
                  </div>
                </div>

                <!-- Standalone artifact files (non-recording types with exactly 1 file) -->
                <template v-for="file in getStandaloneArtifactFiles(session)" :key="file.id">
                  <div class="source-status-wrapper mb-2 rounded"
                       :class="getSourceStatusWrapperClass(file, session.id)">
                    <RecordingFileRow
                      :filename="file.name"
                      :fileType="file.fileType"
                      :sizeInBytes="file.size"
                      :timestamp="file.createdAt"
                      :status="file.status"
                    >
                      <template #before>
                        <div class="form-check file-form-check me-2"
                             v-if="showMultiSelectActions[session.id] && !isCheckboxDisabled(file)">
                          <input
                              class="form-check-input file-checkbox"
                              type="checkbox"
                              :id="'source-' + file.id"
                              :checked="selectedRepositoryFile[session.id] && selectedRepositoryFile[session.id][file.id]"
                              @change="() => toggleSourceSelection(session.id, file.id)"
                              @click.stop>
                        </div>
                      </template>
                      <template #extra-badges>
                        <Badge v-if="file.status === RecordingStatus.UNKNOWN"
                               :value="Utils.capitalize(file.status.toLowerCase())" variant="purple" size="xxs"
                               class="ms-1"/>
                        <Badge v-if="file.isFinishingFile" value="Finisher" variant="green" size="xxs" class="ms-1"
                               title="This file indicates the session is finished"/>
                      </template>
                    </RecordingFileRow>
                  </div>
                </template>

                <!-- Recording files (flat with rotation grouping) -->
                <template v-for="entry in getVisibleRecordingGroups(session)" :key="entry.primary.id">
                  <!-- Primary row -->
                  <div class="source-status-wrapper mb-2 rounded"
                       :class="getSourceStatusWrapperClass(entry.primary, session.id)">
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
                          :title="isRotatedGroupExpanded(session.id, entry.primary.name) ? 'Collapse rotated files' : 'Expand rotated files'"
                        >
                          <i class="bi" :class="isRotatedGroupExpanded(session.id, entry.primary.name) ? 'bi-chevron-down' : 'bi-chevron-right'"></i>
                        </button>
                        <div class="form-check file-form-check me-2"
                             v-if="showMultiSelectActions[session.id] && !isCheckboxDisabled(entry.primary)">
                          <input
                              class="form-check-input file-checkbox"
                              type="checkbox"
                              :id="'source-' + entry.primary.id"
                              :checked="selectedRepositoryFile[session.id] && selectedRepositoryFile[session.id][entry.primary.id]"
                              @change="() => toggleSourceSelection(session.id, entry.primary.id)"
                              @click.stop>
                        </div>
                      </template>
                      <template #extra-badges>
                        <Badge v-if="entry.primary.status === RecordingStatus.UNKNOWN"
                               :value="Utils.capitalize(entry.primary.status.toLowerCase())" variant="purple" size="xxs"
                               class="ms-1"/>
                        <Badge v-if="entry.primary.isFinishingFile" value="Finisher" variant="green" size="xxs" class="ms-1"
                               title="This file indicates the session is finished"/>
                        <Badge v-if="entry.children.length > 0"
                               :value="`+${entry.children.length} rotated · ${FormattingService.formatBytes(entry.totalSize)}`"
                               variant="grey" size="xxs" class="ms-1" :uppercase="false"/>
                      </template>
                    </RecordingFileRow>
                  </div>

                  <!-- Rotated children (when expanded) -->
                  <template v-if="entry.children.length > 0 && isRotatedGroupExpanded(session.id, entry.primary.name)">
                    <div v-for="child in entry.children" :key="child.id"
                         class="rotated-child-row mb-2 rounded"
                         :class="getSourceStatusWrapperClass(child, session.id)">
                      <RecordingFileRow
                        :filename="child.name"
                        :fileType="child.fileType"
                        :sizeInBytes="child.size"
                        :timestamp="child.createdAt"
                        :status="child.status"
                      >
                        <template #before>
                          <div class="form-check file-form-check me-2"
                               v-if="showMultiSelectActions[session.id] && !isCheckboxDisabled(child)">
                            <input
                                class="form-check-input file-checkbox"
                                type="checkbox"
                                :id="'source-' + child.id"
                                :checked="selectedRepositoryFile[session.id] && selectedRepositoryFile[session.id][child.id]"
                                @change="() => toggleSourceSelection(session.id, child.id)"
                                @click.stop>
                          </div>
                        </template>
                        <template #extra-badges>
                          <Badge v-if="child.status === RecordingStatus.UNKNOWN"
                                 :value="Utils.capitalize(child.status.toLowerCase())" variant="purple" size="xxs"
                                 class="ms-1"/>
                          <Badge v-if="child.isFinishingFile" value="Finisher" variant="green" size="xxs" class="ms-1"
                                 title="This file indicates the session is finished"/>
                        </template>
                      </RecordingFileRow>
                    </div>
                  </template>
                </template>

                <!-- Show More for recordings -->
                <div v-if="hasMoreRecordingGroups(session)" class="text-center mt-1 mb-2">
                  <button class="btn btn-sm btn-outline-primary show-more-btn"
                          @click.stop="showMoreRecordingGroups(session.id)">
                    <i class="bi bi-chevron-down me-1"></i>Show {{ getRemainingRecordingGroupCount(session) }} more files
                  </button>
                </div>
              </div>
            </div>
    </div>

    <!-- Loading Sessions Placeholder -->
    <div class="col-12" v-if="isLoading && !recordingSessions.length">
      <div class="modern-empty-state loading">
        <div class="spinner-border text-primary" role="status">
          <span class="visually-hidden">Loading...</span>
        </div>
        <p class="mt-3">Loading recording sessions...</p>
      </div>
    </div>

    <!-- No Sessions Message -->
    <div class="col-12" v-if="!isLoading && !recordingSessions.length">
      <div class="modern-empty-state">
        <i class="bi bi-folder-x display-4 text-muted"></i>
        <h5 class="mt-3">No Recording Sessions Available</h5>
        <p class="text-muted">There are no recording sessions available for this repository.</p>
      </div>
    </div>

  <!-- Loading Placeholder -->
  <div class="container-fluid p-4" v-if="isLoading">
    <div class="row">
      <div class="col-12">
        <div class="card shadow-sm border-0">
          <div class="card-header bg-light d-flex justify-content-between align-items-center py-3">
            <div class="d-flex align-items-center">
              <i class="bi bi-link-45deg fs-4 me-2 text-primary"></i>
              <h5 class="card-title mb-0">Repository</h5>
            </div>
          </div>
          <div class="card-body p-5 text-center">
            <div class="spinner-border text-primary" role="status">
              <span class="visually-hidden">Loading...</span>
            </div>
            <p class="mt-3">Loading repository information...</p>
          </div>
        </div>
      </div>
    </div>
  </div>
  </PageHeader>

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
.card {
  border-radius: 0.5rem;
  overflow: hidden;
  transition: all 0.2s ease;
}

.card-header {
  border-bottom: none;
}

/* Sessions header bar styling */
.sessions-header-bar {
  background: linear-gradient(135deg, #5e64ff 0%, #4a50e2 100%);
  border: 1px solid #4a50e2;
  border-radius: 6px;
  box-shadow: 0 2px 6px rgba(94, 100, 255, 0.25);
  position: relative;
  height: 31px;
}

.header-text {
  font-size: 0.75rem;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.95);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-family: 'SF Pro Display', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
  backdrop-filter: blur(1px);
}

.cursor-pointer {
  cursor: pointer;
}

code {
  background-color: #f8f9fa;
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  font-size: 0.875rem;
  word-break: break-all;
}

.form-check-input:checked {
  background-color: #5e64ff;
  border-color: #5e64ff;
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
  border-color: #5e64ff;
  box-shadow: 0 0 0 0.1rem rgba(94, 100, 255, 0.2);
}

.file-form-check {
  min-height: auto;
  margin-bottom: 0;
  padding-left: 1.8em;
}

.btn-primary {
  background-color: #5e64ff;
  border-color: #5e64ff;
}

.btn-primary:hover {
  background-color: #4a51eb;
  border-color: #4a51eb;
}

.btn-outline-danger:hover {
  background-color: #e63757;
  border-color: #e63757;
}

.form-control:focus {
  border-color: #d8e2ef;
  box-shadow: none;
  outline: none;
}

.table {
  margin-bottom: 0;
}

.table th {
  vertical-align: middle;
  font-weight: 500;
}

.table td {
  vertical-align: middle;
}

.folder-row {
  background-color: white;
  cursor: pointer;
  transition: all 0.15s ease;
  border: 1px solid #eee;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.folder-row:hover {
  background-color: rgba(94, 100, 255, 0.03);
  transform: translateY(-1px);
  box-shadow: 0 3px 6px rgba(0, 0, 0, 0.08);
}

.folder-row.session-active {
  background-color: rgba(255, 193, 7, 0.07);
  border-left: 3px solid #ffc107;
}

.folder-row.session-finished {
  background-color: rgba(40, 167, 69, 0.05);
  border-left: 3px solid #28a745;
}

.folder-row.session-unknown {
  background-color: rgba(111, 66, 193, 0.05);
  border-left: 3px solid #6f42c1;
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
  color: #6c757d;
  font-size: 0.7rem;
  cursor: pointer;
  border-radius: 3px;
  transition: all 0.15s ease;
}

.rotation-toggle-btn:hover {
  background-color: rgba(94, 100, 255, 0.1);
  color: #5e64ff;
}

.rotated-child-row {
  margin-left: 28px;
  border-left: 2px solid #dee2e6;
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
  border-top: 1px solid #dee2e6;
}

.text-primary {
  color: #5e64ff !important;
}

.text-warning {
  color: #d99e00 !important;
}

.text-secondary {
  color: #6c757d !important;
}


.modern-empty-state {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: #adb5bd;
  background-color: white;
  border-radius: 10px;
  padding: 3rem;
  text-align: center;
  box-shadow: 0 2px 15px rgba(0, 0, 0, 0.05);
  margin-bottom: 2rem;
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
  color: #5e64ff;
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
  background-color: #5e64ff;
  color: #fff;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.15);
  transform: translateY(-1px);
}

.multi-select-controls {
  background-color: #f8f9fa;
  padding: 8px 12px;
  border-radius: 6px;
  margin-bottom: 12px;
  border: 1px solid rgba(94, 100, 255, 0.2);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.select-all-btn {
  background-color: rgba(94, 100, 255, 0.1);
  color: #5e64ff;
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
  color: #6c757d;
  border: 1px solid rgba(108, 117, 125, 0.2);
  font-size: 0.85rem;
  font-weight: 500;
  transition: all 0.2s ease;
}

.clear-btn:hover:not(:disabled) {
  background-color: rgba(108, 117, 125, 0.2);
  border-color: rgba(108, 117, 125, 0.3);
}


@keyframes fadeIn {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

/* Session header layout */
.session-name-label {
  font-size: 0.75rem;
  color: #9ca3af;
  font-weight: 500;
  margin-right: 4px;
}

.instance-link {
  color: #5e64ff;
  text-decoration: none;
  font-weight: 600;
  font-size: 0.95rem;
  transition: all 0.15s ease;
}

.instance-link:hover {
  text-decoration: underline;
  color: #4a51eb;
}

.session-separator {
  color: #d1d5db;
  margin: 0 8px;
  font-weight: 400;
}

.session-id-part {
  color: #6b7280;
  font-weight: 500;
  font-size: 0.88rem;
}

.session-name {
  font-size: 0.95rem;
  color: #1f2937;
}

.session-metadata {
  font-size: 0.75rem;
  color: #6b7280;
  margin-top: 2px;
  margin-left: 18px;
}

/* Status dot indicator */
.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-dot-active {
  background-color: #f59e0b;
  box-shadow: 0 0 6px rgba(245, 158, 11, 0.5);
}

.status-dot-finished {
  background-color: #10b981;
  box-shadow: 0 0 6px rgba(16, 185, 129, 0.4);
}

.status-dot-unknown {
  background-color: #8b5cf6;
  box-shadow: 0 0 6px rgba(139, 92, 246, 0.4);
}

/* Form switch styling */
.form-check-input[id="createDirectory"] {
  width: 2.5em;
  height: 1.25em;
}


/* Action buttons animation */
.action-buttons-container {
  animation: fadeIn 0.2s ease-in-out;
}

/* Show More button styling */
.show-more-btn {
  background-color: rgba(94, 100, 255, 0.05);
  color: #5e64ff;
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
  color: #4a51eb;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

/* Profiler Configuration Controls Styling */
.profiler-config-controls {
  background-color: #f8f9fa;
  padding: 8px 12px;
  border-radius: 6px;
  margin-bottom: 12px;
  border: 1px solid rgba(94, 100, 255, 0.2);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.profiler-config-controls .raw-command-code {
  background-color: white;
  padding: 0.5rem 0.75rem;
  border-radius: 4px;
  font-size: 0.75rem;
  word-break: break-all;
  color: #2d3748;
  border: 1px solid #e2e8f0;
  font-family: 'Monaco', 'Consolas', monospace;
  line-height: 1.6;
  max-height: 100px;
  overflow-y: auto;
  width: 100%;
  flex: 1;
  cursor: pointer;
  transition: all 0.15s ease;
}

.profiler-config-controls .raw-command-code:hover {
  background-color: #f8f9fa;
  border-color: #5e64ff;
  box-shadow: 0 0 0 0.1rem rgba(94, 100, 255, 0.1);
}

/* Artifact type panel styles */
.type-panel-header-wrapper {
  cursor: pointer;
  user-select: none;
}

.type-panel-chevron {
  font-size: 0.65rem;
  color: #6c757d;
  width: 16px;
  text-align: center;
}

.type-panel-body {
  border-left: 1px solid #e9ecef;
  margin-left: 32px;
  padding-left: 20px;
  padding-top: 6px;
}

</style>
