<template>
  <LoadingState v-if="loading" message="Loading GC roots..." />

  <div v-else-if="!heapExists" class="no-heap-dump">
    <div class="alert alert-info d-flex align-items-center">
      <i class="bi bi-info-circle me-3 fs-4"></i>
      <div>
        <h6 class="mb-1">No Heap Dump Available</h6>
        <p class="mb-0 small">
          No heap dump file (.hprof) was found for this profile. To analyze heap memory, generate a
          heap dump and add it to the recording folder.
        </p>
      </div>
    </div>
  </div>

  <HeapDumpNotInitialized
    v-else-if="!cacheReady"
    icon="diagram-3"
    message="The heap dump needs to be initialized before you can view GC roots. This process builds indexes and prepares the data for analysis."
  />

  <ErrorState v-else-if="error" :message="error" />

  <div v-else>
    <PageHeader
      title="GC Roots"
      description="Garbage collection root objects that prevent memory from being collected"
      icon="bi-diagram-3"
    />

    <div v-if="gcRootData">
      <!-- Summary Metrics -->
      <StatsTable :metrics="summaryMetrics" class="mb-4" />

      <!-- Tabbed Content Section -->
      <TabBar v-model="activeTab" :tabs="analysisTabs" class="mb-3" />

      <!-- Overview Tab -->
      <div v-show="activeTab === 'overview'">
        <DualPanel left-title="Root Type Distribution">
          <template #left>
            <DonutWithLegend
              :data="rootTypeChartData"
              :tooltip-formatter="(val: number) => FormattingService.formatNumber(val) + ' roots'"
            />
          </template>
        </DualPanel>
      </div>

      <!-- Top Retainers -->
      <div v-show="activeTab === 'top-retainers'">
        <GCRootsTopRetainers
          v-if="client"
          :client="client"
          :total-heap-bytes="totalHeapBytes"
          :active="activeTab === 'top-retainers'"
          @show-referrers="openTreeModal($event, 'REFERRERS')"
          @show-reachables="openTreeModal($event, 'REACHABLES')"
          @show-g-c-root-path="openGCRootPathModal"
          @show-instance-detail="openInstanceDetailPanel"
        />
      </div>

      <!-- Roots by Class -->
      <div v-show="activeTab === 'by-class'">
        <GCRootsByClass v-if="client" :client="client" :active="activeTab === 'by-class'" />
      </div>

      <!-- Roots by ClassLoader -->
      <div v-show="activeTab === 'by-classloader'">
        <GCRootsByClassLoader
          v-if="client"
          :client="client"
          :active="activeTab === 'by-classloader'"
        />
      </div>

      <!-- Native / JNI -->
      <div v-show="activeTab === 'native'">
        <GCRootsNativeAudit
          v-if="client"
          :client="client"
          :active="activeTab === 'native'"
          @show-referrers="openTreeModal($event, 'REFERRERS')"
          @show-reachables="openTreeModal($event, 'REACHABLES')"
          @show-g-c-root-path="openGCRootPathModal"
          @show-instance-detail="openInstanceDetailPanel"
        />
      </div>

      <!-- Leak Hints -->
      <div v-show="activeTab === 'leak-hints'">
        <GCRootsLeakHints v-if="client" :client="client" :active="activeTab === 'leak-hints'" />
      </div>

      <!-- Instance Tree Modal -->
      <InstanceTreeModal
        :show="treeModalVisible"
        :object-id="treeModalObjectId"
        :initial-mode="treeModalMode"
        :profile-id="profileId"
        @update:show="treeModalVisible = $event"
      />

      <!-- Instance Details Side Panel -->
      <InstanceDetailPanel
        v-if="client"
        :is-open="detailPanelOpen"
        :object-id="detailPanelObjectId"
        :client="client"
        @close="detailPanelOpen = false"
        @navigate="detailPanelObjectId = $event"
      />

      <!-- About Tab -->
      <div v-show="activeTab === 'about'">
        <AboutPanel
          icon="bi-question-circle"
          title="Understanding GC Roots"
          subtitle="What prevents objects from being garbage collected"
        >
          <AboutCallout variant="intro">
            <p>
              GC roots are the starting points for garbage collection. The garbage collector traces
              object references starting from these roots to determine which objects are reachable
              and should be kept alive. Objects that cannot be traced from any GC root are
              considered unreachable and eligible for collection.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-diagram-3" title="GC Root Types">
            <FeatureGrid>
              <FeatureCard icon="bi-globe" variant="purple" title="JNI Global">
                Objects referenced by JNI global references. These are native code references that
                persist across JNI calls and must be explicitly deleted.
              </FeatureCard>
              <FeatureCard icon="bi-geo-alt" variant="danger" title="JNI Local">
                Objects referenced by JNI local references within a native method's stack frame.
                Automatically cleaned up when the native method returns.
              </FeatureCard>
              <FeatureCard icon="bi-layers" variant="info" title="Java Frame">
                Objects referenced from Java stack frames, including local variables and method
                parameters in active method calls.
              </FeatureCard>
              <FeatureCard icon="bi-cpu" variant="success" title="Native Stack">
                Objects referenced from native code execution stacks. These are JNI references held
                on the native (C/C++) call stack.
              </FeatureCard>
              <FeatureCard icon="bi-pin-angle" variant="warning" title="Sticky Class">
                System classes that are never unloaded, such as <code>java.lang.*</code>, primitive
                types, and other bootstrap classes loaded by the JVM.
              </FeatureCard>
              <FeatureCard icon="bi-lock" variant="teal" title="Thread Block">
                Objects held as references within thread synchronization blocks. These are objects
                referenced by threads waiting on monitors.
              </FeatureCard>
              <FeatureCard icon="bi-shield-lock" variant="danger" title="Monitor Used">
                Objects actively being used as monitor locks (<code>synchronized</code>). These
                objects are held by threads that have acquired their intrinsic lock.
              </FeatureCard>
              <FeatureCard icon="bi-person-badge" variant="primary" title="Thread Object">
                Thread objects themselves. Every active Java thread is a GC root, keeping itself and
                all objects it references alive.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutSection icon="bi-lightning-charge" title="Why It Matters">
            <div class="benefits-list">
              <div class="benefit-item">
                <i class="bi bi-check-circle-fill text-success"></i>
                <span>Identify memory leaks by finding unexpected retention paths from GC roots</span>
              </div>
              <div class="benefit-item">
                <i class="bi bi-check-circle-fill text-success"></i>
                <span>Understand which objects cannot be collected and why they are kept alive</span>
              </div>
              <div class="benefit-item">
                <i class="bi bi-check-circle-fill text-success"></i>
                <span>Analyze thread state and synchronization issues through monitor roots</span>
              </div>
              <div class="benefit-item">
                <i class="bi bi-check-circle-fill text-success"></i>
                <span>Debug native code memory issues through JNI global and local references</span>
              </div>
            </div>
          </AboutSection>

          <AboutCallout variant="note" title="Investigating Memory Leaks?" icon="bi-lightbulb-fill">
            <p>
              Look for objects with unexpected GC roots. Common culprits include static fields
              (via Sticky Class), thread locals (via Thread Object), and JNI global references
              that weren't properly cleaned up. Use the dominator tree and shortest path to GC
              root analysis to trace retention paths.
            </p>
          </AboutCallout>
        </AboutPanel>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import PageHeader from '@shared/components/layout/PageHeader.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import HeapDumpNotInitialized from '@/components/HeapDumpNotInitialized.vue';
import TabBar from '@shared/components/TabBar.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import DualPanel from '@shared/components/DualPanel.vue';
import DonutWithLegend from '@shared/components/DonutWithLegend.vue';
import type { DonutChartData } from '@shared/components/DonutWithLegend.vue';
import InstanceTreeModal from '@/components/heap/InstanceTreeModal.vue';
import InstanceDetailPanel from '@/components/heap/InstanceDetailPanel.vue';
import GCRootsTopRetainers from '@/components/gcroots/GCRootsTopRetainers.vue';
import GCRootsByClass from '@/components/gcroots/GCRootsByClass.vue';
import GCRootsByClassLoader from '@/components/gcroots/GCRootsByClassLoader.vue';
import GCRootsNativeAudit from '@/components/gcroots/GCRootsNativeAudit.vue';
import GCRootsLeakHints from '@/components/gcroots/GCRootsLeakHints.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import GCRootSummary from '@/services/api/model/GCRootSummary';
import FormattingService from '@shared/services/FormattingService';

const route = useRoute();
const router = useRouter();

const profileId = route.params.profileId as string;
const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);
const gcRootData = ref<GCRootSummary | null>(null);
const totalHeapBytes = ref(0);

const client = shallowRef<HeapDumpClient | null>(null);

// Tree modal state (referrers / reachables)
const treeModalVisible = ref(false);
const treeModalObjectId = ref(0);
const treeModalMode = ref<'REFERRERS' | 'REACHABLES'>('REFERRERS');

// Instance detail side-panel state
const detailPanelOpen = ref(false);
const detailPanelObjectId = ref<number | null>(null);

const openTreeModal = (objectId: number, mode: 'REFERRERS' | 'REACHABLES') => {
  treeModalObjectId.value = objectId;
  treeModalMode.value = mode;
  treeModalVisible.value = true;
};

const openGCRootPathModal = (objectId: number) => {
  router.push(`/profiles/${profileId}/heap-dump/gc-root-path?objectId=${objectId}`);
};

const openInstanceDetailPanel = (objectId: number) => {
  detailPanelObjectId.value = objectId;
  detailPanelOpen.value = true;
};

const rootTypeColors = [
  '#4285F4',
  '#EA4335',
  '#FBBC05',
  '#34A853',
  '#9C27B0',
  '#FF5722',
  '#00BCD4',
  '#795548',
  '#607D8B',
  '#E91E63'
];

const analysisTabs = [
  { id: 'overview', label: 'Overview', icon: 'pie-chart' },
  { id: 'top-retainers', label: 'Top Retainers', icon: 'trophy' },
  { id: 'by-class', label: 'By Class', icon: 'diagram-2' },
  { id: 'by-classloader', label: 'By ClassLoader', icon: 'stack' },
  { id: 'native', label: 'Native / JNI', icon: 'cpu' },
  { id: 'leak-hints', label: 'Leak Hints', icon: 'search' },
  { id: 'about', label: 'How It Works', icon: 'info-circle' }
];
const activeTab = ref(analysisTabs[0].id);

// Computed metrics for StatsTable
const summaryMetrics = computed(() => {
  if (!gcRootData.value) return [];
  return [
    {
      icon: 'diagram-3',
      title: 'Total GC Roots',
      value: FormattingService.formatNumber(gcRootData.value.totalRoots),
      variant: 'highlight' as const
    },
    {
      icon: 'tags',
      title: 'Root Types',
      value: Object.keys(gcRootData.value.rootsByType).length.toString(),
      variant: 'info' as const
    }
  ];
});

const chartEntries = computed(() => {
  if (!gcRootData.value) return [];
  return Object.entries(gcRootData.value.rootsByType).sort((a, b) => b[1] - a[1]);
});

const rootTypeChartData = computed<DonutChartData>(() => ({
  series: chartEntries.value.map(([, count]) => count),
  labels: chartEntries.value.map(([type]) => formatRootType(type)),
  colors: chartEntries.value.map(([type]) => getRootTypeColor(type)),
  totalValue: FormattingService.formatNumber(gcRootData.value?.totalRoots ?? 0),
  legendItems: chartEntries.value.map(([type, count]) => ({
    color: getRootTypeColor(type),
    label: formatRootType(type),
    value: FormattingService.formatNumber(count)
  }))
}));

const formatRootType = (type: string): string => {
  return type
    .replace(/_/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .split(' ')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ');
};

const getRootTypeColor = (type: string): string => {
  if (!gcRootData.value) return rootTypeColors[0];
  const types = Object.keys(gcRootData.value.rootsByType);
  const index = types.indexOf(type);
  return rootTypeColors[index % rootTypeColors.length];
};

const scrollToTop = () => {
  const workspaceContent = document.querySelector('.workspace-content');
  if (workspaceContent) {
    workspaceContent.scrollTop = 0;
  }
};

const loadData = async () => {
  try {
    loading.value = true;
    error.value = null;

    client.value = new HeapDumpClient(profileId);

    heapExists.value = await client.value.exists();

    if (!heapExists.value) {
      loading.value = false;
      return;
    }

    cacheReady.value = await client.value.isCacheReady();

    if (!cacheReady.value) {
      loading.value = false;
      return;
    }

    // Pull GC summary + heap summary in parallel — totalBytes powers the
    // "% of heap" column in the Top Retainers tab.
    const [gcSummary, heapSummary] = await Promise.all([
      client.value.getGCRoots(),
      client.value.getSummary()
    ]);
    gcRootData.value = gcSummary;
    totalHeapBytes.value = heapSummary.totalBytes;
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load GC roots';
    console.error('Error loading GC roots:', err);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  scrollToTop();
  loadData();
});
</script>

<style scoped>
.no-heap-dump {
  padding: 2rem;
}

/* About Tab Styles */
.benefits-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
}

.benefit-item {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  font-size: 0.85rem;
  color: var(--color-text);
  padding: 0.5rem 0;
}

.benefit-item i {
  flex-shrink: 0;
  margin-top: 0.1rem;
}
</style>
