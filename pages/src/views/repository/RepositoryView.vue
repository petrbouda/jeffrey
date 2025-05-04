<script setup lang="ts">
import {computed, onMounted, onUnmounted, ref, nextTick} from 'vue';
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

const route = useRoute()
const toast = ToastService;

const currentProject = ref<SettingsResponse | null>();
const currentRepository = ref<RepositoryInfo | null>();
const isLoading = ref(false);
const isGenerating = ref(false);
const recordingSessions = ref<RecordingSession[]>([]);
const isLoadingSessions = ref(false);
const uploadPanelExpanded = ref(true);
const showFlameMenu = ref(false);
const flameMenuPosition = ref({
  top: '0px',
  left: '0px'
});
const activeSessionId = ref<string | null>(null);
const selectedRawRecording = ref<{ [sessionId: string]: { [sourceId: string]: boolean } }>({});
const showMultiSelectActions = ref<{ [sessionId: string]: boolean }>({});

const repositoryService = new ProjectRepositoryClient(route.params.projectId as string)
const settingsService = new ProjectSettingsClient(route.params.projectId as string)

const inputCreateDirectoryCheckbox = ref(true);
const inputRepositoryPath = ref('')
const inputRepositoryType = ref('ASYNC_PROFILER')
const inputFinishedSessionDetection = ref(true);
const inputFinishedSessionFile = ref('perfcounters.hsprof')

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

// Clean up event listeners when component is unmounted
onUnmounted(() => {
  window.removeEventListener("resize", closeFlameMenu);
  document.removeEventListener("scroll", closeFlameMenu);
});

// Handler to close the flame menu
const closeFlameMenu = () => {
  if (showFlameMenu.value) {
    showFlameMenu.value = false;
  }
};


// Function to fetch recording sessions
const fetchRecordingSessions = async () => {
  // Only fetch if repository is linked
  if (!currentRepository.value) return;

  isLoadingSessions.value = true;
  try {
    // Call the real API
    const data = await repositoryService.listRecordingSessions();

    // Set the data to the reactive variable
    recordingSessions.value = data;

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

// Sort recordings in a session from latest to earliest (newest first)
const getSortedRecordings = (session: RecordingSession) => {
  return [...session.recordings].sort((a, b) => {
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
    if (!selectedRawRecording.value[session.id]) {
      selectedRawRecording.value[session.id] = {};
      session.recordings.forEach(source => {
        selectedRawRecording.value[session.id][source.id] = false;
      });
    }
    
    // Initialize multi-select actions visibility
    if (showMultiSelectActions.value[session.id] === undefined) {
      showMultiSelectActions.value[session.id] = false;
    }
  });
};

// Toggle expanded state for a session
const toggleSession = (sessionId: string) => {
  expandedSessions.value[sessionId] = !expandedSessions.value[sessionId];
};

// Computed property to get the count of recordings for each session
const getSourcesCount = (session: RecordingSession): number => {
  return session.recordings.length;
};

// Helper function to debug session status
const getSessionStatusClass = (session: RecordingSession) => {
  if (session.status === RecordingStatus.ACTIVE) return 'session-active';
  if (session.status === RecordingStatus.FINISHED) return 'session-finished';
  if (session.status === RecordingStatus.UNKNOWN) return 'session-unknown';
  
  // If none of the above match, return the string value for debugging
  return `status-unknown session-${String(session.status).toLowerCase()}`;
};

// Helper function to debug source status
const getSourceStatusClass = (source, sessionId) => {
  const classes = [];
  
  if (selectedRawRecording.value[sessionId] && selectedRawRecording.value[sessionId][source.id]) {
    classes.push('source-selected');
  }
  
  if (source.status === RecordingStatus.ACTIVE) classes.push('source-active');
  else if (source.status === RecordingStatus.FINISHED) classes.push('source-finished');
  else if (source.status === RecordingStatus.UNKNOWN) classes.push('source-unknown');
  else classes.push(`source-${String(source.status).toLowerCase()}`);
  
  return classes.join(' ');
};

const fetchRepositoryData = async () => {
  isLoading.value = true;
  try {
    const data = await repositoryService.get();
    currentRepository.value = data;
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
    const data = await settingsService.get();
    currentProject.value = data;
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

const generateRecording = async () => {
  isGenerating.value = true;

  try {
    await repositoryService.generateRecording();
    toast.success('Recording', 'New Recording generated');
  } catch (error: any) {
    toast.error('Failed to generate recording', error.message);
  } finally {
    isGenerating.value = false;
  }
};

// Toggle actions menu for sessions
const toggleFlameMenu = (event: MouseEvent, sessionId: string) => {
  // Stop event propagation to prevent row toggling
  event.stopPropagation();

  // Set the active session ID first so that we can calculate the menu items
  activeSessionId.value = sessionId;
  
  if (!showFlameMenu.value) {
    // Capture the exact mouse click coordinates
    const clickX = event.clientX;
    const clickY = event.clientY;
    
    // If menu is currently hidden, show it but initially off-screen
    flameMenuPosition.value = {
      top: '-9999px',
      left: '-9999px'
    };
    
    // Show the menu
    showFlameMenu.value = true;
    
    // Use setTimeout to let the menu render first so we can measure it
    setTimeout(() => {
      const menuWidth = 190; // This should match the min-width in CSS
      
      // Get the menu element after it's rendered to calculate its height
      const menuElement = document.querySelector('.flamegraph-menu');
      const menuHeight = menuElement ? menuElement.getBoundingClientRect().height : 200; // Fallback height
      
      // Calculate left position, ensuring it doesn't go off screen
      // Position to the left of the click point (for right-side alignment)
      const leftPosition = Math.max(10, clickX - menuWidth); // Ensure at least 10px from left edge
      
      // Check if there's space on the right if we're near the left edge
      const rightEdgeSpace = window.innerWidth - clickX;
      const finalLeftPosition = rightEdgeSpace < menuWidth ? clickX - menuWidth : leftPosition;
      
      // Determine if click is in the lower half of the viewport
      const windowHeight = window.innerHeight;
      const isInLowerHalf = clickY > windowHeight / 2;
      
      // Position menu above or below the click point based on position
      if (isInLowerHalf) {
        // Position menu above the click point
        flameMenuPosition.value = {
          top: `${clickY - menuHeight - 5}px`, // 5px gap above
          left: `${finalLeftPosition}px`
        };
      } else {
        // Position menu below the click point
        flameMenuPosition.value = {
          top: `${clickY + 5}px`, // 5px gap below
          left: `${finalLeftPosition}px`
        };
      }
    }, 0);
  } else {
    // If menu is currently shown, hide it
    showFlameMenu.value = false;
  }

  // Close menu on any click outside
  if (showFlameMenu.value) {
    const closeMenuListener = () => {
      showFlameMenu.value = false;
      document.removeEventListener('click', closeMenuListener);
    };
    setTimeout(() => {
      document.addEventListener('click', closeMenuListener);
    }, 100);
  }
};

// Execute menu item action
const executeMenuItem = (item: any) => {
  showFlameMenu.value = false;
  if (item.command) {
    item.command();
  }
};

// Create menu items for a session
const createContextMenuItems = (sessionId: string) => {
  const session = recordingSessions.value.find(s => s.id === sessionId);
  if (!session) return [];

  const items = [
    {
      label: 'Download All',
      icon: 'bi-files',
      command: () => {
        downloadAll(sessionId);
      }
    },
    {
      label: 'Merge and Download',
      icon: 'bi-folder-symlink',
      command: () => {
        downloadAndCopy(sessionId);
      }
    },
    {
      type: 'divider'
    },
    {
      label: 'Delete All',
      icon: 'bi-trash',
      danger: true,
      command: () => {
        deleteAll(sessionId);
      }
    }
  ];

  // Add additional items based on session status
  if (session.status === RecordingStatus.FINISHED) {
    items.push({
      label: 'Download Recording',
      icon: 'bi-download',
      command: () => {
        downloadRecording(sessionId);
      }
    });
  }

  return items;
};

const downloadRecording = (sessionId: string) => {
  toast.info('Download Session', 'Downloading recording session is not implemented yet');
  // Implementation would go here
};

const downloadRecordingSource = async (source: any) => {
  try {
    await repositoryService.downloadRawRecording(source);
    toast.success('Download', `Successfully downloaded recording: ${source.name}`);
  } catch (error: any) {
    console.error("Error downloading recording:", error);
    toast.error('Download', error.message || 'Failed to download recording');
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

const clearAllSelections = (sessionId: string) => {
  // Ensure the session exists in the selection object
  if (!selectedRawRecording.value[sessionId]) {
    selectedRawRecording.value[sessionId] = {};
  }
  
  // Set all sources to unselected
  const session = recordingSessions.value.find(s => s.id === sessionId);
  if (session) {
    session.recordings.forEach(source => {
      selectedRawRecording.value[sessionId][source.id] = false;
    });
  }
};

const getSelectedCount = (sessionId: string): number => {
  if (!selectedRawRecording.value[sessionId]) return 0;
  
  // Count the number of selected sources
  return Object.values(selectedRawRecording.value[sessionId]).filter(Boolean).length;
};

const toggleSourceSelection = (sessionId: string, sourceId: string) => {
  // Ensure the session exists in the selection object
  if (!selectedRawRecording.value[sessionId]) {
    selectedRawRecording.value[sessionId] = {};
  }
  
  // Toggle the selection status of the source
  selectedRawRecording.value[sessionId][sourceId] = !selectedRawRecording.value[sessionId][sourceId];
};

const toggleSelectAllSources = (sessionId: string, selectAll: boolean) => {
  const session = recordingSessions.value.find(s => s.id === sessionId);
  if (!session) return;
  
  // Ensure the session exists in the selection object
  if (!selectedRawRecording.value[sessionId]) {
    selectedRawRecording.value[sessionId] = {};
  }
  
  // Set all completed sources to the selected state
  session.recordings.forEach(source => {
    if (source.status !== RecordingStatus.ACTIVE) {
      selectedRawRecording.value[sessionId][source.id] = selectAll;
    }
  });
};

const downloadSelectedSources = async (sessionId: string) => {
  try {
    const session = recordingSessions.value.find(s => s.id === sessionId);
    if (!session) return;
    
    // Get all selected sources
    const selectedSources = session.recordings.filter(source => 
      selectedRawRecording.value[sessionId][source.id]
    );
    
    if (selectedSources.length === 0) {
      toast.info('Download Selected', 'No recordings selected');
      return;
    }
    
    await repositoryService.downloadSelectedRawRecording(selectedSources);
    toast.success('Download Selected', `Successfully downloaded ${selectedSources.length} recording(s)`);
    
    // Clear selections after download
    toggleSelectAllSources(sessionId, false);
    
  } catch (error: any) {
    console.error("Error downloading selected recordings:", error);
    toast.error('Download Selected', error.message || 'Failed to download selected recordings');
  }
};

const downloadAll = async (sessionId: string) => {
  try {
    const session = recordingSessions.value.find(s => s.id === sessionId);
    if (!session) {
      toast.error('Download All', 'Session not found');
      return;
    }
    
    await repositoryService.downloadRecordingSession(session, false);
    toast.success('Download All', 'Successfully downloaded all recordings');
    
    // Refresh sessions list
    await fetchRecordingSessions();
  } catch (error: any) {
    console.error("Error downloading recordings:", error);
    toast.error('Download All', error.message || 'Failed to download recordings');
  }
};

const downloadAndCopy = async (sessionId: string) => {
  try {
    const session = recordingSessions.value.find(s => s.id === sessionId);
    if (!session) {
      toast.error('Merge and Download', 'Session not found');
      return;
    }
    
    await repositoryService.downloadRecordingSession(session, true);
    toast.success('Merge and Download', 'Successfully merged and downloaded recordings');
    
    // Refresh sessions list
    await fetchRecordingSessions();
  } catch (error: any) {
    console.error("Error merging and downloading recordings:", error);
    toast.error('Merge and Download', error.message || 'Failed to merge and download recordings');
  }
};

const moveAll = (sessionId: string) => {
  toast.info('Move All', 'Move all recordings is not implemented yet');
  // Implementation would go here
};

const mergeAndMove = (sessionId: string) => {
  toast.info('Merge and Move', 'Merge and move recordings is not implemented yet');
  // Implementation would go here
};

const deleteAll = (sessionId: string) => {
  toast.info('Delete All', 'Delete all recordings is not implemented yet');
  // Implementation would go here
};

// Close menu when scrolling or window is resized
document.addEventListener("scroll", closeFlameMenu);
window.addEventListener("resize", closeFlameMenu);
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
            <span class="badge linked-badge ms-2" v-if="currentRepository">
              Linked
            </span>
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
                      <span class="badge rounded-pill bg-success ms-2" v-if="currentRepository.directoryExists">
                        <i class="bi bi-check-circle me-1"></i>Directory Exists
                      </span>
                      <span class="badge rounded-pill bg-danger ms-2" v-else>
                        <i class="bi bi-exclamation-triangle me-1"></i>Directory Does Not Exist
                      </span>
                    </div>
                  </td>
                </tr>
                <tr>
                  <td class="fw-medium" style="width: 25%">Repository Type</td>
                  <td style="width: 75%">
                    <span class="badge source-badge ms-2" 
                          :class="currentRepository.repositoryType === 'JDK' ? 'jdk-source' : 'default-source'">
                      {{ currentRepository.repositoryType === 'ASYNC_PROFILER' ? 'Async-Profiler' : currentRepository.repositoryType }}
                    </span>
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
                          <span class="badge source-badge default-source">Async-Profiler</span>
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
                          <span class="badge source-badge jdk-source">JDK</span>
                          <span class="badge bg-secondary ms-1">Coming soon</span>
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
          <span class="badge modern-badge ms-2">
            {{ recordingSessions.length }} session{{ recordingSessions.length !== 1 ? 's' : '' }}
          </span>
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
                    <div class="fw-bold">
                      <i class="bi me-2"
                         :class="expandedSessions[session.id] ? 'bi-chevron-down' : 'bi-chevron-right'"></i>
                      {{ session.id }}
                      <span class="badge modern-count-badge ms-2">
                        {{ getSourcesCount(session) }} sources
                      </span>
                      <span class="badge status-badge ms-1"
                            :class="{
                              'status-active': session.status === RecordingStatus.ACTIVE,
                              'status-finished': session.status === RecordingStatus.FINISHED,
                              'status-unknown': session.status === RecordingStatus.UNKNOWN
                            }">
                        {{ session.status.toLowerCase() }}
                      </span>
                    </div>
                  </div>
                  <div class="d-flex align-items-center">
                    <div class="d-flex text-muted small me-3">
                      <div class="me-3"><i class="bi bi-calendar-date me-1"></i>Created: {{ formatDate(session.createdAt) }}</div>
                      <div><i class="bi bi-check-circle me-1"></i>Finished: {{ formatDate(session.finishedAt) }}</div>
                    </div>
                    <button
                        class="action-btn action-menu-btn me-2"
                        :class="{'active': showMultiSelectActions[session.id]}"
                        type="button"
                        title="Toggle multi-select mode"
                        @click.stop="toggleSelectionMode(session.id)">
                      <i class="bi" :class="{'bi-check2-square': showMultiSelectActions[session.id], 'bi-square': !showMultiSelectActions[session.id]}"></i>
                    </button>
                    <button
                        class="action-btn action-menu-btn"
                        type="button"
                        title="Session actions"
                        @click="toggleFlameMenu($event, session.id)">
                      <i class="bi bi-three-dots"></i>
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
                  
                  <div class="d-flex align-items-center">
                    <span class="me-3 badge selection-count" v-if="getSelectedCount(session.id) > 0">
                      {{ getSelectedCount(session.id) }} selected
                    </span>
                    <button 
                        class="btn btn-sm btn-primary"
                        @click.stop="downloadSelectedSources(session.id)"
                        :disabled="getSelectedCount(session.id) === 0"
                        title="Download selected recordings">
                      <i class="bi bi-download me-1"></i>Download Selected
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
                      <div class="form-check me-2" v-if="showMultiSelectActions[session.id]">
                        <input 
                            class="form-check-input" 
                            type="checkbox" 
                            :id="'source-' + source.id"
                            :disabled="source.status === RecordingStatus.ACTIVE"
                            :checked="selectedRawRecording[session.id] && selectedRawRecording[session.id][source.id]"
                            @change="() => toggleSourceSelection(session.id, source.id)"
                            @click.stop>
                      </div>
                      <i class="bi bi-file-earmark-text fs-5 me-3 text-primary opacity-75"></i>
                      <div>
                        <div class="fw-bold">
                          {{ source.name }}
                          <span class="badge size-badge ms-2">{{ formatFileSize(source.size) }}</span>
                          <span class="badge status-badge small-status-badge status-active ms-1"
                                v-if="source.status === RecordingStatus.ACTIVE">
                            {{ source.status.toLowerCase() }}
                          </span>
                        </div>
                        <div class="d-flex text-muted small mt-1">
                          <div class="me-3"><i class="bi bi-calendar me-1"></i>Created: {{ formatDate(source.createdAt) }}</div>
                          <div><i class="bi bi-check-circle me-1"></i>Finished: {{ formatDate(source.finishedAt) }}</div>
                        </div>
                      </div>
                    </div>
                    <div>
                      <button
                          class="action-btn action-menu-btn"
                          type="button"
                          @click.stop="downloadRecordingSource(source)"
                          :disabled="source.status === RecordingStatus.ACTIVE"
                          title="Download recording">
                        <i class="bi bi-download"></i>
                      </button>
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

  <!-- Session Actions Dropdown Menu -->
  <div v-if="showFlameMenu && activeSessionId" class="flamegraph-menu shadow-sm" :style="flameMenuPosition">
    <div class="menu-header">
      <button class="menu-close" @click="showFlameMenu = false">
        <i class="bi bi-x"></i>
      </button>
    </div>
    <div v-if="createContextMenuItems(activeSessionId).length === 0" class="menu-item disabled">
      No actions available
    </div>
    <template v-for="(item, index) in createContextMenuItems(activeSessionId)" :key="index">
      <div v-if="item.type === 'divider'" class="menu-divider"></div>
      <div v-else
           class="menu-item"
           :class="{'menu-item-danger': item.danger}"
           @click="executeMenuItem(item)">
        <i v-if="item.icon" class="bi me-2" :class="item.icon"></i>
        {{ item.label }}
      </div>
    </template>
  </div>
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

.border-4 {
  border-width: 4px !important;
}

.form-check-input:checked {
  background-color: #5e64ff;
  border-color: #5e64ff;
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

.toggle-btn {
  border-radius: 4px;
  padding: 0.375rem 0.75rem;
  font-size: 0.85rem;
  font-weight: 500;
  transition: all 0.2s ease;
}

.custom-info-btn {
  background-color: #5e64ff;
  border: 1px solid #5e64ff;
  color: white;
  border-radius: 4px;
  padding: 0.375rem 0.75rem;
  font-size: 0.85rem;
  font-weight: 500;
  transition: all 0.2s ease;
}

.custom-info-btn:hover {
  background-color: #4a51eb;
  border-color: #4a51eb;
  color: white;
}

.custom-info-btn:focus {
  color: white;
  outline: none;
  border-color: #5e64ff;
}

/* Recording sessions table styles */
/* Modern Table Styling */
.modern-table-wrapper {
  background-color: #fff;
  border-radius: 10px;
  box-shadow: 0 2px 15px rgba(0, 0, 0, 0.05);
  overflow: hidden;
  margin-bottom: 2rem;
}

.modern-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
}

.modern-table thead th {
  background-color: #f8f9fa;
  font-weight: 600;
  font-size: 0.85rem;
  color: #495057;
  padding: 14px 20px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.modern-table tbody tr {
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.modern-table tbody tr:last-child {
  border-bottom: none;
}

.modern-table td {
  padding: 14px 20px;
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
  background-color: rgba(108, 117, 125, 0.05);
  border-left: 3px solid #6c757d;
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
  background-color: rgba(108, 117, 125, 0.05);
  border-left: 3px solid #6c757d;
}

.source-name {
  font-weight: 500;
  color: #333;
}

.bg-light-blue {
  background-color: rgba(94, 100, 255, 0.05);
}

.bg-soft-primary {
  background-color: rgba(94, 100, 255, 0.15);
}

.bg-soft-warning {
  background-color: rgba(255, 193, 7, 0.2);
}

.bg-soft-secondary {
  background-color: rgba(108, 117, 125, 0.15);
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

.sources-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 2px;
}

.size-badge {
  background-color: #e9ecef;
  color: #495057 !important;
  font-size: 0.75rem;
  font-weight: 500;
  border-radius: 4px;
  padding: 0.2rem 0.5rem;
  letter-spacing: 0.02em;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.small-status-badge {
  font-size: 0.7rem;
  font-weight: 500;
  padding: 0.15rem 0.4rem;
  border-radius: 3px;
  letter-spacing: 0.01em;
}

.status-badge {
  font-weight: 500;
  font-size: 0.75rem;
  border-radius: 4px;
  padding: 0.2rem 0.5rem;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.15);
}

.status-active {
  background-color: #ffc107;
  color: #212529;
}

.status-finished {
  background-color: #5cb85c;
  color: white;
  font-weight: 600;
}

.status-unknown {
  background-color: #e9ecef;
  color: #495057;
}

.modern-badge {
  background-color: #5e64ff;
  color: white;
  font-size: 0.75rem;
  font-weight: 500;
  border-radius: 4px;
  padding: 0.2rem 0.5rem;
  letter-spacing: 0.02em;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.15);
}

.linked-badge {
  background-color: #28a745;
  color: white;
  font-size: 0.75rem;
  font-weight: 500;
  border-radius: 4px;
  padding: 0.2rem 0.5rem;
  letter-spacing: 0.02em;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.15);
}

.modern-count-badge {
  background-color: #5e64ff;
  color: white;
  font-size: 0.75rem;
  font-weight: 500;
  border-radius: 4px;
  padding: 0.2rem 0.5rem;
  letter-spacing: 0.02em;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.15);
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

.source-badge {
  display: inline-flex;
  align-items: center;
  padding: 0.3rem 0.6rem;
  border-radius: 6px;
  font-size: 0.7rem;
  font-weight: 500;
  white-space: nowrap;
}

.jdk-source {
  background-color: rgba(13, 202, 240, 0.15); /* Light bg-info */
  color: #0991ad; /* Darker shade of info blue */
}

.default-source {
  background-color: rgba(138, 43, 226, 0.15); /* Light blueviolet */
  color: #6a1eae; /* Darker shade of blueviolet */
}

.dropdown-menu {
  box-shadow: 0 0.5rem 1rem rgba(0, 0, 0, 0.15);
  border: 1px solid rgba(0, 0, 0, 0.1);
  font-size: 0.875rem;
}

.dropdown-item {
  padding: 0.5rem 1rem;
}

.dropdown-item:active {
  background-color: #5e64ff;
}

.dropdown-item.text-danger:active {
  background-color: #dc3545;
  color: white !important;
}

.dropdown-item i {
  opacity: 0.7;
}

.position-fixed.dropdown-menu {
  z-index: 1050;
  transform: none !important;
  top: auto !important;
  left: auto !important;
  position: fixed !important;
  margin: 0 !important;
}

/* Action button styling */
.actions-cell {
  width: 70px;
  text-align: center;
}

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

.source-selected {
  background-color: rgba(94, 100, 255, 0.05);
  border-left: 3px solid #5e64ff;
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

.selection-count {
  background-color: #e6e7ff;
  color: #5e64ff;
  font-weight: 500;
  font-size: 0.75rem;
  padding: 0.35rem 0.65rem;
  border-radius: 4px;
  border: 1px solid rgba(94, 100, 255, 0.2);
}

/* Dropdown menu styling */
.flamegraph-menu {
  position: fixed;
  background: white;
  border-radius: 6px;
  min-width: 190px;
  padding: 0 0 4px 0;
  z-index: 9999;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  border: 1px solid #e9ecef;
  animation: fadeIn 0.15s ease;
  will-change: transform, opacity;
}

.flamegraph-menu .menu-header {
  display: flex;
  justify-content: flex-end;
  padding: 6px 8px;
  background-color: #f8f9fa;
  border-bottom: 1px solid #e9ecef;
}

.flamegraph-menu .menu-close {
  background: transparent;
  border: none;
  color: #6c757d;
  cursor: pointer;
  width: 20px;
  height: 20px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1rem;
  transition: color 0.15s ease;
  border-radius: 3px;
}

.flamegraph-menu .menu-close:hover {
  color: #495057;
  background-color: rgba(108, 117, 125, 0.1);
}

.flamegraph-menu .menu-item {
  padding: 8px 12px;
  font-size: 0.9rem;
  color: #343a40;
  cursor: pointer;
  transition: all 0.1s ease;
}

.flamegraph-menu .menu-item:hover {
  background-color: rgba(63, 81, 181, 0.08);
  color: #3f51b5;
}

.flamegraph-menu .menu-item:active {
  background-color: rgba(63, 81, 181, 0.2);
}

.flamegraph-menu .menu-item-danger {
  color: #dc3545;
}

.flamegraph-menu .menu-item-danger:hover {
  background-color: rgba(220, 53, 69, 0.08);
  color: #dc3545;
}

.flamegraph-menu .menu-item-danger:active {
  background-color: rgba(220, 53, 69, 0.2);
}

.flamegraph-menu .menu-item.disabled {
  color: #adb5bd;
  font-style: italic;
  cursor: default;
}

.flamegraph-menu .menu-divider {
  height: 1px;
  margin: 6px 0;
  background-color: #dee2e6;
  width: 100%;
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

/* Info tooltip styling */
.info-tooltip {
  cursor: help;
  font-size: 0.85rem;
  opacity: 0.7;
  transition: opacity 0.2s ease;
}

.info-tooltip:hover {
  opacity: 1;
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
</style>
