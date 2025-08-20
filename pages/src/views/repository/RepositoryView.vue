<script setup lang="ts">
import {computed, nextTick, onMounted, ref} from 'vue';
import {useRoute} from 'vue-router'
import ProjectRepositoryClient from "@/services/project/ProjectRepositoryClient.ts";
import Utils from "@/services/Utils";
import ProjectSettingsClient from "@/services/project/ProjectSettingsClient.ts";
import RepositoryInfo from "@/services/project/model/RepositoryInfo.ts";
import SettingsResponse from "@/services/project/model/SettingsResponse.ts";
import {ToastService} from "@/services/ToastService";
import RecordingSession from "@/services/model/data/RecordingSession.ts";
import RecordingStatus from "@/services/model/data/RecordingStatus.ts";
import RecordingFileType from "@/services/model/data/RecordingFileType.ts";
import * as bootstrap from 'bootstrap';
import RepositoryFile from "@/services/model/data/RepositoryFile.ts";
import ConfirmationDialog from '@/components/ConfirmationDialog.vue';
import Badge from '@/components/Badge.vue';
import FormattingService from "@/services/FormattingService.ts";
import RepositoryDisabledAlert from '@/components/alerts/RepositoryDisabledAlert.vue';
import ProjectClient from "@/services/ProjectClient.ts";
import ProjectInfo from "@/services/project/model/ProjectInfo.ts";

// Using formatFileType from Utils class

const route = useRoute()
const toast = ToastService;

const currentProject = ref<SettingsResponse | null>();
const currentRepository = ref<RepositoryInfo | null>();
const projectInfo = ref<ProjectInfo | null>(null);
const isLoading = ref(false);
const recordingSessions = ref<RecordingSession[]>([]);
const selectedRepositoryFile = ref<{ [sessionId: string]: { [sourceId: string]: boolean } }>({});
const showMultiSelectActions = ref<{ [sessionId: string]: boolean }>({});
const showActions = ref<{ [sessionId: string]: boolean }>({});

const repositoryService = new ProjectRepositoryClient(route.params.projectId as string)
const settingsService = new ProjectSettingsClient(route.params.projectId as string)
const projectClient = new ProjectClient(route.params.projectId as string)

// State for delete session confirmation modal
const deleteSessionDialog = ref(false);
const sessionToDelete = ref<RecordingSession | null>(null);
const deletingSession = ref(false);

// State for delete selected files confirmation modal
const deleteSelectedFilesDialog = ref(false);
const sessionIdWithFilesToDelete = ref('');
const deletingSelectedFiles = ref(false);

// Computed property to check if project is in LOCAL workspace
// const isLocalWorkspace = computed(() => {
//   return projectInfo.value?.workspaceId === null;
// });
const isLocalWorkspace = computed(() => {
  return false;
});


// Repository Statistics Computed Properties
const totalSessions = computed(() => {
  // Use mock data if no real sessions exist
  if (recordingSessions.value.length === 0 && currentRepository.value) {
    return 12; // Mock: 12 sessions
  }
  return recordingSessions.value.length;
});

const totalFiles = computed(() => {
  // Use mock data if no real sessions exist
  if (recordingSessions.value.length === 0 && currentRepository.value) {
    return 147; // Mock: 147 total files
  }
  return recordingSessions.value.reduce((total, session) => {
    return total + session.files.length;
  }, 0);
});

const totalRepositorySize = computed(() => {
  // Use mock data if no real sessions exist
  if (recordingSessions.value.length === 0 && currentRepository.value) {
    return 2847692800; // Mock: ~2.65 GB
  }
  return recordingSessions.value.reduce((totalSize, session) => {
    return totalSize + session.files.reduce((sessionSize, file) => {
      return sessionSize + (file.size || 0);
    }, 0);
  }, 0);
});

const activeSessions = computed(() => {
  // Use mock data if no real sessions exist
  if (recordingSessions.value.length === 0 && currentRepository.value) {
    return 3; // Mock: 3 active sessions
  }
  return recordingSessions.value.filter(session =>
      session.status === RecordingStatus.ACTIVE
  ).length;
});

const jfrFiles = computed(() => {
  // Use mock data if no real sessions exist
  if (recordingSessions.value.length === 0 && currentRepository.value) {
    return 89; // Mock: 89 JFR files
  }
  return recordingSessions.value.reduce((count, session) => {
    return count + session.files.filter(file =>
        file.fileType === 'JFR'
    ).length;
  }, 0);
});

const heapDumpFiles = computed(() => {
  // Use mock data if no real sessions exist
  if (recordingSessions.value.length === 0 && currentRepository.value) {
    return 23; // Mock: 23 heap dump files
  }
  return recordingSessions.value.reduce((count, session) => {
    return count + session.files.filter(file =>
        file.fileType === 'HEAP_DUMP'
    ).length;
  }, 0);
});

const lastActivityTime = computed(() => {
  // Use mock data if no real sessions exist
  if (recordingSessions.value.length === 0 && currentRepository.value) {
    return '2h ago'; // Mock: 2 hours ago
  }

  if (recordingSessions.value.length === 0) return 'Never';

  // Find the most recent activity (creation or finish time)
  const allDates = recordingSessions.value.flatMap(session => [
    session.createdAt,
    ...session.files.map(file => file.createdAt).filter(Boolean),
  ]).filter(Boolean);

  if (allDates.length === 0) return 'Never';

  const mostRecent = new Date(Math.max(...allDates.map(date => new Date(date).getTime())));
  const now = new Date();
  const diffInHours = (now.getTime() - mostRecent.getTime()) / (1000 * 60 * 60);

  if (diffInHours < 1) return 'Now';
  if (diffInHours < 24) return `${Math.floor(diffInHours)}h ago`;
  if (diffInHours < 168) return `${Math.floor(diffInHours / 24)}d ago`;
  return `${Math.floor(diffInHours / 168)}w ago`;
});

const latestSessionTime = computed(() => {
  // Use mock data if no real sessions exist
  if (recordingSessions.value.length === 0 && currentRepository.value) {
    return '2h ago'; // Mock: 2 hours ago
  }
  if (recordingSessions.value.length === 0) return 'Never';

  // Find the most recent session by created date
  const mostRecentSession = recordingSessions.value.reduce((latest, session) => {
    return new Date(session.createdAt) > new Date(latest.createdAt) ? session : latest;
  });

  return FormattingService.formatRelativeTime(new Date(mostRecentSession.createdAt).getTime());
});

onMounted(() => {
  fetchRepositoryData();
  fetchProjectSettings();
  fetchProjectInfo();

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

// Sort recordings in a session: non-recording files first, then recording files, both sorted by creation date (newest first)
const getSortedRecordings = (session: RecordingSession) => {
  return [...session.files].sort((a, b) => {
    // First sort by file type (non-recording files first)
    if (a.isRecordingFile !== b.isRecordingFile) {
      return a.isRecordingFile ? 1 : -1; // Non-recording files first (false comes before true)
    }
    // Then sort by creation date (newest first) within each type
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
  });
};

// Track which sessions are expanded
const expandedSessions = ref<{ [key: string]: boolean }>({});

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

// Helper function to generate source status class including file type
const getSourceStatusClass = (source: RepositoryFile, sessionId: string) => {
  const classes = [];

  // Add selection class if selected
  if (selectedRepositoryFile.value[sessionId] && selectedRepositoryFile.value[sessionId][source.id]) {
    classes.push('source-selected');
  }

  // Add status class
  if (source.status === RecordingStatus.ACTIVE) classes.push('source-active');
  else if (source.status === RecordingStatus.FINISHED) classes.push('source-finished');
  else if (source.status === RecordingStatus.UNKNOWN) classes.push('source-unknown');
  else classes.push(`source-${String(source.status).toLowerCase()}`);

  // Add file type class
  if (source.isRecordingFile) {
    classes.push('recording-file');
  } else {
    // For non-recording files, distinguish between known and unknown types
    if (source.fileType === 'UNKNOWN') {
      classes.push('additional-file-unknown');
    } else {
      classes.push('additional-file-known');
    }
  }

  // Add temporary file class for ASPROF_TEMP files
  if (source.fileType === 'ASPROF_TEMP') {
    classes.push('temporary-file');
  }

  return classes.join(' ');
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

const getFileTypeVariant = (fileType: string): string => {
  switch (fileType) {
    case 'JFR':
      return 'primary';
    case 'HEAP_DUMP':
      return 'purple';
    case 'ASPROF_TEMP':
      return 'orange';
    case 'PERF_COUNTERS':
      return 'blue';
    case 'UNKNOWN':
    default:
      return 'grey';
  }
};

const fetchRepositoryData = async () => {
  isLoading.value = true;
  try {
    // Try to fetch recording sessions first to determine if repository exists
    recordingSessions.value = await repositoryService.listRecordingSessions();
    
    // If we got sessions, consider repository as linked
    currentRepository.value = { linked: true } as any;
    
    // Initialize the expanded state for sessions
    initializeExpandedState();
  } catch (error: any) {
    if (error.response && error.response.status === 404) {
      currentRepository.value = null;
      recordingSessions.value = [];
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
    projectInfo.value = await projectClient.info();
  } catch (error: any) {
    console.error('Failed to load project info:', error);
  }
};


const toggleSelectionMode = (sessionId: string) => {
  // Toggle the multi-select mode for this session
  showMultiSelectActions.value[sessionId] = !showMultiSelectActions.value[sessionId];

  // If turning off selection mode, clear all selections
  if (!showMultiSelectActions.value[sessionId]) {
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

  // Set all completed sources to the selected state
  session.files.forEach(source => {
    if (source.status !== RecordingStatus.ACTIVE) {
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

    await repositoryService.copyRecordingSession(session, true);
    toast.success('Merge & Copy', `Successfully merged and copied session ${session.id}`);

    // Refresh sessions list
    await fetchRepositoryData();

  } catch (error: any) {
    console.error("Error merging and copying session:", error);
    toast.error('Merge & Copy', error.message || 'Failed to merge and copy recording session');
  }
};

const copyAll = async (sessionId: string) => {
  try {
    const session = recordingSessions.value.find(s => s.id === sessionId);
    if (!session) {
      toast.error('Copy All', 'Session not found');
      return;
    }

    await repositoryService.copyRecordingSession(session, false);
    toast.success('Copy All', `Successfully copied session ${session.id}`);

    // Refresh sessions list
    await fetchRepositoryData();

  } catch (error: any) {
    console.error("Error copying session:", error);
    toast.error('Copy All', error.message || 'Failed to copy recording session');
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

    await repositoryService.copySelectedRepositoryFile(session.id, selectedSources, merge);
    toast.success(
        merge ? 'Merge & Copy' : 'Copy Selected',
        `Successfully ${merge ? 'merged and copied' : 'copied'} ${selectedSources.length} recording(s)`
    );

    // Refresh sessions list
    await fetchRepositoryData();

    // Clear selections after download
    toggleSelectAllSources(sessionId, false);

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

// Get visible files for a session (limited by visibleFilesCount)
const getVisibleRecordings = (session: RecordingSession) => {
  const sortedFiles = getSortedRecordings(session);
  const limit = visibleFilesCount.value[session.id] || DEFAULT_FILES_LIMIT;
  return sortedFiles.slice(0, limit);
};

// Check if session has more files than currently visible
const hasMoreFiles = (session: RecordingSession): boolean => {
  const limit = visibleFilesCount.value[session.id] || DEFAULT_FILES_LIMIT;
  return session.files.length > limit;
};

// Show more files for a session
const showMoreFiles = (sessionId: string) => {
  const session = recordingSessions.value.find(s => s.id === sessionId);
  if (!session) return;

  // Show all files
  visibleFilesCount.value[sessionId] = session.files.length;
};

// Check if checkbox should be disabled for a repository file
const isCheckboxDisabled = (source: RepositoryFile): boolean => {
  return source.status === RecordingStatus.ACTIVE || source.fileType === RecordingFileType.ASPROF;
};
</script>

<template>
  <!-- Repository Disabled State for Local Workspace -->
  <RepositoryDisabledAlert v-if="isLocalWorkspace"/>

  <div v-else class="row g-4">
    <!-- Page Header -->
    <div class="col-12">
      <div class="d-flex align-items-center mb-2">
        <i class="bi bi-database fs-4 me-2 text-primary"></i>
        <h3 class="mb-0">Remote Repository</h3>
      </div>
    </div>

    <!-- Repository Statistics Cards -->
    <div class="col-12" v-if="!isLoading && currentRepository">
      <div class="repository-stats-compact mb-4">
        <div class="stats-compact-content">
          <div class="row g-3">
            <!-- Sessions Overview -->
            <div class="col-md-4">
              <div class="compact-stat-card">
                <div class="compact-stat-header">
                  <i class="bi bi-collection text-primary"></i>
                  <span class="compact-stat-title">Sessions</span>
                </div>
                <div class="compact-stat-metrics">
                  <div class="metric-item">
                    <span class="metric-label">Total</span>
                    <span class="metric-value">{{ totalSessions }}</span>
                  </div>
                  <div class="metric-item">
                    <span class="metric-label">Active</span>
                    <span class="metric-value text-warning">{{ activeSessions }}</span>
                  </div>
                  <div class="metric-item">
                    <span class="metric-label">Last Activity</span>
                    <span class="metric-value">{{ lastActivityTime }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Storage Overview -->
            <div class="col-md-4">
              <div class="compact-stat-card">
                <div class="compact-stat-header">
                  <i class="bi bi-hdd text-success"></i>
                  <span class="compact-stat-title">Storage</span>
                </div>
                <div class="compact-stat-metrics">
                  <div class="metric-item">
                    <span class="metric-label">Total Size</span>
                    <span class="metric-value">{{ FormattingService.formatBytes(totalRepositorySize) }}</span>
                  </div>
                  <div class="metric-item">
                    <span class="metric-label">Total Files</span>
                    <span class="metric-value">{{ totalFiles }}</span>
                  </div>
                  <div class="metric-item">
                    <span class="metric-label">Latest Session</span>
                    <span class="metric-value">{{ latestSessionTime }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- File Types -->
            <div class="col-md-4">
              <div class="compact-stat-card">
                <div class="compact-stat-header">
                  <i class="bi bi-files text-info"></i>
                  <span class="compact-stat-title">File Types</span>
                </div>
                <div class="compact-stat-metrics">
                  <div class="metric-item">
                    <span class="metric-label">JFR Files</span>
                    <span class="metric-value text-primary">{{ jfrFiles }}</span>
                  </div>
                  <div class="metric-item">
                    <span class="metric-label">Heap Dumps</span>
                    <span class="metric-value text-danger">{{ heapDumpFiles }}</span>
                  </div>
                  <div class="metric-item">
                    <span class="metric-label">Other Files</span>
                    <span class="metric-value">{{ totalFiles - jfrFiles - heapDumpFiles }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Recording Sessions Header -->
    <div class="col-12" v-if="recordingSessions.length > 0">
      <div class="card shadow-sm border-0 mb-4">
        <div class="card-header bg-light d-flex align-items-center py-3">
          <i class="bi bi-collection fs-4 me-2 text-primary"></i>
          <h5 class="mb-0">Recording Sessions</h5>
          <Badge :value="`${recordingSessions.length} session${recordingSessions.length !== 1 ? 's' : ''}`"
                 variant="primary" size="xs" class="ms-2"/>
        </div>

        <div class="card-body">
          <!-- Card-Based Sessions Layout -->
          <div>
            <!-- Sessions list -->
            <div v-for="session in sortedSessions" :key="session.id" class="mb-3">
              <!-- Session header -->
              <div class="folder-row p-3 rounded"
                   :class="getSessionStatusClass(session)"
                   @click="toggleSession(session.id)">
                <div class="d-flex justify-content-between align-items-center">
                  <div class="d-flex align-items-center">
                    <i class="bi fs-5 me-3 text-primary"
                       :class="expandedSessions[session.id] ? 'bi-folder2-open' : 'bi-folder2'"></i>
                    <div class="toggle-arrow-container me-2">
                      <i class="bi"
                         :class="expandedSessions[session.id] ? 'bi-chevron-down' : 'bi-chevron-right'"></i>
                    </div>
                    <div>
                      <div class="fw-bold">
                        {{ session.id }}
                        <Badge :value="`${getSourcesCount(session)} sources`" variant="primary" size="xs" class="ms-2"/>
                        <Badge :value="Utils.capitalize(session.status.toLowerCase())"
                               :variant="getStatusVariant(session.status)" size="xs" class="ms-1"/>
                        <Badge :value="`${formatDate(session.createdAt)}`"
                               variant="grey" size="xs" class="ms-1"/>
                      </div>
                    </div>
                  </div>
                  <div class="d-flex align-items-center gap-2">
                    <div v-if="showActions[session.id]"
                         class="d-flex align-items-center gap-2 action-buttons-container">
                      <button
                          class="btn btn-sm btn-outline-primary"
                          type="button"
                          title="Merge and Copy Recordings"
                          @click.stop="copyAndMerge(session.id)">
                        <i class="bi bi-folder-symlink me-1"></i>Merge &amp; Copy
                      </button>
                      <button
                          class="btn btn-sm btn-outline-primary"
                          type="button"
                          title="Copy All Recordings"
                          @click.stop="copyAll(session.id)">
                        <i class="bi bi-files me-1"></i>Copy All
                      </button>
                      <button
                          class="btn btn-sm btn-outline-danger"
                          type="button"
                          title="Delete All Recordings"
                          @click.stop="deleteAll(session.id)">
                        <i class="bi bi-trash me-1"></i>Delete All
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
                        class="action-btn action-menu-btn ms-2"
                        :class="{'active': showMultiSelectActions[session.id]}"
                        type="button"
                        title="Toggle multi-select mode"
                        @click.stop="toggleSelectionMode(session.id)">
                      <i class="bi"
                         :class="{'bi-check2-square': showMultiSelectActions[session.id], 'bi-square': !showMultiSelectActions[session.id]}"></i>
                    </button>
                  </div>
                </div>
              </div>

              <!-- Session recordings (shown when expanded) -->
              <div v-if="expandedSessions[session.id]" class="ps-4 pt-2">
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
                        class="btn btn-sm btn-outline-primary"
                        @click.stop="downloadSelectedSources(session.id, false)"
                        :disabled="getSelectedCount(session.id) === 0"
                        title="Copy selected recordings">
                      <i class="bi bi-files me-1"></i>Copy Selected
                    </button>
                    <button
                        class="btn btn-sm btn-outline-danger"
                        @click.stop="deleteSelectedSources(session.id)"
                        :disabled="getSelectedCount(session.id) === 0"
                        title="Delete selected recordings">
                      <i class="bi bi-trash me-1"></i>Delete Selected
                    </button>
                  </div>
                </div>

                <!-- Sources list -->
                <div v-for="source in getVisibleRecordings(session)"
                     :key="source.id"
                     class="child-row p-2 mb-2 rounded"
                     :class="getSourceStatusClass(source, session.id)">
                  <div class="d-flex justify-content-between align-items-center">
                    <div class="d-flex align-items-center">
                      <div class="form-check file-form-check me-2" v-if="showMultiSelectActions[session.id]">
                        <input
                            class="form-check-input file-checkbox"
                            type="checkbox"
                            :id="'source-' + source.id"
                            :disabled="isCheckboxDisabled(source)"
                            :checked="selectedRepositoryFile[session.id] && selectedRepositoryFile[session.id][source.id]"
                            @change="() => toggleSourceSelection(session.id, source.id)"
                            @click.stop>
                      </div>
                      <!-- Different icon based on file type -->
                      <div class="recording-file-icon-medium me-3">
                        <i class="bi" :class="{
                          'bi-file-earmark-code': source.fileType === 'JFR',
                          'bi-file-earmark-binary': source.fileType === 'HEAP_DUMP',
                          'bi-hourglass-split': source.fileType === 'ASPROF_TEMP',
                          'bi-file-earmark-bar-graph': source.fileType === 'PERF_COUNTERS',
                          'bi-file-earmark': source.fileType === 'UNKNOWN'
                        }"></i>
                      </div>
                      <div>
                        <div class="fw-bold">
                          {{ source.name }}
                          <Badge :value="Utils.formatFileType(source.fileType)"
                                 :variant="getFileTypeVariant(source.fileType)" size="xs" class="ms-2"/>
                          <Badge v-if="source.status === RecordingStatus.ACTIVE"
                                 :value="Utils.capitalize(source.status.toLowerCase())" variant="warning" size="xs"
                                 class="ms-1"/>
                          <Badge v-if="source.status === RecordingStatus.UNKNOWN"
                                 :value="Utils.capitalize(source.status.toLowerCase())" variant="purple" size="xs"
                                 class="ms-1"/>
                          <Badge v-if="source.isFinishingFile" value="Finisher" variant="green" size="xs" class="ms-1"
                                 title="This file indicates the session is finished"/>
                          <Badge :value="FormattingService.formatBytes(source.size)" variant="grey" size="xs"
                                 class="ms-1" :uppercase="false"/>
                          <Badge :value="`${formatDate(source.createdAt)}`"
                                 variant="grey" size="xs" class="ms-1"/>
                        </div>
                      </div>
                    </div>
                    <div>
                      <!-- Download button removed as requested -->
                    </div>
                  </div>
                </div>

                <!-- Show More button -->
                <div v-if="hasMoreFiles(session)" class="text-center mt-1">
                  <button
                      class="btn btn-sm btn-outline-primary show-more-btn"
                      @click.stop="showMoreFiles(session.id)"
                      title="Show all files">
                    <i class="bi bi-chevron-down me-1"></i>Show
                    {{ session.files.length - (visibleFilesCount[session.id] || DEFAULT_FILES_LIMIT) }} more files
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Loading Sessions Placeholder -->
    <div class="col-12" v-if="isLoading && !recordingSessions.length">
      <div class="card shadow-sm border-0 mb-4">
        <div class="card-header bg-light d-flex align-items-center py-3">
          <i class="bi bi-collection fs-4 me-2 text-primary"></i>
          <h5 class="mb-0">Recording Sessions</h5>
        </div>

        <div class="card-body">
          <div class="modern-empty-state loading">
            <div class="spinner-border text-primary" role="status">
              <span class="visually-hidden">Loading...</span>
            </div>
            <p class="mt-3">Loading recording sessions...</p>
          </div>
        </div>
      </div>
    </div>

    <!-- No Sessions Message -->
    <div class="col-12" v-if="!isLoading && !recordingSessions.length && currentRepository">
      <div class="card shadow-sm border-0 mb-4">
        <div class="card-header bg-light d-flex align-items-center py-3">
          <i class="bi bi-collection fs-4 me-2 text-primary"></i>
          <h5 class="mb-0">Recording Sessions</h5>
        </div>

        <div class="card-body">
          <div class="modern-empty-state">
            <i class="bi bi-folder-x display-4 text-muted"></i>
            <h5 class="mt-3">No Recording Sessions Available</h5>
            <p class="text-muted">There are no recording sessions available for this repository.</p>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- Loading Placeholder -->
  <div class="container-fluid p-4" v-if="isLoading && !currentRepository">
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

.child-row {
  background-color: white;
  border: 1px solid #eee;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  transition: all 0.2s ease;
}

.child-row:hover {
  background-color: rgba(247, 248, 252, 0.8);
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.child-row.source-active {
  background-color: rgba(255, 193, 7, 0.1);
  border-left: 3px solid #ffc107;
}

.child-row.source-finished {
  background-color: rgba(108, 117, 125, 0.05);
  border-left: 3px solid #6c757d;
}

.child-row.source-unknown {
  background-color: rgba(111, 66, 193, 0.05);
  border-left: 3px solid #6f42c1;
}

.child-row.source-selected {
  background-color: rgba(94, 100, 255, 0.08);
  border-left: 3px solid #5e64ff;
  box-shadow: 0 2px 4px rgba(94, 100, 255, 0.15);
}

/* Styling for recording file rows vs non-recording file rows */
.child-row.recording-file {
  background-color: #f7faff;
  border-left: 3px solid #5e64ff;
  box-shadow: 0 1px 3px rgba(94, 100, 255, 0.15);
}

/* Non-recording files with known file type */
.child-row.additional-file-known {
  background-color: #f0f9ff; /* Light blue background */
  border-left: 3px solid #0ea5e9; /* Sky blue border */
  box-shadow: 0 1px 3px rgba(14, 165, 233, 0.15);
}

/* Non-recording files with unknown file type */
.child-row.additional-file-unknown {
  background-color: #f8f9fa; /* Light gray background */
  border-left: 3px solid #adb5bd; /* Gray border */
  box-shadow: 0 1px 3px rgba(108, 117, 125, 0.1);
}

/* Hover states for different file types */
.child-row.recording-file:hover {
  background-color: #eef2ff;
  box-shadow: 0 2px 5px rgba(94, 100, 255, 0.2);
}

.child-row.additional-file-known:hover {
  background-color: #e0f2fe; /* Lighter sky blue on hover */
  box-shadow: 0 2px 5px rgba(14, 165, 233, 0.2);
}

.child-row.additional-file-unknown:hover {
  background-color: #f1f3f5;
  box-shadow: 0 2px 5px rgba(108, 117, 125, 0.15);
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


/* File icon styling for different file types */
.recording-file-icon-medium {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 5px;
  font-size: 1rem;
}

/* Recording file icons - blue theme */
.child-row.recording-file .recording-file-icon-medium {
  background-color: rgba(94, 100, 255, 0.1);
  color: #5e64ff;
}

/* Known non-recording file icons - sky blue theme */
.child-row.additional-file-known .recording-file-icon-medium {
  background-color: rgba(14, 165, 233, 0.1);
  color: #0ea5e9;
}

/* Unknown non-recording file icons - gray theme */
.child-row.additional-file-unknown .recording-file-icon-medium {
  background-color: rgba(108, 117, 125, 0.1);
  color: #6c757d;
}

/* Temporary file styling */
.child-row.temporary-file {
  background-color: rgba(255, 142, 51, 0.08) !important;
  border-left: 3px solid #ff8e33 !important;
  border-top: 1px dashed #ff8e33 !important;
  border-right: 1px dashed #ff8e33 !important;
  border-bottom: 1px dashed #ff8e33 !important;
  box-shadow: 0 1px 3px rgba(255, 142, 51, 0.15) !important;
}

.child-row.temporary-file:hover {
  background-color: rgba(255, 142, 51, 0.12) !important;
  box-shadow: 0 2px 5px rgba(255, 142, 51, 0.2) !important;
}

/* Temporary file icon styling - orange theme */
.child-row.temporary-file .recording-file-icon-medium {
  background-color: rgba(255, 142, 51, 0.15) !important;
  color: #ff8e33 !important;
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

.toggle-arrow-container {
  width: 24px;
  height: 24px;
  font-size: 1rem;
  color: #5e64ff;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.toggle-arrow-container:hover {
  transform: translateY(-1px);
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

/* Compact Repository Statistics Cards Styling */
.repository-stats-compact {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04),
  0 1px 3px rgba(0, 0, 0, 0.02);
  backdrop-filter: blur(10px);
}

.stats-compact-content {
  padding: 16px 20px;
}

.compact-stat-card {
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 8px;
  padding: 12px 16px;
  height: 100%;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04),
  0 1px 2px rgba(0, 0, 0, 0.02);
}

.compact-stat-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 3px 8px rgba(0, 0, 0, 0.06),
  0 2px 4px rgba(94, 100, 255, 0.1);
  border-color: rgba(94, 100, 255, 0.15);
}

.compact-stat-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.compact-stat-header i {
  font-size: 1rem;
}

.compact-stat-title {
  font-size: 0.8rem;
  font-weight: 600;
  color: #374151;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.compact-stat-metrics {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metric-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 2px 0;
}

.metric-label {
  font-size: 0.75rem;
  color: #6b7280;
  font-weight: 500;
}

.metric-value {
  font-size: 0.8rem;
  font-weight: 600;
  color: #374151;
  text-align: right;
}

/* Responsive adjustments for smaller screens */
@media (max-width: 768px) {
  .stats-compact-content {
    padding: 12px 16px;
  }

  .compact-stat-card {
    padding: 10px 12px;
  }

  .compact-stat-header {
    gap: 6px;
    margin-bottom: 6px;
  }

  .compact-stat-title {
    font-size: 0.75rem;
  }

  .metric-label {
    font-size: 0.7rem;
  }

  .metric-value {
    font-size: 0.75rem;
  }
}

@media (max-width: 576px) {
  .repository-stats-compact {
    border-radius: 8px;
  }

  .stats-compact-content {
    padding: 10px 12px;
  }

  .compact-stat-card {
    padding: 8px 10px;
  }

  .compact-stat-metrics {
    gap: 3px;
  }

  .metric-item {
    padding: 1px 0;
  }
}

</style>
