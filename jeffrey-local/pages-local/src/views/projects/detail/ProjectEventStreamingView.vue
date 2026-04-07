<template>
  <PageHeader
    title="Event Streaming"
    description="Subscribe to live JFR events from one or more remote sessions' streaming repositories."
    icon="bi-broadcast"
  >
    <!-- Summary Panel -->
    <div
      class="summary-panel mb-3"
      :class="{
        'summary-panel--empty': sessions.length === 0,
        'summary-panel--connected': connected
      }"
    >
      <!-- Empty state -->
      <template v-if="sessions.length === 0">
        <div class="summary-row">
          <i class="bi bi-gear summary-bar-icon"></i>
          <span class="summary-bar-placeholder">
            No streaming configured — click Configure to select sessions and event types
          </span>
          <div class="summary-actions">
            <button class="summary-btn summary-btn--configure" @click="showConfigDrawer = true">
              <i class="bi bi-gear"></i> Configure
            </button>
            <button class="summary-btn summary-btn--primary" disabled>
              <i class="bi bi-play-fill"></i> Subscribe
            </button>
          </div>
        </div>
      </template>

      <!-- Configured / Connected state -->
      <template v-else>
        <!-- Header row: Actions -->
        <div class="summary-row">
          <div class="summary-segment" style="flex: 1">
            <span class="summary-dot" :class="connected ? 'dot-active dot-pulse' : 'dot-idle'"></span>
            <span class="summary-status-text">{{ connected ? 'Connected' : 'Ready to subscribe' }}</span>
          </div>
          <div class="summary-actions">
            <button
              class="summary-btn summary-btn--configure"
              :disabled="connected"
              @click="showConfigDrawer = true"
            >
              <i class="bi bi-gear"></i> Configure
            </button>
            <button
              v-if="!connected"
              class="summary-btn summary-btn--primary"
              :disabled="eventTypes.length === 0 || sessions.length === 0"
              @click="startStreaming"
            >
              <i class="bi bi-play-fill"></i> Subscribe
            </button>
            <button
              v-else
              class="summary-btn summary-btn--danger"
              @click="stopStreaming"
            >
              <i class="bi bi-stop-fill"></i> Disconnect
            </button>
            <button
              class="summary-btn summary-btn--ghost"
              :disabled="events.length === 0 && sessions.length === 0"
              @click="clearAll"
            >
              <i class="bi bi-arrow-counterclockwise"></i>
            </button>
          </div>
        </div>

        <!-- Config details grid -->
        <div class="summary-details">
          <div class="summary-detail-card">
            <div class="summary-detail-icon"><i class="bi bi-broadcast"></i></div>
            <div class="summary-detail-body">
              <div class="summary-detail-label">Sessions</div>
              <div class="summary-detail-value">{{ sessionsSummaryLabel }}</div>
              <div class="summary-session-chips">
                <span
                  v-for="s in sessions"
                  :key="s.id"
                  class="session-chip"
                  :class="{ 'session-chip--failed': failedSessions.has(s.id) }"
                  :data-session-slot="sessionSlot(s.id)"
                  :title="s.sessionInstance ? `${s.id}\n${s.sessionInstance}` : s.id"
                >
                  <span class="session-chip-dot"></span>
                  {{ s.id }}
                  <i v-if="failedSessions.has(s.id)" class="bi bi-exclamation-triangle-fill session-chip-warn"></i>
                </span>
              </div>
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
                <div v-if="timeSummaryEnd" class="summary-time-line">
                  <span class="summary-time-prefix">To</span>
                  <span class="summary-time-value">{{ timeSummaryEnd }}</span>
                </div>
              </div>
              <span v-if="continuous" class="summary-continuous-badge">Continuous</span>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- Status Strip (visible when connected or has events) -->
    <div v-if="connected || events.length > 0" class="status-strip mb-3">
      <div class="status-strip-item">
        <span class="status-strip-dot" :class="connected ? 'dot-active' : 'dot-idle'"></span>
        <span :class="connected ? 'status-strip-connected' : ''">{{ connected ? 'Connected' : 'Disconnected' }}</span>
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
      title="No events received"
      :description="
        connected
          ? 'Waiting for events from the streaming repository...'
          : 'Select one or more sessions and click Subscribe to start receiving events.'
      "
      icon="bi-broadcast"
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
            :data-session-slot="sessionSlot(event.sessionId)"
            :title="event.sessionId"
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

  <StreamingConfigDrawer
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
import StreamingConfigDrawer from '@/components/streaming/StreamingConfigDrawer.vue'
import type { StreamingConfig, StartMode, EndMode, SelectedSession } from '@/components/streaming/StreamingConfigDrawer.vue'
import FormattingService from '@/services/FormattingService'
import EventStreamingClient, { type StreamingEvent } from '@/services/api/EventStreamingClient'
import { getEventTypePrefix } from '@/services/EventTypeCatalog'
import Utils from '@/services/Utils'
import { useNavigation } from '@/composables/useNavigation'

const MAX_VISIBLE_TAGS = 3
const SESSION_PALETTE_SIZE = 6

const { workspaceId, projectId } = useNavigation()

const showConfigDrawer = ref(false)
const eventTagsExpanded = ref(false)
const sessions = ref<SelectedSession[]>([])
const sessionIndexMap = ref<Record<string, number>>({})
const failedSessions = ref<Set<string>>(new Set())
const eventTypes = ref<string[]>([])
const startMode = ref<StartMode>('beginning')
const startTimeInput = ref('')
const endMode = ref<EndMode>('now')
const endTimeInput = ref('')
const continuous = ref(false)
const connected = ref(false)
const events = ref<StreamingEvent[]>([])
const batchCount = ref(0)
const lastBatchTime = ref<number | null>(null)
const maxDisplayed = ref(200)
const maxEvents = ref(1000)

let client: EventStreamingClient | null = null

const currentConfig = computed<StreamingConfig>(() => ({
  sessions: sessions.value.map((s) => ({ ...s })),
  eventTypes: eventTypes.value,
  startMode: startMode.value,
  startTime: startTimeInput.value,
  endMode: endMode.value,
  endTime: endTimeInput.value,
  continuous: continuous.value,
  maxEvents: maxEvents.value
}))

function sessionSlot(sessionId: string): number {
  const idx = sessionIndexMap.value[sessionId]
  return idx == null ? 0 : idx % SESSION_PALETTE_SIZE
}

const visibleEventTags = computed(() =>
  eventTagsExpanded.value ? eventTypes.value : eventTypes.value.slice(0, MAX_VISIBLE_TAGS)
)
const overflowCount = computed(() => Math.max(0, eventTypes.value.length - MAX_VISIBLE_TAGS))

const sessionsSummaryLabel = computed(() => {
  const count = sessions.value.length
  if (count === 0) return ''
  if (count === 1) return '1 session'
  return `${count} sessions`
})

function datetimeLocalToMillis(value: string): number {
  return new Date(value).getTime()
}

const timeSummaryStart = computed(() => {
  if (startMode.value === 'beginning') return 'Beginning'
  if (startMode.value === 'custom' && startTimeInput.value) {
    return FormattingService.formatDateTime(new Date(startTimeInput.value))
  }
  return 'Now'
})

const timeSummaryEnd = computed(() => {
  if (continuous.value) return ''
  if (endMode.value === 'custom' && endTimeInput.value) {
    return FormattingService.formatDateTime(new Date(endTimeInput.value))
  }
  return 'Now'
})

const displayedEvents = computed(() => {
  return events.value.slice(-maxDisplayed.value).reverse()
})

function onConfigApply(config: StreamingConfig) {
  sessions.value = config.sessions.map((s) => ({ ...s }))
  // Refresh index map: stable slot assignment in selection order.
  // Existing sessions keep their slot; new sessions get the next free index.
  const newMap: Record<string, number> = {}
  sessions.value.forEach((s, idx) => {
    const existing = sessionIndexMap.value[s.id]
    newMap[s.id] = existing != null ? existing : idx
  })
  sessionIndexMap.value = newMap
  failedSessions.value = new Set()
  eventTypes.value = config.eventTypes
  startMode.value = config.startMode
  startTimeInput.value = config.startTime
  endMode.value = config.endMode
  endTimeInput.value = config.endTime
  continuous.value = config.continuous
  maxEvents.value = config.maxEvents
}

function startStreaming() {
  if (sessions.value.length === 0 || !workspaceId.value || !projectId.value) return

  client = new EventStreamingClient(workspaceId.value, projectId.value)

  const options: { startTime?: number; endTime?: number; continuous?: boolean } = {}
  if (startMode.value === 'beginning') {
    options.startTime = 0
  } else if (startMode.value === 'custom' && startTimeInput.value) {
    options.startTime = datetimeLocalToMillis(startTimeInput.value)
  }
  if (!continuous.value && endMode.value === 'custom' && endTimeInput.value) {
    options.endTime = datetimeLocalToMillis(endTimeInput.value)
  }
  if (continuous.value) {
    options.continuous = true
  }

  // Fresh subscription → clean slate
  events.value = []
  batchCount.value = 0
  lastBatchTime.value = null
  failedSessions.value = new Set()

  client.subscribe(
    sessions.value.map((s) => s.id),
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
      connected.value = false
    },
    () => {
      connected.value = false
    },
    (failedSessionId) => {
      failedSessions.value = new Set([...failedSessions.value, failedSessionId])
    },
    options
  )

  connected.value = true
}

function stopStreaming() {
  client?.unsubscribe()
  client = null
  connected.value = false
}

function clearAll() {
  stopStreaming()
  sessions.value = []
  sessionIndexMap.value = {}
  failedSessions.value = new Set()
  eventTypes.value = []
  startMode.value = 'beginning'
  startTimeInput.value = ''
  endMode.value = 'now'
  endTimeInput.value = ''
  continuous.value = false
  events.value = []
  batchCount.value = 0
  lastBatchTime.value = null
  maxDisplayed.value = 200
  maxEvents.value = 1000
}

onUnmounted(() => {
  stopStreaming()
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
  background: linear-gradient(135deg, var(--color-white), rgba(0, 210, 122, 0.06));
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

/* Segments */
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

/* Config details grid */
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

/* Tags in summary */
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

.summary-seg-arrow {
  color: var(--color-muted);
  font-size: 0.8rem;
}

.summary-continuous-badge {
  padding: 2px 10px;
  border-radius: var(--radius-sm);
  font-size: 0.72rem;
  font-weight: 500;
  background: var(--color-primary-light);
  color: var(--color-primary);
  margin-left: auto;
}

/* Status dot */
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

.dot-pulse {
  animation: dotPulse 2s infinite;
}

@keyframes dotPulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(0, 210, 122, 0.4); }
  50% { box-shadow: 0 0 0 4px rgba(0, 210, 122, 0); }
}

/* Actions */
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

/* ===== Session chips (summary panel) ===== */
.summary-session-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}

.session-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-size: 0.72rem;
  font-family: var(--font-monospace);
  font-weight: 500;
  background: var(--color-light);
  border: 1px solid var(--color-border);
  color: var(--color-body);
  white-space: nowrap;
}

.session-chip-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--color-muted);
}

.session-chip-warn {
  color: var(--color-danger);
  font-size: 0.7rem;
  margin-left: 2px;
}

.session-chip--failed {
  opacity: 0.55;
  text-decoration: line-through;
}

/* ===== Session palette — chip dot + row left border =====
   Only the 3px left border on the first cell signals the session.
   No row background tint. Border lives on the first <td> because
   box-shadow / background on a <tr> (display: table-row) is unreliable.
*/
.session-chip[data-session-slot="0"] .session-chip-dot { background: var(--color-primary); }
.session-chip[data-session-slot="1"] .session-chip-dot { background: var(--color-amber); }
.session-chip[data-session-slot="2"] .session-chip-dot { background: var(--color-violet); }
.session-chip[data-session-slot="3"] .session-chip-dot { background: var(--color-teal); }
.session-chip[data-session-slot="4"] .session-chip-dot { background: var(--color-danger); }
.session-chip[data-session-slot="5"] .session-chip-dot { background: var(--color-info); }

tbody tr[data-session-slot] td:first-child {
  border-left-width: 3px;
  border-left-style: solid;
  padding-left: 9px;
}

tbody tr[data-session-slot="0"] td:first-child { border-left-color: var(--color-primary); }
tbody tr[data-session-slot="1"] td:first-child { border-left-color: var(--color-amber); }
tbody tr[data-session-slot="2"] td:first-child { border-left-color: var(--color-violet); }
tbody tr[data-session-slot="3"] td:first-child { border-left-color: var(--color-teal); }
tbody tr[data-session-slot="4"] td:first-child { border-left-color: var(--color-danger); }
tbody tr[data-session-slot="5"] td:first-child { border-left-color: var(--color-info); }

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
