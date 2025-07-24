<script setup lang="ts">
import {computed, nextTick, onMounted, ref} from 'vue';
import {useRoute} from 'vue-router'
import ProjectRepositoryClient from "@/services/project/ProjectRepositoryClient.ts";
import Utils from "@/services/Utils";
import ProjectSettingsClient from "@/services/project/ProjectSettingsClient.ts";
import RepositoryInfo from "@/services/project/model/RepositoryInfo.ts";
import SettingsResponse from "@/services/project/model/SettingsResponse.ts";
import {ToastService} from "@/services/ToastService";
import MessageBus from "@/services/MessageBus";
import RecordingSession from "@/services/model/data/RecordingSession.ts";
import RecordingStatus from "@/services/model/data/RecordingStatus.ts";
import * as bootstrap from 'bootstrap';
import RepositoryFile from "@/services/model/data/RepositoryFile.ts";
import ConfirmationDialog from '@/components/ConfirmationDialog.vue';
import Badge from '@/components/Badge.vue';

// Using formatFileType from Utils class

const route = useRoute()
const toast = ToastService;

const currentProject = ref<SettingsResponse | null>();
const currentRepository = ref<RepositoryInfo | null>();
const isLoading = ref(false);
const recordingSessions = ref<RecordingSession[]>([]);
const isLoadingSessions = ref(false);
const uploadPanelExpanded = ref(true);
const selectedRepositoryFile = ref<{ [sessionId: string]: { [sourceId: string]: boolean } }>({});
const showMultiSelectActions = ref<{ [sessionId: string]: boolean }>({});
const showActions = ref<{ [sessionId: string]: boolean }>({});

const repositoryService = new ProjectRepositoryClient(route.params.projectId as string)
const settingsService = new ProjectSettingsClient(route.params.projectId as string)

const inputCreateDirectoryCheckbox = ref(true);
const inputRepositoryPath = ref('')
const inputRepositoryType = ref('ASYNC_PROFILER')
const inputFinishedSessionDetection = ref(true);
const inputFinishedSessionFile = ref('perfcounters.hsperfdata')

// State for delete session confirmation modal
const deleteSessionDialog = ref(false);
const sessionToDelete = ref<RecordingSession | null>(null);
const deletingSession = ref(false);

// State for delete selected files confirmation modal
const deleteSelectedFilesDialog = ref(false);
const sessionIdWithFilesToDelete = ref('');
const deletingSelectedFiles = ref(false);

onMounted(() => {
  fetchRepositoryData();
  fetchProjectSettings();
  
  // Initialize tooltips after the DOM is loaded
  nextTick(() => {
    const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
    tooltipTriggerList.map(function (tooltipTriggerEl) {
      return new bootstrap.Tooltip(tooltipTriggerEl);
    });
  });
});

// Function to fetch recording sessions
const fetchRecordingSessions = async () => {
  // Only fetch if repository is linked
  if (!currentRepository.value) return;

  isLoadingSessions.value = true;
  try {
    // Call the real API
    recordingSessions.value = await repositoryService.listRecordingSessions();

    // Initialize the expanded state for sessions
    initializeExpandedState();
  } catch (error: any) {
    console.error("Error fetching sessions:", error);
    toast.error('Failed to load recording sessions', error.message);
  } finally {
    isLoadingSessions.value = false;
  }
};

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

// Format file size to human-readable format
const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 Bytes';

  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  return parseFloat((bytes / Math.pow(1024, i)).toFixed(2)) + ' ' + sizes[i];
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

// Initialize expanded state for sessions - ACTIVE sessions are expanded by default
const initializeExpandedState = () => {
  recordingSessions.value.forEach(session => {
    expandedSessions.value[session.id] = session.status === RecordingStatus.ACTIVE;
    
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
  
  return classes.join(' ');
};

const getStatusVariant = (status: RecordingStatus): string => {
  switch (status) {
    case RecordingStatus.ACTIVE:
      return 'warning';
    case RecordingStatus.FINISHED:
      return 'success';
    case RecordingStatus.UNKNOWN:
    default:
      return 'purple';
  }
};

const getFileTypeVariant = (fileType: string): string => {
  switch (fileType) {
    case 'JFR':
      return 'info';
    case 'HEAP_DUMP':
      return 'purple';
    case 'PERF_COUNTERS':
      return 'green';
    case 'UNKNOWN':
    default:
      return 'grey';
  }
};

const fetchRepositoryData = async () => {
  isLoading.value = true;
  try {
    currentRepository.value = await repositoryService.get();
    // Set the repository panel to collapsed by default when repository is linked
    uploadPanelExpanded.value = false;

    // Once we have a repository, fetch the recording sessions
    await fetchRecordingSessions();
  } catch (error: any) {
    if (error.response && error.response.status === 404) {
      currentRepository.value = null;
      // Keep panel expanded by default when no repository is linked
      uploadPanelExpanded.value = true;
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

const updateRepositoryLink = async () => {
  if (!Utils.isNotBlank(inputRepositoryPath.value)) {
    toast.error('Repository Link', 'Repository path is required');
    return;
  }

  isLoading.value = true;

  try {
    await repositoryService.create(
        inputRepositoryPath.value,
        inputRepositoryType.value,
        inputCreateDirectoryCheckbox.value,
        inputFinishedSessionDetection.value ? inputFinishedSessionFile.value : null
    );

    await fetchRepositoryData();
    // Repository data should now be loaded, so sessions should be fetched,
    // but let's explicitly fetch sessions here just to be sure
    if (currentRepository.value) {
      // Set the repository panel to collapsed by default when repository is linked
      uploadPanelExpanded.value = false;
      toast.success('Repository Link', 'Repository link has been updated');

      // Fetch recording sessions for the new repository
      await fetchRecordingSessions();
    }

    // Emit repository status change event
    MessageBus.emit(MessageBus.REPOSITORY_STATUS_CHANGED, true);

    // Reset form
    inputRepositoryPath.value = '';
    inputCreateDirectoryCheckbox.value = true;
  } catch (error: any) {
    toast.error('Cannot link a Repository', error.response?.data || error.message);
  } finally {
    isLoading.value = false;
  }
};

const unlinkRepository = async () => {
  if (!confirm('Are you sure you want to unlink this repository?')) {
    return;
  }

  isLoading.value = true;

  try {
    await repositoryService.delete();
    currentRepository.value = null;
    
    // Clear recording sessions immediately
    recordingSessions.value = [];
    
    // Set the repository panel to expanded when repository is unlinked
    uploadPanelExpanded.value = true;
    
    toast.success('Repository Link', 'Repository has been unlinked');

    // Emit repository status change event
    MessageBus.emit(MessageBus.REPOSITORY_STATUS_CHANGED, false);
  } catch (error: any) {
    toast.error('Failed to unlink repository', error.message);
  } finally {
    isLoading.value = false;
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
    await fetchRecordingSessions();
    
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
    await fetchRecordingSessions();
    
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
    await fetchRecordingSessions();
    
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
    await fetchRecordingSessions();
    
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
    await fetchRecordingSessions();
    
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
</script>

<template>
  <div class="row g-4">
    <!-- Page Header -->
    <div class="col-12">
      <div class="d-flex align-items-center mb-3">
        <i class="bi bi-database fs-4 me-2 text-primary"></i>
        <h3 class="mb-0">Remote Repository</h3>
      </div>
      <p class="text-muted mb-2">
        Link a directory to become a Remote Repository for this project. The repository is a place with automatically generated
        Raw Recordings (we can merge/download Raw Recordings to Jeffrey to become regular recordings, ready for profile processing and initialization).
        <br/>
        <span class="fst-italic">Jobs can work with these Raw Recordings, e.g. automatically generate profiles.</span>.
      </p>
    </div>

    <!-- Repository Card - Unified for both linked and unlinked states -->
    <div class="col-12" v-if="!isLoading">
      <div class="card shadow-sm border-0">
        <div class="card-header bg-light d-flex justify-content-between align-items-center cursor-pointer py-3"
             @click="uploadPanelExpanded = !uploadPanelExpanded">
          <div class="d-flex align-items-center">
            <i class="bi bi-link-45deg fs-4 me-2 text-primary"></i>
            <h5 class="card-title mb-0">Link a Repository</h5>
            <Badge v-if="currentRepository" value="Linked" variant="success" size="sm" class="ms-2" />
          </div>
          <div class="d-flex align-items-center">
            <button class="btn btn-sm btn-outline-primary" @click.stop="uploadPanelExpanded = !uploadPanelExpanded">
              <i class="bi" :class="uploadPanelExpanded ? 'bi-chevron-up' : 'bi-chevron-down'"></i>
            </button>
          </div>
        </div>

        <div class="card-body" v-if="uploadPanelExpanded">
          <!-- Repository information when linked -->
          <div v-if="currentRepository">

            <div class="table-responsive">
              <table class="table table-hover">
                <tbody>
                <tr>
                  <td class="fw-medium" style="width: 25%">Repository Path</td>
                  <td style="width: 75%">
                    <div class="d-flex align-items-center flex-wrap">
                      <code class="me-2 d-inline-block text-break">{{ currentRepository.repositoryPath }}</code>
                      <Badge v-if="currentRepository.directoryExists" value="Directory Exists" variant="success" size="sm" class="ms-2" />
                      <Badge v-else value="Directory Does Not Exist" variant="danger" size="sm" class="ms-2" />
                    </div>
                  </td>
                </tr>
                <tr>
                  <td class="fw-medium" style="width: 25%">Repository Type</td>
                  <td style="width: 75%">
                    <Badge :value="currentRepository.repositoryType === 'ASYNC_PROFILER' ? 'Async-Profiler' : currentRepository.repositoryType" :variant="currentRepository.repositoryType === 'JDK' ? 'info' : 'purple'" size="sm" class="ms-2" />
                  </td>
                </tr>
                <tr>
                  <td class="fw-medium" style="width: 25%">
                    Finished-session detection
                  </td>
                  <td style="width: 75%">
                    <code v-if="currentRepository.finishedSessionDetectionFile">{{ currentRepository.finishedSessionDetectionFile }}</code>
                    <span class="text-muted fst-italic" v-else>Detection is not configured</span>
                  </td>
                </tr>
                </tbody>
              </table>
            </div>

            <div class="d-flex justify-content-end mt-4">
              <button
                  class="btn btn-outline-danger"
                  @click="unlinkRepository"
                  :disabled="isLoading"
              >
                <i class="bi bi-link-break me-2"></i>Unlink Repository
                <span class="spinner-border spinner-border-sm ms-2" v-if="isLoading"></span>
              </button>
            </div>
          </div>
          
          <!-- Repository link form when not linked -->
          <div v-else>
            <div class="info-panel mb-4">
              <div class="info-panel-icon">
                <i class="bi bi-info-circle-fill"></i>
              </div>
              <div class="info-panel-content">
                <h6 class="fw-bold mb-1">Link Repository</h6>
                <p class="mb-0">
                  Link a directory with the latest recordings on the host, e.g. <code>/home/my-account/recordings</code>
                </p>
              </div>
            </div>

          <form @submit.prevent="updateRepositoryLink">
            <div class="table-responsive">
              <table class="table table-hover">
                <tbody>
                <tr>
                  <td class="fw-medium" style="width: 25%">
                    Repository Path <span class="text-danger">*</span>
                  </td>
                  <td style="width: 75%">
                    <div class="input-group search-container">
                      <span class="input-group-text"><i class="bi bi-folder2"></i></span>
                      <input
                          type="text"
                          class="form-control search-input"
                          id="repositoryPath"
                          v-model="inputRepositoryPath"
                          placeholder="Enter the path to the repository directory"
                          required
                      >
                    </div>
                  </td>
                </tr>
                <tr>
                  <td class="fw-medium">Repository Type</td>
                  <td>
                    <div class="d-flex flex-wrap gap-4 mt-2">
                      <div class="form-check">
                        <input
                            class="form-check-input"
                            type="radio"
                            id="asyncProfiler"
                            value="ASYNC_PROFILER"
                            v-model="inputRepositoryType"
                        >
                        <label class="form-check-label" for="asyncProfiler">
                          <Badge value="Async-Profiler" variant="purple" size="sm" />
                        </label>
                      </div>
                      <div class="form-check opacity-50">
                        <input
                            class="form-check-input"
                            type="radio"
                            id="jdk"
                            value="JDK"
                            v-model="inputRepositoryType"
                            disabled
                        >
                        <label class="form-check-label" for="jdk">
                          <Badge value="JDK" variant="info" size="sm" />
                          <Badge value="Coming soon" variant="secondary" size="sm" class="ms-1" />
                        </label>
                      </div>
                    </div>
                  </td>
                </tr>
                <tr>
                  <td class="fw-medium" style="width: 25%">Create directory if it doesn't exist</td>
                  <td style="width: 75%">
                    <div class="form-check form-switch mt-2">
                      <input 
                          class="form-check-input" 
                          type="checkbox" 
                          id="createDirectory" 
                          v-model="inputCreateDirectoryCheckbox"
                      >
                      <label class="form-check-label" for="createDirectory"></label>
                    </div>
                  </td>
                </tr>
                <tr>
                  <td class="fw-medium" style="width: 25%; vertical-align: top; padding-top: 1rem;">
                    Finished-session detection
                  </td>
                  <td style="width: 75%">
                    <div class="d-flex">
                      <div class="d-flex flex-column flex-grow-1">
                        <div class="d-flex align-items-center mb-2">
                          <div class="form-check form-switch me-2">
                            <input 
                                class="form-check-input" 
                                type="checkbox" 
                                id="finishedSessionIndication" 
                                v-model="inputFinishedSessionDetection"
                            >
                            <label class="form-check-label" for="finishedSessionIndication"></label>
                          </div>
                        </div>
                        <div class="input-group search-container" :class="{'disabled-input': !inputFinishedSessionDetection}">
                          <span class="input-group-text"><i class="bi bi-check-circle-fill"></i></span>
                          <input
                              type="text"
                              class="form-control search-input"
                              id="finishedSessionFile"
                              v-model="inputFinishedSessionFile"
                              placeholder="File name that indicates a completed session"
                              :disabled="!inputFinishedSessionDetection"
                          >
                        </div>
                        <div class="form-text small mt-1" :class="{'text-muted-disabled': !inputFinishedSessionDetection}">
                          It's recommended to use JVM Performance Counters file that is dumped when JVM exits. 
                          Use an option <code style="font-size: 0.8rem;">-XX:PerfDataSaveFile=&lt;project&gt;/&lt;recording-session&gt;/perfcounters.hsprof</code>.
                          Presence of the file in the Recording Session folder means that the recording is no longer active.
                          A content of the PerfCounter file can be used to enhance a generated Profile later.
                        </div>
                      </div>
                    </div>
                  </td>
                </tr>
                </tbody>
              </table>
            </div>

            <div class="d-flex justify-content-end mt-4">
              <button
                  type="submit"
                  class="btn btn-primary"
                  :disabled="isLoading"
              >
                Link Repository
                <span class="spinner-border spinner-border-sm ms-2" v-if="isLoading"></span>
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>

    <!-- Recording Sessions Header -->
    <div class="col-12" v-if="recordingSessions.length > 0">
      <div class="card shadow-sm border-0 mb-4">
        <div class="card-header bg-light d-flex align-items-center py-3">
          <i class="bi bi-collection fs-4 me-2 text-primary"></i>
          <h5 class="mb-0">Recording Sessions</h5>
          <Badge :value="`${recordingSessions.length} session${recordingSessions.length !== 1 ? 's' : ''}`" variant="primary" size="sm" class="ms-2" />
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
                        <Badge :value="`${getSourcesCount(session)} sources`" variant="primary" size="sm" class="ms-2" />
                        <Badge :value="Utils.capitalize(session.status.toLowerCase())" :variant="getStatusVariant(session.status)" size="sm" class="ms-1" />
                      </div>
                      <div class="text-muted small mt-1 d-flex align-items-center">
                        <i class="bi bi-clock-history me-1"></i>{{ formatDate(session.createdAt) }} <i class="bi bi-dash mx-1"></i> {{ formatDate(session.finishedAt) }}
                      </div>
                    </div>
                  </div>
                  <div class="d-flex align-items-center gap-2">
                    <div v-if="showActions[session.id]" class="d-flex align-items-center gap-2 action-buttons-container">
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
                      <i class="bi" :class="{'bi-check2-square': showMultiSelectActions[session.id], 'bi-square': !showMultiSelectActions[session.id]}"></i>
                    </button>
                  </div>
                </div>
              </div>

              <!-- Session recordings (shown when expanded) -->
              <div v-if="expandedSessions[session.id]" class="ps-4 pt-2">
                <!-- Multi-select controls -->
                <div v-if="showMultiSelectActions[session.id]" class="multi-select-controls d-flex justify-content-between align-items-center mb-2">
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
                    <Badge v-if="getSelectedCount(session.id) > 0" :value="`${getSelectedCount(session.id)} selected`" variant="secondary" size="sm" class="me-2" />
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
                <div v-for="source in getSortedRecordings(session)" 
                     :key="source.id" 
                     class="child-row p-3 mb-2 rounded"
                     :class="getSourceStatusClass(source, session.id)">
                  <div class="d-flex justify-content-between align-items-center">
                    <div class="d-flex align-items-center">
                      <div class="form-check file-form-check me-2" v-if="showMultiSelectActions[session.id]">
                        <input 
                            class="form-check-input file-checkbox" 
                            type="checkbox" 
                            :id="'source-' + source.id"
                            :disabled="source.status === RecordingStatus.ACTIVE"
                            :checked="selectedRepositoryFile[session.id] && selectedRepositoryFile[session.id][source.id]"
                            @change="() => toggleSourceSelection(session.id, source.id)"
                            @click.stop>
                      </div>
                      <!-- Different icon based on file type -->
                      <div class="recording-file-icon-medium me-3">
                        <i class="bi" :class="{
                          'bi-file-earmark-code': source.fileType === 'JFR',
                          'bi-file-earmark-binary': source.fileType === 'HEAP_DUMP',
                          'bi-file-earmark-bar-graph': source.fileType === 'PERF_COUNTERS',
                          'bi-file-earmark': source.fileType === 'UNKNOWN'
                        }"></i>
                      </div>
                      <div>
                        <div class="fw-bold">
                          {{ source.name }}
                          <Badge :value="formatFileSize(source.size)" variant="grey" size="xs" class="ms-2" />
                          <!-- File type badge -->
                          <Badge :value="Utils.formatFileType(source.fileType)" :variant="getFileTypeVariant(source.fileType)" size="xs" class="ms-1" />
                          <Badge v-if="source.status === RecordingStatus.ACTIVE" :value="Utils.capitalize(source.status.toLowerCase())" variant="warning" size="xs" class="ms-1" />
                          <Badge v-if="source.status === RecordingStatus.UNKNOWN" :value="Utils.capitalize(source.status.toLowerCase())" variant="purple" size="xs" class="ms-1" />
                          <Badge v-if="source.isFinishingFile" value="Finisher" variant="success" size="xs" class="ms-1" title="This file indicates the session is finished" />
                        </div>
                        <div class="text-muted small mt-1 d-flex align-items-center">
                          <i class="bi bi-clock-history me-1"></i>{{ formatDate(source.createdAt) }} <i class="bi bi-dash mx-1"></i> {{ formatDate(source.finishedAt) }}
                        </div>
                      </div>
                    </div>
                    <div>
                      <!-- Download button removed as requested -->
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Loading Sessions Placeholder -->
    <div class="col-12" v-if="isLoadingSessions && !recordingSessions.length">
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
    <div class="col-12" v-if="!isLoadingSessions && !recordingSessions.length">
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
  </div>
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
  margin-top: 0.15em;
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

.info-panel {
  display: flex;
  background-color: #f8f9fa;
  border-radius: 6px;
  overflow: hidden;
  border-left: 4px solid #5e64ff;
}

.info-panel-icon {
  flex: 0 0 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(94, 100, 255, 0.1);
  color: #5e64ff;
  font-size: 1.1rem;
}

.info-panel-content {
  flex: 1;
  padding: 0.875rem 1rem;
}

.info-panel-content h6 {
  color: #343a40;
  margin-bottom: 0.5rem;
  font-size: 0.9rem;
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

/* Badge styling */
.badge {
  font-weight: 500;
}

.card-header .badge {
  font-size: 0.75rem;
  font-weight: 500;
  letter-spacing: 0.02em;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.15);
}

/* Search input styles */
.search-container {
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
  border-radius: 0.25rem;
  overflow: hidden;
}

.search-container .input-group-text {
  background-color: #fff;
  border-right: none;
  padding: 0 0.75rem;
  display: flex;
  align-items: center;
  height: 38px;
}

.search-input {
  border-left: none;
  font-size: 0.875rem;
  height: 38px;
  padding: 0.375rem 0.75rem;
  line-height: 1.5;
}

.search-input:focus {
  box-shadow: none;
  border-color: #ced4da;
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
.form-check-input[id="createDirectory"],
.form-check-input[id="finishedSessionIndication"] {
  width: 2.5em;
  height: 1.25em;
}

.disabled-input {
  opacity: 0.65;
}

.text-muted-disabled {
  opacity: 0.5;
}

/* Action buttons animation */
.action-buttons-container {
  animation: fadeIn 0.2s ease-in-out;
}

</style>
