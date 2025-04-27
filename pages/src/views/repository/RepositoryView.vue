<script setup lang="ts">
import {onMounted, ref, computed} from 'vue';
import {useRoute} from 'vue-router'
import ProjectRepositoryClient from "@/services/project/ProjectRepositoryClient.ts";
import Utils from "@/services/Utils";
import ProjectSettingsClient from "@/services/project/ProjectSettingsClient.ts";
import RepositoryInfo from "@/services/project/model/RepositoryInfo.ts";
import SettingsResponse from "@/services/project/model/SettingsResponse.ts";
import {ToastService} from "@/services/ToastService";
import MessageBus from "@/services/MessageBus";
import RecordingSession from "@/services/model/data/RecordingSession.ts";
import RecordingSource from "@/services/model/data/RecordingSource.ts";
import RecordingStatus from "@/services/model/data/RecordingStatus.ts";

const route = useRoute()
const toast = ToastService;

const currentProject = ref<SettingsResponse | null>();
const currentRepository = ref<RepositoryInfo | null>();
const isLoading = ref(false);
const isGenerating = ref(false);
const isRepositoryCardCollapsed = ref(false);
const recordingSessions = ref<RecordingSession[]>([]);
const isLoadingSessions = ref(false);

const repositoryService = new ProjectRepositoryClient(route.params.projectId as string)
const settingsService = new ProjectSettingsClient(route.params.projectId as string)

const inputCreateDirectoryCheckbox = ref(true);
const inputRepositoryPath = ref('')
const inputRepositoryType = ref('ASYNC_PROFILER')

onMounted(() => {
  fetchRepositoryData();
  fetchProjectSettings();
  // Also call fetchRecordingSessions directly for testing purposes
  fetchRecordingSessions();
});

// Mock function to generate recording sessions as specified
const getMockRecordingSessions = (): RecordingSession[] => {
  console.log("Generating mock recording sessions");
  const now = new Date();
  
  // Create dates for the sessions
  const thirdSessionDate = new Date(now.getTime());
  const secondSessionDate = new Date(now.getTime() - 24 * 60 * 60 * 1000); // Yesterday
  const firstSessionDate = new Date(now.getTime() - 48 * 60 * 60 * 1000);  // 2 days ago
  
  // Format dates to ISO string
  const thirdSessionDateStr = thirdSessionDate.toISOString();
  const secondSessionDateStr = secondSessionDate.toISOString();
  const firstSessionDateStr = firstSessionDate.toISOString();
  
  // Session 1 (oldest) with 3 recording sources - all FINISHED
  const session1Sources = Array.from({length: 3}, (_, i) => {
    const sourceDate = new Date(firstSessionDate.getTime() + i * 30 * 60 * 1000);
    const finishedDate = new Date(sourceDate.getTime() + 15 * 60 * 1000);
    return new RecordingSource(
      `source-1-${i+1}`,
      sourceDate.toISOString(),
      finishedDate.toISOString(),
      RecordingStatus.FINISHED,
      Math.floor(Math.random() * 5000000) + 1000000, // Random size between 1MB and 6MB
      finishedDate.toISOString()
    );
  });
  
  // Session 2 (middle) with 4 recording sources - all FINISHED
  const session2Sources = Array.from({length: 4}, (_, i) => {
    const sourceDate = new Date(secondSessionDate.getTime() + i * 30 * 60 * 1000);
    const finishedDate = new Date(sourceDate.getTime() + 15 * 60 * 1000);
    return new RecordingSource(
      `source-2-${i+1}`,
      sourceDate.toISOString(),
      finishedDate.toISOString(),
      RecordingStatus.FINISHED,
      Math.floor(Math.random() * 5000000) + 1000000, // Random size between 1MB and 6MB
      finishedDate.toISOString()
    );
  });
  
  // Session 3 (newest) with 5 recording sources - last one IN_PROGRESS, others FINISHED
  const session3Sources = Array.from({length: 5}, (_, i) => {
    const sourceDate = new Date(thirdSessionDate.getTime() + i * 30 * 60 * 1000);
    // Last source is IN_PROGRESS, doesn't have finishedAt
    if (i === 4) {
      return new RecordingSource(
        `source-3-${i+1}`,
        sourceDate.toISOString(),
        sourceDate.toISOString(), // lastModifiedAt is same as created for in-progress
        RecordingStatus.IN_PROGRESS,
        Math.floor(Math.random() * 2000000) + 500000, // Random size for in-progress file (smaller)
        null
      );
    } else {
      const finishedDate = new Date(sourceDate.getTime() + 15 * 60 * 1000);
      return new RecordingSource(
        `source-3-${i+1}`,
        sourceDate.toISOString(),
        finishedDate.toISOString(),
        RecordingStatus.FINISHED,
        Math.floor(Math.random() * 5000000) + 1000000, // Random size between 1MB and 6MB
        finishedDate.toISOString()
      );
    }
  });
  
  // Return sessions in order from earliest to latest (as they would be from the database)
  // The UI will then sort them based on the sortedSessions computed property
  return [
    // First session (oldest) - FINISHED
    new RecordingSession(
      "session-1",
      firstSessionDateStr,
      firstSessionDateStr,
      RecordingStatus.FINISHED,
      session1Sources,
      new Date(firstSessionDate.getTime() + 2 * 60 * 60 * 1000).toISOString()
    ),
    // Second session (middle) - FINISHED
    new RecordingSession(
      "session-2",
      secondSessionDateStr,
      secondSessionDateStr,
      RecordingStatus.FINISHED,
      session2Sources,
      new Date(secondSessionDate.getTime() + 2 * 60 * 60 * 1000).toISOString()
    ),
    // Third session (newest) - IN_PROGRESS (no finishedAt)
    new RecordingSession(
      "session-3",
      thirdSessionDateStr,
      thirdSessionDateStr,
      RecordingStatus.IN_PROGRESS,
      session3Sources,
      null
    )
  ];
};

// Function to fetch recording sessions
const fetchRecordingSessions = async () => {
  // TEMP FOR TESTING: Generate mock data even if no repository
  // In the real implementation, we'd only fetch if repository is linked
  // if (!currentRepository.value) return;
  
  isLoadingSessions.value = true;
  try {
    // Debug logging
    console.log("Fetching recording sessions");
    
    // In a real app, we would call the API
    // const data = await repositoryService.listRecordingSessions();
    
    // For now, use mock data
    const mockData = getMockRecordingSessions();
    console.log("Mock data generated:", mockData);
    
    // Set the data to the reactive variable
    recordingSessions.value = mockData;
    
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
  return date.toLocaleString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
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
const expandedSessions = ref<{[key: string]: boolean}>({});

// Initialize expanded state for sessions - IN_PROGRESS sessions are expanded by default
const initializeExpandedState = () => {
  recordingSessions.value.forEach(session => {
    expandedSessions.value[session.id] = session.status === RecordingStatusEnum.IN_PROGRESS;
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

// Computed property to get the count of in-progress recordings for each session
const getInProgressSourcesCount = (session: RecordingSession): number => {
  return session.recordings.filter(source => source.status === RecordingStatus.IN_PROGRESS).length;
};

// Make RecordingStatus available to the template
const RecordingStatusEnum = RecordingStatus;

const fetchRepositoryData = async () => {
  isLoading.value = true;
  try {
    const data = await repositoryService.get();
    currentRepository.value = data;
    // Set the repository card to collapsed by default when repository is linked
    isRepositoryCardCollapsed.value = true;
    
    // Once we have a repository, fetch the recording sessions
    console.log("Repository fetched, now fetching sessions");
    await fetchRecordingSessions();
  } catch (error: any) {
    if (error.response && error.response.status === 404) {
      currentRepository.value = null;
      // Keep card opened by default when no repository is linked
      isRepositoryCardCollapsed.value = false;
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
        inputCreateDirectoryCheckbox.value
    );

    await fetchRepositoryData();
    // Repository data should now be loaded, so sessions should be fetched,
    // but let's explicitly fetch sessions here just to be sure
    if (currentRepository.value) {
      // Set the repository card to collapsed by default when repository is linked
      isRepositoryCardCollapsed.value = true;
      toast.success('Repository Link', 'Repository link has been updated');
      
      console.log("New repository linked, explicitly fetching sessions");
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

const toggleRepositoryCard = () => {
  isRepositoryCardCollapsed.value = !isRepositoryCardCollapsed.value;
};
</script>

<template>
  <div class="row g-4">
    <!-- Page Header -->
    <div class="col-12">
      <div class="d-flex align-items-center mb-3">
        <i class="bi bi-calendar-check fs-4 me-2 text-primary"></i>
        <h3 class="mb-0">Repository</h3>
      </div>
      <p class="text-muted mb-2">
        Link a directory to become a repository for this project. The repository is a place with automatically generated
        recordings.
        <br />
        <span class="fst-italic">Jobs can work with these recordings, e.g. automatically generate profiles.</span>.
      </p>
    </div>

    <!-- Current Repository Card -->
    <div class="col-12" v-if="currentRepository">
      <div class="card shadow-sm border-0 h-100">
        <div class="card-header bg-soft-blue d-flex justify-content-between align-items-center text-white py-3">
          <div class="d-flex align-items-center">
            <i class="bi bi-link-45deg fs-4 me-2"></i>
            <h5 class="card-title mb-0">Current Repository</h5>
            <span class="badge bg-success ms-2 px-2 py-1">
              Linked
            </span>
          </div>
          <button 
              class="btn btn-sm btn-outline-light toggle-btn" 
              @click="toggleRepositoryCard"
              title="Toggle Repository Details"
          >
            <i class="bi" :class="isRepositoryCardCollapsed ? 'bi-chevron-down' : 'bi-chevron-up'"></i>
            <span class="ms-1">{{ isRepositoryCardCollapsed ? 'Show Details' : 'Hide Details' }}</span>
          </button>
        </div>

        <div class="card-body" v-show="!isRepositoryCardCollapsed">
          <div class="info-panel mb-4">
            <div class="info-panel-icon">
              <i class="bi bi-info-circle-fill"></i>
            </div>
            <div class="info-panel-content">
              <h6 class="fw-bold mb-1">Repository Information</h6>
              <p class="mb-0">
                Linked Repository is a directory with the latest recordings from the application.
                Generate a concrete recording from the repository and make a new Profile from it.
              </p>
            </div>
          </div>

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
                  <span class="badge bg-primary px-3 py-2">{{ currentRepository.repositoryType }}</span>
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
      </div>
    </div>

    <!-- Recording Sessions Card -->
    <div class="col-12" v-if="recordingSessions.length > 0">
      <div class="card shadow-sm border-0 h-100">
        <div class="card-header bg-soft-blue d-flex justify-content-between align-items-center text-white py-3">
          <div class="d-flex align-items-center">
            <i class="bi bi-collection fs-4 me-2"></i>
            <h5 class="card-title mb-0">Recording Sessions</h5>
            <span class="badge sessions-count-badge ms-2">
              {{ recordingSessions.length }} session{{ recordingSessions.length !== 1 ? 's' : '' }}
            </span>
          </div>
        </div>

        <div class="card-body">
          <div class="table-responsive">
            <table class="table table-hover sessions-table">
              <thead>
                <tr>
                  <th>Session ID</th>
                  <th>Created At</th>
                  <th>Finished At</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <template v-for="session in sortedSessions" :key="session.id">
                  <!-- Main session row -->
                  <tr class="session-row clickable" 
                      :class="{'bg-light-blue': session.status === RecordingStatusEnum.IN_PROGRESS}"
                      @click="toggleSession(session.id)">
                    <td>
                      <div class="d-flex align-items-center flex-wrap">
                        <div class="d-flex align-items-center">
                          <i class="bi fs-5 me-2 text-primary" 
                             :class="expandedSessions[session.id] ? 'bi-folder2-open' : 'bi-folder2'"></i>
                          <span class="fw-medium">{{ session.id }}</span>
                          <i class="bi ms-2" 
                             :class="expandedSessions[session.id] ? 'bi-chevron-down' : 'bi-chevron-right'"></i>
                        </div>
                        <div class="sources-badges ms-2">
                          <span class="badge bg-soft-primary text-primary">
                            {{ getSourcesCount(session) }} sources
                          </span>
                          <span class="badge in-progress-badge ms-1" v-if="session.status === RecordingStatusEnum.IN_PROGRESS">
                            in progress
                          </span>
                        </div>
                      </div>
                    </td>
                    <td>{{ formatDate(session.createdAt) }}</td>
                    <td>{{ formatDate(session.finishedAt) }}</td>
                    <td>
                      <div class="d-flex">
                        <div class="dropdown d-inline-block" @click.stop>
                          <button class="btn btn-sm btn-outline-secondary dropdown-toggle" 
                                  type="button" 
                                  data-bs-toggle="dropdown" 
                                  aria-expanded="false">
                            Actions
                          </button>
                          <ul class="dropdown-menu">
                            <li><a class="dropdown-item" href="#" @click.prevent><i class="bi bi-files me-2"></i>Copy All</a></li>
                            <li><a class="dropdown-item" href="#" @click.prevent><i class="bi bi-intersect me-2"></i>Merge and Copy</a></li>
                            <li><hr class="dropdown-divider"></li>
                            <li><a class="dropdown-item" href="#" @click.prevent><i class="bi bi-arrows-move me-2"></i>Move All</a></li>
                            <li><a class="dropdown-item" href="#" @click.prevent><i class="bi bi-folder-symlink me-2"></i>Merge and Move</a></li>
                            <li><hr class="dropdown-divider"></li>
                            <li><a class="dropdown-item text-danger" href="#" @click.prevent><i class="bi bi-trash me-2"></i>Delete All</a></li>
                          </ul>
                        </div>
                      </div>
                    </td>
                  </tr>
                  
                  <!-- Nested sources rows -->
                  <tr class="source-row" 
                      v-if="expandedSessions[session.id]"
                      v-for="source in getSortedRecordings(session)" 
                      :key="source.id"
                      :class="{'bg-soft-warning bg-opacity-10': source.status === RecordingStatusEnum.IN_PROGRESS}">
                    <td class="ps-4">
                      <div class="d-flex align-items-center">
                        <i class="bi bi-file-earmark-text fs-5 me-2 text-primary opacity-75"></i>
                        <div>
                          <div class="d-flex align-items-center flex-wrap">
                            <span>{{ source.id }}</span>
                            <span class="badge bg-secondary ms-2 size-badge">{{ formatFileSize(source.size) }}</span>
                            <span class="badge in-progress-badge small-status-badge ms-1" v-if="source.status === RecordingStatusEnum.IN_PROGRESS">
                              in progress
                            </span>
                          </div>
                        </div>
                      </div>
                    </td>
                    <td>{{ formatDate(source.createdAt) }}</td>
                    <td>{{ formatDate(source.finishedAt) }}</td>
                    <td>
                      <div class="d-flex gap-1">
                        <button class="btn btn-sm btn-outline-primary" title="View source details">
                          <i class="bi bi-eye"></i>
                        </button>
                        <button class="btn btn-sm btn-outline-success" title="Create recording from source" 
                                :disabled="source.status !== RecordingStatusEnum.FINISHED">
                          <i class="bi bi-file-earmark-plus"></i>
                        </button>
                      </div>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>

    <!-- Loading Sessions Placeholder -->
    <div class="col-12" v-if="isLoadingSessions && !recordingSessions.length">
      <div class="card shadow-sm border-0">
        <div class="card-header bg-soft-blue d-flex justify-content-between align-items-center text-white py-3">
          <div class="d-flex align-items-center">
            <i class="bi bi-collection fs-4 me-2"></i>
            <h5 class="card-title mb-0">Recording Sessions</h5>
          </div>
        </div>
        <div class="card-body p-5 text-center">
          <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
          <p class="mt-3">Loading recording sessions...</p>
        </div>
      </div>
    </div>

    <!-- No Sessions Message -->
    <div class="col-12" v-if="!isLoadingSessions && !recordingSessions.length">
      <div class="card shadow-sm border-0">
        <div class="card-header bg-soft-blue d-flex justify-content-between align-items-center text-white py-3">
          <div class="d-flex align-items-center">
            <i class="bi bi-collection fs-4 me-2"></i>
            <h5 class="card-title mb-0">Recording Sessions</h5>
          </div>
        </div>
        <div class="card-body p-5 text-center">
          <div class="empty-state mb-3">
            <i class="bi bi-folder-x display-4 text-muted"></i>
          </div>
          <h5>No Recording Sessions Available</h5>
          <p class="text-muted">There are no recording sessions available for this repository.</p>
        </div>
      </div>
    </div>

    <!-- Link Repository Card -->
    <div class="col-12" v-if="!currentRepository && !isLoading">
      <div class="card shadow-sm border-0">
        <div class="card-header bg-soft-blue d-flex justify-content-between align-items-center text-white py-3">
          <div class="d-flex align-items-center">
            <i class="bi bi-link-45deg fs-4 me-2"></i>
            <h5 class="card-title mb-0">Link a Repository</h5>
          </div>
        </div>

        <div class="card-body">
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
                          Async-Profiler
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
                          JDK <span class="badge bg-secondary">Coming soon</span>
                        </label>
                      </div>
                    </div>
                  </td>
                </tr>
                <tr>
                  <td class="fw-medium" style="width: 25%">Options</td>
                  <td style="width: 75%">
                    <div class="form-check">
                      <input
                          class="form-check-input"
                          type="checkbox"
                          id="createDirectory"
                          v-model="inputCreateDirectoryCheckbox"
                      >
                      <label class="form-check-label" for="createDirectory">
                        Create directory if it doesn't exist
                      </label>
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
  </div>

  <!-- Loading Placeholder -->
  <div class="container-fluid p-4" v-if="isLoading && !currentRepository">
    <div class="row">
      <div class="col-12">
        <div class="card shadow-sm border-0">
          <div class="card-header bg-soft-blue d-flex justify-content-between align-items-center text-white py-3">
            <div class="d-flex align-items-center">
              <i class="bi bi-link-45deg fs-4 me-2"></i>
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
  background-color: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.toggle-btn:hover {
  background-color: rgba(255, 255, 255, 0.25);
  border-color: rgba(255, 255, 255, 0.4);
}

.toggle-btn:focus {
  box-shadow: 0 0 0 0.2rem rgba(255, 255, 255, 0.15);
}

/* Recording sessions table styles */
.sessions-table thead th {
  background-color: #f8f9fa;
  font-weight: 600;
  font-size: 0.85rem;
  color: #495057;
  border-bottom-width: 1px;
}

.sessions-table tbody tr:hover {
  background-color: rgba(94, 100, 255, 0.02);
}

.sessions-table .session-row {
  border-top: 1px solid rgba(0, 0, 0, 0.05);
}

.session-row.clickable {
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.session-row.clickable:hover {
  background-color: rgba(0, 0, 0, 0.02);
}

.sessions-table .source-row {
  background-color: rgba(0, 0, 0, 0.01);
  border-top: none;
}

.sessions-table .source-row td {
  color: #555;
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
  font-size: 0.7rem;
  font-weight: 500;
  padding: 0.2rem 0.4rem;
  background-color: rgba(108, 117, 125, 0.15) !important;
  color: #6c757d !important;
}

.small-status-badge {
  font-size: 0.7rem;
  font-weight: 500;
  padding: 0.15rem 0.4rem;
}

.in-progress-badge {
  background-color: rgba(255, 193, 7, 0.25);
  color: #b68100;
  border: 1px solid rgba(214, 158, 0, 0.3);
  font-weight: 600;
}

.sessions-count-badge {
  background-color: rgba(255, 255, 255, 0.25);
  color: #fff;
  font-size: 0.75rem;
  font-weight: 600;
  border-radius: 4px;
  padding: 0.2rem 0.5rem;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  color: #adb5bd;
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
</style>
