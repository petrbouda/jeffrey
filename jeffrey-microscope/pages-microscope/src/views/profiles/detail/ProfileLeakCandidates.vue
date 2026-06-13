<template>
  <div class="leak-container">
    <LoadingState v-if="loading" message="Loading leak-candidate data..." />
    <ErrorState v-else-if="error" message="Failed to load leak-candidate data" />

    <div v-else>
      <PageHeader
        title="Memory Leak Candidates"
        description="Live objects flagged by JFR's old-object sampler — long-lived objects that survived collection and may indicate a leak"
        icon="bi-bug"
      />

      <div class="mb-4">
        <StatsTable :metrics="metricsData" />
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <div v-show="activeTab === 'candidates'">
      <EmptyState
        v-if="candidates.length === 0"
        icon="bi-bug"
        title="No leak candidates recorded"
        description="This recording has no jdk.OldObjectSample events. Enable the old-object sampler (-XX:+UnlockDiagnosticVMOptions or the 'profile' JFR settings) to capture potential leaks."
      />
      <DataTable v-else>
        <template #toolbar>
          <TableToolbar v-model="candidatesView.query" search-placeholder="Filter classes...">
            <span class="toolbar-info">Leak candidates</span>
            <template #filters>
              <Badge key-label="Total" :value="candidatesView.matchCount" variant="secondary" size="s" borderless />
            </template>
          </TableToolbar>
        </template>
        <thead>
          <tr>
            <th class="rank-col">#</th>
            <th>Leaked Class</th>
            <th class="text-end">Size</th>
            <th class="text-end">Age</th>
            <th class="text-end">Array Elements</th>
            <th class="text-end">Heap at Sample</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(candidate, index) in candidatesView.visible" :key="index">
            <td class="text-muted">{{ index + 1 }}</td>
            <td class="class-cell" :title="candidate.className ?? ''">
              <ClassNameDisplay v-if="candidate.className" :class-name="candidate.className" />
              <span v-else class="text-muted">—</span>
            </td>
            <td class="text-end">{{ FormattingService.formatBytes(candidate.objectSizeBytes) }}</td>
            <td class="text-end">{{ FormattingService.formatDuration2Units(candidate.objectAgeNanos) }}</td>
            <td class="text-end">
              {{ candidate.arrayElements > 0 ? FormattingService.formatNumber(candidate.arrayElements) : '—' }}
            </td>
            <td class="text-end">{{ FormattingService.formatBytes(candidate.lastKnownHeapUsageBytes) }}</td>
          </tr>
        </tbody>
        <template #footer>
          <TableShowMore
            :shown="candidatesView.visible.length"
            :match-count="candidatesView.matchCount"
            :total="candidatesView.total"
            :expanded="candidatesView.expanded"
            :page-size="candidatesView.pageSize"
            @toggle="candidatesView.toggle"
          />
        </template>
      </DataTable>
      </div>

      <!-- How It Works Tab -->
      <div v-show="activeTab === 'about'">
        <AboutPanel
          icon="bi-question-circle"
          title="Understanding Leak Candidates"
          subtitle="How JFR's old-object sampler finds objects that should have been collected"
        >
          <AboutCallout variant="intro">
            <p>
              JFR has a built-in, low-overhead leak detector: the <strong>old-object sampler</strong>.
              Rather than dumping the whole heap, it tags a small sample of allocations and watches
              which ones <em>keep surviving</em> garbage collections. The objects still alive at the end
              of the recording — especially old, large ones — are your leak suspects, complete with the
              stack trace that allocated them.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-gear" title="How the Old-Object Sampler Works">
            <FeatureGrid>
              <FeatureCard icon="bi-tag" variant="info" title="1. Sample at allocation">
                A fraction of allocations are tagged and placed in a fixed-size queue, along with their
                allocation stack trace and timestamp. Sampling keeps the overhead tiny.
              </FeatureCard>
              <FeatureCard icon="bi-arrow-repeat" variant="warning" title="2. Track across GCs">
                Each surviving GC, the sampler checks which tagged objects are still reachable. An object
                that outlives many collections is, by definition, long-lived — and possibly leaked.
              </FeatureCard>
              <FeatureCard icon="bi-flag" variant="danger" title="3. Report survivors">
                At dump time the still-live samples are emitted with their size, age, array length and the
                heap usage when sampled — the rows in this table.
              </FeatureCard>
              <FeatureCard icon="bi-dice-5" variant="purple" title="Statistical, not exhaustive">
                Because it samples, absence here doesn't prove no leak, and one row represents a class of
                similar objects. It points you at <em>what</em> to investigate, not an exact inventory.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutSection icon="bi-table" title="Reading the Columns">
            <FeatureGrid>
              <FeatureCard icon="bi-rulers" variant="primary" title="Size">
                Shallow size of the sampled object. Large entries (big arrays, caches) are the
                highest-impact leaks.
              </FeatureCard>
              <FeatureCard icon="bi-clock-history" variant="warning" title="Age">
                How long the object has been alive. The older a sampled object, the more suspicious — a
                true leak only grows older.
              </FeatureCard>
              <FeatureCard icon="bi-list-ol" variant="info" title="Array Elements">
                For arrays, the element count — an ever-growing backing array (a list/map that's never
                cleared) is a textbook leak.
              </FeatureCard>
              <FeatureCard icon="bi-graph-up" variant="neutral" title="Heap at Sample">
                Heap usage when the object was sampled — context for whether it appeared under memory
                pressure.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutCallout variant="tip" title="Confirming a leak" icon="bi-lightbulb-fill">
            A class with many old, growing instances here is the signature. To find <em>what holds them
            alive</em>, take a heap dump and use the dominator tree / GC-root paths on the Heap Dump
            pages — the old-object sampler tells you the suspect; the dump tells you the retainer.
          </AboutCallout>

          <AboutSection icon="bi-broadcast" title="How JFR Emits This">
            <p>
              <code>jdk.OldObjectSample</code> carries the leaked object's type, <code>objectSize</code>,
              <code>objectAge</code>, <code>arrayElements</code> and <code>lastKnownHeapUsage</code>, plus
              the allocation stack and (when resolvable) a path to a GC root.
            </p>
            <p>
              It's <strong>off by default</strong> because tracking surviving samples adds some cost.
              Enable it with the <code>profile</code> settings (which set an old-object queue) or
              <code>+jdk.OldObjectSample#enabled=true</code> — otherwise this page shows an empty state.
            </p>
          </AboutSection>
        </AboutPanel>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import PageHeader from '@/components/layout/PageHeader.vue';
import StatsTable from '@/components/StatsTable.vue';
import TabBar from '@/components/TabBar.vue';
import type { TabBarItem } from '@/components/TabBar.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import TableShowMore from '@/components/table/TableShowMore.vue';
import ClassNameDisplay from '@/components/heap/ClassNameDisplay.vue';
import Badge from '@/components/Badge.vue';
import EmptyState from '@/components/EmptyState.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import FormattingService from '@/services/FormattingService';
import ProfileLeakCandidatesClient from '@/services/api/ProfileLeakCandidatesClient';
import type { LeakCandidate, LeakOverview } from '@/services/api/model/LeakModels';
import { useTableView } from '@/composables/useTableView';

const route = useRoute();

const loading = ref(true);
const error = ref(false);

const overview = ref<LeakOverview>();
const candidates = ref<LeakCandidate[]>([]);

const activeTab = ref('candidates');
const tabs = computed<TabBarItem[]>(() => [
  {
    id: 'candidates',
    label: 'Leak Candidates',
    icon: 'bug',
    badge: candidates.value.length || undefined
  },
  { id: 'about', label: 'How It Works', icon: 'book' }
]);

const candidatesView = useTableView<LeakCandidate>(candidates, {
  searchableText: (c) => c.className ?? ''
});

const metricsData = computed(() => {
  if (!overview.value) {
    return [];
  }
  const o = overview.value;
  return [
    {
      icon: 'bug',
      title: 'Leak Candidates',
      value: FormattingService.formatNumber(o.candidateCount),
      variant: 'highlight' as const
    },
    {
      icon: 'box-arrow-up',
      title: 'Largest',
      value: FormattingService.formatBytes(o.largestBytes),
      variant: 'danger' as const
    },
    {
      icon: 'boxes',
      title: 'Total Retained',
      value: FormattingService.formatBytes(o.totalBytes),
      variant: 'warning' as const
    },
    {
      icon: 'clock-history',
      title: 'Oldest',
      value: FormattingService.formatDuration2Units(o.oldestAgeNanos),
      variant: 'info' as const
    }
  ];
});

onMounted(async () => {
  try {
    const profileId = route.params.profileId as string;
    const client = new ProfileLeakCandidatesClient(profileId);

    const [overviewResult, candidatesResult] = await Promise.all([
      client.getOverview(),
      client.getCandidates()
    ]);

    overview.value = overviewResult;
    candidates.value = candidatesResult;

    loading.value = false;
  } catch (e) {
    console.error('Failed to load leak-candidate data:', e);
    error.value = true;
    loading.value = false;
  }
});
</script>

<style scoped>
.leak-container {
  width: 100%;
  color: var(--color-text);
}

.toolbar-info {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--color-text);
}

.rank-col {
  width: 48px;
}

.class-cell {
  max-width: 520px;
}
</style>
