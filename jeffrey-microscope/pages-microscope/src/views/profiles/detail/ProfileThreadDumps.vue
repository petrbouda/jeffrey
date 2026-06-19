<!--
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 -->

<template>
  <LoadingState v-if="loading" message="Loading thread dumps..." />

  <ErrorState v-else-if="error" message="Failed to load thread dumps" />

  <div v-else>
    <PageHeader
      title="Thread Dumps"
      description="Periodic jstack-style snapshots from jdk.ThreadDump: states, hot frames, locks, deadlocks and stuck threads"
      icon="bi-file-earmark-text"
    />

    <DisabledEventsNotice
      v-if="!hasData"
      title="No thread dumps in this recording"
      icon="bi-file-earmark-text"
      action-label="Enable jdk.ThreadDump, then re-record and re-import"
      :command="enableCommand"
    >
      <p>
        A thread dump (<code>jdk.ThreadDump</code>) is a periodic, full
        <code>jstack</code>-style snapshot of every thread — its state, stack and held/awaited
        locks — emitted by the JVM on a fixed interval. Jeffrey parses each dump and correlates
        them across the recording, so this page stays empty until at least one is present.
      </p>
      <p>
        In the JDK's bundled configurations this event is <strong>disabled</strong> in the lean
        <code>default</code> config but <strong>enabled</strong> in <code>profile</code> with a
        <code>period</code> of <code>60s</code> (one dump per minute). So a recording made with
        <code>default</code> (or a minimal config) has none, whereas <code>profile</code> captures
        one every minute.
      </p>

      <template #action>
        <p>
          <strong>A — inline, no extra file.</strong> Use the copyable command above: it keeps the
          bundled <code>profile</code> config and adds <code>jdk.ThreadDump#enabled=true</code> with
          <code>period=60s</code> so a full thread dump is captured every minute.
        </p>
        <p>
          <strong>B — a reusable <code>.jfc</code> overlay.</strong> Save this as
          <code>thread-dump.jfc</code> and record with
          <code>settings=profile,settings=thread-dump.jfc</code>:
        </p>
        <pre class="jfc-block">{{ jfcSnippet }}</pre>
        <p>
          Re-import the <code>.jfr</code> afterwards. Dumps are a coarse periodic sample — great for
          hangs, saturation, deadlocks and stuck threads, but they will miss sub-second spikes.
        </p>
      </template>
    </DisabledEventsNotice>

    <div v-else>
      <div class="mb-4">
        <StatsTable :metrics="metrics" />
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- Locks & Deadlocks -->
      <div v-show="activeTab === 'locks'">
        <h6 class="section-title">Deadlocks</h6>
        <EmptyState
          v-if="data!.deadlocks.length === 0"
          icon="bi-check-circle"
          title="No deadlocks detected"
          description="No 'Found one Java-level deadlock' section appeared in any dump."
        />
        <div v-for="(d, i) in data!.deadlocks" :key="i" class="deadlock-card mb-3">
          <div class="deadlock-head">
            <i class="bi bi-exclamation-octagon-fill"></i>
            <span>Deadlock at {{ formatOffset(d.timeOffsetMillis) }}</span>
            <span class="deadlock-threads">{{ d.involvedThreads.join(' ↔ ') }}</span>
          </div>
          <pre class="deadlock-body">{{ d.description }}</pre>
        </div>

        <DataTable v-if="data!.lockContention.length > 0" class="mt-4">
          <template #toolbar>
            <TableToolbar v-model="lockContentionView.query" search-placeholder="Filter monitors...">
              <span class="toolbar-info">Lock Contention <span class="muted">(worst dump)</span></span>
              <template #filters>
                <Badge key-label="Total" :value="lockContentionView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Monitor</th>
              <th>Class</th>
              <th class="text-end">Waiters</th>
              <th>Owner</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(c, i) in lockContentionView.visible" :key="i">
              <td><code>{{ c.monitorId }}</code></td>
              <td class="class-cell" :title="c.monitorClass ?? ''">
                <ClassNameDisplay v-if="c.monitorClass" :class-name="c.monitorClass" />
                <span v-else>—</span>
              </td>
              <td class="text-end">{{ FormattingService.formatNumber(c.waiterCount) }}</td>
              <td>{{ c.owner ?? '—' }}</td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="lockContentionView.visible.length"
              :match-count="lockContentionView.matchCount"
              :total="lockContentionView.total"
              :expanded="lockContentionView.expanded"
              :page-size="lockContentionView.pageSize"
              @toggle="lockContentionView.toggle"
            />
          </template>
        </DataTable>
        <EmptyState
          v-if="data!.lockContention.length === 0"
          icon="bi-unlock"
          title="No monitor contention recorded"
        />
      </div>

      <!-- Stuck -->
      <div v-show="activeTab === 'stuck'">
        <ChartDescription
          shows="Threads whose stack stayed identical across consecutive dumps"
          use-case="A stack that does not move across dumps is a hung, slow, or deadlocked thread — the longer the run, the more suspicious"
        />
        <DataTable v-if="data!.stuckThreads.length > 0">
          <template #toolbar>
            <TableToolbar v-model="stuckThreadsView.query" search-placeholder="Filter threads...">
              <span class="toolbar-info">Stuck Threads</span>
              <template #filters>
                <Badge key-label="Total" :value="stuckThreadsView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Thread</th>
              <th>State</th>
              <th>Top Frame</th>
              <th class="text-end">Dumps</th>
              <th class="text-end">Stuck For</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(s, i) in stuckThreadsView.visible" :key="i">
              <td>{{ s.name }}</td>
              <td><Badge :value="s.state" :variant="stateVariant(s.state)" size="s" /></td>
              <td><code>{{ s.topFrame }}</code></td>
              <td class="text-end">{{ s.consecutiveDumps }}</td>
              <td class="text-end">{{ formatOffset(s.stuckForMillis) }}</td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="stuckThreadsView.visible.length"
              :match-count="stuckThreadsView.matchCount"
              :total="stuckThreadsView.total"
              :expanded="stuckThreadsView.expanded"
              :page-size="stuckThreadsView.pageSize"
              @toggle="stuckThreadsView.toggle"
            />
          </template>
        </DataTable>
        <EmptyState
          v-if="data!.stuckThreads.length === 0"
          icon="bi-check-circle"
          title="No stuck threads"
          description="No thread kept the same stack across 3+ consecutive dumps."
        />
      </div>

      <!-- Browser -->
      <div v-show="activeTab === 'browse'">
        <div class="tdb-chart">
          <ChartDescription
            shows="Live thread count per dump over time, with deadlocks overlaid."
            use-case="Brush a region on the lower chart to limit the dump picker to that interval — zoom into a spike or the moments around a deadlock."
          />
          <TimeSeriesChart
            :primaryData="threadSeries"
            primaryTitle="Live threads"
            :xAnnotations="deadlockAnnotations"
            time-unit="milliseconds"
            :visibleMinutes="6000"
            :zoomEnabled="true"
            @update:timeRange="onRange"
          />
        </div>

        <div class="tdb-toolbar">
          <select
            v-model.number="selectedIndex"
            class="form-select form-select-sm tdb-dump"
            aria-label="Thread dump"
            @change="loadDump"
          >
            <option v-for="d in windowedDumps" :key="d.index" :value="d.index">
              {{ formatOffset(d.timeOffsetMillis) }} · {{ d.threadCount }} threads<template v-if="d.deadlockCount > 0"> · ⚠ deadlock</template>
            </option>
          </select>

          <div class="tdb-chips">
            <button
              type="button"
              class="tdb-chip"
              :class="{ active: browseStateFilter === '' }"
              @click="browseStateFilter = ''"
            >
              All <span class="n">{{ dumpThreads.length }}</span>
            </button>
            <button
              v-for="s in presentStates"
              :key="s"
              type="button"
              class="tdb-chip"
              :class="{ active: browseStateFilter === s }"
              @click="browseStateFilter = browseStateFilter === s ? '' : s"
            >
              {{ s.replace('_', ' ') }} <span class="n">{{ stateCounts[s] }}</span>
            </button>
          </div>

          <button
            type="button"
            class="tdb-toggle"
            :class="{ on: grouped }"
            :title="grouped ? 'One row per unique stack — toggle off for one row per thread' : 'One row per thread — toggle on to merge identical stacks'"
            @click="grouped = !grouped"
          >
            <span class="sw"></span> Group identical stacks
          </button>

          <button
            type="button"
            class="tdb-toggle"
            :class="{ on: showRaw }"
            title="Show the raw jstack-style text of the selected dump"
            @click="showRaw = !showRaw"
          >
            <span class="sw"></span> Raw text
          </button>

          <span class="tdb-grow"></span>
          <input
            v-model="browseQuery"
            class="form-control form-control-sm tdb-search"
            placeholder="Filter by thread name..."
          />
        </div>

        <LoadingState v-if="dumpLoading" message="Loading dump..." />
        <pre v-else-if="showRaw" class="tdb-rawdump">{{ selectedDump?.rawText }}</pre>

        <EmptyState
          v-else-if="grouped ? groupSections.length === 0 : flatSections.length === 0"
          icon="bi-search"
          title="No threads match"
        />

        <div v-else class="tdb-body">
          <!-- thread list -->
          <div class="tdb-list">
            <template v-if="grouped">
              <template v-for="sec in groupSections" :key="sec.state">
                <div class="tdb-sec-hd">
                  <Badge :value="sec.state" :variant="stateVariant(sec.state)" size="xs" />
                  <span class="n">{{ sec.groups.length }}</span>
                </div>
                <div
                  v-for="g in sec.groups"
                  :key="g.key"
                  class="tdb-row"
                  :class="{ sel: selectedGroup && selectedGroup.key === g.key }"
                  @click="selectedKey = g.key"
                >
                  <span class="tdb-count" :class="{ one: g.members.length === 1 }">{{ g.members.length }}×</span>
                  <span class="tdb-col">
                    <span class="tdb-name">{{ groupTitle(g) }}</span>
                    <span class="tdb-frame">{{ topFrame(g.members[0]) }}</span>
                  </span>
                </div>
              </template>
            </template>
            <template v-else>
              <template v-for="sec in flatSections" :key="sec.state">
                <div class="tdb-sec-hd">
                  <Badge :value="sec.state" :variant="stateVariant(sec.state)" size="xs" />
                  <span class="n">{{ sec.threads.length }}</span>
                </div>
                <div
                  v-for="t in sec.threads"
                  :key="t.name"
                  class="tdb-row"
                  :class="{ sel: selectedThread && selectedThread.name === t.name }"
                  @click="selectedName = t.name"
                >
                  <span class="tdb-col">
                    <span class="tdb-name">{{ t.name }}</span>
                    <span class="tdb-frame">{{ topFrame(t) }}</span>
                  </span>
                </div>
              </template>
            </template>
          </div>

          <!-- detail -->
          <div class="tdb-detail">
            <template v-if="grouped && selectedGroup">
              <div class="tdb-dhd">
                <Badge :value="selectedGroup.state" :variant="stateVariant(selectedGroup.state)" size="s" />
                <h3 class="tdb-title">{{ groupTitle(selectedGroup) }}</h3>
                <span v-if="selectedGroup.members.length > 1" class="tdb-count">{{ selectedGroup.members.length }}×</span>
              </div>
              <div class="tdb-meta">
                <span>Threads <b>{{ selectedGroup.members.length }}</b></span>
                <span>Group <b>{{ selectedGroup.members[0].group }}</b></span>
              </div>
              <pre class="tdb-stack">{{ stackText(selectedGroup.members[0]) }}</pre>
              <ul v-if="selectedGroup.members[0].locks.length" class="tdb-locks">
                <li v-for="(l, li) in selectedGroup.members[0].locks" :key="li">
                  {{ lockLabel(l.kind) }} <code>{{ l.monitorId }}</code><span v-if="l.monitorClass"> (a {{ l.monitorClass }})</span>
                </li>
              </ul>
              <div v-if="selectedGroup.members.length > 1" class="tdb-members">
                <div class="tdb-members-hd">Threads in this group ({{ selectedGroup.members.length }})</div>
                <DataTable>
                  <thead>
                    <tr><th>Thread</th><th class="text-end">Locks</th></tr>
                  </thead>
                  <tbody>
                    <tr v-for="(m, mi) in selectedGroup.members" :key="mi">
                      <td>{{ m.name }}</td>
                      <td class="text-end">{{ m.locks.length || '—' }}</td>
                    </tr>
                  </tbody>
                </DataTable>
              </div>
            </template>

            <template v-else-if="!grouped && selectedThread">
              <div class="tdb-dhd">
                <Badge :value="selectedThread.state" :variant="stateVariant(selectedThread.state)" size="s" />
                <h3 class="tdb-title">{{ selectedThread.name }}</h3>
              </div>
              <div class="tdb-meta">
                <span>Group <b>{{ selectedThread.group }}</b></span>
                <span>Locks <b>{{ selectedThread.locks.length }}</b></span>
              </div>
              <pre class="tdb-stack">{{ stackText(selectedThread) }}</pre>
              <ul v-if="selectedThread.locks.length" class="tdb-locks">
                <li v-for="(l, li) in selectedThread.locks" :key="li">
                  {{ lockLabel(l.kind) }} <code>{{ l.monitorId }}</code><span v-if="l.monitorClass"> (a {{ l.monitorClass }})</span>
                </li>
              </ul>
            </template>

            <div v-else class="tdb-empty">Select a thread.</div>
          </div>
        </div>
      </div>

      <!-- How It Works -->
      <div v-show="activeTab === 'about'">
        <AboutPanel
          icon="bi-question-circle"
          title="Understanding Thread Dumps"
          subtitle="Periodic jstack-style snapshots correlated across the whole recording"
        >
          <AboutCallout variant="intro">
            <p>
              The JVM periodically emits a full textual thread dump (<code>jdk.ThreadDump</code>, like
              <code>jstack</code>): every thread's state, stack and held/awaited locks. Jeffrey parses
              each dump and correlates them across the recording, so you get trends instead of a single
              snapshot.
            </p>
          </AboutCallout>

          <AboutCallout variant="tip" title="Sampled, not continuous" icon="bi-lightbulb-fill">
            Dumps are periodic (~every 60s by default), so they are a coarse sample — great for
            hangs and saturation, not for sub-second spikes.
          </AboutCallout>

          <AboutSection icon="bi-graph-up" title="What the Views Show">
            <FeatureGrid>
              <FeatureCard icon="bi-search" variant="primary" title="Browser">
                A master&ndash;detail view of any dump: threads sharing an identical stack are grouped (with a
                count) so idle pools collapse to one row — pick a group or thread to read its full stack and
                held/awaited locks. Toggle grouping off for a per-thread list, or switch to raw text.
              </FeatureCard>
              <FeatureCard icon="bi-lock" variant="danger" title="Locks & Deadlocks">
                JVM-reported deadlocks and the most-contended monitors with their waiters and owner.
              </FeatureCard>
              <FeatureCard icon="bi-hourglass-split" variant="warning" title="Stuck Threads">
                Threads whose stack never moves across consecutive dumps.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutSection icon="bi-broadcast" title="How JFR Emits This">
            <ul>
              <li>
                <code>jdk.ThreadDump</code> — a periodic, full <code>jstack</code>-style snapshot of
                every thread (state, stack, held/awaited locks), emitted on a fixed interval. It is
                <strong>disabled</strong> in the bundled <code>default</code> config but
                <strong>enabled</strong> in <code>profile</code> with <code>period=60s</code> (one dump
                per minute) — so a <code>default</code>-config recording has none.
              </li>
            </ul>
          </AboutSection>
        </AboutPanel>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import PageHeader from '@/components/layout/PageHeader.vue';
import StatsTable from '@/components/StatsTable.vue';
import TabBar from '@/components/TabBar.vue';
import TimeSeriesChart, { type ChartAnnotation } from '@/components/TimeSeriesChart.vue';
import ChartDescription from '@/components/ChartDescription.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import Badge from '@shared/components/Badge.vue';
import ClassNameDisplay from '@/components/heap/ClassNameDisplay.vue';
import DisabledEventsNotice from '@/components/alerts/DisabledEventsNotice.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import TableShowMore from '@/components/table/TableShowMore.vue';
import { useTableView } from '@/composables/useTableView';
import ProfileThreadClient from '@/services/api/ProfileThreadClient';
import FormattingService from '@shared/services/FormattingService';
import type {
  DeadlockEntry,
  ParsedDump,
  ParsedThread,
  ThreadDumpAnalysis,
  ThreadLockKind,
  ThreadState
} from '@/services/api/model/ThreadDumpModels';
import type { Variant } from '@shared/types/ui';

const route = useRoute();

const enableCommand =
  'java -XX:StartFlightRecording=settings=profile,jdk.ThreadDump#enabled=true,jdk.ThreadDump#period=60s,filename=app.jfr,dumponexit=true -jar app.jar';

const jfcSnippet = `<?xml version="1.0" encoding="UTF-8"?>
<configuration version="2.0">
  <event name="jdk.ThreadDump">
    <setting name="enabled">true</setting>
    <setting name="period">60 s</setting>
  </event>
</configuration>`;

const loading = ref(true);
const error = ref(false);
const data = ref<ThreadDumpAnalysis>();
const activeTab = ref('browse');

let client: ProfileThreadClient | null = null;

const stateVariant = (state: ThreadState): Variant => {
  switch (state) {
    case 'RUNNABLE':
      return 'success';
    case 'BLOCKED':
      return 'danger';
    case 'WAITING':
      return 'info';
    case 'TIMED_WAITING':
      return 'warning';
    default:
      return 'secondary';
  }
};

const lockLabel = (kind: ThreadLockKind): string => {
  switch (kind) {
    case 'LOCKED':
      return 'locked';
    case 'WAITING_TO_LOCK':
      return 'waiting to lock';
    case 'PARKING_TO_WAIT':
      return 'parking to wait for';
    default:
      return 'waiting on';
  }
};

const formatOffset = (millis: number): string => FormattingService.formatDuration2Units(millis * 1_000_000);

const lockContentionView = useTableView(() => data.value?.lockContention ?? [], {
  searchableText: c => c.monitorClass ?? ''
});

const stuckThreadsView = useTableView(() => data.value?.stuckThreads ?? [], {
  searchableText: s => s.name
});

const tabs = [
  { id: 'browse', label: 'Browser', icon: 'search' },
  { id: 'locks', label: 'Locks & Deadlocks', icon: 'lock' },
  { id: 'stuck', label: 'Stuck Threads', icon: 'hourglass-split' },
  { id: 'about', label: 'How It Works', icon: 'book' }
];

const hasData = computed(() => (data.value?.header.dumpCount ?? 0) > 0);

const metrics = computed(() => {
  const h = data.value?.header;
  if (!h) {
    return [];
  }
  return [
    {
      icon: 'file-earmark-text',
      title: 'Thread Dumps',
      value: FormattingService.formatNumber(h.dumpCount),
      variant: 'highlight' as const,
      breakdown: [{ label: 'Peak Threads', value: FormattingService.formatNumber(h.peakThreadCount) }]
    },
    {
      icon: 'exclamation-octagon',
      title: 'Deadlocks',
      value: FormattingService.formatNumber(h.deadlockCount),
      variant: h.deadlockCount > 0 ? ('danger' as const) : ('success' as const)
    },
    {
      icon: 'hourglass-split',
      title: 'Stuck Threads',
      value: FormattingService.formatNumber(h.stuckThreadCount),
      variant: h.stuckThreadCount > 0 ? ('warning' as const) : ('success' as const)
    }
  ];
});

// ----- Browser: lazily load a single parsed dump, then group/inspect its threads -----
const selectedIndex = ref(0);
const selectedDump = ref<ParsedDump>();
const dumpLoading = ref(false);
const showRaw = ref(false);
const browseQuery = ref('');
const browseStateFilter = ref<'' | ThreadState>('');
const grouped = ref(true);
const selectedKey = ref<string | null>(null);
const selectedName = ref<string | null>(null);

const STATE_ORDER: ThreadState[] = [
  'BLOCKED',
  'RUNNABLE',
  'WAITING',
  'TIMED_WAITING',
  'NEW',
  'TERMINATED',
  'UNKNOWN'
];

interface StackGroup {
  key: string;
  state: ThreadState;
  members: ParsedThread[];
}

const topFrame = (t: ParsedThread): string => (t.frames.length ? t.frames[0] : '(no Java frames)');

const stackText = (t: ParsedThread): string =>
  t.frames.length ? t.frames.map((f) => '  at ' + f).join('\n') : '(no Java frames)';

// A multi-thread group's display name collapses a numeric pool suffix to "-∗".
const groupTitle = (g: StackGroup): string => {
  if (g.members.length === 1) {
    return g.members[0].name;
  }
  const m = g.members[0].name.match(/^(.*?)[-.]?\d+$/);
  if (m) {
    return m[1] + '-∗';
  }
  if (!g.members[0].frames.length) {
    return g.members.length + ' threads (no Java frames)';
  }
  return g.members[0].name + ' +' + (g.members.length - 1);
};

const dumpThreads = computed<ParsedThread[]>(() => selectedDump.value?.threads ?? []);

const stateCounts = computed<Record<string, number>>(() => {
  const c: Record<string, number> = {};
  for (const t of dumpThreads.value) {
    c[t.state] = (c[t.state] ?? 0) + 1;
  }
  return c;
});

const presentStates = computed<ThreadState[]>(() => STATE_ORDER.filter((s) => stateCounts.value[s]));

const visibleThreads = computed<ParsedThread[]>(() => {
  const query = browseQuery.value.trim().toLowerCase();
  return dumpThreads.value.filter((t) => {
    if (browseStateFilter.value && t.state !== browseStateFilter.value) {
      return false;
    }
    return query === '' || t.name.toLowerCase().includes(query);
  });
});

// Merge threads with an identical (state + full stack) into one group; biggest groups first.
const buildGroups = (list: ParsedThread[]): StackGroup[] => {
  const m = new Map<string, StackGroup>();
  for (const t of list) {
    const key = t.state + '|' + t.frames.join('\n');
    let g = m.get(key);
    if (!g) {
      g = { key, state: t.state, members: [] };
      m.set(key, g);
    }
    g.members.push(t);
  }
  return [...m.values()].sort((a, b) => b.members.length - a.members.length);
};

const groupSections = computed(() =>
  STATE_ORDER.map((state) => ({
    state,
    groups: buildGroups(visibleThreads.value.filter((t) => t.state === state))
  })).filter((sec) => sec.groups.length > 0)
);

const flatSections = computed(() =>
  STATE_ORDER.map((state) => ({
    state,
    threads: visibleThreads.value
      .filter((t) => t.state === state)
      .sort((a, b) => a.name.localeCompare(b.name))
  })).filter((sec) => sec.threads.length > 0)
);

// Selection falls back to the first item whenever the current pick is filtered/grouped away.
const selectedGroup = computed<StackGroup | null>(() => {
  const all = groupSections.value.flatMap((s) => s.groups);
  return all.find((g) => g.key === selectedKey.value) ?? all[0] ?? null;
});

const selectedThread = computed<ParsedThread | null>(() => {
  const all = flatSections.value.flatMap((s) => s.threads);
  return all.find((t) => t.name === selectedName.value) ?? all[0] ?? null;
});

// ----- Time-span picker: a threads-over-time chart whose brush limits the dump list -----
const timeWindow = ref<{ start: number; end: number } | null>(null);

const threadSeries = computed<number[][]>(() =>
  (data.value?.dumps ?? []).map((d) => [d.timeOffsetMillis, d.threadCount])
);

const escapeHtml = (value: string): string =>
  value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

// One vertical marker per dump-time that had deadlock(s); the tooltip carries the count,
// the involved threads and the exact time. Multiple deadlocks at the same dump are grouped.
const deadlockAnnotations = computed<ChartAnnotation[]>(() => {
  const entries = data.value?.deadlocks ?? [];
  if (entries.length === 0) {
    return [];
  }

  const byTime = new Map<number, DeadlockEntry[]>();
  for (const entry of entries) {
    const list = byTime.get(entry.timeOffsetMillis) ?? [];
    list.push(entry);
    byTime.set(entry.timeOffsetMillis, list);
  }

  return [...byTime.entries()].map(([time, list]) => {
    const count = list.length;
    const threads = [...new Set(list.flatMap((e) => e.involvedThreads))];
    const threadSummary = threads.length > 0 ? escapeHtml(threads.join(' ↔ ')) : '—';
    const tooltipHtml =
      '<div class="tsc-anno-tip-title">⚠ Deadlock</div>' +
      `<div><strong>${count}</strong> deadlock${count === 1 ? '' : 's'} occurred</div>` +
      `<div class="tsc-anno-tip-time">${threadSummary}<br/>at ${formatOffset(time)}</div>`;
    return { x: time, label: 'Deadlock', tooltipHtml };
  });
});

// Dumps inside the brushed time window (all dumps when nothing is selected).
const windowedDumps = computed(() => {
  const dumps = data.value?.dumps ?? [];
  const w = timeWindow.value;
  if (!w) {
    return dumps;
  }
  return dumps.filter((d) => d.timeOffsetMillis >= w.start && d.timeOffsetMillis <= w.end);
});

const onRange = (payload: { start: number; end: number; isZoomed: boolean }): void => {
  timeWindow.value = payload.isZoomed ? { start: payload.start, end: payload.end } : null;
};

// When the window changes, keep the loaded dump valid — snap to the first one in range.
watch(windowedDumps, (dumps) => {
  if (dumps.length > 0 && !dumps.some((d) => d.index === selectedIndex.value)) {
    selectedIndex.value = dumps[0].index;
    loadDump();
  }
});

const loadDump = async () => {
  if (!client) {
    return;
  }
  try {
    dumpLoading.value = true;
    selectedDump.value = await client.dump(selectedIndex.value);
  } catch (err) {
    console.error('Error loading thread dump:', err);
  } finally {
    dumpLoading.value = false;
  }
};

const loadData = async () => {
  try {
    loading.value = true;
    error.value = false;
    client = new ProfileThreadClient(route.params.profileId as string);
    data.value = await client.dumps();

    if (activeTab.value === 'browse' && (data.value?.dumps.length ?? 0) > 0) {
      loadDump();
    }
  } catch (err) {
    error.value = true;
    console.error('Error loading thread dumps:', err);
  } finally {
    loading.value = false;
  }
};

watch(activeTab, (tab) => {
  if (tab === 'browse' && !selectedDump.value && (data.value?.dumps.length ?? 0) > 0) {
    loadDump();
  }
});

onMounted(loadData);
</script>

<style scoped>
.section-title {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  color: var(--color-text-muted);
  margin-bottom: 0.5rem;
}

.muted {
  color: var(--color-text-muted);
  font-weight: 400;
  text-transform: none;
}

.deadlock-card {
  border: 1px solid var(--color-danger-border-light);
  border-radius: var(--radius-md);
  background: var(--color-danger-bg-light);
  overflow: hidden;
}

.deadlock-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  font-weight: 600;
  color: var(--color-danger);
}

.deadlock-threads {
  margin-left: auto;
  font-weight: 500;
  font-size: 0.85rem;
}

.deadlock-body {
  margin: 0;
  padding: 0.75rem;
  font-size: 0.8rem;
  white-space: pre-wrap;
  word-break: break-word;
}

.class-cell {
  max-width: 520px;
}

.toolbar-info {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--color-text);
}

/* Browser: master-detail + stack grouping */
.tdb-chart {
  margin-bottom: 1rem;
}

.tdb-toolbar {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
  margin-bottom: 0.75rem;
}

.tdb-dump {
  width: auto;
  min-width: 230px;
  height: 31px;
}

.tdb-grow {
  flex: 1;
}

.tdb-search {
  max-width: 18rem;
  height: 31px;
}

.tdb-chips {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}

.tdb-chip {
  height: 31px;
  font-size: 0.75rem;
  font-weight: 600;
  border: 1px solid var(--color-border-input);
  background: var(--color-white);
  color: var(--color-text);
  border-radius: var(--radius-base);
  padding: 0 0.65rem;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.tdb-chip .n {
  font-variant-numeric: tabular-nums;
  opacity: 0.7;
  font-size: 0.7rem;
}

.tdb-chip:hover {
  background: var(--color-bg-hover);
}

.tdb-chip.active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-white);
}

.tdb-chip.active .n {
  opacity: 0.85;
}

.tdb-toggle {
  height: 31px;
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--color-text-muted);
  cursor: pointer;
  border: 1px solid var(--color-border-input);
  border-radius: var(--radius-base);
  padding: 0 0.7rem 0 0.4rem;
  background: var(--color-white);
}

.tdb-toggle .sw {
  width: 30px;
  height: 18px;
  border-radius: 999px;
  background: var(--color-border-input);
  position: relative;
  transition: 0.15s;
}

.tdb-toggle .sw::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--color-white);
  box-shadow: var(--shadow-sm);
  transition: 0.15s;
}

.tdb-toggle.on {
  color: var(--color-primary);
  border-color: var(--color-primary);
  background: var(--color-primary-lighter);
}

.tdb-toggle.on .sw {
  background: var(--color-primary);
}

.tdb-toggle.on .sw::after {
  transform: translateX(12px);
}

.tdb-body {
  display: grid;
  grid-template-columns: 340px 1fr;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.tdb-list {
  border-right: 1px solid var(--color-border);
}

.tdb-sec-hd {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.75rem;
  background: var(--color-light);
  border-bottom: 1px solid var(--color-border);
}

.tdb-sec-hd .n {
  font-size: 0.7rem;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.tdb-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.5rem 0.75rem;
  cursor: pointer;
  border-bottom: 1px solid var(--color-border);
  border-left: 3px solid transparent;
}

.tdb-row:hover {
  background: var(--color-bg-hover);
}

.tdb-row.sel {
  background: var(--color-primary-light);
  border-left-color: var(--color-primary);
}

.tdb-col {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.tdb-name {
  font-weight: 600;
  font-size: 0.8rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tdb-frame {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.65rem;
  color: var(--color-text-light);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tdb-count {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-weight: 700;
  font-size: 0.7rem;
  min-width: 30px;
  text-align: center;
  padding: 0.2rem 0.4rem;
  border-radius: var(--radius-sm);
  background: var(--color-dark);
  color: var(--color-white);
  flex: none;
}

.tdb-count.one {
  background: var(--color-lighter);
  color: var(--color-text-light);
  font-weight: 600;
}

.tdb-detail {
  padding: 1.1rem 1.3rem;
}

.tdb-dhd {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-bottom: 0.5rem;
  flex-wrap: wrap;
}

.tdb-title {
  margin: 0;
  font-size: 1.05rem;
}

.tdb-meta {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
  color: var(--color-text-muted);
  font-size: 0.78rem;
  margin-bottom: 0.25rem;
}

.tdb-meta b {
  color: var(--color-text);
}

.tdb-stack {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.72rem;
  line-height: 1.6;
  white-space: pre;
  overflow-x: auto;
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 0.7rem 0.85rem;
  margin: 0.6rem 0 0;
}

.tdb-locks {
  margin: 0.5rem 0 0;
  padding-left: 1.1rem;
  font-size: 0.72rem;
  color: var(--color-text-muted);
}

.tdb-members {
  margin-top: 1.1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.tdb-members-hd {
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-text-light);
  padding: 0.5rem 0.75rem;
  background: var(--color-light);
  border-bottom: 1px solid var(--color-border);
}

.tdb-empty {
  color: var(--color-text-light);
  text-align: center;
  padding: 2.5rem 0;
}

.tdb-rawdump {
  font-size: 0.72rem;
  overflow-x: auto;
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 0.75rem;
}

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
