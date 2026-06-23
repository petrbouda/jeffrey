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
        <DisabledEventsNotice
          v-if="moduleRequires.length === 0"
          title="No module dependencies recorded"
          icon="bi-box"
          action-label="Re-record with the module events enabled, then re-import"
          :command="moduleEnableCommand"
        >
          <p>
            Module dependencies come from <code>jdk.ModuleRequire</code>, which the JVM emits once at
            startup (one event per <code>requires</code> edge in the resolved module graph). This event
            is <strong>enabled by default</strong> in both the bundled <code>default</code> and
            <code>profile</code> configs, so an empty tab usually means one of two things: the
            recording was made with a minimal or custom config that disabled it, or the application
            runs from the classpath with no named modules (only the unnamed module), in which case
            there are no <code>requires</code> edges to report.
          </p>
        </DisabledEventsNotice>
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
        <DisabledEventsNotice
          v-if="moduleExports.length === 0"
          title="No package exports recorded"
          icon="bi-box-arrow-up-right"
          action-label="Enable jdk.ModuleExport, then re-record and re-import"
          :command="moduleEnableCommand"
        >
          <p>
            Package exports come from <code>jdk.ModuleExport</code>, emitted once at JVM startup — one
            event per <code>exports</code> declaration in the resolved module graph (the package, and
            the target module, or unqualified when exported to everyone).
          </p>
          <p>
            This event is <strong>disabled in the bundled <code>default</code> config</strong> and
            <strong>enabled in <code>profile</code></strong>. So an empty tab usually means the
            recording was made with <code>default</code> (or a minimal config) rather than
            <code>profile</code> — enable the event and re-record to populate this view.
          </p>

          <template #action>
            <p>
              <strong>A — inline, no extra file.</strong> Use the copyable command above: it records
              with the bundled <code>profile</code> config and explicitly enables both
              <code>jdk.ModuleRequire</code> and <code>jdk.ModuleExport</code>.
            </p>
            <p>
              <strong>B — a reusable <code>.jfc</code> overlay.</strong> Save this as
              <code>modules.jfc</code> and record with
              <code>settings=profile,settings=modules.jfc</code>:
            </p>
            <pre class="jfc-block">{{ moduleJfcSnippet }}</pre>
            <p>
              Re-import the <code>.jfr</code> into Jeffrey afterwards. Both events fire once at
              startup, so they add negligible overhead.
            </p>
          </template>
        </DisabledEventsNotice>
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
import PageHeader from '@shared/components/layout/PageHeader.vue';
import TabBar from '@shared/components/TabBar.vue';
import type { TabBarItem } from '@shared/components/TabBar.vue';
import DataTable from '@shared/components/table/DataTable.vue';
import TableToolbar from '@shared/components/table/TableToolbar.vue';
import TableShowMore from '@shared/components/table/TableShowMore.vue';
import ChartDescription from '@shared/components/ChartDescription.vue';
import Badge from '@shared/components/Badge.vue';
import DisabledEventsNotice from '@/components/alerts/DisabledEventsNotice.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import ProfileSystemClient from '@/services/api/ProfileSystemClient';
import type { ModuleEdge, ModuleExport } from '@/services/api/model/SystemModels';
import { useTableView } from '@/composables/useTableView';

const route = useRoute();

const moduleEnableCommand =
  'java -XX:StartFlightRecording=settings=profile,jdk.ModuleRequire#enabled=true,jdk.ModuleExport#enabled=true,filename=app.jfr,dumponexit=true -jar app.jar';

const moduleJfcSnippet = `<?xml version="1.0" encoding="UTF-8"?>
<configuration version="2.0">
  <event name="jdk.ModuleRequire">
    <setting name="enabled">true</setting>
    <setting name="period">endChunk</setting>
  </event>
  <event name="jdk.ModuleExport">
    <setting name="enabled">true</setting>
    <setting name="period">endChunk</setting>
  </event>
</configuration>`;

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

<style scoped>
.jfc-block {
  margin: 8px 0 12px;
  padding: 12px 14px;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.78rem;
  line-height: 1.5;
  color: var(--color-text);
  overflow-x: auto;
  white-space: pre;
}
</style>
