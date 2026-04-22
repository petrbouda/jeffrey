<template>
  <div class="project-detail">
    <div class="project-nav">
      <div class="container-fluid">
        <!-- Slim breadcrumb row above the tabs -->
        <div class="project-breadcrumb" aria-label="breadcrumb">
          <router-link to="/workspaces" class="crumb">Workspaces</router-link>
          <i class="bi bi-chevron-right crumb-sep"></i>
          <router-link
            v-if="workspaceInfo"
            :to="`/workspaces/${workspaceInfo.id}/projects`"
            class="crumb"
          >
            {{ workspaceInfo.name ?? workspaceInfo.id }}
          </router-link>
          <span v-else class="crumb crumb-loading">Loading…</span>
          <i class="bi bi-chevron-right crumb-sep"></i>
          <span class="crumb crumb-current">{{ projectInfo?.name ?? 'Loading…' }}</span>
        </div>

        <!-- Stacked icon-over-label tabs, matching MainNavigation -->
        <nav class="nav-container" aria-label="Project sections">
          <router-link
            :to="generateProjectUrl('instances/timeline')"
            class="nav-pill"
            active-class="active"
          >
            <i class="bi bi-bar-chart-steps"></i>
            <span>Timeline</span>
          </router-link>
          <router-link
            v-if="!isCollectorOnly"
            :to="generateProjectUrl('recordings')"
            class="nav-pill"
            active-class="active"
          >
            <i class="bi bi-record-circle"></i>
            <span>Recordings</span>
          </router-link>
          <router-link
            :to="generateProjectUrl('instances')"
            class="nav-pill"
            :class="{ active: isInstancesActive }"
          >
            <i class="bi bi-grid"></i>
            <span>Instances</span>
          </router-link>
          <router-link
            :to="generateProjectUrl('events/live-stream')"
            class="nav-pill"
            active-class="active"
          >
            <i class="bi bi-broadcast"></i>
            <span>Live Stream</span>
          </router-link>
          <router-link
            :to="generateProjectUrl('events/replay-stream')"
            class="nav-pill"
            active-class="active"
          >
            <i class="bi bi-collection-play"></i>
            <span>Replay Stream</span>
          </router-link>
          <router-link
            :to="generateProjectUrl('profiler-settings')"
            class="nav-pill"
            active-class="active"
          >
            <i class="bi bi-cpu"></i>
            <span>Profiler Settings</span>
          </router-link>
          <router-link
            :to="generateProjectUrl('settings')"
            class="nav-pill"
            active-class="active"
          >
            <i class="bi bi-sliders"></i>
            <span>Settings</span>
          </router-link>
        </nav>
      </div>
    </div>

    <div class="content-spacer"></div>

    <router-view></router-view>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ToastService from '@/services/ToastService';
import ProjectClient from '@/services/api/ProjectClient.ts';
import Project from '@/services/api/model/Project.ts';
import WorkspaceClient from '@/services/api/WorkspaceClient.ts';
import Workspace from '@/services/api/model/Workspace.ts';
import { useNavigation } from '@/composables/useNavigation';

const route = useRoute();
const router = useRouter();
const { workspaceId, projectId, generateProjectUrl } = useNavigation();

const projectInfo = ref<Project | null>(null);
const workspaceInfo = ref<Workspace | null>(null);

// Scheduler is always disabled in local mode; Collector-only mode is never active.
const isCollectorOnly = computed(() => false);

// Prefix-matching would mark Instances active on /instances/timeline (which has its own tab).
// Custom predicate: active on /instances and /instances/{instanceId} but not on /timeline.
const isInstancesActive = computed(() => {
  const path = route.path;
  return /\/instances(\/[^/]+)?$/.test(path) && !path.endsWith('/instances/timeline');
});

const workspaceClient = new WorkspaceClient();

async function initializeProject() {
  if (!projectId.value || !workspaceId.value) return;

  try {
    const projectClient = new ProjectClient(workspaceId.value, projectId.value);

    const [project, workspace] = await Promise.all([
      projectClient.get(),
      workspaceClient.getById(workspaceId.value).catch(err => {
        console.error('Failed to load workspace:', err);
        return null;
      })
    ]);

    projectInfo.value = project;
    workspaceInfo.value = workspace;
  } catch (error) {
    console.error('Failed to load project:', error);
    ToastService.error('Failed to load project', 'Cannot load project from the server.');
    await router.push('/workspaces');
  }
}

watch(
  [projectId, workspaceId],
  async ([newProjectId, newWorkspaceId]) => {
    if (newProjectId && newWorkspaceId) {
      await initializeProject();
    }
  },
  { immediate: true }
);
</script>

<style scoped lang="scss">
/* Extend --color-light across the full AppLayout content area so the project-detail view uses
   the same page background as Index.vue (bg-light). Without this, AppLayout's slightly darker
   --color-bg-body shows through around and below the content. Negative margins escape the
   parent's 1rem padding; matching padding restores inner spacing. */
.project-detail {
  background-color: var(--color-light);
  margin: -1rem;
  padding: 1rem;
  min-height: calc(100vh - 72px);
}

/* Nav strip still spans edge-to-edge, matching MainNavigation. Escapes the project-detail's
   own 1rem padding. */
.project-nav {
  background-color: white;
  box-shadow: 0 4px 12px -2px rgba(0, 0, 0, 0.05);
  position: relative;
  z-index: 10;
  margin: -1rem -1rem 0;
}

.project-breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 16px 7px;
  font-size: 0.75rem;
  border-bottom: 1px solid var(--color-border);
}
.crumb {
  color: var(--color-text-muted);
  text-decoration: none;
  transition: color 0.15s;
}
.crumb:hover {
  color: var(--color-primary);
  text-decoration: underline;
}
.crumb-current {
  color: var(--color-dark);
  font-weight: 700;
  cursor: default;
}
.crumb-current:hover {
  color: var(--color-dark);
  text-decoration: none;
}
.crumb-loading {
  color: var(--color-text-light);
  font-style: italic;
}
.crumb-sep {
  font-size: 0.6rem;
  color: var(--color-text-light);
}

/* Tab container + pills: mirrors MainNavigation.vue so the visual language matches. */
.nav-container {
  display: flex;
  align-items: center;
  padding: 0 1rem;
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;

  &::-webkit-scrollbar {
    display: none;
  }
}

.nav-pill {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0.75rem 1.5rem;
  border: none;
  background: transparent;
  position: relative;
  color: var(--color-slate-muted, var(--color-text-muted));
  font-size: 0.85rem;
  font-weight: 500;
  min-width: 100px;
  border-radius: 0;
  transition: all 0.25s ease;
  text-decoration: none;

  i {
    font-size: 1.25rem;
    margin-bottom: 0.25rem;
    transition: all 0.25s ease;
  }

  span {
    opacity: 0.7;
    transition: all 0.25s ease;
  }

  &::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 0;
    width: 100%;
    height: 3px;
    background-color: transparent;
    transition: background-color 0.25s ease;
  }

  &:hover {
    color: var(--color-slate-text, var(--color-text));

    i {
      transform: translateY(-2px);
    }

    span {
      opacity: 1;
    }
  }

  &.active {
    color: var(--color-primary);

    i {
      transform: translateY(-2px);
    }

    span {
      opacity: 1;
    }

    &::after {
      background-color: var(--color-primary);
    }
  }
}

.content-spacer {
  height: 24px;
}
</style>
