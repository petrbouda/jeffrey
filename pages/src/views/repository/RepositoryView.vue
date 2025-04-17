<script setup lang="ts">
import {onMounted, ref} from 'vue';
import {useRoute} from 'vue-router'
import ProjectRepositoryService from "@/services/project/ProjectRepositoryService";
import Utils from "@/services/Utils";
import ProjectSettingsService from "@/services/project/ProjectSettingsService";
import RepositoryInfo from "@/services/project/model/RepositoryInfo.ts";
import SettingsResponse from "@/services/project/model/SettingsResponse.ts";
import {ToastService} from "@/services/ToastService";

const route = useRoute()
const toast = ToastService;

const currentProject = ref<SettingsResponse | null>();
const currentRepository = ref<RepositoryInfo | null>();
const isLoading = ref(false);
const isGenerating = ref(false);

const repositoryService = new ProjectRepositoryService(route.params.projectId as string)
const settingsService = new ProjectSettingsService(route.params.projectId as string)

const inputCreateDirectoryCheckbox = ref(true);
const inputRepositoryPath = ref('')
const inputRepositoryType = ref('ASYNC_PROFILER')

onMounted(() => {
  fetchRepositoryData();
  fetchProjectSettings();
});

const fetchRepositoryData = async () => {
  isLoading.value = true;
  try {
    const data = await repositoryService.get();
    currentRepository.value = data;
  } catch (error: any) {
    if (error.response && error.response.status === 404) {
      currentRepository.value = null;
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
    toast.success('Repository Link', 'Repository link has been updated');

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
</script>

<template>
  <div class="row g-4">
    <!-- Current Repository Card -->
    <div class="col-12" v-if="currentRepository">
      <div class="card shadow-sm border-0 h-100">
        <div class="card-header bg-soft-blue d-flex justify-content-between align-items-center text-white py-3">
          <div class="d-flex align-items-center">
            <i class="bi bi-link-45deg fs-4 me-2"></i>
            <h5 class="card-title mb-0">Current Repository</h5>
          </div>
        </div>

        <div class="card-body">
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

          <div class="d-flex justify-content-between mt-4">
            <button
                class="btn btn-primary"
                disabled
            >
              <i class="bi bi-file-earmark-plus me-2"></i>
              Generate Recording 
              <span class="badge bg-secondary ms-2">Coming soon</span>
            </button>
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
</style>
