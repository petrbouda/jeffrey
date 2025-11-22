<template>
  <!-- Feature Collection Navigation -->
  <div class="feature-collection-nav normal-nav content-aligned">
    <div class="nav-container">
      <div class="nav-pill"
           :class="{ 'active': selectedMode === 'JDK' }"
           @click="selectMode('JDK')"
           title="Standard JDK runtime analysis features">
        <div class="pill-content">
          <div class="title-row">
            <i class="bi bi-cpu"></i>
            <span>JDK Runtime</span>
          </div>
          <small>Core JVM performance metrics</small>
        </div>
      </div>
      <div class="nav-pill"
           :class="{ 'active': selectedMode === 'Custom' }"
           @click="selectMode('Custom')"
           title="Custom application-specific analysis features">
        <div class="pill-content">
          <div class="title-row">
            <i class="bi bi-layers"></i>
            <span>Application</span>
          </div>
          <small>Application-specific analysis</small>
        </div>
      </div>
    </div>
  </div>

  <div class="d-flex w-100">
    <!-- Sidebar Menu -->
    <div class="profile-sidebar" :class="{ 'collapsed': sidebarCollapsed }">
      <div class="sidebar" :class="{ 'collapsed': sidebarCollapsed }">
        <div class="edge-toggle" @click="toggleSidebar">
          <div class="edge-toggle-line">
            <i class="bi" :class="sidebarCollapsed ? 'bi-chevron-right' : 'bi-chevron-left'"></i>
          </div>
        </div>

        <div class="scrollbar" style="height: 100%;">
          <!-- Profile Header -->
          <div class="p-2"/>

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
                    <Badge v-if="warningCount > 0" :value="warningCount.toString()" variant="danger" size="xs"
                           class="ms-auto"/>
                  </router-link>
                  <router-link
                      :to="`/workspaces/${workspaceId}/projects/${projectId}/profiles/${profileId}/auto-analysis`"
                      class="nav-item"
                      active-class="active"
                  >
                    <i class="bi bi-robot"></i>
                    <span>Auto Analysis</span>
                    <Badge v-if="autoAnalysisWarningCount > 0" :value="autoAnalysisWarningCount.toString()"
                           variant="danger" size="xs" class="ms-auto"/>
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
                      <i class="bi bi-chevron-right submenu-arrow"
                         :class="{ 'rotated': heapMemorySubmenuExpanded }"></i>
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
                      <i class="bi bi-chevron-right submenu-arrow"
                         :class="{ 'rotated': httpServerSubmenuExpanded }"></i>
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
                      <i class="bi bi-chevron-right submenu-arrow"
                         :class="{ 'rotated': httpClientSubmenuExpanded }"></i>
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
                         :class="{ 'active': $route.path.includes('/application/jdbc/'), 'expanded': jdbcSubmenuExpanded, 'disabled-feature': isFeatureDisabled('jdbc-statements') }">
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

      <!-- Compact Differential Analysis Bar -->
      <div class="compact-comparison-bar mb-3" v-if="!sidebarCollapsed">
        <div class="comparison-cards">
          <!-- Primary Profile -->
          <div class="compact-card primary">
            <div class="card-info">
              <div class="card-title">
                <span>{{ profile?.name || 'Loading...' }}</span>
                <span class="card-label">PRIMARY</span>
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
          <div class="compact-card secondary" :class="{ 'empty': !secondaryProfile }">
            <div class="card-info">
              <div class="card-title">
                <span>{{ secondaryProfile?.name || 'Select Secondary Profile' }}</span>
                <span v-if="secondaryProfile" class="card-label">SECONDARY</span>
              </div>
              <div class="card-meta">
                <span v-if="!secondaryProfile" class="meta-item help-text">Enable differential analysis</span>
                <template v-else>
                  <span class="meta-item">
                    <i class="bi bi-file-text me-1"></i>{{ selectedSecondaryProfileId }}
                  </span>
                  <div class="card-actions">
                    <button class="action-btn" @click="openSecondaryProfileModal" title="Change profile">
                      <i class="bi bi-arrow-repeat"></i>
                    </button>
                    <button class="action-btn remove" @click="clearSecondaryProfile" title="Remove profile">
                      <i class="bi bi-x-lg"></i>
                    </button>
                  </div>
                </template>
              </div>
            </div>
            <button v-if="!secondaryProfile" class="select-btn" @click="openSecondaryProfileModal">
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
import {useNavigation} from '@/composables/useNavigation';
import ToastService from '@/services/ToastService';
import Profile from "@/services/model/Profile.ts";
import ProjectProfileClient from "@/services/ProjectProfileClient.ts";
import ProfileInfo from "@/services/project/model/ProfileInfo.ts";
import SecondaryProfileService from "@/services/SecondaryProfileService.ts";
import Badge from '@/components/Badge.vue';
import SecondaryProfileSelectionModal from '@/components/SecondaryProfileSelectionModal.vue';
import MessageBus from "@/services/MessageBus.ts";
import GuardianClient from "@/services/guardian/GuardianClient";
import AutoAnalysisClient from "@/services/AutoAnalysisClient";
import ProfileFeaturesClient from "@/services/profile/features/ProfileFeaturesClient";
import FeatureType from "@/services/profile/features/FeatureType";

const route = useRoute();
const router = useRouter();
const {workspaceId, projectId, generateProjectUrl} = useNavigation();
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
  if (newPath.includes('/application/jdbc/')) {
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
}, {immediate: true});

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
};

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value;
  MessageBus.emit(MessageBus.SIDEBAR_CHANGED, null);
};

const selectMode = (mode: 'JDK' | 'Custom') => {
  selectedMode.value = mode;
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
    ToastService.warn('No Secondary Profile', 'Please select a secondary profile for comparison');

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

.edge-toggle {
  position: absolute;
  right: -8px;
  top: 0;
  bottom: 0;
  width: 20px;
  cursor: pointer;
  opacity: 0.3;
  transition: opacity 0.2s ease;
  z-index: 10;

  &:hover {
    opacity: 1;
  }

  &:hover .edge-toggle-line {
    background: linear-gradient(135deg, #5e64ff, #4338ca);
    width: 7px;
    box-shadow: -2px 0 8px rgba(94, 100, 255, 0.2);
  }

  &:hover .edge-toggle-line i {
    opacity: 1;
  }
}

.edge-toggle-line {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  width: 5px;
  height: 90px;
  background: linear-gradient(135deg, #f3f4f6, #e5e7eb);
  border-radius: 4px 0 0 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  box-shadow: -1px 0 3px rgba(0, 0, 0, 0.05);
  pointer-events: none;

  i {
    position: absolute;
    font-size: 0.8rem;
    color: white;
    opacity: 0;
    transition: opacity 0.2s ease;
  }
}

/* Show edge toggle on sidebar hover */
.profile-sidebar:hover .edge-toggle {
  opacity: 0.7;
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
  background: #fff;
  border: 1px solid #e9ecef;
  border-radius: 6px;
  flex: 1;
  min-width: 0;
  transition: all 0.2s ease;
}

.compact-card.primary {
  height: auto;
}

.compact-card.secondary {
  height: 100%;
}

.compact-card:hover {
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}

.compact-card.primary {
  border-left: 3px solid #ffc107;
}

.compact-card.secondary:not(.empty) {
  border-left: 3px solid #28a745;
}

.compact-card.secondary.empty {
  border-left: 3px dashed #6c757d;
  background: #f8f9fa;
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
  color: #212529;
  margin-bottom: 0.25rem;
  display: flex;
  align-items: center;
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
  color: #6c757d;
}

.meta-item {
  display: flex;
  align-items: center;
  white-space: nowrap;
}

.card-label {
  font-weight: 600;
  font-size: 0.6rem;
  letter-spacing: 0.5px;
  color: #495057;
  padding: 0.125rem 0.375rem;
  background: #f8f9fa;
  border-radius: 3px;
  margin-left: 0.5rem;
  display: inline-flex;
  align-items: center;
  vertical-align: middle;
}

.vs-divider {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
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
  border: 1px solid #e9ecef;
  background: white;
  border-radius: 4px;
  font-size: 0.7rem;
  cursor: pointer;
  transition: all 0.2s ease;
  color: #495057;
}

.action-btn:hover {
  background: #f8f9fa;
  border-color: #dee2e6;
  transform: translateY(-1px);
}

.action-btn.remove {
  color: #dc3545;
}

.action-btn.remove:hover {
  background: #dc3545;
  border-color: #dc3545;
  color: white;
}

.select-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 2px dashed #6c757d;
  background: transparent;
  border-radius: 6px;
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.2s ease;
  color: #6c757d;
  margin-left: auto;
  align-self: center;
}

.select-btn:hover {
  border-color: #5e64ff;
  color: #5e64ff;
  background: rgba(94, 100, 255, 0.05);
  transform: translateY(-1px);
}

.help-text {
  color: #6c757d;
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
  background: linear-gradient(135deg, #f8f9fb, #ffffff);
  border-bottom: 1px solid #e9ecef;
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
  color: #374151;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.mode-subtitle {
  display: block;
  font-size: 0.65rem;
  color: #6b7280;
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
  background: linear-gradient(135deg, #f1f5f9, #e2e8f0);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  border: 2px solid #e2e8f0;
  padding: 3px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04),
  inset 0 1px 0 rgba(255, 255, 255, 0.8);

  &:hover {
    border-color: #5e64ff;
    box-shadow: 0 4px 12px rgba(94, 100, 255, 0.1),
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
  background: linear-gradient(135deg, #5e64ff, #4338ca);
  border-radius: 7px;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 1;
  box-shadow: 0 3px 8px rgba(94, 100, 255, 0.25),
  0 1px 2px rgba(0, 0, 0, 0.1),
  inset 0 1px 0 rgba(255, 255, 255, 0.2);
  transform: translateX(0);
}

.toggle-switch:has(.custom-mode.active)::before {
  transform: translateX(calc(100% + 6px));
  background: linear-gradient(135deg, #059669, #047857);
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
  color: #64748b;
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
    color: #475569;
    transform: translateY(-1px);

    .mode-icon {
      transform: scale(1.1);
    }
  }

  &.active {
    color: #ffffff;
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
    outline: 2px solid #5e64ff;
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
  border-bottom: 1px solid #e9ecef;
  margin-bottom: 1rem;
}

.feature-collection-nav.content-aligned {
  position: relative;
  z-index: 10;
  margin-bottom: 0.75rem;
  width: 100%;
  margin-left: 0;
  margin-right: 0;
  border-top: 1px solid #e9ecef;
  border-bottom: 1px solid #e9ecef;
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
  color: #718096;
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
    color: #6b7280;
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
    color: #4a5568;

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
    color: #5e64ff;

    i {
      transform: translateY(-2px);
    }

    span {
      opacity: 1;
    }

    small {
      opacity: 0.9;
      color: #5e64ff;
    }

    &::after {
      background-color: #5e64ff;
    }
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
