<template>
  <div>
    <!-- Feature Collection Navigation -->
    <div class="feature-collection-nav normal-nav content-aligned">
      <div class="nav-container">
        <!-- JVM Internals mode (hidden for heap-dump-only and pprof profiles) -->
        <div
          v-if="!isHeapDumpOnlyProfile && !isPprofOnlyProfile"
          class="nav-pill"
          :class="{ active: selectedMode === 'JVM' }"
          title="Core JVM metrics and analysis"
          @click="selectMode('JVM')"
        >
          <div class="pill-content">
            <div class="title-row">
              <i class="bi bi-cpu"></i>
              <span>JVM Internals</span>
            </div>
            <small>Core JVM metrics and analysis</small>
          </div>
        </div>
        <!-- Technologies mode (hidden for heap-dump-only and pprof profiles) -->
        <div
          v-if="!isHeapDumpOnlyProfile && !isPprofOnlyProfile"
          class="nav-pill"
          :class="{ active: selectedMode === 'Technologies' }"
          title="Technology-specific analysis"
          @click="selectMode('Technologies')"
        >
          <div class="pill-content">
            <div class="title-row">
              <i class="bi bi-layers"></i>
              <span>Technologies</span>
            </div>
            <small>Technology-specific analysis</small>
          </div>
        </div>
        <!-- Visualization mode (hidden for heap-dump-only profiles) -->
        <div
          v-if="!isHeapDumpOnlyProfile"
          class="nav-pill"
          :class="{ active: selectedMode === 'Visualization' }"
          title="Profiling graphs and visualizations"
          @click="selectMode('Visualization')"
        >
          <div class="pill-content">
            <div class="title-row">
              <i class="bi bi-bar-chart-line"></i>
              <span>Visualization</span>
            </div>
            <small>Profiling graphs and visualizations</small>
          </div>
        </div>
        <!-- Heap Dump mode (always visible except for pprof profiles, which have no heap data) -->
        <div
          v-if="!isPprofOnlyProfile"
          class="nav-pill"
          :class="{ active: selectedMode === 'HeapDump' }"
          title="Heap dump memory analysis"
          @click="selectMode('HeapDump')"
        >
          <div class="pill-content">
            <div class="title-row">
              <i class="bi bi-database"></i>
              <span>Heap Dump</span>
            </div>
            <small>Memory analysis from heap dumps</small>
          </div>
        </div>
        <!-- Tools mode (hidden for heap-dump-only and pprof profiles) -->
        <div
          v-if="!isHeapDumpOnlyProfile && !isPprofOnlyProfile"
          class="nav-pill"
          :class="{ active: selectedMode === 'Tools' }"
          title="Profile data transformations"
          @click="selectMode('Tools')"
        >
          <div class="pill-content">
            <div class="title-row">
              <i class="bi bi-tools"></i>
              <span>Tools</span>
            </div>
            <small>Profile data transformations</small>
          </div>
        </div>

        <!-- IDE Target Control (profile-wide; visible only when the bridge supports target selection) -->
        <div v-if="ideControlVisible" class="comparison-toggle-wrapper ide-toggle-wrapper">
          <button
            type="button"
            class="comparison-toggle-btn"
            :class="{ active: ideTargetPanelVisible }"
            :title="ideTargetStatus.linked ? 'IDE linked — show details' : 'Set up IDE integration'"
            @click="toggleIdePanel"
          >
            <i class="bi bi-window-stack"></i>
            <span class="toggle-label">IDE</span>
            <span class="toggle-status" :class="{ set: ideTargetStatus.linked }">
              {{ ideTargetStatus.linked ? 'LINKED' : 'NOT SET' }}
            </span>
          </button>
        </div>

        <!-- Comparison Panel Toggle (hidden for heap-dump-only profiles) -->
        <div v-if="!isHeapDumpOnlyProfile" class="comparison-toggle-wrapper">
          <button
            class="comparison-toggle-btn"
            :class="{ active: comparisonPanelVisible, 'has-profile': secondaryProfile }"
            :title="comparisonPanelVisible ? 'Hide differential panel' : 'Show differential panel'"
            @click="toggleComparisonPanel"
          >
            <i class="bi bi-layers"></i>
            <span class="toggle-label">Secondary Profile</span>
            <span class="toggle-status" :class="{ set: secondaryProfile }">
              {{ secondaryProfile ? 'SET' : 'NOT SET' }}
            </span>
          </button>
        </div>
      </div>
    </div>

    <div class="d-flex w-100">
      <!-- Sidebar Menu (hidden on Technologies Hub page) -->
      <ProfileSidebar
        v-if="!isTechnologiesHub"
        :profile-id="profileId"
        :mode="selectedMode"
        :collapsed="sidebarCollapsed"
        :is-feature-disabled="isFeatureDisabled"
        :has-secondary-profile="!!secondaryProfile"
        :active-technology="activeTechnology"
        @toggle-collapse="toggleSidebar"
        @navigate-differential="navigateToDifferentialPage"
        @navigate-technologies-hub="navigateToTechnologiesHub"
      />

      <!-- Main Content -->
      <div class="detail-main-content" :class="{ 'no-sidebar': isTechnologiesHub }">
        <!-- Heap Dump Profile Info (replaces comparison bar for heap-dump-only profiles) -->
        <div v-if="selectedMode === 'HeapDump' && profile" class="heap-dump-profile-info mb-3">
          <i class="bi bi-file-earmark"></i>
          <span class="profile-name">{{ profile.name }}</span>
          <span class="info-separator">&middot;</span>
          <span class="profile-meta">{{ profileId }}</span>
        </div>

        <!-- Compact Differential Analysis Bar -->
        <div
          v-if="selectedMode !== 'HeapDump' && !sidebarCollapsed && comparisonPanelVisible"
          class="compact-comparison-bar mb-3"
        >
          <div class="comparison-cards">
            <!-- Primary Profile -->
            <div class="compact-card primary">
              <div class="card-info">
                <div class="card-title">
                  <span>{{ profile?.name || 'Loading...' }}</span>
                  <Badge value="PRIMARY" variant="primary" size="xxs" borderless />
                  <Badge
                    v-if="profile?.modified"
                    value="MODIFIED"
                    variant="warning"
                    size="xxs"
                    borderless
                  />
                </div>
                <div class="card-meta">
                  <span class="meta-item">
                    <i class="bi bi-file-text me-1"></i>{{ profileId }}
                  </span>
                </div>
              </div>
            </div>

            <!-- VS Divider -->
            <div class="vs-divider">
              <i class="bi bi-arrow-left-right"></i>
            </div>

            <!-- Secondary Profile -->
            <div class="compact-card secondary" :class="{ empty: !secondaryProfile }">
              <div class="card-info">
                <div class="card-title">
                  <span>{{ secondaryProfile?.name || 'Select Secondary Profile' }}</span>
                  <Badge
                    v-if="secondaryProfile"
                    value="SECONDARY"
                    variant="secondary"
                    size="xxs"
                    borderless
                  />
                  <Badge
                    v-if="secondaryProfile"
                    value="BASE"
                    variant="danger"
                    size="xxs"
                    borderless
                  />
                </div>
                <div class="card-meta">
                  <span v-if="!secondaryProfile" class="meta-item help-text"
                    >Enable differential analysis</span
                  >
                  <template v-else>
                    <span class="meta-item">
                      <i class="bi bi-file-text me-1"></i>{{ selectedSecondaryProfileId }}
                    </span>
                    <div class="card-actions">
                      <button
                        class="action-btn"
                        title="Change profile"
                        @click="openSecondaryProfileModal"
                      >
                        <i class="bi bi-arrow-repeat"></i>
                      </button>
                      <button
                        class="action-btn remove"
                        title="Remove profile"
                        @click="clearSecondaryProfile"
                      >
                        <i class="bi bi-x-lg"></i>
                      </button>
                    </div>
                  </template>
                </div>
              </div>
              <button
                v-if="!secondaryProfile"
                class="select-btn"
                @click="openSecondaryProfileModal"
              >
                <i class="bi bi-plus"></i>
              </button>
            </div>
          </div>
        </div>

        <!-- Secondary Profile Selection Modal -->
        <SecondaryProfileSelectionModal
          v-model:show="showSecondaryProfileSelectionModal"
          :current-project-id="projectId"
          :current-profile-id="profileId"
          :current-secondary-profile-id="selectedSecondaryProfileId"
          :current-secondary-project-id="selectedSecondaryProjectId"
          :workspace-id="workspaceId"
          @profile-selected="handleSecondaryProfileSelected"
          @profile-cleared="handleSecondaryProfileCleared"
        />

        <!-- IDE Target Detail Panel (toggled from the top nav, like the comparison panel) -->
        <IdeTargetBar v-if="ideControlVisible && ideTargetPanelVisible" :profile-id="profileId" />

        <!-- Content Area without tabs -->
        <div class="detail-content-container mb-4">
          <div class="card">
            <div class="card-body">
              <router-view
                v-if="profile"
                :profile="profile"
                :secondary-profile="secondaryProfile"
                :disabled-features="disabledFeatures"
              ></router-view>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import ToastService from '@shared/services/ToastService';
import Profile from '@/services/api/model/Profile.ts';
import DirectProfileClient from '@/services/api/DirectProfileClient.ts';

const directProfileClient = new DirectProfileClient();
import ProfileInfo from '@/services/api/model/ProfileInfo.ts';
import RecordingEventSource from '@workspaces/services/api/model/RecordingEventSource.ts';
import SecondaryProfileService from '@/services/SecondaryProfileService.ts';
import SecondaryProfileSelectionModal from '@/components/SecondaryProfileSelectionModal.vue';
import Badge from '@shared/components/Badge.vue';
import MessageBus from '@/services/MessageBus.ts';
import ProfileFeaturesClient from '@/services/api/ProfileFeaturesClient';
import FeatureType from '@/services/api/model/FeatureType';
import { profileStore, ProfileWithContext } from '@/stores/profileStore';
import IdeTargetBar from '@/components/IdeTargetBar.vue';
import ideConfigStore from '@/stores/ideConfigStore';
import ideProfileTargetStore from '@/stores/ideProfileTargetStore';
import ProfileSidebar from '@/components/profile/ProfileSidebar.vue';
import { DifferentialType, ProfileMode } from '@/views/profiles/navigation/profileNavConfig';
const route = useRoute();
const router = useRouter();
const { workspaceId, projectId, navigateToProjectRecordings } = useNavigation();
const profileId = route.params.profileId as string;

const profile = ref<Profile | null>(null);
const secondaryProfile = ref<Profile | null>(null);
const selectedSecondaryProfileId = ref<string>('');
const selectedSecondaryProjectId = ref<string>('');
const loadingProfiles = ref(false);
const loading = ref(true);
const sidebarCollapsed = ref(false);
const showSecondaryProfileSelectionModal = ref(false);
const disabledFeatures = ref<FeatureType[]>([]);
const isHeapDumpOnlyProfile = ref(false);
const isPprofOnlyProfile = ref(false);

/**
 * Check if the profile is a heap-dump-only profile (no JFR data).
 * These profiles have HEAP_DUMP event source.
 */
const checkHeapDumpOnlyProfile = (p: Profile): boolean => {
  return p.eventSource === RecordingEventSource.HEAP_DUMP;
};

/**
 * Check if the profile is a pprof profile. pprof carries stack samples only (no GC, JVM internals,
 * heap dump or technology events), so these profiles expose only the stack-based Visualization mode.
 */
const checkPprofOnlyProfile = (p: Profile): boolean => {
  return p.eventSource === RecordingEventSource.PPROF;
};

// Mapping function to determine which features are associated with menu items
const getFeatureTypeForMenuItem = (menuItem: string): FeatureType | null => {
  const featureMapping: { [key: string]: FeatureType } = {
    container: FeatureType.CONTAINER_DASHBOARD,
    'http-server': FeatureType.HTTP_SERVER_DASHBOARD,
    'http-client': FeatureType.HTTP_CLIENT_DASHBOARD,
    'grpc-server': FeatureType.GRPC_SERVER_DASHBOARD,
    'grpc-client': FeatureType.GRPC_CLIENT_DASHBOARD,
    'jdbc-statements': FeatureType.JDBC_STATEMENTS_DASHBOARD,
    'jdbc-pool': FeatureType.JDBC_POOL_DASHBOARD,
    'performance-counters': FeatureType.PERF_COUNTERS_DASHBOARD,
    'method-tracing': FeatureType.TRACING_DASHBOARD,
    'async-profiler': FeatureType.ASYNC_PROFILER_SPANS,
    'ai-analysis': FeatureType.AI_ANALYSIS,
    'heap-dump': FeatureType.HEAP_DUMP,
    subsecond: FeatureType.SUBSECOND
  };
  return featureMapping[menuItem] || null;
};

// Helper function to check if a feature is disabled
const isFeatureDisabled = (menuItem: string): boolean => {
  const featureType = getFeatureTypeForMenuItem(menuItem);
  return featureType ? disabledFeatures.value.includes(featureType) : false;
};
// Check if the current route is the Technologies hub page
const isTechnologiesHub = computed(() => route.name === 'profile-technologies-hub');

// Determine which technology is active based on the current route
const activeTechnology = computed<string | null>(() => {
  const path = route.path;
  const mode = route.query.mode as string | undefined;
  if (path.includes('/technologies/http/')) {
    return mode === 'client' ? 'http-client' : 'http-server';
  }
  if (path.includes('/technologies/grpc/')) {
    return mode === 'client' ? 'grpc-client' : 'grpc-server';
  }
  if (path.includes('/technologies/jdbc')) {
    return 'jdbc';
  }
  if (path.includes('/technologies/method-tracing/')) {
    return 'method-tracing';
  }
  if (path.includes('/technologies/async-profiler/')) {
    return 'async-profiler';
  }
  return null;
});

// Derive mode from the current route path so refresh preserves the active section
function getModeFromPath(path: string): ProfileMode {
  if (path.includes('/technologies/')) {
    return 'Technologies';
  }
  if (
    path.includes('/flamegraphs/') ||
    path.includes('/subsecond/') ||
    path.includes('/timeseries/')
  ) {
    return 'Visualization';
  }
  if (path.includes('/heap-dump/')) {
    return 'HeapDump';
  }
  if (path.includes('/tools/')) {
    return 'Tools';
  }
  return 'JVM';
}
// (heap-dump-only profiles override this to 'HeapDump' in onMounted)
const selectedMode = ref<ProfileMode>(getModeFromPath(route.path));

// Initialize comparison panel visibility from sessionStorage
const getStoredComparisonPanelState = (): boolean => {
  const stored = sessionStorage.getItem('profile-comparison-panel-visible');
  return stored === 'true'; // Default to hidden
};
const comparisonPanelVisible = ref(getStoredComparisonPanelState());

const toggleComparisonPanel = () => {
  comparisonPanelVisible.value = !comparisonPanelVisible.value;
};

// IDE target control (profile-wide). Visible only when IDE integration is on and the active bridge
// supports choosing a target window (the multi-window Jeffrey plugin). Panel defaults to closed.
const ideTargetStatus = ideProfileTargetStore.status;
const ideControlVisible = computed(
  () =>
    ideConfigStore.isEnabled() && (ideTargetStatus.value.selectable || ideTargetStatus.value.linked)
);

const getStoredIdePanelState = (): boolean => {
  return sessionStorage.getItem('profile-ide-panel-visible') === 'true';
};
const ideTargetPanelVisible = ref(getStoredIdePanelState());

const toggleIdePanel = () => {
  ideTargetPanelVisible.value = !ideTargetPanelVisible.value;
};

watch(ideTargetPanelVisible, newValue => {
  sessionStorage.setItem('profile-ide-panel-visible', String(newValue));
});

// Watch for mode changes and persist to sessionStorage
watch(selectedMode, newMode => {
  sessionStorage.setItem('profile-sidebar-mode', newMode);
});

// Watch for comparison panel visibility changes and persist to sessionStorage
watch(comparisonPanelVisible, newValue => {
  sessionStorage.setItem('profile-comparison-panel-visible', String(newValue));
});

// Scroll to top when route changes within profile
watch(
  () => route.path,
  () => {
    nextTick(() => {
      window.scrollTo({ top: 0, behavior: 'instant' });
    });
  }
);

onMounted(async () => {
  // IDE integration config + cached target status (cache-only read, no port scan).
  ideConfigStore.loadOnce();
  void ideProfileTargetStore.load(profileId);

  try {
    // Fetch profile details using direct profile client (simplified URL)
    const profileWithContext = (await directProfileClient.getById(profileId)) as ProfileWithContext;
    profile.value = profileWithContext;

    // Store profile context in profileStore for navigation
    profileStore.setProfile(profileWithContext);

    // Check if this is a heap-dump-only profile (no JFR data)
    isHeapDumpOnlyProfile.value = checkHeapDumpOnlyProfile(profileWithContext);
    if (isHeapDumpOnlyProfile.value) {
      // Auto-select HeapDump mode for heap-dump-only profiles
      selectedMode.value = 'HeapDump';
    }

    // pprof profiles are stack-samples only: force the Visualization mode and, if the URL landed on
    // a non-visualization section (e.g. the JFR /overview default), redirect to the flamegraph.
    isPprofOnlyProfile.value = checkPprofOnlyProfile(profileWithContext);
    if (isPprofOnlyProfile.value) {
      selectedMode.value = 'Visualization';
      if (getModeFromPath(route.path) !== 'Visualization') {
        router.replace(`/profiles/${profileId}/flamegraphs/primary`);
      }
    }

    // Load disabled features (includes heap dump status)
    try {
      disabledFeatures.value = await new ProfileFeaturesClient(profileId).getDisabledFeatures();
    } catch (error) {
      console.error('Failed to load disabled features:', error);
    }

    // Check if there's a previously selected secondary profile in SecondaryProfileService
    const savedProfile = SecondaryProfileService.get();

    if (savedProfile && savedProfile.id !== profileId) {
      selectedSecondaryProfileId.value = savedProfile.id;
      selectedSecondaryProjectId.value = savedProfile.projectId;

      try {
        loadingProfiles.value = true;
        // Load secondary profile using direct client
        const secondaryWithContext = await directProfileClient.getById(savedProfile.id);
        secondaryProfile.value = secondaryWithContext;
      } catch (error) {
        console.error('Failed to load secondary profile:', error);
        SecondaryProfileService.remove(); // Clear invalid secondary profile
      } finally {
        loadingProfiles.value = false;
      }
    }

    // Check if user is trying to access differential pages without a secondary profile
    const currentPath = route.path;
    if (
      (currentPath.includes('/flamegraphs/differential') ||
        currentPath.includes('/subsecond/differential')) &&
      !secondaryProfile.value
    ) {
      // Redirect to the corresponding primary page
      const redirectPath = currentPath.replace('/differential', '/primary');
      router.replace(redirectPath);

      // Show a message and automatically display the comparison panel
      ToastService.warn(
        'No Secondary Profile',
        'Please select a secondary profile to view differential analysis'
      );
      comparisonPanelVisible.value = true;
    }
  } catch (error) {
    console.error('Failed to load profile:', error);
    ToastService.error('Failed to load profile', 'Error occurred while loading profile details');
    navigateToProjectRecordings();
  } finally {
    loading.value = false;
  }
});

// Open secondary profile modal (data loading is handled by the modal itself)
const openSecondaryProfileModal = () => {
  showSecondaryProfileSelectionModal.value = true;
};

// Clear the secondary profile
const clearSecondaryProfile = () => {
  secondaryProfile.value = null;
  selectedSecondaryProfileId.value = '';
  // Don't reset the project selection to maintain user's context

  SecondaryProfileService.remove();
};

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value;
  MessageBus.emit(MessageBus.SIDEBAR_CHANGED, null);
};

const selectMode = (mode: ProfileMode) => {
  selectedMode.value = mode;

  // Navigate to the first item in the selected mode's menu (simplified URLs)
  const firstRoutes: Record<string, string> = {
    JVM: `/profiles/${profileId}/overview`,
    Technologies: `/profiles/${profileId}/technologies/hub`,
    Visualization: `/profiles/${profileId}/flamegraphs/primary`,
    HeapDump: `/profiles/${profileId}/heap-dump/settings`,
    Tools: `/profiles/${profileId}/tools/rename-frames`
  };

  router.push(firstRoutes[mode]);
};

// Back navigation from a drilled-in technology to the Technologies hub
const navigateToTechnologiesHub = () => {
  router.push(`/profiles/${profileId}/technologies/hub`);
};

// Navigate to differential pages only if secondary profile is selected (simplified URLs)
const navigateToDifferentialPage = (type: DifferentialType) => {
  if (secondaryProfile.value) {
    router.push(`/profiles/${profileId}/${type}/differential`);
  } else {
    // Show a toast message and automatically display the comparison panel
    ToastService.warn('No Secondary Profile', 'Please select a secondary profile for comparison');
    comparisonPanelVisible.value = true;
  }
};

// Handle secondary profile selection from modal
const handleSecondaryProfileSelected = async (profile: Profile, projectId: string) => {
  try {
    selectedSecondaryProfileId.value = profile.id;
    selectedSecondaryProjectId.value = projectId;
    secondaryProfile.value = profile;

    // Save the secondary profile using SecondaryProfileService
    const profileInfo: ProfileInfo = {
      id: profile.id,
      projectId: projectId,
      name: profile.name,
      createdAt: profile.createdAt,
      profilingStartedAt: new Date().toISOString(), // Default value
      profilingFinishedAt: new Date().toISOString(), // Default value
      enabled: profile.enabled
    };
    SecondaryProfileService.update(profileInfo);
  } catch (error) {
    console.error('Failed to select secondary profile:', error);
    ToastService.error('Selection failed', 'Failed to select secondary profile');
  }
};

// Handle secondary profile cleared from modal
const handleSecondaryProfileCleared = () => {
  selectedSecondaryProfileId.value = '';
  selectedSecondaryProjectId.value = '';
  secondaryProfile.value = null;
  SecondaryProfileService.remove();
};

// Handle heap dump status changes
const handleHeapDumpStatusChanged = (ready: boolean) => {
  if (ready) {
    disabledFeatures.value = disabledFeatures.value.filter(f => f !== FeatureType.HEAP_DUMP);
  } else {
    if (!disabledFeatures.value.includes(FeatureType.HEAP_DUMP)) {
      disabledFeatures.value.push(FeatureType.HEAP_DUMP);
    }
  }
};

// Set up message bus listener
MessageBus.on(MessageBus.HEAP_DUMP_STATUS_CHANGED, handleHeapDumpStatusChanged);

onUnmounted(() => {
  MessageBus.off(MessageBus.HEAP_DUMP_STATUS_CHANGED, handleHeapDumpStatusChanged);
});
</script>

<style scoped>
/* ProfileDetail-specific styles */
/* Common sidebar styles are in @/assets/_sidebar-menu.scss */
/* Sidebar-specific styles (tech header etc.) live in @/components/profile/ProfileSidebar.vue */

/* Heap Dump Profile Info */
.heap-dump-profile-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  margin: 0 1rem;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.heap-dump-profile-info .bi-file-earmark {
  color: var(--color-primary);
  font-size: var(--font-size-base);
}

.heap-dump-profile-info .profile-name {
  font-weight: var(--font-weight-semibold);
  color: var(--color-dark);
  font-size: var(--font-size-base);
}

.heap-dump-profile-info .info-separator {
  color: var(--color-text-light);
}

.heap-dump-profile-info .profile-meta {
  color: var(--color-text-muted);
}

/* Compact Comparison Bar */
.compact-comparison-bar {
  background: transparent;
  padding: 0 1rem;
}

.comparison-cards {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 1rem;
}

.compact-card {
  display: flex;
  align-items: stretch;
  padding: 0.75rem 1rem;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-base);
  flex: 1;
  min-width: 0;
  transition: all var(--transition-base);
}

.compact-card.primary {
  height: auto;
}

.compact-card.secondary {
  height: 100%;
}

.compact-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

.compact-card.primary {
  border-left: 3px solid var(--color-accent-blue);
}

.compact-card.secondary:not(.empty) {
  border-left: 3px solid var(--color-danger);
}

.compact-card.secondary.empty {
  border-left: 3px dashed var(--color-text-muted);
  background: var(--color-light);
}

.card-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.card-title {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--color-dark);
  margin-bottom: 0.25rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}

.card-title > span:first-child {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}

.card-meta {
  display: flex;
  gap: 0.75rem;
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.meta-item {
  display: flex;
  align-items: center;
  white-space: nowrap;
}

.vs-divider {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-hover));
  color: white;
  font-weight: 700;
  font-size: 0.7rem;
  padding: 0.375rem 0.75rem;
  border-radius: 20px;
  box-shadow: 0 2px 4px rgba(94, 100, 255, 0.2);
  flex-shrink: 0;
  align-self: center;
  height: auto;
}

.card-actions {
  display: flex;
  gap: 0.25rem;
  margin-left: auto;
}

.card-meta .card-actions {
  margin-left: auto;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--color-border);
  background: white;
  border-radius: 4px;
  font-size: 0.7rem;
  cursor: pointer;
  transition: all 0.2s ease;
  color: var(--color-text);
}

.action-btn:hover {
  background: var(--color-light);
  border-color: var(--color-border);
  transform: translateY(-1px);
}

.action-btn.remove {
  color: var(--color-danger);
}

.action-btn.remove:hover {
  background: var(--color-danger);
  border-color: var(--color-danger);
  color: white;
}

.select-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 2px dashed var(--color-text-muted);
  background: transparent;
  border-radius: 6px;
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.2s ease;
  color: var(--color-text-muted);
  margin-left: auto;
  align-self: center;
}

.select-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: rgba(94, 100, 255, 0.05);
  transform: translateY(-1px);
}

.help-text {
  color: var(--color-text-muted);
  font-style: italic;
}

/* Responsive Design */
@media (max-width: 768px) {
  .compact-comparison-bar {
    padding: 0.5rem;
  }

  .comparison-cards {
    flex-direction: column;
    gap: 0.75rem;
  }

  .vs-divider {
    align-self: center;
    transform: rotate(90deg);
  }

  .card-meta {
    flex-direction: column;
    gap: 0.25rem;
  }
}

/* Feature Mode Switcher */
.feature-mode-switcher {
  background: linear-gradient(135deg, var(--color-light), var(--color-white));
  border-bottom: 1px solid var(--color-border);
  margin: 0 0.5rem;
  border-radius: 8px;
}

.mode-header {
  text-align: center;
}

.mode-title {
  display: block;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-text);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.mode-subtitle {
  display: block;
  font-size: 0.65rem;
  color: var(--color-text-muted);
  margin-top: 0.125rem;
}

.toggle-switch-container {
  display: flex;
  justify-content: center;
  align-items: center;
}

.toggle-switch {
  display: flex;
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  height: 48px;
  width: 100%;
  background: linear-gradient(135deg, var(--color-neutral-light), var(--color-slate-lighter));
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  border: 2px solid var(--color-slate-lighter);
  padding: 3px;
  box-shadow:
    0 2px 8px rgba(0, 0, 0, 0.04),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);

  &:hover {
    border-color: var(--color-primary);
    box-shadow:
      0 4px 12px rgba(94, 100, 255, 0.1),
      inset 0 1px 0 rgba(255, 255, 255, 0.8);
  }
}

.toggle-switch::before {
  content: '';
  position: absolute;
  top: 3px;
  left: 3px;
  height: calc(100% - 6px);
  width: calc(50% - 6px);
  background: linear-gradient(135deg, var(--color-primary), var(--color-indigo-light));
  border-radius: 7px;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 1;
  box-shadow:
    0 3px 8px rgba(94, 100, 255, 0.25),
    0 1px 2px rgba(0, 0, 0, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
  transform: translateX(0);
}

.toggle-switch:has(.custom-mode.active)::before {
  transform: translateX(calc(100% + 6px));
  background: linear-gradient(135deg, var(--color-success-hover), var(--color-success-hover));
}

.toggle-button {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--color-text-muted);
  background-color: transparent;
  border: none;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  z-index: 2;
  gap: 0.25rem;
  padding: 0.25rem;

  .mode-icon {
    font-size: 0.9rem;
    transition: all 0.3s ease;
  }

  .mode-label {
    font-size: 0.65rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.02em;
    transition: all 0.3s ease;
  }

  &:hover:not(.active) {
    color: var(--color-text);
    transform: translateY(-1px);

    .mode-icon {
      transform: scale(1.1);
    }
  }

  &.active {
    color: var(--color-white);
    font-weight: 700;
    text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);

    .mode-icon {
      transform: scale(1.1);
    }

    .mode-label {
      font-weight: 700;
    }
  }

  &.jdk-mode.active {
    /* JDK mode has the default blue gradient */
  }

  &.custom-mode.active {
    /* Custom mode uses green gradient via ::before selector above */
  }

  &:focus {
    outline: none;
  }

  &:focus-visible {
    outline: 2px solid var(--color-primary);
    outline-offset: 2px;
    border-radius: 6px;
  }
}

/* Feature Collection Navigation */
.feature-collection-nav {
  background-color: white;
  padding: 0;
  position: relative;
  box-shadow: 0 4px 12px -2px rgba(0, 0, 0, 0.05);
  z-index: 10;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 1rem;
}

.feature-collection-nav.content-aligned {
  position: relative;
  z-index: 10;
  margin-bottom: 0.75rem;
  width: 100%;
  margin-left: 0;
  margin-right: 0;
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
  background-color: white;
}

.feature-collection-nav.no-gap {
  margin-top: 0 !important;
  padding-top: 0 !important;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.feature-collection-nav.normal-nav.no-gap {
  margin-bottom: 1.5rem !important;
}

.feature-collection-nav .nav-container {
  display: flex;
  align-items: center;
  padding: 0 0rem;
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;

  &::-webkit-scrollbar {
    display: none;
  }
}

.feature-collection-nav .nav-pill {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding: 0.75rem 1.5rem;
  border: none;
  background: transparent;
  position: relative;
  color: var(--color-slate-muted);
  font-size: 0.85rem;
  font-weight: 500;
  min-width: 140px;
  border-radius: 0;
  transition: all 0.25s ease;
  cursor: pointer;

  .pill-content {
    display: flex;
    flex-direction: column;
    gap: 0.3rem;
    width: 100%;
  }

  .title-row {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  i {
    font-size: 1rem;
    transition: all 0.25s ease;
    flex-shrink: 0;
  }

  span {
    opacity: 0.8;
    transition: all 0.25s ease;
    font-weight: 600;
    font-size: 0.9rem;
    line-height: 1.2;
  }

  small {
    font-size: 0.7rem;
    opacity: 0.6;
    transition: all 0.25s ease;
    color: var(--color-text-muted);
    line-height: 1.1;
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
    color: var(--color-slate-text);

    i {
      transform: translateY(-2px);
    }

    span {
      opacity: 1;
    }

    small {
      opacity: 0.8;
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

    small {
      opacity: 0.9;
      color: var(--color-primary);
    }

    &::after {
      background-color: var(--color-primary);
    }
  }
}

@keyframes pulse-highlight {
  0% {
    background-color: var(--color-light);
  }
  50% {
    background-color: rgba(0, 123, 255, 0.15);
  }
  100% {
    background-color: var(--color-light);
  }
}

@keyframes pulse-highlight-error {
  0% {
    background-color: var(--color-light);
  }
  50% {
    background-color: rgba(220, 53, 69, 0.15);
  }
  100% {
    background-color: var(--color-light);
  }
}

/* Comparison Toggle Button */
.comparison-toggle-wrapper {
  display: flex;
  align-items: center;
  padding: 0.5rem 1rem;
  margin-left: auto;
}

.comparison-toggle-btn {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  padding: 0.5rem 1rem;
  border: none;
  background: transparent;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--color-slate-muted);
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  position: relative;
}

.comparison-toggle-btn::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 60%;
  background: var(--color-slate-light);
  border-radius: 2px;
  transition: all 0.2s ease;
}

.comparison-toggle-btn:hover {
  color: var(--color-primary);
  background: rgba(94, 100, 255, 0.04);
}

.comparison-toggle-btn:hover::before {
  background: var(--color-indigo-accent);
}

.comparison-toggle-btn.active {
  color: var(--color-primary);
  background: rgba(94, 100, 255, 0.08);
}

.comparison-toggle-btn.active::before {
  background: var(--color-primary);
  height: 70%;
}

.comparison-toggle-btn i {
  font-size: 1rem;
  opacity: 0.9;
}

.comparison-toggle-btn .toggle-label {
  font-weight: 600;
  letter-spacing: 0.01em;
}

.comparison-toggle-btn .toggle-status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.6rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  padding: 0.15rem 0.4rem;
  border-radius: 2px;
  background: var(--color-amber-light);
  color: var(--color-amber-darkest);
  transition: all 0.2s ease;
}

.comparison-toggle-btn .toggle-status.set {
  background: var(--color-success-100);
  color: var(--color-success-hover);
}

/* The IDE wrapper carries the margin-left:auto that pushes the right-hand group over;
   the Secondary Profile wrapper that follows must NOT add a second auto margin, or the
   free space gets split between them (leaving a big gap). Sit it flush instead, and drop
   the facing paddings so the two buttons sit close together. */
.ide-toggle-wrapper {
  padding-right: 0;
}

.ide-toggle-wrapper + .comparison-toggle-wrapper {
  margin-left: 0;
  padding-left: 0.5rem;
}

/* Responsive adjustments for toggle button */
@media (max-width: 992px) {
  .comparison-toggle-btn .toggle-label {
    display: none;
  }
}

@media (max-width: 768px) {
  .comparison-toggle-wrapper {
    padding: 0.5rem;
  }
  .comparison-toggle-btn .toggle-status {
    display: none;
  }
}

.detail-main-content.no-sidebar {
  padding-left: 0;
}
</style>
