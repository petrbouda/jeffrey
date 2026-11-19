<!--
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 -->

<!--
  The connector, browsed in columns the way its share is laid out: workspaces, projects, instances,
  sessions. Each column is one folder listing, read when its parent is picked; a session is read
  on its own only when it is opened, in a drawer over the instance and session columns.
-->

<template>
  <div class="connector-browser-root">
    <div v-if="loadingConnector" class="connector-card">
      <LoadingState message="Reading the connector…" />
    </div>
    <div v-else-if="connectorError" class="connector-card">
      <ErrorState :message="connectorError" @retry="refreshConnector" />
    </div>

    <!-- Configuration declares no connector: say where one is declared -->
    <div v-else-if="!connector" class="connector-card">
      <EmptyState
        icon="bi-hdd-network"
        title="No connector configured"
        :description="NO_CONNECTOR_DESCRIPTION"
      >
        <template #action>
          <code class="connector-property">{{ CONNECTOR_PROPERTIES }}</code>
        </template>
      </EmptyState>
    </div>

    <div v-else class="connector-card connector-browser">
      <header class="connector-head">
        <h3 class="connector-name">
          <StatusDot :tone="STATUS_TONES[status]" size="l" :label="statusLabel" />
          {{ connector.name }}
          <Badge :value="KIND_LABELS[connector.kind]" variant="secondary" size="xs" :uppercase="false" />
          <Badge v-if="connector.readOnly" value="Read-only" variant="warning" size="xs" />
        </h3>
        <span class="connector-location" :title="connector.location">{{ connector.location }}</span>
        <div class="connector-actions">
          <button class="icon-btn" type="button" title="Read the connector again" @click="refreshAll">
            <i class="bi bi-arrow-clockwise"></i>
          </button>
        </div>
      </header>

      <div class="connector-stage">
        <!-- Workspaces -->
        <BrowserColumn
          v-model:filter="workspaceFilter"
          title="Workspaces"
          :count="workspaces.loading.value ? undefined : workspaces.items.value.length"
          :width="WORKSPACE_COLUMN_WIDTH"
          filter-placeholder="Filter workspaces…"
        >
          <LoadingState v-if="workspaces.loading.value" message="Loading workspaces…" />
          <ErrorState
            v-else-if="workspaces.error.value"
            :message="workspaces.error.value"
            @retry="loadWorkspaces"
          />
          <ColumnNote v-else-if="workspaces.items.value.length === 0" icon="bi-folder-x">
            {{ NO_WORKSPACE }}
          </ColumnNote>
          <template v-else>
            <BrowserColumnItem
              v-for="workspace in shownWorkspaces"
              :key="workspace.id"
              icon="bi-folder"
              :title="workspace.name"
              :state="itemState(workspace.id === workspaceId, projectId !== null)"
              @select="selectWorkspace(workspace.id)"
            >
              <template v-if="workspace.id === workspaceId" #trailing>
                <button
                  class="item-btn"
                  type="button"
                  title="Workspace info"
                  @click="showWorkspaceInfo = true"
                >
                  <i class="bi bi-info-circle"></i>
                </button>
                <button
                  v-if="!connector.readOnly"
                  class="item-btn"
                  type="button"
                  title="Delete workspace"
                  @click="workspaceToDelete = workspace"
                >
                  <i class="bi bi-trash"></i>
                </button>
              </template>
            </BrowserColumnItem>
          </template>
        </BrowserColumn>

        <!-- Projects -->
        <BrowserColumn
          v-model:filter="projectFilter"
          title="Projects"
          :count="countOf(projects, workspaceId)"
          :width="PROJECT_COLUMN_WIDTH"
          :filter-placeholder="workspaceId ? 'Filter projects…' : undefined"
        >
          <ColumnNote v-if="!workspaceId" icon="bi-folder">Pick a workspace</ColumnNote>
          <LoadingState v-else-if="projects.loading.value" message="Loading projects…" />
          <ErrorState
            v-else-if="projects.error.value"
            :message="projects.error.value"
            @retry="loadProjects"
          />
          <ColumnNote v-else-if="projects.items.value.length === 0" icon="bi-folder-plus">
            {{ NO_PROJECT }}
          </ColumnNote>
          <template v-else>
            <BrowserColumnItem
              v-for="project in shownProjects"
              :key="project.id"
              icon="bi-folder"
              :title="project.name"
              :subtitle="project.createdAt ? FormattingService.formatDateFromMillis(project.createdAt) : undefined"
              :state="itemState(project.id === projectId, instanceId !== null)"
              @select="selectProject(project.id)"
            >
              <template v-if="project.id === projectId && !connector.readOnly" #trailing>
                <button
                  class="item-btn"
                  type="button"
                  title="Delete project"
                  @click="projectToDelete = project"
                >
                  <i class="bi bi-trash"></i>
                </button>
              </template>
            </BrowserColumnItem>
          </template>
        </BrowserColumn>

        <!-- Instances -->
        <BrowserColumn
          v-model:filter="instanceFilter"
          title="Instances"
          :count="countOf(instances, projectId)"
          :width="INSTANCE_COLUMN_WIDTH"
          :filter-placeholder="projectId ? 'Filter instances…' : undefined"
        >
          <ColumnNote v-if="!projectId" icon="bi-box">Pick a project</ColumnNote>
          <LoadingState v-else-if="instances.loading.value" message="Loading instances…" />
          <ErrorState
            v-else-if="instances.error.value"
            :message="instances.error.value"
            @retry="loadInstances"
          />
          <ColumnNote v-else-if="instances.items.value.length === 0" icon="bi-box">
            No instance of this project has started a recording session.
          </ColumnNote>
          <template v-else>
            <BrowserColumnItem
              v-for="instance in shownInstances"
              :key="instance.id"
              icon="bi-box"
              :title="instance.id"
              :subtitle="startedText(instance.createdAt)"
              :tooltip="instance.id"
              mono
              :state="itemState(instance.id === instanceId, sessionId !== null)"
              @select="selectInstance(instance.id)"
            />
          </template>
        </BrowserColumn>

        <!-- Sessions -->
        <BrowserColumn title="Sessions" :count="countOf(sessions, instanceId)" grow>
          <ColumnNote v-if="!instanceId" icon="bi-folder2">Pick an instance</ColumnNote>
          <LoadingState v-else-if="sessions.loading.value" message="Loading sessions…" />
          <ErrorState
            v-else-if="sessions.error.value"
            :message="sessions.error.value"
            @retry="loadSessions"
          />
          <ColumnNote v-else-if="sessions.items.value.length === 0" icon="bi-inbox">
            No recording sessions found for this instance.
          </ColumnNote>
          <template v-else>
            <BrowserColumnItem
              v-for="session in sessions.items.value"
              :key="session.id"
              :icon="session.id === sessionId ? 'bi-folder2-open' : 'bi-folder2'"
              :title="session.id"
              :subtitle="startedText(session.createdAt)"
              mono
              :state="session.id === sessionId ? 'active' : 'none'"
              @select="toggleSession(session.id)"
            >
              <template v-if="session.newest" #badge>
                <Badge value="latest" variant="success" size="xxs" :uppercase="false" />
              </template>
            </BrowserColumnItem>
          </template>
          <template v-if="instanceId && hasOlderSessions && sessions.items.value.length > 0" #footer>
            <LoadMoreFooter
              :shown="sessions.items.value.length"
              :has-more="hasOlderSessions"
              noun="sessions"
              :loading="loadingOlderSessions"
              @loadMore="loadOlderSessions"
            />
          </template>
        </BrowserColumn>

        <PanelDrawer
          :show="selectedSession !== undefined"
          :left="DRAWER_LEFT"
          aria-label="Recording session"
          @update:show="open => { if (!open) { closeSession(); } }"
        >
          <template #header>
            <div v-if="selectedSession" class="drawer-title-row">
              <span class="drawer-icon" :class="{ 'drawer-icon-newest': selectedSession.newest }">
                <i class="bi bi-folder2-open"></i>
              </span>
              <div class="drawer-title">
                <span class="drawer-title-label">Session</span>
                <span class="drawer-title-id">{{ selectedSession.id }}</span>
                <Badge
                  v-if="selectedSession.newest"
                  value="latest · may still grow"
                  variant="success"
                  size="xs"
                  :uppercase="false"
                />
              </div>
              <div class="drawer-stepper">
                <button
                  class="icon-btn"
                  type="button"
                  title="Newer session"
                  :disabled="sessionIndex <= 0"
                  @click="stepSession(-1)"
                >
                  <i class="bi bi-chevron-up"></i>
                </button>
                <button
                  class="icon-btn"
                  type="button"
                  title="Older session"
                  :disabled="sessionIndex >= sessions.items.value.length - 1"
                  @click="stepSession(1)"
                >
                  <i class="bi bi-chevron-down"></i>
                </button>
                <span class="drawer-position">
                  {{ sessionIndex + 1 }} of {{ sessions.items.value.length }}
                </span>
                <button class="icon-btn" type="button" title="Close (Esc)" @click="closeSession">
                  <i class="bi bi-x-lg"></i>
                </button>
              </div>
            </div>
            <div class="drawer-path" :title="pathText">{{ pathText }}</div>
          </template>

          <RecordingSessionDetail
            v-if="selectedSession && workspaceId && projectId"
            :key="selectedSession.id"
            :session="selectedSession"
            :workspace-id="workspaceId"
            :project-id="projectId"
            @deleted="onSessionDeleted"
          />
        </PanelDrawer>
      </div>

      <footer class="connector-path">{{ pathText || connector.name }}</footer>
    </div>

    <!-- Drawers and dialogs -->

    <LeftDrawer
      v-if="selectedWorkspace"
      v-model:show="showWorkspaceInfo"
      title="Workspace Info"
      icon="bi-info-circle"
    >
      <DrawerSection label="Workspace" icon="bi-folder">
        <div class="info-rows">
          <InfoRow label="Reference ID" mono>
            <span class="info-row-text">{{ selectedWorkspace.id }}</span>
            <button class="info-copy-btn" title="Copy" @click="copyText(selectedWorkspace.id)">
              <i class="bi bi-clipboard"></i>
            </button>
          </InfoRow>

          <InfoRow label="Projects">{{ projects.items.value.length }}</InfoRow>
        </div>
      </DrawerSection>

      <DrawerSection v-if="connector" label="Connector" icon="bi-hdd-network">
        <div class="info-rows">
          <InfoRow label="Name">{{ connector.name }}</InfoRow>
          <InfoRow label="Kind">{{ KIND_LABELS[connector.kind] }}</InfoRow>
          <InfoRow label="Address" mono>{{ connector.location }}</InfoRow>
          <InfoRow v-if="connector.readOnly" label="Access">
            Read-only — declared so in configuration; nothing is changed through it
          </InfoRow>
        </div>
      </DrawerSection>
    </LeftDrawer>

    <ConfirmationDialog
      :show="workspaceToDelete !== null"
      title="Delete Workspace"
      :message="workspaceToDelete ? `Delete workspace &quot;${workspaceToDelete.name}&quot;? This is permanent.` : ''"
      confirm-label="Delete"
      modal-id="deleteWorkspaceModal"
      @update:show="open => { if (!open) { workspaceToDelete = null; } }"
      @confirm="deleteWorkspace"
    />

    <ConfirmationDialog
      :show="projectToDelete !== null"
      title="Delete Project"
      :message="projectToDelete ? `Delete project &quot;${projectToDelete.name}&quot; with all its instances and sessions?` : ''"
      sub-message="This action cannot be undone."
      confirm-label="Delete Project"
      modal-id="deleteProjectModal"
      @update:show="open => { if (!open) { projectToDelete = null; } }"
      @confirm="deleteProject"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import Badge from '@shared/components/Badge.vue';
import ConfirmationDialog from '@shared/components/ConfirmationDialog.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import LeftDrawer from '@shared/components/LeftDrawer.vue';
import LoadMoreFooter from '@shared/components/LoadMoreFooter.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import StatusDot from '@shared/components/StatusDot.vue';
import BrowserColumn from '@shared/components/browser/BrowserColumn.vue';
import BrowserColumnItem from '@shared/components/browser/BrowserColumnItem.vue';
import ColumnNote from '@shared/components/browser/ColumnNote.vue';
import DrawerSection from '@shared/components/drawer/DrawerSection.vue';
import InfoRow from '@shared/components/drawer/InfoRow.vue';
import PanelDrawer from '@shared/components/drawer/PanelDrawer.vue';
import FormattingService from '@shared/services/FormattingService';
import ToastService from '@shared/services/ToastService';
import type { BrowserItemState, StatusDotTone } from '@shared/types/ui';
import RecordingSessionDetail from '@/components/connector/RecordingSessionDetail.vue';
import { type Listing, useListing } from '@/composables/useListing';
import ProjectClient from '@/services/api/connector/ProjectClient';
import ProjectInstanceClient from '@/services/api/connector/ProjectInstanceClient';
import ConnectorClient from '@/services/api/connector/ConnectorClient';
import WorkspaceClient from '@/services/api/connector/WorkspaceClient';
import WorkspaceProjectsClient from '@/services/api/connector/WorkspaceProjectsClient';
import Project from '@/services/api/model/connector/Project';
import ProjectInstance from '@/services/api/model/connector/ProjectInstance';
import RecordingSessionHeader from '@/services/api/model/connector/RecordingSessionHeader';
import Connector, { type ConnectorKind } from '@/services/api/model/connector/Connector';
import Workspace from '@/services/api/model/connector/Workspace';
import { EMPTY_SELECTION, type ConnectorSelection } from '@/services/connector/connectorSelection';

const props = withDefaults(
  defineProps<{
    /**
     * Where to open the browser, e.g. from a link. Only seeds the first load, level by level: a
     * level the connector no longer lists falls back to the default, and the reader's own picks win
     * afterwards.
     */
    initialSelection?: ConnectorSelection;
  }>(),
  {
    initialSelection: () => EMPTY_SELECTION
  }
);

const emit = defineEmits<{
  (e: 'update:selection', selection: ConnectorSelection): void;
}>();

type ConnectorStatus = 'online' | 'offline' | 'unknown';

const WORKSPACE_COLUMN_WIDTH = '14rem';
const PROJECT_COLUMN_WIDTH = '16rem';
const INSTANCE_COLUMN_WIDTH = '24rem';
/** The session drawer covers the instance and session columns; the first two stay in view. */
const DRAWER_LEFT = `calc(${WORKSPACE_COLUMN_WIDTH} + ${PROJECT_COLUMN_WIDTH})`;
const PATH_SEPARATOR = ' / ';

const STATUS_TONES: Record<ConnectorStatus, StatusDotTone> = {
  online: 'success',
  offline: 'danger',
  unknown: 'warning'
};

const STATUS_LABELS: Record<ConnectorStatus, string> = {
  online: 'Online',
  offline: 'Unreachable',
  unknown: 'Checking…'
};

const KIND_LABELS: Record<ConnectorKind, string> = {
  AZURE_FILES: 'Azure Files',
  LOCAL: 'Local directory'
};

const CONNECTOR_PROPERTIES = 'jeffrey.microscope.connector.*';
const NO_CONNECTOR_DESCRIPTION =
  'Microscope reads the recordings of one connector: the recording volume on an Azure file share ' +
  'or in a directory on this machine. ' +
  "It is declared in Microscope's configuration only and read at startup: add it " +
  'under these properties and restart.';
const NO_WORKSPACE =
  'A workspace appears here once the provisioner provisions a project into it: its folder on the ' +
  "volume is named by the provisioner's project.workspace-ref-id.";
const NO_PROJECT =
  'Projects appear here once the provisioner starts a profiled JVM in this workspace.';
const NOT_LISTED = 'Start not listed by the volume';

const connectorClient = new ConnectorClient();
const workspaceClient = new WorkspaceClient();

// --- The connector ---
const connector = ref<Connector | null>(null);
const loadingConnector = ref(true);
const connectorError = ref<string | null>(null);
const status = ref<ConnectorStatus>('unknown');
const statusLabel = computed(() => STATUS_LABELS[status.value]);

// --- One listing per column ---
const workspaces = useListing<Workspace>('Could not load workspaces');
const projects = useListing<Project>('Could not load projects');
const instances = useListing<ProjectInstance>('Could not load instances');
const sessions = useListing<RecordingSessionHeader>('Could not load sessions');
const hasOlderSessions = ref(false);
const loadingOlderSessions = ref(false);

// --- The path picked ---
const workspaceId = ref<string | null>(null);
const projectId = ref<string | null>(null);
const instanceId = ref<string | null>(null);
const sessionId = ref<string | null>(null);

/** What is left of the link to restore; each level is used once, when its listing arrives. */
const pending: ConnectorSelection = { ...props.initialSelection };

const workspaceFilter = ref('');
const projectFilter = ref('');
const instanceFilter = ref('');

const showWorkspaceInfo = ref(false);
const workspaceToDelete = ref<Workspace | null>(null);
const projectToDelete = ref<Project | null>(null);

const selectedWorkspace = computed(() =>
  workspaces.items.value.find(workspace => workspace.id === workspaceId.value)
);
const selectedSession = computed(() =>
  sessions.items.value.find(session => session.id === sessionId.value)
);
const sessionIndex = computed(() =>
  sessions.items.value.findIndex(session => session.id === sessionId.value)
);

const matches = (value: string, filter: string): boolean => {
  const query = filter.trim().toLowerCase();
  return query.length === 0 || value.toLowerCase().includes(query);
};

const shownWorkspaces = computed(() =>
  workspaces.items.value.filter(workspace => matches(workspace.name, workspaceFilter.value))
);
const shownProjects = computed(() =>
  projects.items.value.filter(project => matches(project.name, projectFilter.value))
);

/** Newest started first; on a share that lists no times, by name. */
const compareByStart = (a: ProjectInstance, b: ProjectInstance): number => {
  if (a.createdAt !== b.createdAt) {
    return (b.createdAt ?? Number.NEGATIVE_INFINITY) - (a.createdAt ?? Number.NEGATIVE_INFINITY);
  }
  return a.id.localeCompare(b.id);
};

const shownInstances = computed(() =>
  instances.items.value.filter(instance => matches(instance.id, instanceFilter.value))
);

const pathText = computed(() =>
  [selectedWorkspace.value?.name, projectId.value, instanceId.value, sessionId.value]
    .filter(Boolean)
    .join(PATH_SEPARATOR)
);

/** An entry on the path is `path` while the path goes on below it, `active` where it ends. */
const itemState = (selected: boolean, pathContinues: boolean): BrowserItemState => {
  if (!selected) {
    return 'none';
  }
  return pathContinues ? 'path' : 'active';
};

/** A column counts its entries once its parent is picked and its listing has arrived. */
const countOf = <T,>(listing: Listing<T>, parentId: string | null): number | undefined =>
  parentId && !listing.loading.value && !listing.error.value
    ? listing.items.value.length
    : undefined;

const startedText = (createdAt: number | null): string =>
  createdAt === null
    ? NOT_LISTED
    : `${FormattingService.formatTimestampUTCShort(createdAt)} · ${FormattingService.formatRelativeTime(createdAt)}`;

/**
 * The entry to pick once a listing arrives: the one picked before, when the listing still holds
 * it; else the one the link names; else the given default.
 */
const choose = (
  ids: string[],
  current: string | null,
  linked: string | null,
  fallback: string | null
): string | null => {
  for (const candidate of [current, linked]) {
    if (candidate && ids.includes(candidate)) {
      return candidate;
    }
  }
  return fallback;
};

watch([workspaceId, projectId, instanceId, sessionId], () => {
  emit('update:selection', {
    workspaceId: workspaceId.value,
    projectId: projectId.value,
    instanceId: instanceId.value,
    sessionId: sessionId.value
  });
});

// --- Reading the connector, level by level ---
const refreshConnector = async () => {
  loadingConnector.value = true;
  connectorError.value = null;
  try {
    connector.value = await connectorClient.current({ suppressToast: true });
  } catch (error: any) {
    // An unread connector is not an absent one: the not-configured state would be wrong here.
    connectorError.value =
      error?.response?.data?.message ?? error?.message ?? 'Could not read the connector';
    connector.value = null;
  } finally {
    loadingConnector.value = false;
  }
  if (connector.value) {
    status.value = 'unknown';
    await loadWorkspaces();
  }
};

/** Reads every listing on the path again, keeping the path wherever the connector still has it. */
const refreshAll = () => loadWorkspaces();

const loadWorkspaces = async () => {
  const answer = await workspaces.load(
    () => workspaceClient.list({ suppressToast: true }),
    list => list
  );
  if (answer === undefined) {
    status.value = workspaces.error.value ? 'offline' : status.value;
    return;
  }
  status.value = 'online';
  const ids = answer.map(workspace => workspace.id);
  const target = choose(ids, workspaceId.value, pending.workspaceId, ids[0] ?? null);
  pending.workspaceId = null;
  await openWorkspace(target);
};

const openWorkspace = async (id: string | null) => {
  if (id !== workspaceId.value) {
    workspaceId.value = id;
    projectFilter.value = '';
    clearBelowWorkspace();
  }
  if (id) {
    await loadProjects();
  }
};

const loadProjects = async () => {
  const id = workspaceId.value;
  if (!id) {
    return;
  }
  const answer = await projects.load(() => new WorkspaceProjectsClient(id).list(), list => list);
  if (answer === undefined) {
    return;
  }
  const ids = answer.map(project => project.id);
  // A workspace with a single project opens it straight away
  const only = ids.length === 1 ? ids[0] : null;
  const target = choose(ids, projectId.value, pending.projectId, only);
  pending.projectId = null;
  await openProject(target);
};

const openProject = async (id: string | null) => {
  if (id !== projectId.value) {
    projectId.value = id;
    instanceFilter.value = '';
    clearBelowProject();
  }
  if (id) {
    await loadInstances();
  }
};

const loadInstances = async () => {
  const workspace = workspaceId.value;
  const project = projectId.value;
  if (!workspace || !project) {
    return;
  }
  const answer = await instances.load(
    () => new ProjectInstanceClient(workspace, project).list(),
    list => [...list].sort(compareByStart)
  );
  if (answer === undefined) {
    return;
  }
  // A project opens on its newest instance: the one most likely still recording
  const ids = instances.items.value.map(instance => instance.id);
  const target = choose(ids, instanceId.value, pending.instanceId, ids[0] ?? null);
  pending.instanceId = null;
  await openInstance(target);
};

const openInstance = async (id: string | null) => {
  if (id !== instanceId.value) {
    instanceId.value = id;
    clearBelowInstance();
  }
  if (id) {
    await loadSessions();
  }
};

/**
 * Names a page of the instance's sessions, newest first, without reading them: one listing of
 * the instance on the connector. A session is read when it is opened.
 */
const loadSessions = async () => {
  const workspace = workspaceId.value;
  const project = projectId.value;
  const instance = instanceId.value;
  if (!workspace || !project || !instance) {
    return;
  }
  const answer = await sessions.load(
    () => new ProjectInstanceClient(workspace, project).find(instance),
    found => found?.sessions ?? []
  );
  if (answer === undefined) {
    return;
  }
  hasOlderSessions.value = answer?.hasOlder ?? false;
  const ids = sessions.items.value.map(session => session.id);
  sessionId.value = choose(ids, sessionId.value, pending.sessionId, null);
  pending.sessionId = null;
};

/** Reads the page of sessions after the oldest on screen and adds it below. */
const loadOlderSessions = async () => {
  const workspace = workspaceId.value;
  const project = projectId.value;
  const instance = instanceId.value;
  const oldest = sessions.items.value[sessions.items.value.length - 1];
  if (!workspace || !project || !instance || !oldest || loadingOlderSessions.value) {
    return;
  }
  loadingOlderSessions.value = true;
  try {
    const older = await new ProjectInstanceClient(workspace, project).find(instance, oldest.id);
    if (older && instanceId.value === instance) {
      sessions.items.value = [...sessions.items.value, ...older.sessions];
      hasOlderSessions.value = older.hasOlder;
    }
  } catch {
    ToastService.error('Older sessions', 'They could not be read from the connector.');
  } finally {
    loadingOlderSessions.value = false;
  }
};

const clearBelowWorkspace = () => {
  projects.clear();
  projectId.value = null;
  clearBelowProject();
};

const clearBelowProject = () => {
  instances.clear();
  instanceId.value = null;
  clearBelowInstance();
};

const clearBelowInstance = () => {
  sessions.clear();
  sessionId.value = null;
  hasOlderSessions.value = false;
};

// --- The reader's picks ---
const selectWorkspace = (id: string) => {
  if (id !== workspaceId.value) {
    openWorkspace(id);
  }
};

const selectProject = (id: string) => {
  if (id !== projectId.value) {
    openProject(id);
  }
};

/** Picking the instance already picked closes its session and reads its sessions again. */
const selectInstance = (id: string) => {
  if (id === instanceId.value) {
    sessionId.value = null;
  }
  openInstance(id);
};

const toggleSession = (id: string) => {
  sessionId.value = sessionId.value === id ? null : id;
};

const closeSession = () => {
  sessionId.value = null;
};

const stepSession = (delta: number) => {
  const next = sessions.items.value[sessionIndex.value + delta];
  if (next) {
    sessionId.value = next.id;
  }
};

const onSessionDeleted = () => {
  sessionId.value = null;
  loadSessions();
};

// --- Changes ---
const copyText = async (value: string) => {
  try {
    await navigator.clipboard.writeText(value);
    ToastService.success('Copied', value);
  } catch {
    ToastService.error('Copy failed', 'Clipboard not available');
  }
};

const deleteWorkspace = async () => {
  const workspace = workspaceToDelete.value;
  workspaceToDelete.value = null;
  if (!workspace) {
    return;
  }
  try {
    await workspaceClient.delete(workspace.id);
    ToastService.success('Workspace Deleted', `"${workspace.name}" removed.`);
    if (workspaceId.value === workspace.id) {
      workspaceId.value = null;
      clearBelowWorkspace();
    }
    await loadWorkspaces();
  } catch (error: any) {
    ToastService.error(
      'Failed to delete workspace',
      error?.response?.data?.message ?? error?.message ?? 'Unknown error'
    );
  }
};

const deleteProject = async () => {
  const project = projectToDelete.value;
  const workspace = workspaceId.value;
  projectToDelete.value = null;
  if (!project || !workspace) {
    return;
  }
  try {
    await new ProjectClient(workspace, project.id).delete();
    ToastService.success('Project Deleted', `"${project.name}" removed.`);
    if (projectId.value === project.id) {
      projectId.value = null;
      clearBelowProject();
    }
    await loadProjects();
  } catch (error: any) {
    ToastService.error(
      'Failed to delete project',
      error?.response?.data?.message ?? error?.message ?? 'Unknown error'
    );
  }
};

onMounted(refreshConnector);
</script>

<style scoped>
@import '@shared/styles/shared-components.css';

.connector-card {
  background: var(--color-white);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

/* ============== Browser ============== */
.connector-browser {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 200px);
  min-height: 32rem;
  overflow: hidden;
}

.connector-head {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  padding: var(--spacing-3) var(--spacing-4);
  border-bottom: 1px solid var(--color-border);
}

.connector-name {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-bold);
  color: var(--color-dark);
  white-space: nowrap;
}

.connector-location {
  min-width: 0;
  font-family: var(--font-family-monospace);
  font-size: 0.75rem;
  color: var(--color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.connector-actions {
  margin-left: auto;
  display: flex;
  gap: var(--spacing-1);
}

.connector-stage {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
}

.connector-path {
  padding: 6px var(--spacing-4);
  border-top: 1px solid var(--color-border);
  background: var(--color-light);
  font-family: var(--font-family-monospace);
  font-size: 0.7rem;
  color: var(--color-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.icon-btn {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-base);
  border: 1px solid transparent;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: background var(--transition-base);
}

.icon-btn:hover:not(:disabled) {
  background: var(--color-light);
  color: var(--color-text);
}

.icon-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* Actions on the picked workspace and project: they sit on the primary fill */
.item-btn {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: inherit;
  font-size: 0.8rem;
  cursor: pointer;
  opacity: 0.85;
}

.item-btn:hover {
  opacity: 1;
  background: var(--color-primary-light);
}

/* ============== Session drawer header ============== */
.drawer-title-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
}

.drawer-icon {
  width: 34px;
  height: 34px;
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.drawer-icon-newest {
  background: var(--color-success-light);
  color: var(--color-success-hover);
}

.drawer-title {
  min-width: 0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--spacing-2);
}

.drawer-title-label {
  font-size: var(--font-size-base);
  color: var(--color-text-muted);
}

.drawer-title-id {
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-dark);
}

.drawer-stepper {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 2px;
}

.drawer-position {
  padding: 0 var(--spacing-2);
  font-size: 0.75rem;
  color: var(--color-text-muted);
  white-space: nowrap;
}

.drawer-path {
  margin: 2px 0 0 calc(34px + var(--spacing-3));
  font-family: var(--font-family-monospace);
  font-size: 0.7rem;
  color: var(--color-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ============== Empty states ============== */
.connector-property {
  background: var(--color-light);
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
  color: var(--color-code-text);
}

/* ============== Workspace Info drawer ============== */
.info-copy-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  border-radius: var(--radius-sm);
  cursor: pointer;
  flex-shrink: 0;
  transition: all var(--transition-base);
}

.info-copy-btn:hover {
  background: var(--color-light);
  color: var(--color-primary);
}
</style>
