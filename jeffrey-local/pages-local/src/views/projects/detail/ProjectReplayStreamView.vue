<template>
  <PageHeader
    title="Replay Stream"
    description="Replay historical JFR events from dumped recording files."
    icon="bi-collection-play"
  >
    <!-- Summary Panel -->
    <div
      class="summary-panel mb-3"
      :class="{
        'summary-panel--empty': !session,
        'summary-panel--connected': replaying
      }"
    >
      <!-- Empty state -->
      <template v-if="!session">
        <div class="summary-row">
          <i class="bi bi-gear summary-bar-icon"></i>
          <span class="summary-bar-placeholder">
            No replay configured — click Configure to select sessions and event types
          </span>
          <div class="summary-actions">
            <button class="summary-btn summary-btn--configure" @click="showConfigDrawer = true">
              <i class="bi bi-gear"></i> Configure
            </button>
            <button class="summary-btn summary-btn--primary" disabled>
              <i class="bi bi-play-fill"></i> Start Replay
            </button>
          </div>
        </div>
      </template>

      <!-- Configured / Replaying state -->
      <template v-else>
        <!-- Header row: Actions -->
        <div class="summary-row">
          <div class="summary-segment" style="flex: 1">
            <span class="summary-dot" :class="replaying ? 'dot-active dot-pulse' : completed ? 'dot-completed' : 'dot-idle'"></span>
            <span class="summary-status-text">{{ statusText }}</span>
          </div>
          <div class="summary-actions">
            <button
              class="summary-btn summary-btn--configure"
              :disabled="replaying"
              @click="showConfigDrawer = true"
            >
              <i class="bi bi-gear"></i> Configure
            </button>
            <button
              v-if="!replaying"
              class="summary-btn summary-btn--primary"
              :disabled="eventTypes.length === 0 || !session"
              @click="startReplay"
            >
              <i class="bi bi-play-fill"></i> Start Replay
            </button>
            <button
              v-else
              class="summary-btn summary-btn--danger"
              @click="stopReplay"
            >
              <i class="bi bi-stop-fill"></i> Stop
            </button>
            <button
              class="summary-btn summary-btn--ghost"
              :disabled="events.length === 0 && !session"
              @click="clearAll"
            >
              <i class="bi bi-arrow-counterclockwise"></i>
            </button>
          </div>
        </div>

        <!-- Config details grid -->
        <div class="summary-details">
          <div class="summary-detail-card">
            <div class="summary-detail-icon"><i class="bi bi-collection-play"></i></div>
            <div class="summary-detail-body">
              <div class="summary-detail-label">Session</div>
              <div class="summary-detail-value" :title="session?.sessionInstance ? `${session.id}\n${session.sessionInstance}` : session?.id">
                {{ session?.id }}
              </div>
              <div v-if="session?.sessionInstance" class="summary-detail-sub">{{ session.sessionInstance }}</div>
            </div>
          </div>

          <div class="summary-detail-card">
            <div class="summary-detail-icon summary-detail-icon--events"><i class="bi bi-lightning"></i></div>
            <div class="summary-detail-body">
              <div class="summary-detail-label">Events</div>
              <div v-if="eventTypes.length === 0" class="summary-detail-sub" style="font-style:italic">No events selected</div>
              <div v-else class="summary-tags">
                <span
                  v-for="et in visibleEventTags"
                  :key="et"
                  class="summary-tag"
                  :class="'summary-tag--' + getEventTypePrefix(et)"
                >{{ et }}</span>
                <a v-if="overflowCount > 0 && !eventTagsExpanded" href="#" class="summary-overflow" @click.prevent="eventTagsExpanded = true">+{{ overflowCount }} more</a>
                <a v-if="eventTagsExpanded && overflowCount > 0" href="#" class="summary-overflow" @click.prevent="eventTagsExpanded = false">Show less</a>
              </div>
            </div>
          </div>

          <div class="summary-detail-card">
            <div class="summary-detail-icon summary-detail-icon--time"><i class="bi bi-clock"></i></div>
            <div class="summary-detail-body">
              <div class="summary-detail-label">Time Range</div>
              <div class="summary-time-lines">
                <div class="summary-time-line">
                  <span class="summary-time-prefix">From</span>
                  <span class="summary-time-value">{{ timeSummaryStart }}</span>
                </div>
                <div class="summary-time-line">
                  <span class="summary-time-prefix">To</span>
                  <span class="summary-time-value">{{ timeSummaryEnd }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- Status Strip (visible when replaying or has events) -->
    <div v-if="replaying || events.length > 0" class="status-strip mb-3">
      <div class="status-strip-item">
        <span class="status-strip-dot" :class="replaying ? 'dot-active' : completed ? 'dot-completed' : 'dot-idle'"></span>
        <span :class="replaying ? 'status-strip-connected' : ''">{{ statusText }}</span>
      </div>
      <span class="status-strip-item">
        <i class="bi bi-collection"></i> {{ events.length }} events
      </span>
      <span class="status-strip-item">
        <i class="bi bi-stack"></i> {{ batchCount }} batches
      </span>
      <span v-if="lastBatchTime" class="status-strip-item">
        <i class="bi bi-clock"></i> Last batch: {{ FormattingService.formatTimestamp(lastBatchTime) }}
      </span>
    </div>

    <!-- Events Table -->
    <EmptyState
      v-if="events.length === 0"
      title="No events"
      :description="
        replaying
          ? 'Reading events from recording files...'
          : 'Select a session and click Start Replay to read historical events.'
      "
      icon="bi-collection-play"
    />

    <div v-else class="table-responsive">
      <table class="table table-sm table-hover mb-0">
        <thead>
          <tr>
            <th style="width: 180px">Timestamp</th>
            <th style="width: 250px">Event Type</th>
            <th>Fields</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(event, index) in displayedEvents"
            :key="index"
          >
            <td class="text-nowrap">
              <code>{{ FormattingService.formatTimestamp(event.timestamp) }}</code>
            </td>
            <td>
              <Badge :value="event.eventType" variant="primary" size="s" />
            </td>
            <td>
              <div class="fields-container">
                <span
                  v-for="(value, key) in event.fields"
                  :key="key"
                  class="field-tag"
                >
                  <span class="field-key">{{ key }}</span>=<span class="field-value">{{ Utils.typedValueToDisplay(value) }}</span>
                </span>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Show More -->
    <div v-if="events.length > maxDisplayed" class="text-center mt-2">
      <small class="text-muted">
        Showing {{ maxDisplayed }} of {{ events.length }} events.
        <a href="#" @click.prevent="maxDisplayed += 200">Show more</a>
      </small>
    </div>
  </PageHeader>

  <ReplayConfigDrawer
    v-if="workspaceId && projectId"
    v-model:show="showConfigDrawer"
    :workspace-id="workspaceId"
    :project-id="projectId"
    :config="currentConfig"
    @apply="onConfigApply"
  />
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import Badge from '@/components/Badge.vue'
import ReplayConfigDrawer from '@/components/streaming/ReplayConfigDrawer.vue'
import type { ReplayStreamConfig, ReplayStartMode, ReplayEndMode, SelectedSession } from '@/components/streaming/ReplayConfigDrawer.vue'
import FormattingService from '@/services/FormattingService'
import ReplayStreamClient from '@/services/api/ReplayStreamClient'
import type { StreamingEvent } from '@/services/api/EventStreamingClient'
import { getEventTypePrefix } from '@/services/EventTypeCatalog'
import Utils from '@/services/Utils'
import { useNavigation } from '@/composables/useNavigation'

const MAX_VISIBLE_TAGS = 3

const { workspaceId, projectId } = useNavigation()

const showConfigDrawer = ref(false)
const eventTagsExpanded = ref(false)
const session = ref<SelectedSession | null>(null)
const eventTypes = ref<string[]>([])
const startMode = ref<ReplayStartMode>('beginning')
const startTimeInput = ref('')
const endMode = ref<ReplayEndMode>('latest')
const endTimeInput = ref('')
const replaying = ref(false)
const completed = ref(false)
const events = ref<StreamingEvent[]>([])
const batchCount = ref(0)
const lastBatchTime = ref<number | null>(null)
const maxDisplayed = ref(200)
const maxEvents = ref(1000)

let client: ReplayStreamClient | null = null

const currentConfig = computed<ReplayStreamConfig>(() => ({
  session: session.value ? { ...session.value } : null,
  eventTypes: eventTypes.value,
  startMode: startMode.value,
  startTime: startTimeInput.value,
  endMode: endMode.value,
  endTime: endTimeInput.value,
  maxEvents: maxEvents.value
}))

const statusText = computed(() => {
  if (replaying.value) return 'Replaying...'
  if (completed.value) return 'Replay Complete'
  return 'Ready to replay'
})

const visibleEventTags = computed(() =>
  eventTagsExpanded.value ? eventTypes.value : eventTypes.value.slice(0, MAX_VISIBLE_TAGS)
)
const overflowCount = computed(() => Math.max(0, eventTypes.value.length - MAX_VISIBLE_TAGS))

function datetimeLocalToMillis(value: string): number {
  return new Date(value).getTime()
}

const timeSummaryStart = computed(() => {
  if (startMode.value === 'beginning') return 'Beginning'
  if (startMode.value === 'custom' && startTimeInput.value) {
    return FormattingService.formatDateTime(new Date(startTimeInput.value))
  }
  return 'Beginning'
})

const timeSummaryEnd = computed(() => {
  if (endMode.value === 'latest') return 'Latest event'
  if (endMode.value === 'custom' && endTimeInput.value) {
    return FormattingService.formatDateTime(new Date(endTimeInput.value))
  }
  return 'Latest event'
})

const displayedEvents = computed(() => {
  return events.value.slice(-maxDisplayed.value).reverse()
})

function onConfigApply(config: ReplayStreamConfig) {
  session.value = config.session ? { ...config.session } : null
  eventTypes.value = config.eventTypes
  startMode.value = config.startMode
  startTimeInput.value = config.startTime
  endMode.value = config.endMode
  endTimeInput.value = config.endTime
  maxEvents.value = config.maxEvents
  completed.value = false
}

function startReplay() {
  if (!session.value || !workspaceId.value || !projectId.value) return

  client = new ReplayStreamClient(workspaceId.value, projectId.value)

  const options: { startTime?: number; endTime?: number } = {}
  if (startMode.value === 'beginning') {
    options.startTime = 0
  } else if (startMode.value === 'custom' && startTimeInput.value) {
    options.startTime = datetimeLocalToMillis(startTimeInput.value)
  }
  if (endMode.value === 'custom' && endTimeInput.value) {
    options.endTime = datetimeLocalToMillis(endTimeInput.value)
  }

  // Fresh replay → clean slate
  events.value = []
  batchCount.value = 0
  lastBatchTime.value = null
  completed.value = false

  client.replay(
    session.value.id,
    eventTypes.value,
    (batch) => {
      batchCount.value++
      lastBatchTime.value = Date.now()
      events.value.push(...batch)
      const limit = maxEvents.value
      if (events.value.length > limit) {
        events.value = events.value.slice(-limit)
      }
    },
    () => {
      replaying.value = false
      completed.value = true
    },
    () => {
      replaying.value = false
      completed.value = true
    },
    options
  )

  replaying.value = true
}

function stopReplay() {
  client?.cancel()
  client = null
  replaying.value = false
}

function clearAll() {
  stopReplay()
  session.value = null
  eventTypes.value = []
  startMode.value = 'beginning'
  startTimeInput.value = ''
  endMode.value = 'latest'
  endTimeInput.value = ''
  events.value = []
  batchCount.value = 0
  lastBatchTime.value = null
  maxDisplayed.value = 200
  maxEvents.value = 1000
  completed.value = false
}

onUnmounted(() => {
  stopReplay()
})
</script>

<style scoped>
/* ===== Summary Panel ===== */
.summary-panel {
  padding: 12px 16px;
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}

.summary-panel--empty {
  border-style: dashed;
  background: var(--color-white);
}

.summary-panel--connected {
  border-color: rgba(0, 210, 122, 0.3);
  background: var(--color-white);
}

.summary-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.summary-bar-icon {
  color: var(--color-muted);
  font-size: 0.85rem;
}

.summary-bar-placeholder {
  font-size: 0.8rem;
  color: var(--color-muted);
  font-style: italic;
  flex: 1;
}

.summary-segment {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.8rem;
  color: var(--color-muted);
  min-width: 0;
}

.summary-status-text {
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--color-body);
}

.summary-details {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 10px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border-light);
}

.summary-detail-card {
  display: flex;
  gap: 10px;
  padding: 10px 12px;
  background: var(--color-white);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
}

.summary-detail-icon {
  width: 30px;
  height: 30px;
  border-radius: var(--radius-sm);
  background: var(--color-primary-light);
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.85rem;
  flex-shrink: 0;
}

.summary-detail-icon--events {
  background: var(--color-amber-light);
  color: var(--color-amber-text);
}

.summary-detail-icon--time {
  background: var(--color-teal-light);
  color: var(--color-teal);
}

.summary-detail-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.summary-detail-label {
  font-size: 0.68rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-muted);
}

.summary-detail-value {
  font-family: var(--font-monospace);
  font-size: 0.78rem;
  color: var(--color-body);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.summary-detail-sub {
  font-size: 0.7rem;
  color: var(--color-muted);
}

.summary-time-lines {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.summary-time-line {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 0.78rem;
}

.summary-time-prefix {
  font-size: 0.68rem;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--color-primary);
  min-width: 34px;
}

.summary-time-value {
  font-weight: 500;
  color: var(--color-body);
}

.summary-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.summary-tag {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  font-weight: 500;
  font-family: var(--font-monospace);
  white-space: nowrap;
}

.summary-tag--jdk { background: var(--color-blue-bg); color: var(--color-blue-text); }
.summary-tag--jeffrey { background: var(--color-violet-light); color: var(--color-violet); }
.summary-tag--profiler { background: var(--color-teal-light); color: var(--color-teal); }
.summary-tag--custom { background: var(--color-amber-light); color: var(--color-amber-text); }

.summary-overflow {
  font-size: 0.75rem;
  color: var(--color-primary);
  white-space: nowrap;
  cursor: pointer;
  text-decoration: none;
}

.summary-overflow:hover {
  text-decoration: underline;
}

.summary-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot-active {
  background-color: var(--color-success);
}

.dot-idle {
  background-color: var(--color-text-light);
}

.dot-completed {
  background-color: var(--color-primary);
}

.dot-pulse {
  animation: dotPulse 2s infinite;
}

@keyframes dotPulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(0, 210, 122, 0.4); }
  50% { box-shadow: 0 0 0 4px rgba(0, 210, 122, 0); }
}

.summary-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.summary-btn {
  height: 34px;
  padding: 0 14px;
  border: none;
  border-radius: var(--radius-sm);
  font-family: var(--font-base);
  font-size: 0.78rem;
  font-weight: 500;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  transition: var(--transition-fast);
  white-space: nowrap;
}

.summary-btn:disabled {
  opacity: 0.4;
  cursor: default;
}

.summary-btn--configure {
  background: var(--color-white);
  border: 1px solid var(--color-border);
  color: var(--color-muted);
}

.summary-btn--configure:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-light);
}

.summary-btn--primary {
  background: var(--color-primary);
  color: var(--color-white);
}

.summary-btn--primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
}

.summary-btn--danger {
  background: transparent;
  border: 1px solid var(--color-border);
  color: var(--color-danger);
}

.summary-btn--danger:hover {
  background: rgba(230, 55, 87, 0.06);
  border-color: var(--color-danger);
}

.summary-btn--ghost {
  background: transparent;
  color: var(--color-text-light);
  padding: 0 8px;
}

.summary-btn--ghost:hover:not(:disabled) {
  color: var(--color-primary);
}

/* ===== Status Strip ===== */
.status-strip {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 6px 16px;
  font-size: 0.75rem;
  color: var(--color-muted);
}

.status-strip-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.status-strip-item i {
  font-size: 0.7rem;
}

.status-strip-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
}

.status-strip-connected {
  color: var(--color-success);
  font-weight: 500;
}

/* ===== Events Table Fields ===== */
.fields-container {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.field-tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xs);
  font-size: 0.75rem;
  font-family: var(--font-monospace);
}

.field-key {
  color: var(--color-primary);
  font-weight: 500;
}

.field-value {
  color: var(--color-body);
}
</style>
