<template>
  <div class="modules-container">
    <LoadingState v-if="loading" message="Loading module graph..." />
    <ErrorState v-else-if="error" message="Failed to load module graph" />

    <div v-else>
      <PageHeader
        title="Modules"
        description="The startup module graph from jdk.ModuleRequire (dependencies) and jdk.ModuleExport (package exports) — confirm what the app reads and what's exported, and to whom"
        icon="bi-boxes"
      />

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- Requires -->
      <div v-show="activeTab === 'requires'">
        <ChartDescription
          shows="Module dependencies from jdk.ModuleRequire — which module reads which, captured at JVM startup."
          use-case="Audit the runtime module set or chase module-resolution failures — confirm a module actually reads the one it needs."
        />
        <EmptyState
          v-if="moduleRequires.length === 0"
          icon="bi-box"
          title="No module dependencies recorded"
          description="This recording has no jdk.ModuleRequire events."
        />
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar
              v-model="moduleRequiresView.query"
              search-placeholder="Filter dependencies..."
            >
              <span class="toolbar-info">Module dependencies</span>
              <template #filters>
                <Badge
                  key-label="Edges"
                  :value="moduleRequiresView.matchCount"
                  variant="secondary"
                  size="s"
                  borderless
                />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Source Module</th>
              <th>Requires</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(edge, index) in moduleRequiresView.visible" :key="index">
              <td>
                <code>{{ edge.source ?? 'unnamed' }}</code>
              </td>
              <td>
                <code>{{ edge.required ?? 'unnamed' }}</code>
              </td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="moduleRequiresView.visible.length"
              :match-count="moduleRequiresView.matchCount"
              :total="moduleRequiresView.total"
              :expanded="moduleRequiresView.expanded"
              :page-size="moduleRequiresView.pageSize"
              @toggle="moduleRequiresView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- Exports -->
      <div v-show="activeTab === 'exports'">
        <ChartDescription
          shows="Package exports from jdk.ModuleExport — which package is exported and to which target module (or unqualified, i.e. to everyone)."
          use-case="Chase IllegalAccess errors or audit the API surface — confirm a package is exported to the module trying to use it."
        />
        <EmptyState
          v-if="moduleExports.length === 0"
          icon="bi-box-arrow-up-right"
          title="No package exports recorded"
          description="This recording has no jdk.ModuleExport events."
        />
        <DataTable v-else>
          <template #toolbar>
            <TableToolbar v-model="moduleExportsView.query" search-placeholder="Filter exports...">
              <span class="toolbar-info">Package exports</span>
              <template #filters>
                <Badge
                  key-label="Exports"
                  :value="moduleExportsView.matchCount"
                  variant="secondary"
                  size="s"
                  borderless
                />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Exported Package</th>
              <th>Target</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(exp, index) in moduleExportsView.visible" :key="index">
              <td>
                <code>{{ exp.packageName ?? '—' }}</code>
              </td>
              <td>
                <Badge
                  v-if="!exp.targetModule"
                  value="unqualified"
                  variant="info"
                  size="xs"
                  borderless
                />
                <code v-else>{{ exp.targetModule }}</code>
              </td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="moduleExportsView.visible.length"
              :match-count="moduleExportsView.matchCount"
              :total="moduleExportsView.total"
              :expanded="moduleExportsView.expanded"
              :page-size="moduleExportsView.pageSize"
              @toggle="moduleExportsView.toggle"
            />
          </template>
        </DataTable>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import PageHeader from '@/components/layout/PageHeader.vue';
import TabBar from '@/components/TabBar.vue';
import type { TabBarItem } from '@/components/TabBar.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import TableShowMore from '@/components/table/TableShowMore.vue';
import ChartDescription from '@/components/ChartDescription.vue';
import Badge from '@/components/Badge.vue';
import EmptyState from '@/components/EmptyState.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import ProfileSystemClient from '@/services/api/ProfileSystemClient';
import type { ModuleEdge, ModuleExport } from '@/services/api/model/SystemModels';
import { useTableView } from '@/composables/useTableView';

const route = useRoute();

const loading = ref(true);
const error = ref(false);

const moduleRequires = ref<ModuleEdge[]>([]);
const moduleExports = ref<ModuleExport[]>([]);

const moduleRequiresView = useTableView<ModuleEdge>(moduleRequires, {
  searchableText: r => `${r.source ?? ''} ${r.required ?? ''}`
});
const moduleExportsView = useTableView<ModuleExport>(moduleExports, {
  searchableText: r => `${r.packageName ?? ''} ${r.targetModule ?? ''}`
});

const activeTab = ref('requires');

const tabs = computed<TabBarItem[]>(() => [
  { id: 'requires', label: 'Requires', icon: 'box', badge: moduleRequires.value.length || undefined },
  {
    id: 'exports',
    label: 'Exports',
    icon: 'box-arrow-up-right',
    badge: moduleExports.value.length || undefined
  }
]);

onMounted(async () => {
  try {
    const profileId = route.params.profileId as string;
    const client = new ProfileSystemClient(profileId);

    const [moduleRequiresResult, moduleExportsResult] = await Promise.all([
      client.getModuleRequires(),
      client.getModuleExports()
    ]);

    moduleRequires.value = moduleRequiresResult;
    moduleExports.value = moduleExportsResult;

    loading.value = false;
  } catch (e) {
    console.error('Failed to load module data:', e);
    error.value = true;
    loading.value = false;
  }
});
</script>
