<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import {useNavigation} from '@/composables/useNavigation';
import ProjectRepositoryClient from "@/services/api/ProjectRepositoryClient.ts";
import ProjectSettingsClient from "@/services/api/ProjectSettingsClient.ts";
import SettingsResponse from "@/services/api/model/SettingsResponse.ts";
import RepositoryStatisticsModel from "@/services/api/model/RepositoryStatistics.ts";
import {ToastService} from "@/services/ToastService";
import RecordingSession from "@/services/api/model/RecordingSession.ts";
import RepositoryStatistics from '@/components/RepositoryStatistics.vue';
import RecordingSessionList from '@/components/RecordingSessionList.vue';
import ProjectClient from "@/services/api/ProjectClient.ts";
import ProjectInfo from "@/services/api/model/ProjectInfo.ts";
import WorkspaceType from "@/services/api/model/WorkspaceType.ts";
import WorkspaceClient from "@/services/api/WorkspaceClient.ts";
import Workspace from "@/services/api/model/Workspace.ts";
import PageHeader from '@/components/layout/PageHeader.vue';

const toast = ToastService;

const currentProject = ref<SettingsResponse | null>();
const projectInfo = ref<ProjectInfo | null>(null);
const workspaceInfo = ref<Workspace | null>(null);
const repositoryStatistics = ref<RepositoryStatisticsModel | null>(null);
const isLoading = ref(false);
const recordingSessions = ref<RecordingSession[]>([]);

const {workspaceId, projectId} = useNavigation();

const repositoryService = new ProjectRepositoryClient(workspaceId.value!, projectId.value!)
const settingsService = new ProjectSettingsClient(workspaceId.value!, projectId.value!)
const projectClient = new ProjectClient(workspaceId.value!, projectId.value!)

const isRemoteWorkspace = computed(() => {
  return workspaceInfo.value?.type === WorkspaceType.REMOTE;
});

onMounted(() => {
  fetchRepositoryData();
  fetchProjectSettings();
  fetchProjectInfo();
  fetchWorkspaceInfo();
});

const fetchRepositoryData = async () => {
  isLoading.value = true;
  try {
    recordingSessions.value = await repositoryService.listRecordingSessions();
    repositoryStatistics.value = await repositoryService.getRepositoryStatistics();
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

    <RecordingSessionList
      v-if="!isLoading"
      :sessions="recordingSessions"
      :workspaceId="workspaceId!"
      :projectId="projectId!"
      :isRemoteWorkspace="isRemoteWorkspace"
      :showInstanceLink="true"
      @refresh="fetchRepositoryData"
    />

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

.text-primary {
  color: #5e64ff !important;
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
</style>
