<template>
  <LoadingState v-if="loading" message="Loading garbage collection data..." />

  <ErrorState v-else-if="error" message="Failed to load garbage collection data" />

  <div v-else>
    <!-- Header Section -->
    <PageHeader
      title="Garbage Collection Analysis"
      description="Comprehensive analysis of garbage collection events and performance"
      icon="bi-recycle"
    />

    <!-- Key Metrics Row -->
    <GCMetricsStatsRow :profile-id="route.params.profileId as string" />

    <!-- GC Analysis Section -->
    <TabBar v-model="activeTab" :tabs="gcTabs" class="mb-3" />

    <!-- Pause Distribution Tab -->
    <div v-show="activeTab === 'distribution'">
      <div class="chart-container">
        <div id="gc-pause-distribution-chart"></div>
      </div>
    </div>

    <!-- GC Efficiency Tab -->
    <div v-show="activeTab === 'efficiency'">
      <div v-if="gcSummary" class="row">
        <div class="col-md-6">
          <div class="chart-container">
            <div id="gc-efficiency-pie-chart"></div>
          </div>
        </div>
        <div class="col-md-6">
          <div class="efficiency-stats">
            <h6 class="mb-3">GC Efficiency Metrics</h6>
            <div class="stat-item mb-3">
              <label class="stat-label">Application Time</label>
              <div class="stat-value">{{ gcSummary.applicationTime }}</div>
              <div class="progress mt-1">
                <div
                  class="progress-bar bg-success"
                  role="progressbar"
                  :style="{ width: gcSummary.gcThroughput + '%' }"
                ></div>
              </div>
            </div>
            <div class="stat-item mb-3">
              <label class="stat-label">GC Time</label>
              <div class="stat-value">{{ gcSummary.totalGcTime }}</div>
              <div class="progress mt-1">
                <div
                  class="progress-bar bg-warning"
                  role="progressbar"
                  :style="{ width: gcSummary.gcOverhead + '%' }"
                ></div>
              </div>
            </div>
            <div class="stat-item">
              <label class="stat-label">Collection Frequency</label>
              <div class="stat-value">{{ gcSummary.collectionFrequency }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Longest Pauses Tab -->
    <div v-show="activeTab === 'events'">
      <EmptyState
        v-if="!gcOverviewData?.longestPauses || gcOverviewData.longestPauses.length === 0"
        icon="bi-recycle"
        title="No garbage collection pause events"
      />
      <DataTable v-else>
        <template #toolbar>
          <TableToolbar v-model="longestPausesView.query" search-placeholder="Filter by cause...">
            <span class="toolbar-info">Longest pauses</span>
            <template #filters>
              <Badge
                key-label="Total"
                :value="longestPausesView.matchCount"
                variant="secondary"
                size="s"
                borderless
              />
            </template>
          </TableToolbar>
        </template>
        <thead>
          <tr>
            <th>ID</th>
            <th>Cause</th>
            <th>Sum of Pauses</th>
            <th>Duration</th>
            <th>Before GC</th>
            <th>After GC</th>
            <th>Difference</th>
            <th>Efficiency</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="event in longestPausesView.visible"
            :key="event.gcId"
            @click="showEventDetails(event)"
            style="cursor: pointer"
          >
            <td>{{ event.gcId }}</td>
            <td>
              <div class="cause-cell">
                <div class="d-flex align-items-center gap-2 mb-1">
                  <Badge
                    :value="event.cause"
                    variant="secondary"
                    size="m"
                    :title="getGCCauseTooltip(event.cause)"
                    class="gc-cause-badge"
                  />
                  <Badge
                    v-if="event.collectorName"
                    :value="event.collectorName"
                    :variant="getGenerationTypeBadgeVariant(event.generationType)"
                    size="s"
                  />
                  <Badge
                    :value="getConcurrentBadgeValue(event.concurrent)"
                    :variant="getConcurrentBadgeVariant(event.concurrent)"
                    size="s"
                  />
                  <Badge
                    v-if="event.type"
                    :value="event.type"
                    variant="secondary"
                    size="s"
                    borderless
                  />
                </div>
                <span class="timestamp-path text-muted small">{{
                  FormattingService.formatTimestamp(event.timestamp)
                }}</span>
              </div>
            </td>
            <td>{{ FormattingService.formatDuration2Units(event.sumOfPauses) }}</td>
            <td>{{ FormattingService.formatDuration2Units(event.duration) }}</td>
            <td>{{ FormattingService.formatBytes(event.beforeGC) }}</td>
            <td>{{ FormattingService.formatBytes(event.afterGC) }}</td>
            <td>
              <Badge
                :value="formatDifference(event.beforeGC, event.afterGC)"
                :variant="getDifferenceBadgeVariant(event.beforeGC, event.afterGC)"
                size="m"
              />
            </td>
            <td>
              <div class="d-flex align-items-center">
                <div class="progress flex-grow-1 me-2" style="height: 6px; min-width: 40px">
                  <div
                    class="progress-bar"
                    :class="getDifferenceBarClass(event.beforeGC, event.afterGC)"
                    :style="{
                      width: getDifferencePercentage(event.beforeGC, event.afterGC) + '%'
                    }"
                  ></div>
                </div>
                <small class="text-muted"
                  >{{ getDifferencePercentage(event.beforeGC, event.afterGC).toFixed(1) }}%</small
                >
              </div>
            </td>
          </tr>
        </tbody>
        <template #footer>
          <TableShowMore
            :shown="longestPausesView.visible.length"
            :match-count="longestPausesView.matchCount"
            :total="longestPausesView.total"
            :expanded="longestPausesView.expanded"
            :page-size="longestPausesView.pageSize"
            @toggle="longestPausesView.toggle"
          />
        </template>
      </DataTable>
    </div>

    <!-- Concurrent Cycles Tab -->
    <div v-show="activeTab === 'concurrent-cycles'">
      <div v-if="!gcOverviewData?.longestConcurrentEvents" class="alert alert-info">
        <i class="bi bi-info-circle me-2"></i>
        This garbage collector does not support concurrent cycles
      </div>
      <EmptyState
        v-else-if="gcOverviewData.longestConcurrentEvents.length === 0"
        icon="bi-recycle"
        title="No concurrent cycle events"
      />
      <DataTable v-else>
        <template #toolbar>
          <TableToolbar
            v-model="concurrentEventsView.query"
            search-placeholder="Filter by collector..."
          >
            <span class="toolbar-info">Concurrent cycles</span>
            <template #filters>
              <Badge
                key-label="Total"
                :value="concurrentEventsView.matchCount"
                variant="secondary"
                size="s"
                borderless
              />
            </template>
          </TableToolbar>
        </template>
        <thead>
          <tr>
            <th>ID</th>
            <th>Timestamp</th>
            <th>Collector Name</th>
            <th>Duration</th>
            <th>Sum of Pauses</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="event in concurrentEventsView.visible"
            :key="event.gcId"
            @click="showConcurrentEventDetails(event)"
            style="cursor: pointer"
          >
            <td>{{ event.gcId }}</td>
            <td>{{ FormattingService.formatTimestamp(event.timestamp) }}</td>
            <td>
              <Badge
                v-if="event.collectorName"
                :value="event.collectorName"
                :variant="getGenerationTypeBadgeVariant(event.generationType)"
                size="s"
                class="ms-2"
              />
            </td>
            <td>{{ FormattingService.formatDuration2Units(event.duration) }}</td>
            <td>{{ FormattingService.formatDuration2Units(event.sumOfPauses) }}</td>
          </tr>
        </tbody>
        <template #footer>
          <TableShowMore
            :shown="concurrentEventsView.visible.length"
            :match-count="concurrentEventsView.matchCount"
            :total="concurrentEventsView.total"
            :expanded="concurrentEventsView.expanded"
            :page-size="concurrentEventsView.pageSize"
            @toggle="concurrentEventsView.toggle"
          />
        </template>
      </DataTable>
    </div>

    <!-- Pause Types Reference Tab -->
    <!-- Promotion & Tenuring Tab -->
    <div v-show="activeTab === 'tenuring'">
      <EmptyState
        v-if="!tenuringData || tenuringData.gcs.length === 0"
        icon="bi-arrow-up-circle"
        title="No tenuring distribution recorded"
        description="This recording has no jdk.TenuringDistribution events — the collector may not use survivor-age tenuring."
      />
      <template v-else>
        <ChartDescription
          shows="Surviving bytes at each survivor age, one stacked bar per young collection."
          use-case="Objects drifting toward the top of the stack are about to be promoted — a tall, top-heavy stack signals premature promotion."
        />
        <div class="chart-container">
          <div id="gc-tenuring-stacked-chart"></div>
        </div>

        <DataTable class="mt-4">
          <template #toolbar>
            <TableToolbar :show-search="false">
              <span class="toolbar-info">Survivor-age distribution</span>
              <template #filters>
                <Badge
                  key-label="Collections"
                  :value="tenuringGcsView.total"
                  variant="secondary"
                  size="s"
                  borderless
                />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th class="gc-id-column">GC ID</th>
              <th class="text-end">Surviving Bytes</th>
              <th>Age Distribution</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="gc in tenuringGcsView.visible" :key="gc.gcId">
              <td>{{ gc.gcId }}</td>
              <td class="text-end">{{ FormattingService.formatBytes(gc.totalSizeBytes) }}</td>
              <td>
                <div class="tenuring-buckets">
                  <Badge
                    v-for="bucket in gc.buckets"
                    :key="bucket.age"
                    :key-label="`age ${bucket.age}`"
                    :value="FormattingService.formatBytesShort(bucket.sizeBytes)"
                    variant="secondary"
                    size="xs"
                  />
                </div>
              </td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="tenuringGcsView.visible.length"
              :match-count="tenuringGcsView.matchCount"
              :total="tenuringGcsView.total"
              :expanded="tenuringGcsView.expanded"
              :page-size="tenuringGcsView.pageSize"
              @toggle="tenuringGcsView.toggle"
            />
          </template>
        </DataTable>
      </template>
    </div>

    <!-- G1 IHOP & CPU Tab -->
    <div v-show="activeTab === 'ihop'">
      <EmptyState
        v-if="ihopData && !hasIhopTimeline && ihopData.cpuTimes.length === 0"
        icon="bi-sliders"
        title="No G1 IHOP data recorded"
        description="jdk.G1AdaptiveIHOP events are only emitted by the G1 collector."
      />
      <template v-else-if="ihopData">
        <template v-if="hasIhopTimeline">
          <ChartDescription
            shows="The adaptive IHOP threshold vs. current old-generation occupancy over time."
            use-case="When occupancy crosses the threshold, G1 starts a concurrent marking cycle — it explains when and why cycles begin."
          />
          <div class="ihop-chart-container mb-4">
            <TimeSeriesChart
              :primaryData="ihopOccupancySeries"
              primaryTitle="Old Gen Occupancy"
              :secondaryData="ihopThresholdSeries"
              secondaryTitle="IHOP Threshold"
              :primaryAxisType="AxisFormatType.BYTES"
              :secondaryAxisType="AxisFormatType.BYTES"
              :visibleMinutes="60"
            />
          </div>
        </template>

        <DataTable v-if="ihopData.cpuTimes.length > 0">
          <template #toolbar>
            <TableToolbar :show-search="false">
              <span class="toolbar-info">GC CPU time</span>
              <template #filters>
                <Badge
                  key-label="Collections"
                  :value="cpuTimesView.total"
                  variant="secondary"
                  size="s"
                  borderless
                />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th class="gc-id-column">GC ID</th>
              <th class="text-end">User Time</th>
              <th class="text-end">System Time</th>
              <th class="text-end">Real Time</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="entry in cpuTimesView.visible" :key="entry.gcId">
              <td>{{ entry.gcId }}</td>
              <td class="text-end">
                {{ FormattingService.formatDuration2Units(entry.userNanos) }}
              </td>
              <td class="text-end">
                {{ FormattingService.formatDuration2Units(entry.systemNanos) }}
              </td>
              <td class="text-end">
                {{ FormattingService.formatDuration2Units(entry.realNanos) }}
              </td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="cpuTimesView.visible.length"
              :match-count="cpuTimesView.matchCount"
              :total="cpuTimesView.total"
              :expanded="cpuTimesView.expanded"
              :page-size="cpuTimesView.pageSize"
              @toggle="cpuTimesView.toggle"
            />
          </template>
        </DataTable>

        <DataTable v-if="ihopData.mmu.length > 0" class="mt-4">
          <template #toolbar>
            <TableToolbar :show-search="false">
              <span class="toolbar-info">Pause-target adherence (MMU)</span>
              <template #filters>
                <Badge
                  key-label="Collections"
                  :value="mmuView.total"
                  variant="secondary"
                  size="s"
                  borderless
                />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th class="gc-id-column">GC ID</th>
              <th class="text-end">GC Time</th>
              <th class="text-end">Pause Target</th>
              <th class="text-end">Status</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="entry in mmuView.visible"
              :key="entry.gcId"
              :class="{ 'table-danger': entry.gcTimeNanos > entry.pauseTargetNanos }"
            >
              <td>{{ entry.gcId }}</td>
              <td class="text-end">
                {{ FormattingService.formatDuration2Units(entry.gcTimeNanos) }}
              </td>
              <td class="text-end">
                {{ FormattingService.formatDuration2Units(entry.pauseTargetNanos) }}
              </td>
              <td class="text-end">
                <Badge
                  v-if="entry.gcTimeNanos > entry.pauseTargetNanos"
                  value="Exceeded"
                  variant="danger"
                  size="xs"
                  borderless
                />
                <Badge v-else value="Within" variant="success" size="xs" borderless />
              </td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="mmuView.visible.length"
              :match-count="mmuView.matchCount"
              :total="mmuView.total"
              :expanded="mmuView.expanded"
              :page-size="mmuView.pageSize"
              @toggle="mmuView.toggle"
            />
          </template>
        </DataTable>
      </template>
    </div>

    <div v-show="activeTab === 'phase-parallel'">
      <ChartDescription
        shows="Parallel GC sub-phases (jdk.GCPhaseParallel) aggregated by name across all GC worker threads and collections — e.g. Ext Root Scanning, Object Copy, Termination."
        use-case="Find which sub-phase dominates a stop-the-world pause: heavy Object Copy points at high promotion, heavy Termination at worker load imbalance."
      />
      <DisabledEventsNotice
        v-if="!phaseParallelData || phaseParallelData.length === 0"
        title="No parallel sub-phase events"
        icon="bi-layers"
        action-label="Record with the detailed GC tier, then re-record and re-import"
        :command="detailedGcCommand"
      >
        <p>
          The parallel sub-phase breakdown comes from <code>jdk.GCPhaseParallel</code>, a
          detailed-tier GC event. The bundled <code>default</code> config records GC at the
          <code>normal</code> detail level, where it is effectively <strong>off</strong>, while the
          <code>profile</code> config sets the GC detail level to <code>detailed</code> and turns it
          on.
        </p>
        <p>
          Re-record with <code>settings=profile</code> (which selects the <code>detailed</code> GC
          level), or enable the specific events inline with the command above.
        </p>

        <template #action>
          <p>
            <strong>A — inline, no extra file.</strong> Use the copyable command above: it keeps the
            bundled <code>profile</code> config and adds the detailed-tier GC events on top.
          </p>
          <p>
            <strong>B — a reusable <code>.jfc</code> overlay.</strong> Save this as
            <code>gc-detailed.jfc</code> and record with
            <code>settings=profile,settings=gc-detailed.jfc</code>:
          </p>
          <pre class="jfc-block">{{ gcDetailedJfcSnippet }}</pre>
          <p>Re-import the <code>.jfr</code> afterwards to populate the sub-phase breakdown.</p>
        </template>
      </DisabledEventsNotice>
      <DataTable v-else>
        <template #toolbar>
          <TableToolbar v-model="phaseParallelView.query" search-placeholder="Filter phases...">
            <span class="toolbar-info">GC sub-phases</span>
            <template #filters>
              <Badge
                key-label="Phases"
                :value="phaseParallelView.matchCount"
                variant="secondary"
                size="s"
                borderless
              />
            </template>
          </TableToolbar>
        </template>
        <thead>
          <tr>
            <th>Phase</th>
            <th class="text-end">Samples</th>
            <th class="text-end">Total Time</th>
            <th class="text-end">Avg Time</th>
            <th class="text-end">Max Time</th>
            <th class="text-end">% of Total</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(phase, index) in phaseParallelView.visible" :key="index">
            <td>{{ phase.name }}</td>
            <td class="text-end">{{ FormattingService.formatNumber(phase.count) }}</td>
            <td class="text-end">{{ FormattingService.formatDuration2Units(phase.totalNanos) }}</td>
            <td class="text-end">{{ FormattingService.formatDuration2Units(phase.avgNanos) }}</td>
            <td class="text-end">{{ FormattingService.formatDuration2Units(phase.maxNanos) }}</td>
            <td class="text-end">{{ phase.percentOfTotal.toFixed(1) }}%</td>
          </tr>
        </tbody>
        <template #footer>
          <TableShowMore
            :shown="phaseParallelView.visible.length"
            :match-count="phaseParallelView.matchCount"
            :total="phaseParallelView.total"
            :expanded="phaseParallelView.expanded"
            :page-size="phaseParallelView.pageSize"
            @toggle="phaseParallelView.toggle"
          />
        </template>
      </DataTable>
    </div>

    <div v-show="activeTab === 'plab'">
      <ChartDescription
        shows="G1 promotion-buffer (PLAB) evacuation statistics per young/old evacuation (jdk.G1EvacuationYoungStatistics, jdk.G1EvacuationOldStatistics) — bytes allocated for copying survivors vs. bytes wasted (alignment, refill, undo, evacuation failure), with a waste %."
        use-case="High waste % means PLABs are poorly sized — tune -XX:*PLABSize / -XX:+ResizePLAB. Non-zero failure bytes indicate to-space exhaustion (evacuation failure), the usual cause of surprise Full GCs."
      />
      <DisabledEventsNotice
        v-if="!plabData || plabData.length === 0"
        title="No G1 PLAB statistics"
        icon="bi-speedometer"
        action-label="Record with the detailed GC tier, then re-record and re-import"
        :command="detailedGcCommand"
      >
        <p>
          PLAB statistics come from <code>jdk.G1EvacuationYoungStatistics</code> and
          <code>jdk.G1EvacuationOldStatistics</code> — emitted only by the G1 collector, and part of
          the detailed GC tier. The bundled <code>default</code> config records GC at the
          <code>normal</code> detail level, where they are effectively <strong>off</strong>; the
          <code>profile</code> config sets the GC detail level to <code>detailed</code> and turns
          them on. Re-record with <code>settings=profile</code>, or enable the specific events inline
          with the command above.
        </p>
      </DisabledEventsNotice>
      <DataTable v-else>
        <template #toolbar>
          <TableToolbar v-model="plabView.query" search-placeholder="Filter by generation...">
            <span class="toolbar-info">PLAB allocation &amp; waste</span>
            <template #filters>
              <Badge key-label="Evacuations" :value="plabView.matchCount" variant="secondary" size="s" borderless />
            </template>
          </TableToolbar>
        </template>
        <thead>
          <tr>
            <th>GC ID</th>
            <th>Generation</th>
            <th class="text-end">Allocated</th>
            <th class="text-end">Used</th>
            <th class="text-end">Wasted</th>
            <th class="text-end">Waste %</th>
            <th class="text-end">Direct Alloc</th>
            <th class="text-end">Regions Refilled</th>
            <th class="text-end">PLABs Filled</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, index) in plabView.visible" :key="index">
            <td>{{ row.gcId }}</td>
            <td>
              <Badge
                :value="row.generation"
                :variant="row.generation === 'Young' ? 'success' : 'warning'"
                size="s"
              />
            </td>
            <td class="text-end">{{ FormattingService.formatBytes(row.allocated) }}</td>
            <td class="text-end">{{ FormattingService.formatBytes(row.used) }}</td>
            <td class="text-end">{{ FormattingService.formatBytes(row.totalWasted) }}</td>
            <td class="text-end">
              <Badge
                :value="row.wastePercent.toFixed(1) + '%'"
                :variant="row.wastePercent > 20 ? 'danger' : 'secondary'"
                size="s"
                borderless
              />
            </td>
            <td class="text-end">{{ FormattingService.formatBytes(row.directAllocated) }}</td>
            <td class="text-end">{{ FormattingService.formatNumber(row.regionsRefilled) }}</td>
            <td class="text-end">{{ FormattingService.formatNumber(row.numPlabsFilled) }}</td>
          </tr>
        </tbody>
        <template #footer>
          <TableShowMore
            :shown="plabView.visible.length"
            :match-count="plabView.matchCount"
            :total="plabView.total"
            :expanded="plabView.expanded"
            :page-size="plabView.pageSize"
            @toggle="plabView.toggle"
          />
        </template>
      </DataTable>
    </div>

    <div v-show="activeTab === 'pause-types'">
      <p class="pause-types-intro text-muted">
        Reference for every GC cause the JVM may emit via Java Flight Recorder. Filter by name or
        category to look up an unfamiliar cause from the
        <em>Longest Pauses</em> table.
      </p>

      <div class="pause-types-toolbar">
        <div class="input-group search-container pause-types-search">
          <span class="input-group-text"><i class="bi bi-search search-icon"></i></span>
          <input
            type="text"
            class="form-control search-input"
            placeholder="Filter by cause name…"
            v-model="pauseTypeSearch"
            autocomplete="off"
          />
          <button
            v-if="pauseTypeSearch"
            class="btn btn-outline-secondary clear-btn"
            type="button"
            @click="pauseTypeSearch = ''"
            title="Clear filter"
          >
            <i class="bi bi-x-lg"></i>
          </button>
        </div>

        <div class="pause-types-chips">
          <button
            type="button"
            class="pause-type-chip pause-type-chip--all"
            :class="{ active: pauseTypeActiveFilters.size === 0 }"
            @click="clearPauseTypeFilters"
          >
            All
          </button>
          <button
            v-for="group in pauseTypeGroups"
            :key="group.key"
            type="button"
            class="pause-type-chip"
            :class="[
              `pause-type-chip--${group.key}`,
              { active: pauseTypeActiveFilters.has(group.key) }
            ]"
            @click="togglePauseTypeFilter(group.key)"
          >
            <span class="dot"></span>{{ group.title }}
          </button>
        </div>
      </div>

      <div class="pause-types-result-count">
        Showing {{ filteredPauseTypes.length }} of {{ allPauseTypes.length }} causes
      </div>

      <EmptyState
        v-if="filteredPauseTypes.length === 0"
        icon="bi-funnel"
        title="No causes match the current filter"
      />
      <DataTable v-else table-class="pause-types-table">
        <thead>
          <tr>
            <th>Cause</th>
            <th>Category</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in pauseTypesView.visible" :key="item.name">
            <td class="pause-type-name">{{ item.name }}</td>
            <td>
              <Badge :value="item.group.shortLabel" :variant="item.group.variant" size="s" />
            </td>
            <td class="pause-type-desc">{{ item.description }}</td>
          </tr>
        </tbody>
        <template #footer>
          <TableShowMore
            :shown="pauseTypesView.visible.length"
            :match-count="pauseTypesView.matchCount"
            :total="pauseTypesView.total"
            :expanded="pauseTypesView.expanded"
            :page-size="pauseTypesView.pageSize"
            @toggle="pauseTypesView.toggle"
          />
        </template>
      </DataTable>
    </div>

    <!-- How It Works Tab -->
    <div v-show="activeTab === 'about'">
      <AboutPanel
        icon="bi-question-circle"
        title="Understanding Garbage Collection"
        subtitle="How the JVM reclaims memory — and what these charts actually measure"
      >
        <AboutCallout variant="intro">
          <p>
            The garbage collector reclaims objects no longer reachable from the running program. The
            JVM ships several collectors with different pause-versus-throughput trade-offs, but they
            share a generational model and the same fundamental tension: doing GC work
            <em>stops or competes with</em> your application. These charts measure how often that
            happens, for how long, and why.
          </p>
        </AboutCallout>

        <AboutSection icon="bi-layers" title="The Generational Heap">
          <FeatureGrid>
            <FeatureCard icon="bi-egg" variant="info" title="Eden">
              Where almost all objects are born. Cheap bump-pointer allocation; a young collection
              sweeps it whenever it fills. Most objects die here ("the weak generational
              hypothesis").
            </FeatureCard>
            <FeatureCard icon="bi-arrow-left-right" variant="success" title="Survivor">
              Two spaces that ping-pong surviving young objects, ageing them one GC at a time. The
              <em>Promotion &amp; Tenuring</em> tab visualizes this ageing.
            </FeatureCard>
            <FeatureCard icon="bi-archive" variant="warning" title="Old Generation">
              Objects that survive enough young collections are <em>promoted</em> here. Filling old
              gen triggers the expensive old/full collections and (for G1) concurrent marking.
            </FeatureCard>
            <FeatureCard icon="bi-box-seam" variant="purple" title="Metaspace">
              Native memory for class metadata — not the Java heap, but collected alongside it when
              class loaders become unreachable. See the Class Loading and NMT pages.
            </FeatureCard>
          </FeatureGrid>
        </AboutSection>

        <AboutSection icon="bi-stopwatch" title="Stop-the-World vs Concurrent">
          <FeatureGrid>
            <FeatureCard icon="bi-pause-circle" variant="danger" title="Stop-the-world (STW) pause">
              Every application thread is frozen at a safepoint while the collector works. This is
              the latency you feel; the <em>Pause Distribution</em> and <em>Longest Pauses</em> tabs
              are all about these windows.
            </FeatureCard>
            <FeatureCard icon="bi-arrow-repeat" variant="success" title="Concurrent phase">
              Work the collector does <em>while your app runs</em> (G1 marking, ZGC/Shenandoah
              relocation). It trades CPU for shorter pauses — visible on the
              <em>Concurrent Cycles</em>
              tab.
            </FeatureCard>
          </FeatureGrid>
          <AboutCallout variant="tip" title="Sum of pauses ≠ duration" icon="bi-lightbulb-fill">
            A single collection can have several STW sub-pauses around concurrent work. "Sum of
            pauses" is the total STW time charged to your app; "duration" is wall-clock
            start-to-end. For low-latency collectors the two diverge sharply — that's the whole
            point of going concurrent.
          </AboutCallout>
        </AboutSection>

        <AboutSection icon="bi-graph-up" title="Reading the Charts">
          <FeatureGrid>
            <FeatureCard icon="bi-bar-chart" variant="primary" title="Pause Distribution">
              A histogram of pause lengths. A tight cluster of short pauses is healthy; a long tail
              is what hurts p99 latency. Cross-reference the longest ones with their cause.
            </FeatureCard>
            <FeatureCard icon="bi-pie-chart" variant="success" title="GC Efficiency">
              Throughput (time in app) vs overhead (time in GC). Sustained overhead above a few
              percent means the heap is too small or allocation too high.
            </FeatureCard>
            <FeatureCard
              icon="bi-arrow-up-circle"
              variant="warning"
              title="Promotion &amp; Tenuring"
            >
              Stacked surviving bytes per survivor age. A tall, top-heavy stack means objects are
              promoted too young — survivor spaces too small or allocation spikes.
            </FeatureCard>
            <FeatureCard icon="bi-sliders" variant="info" title="G1 IHOP &amp; MMU">
              IHOP shows old-gen occupancy crossing the adaptive threshold that <em>starts</em> a
              concurrent cycle; MMU shows whether each collection stayed within its pause target.
            </FeatureCard>
          </FeatureGrid>
        </AboutSection>

        <AboutSection icon="bi-cpu" title="Collectors at a Glance">
          <FeatureGrid>
            <FeatureCard icon="bi-1-circle" variant="neutral" title="Serial">
              Single-threaded, smallest footprint. Fine for tiny heaps and CLI tools; pauses scale
              with heap size.
            </FeatureCard>
            <FeatureCard icon="bi-speedometer2" variant="warning" title="Parallel">
              Throughput-first: multiple GC threads, fully STW. Maximizes work done per CPU at the
              cost of longer pauses.
            </FeatureCard>
            <FeatureCard icon="bi-diagram-3" variant="primary" title="G1 (default)">
              Region-based, balances throughput and pause goals via a soft
              <code>MaxGCPauseMillis</code>
              target. The IHOP/MMU/tenuring tabs are G1-specific.
            </FeatureCard>
            <FeatureCard icon="bi-lightning-charge" variant="success" title="ZGC / Shenandoah">
              Concurrent, low-latency collectors with sub-millisecond pauses largely independent of
              heap size — they relocate objects while the app runs.
            </FeatureCard>
          </FeatureGrid>
        </AboutSection>

        <AboutSection icon="bi-broadcast" title="How JFR Emits This">
          <p>
            Core GC events are <strong>on by default</strong> in every JFR configuration, so this
            page works for any recording:
          </p>
          <ul>
            <li>
              <code>jdk.GarbageCollection</code> + <code>jdk.GCPhasePause</code> — one event per
              collection with cause, sum-of-pauses and the STW phase breakdown.
            </li>
            <li>
              <code>jdk.YoungGarbageCollection</code> / <code>jdk.OldGarbageCollection</code> /
              <code>jdk.G1GarbageCollection</code> — collector-specific detail.
            </li>
            <li>
              <code>jdk.GCHeapSummary</code> — before/after heap occupancy (drives the efficiency
              and difference columns); <code>jdk.GCConfiguration</code> identifies the collector.
            </li>
          </ul>
          <p>
            The deep-tuning tabs rely on events that are <strong>G1-only and/or config-gated</strong>,
            so they show an empty state on other collectors or default recordings:
            <code>jdk.TenuringDistribution</code>, <code>jdk.G1AdaptiveIHOP</code>,
            <code>jdk.G1MMU</code>, <code>jdk.GCCPUTime</code>.
          </p>
          <p>
            Reference processing (<code>jdk.GCReferenceStatistics</code>) has its own
            <strong>Reference Processing</strong> page in the Garbage Collection menu.
          </p>
        </AboutSection>
      </AboutPanel>
    </div>

    <!-- GC Event Details Modal -->
    <GCEventDetailsModal
      :event="selectedConcurrentEvent"
      modal-id="gcEventDetailsModal"
      :show="showEventDetailsModal"
      @update:show="showEventDetailsModal = $event"
    />

    <!-- GC Pause Details Modal -->
    <GCPauseDetailsModal
      :event="selectedPauseEvent"
      modal-id="gcPauseDetailsModal"
      :show="showPauseDetailsModal"
      @update:show="showPauseDetailsModal = $event"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import ApexCharts from 'apexcharts';
import PageHeader from '@/components/layout/PageHeader.vue';
import GCMetricsStatsRow from '@/components/gc/GCMetricsStatsRow.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import TabBar from '@/components/TabBar.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import DisabledEventsNotice from '@/components/alerts/DisabledEventsNotice.vue';
import Badge from '@shared/components/Badge.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import TableShowMore from '@/components/table/TableShowMore.vue';
import { useTableView } from '@/composables/useTableView';
import type { Variant } from '@shared/types/ui';
import GCEventDetailsModal from '@/components/gc/GCEventDetailsModal.vue';
import GCPauseDetailsModal from '@/components/gc/GCPauseDetailsModal.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import ChartDescription from '@/components/ChartDescription.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import type { IhopData, TenuringData } from '@/services/api/model/GCTuningModels';
import type GCPhaseParallelAggregate from '@/services/api/model/GCPhaseParallelAggregate';
import type G1PlabStatistics from '@/services/api/model/G1PlabStatistics';
import ProfileGCClient from '@/services/api/ProfileGCClient';
import GCOverviewData from '@/services/api/model/GCOverviewData';
import ConcurrentEvent from '@/services/api/model/ConcurrentEvent';
import GCEvent from '@/services/api/model/GCEvent';
import FormattingService from '@shared/services/FormattingService';
import {
  getConcurrentBadgeValue,
  getConcurrentBadgeVariant,
  getGenerationTypeBadgeVariant
} from '@/services/api/model/GarbageCollectionUtils';
import { GarbageCollectionCauseDescriptions } from '@/services/api/model/GarbageCollectionCauseDescriptions';
import '@/styles/shared-components.css';

const route = useRoute();

const detailedGcCommand =
  'java -XX:StartFlightRecording=settings=profile,jdk.GCPhaseParallel#enabled=true,jdk.G1EvacuationYoungStatistics#enabled=true,jdk.G1EvacuationOldStatistics#enabled=true,filename=app.jfr,dumponexit=true -jar app.jar';

const gcDetailedJfcSnippet = `<?xml version="1.0" encoding="UTF-8"?>
<configuration version="2.0">
  <event name="jdk.GCPhaseParallel">
    <setting name="enabled">true</setting>
    <setting name="threshold">0 ms</setting>
  </event>
  <event name="jdk.G1EvacuationYoungStatistics">
    <setting name="enabled">true</setting>
  </event>
  <event name="jdk.G1EvacuationOldStatistics">
    <setting name="enabled">true</setting>
  </event>
</configuration>`;

const loading = ref(true);
const error = ref<string | null>(null);

// Modal state
const showEventDetailsModal = ref(false);
const selectedConcurrentEvent = ref<ConcurrentEvent | null>(null);
const showPauseDetailsModal = ref(false);
const selectedPauseEvent = ref<GCEvent | null>(null);

// Tabs configuration for GC Analysis
const gcTabs = [
  { id: 'distribution', label: 'Pause Distribution', icon: 'bar-chart' },
  { id: 'efficiency', label: 'GC Efficiency', icon: 'pie-chart' },
  { id: 'events', label: 'Longest Pauses', icon: 'table' },
  { id: 'concurrent-cycles', label: 'Concurrent Cycles', icon: 'layers' },
  { id: 'tenuring', label: 'Promotion & Tenuring', icon: 'arrow-up-circle' },
  { id: 'ihop', label: 'G1 IHOP & CPU', icon: 'sliders' },
  { id: 'phase-parallel', label: 'Sub-Phases', icon: 'layers' },
  { id: 'plab', label: 'G1 PLAB', icon: 'speedometer' },
  { id: 'pause-types', label: 'Pause Types', icon: 'info-circle' },
  { id: 'about', label: 'How It Works', icon: 'book' }
];
const activeTab = ref(gcTabs[0].id);

// Pause Types tab — searchable, category-chip-filterable list of every GC cause
// the JVM can emit. Descriptions come from the shared map, so the per-event tooltip
// on the Longest Pauses table and the rows below cannot drift.
type PauseTypeGroup = {
  key: string;
  title: string;
  shortLabel: string;
  variant: Variant;
  causes: string[];
};

const pauseTypeGroups: PauseTypeGroup[] = [
  {
    key: 'allocation',
    title: 'Allocation-Driven Pauses',
    shortLabel: 'Allocation',
    variant: 'indigo',
    causes: [
      'Allocation Failure',
      'G1 Evacuation Pause',
      'G1 Humongous Allocation',
      'To-space Exhausted',
      'Promotion Failed'
    ]
  },
  {
    key: 'concurrent',
    title: 'Concurrent Cycles',
    shortLabel: 'Concurrent',
    variant: 'green',
    causes: ['Concurrent Mark Start', 'Concurrent Mode Failure']
  },
  {
    key: 'pressure',
    title: 'Memory Pressure & Failure Modes',
    shortLabel: 'Pressure',
    variant: 'danger',
    causes: ['Last Ditch Collection', 'Metadata GC Threshold', 'Metadata GC Clear Soft References']
  },
  {
    key: 'tuning',
    title: 'JVM-Initiated Tuning',
    shortLabel: 'Tuning',
    variant: 'purple',
    causes: ['Ergonomics', 'Proactive', 'Warmup', 'Timer']
  },
  {
    key: 'external',
    title: 'External / Diagnostic Triggers',
    shortLabel: 'External',
    variant: 'orange',
    causes: [
      'System.gc()',
      'Diagnostic Command',
      'JFR Periodic',
      'Heap Inspection/Dump',
      'GCLocker Initiated GC'
    ]
  }
];

const allPauseTypes = (() => {
  const lookup = new Map(
    GarbageCollectionCauseDescriptions.getAllCauses().map(c => [c.name, c.description])
  );
  return pauseTypeGroups.flatMap(group =>
    group.causes.map(name => ({
      name,
      description: lookup.get(name) ?? '',
      group
    }))
  );
})();

const pauseTypeSearch = ref('');
const pauseTypeActiveFilters = ref<Set<string>>(new Set());

const filteredPauseTypes = computed(() => {
  const q = pauseTypeSearch.value.trim().toLowerCase();
  return allPauseTypes.filter(item => {
    if (
      pauseTypeActiveFilters.value.size > 0 &&
      !pauseTypeActiveFilters.value.has(item.group.key)
    ) {
      return false;
    }
    if (q && !item.name.toLowerCase().includes(q)) {
      return false;
    }
    return true;
  });
});

const togglePauseTypeFilter = (key: string) => {
  const next = new Set(pauseTypeActiveFilters.value);
  if (next.has(key)) {
    next.delete(key);
  } else {
    next.add(key);
  }
  pauseTypeActiveFilters.value = next;
};

const clearPauseTypeFilters = () => {
  pauseTypeActiveFilters.value = new Set();
};

// Chart instances
let distributionChart: ApexCharts | null = null;
let efficiencyChart: ApexCharts | null = null;
let tenuringChart: ApexCharts | null = null;

// GC Overview Data
const gcOverviewData = ref<GCOverviewData>();

// Client initialization - will be set after workspace/project IDs are available
let client: ProfileGCClient;

// GC Summary data (computed from real data)
const gcSummary = computed(() => {
  const data = gcOverviewData.value;
  if (!data?.header) {
    return null;
  }
  const header = data.header;
  return {
    totalCollections: FormattingService.formatNumber(header.totalCollections),
    youngCollections: FormattingService.formatNumber(header.youngCollections),
    oldCollections: FormattingService.formatNumber(header.oldCollections),
    maxPauseTime: FormattingService.formatDuration2Units(header.maxPauseTime),
    p99PauseTime: FormattingService.formatDuration2Units(header.p99PauseTime),
    p95PauseTime: FormattingService.formatDuration2Units(header.p95PauseTime),
    totalMemoryFreed: FormattingService.formatBytes(header.totalMemoryFreed),
    avgMemoryFreed: FormattingService.formatBytes(header.avgMemoryFreed),
    gcThroughput: FormattingService.formatPercentage(header.gcThroughput / 100),
    gcOverhead: FormattingService.formatPercentage(header.gcOverhead / 100),
    applicationTime: FormattingService.formatDuration2Units(data.efficiency.applicationTime),
    totalGcTime: FormattingService.formatDuration2Units(data.efficiency.gcTime),
    collectionFrequency: `${header.collectionFrequency.toFixed(2)} GC/s`,
    manualGCTime: FormattingService.formatDuration2Units(header.manualGCCalls.totalTime),
    systemGCCalls: FormattingService.formatNumber(header.manualGCCalls.systemGCCalls),
    diagnosticCommandCalls: FormattingService.formatNumber(
      header.manualGCCalls.diagnosticCommandCalls
    )
  };
});

const formatDifference = (beforeGC: number, afterGC: number) => {
  const difference = afterGC - beforeGC;
  const absValue = Math.abs(difference);
  const formattedValue = FormattingService.formatBytes(absValue);

  if (difference === 0) {
    return formattedValue;
  }

  return difference > 0 ? `+${formattedValue}` : `-${formattedValue}`;
};

const getDifferenceBadgeVariant = (beforeGC: number, afterGC: number): Variant => {
  const difference = afterGC - beforeGC;

  if (difference === 0) {
    return 'secondary';
  }

  return difference > 0 ? 'danger' : 'success';
};

const getDifferenceBarClass = (beforeGC: number, afterGC: number) => {
  const difference = afterGC - beforeGC;

  if (difference < 0) {
    return 'bg-success'; // Memory decreased (good) - green
  } else {
    return 'bg-danger'; // Memory increased (bad) - red
  }
};

const getDifferencePercentage = (beforeGC: number, afterGC: number) => {
  if (beforeGC === 0) return 0;
  const difference = Math.abs(afterGC - beforeGC);
  return Math.min((difference / beforeGC) * 100, 100);
};

const getGCCauseTooltip = (cause: string) => {
  return GarbageCollectionCauseDescriptions.getTooltipContent(cause);
};

const showEventDetails = (event: GCEvent) => {
  selectedPauseEvent.value = event;
  showPauseDetailsModal.value = true;
};

const showConcurrentEventDetails = (event: ConcurrentEvent) => {
  selectedConcurrentEvent.value = event;
  showEventDetailsModal.value = true;
};

// Create pause distribution chart
const createDistributionChart = async () => {
  await nextTick();

  const chartElement = document.getElementById('gc-pause-distribution-chart');
  if (!chartElement) {
    console.warn('Distribution chart element not found');
    return;
  }

  const distributionData = gcOverviewData.value?.pauseDistribution?.buckets;

  if (!distributionData || distributionData.length === 0) {
    console.warn('No pause distribution data available');
    return;
  }

  const options = {
    chart: {
      type: 'bar' as const,
      height: 380,
      fontFamily: 'inherit',
      animations: {
        enabled: true,
        easing: 'easeinout',
        speed: 800
      },
      toolbar: {
        show: false
      }
    },
    series: [
      {
        name: 'GC Events',
        data:
          distributionData?.map(bucket => ({
            x: bucket.range,
            y: bucket.count
          })) || []
      }
    ],
    plotOptions: {
      bar: {
        horizontal: false,
        columnWidth: '70%',
        borderRadius: 4,
        dataLabels: {
          position: 'top'
        }
      }
    },
    dataLabels: {
      enabled: true,
      formatter: (val: number) => val.toString(),
      offsetY: -20,
      style: {
        fontSize: '10px',
        colors: ['#304758']
      }
    },
    xaxis: {
      title: {
        text: 'Pause Time Range',
        style: {
          fontSize: '12px'
        }
      },
      labels: {
        style: {
          fontSize: '10px'
        }
      }
    },
    yaxis: {
      title: {
        text: 'Number of Events',
        style: {
          fontSize: '12px'
        }
      },
      labels: {
        style: {
          fontSize: '10px'
        }
      }
    },
    colors: ['#007bff'],
    tooltip: {
      y: {
        formatter: (value: number) => value + ' events'
      }
    },
    grid: {
      borderColor: '#e7e7e7',
      strokeDashArray: 3
    }
  };

  if (distributionChart) {
    distributionChart.destroy();
  }

  distributionChart = new ApexCharts(chartElement, options);
  distributionChart.render();
};

// Create efficiency pie chart
const createEfficiencyChart = async () => {
  await nextTick();

  const chartElement = document.getElementById('gc-efficiency-pie-chart');
  if (!chartElement) return;

  const efficiency = gcOverviewData.value?.efficiency;
  const throughput = efficiency?.throughputPercentage;
  const overhead = efficiency?.overheadPercentage;

  const options = {
    chart: {
      type: 'donut' as const,
      height: 380,
      fontFamily: 'inherit'
    },
    series: [throughput || 0, overhead || 0],
    labels: ['Application Time', 'GC Time'],
    colors: ['#28a745', '#ffc107'],
    plotOptions: {
      pie: {
        donut: {
          size: '70%',
          labels: {
            show: true,
            total: {
              show: true,
              label: 'Throughput',
              formatter: () => `${throughput?.toFixed(1) || '0'}%`
            }
          }
        }
      }
    },
    legend: {
      position: 'bottom' as const
    },
    tooltip: {
      y: {
        formatter: (value: number) => value.toFixed(1) + '%'
      }
    }
  } as ApexCharts.ApexOptions;

  if (efficiencyChart) {
    efficiencyChart.destroy();
  }

  efficiencyChart = new ApexCharts(chartElement, options);
  efficiencyChart.render();
};

// Deep-tuning tab data, loaded lazily on first visit so the main page stays fast.
const tenuringData = ref<TenuringData | null>(null);
const ihopData = ref<IhopData | null>(null);
const phaseParallelData = ref<GCPhaseParallelAggregate[] | null>(null);
const plabData = ref<G1PlabStatistics[] | null>(null);

const ihopThresholdSeries = computed<number[][]>(
  () => ihopData.value?.ihopTimeline?.series?.[0]?.data ?? []
);
const ihopOccupancySeries = computed<number[][]>(
  () => ihopData.value?.ihopTimeline?.series?.[1]?.data ?? []
);
const hasIhopTimeline = computed(() => ihopOccupancySeries.value.some(point => point[1] > 0));

// Full-text filtering + "show 50 then all" views for the GC tables. Numeric-only tables
// (tenuring by GC id, CPU time, MMU) get a row window but no search box.
const longestPausesView = useTableView(() => gcOverviewData.value?.longestPauses ?? [], {
  searchableText: event => event.cause
});
const concurrentEventsView = useTableView(
  () => gcOverviewData.value?.longestConcurrentEvents ?? [],
  {
    searchableText: event => event.collectorName ?? ''
  }
);
const tenuringGcsView = useTableView(() => tenuringData.value?.gcs ?? []);
const cpuTimesView = useTableView(() => ihopData.value?.cpuTimes ?? []);
const mmuView = useTableView(() => ihopData.value?.mmu ?? []);
const phaseParallelView = useTableView(() => phaseParallelData.value ?? [], {
  searchableText: phase => phase.name
});
const plabView = useTableView(() => plabData.value ?? [], {
  searchableText: row => row.generation
});
const pauseTypesView = useTableView(() => filteredPauseTypes.value);

// Collapse the pause-types window whenever the existing search/chip filter changes.
watch(filteredPauseTypes, () => {
  pauseTypesView.expanded = false;
});

const loadTenuringData = async () => {
  if (!tenuringData.value) {
    try {
      if (!client) {
        client = new ProfileGCClient(route.params.profileId as string);
      }
      tenuringData.value = await client.getTenuring();
    } catch (err) {
      console.error('Error loading tenuring data:', err);
    }
  }
  createTenuringChart();
};

// Stacked-bar of surviving bytes per survivor age, one bar per collection. Each distinct age
// becomes a series so ApexCharts stacks them; gcId is the category axis.
const createTenuringChart = async () => {
  const gcs = tenuringData.value?.gcs ?? [];
  if (gcs.length === 0) {
    return;
  }

  await nextTick();
  const chartElement = document.getElementById('gc-tenuring-stacked-chart');
  if (!chartElement) {
    return;
  }

  const ages = [...new Set(gcs.flatMap(gc => gc.buckets.map(bucket => bucket.age)))].sort(
    (a, b) => a - b
  );
  const series = ages.map(age => ({
    name: `Age ${age}`,
    data: gcs.map(gc => gc.buckets.find(bucket => bucket.age === age)?.sizeBytes ?? 0)
  }));

  const options = {
    chart: {
      type: 'bar' as const,
      height: 380,
      stacked: true,
      fontFamily: 'inherit',
      toolbar: { show: false }
    },
    series,
    plotOptions: {
      bar: { horizontal: false, columnWidth: '70%' }
    },
    dataLabels: { enabled: false },
    xaxis: {
      categories: gcs.map(gc => gc.gcId),
      title: { text: 'GC ID', style: { fontSize: '12px' } },
      labels: { style: { fontSize: '10px' } }
    },
    yaxis: {
      title: { text: 'Surviving Bytes', style: { fontSize: '12px' } },
      labels: {
        style: { fontSize: '10px' },
        formatter: (value: number) => FormattingService.formatBytesShort(value)
      }
    },
    legend: { position: 'bottom' as const },
    tooltip: {
      y: { formatter: (value: number) => FormattingService.formatBytes(value) }
    },
    grid: { borderColor: '#e7e7e7', strokeDashArray: 3 }
  } as ApexCharts.ApexOptions;

  if (tenuringChart) {
    tenuringChart.destroy();
  }
  tenuringChart = new ApexCharts(chartElement, options);
  tenuringChart.render();
};

const loadIhopData = async () => {
  if (ihopData.value) {
    return;
  }
  try {
    if (!client) {
      client = new ProfileGCClient(route.params.profileId as string);
    }
    ihopData.value = await client.getIhop();
  } catch (err) {
    console.error('Error loading IHOP data:', err);
  }
};

const loadPhaseParallelData = async () => {
  if (phaseParallelData.value) {
    return;
  }
  try {
    if (!client) {
      client = new ProfileGCClient(route.params.profileId as string);
    }
    phaseParallelData.value = await client.getPhaseParallel();
  } catch (err) {
    console.error('Error loading GC parallel sub-phase data:', err);
  }
};

const loadPlabData = async () => {
  if (plabData.value) {
    return;
  }
  try {
    if (!client) {
      client = new ProfileGCClient(route.params.profileId as string);
    }
    plabData.value = await client.getPlabStatistics();
  } catch (err) {
    console.error('Error loading G1 PLAB statistics data:', err);
  }
};

// Re-render the relevant chart when the user switches into a chart-backed tab.
watch(activeTab, newId => {
  if (newId === 'distribution') {
    createDistributionChart();
  } else if (newId === 'efficiency') {
    createEfficiencyChart();
  } else if (newId === 'tenuring') {
    loadTenuringData();
  } else if (newId === 'ihop') {
    loadIhopData();
  } else if (newId === 'phase-parallel') {
    loadPhaseParallelData();
  } else if (newId === 'plab') {
    loadPlabData();
  }
});

// Load data on component mount
// Load GC data from API
const loadGCData = async () => {
  try {
    loading.value = true;
    error.value = null;

    // Initialize client if needed
    if (!client) {
      client = new ProfileGCClient(route.params.profileId as string);
    }

    // Load overview data from API
    gcOverviewData.value = await client.getOverview();

    // Wait for DOM updates
    await nextTick();

    // Create the distribution chart by default since it's the first tab
    // Use a timeout to ensure the DOM is fully rendered
    setTimeout(() => {
      createDistributionChart();
    }, 100);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading GC data:', err);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadGCData();
});

onUnmounted(() => {
  // Cleanup charts
  if (distributionChart) {
    distributionChart.destroy();
  }
  if (efficiencyChart) {
    efficiencyChart.destroy();
  }
  if (tenuringChart) {
    tenuringChart.destroy();
  }
});
</script>

<style scoped>
/* Promotion & Tenuring / IHOP tabs */
.gc-id-column {
  width: 90px;
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

.toolbar-info {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--color-text);
}

.tenuring-buckets {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.ihop-chart-container {
  width: 100%;
}

.loading-overlay,
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}

/* Charts */
.chart-container {
  height: 400px;
  width: 100%;
}

/* Efficiency Stats */
.efficiency-stats {
  padding: 1rem;
  background-color: var(--color-light);
  border-radius: 8px;
  height: 400px;
}

.stat-item {
  display: flex;
  flex-direction: column;
}

.stat-label {
  font-size: 0.8rem;
  color: var(--color-text-muted);
  margin-bottom: 0.25rem;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.stat-value {
  font-size: 1.1rem;
  font-weight: 600;
  margin-bottom: 0.25rem;
}

.progress {
  height: 6px;
  border-radius: 3px;
}

/* GC Cause Tooltips */
.gc-cause-badge {
  cursor: help;
}

/* Cause Cell Styles */
.cause-cell {
  display: flex;
  flex-direction: column;
}

.timestamp-path {
  font-size: 0.75rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 250px;
}

/* Responsive Design */
@media (max-width: 768px) {
  .chart-container {
    height: 300px;
  }

  .efficiency-stats {
    height: auto;
    margin-top: 1rem;
  }
}

/* Pause Types tab — searchable, chip-filterable reference table. */
.pause-types-intro {
  font-size: 0.88rem;
  margin-bottom: 1rem;
}

.pause-types-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  align-items: center;
  margin-bottom: 0.75rem;
}

.pause-types-search {
  flex: 1;
  min-width: 240px;
  max-width: 360px;
}

.pause-types-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.pause-type-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.35rem 0.75rem;
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  font-size: 0.75rem;
  color: var(--color-text);
  cursor: pointer;
  user-select: none;
  transition:
    background-color 0.12s,
    border-color 0.12s,
    color 0.12s;
}

.pause-type-chip:hover {
  border-color: var(--chip-color, var(--color-primary));
  color: var(--color-dark);
}

.pause-type-chip.active {
  background: var(--chip-color, var(--color-primary));
  border-color: var(--chip-color, var(--color-primary));
  color: #fff;
}

.pause-type-chip .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--chip-color, var(--color-primary));
}

.pause-type-chip.active .dot {
  background: #fff;
}

.pause-type-chip--all {
  --chip-color: var(--color-secondary);
}
.pause-type-chip--allocation {
  --chip-color: var(--color-primary);
}
.pause-type-chip--concurrent {
  --chip-color: var(--color-success);
}
.pause-type-chip--pressure {
  --chip-color: var(--color-danger);
}
.pause-type-chip--tuning {
  --chip-color: var(--color-violet);
}
.pause-type-chip--external {
  --chip-color: var(--color-amber);
}

.pause-types-result-count {
  font-size: 0.78rem;
  color: var(--color-text-muted);
  margin-bottom: 0.5rem;
}

.pause-types-table .pause-type-name {
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  font-weight: 600;
  color: var(--color-dark);
  white-space: nowrap;
  width: 220px;
}

.pause-types-table .pause-type-desc {
  color: var(--color-text-muted);
  line-height: 1.5;
}
</style>
