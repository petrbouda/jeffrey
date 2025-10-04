<template>
  <div class="d-flex w-100">
    <!-- Sidebar Menu -->
    <div class="profile-sidebar" :class="{ 'collapsed': sidebarCollapsed }">
      <div class="sidebar" :class="{ 'collapsed': sidebarCollapsed }">
        <div class="hamburger-toggle d-flex align-items-center justify-content-center">
          <button class="btn btn-sm hamburger-btn" @click="toggleSidebar">
            <i class="bi" :class="sidebarCollapsed ? 'bi-chevron-right' : 'bi-chevron-left'"></i>
          </button>
        </div>

        <div class="scrollbar" style="height: 100%;">
          <!-- Profile Header -->
          <div class="p-3 border-bottom">
            <div v-if="!sidebarCollapsed">
              <h5 class="fs-6 fw-bold mb-0 text-truncate" style="max-width: 260px;">{{
                  profile?.name || 'Loading...'
                }}</h5>
              <p class="text-muted mb-0 fs-7">Profile details</p>
            </div>
          </div>

          <!-- Mode Switcher -->
          <div v-if="!sidebarCollapsed" class="mode-switcher p-2">
            <div class="toggle-switch-container">
              <div class="toggle-switch">
                <button
                  class="toggle-button"
                  :class="{ 'active': selectedMode === 'JDK' }"
                  @click="selectedMode = 'JDK'"
                >
                  JDK
                </button>
                <button
                  class="toggle-button"
                  :class="{ 'active': selectedMode === 'Custom' }"
                  @click="selectedMode = 'Custom'"
                >
                  Custom
                </button>
              </div>
            </div>
          </div>

          <div class="sidebar-menu" v-if="!sidebarCollapsed">
            <!-- JDK Mode Menu -->
            <template v-if="selectedMode === 'JDK'">
              <div class="nav-section">
                <div class="nav-section-title">OVERVIEW</div>
                <div class="nav-items">
                  <router-link
                      :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/overview`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-gear"></i>
                    <span>Configuration</span>
                  </router-link>
                  <router-link
                      :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/guardian`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-shield-check"></i>
                    <span>Guardian Analysis</span>
                    <Badge v-if="warningCount > 0" :value="warningCount.toString()" variant="danger" size="xs" class="ms-auto" />
                  </router-link>
                  <router-link
                      :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/auto-analysis`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-robot"></i>
                    <span>Auto Analysis</span>
                    <Badge v-if="autoAnalysisWarningCount > 0" :value="autoAnalysisWarningCount.toString()" variant="danger" size="xs" class="ms-auto" />
                  </router-link>
                  <router-link
                      :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/event-types`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-list-check"></i>
                    <span>Event Types</span>
                  </router-link>
                  <router-link
                      :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/events`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-collection"></i>
                    <span>Events</span>
                  </router-link>
                  <router-link
                      v-if="hasPerformanceCounters"
                      :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/performance-counters`"
                      class="nav-item"
                      :class="{ 'disabled-feature': isFeatureDisabled('performance-counters') }"
                      active-class="active"
                  >
                    <i class="bi bi-speedometer2"></i>
                    <span>Performance Counters</span>
                  </router-link>
                </div>
              </div>

              <div class="nav-section">
                <div class="nav-section-title">RUNTIME</div>
                <div class="nav-items">
                  <router-link
                      :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/threads`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-graph-up"></i>
                    <span>Thread Statistics</span>
                  </router-link>
                  <router-link
                      :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/threads-timeline`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-clock-history"></i>
                    <span>Thread Timeline</span>
                  </router-link>
                  <router-link
                      :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/jit-compilation`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-lightning"></i>
                    <span>JIT Compilation</span>
                  </router-link>
                  <!-- Heap Memory with Submenu -->
                  <div class="nav-item-group">
                    <div class="nav-item nav-item-parent" 
                         @click="toggleHeapMemorySubmenu" 
                         :class="{ 'active': $route.path.includes('/heap-memory'), 'expanded': heapMemorySubmenuExpanded }">
                      <i class="bi bi-memory"></i>
                      <span>Heap Memory</span>
                      <i class="bi bi-chevron-right submenu-arrow" :class="{ 'rotated': heapMemorySubmenuExpanded }"></i>
                    </div>
                    <div class="nav-submenu" :class="{ 'expanded': heapMemorySubmenuExpanded }">
                      <router-link
                          :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/heap-memory/timeseries`"
                          class="nav-item nav-subitem"
                          active-class="active"
                      >
                        <i class="bi bi-graph-up-arrow"></i>
                        <span>Timeseries</span>
                      </router-link>
                    </div>
                  </div>
                  <!-- Garbage Collection with Submenu -->
                  <div class="nav-item-group">
                    <div class="nav-item nav-item-parent" 
                         @click="toggleGCSubmenu" 
                         :class="{ 'active': $route.path.includes('/garbage-collection'), 'expanded': gcSubmenuExpanded }">
                      <i class="bi bi-recycle"></i>
                      <span>Garbage Collection</span>
                      <i class="bi bi-chevron-right submenu-arrow" :class="{ 'rotated': gcSubmenuExpanded }"></i>
                    </div>
                    <div class="nav-submenu" :class="{ 'expanded': gcSubmenuExpanded }">
                      <router-link
                          :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/garbage-collection`"
                          class="nav-item nav-subitem"
                          active-class="active"
                      >
                        <i class="bi bi-bar-chart-line"></i>
                        <span>Overview</span>
                      </router-link>
                      <router-link
                          :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/garbage-collection/timeseries`"
                          class="nav-item nav-subitem"
                          active-class="active"
                      >
                        <i class="bi bi-graph-up-arrow"></i>
                        <span>Timeseries</span>
                      </router-link>
                      <router-link
                          :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/garbage-collection/configuration`"
                          class="nav-item nav-subitem"
                          active-class="active"
                      >
                        <i class="bi bi-gear"></i>
                        <span>Configuration</span>
                      </router-link>
                    </div>
                  </div>
                  <!-- Container with Submenu -->
                  <div class="nav-item-group">
                    <div class="nav-item nav-item-parent" 
                         @click="toggleContainerSubmenu" 
                         :class="{ 'active': $route.path.includes('/container'), 'expanded': containerSubmenuExpanded, 'disabled-feature': isFeatureDisabled('container') }">
                      <i class="bi bi-server"></i>
                      <span>Container</span>
                      <i class="bi bi-chevron-right submenu-arrow" :class="{ 'rotated': containerSubmenuExpanded }"></i>
                    </div>
                    <div class="nav-submenu" :class="{ 'expanded': containerSubmenuExpanded }">
                      <router-link
                          :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/container/configuration`"
                          class="nav-item nav-subitem"
                          :class="{ 'disabled-feature': isFeatureDisabled('container') }"
                          active-class="active"
                      >
                        <i class="bi bi-gear"></i>
                        <span>Configuration</span>
                      </router-link>
                    </div>
                  </div>
                </div>
              </div>

              <div class="nav-section">
                <div class="nav-section-title">FLAMEGRAPHS</div>
                <div class="nav-items">
                  <router-link
                      :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/flamegraphs/primary`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-fire"></i>
                    <span>Primary</span>
                  </router-link>
                  <a href="#"
                     @click.prevent="navigateToDifferentialPage('flamegraphs')"
                     class="nav-item"
                     :class="{ 'active': $route.path.includes('/flamegraphs/differential') }"
                  >
                    <i class="bi bi-file-diff"></i>
                    <span>Differential</span>
                    <i v-if="!secondaryProfile" class="bi bi-lock ms-auto"
                       title="Select a secondary profile to enable this page"></i>
                  </a>
                  <router-link
                      :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/flamegraphs/saved`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-bookmark"></i>
                    <span>Saved</span>
                  </router-link>
                </div>
              </div>

              <div class="nav-section">
                <div class="nav-section-title">SUBSECOND GRAPHS</div>
                <div class="nav-items">
                  <router-link
                      :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/subsecond/primary`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-bar-chart"></i>
                    <span>Primary</span>
                  </router-link>
                  <a href="#"
                     @click.prevent="navigateToDifferentialPage('subsecond')"
                     class="nav-item"
                     :class="{ 'active': $route.path.includes('/subsecond/differential') }"
                  >
                    <i class="bi bi-file-bar-graph"></i>
                    <span>Differential</span>
                    <i v-if="!secondaryProfile" class="bi bi-lock ms-auto"
                       title="Select a secondary profile to enable this page"></i>
                  </a>
                </div>
              </div>
            </template>

            <!-- Application Mode Menu -->
            <template v-else>
              <div class="nav-section">
                <div class="nav-section-title">CUSTOM</div>
                <div class="nav-items">
                  <!-- HTTP Server Exchange with Submenu -->
                  <div class="nav-item-group">
                    <div class="nav-item nav-item-parent" 
                         @click="toggleHttpServerSubmenu" 
                         :class="{ 'active': $route.path.includes('/application/http') && $route.query.mode !== 'client', 'expanded': httpServerSubmenuExpanded, 'disabled-feature': isFeatureDisabled('http-server') }">
                      <i class="bi bi-cloud-arrow-down"></i>
                      <span>HTTP Server Exchange</span>
                      <i class="bi bi-chevron-right submenu-arrow" :class="{ 'rotated': httpServerSubmenuExpanded }"></i>
                    </div>
                    <div class="nav-submenu" :class="{ 'expanded': httpServerSubmenuExpanded }">
                      <router-link
                          :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/application/http/overview?mode=server`"
                          class="nav-item nav-subitem"
                          :class="{ 'active': $route.path.includes('/application/http/overview') && ($route.query.mode === 'server' || !$route.query.mode), 'disabled-feature': isFeatureDisabled('http-server') }"
                      >
                        <i class="bi bi-bar-chart-line"></i>
                        <span>Overview</span>
                      </router-link>
                      <router-link
                          :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/application/http/endpoints?mode=server`"
                          class="nav-item nav-subitem"
                          :class="{ 'active': $route.path.includes('/application/http/endpoints') && ($route.query.mode === 'server' || !$route.query.mode), 'disabled-feature': isFeatureDisabled('http-server') }"
                      >
                        <i class="bi bi-share"></i>
                        <span>Endpoint Details</span>
                      </router-link>
                    </div>
                  </div>
                  <!-- HTTP Client Exchange with Submenu -->
                  <div class="nav-item-group">
                    <div class="nav-item nav-item-parent" 
                         @click="toggleHttpClientSubmenu" 
                         :class="{ 'active': $route.path.includes('/application/http') && $route.query.mode === 'client', 'expanded': httpClientSubmenuExpanded, 'disabled-feature': isFeatureDisabled('http-client') }">
                      <i class="bi bi-cloud-arrow-up"></i>
                      <span>HTTP Client Exchange</span>
                      <i class="bi bi-chevron-right submenu-arrow" :class="{ 'rotated': httpClientSubmenuExpanded }"></i>
                    </div>
                    <div class="nav-submenu" :class="{ 'expanded': httpClientSubmenuExpanded }">
                      <router-link
                          :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/application/http/overview?mode=client`"
                          class="nav-item nav-subitem"
                          :class="{ 'active': $route.path.includes('/application/http/overview') && $route.query.mode === 'client', 'disabled-feature': isFeatureDisabled('http-client') }"
                      >
                        <i class="bi bi-bar-chart-line"></i>
                        <span>Overview</span>
                      </router-link>
                      <router-link
                          :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/application/http/endpoints?mode=client`"
                          class="nav-item nav-subitem"
                          :class="{ 'active': $route.path.includes('/application/http/endpoints') && $route.query.mode === 'client', 'disabled-feature': isFeatureDisabled('http-client') }"
                      >
                        <i class="bi bi-share"></i>
                        <span>Endpoint Details</span>
                      </router-link>
                    </div>
                  </div>
                  <!-- JDBC Statements with Submenu -->
                  <div class="nav-item-group">
                    <div class="nav-item nav-item-parent" 
                         @click="toggleJdbcSubmenu" 
                         :class="{ 'active': $route.path.includes('/application/jdbc'), 'expanded': jdbcSubmenuExpanded, 'disabled-feature': isFeatureDisabled('jdbc-statements') }">
                      <i class="bi bi-database"></i>
                      <span>JDBC Statements</span>
                      <i class="bi bi-chevron-right submenu-arrow" :class="{ 'rotated': jdbcSubmenuExpanded }"></i>
                    </div>
                    <div class="nav-submenu" :class="{ 'expanded': jdbcSubmenuExpanded }">
                      <router-link
                          :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/application/jdbc/overview`"
                          class="nav-item nav-subitem"
                          :class="{ 'disabled-feature': isFeatureDisabled('jdbc-statements') }"
                          active-class="active"
                      >
                        <i class="bi bi-bar-chart-line"></i>
                        <span>Overview</span>
                      </router-link>
                      <router-link
                          :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/application/jdbc/statement-groups`"
                          class="nav-item nav-subitem"
                          :class="{ 'disabled-feature': isFeatureDisabled('jdbc-statements') }"
                          active-class="active"
                      >
                        <i class="bi bi-collection"></i>
                        <span>Statement Groups</span>
                      </router-link>
                    </div>
                  </div>
                  <router-link
                      :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/application/jdbc-pool`"
                      class="nav-item"
                      :class="{ 'disabled-feature': isFeatureDisabled('jdbc-pool') }"
                      active-class="active">
                    <i class="bi bi-diagram-3"></i>
                    <span>JDBC Connection Pools</span>
                  </router-link>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- Main Content -->
    <div class="profile-main-content">
      <!-- Secondary Profile Selection Bar -->
      <div class="secondary-profile-bar p-3 mb-3 border-bottom" v-if="!sidebarCollapsed">
        <div class="d-flex justify-content-between align-items-center">
          <div class="d-flex align-items-center">
            <span class="me-2 fw-bold">Secondary Profile:</span>

            <!-- Secondary Profile Status -->
            <div v-if="!secondaryProfile" class="secondary-profile-placeholder">
              <span class="text-muted">No secondary profile selected</span>
              <button class="btn btn-sm btn-primary ms-3" @click="openSecondaryProfileModal">
                <i class="bi bi-plus-circle me-1"></i> Select Profile
              </button>
            </div>

            <!-- Selected Secondary Profile Info -->
            <div v-else class="selected-profile-info">
              <span class="badge bg-info text-dark me-2 px-2 py-1">
                <i class="bi bi-file-earmark-text me-1"></i> {{ secondaryProfile.name }}
              </span>
              <span v-if="selectedSecondaryProjectId !== projectId" class="badge bg-secondary text-white me-2 px-2 py-1">
                <i class="bi bi-folder me-1"></i> {{ selectedSecondaryProjectId }}
              </span>
            </div>
          </div>

          <!-- Clear button positioned at the right -->
          <div v-if="secondaryProfile">
            <button
                class="btn btn-sm btn-outline-danger"
                @click="clearSecondaryProfile"
                title="Clear secondary profile"
            >
              <i class="bi bi-x"></i>
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

      <!-- Content Area without tabs -->
      <div class="profile-content-container mb-4">
        <div class="card">
          <div class="card-body">
            <router-view
                :profile="profile"
                :secondaryProfile="secondaryProfile"
                :disabledFeatures="disabledFeatures"
            ></router-view>
          </div>
        </div>
      </div>
    </div>
  </div>

</template>

<script setup lang="ts">
import {onMounted, ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import ToastService from '@/services/ToastService';
import Profile from "@/services/model/Profile.ts";
import Project from "@/services/model/Project.ts";
import ProjectProfileClient from "@/services/ProjectProfileClient.ts";
import FormattingService from "@/services/FormattingService";
import ProfileInfo from "@/services/project/model/ProfileInfo.ts";
import SecondaryProfileService from "@/services/SecondaryProfileService.ts";
import Badge from '@/components/Badge.vue';
import SecondaryProfileSelectionModal from '@/components/SecondaryProfileSelectionModal.vue';
import MessageBus from "@/services/MessageBus.ts";
import GuardianClient from "@/services/guardian/GuardianClient";
import AutoAnalysisClient from "@/services/AutoAnalysisClient";
import ProfilePerformanceCountersClient from "@/services/ProfilePerformanceCountersClient";
import ProfileFeaturesClient from "@/services/profile/features/ProfileFeaturesClient";
import FeatureType from "@/services/profile/features/FeatureType";

const route = useRoute();
const router = useRouter();
const { workspaceId, projectId, generateProjectUrl } = useNavigation();
const profileId = route.params.profileId as string;
const profileService = new ProjectProfileClient(workspaceId.value!, projectId.value!);

const profile = ref<Profile | null>(null);
const secondaryProfile = ref<Profile | null>(null);
const selectedSecondaryProfileId = ref<string>('');
const selectedSecondaryProjectId = ref<string>('');
const loadingProfiles = ref(false);
const loading = ref(true);
const sidebarCollapsed = ref(false);
const showSecondaryProfileSelectionModal = ref(false);
const warningCount = ref<number>(0);
const autoAnalysisWarningCount = ref<number>(0);
const hasPerformanceCounters = ref<boolean>(true); // Default to true until checked
const disabledFeatures = ref<FeatureType[]>([]);

// Mapping function to determine which features are associated with menu items
const getFeatureTypeForMenuItem = (menuItem: string): FeatureType | null => {
  const featureMapping: { [key: string]: FeatureType } = {
    'container': FeatureType.CONTAINER_DASHBOARD,
    'http-server': FeatureType.HTTP_SERVER_DASHBOARD,
    'http-client': FeatureType.HTTP_CLIENT_DASHBOARD,
    'jdbc-statements': FeatureType.JDBC_STATEMENTS_DASHBOARD,
    'jdbc-pool': FeatureType.JDBC_POOL_DASHBOARD,
    'performance-counters': FeatureType.PERF_COUNTERS_DASHBOARD,
  };
  return featureMapping[menuItem] || null;
};

// Helper function to check if a feature is disabled
const isFeatureDisabled = (menuItem: string): boolean => {
  const featureType = getFeatureTypeForMenuItem(menuItem);
  return featureType ? disabledFeatures.value.includes(featureType) : false;
};
// Initialize mode from sessionStorage or default to 'JDK'
const getStoredMode = (): 'JDK' | 'Custom' => {
  const stored = sessionStorage.getItem('profile-sidebar-mode');
  return (stored === 'JDK' || stored === 'Custom') ? stored : 'JDK';
};

const selectedMode = ref<'JDK' | 'Custom'>(getStoredMode());
const httpServerSubmenuExpanded = ref(false);
const httpClientSubmenuExpanded = ref(false);
const jdbcSubmenuExpanded = ref(false);
const heapMemorySubmenuExpanded = ref(false);
const gcSubmenuExpanded = ref(false);
const containerSubmenuExpanded = ref(false);

// Watch for mode changes and persist to sessionStorage
watch(selectedMode, (newMode) => {
  sessionStorage.setItem('profile-sidebar-mode', newMode);
});

// Watch for route changes to auto-expand HTTP and JDBC submenus
watch(() => route.path, (newPath) => {
  if (newPath.includes('/application/http')) {
    if (route.query.mode === 'client') {
      httpClientSubmenuExpanded.value = true;
    } else {
      httpServerSubmenuExpanded.value = true;
    }
  }
  if (newPath.includes('/application/jdbc')) {
    jdbcSubmenuExpanded.value = true;
  }
  if (newPath.includes('/heap-memory')) {
    heapMemorySubmenuExpanded.value = true;
  }
  if (newPath.includes('/garbage-collection')) {
    gcSubmenuExpanded.value = true;
  }
  if (newPath.includes('/container')) {
    containerSubmenuExpanded.value = true;
  }
}, { immediate: true });

onMounted(async () => {
  try {
    // Fetch profile details, available projects, and profiles in parallel
    profile.value = await profileService.get(profileId);

    // Load guardian warning count
    try {
      const guardData = await GuardianClient.list(workspaceId.value!, projectId.value!, profileId);
      // Count WARNING severity items across all categories
      let count = 0;
      guardData.forEach(category => {
        count += category.results.filter(result => result.severity === "WARNING").length;
      });
      warningCount.value = count;
    } catch (error) {
      console.error('Failed to load guardian data:', error);
      warningCount.value = 0;
    }

    // Load auto analysis warning count
    try {
      const analysisData = await AutoAnalysisClient.rules(workspaceId.value!, projectId.value!, profileId);
      // Count WARNING severity items
      autoAnalysisWarningCount.value = analysisData.filter(rule => rule.severity === "WARNING").length;
    } catch (error) {
      console.error('Failed to load auto analysis data:', error);
      autoAnalysisWarningCount.value = 0;
    }

    // Check if performance counters data exists
    try {
      const exists = await ProfilePerformanceCountersClient.exists(workspaceId.value!, projectId.value!, profileId);
      hasPerformanceCounters.value = exists === true;
    } catch (error) {
      console.error('Failed to check performance counters existence:', error);
      hasPerformanceCounters.value = false;
    }

    // Load disabled features from API
    try {
      const profileFeaturesClient = new ProfileFeaturesClient(workspaceId.value!, projectId.value!, profileId);
      disabledFeatures.value = await profileFeaturesClient.getDisabledFeatures();
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
        // Create a profile client for the saved project
        const savedProjectProfileClient = new ProjectProfileClient(workspaceId.value!, savedProfile.projectId);
        secondaryProfile.value = await savedProjectProfileClient.get(savedProfile.id);
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
        (currentPath.includes('/flamegraphs/differential') || currentPath.includes('/subsecond/differential')) &&
        !secondaryProfile.value
    ) {
      // Redirect to the corresponding primary page
      const redirectPath = currentPath.replace('/differential', '/primary');
      router.replace(redirectPath);

      // Show a message
      ToastService.warn('No Secondary Profile', "Please select a secondary profile to view differential analysis");

      // Highlight the secondary profile selection bar with red color
      setTimeout(() => {
        const selectionBar = document.querySelector('.secondary-profile-bar');
        if (selectionBar) {
          selectionBar.classList.add('highlight-selection-bar-error');
          setTimeout(() => {
            selectionBar.classList.remove('highlight-selection-bar-error');
          }, 2000);
        }
      }, 500); // Small delay to ensure the page has rendered
    }
  } catch (error) {
    console.error('Failed to load profile:', error);
    ToastService.error('Failed to load profile', 'Error occurred while loading profile details');
    router.push(generateProjectUrl('profiles'));
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
  ToastService.success('Secondary profile cleared', 'Now, no secondary profile selected for comparison');
};

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value;
  MessageBus.emit(MessageBus.SIDEBAR_CHANGED, null);
};

const toggleHttpServerSubmenu = () => {
  httpServerSubmenuExpanded.value = !httpServerSubmenuExpanded.value;
};

const toggleHttpClientSubmenu = () => {
  httpClientSubmenuExpanded.value = !httpClientSubmenuExpanded.value;
};

const toggleJdbcSubmenu = () => {
  jdbcSubmenuExpanded.value = !jdbcSubmenuExpanded.value;
};

const toggleHeapMemorySubmenu = () => {
  heapMemorySubmenuExpanded.value = !heapMemorySubmenuExpanded.value;
};

const toggleGCSubmenu = () => {
  gcSubmenuExpanded.value = !gcSubmenuExpanded.value;
};

const toggleContainerSubmenu = () => {
  containerSubmenuExpanded.value = !containerSubmenuExpanded.value;
};

// Navigate to differential pages only if secondary profile is selected
const navigateToDifferentialPage = (type: 'flamegraphs' | 'subsecond') => {
  if (secondaryProfile.value) {
    router.push(`/workspaces/${workspaceId.value}/projects/${projectId.value}/profiles/${profileId}/${type}/differential`);
  } else {
    // Show a toast message that secondary profile selection is required
    ToastService.warn('No Secondary Profile','Please select a secondary profile for comparison');

    // Highlight the secondary profile selection bar with red color without scrolling
    const selectionBar = document.querySelector('.secondary-profile-bar');
    if (selectionBar) {
      selectionBar.classList.add('highlight-selection-bar-error');
      setTimeout(() => {
        selectionBar.classList.remove('highlight-selection-bar-error');
      }, 2000);
    }
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

    ToastService.success(`Secondary profile`, `"${profile.name}" selected for comparison`);
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
  ToastService.info('Secondary profile cleared', 'Secondary profile selection has been cleared');
};
</script>

<style scoped>
/* Content container styles */
.profile-content-container {
  width: 100%;
}

.card {
  border: none;
  border-radius: 0;
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
}

/* Sidebar styles */
.profile-sidebar {
  width: 280px;
  min-height: 100vh;
  background-color: #fff;
  border-right: 1px solid #dee2e6;
  transition: all 0.3s ease;
  flex-shrink: 0;
  position: relative;
}

.profile-sidebar.collapsed {
  width: 50px;
}

.sidebar {
  height: 100%;
  width: 100%;
  overflow: hidden;
}

.sidebar.collapsed {
  width: 50px;
}

.scrollbar {
  overflow-y: auto;
  height: calc(100% - 40px);

  &::-webkit-scrollbar {
    width: 5px;
    height: 5px;
  }

  &::-webkit-scrollbar-thumb {
    background-color: rgba(0, 0, 0, 0.1);
    border-radius: 4px;
  }

  &:hover::-webkit-scrollbar-thumb {
    background-color: rgba(0, 0, 0, 0.2);
  }
}

.hamburger-toggle {
  position: absolute;
  right: -16px;
  top: 20px;
  z-index: 10;
}

.hamburger-btn {
  width: 32px;
  height: 32px;
  padding: 0;
  background-color: #fff;
  border: 1px solid #dee2e6;
  border-radius: 50%;
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
}

/* Modern sidebar styling */
.sidebar-menu {
  padding: 0.5rem 0;
}

.nav-section {
  margin-bottom: 1.25rem;
}

.nav-section-title {
  color: #748194;
  font-weight: 600;
  font-size: 0.7rem;
  letter-spacing: 0.03em;
  text-transform: uppercase;
  padding: 0 1.25rem;
  margin-bottom: 0.5rem;
}

.nav-items {
  display: flex;
  flex-direction: column;
}

.nav-item {
  display: flex;
  align-items: center;
  padding: 0.5rem 1.25rem;
  color: #5e6e82;
  font-weight: 500;
  font-size: 0.85rem;
  border-radius: 0;
  transition: all 0.2s ease;
  position: relative;
  text-decoration: none;
  margin: 0.125rem 0;

  &:hover {
    color: #5e64ff;
    background-color: rgba(94, 100, 255, 0.06);
  }

  &.active {
    color: #5e64ff;
    background-color: rgba(94, 100, 255, 0.1);
    border-left: 3px solid #5e64ff;
    padding-left: calc(1.25rem - 3px);

    i {
      color: #5e64ff;
    }
  }

  i {
    color: #7d899b;
    font-size: 0.9rem;
    width: 1.5rem;
    text-align: center;
    margin-right: 0.5rem;
  }
}


/* HTTP Submenu Styles */
.nav-item-group {
  display: flex;
  flex-direction: column;
}

.nav-item-parent {
  cursor: pointer;
  position: relative;
  
  .submenu-arrow {
    margin-left: auto;
    transition: transform 0.2s ease;
    font-size: 0.7rem;
  }
  
  .submenu-arrow.rotated {
    transform: rotate(90deg);
  }
}

.nav-submenu {
  max-height: 0;
  overflow: hidden;
  transition: max-height 0.3s ease;
  background-color: rgba(248, 249, 250, 0.5);
  margin-left: 1rem;
}

.nav-submenu.expanded {
  max-height: 250px;
}

.nav-subitem {
  padding: 0.5rem 0.75rem;
  margin: 0.125rem 0.5rem;
  font-size: 0.8rem;
  border-radius: 4px;
  transition: all 0.2s ease;
  background-color: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(94, 100, 255, 0.08);
  
  &:hover {
    background-color: rgba(94, 100, 255, 0.06);
    border-color: rgba(94, 100, 255, 0.15);
    transform: translateX(2px);
    box-shadow: 0 2px 4px rgba(94, 100, 255, 0.1);
  }
  
  &.active {
    background-color: rgba(94, 100, 255, 0.12);
    border-color: #5e64ff;
    color: #5e64ff;
    font-weight: 600;
    box-shadow: 0 2px 8px rgba(94, 100, 255, 0.15);
    
    i {
      color: #5e64ff;
    }
  }
  
  i {
    font-size: 0.8rem;
    width: 1rem;
    margin-right: 0.4rem;
  }
}

.fs-7 {
  font-size: 0.75rem !important;
}

.profile-main-content {
  flex: 1;
  padding-left: 1rem;
  transition: all 0.3s ease;
  overflow: hidden;
}

.secondary-profile-bar {
  background-color: #f8f9fa;
  border-radius: 0.25rem 0.25rem 0 0;
  box-shadow: 0 -1px 0 rgba(0, 0, 0, 0.05);
}

.secondary-profile-bar select {
  background-color: white;
  border-color: #dee2e6;
}

.secondary-profile-bar .btn-outline-danger {
  border-color: #dc3545;
  color: #dc3545;
  padding: 0.25rem 0.5rem;
  font-size: 0.875rem;
}

.secondary-profile-bar .btn-outline-danger:hover {
  background-color: #dc3545;
  color: white;
}

.toggle-switch-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
}

.toggle-switch {
  display: flex;
  border-radius: 0.5rem;
  overflow: hidden;
  cursor: pointer;
  height: 30px;
  width: 100%;
  max-width: 200px;
  background-color: #f0f2f5;
  transition: background-color 0.3s ease;
  position: relative;
  border: 1px solid #dfe2e6;
  padding: 2px;
}

.toggle-switch::before {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  height: calc(100% - 4px);
  width: calc(50% - 4px);
  background-color: #5e64ff;
  border-radius: 0.25rem;
  transition: transform 0.3s ease;
  z-index: 1;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  transform: translateX(0);
}

.toggle-switch:has(.toggle-button:last-child.active)::before {
  transform: translateX(calc(100% + 4px));
}

.toggle-button {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 0.75rem;
  font-weight: 600;
  color: #495057;
  background-color: transparent;
  border: none;
  transition: color 0.3s ease;
  position: relative;
  z-index: 2;

  &.active {
    color: #fff;
  }
}

@keyframes pulse-highlight {
  0% {
    background-color: #f8f9fa;
  }
  50% {
    background-color: rgba(0, 123, 255, 0.15);
  }
  100% {
    background-color: #f8f9fa;
  }
}

@keyframes pulse-highlight-error {
  0% {
    background-color: #f8f9fa;
  }
  50% {
    background-color: rgba(220, 53, 69, 0.15);
  }
  100% {
    background-color: #f8f9fa;
  }
}

/* Modal styles */

.selected-profile-info {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}


.badge {
  font-weight: 500;
}

.secondary-profile-placeholder {
  display: flex;
  align-items: center;
}

/* Disabled features styling */
.disabled-feature {
  border-right: 2px solid #ffc107 !important;
  border-bottom-right-radius: 4px;
  border-top-right-radius: 4px;
  position: relative;
}

.disabled-feature::after {
  content: '';
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  width: 2px;
  background-color: #ffc107;
  box-shadow: 0 0 8px rgba(255, 193, 7, 0.3);
}

</style>
