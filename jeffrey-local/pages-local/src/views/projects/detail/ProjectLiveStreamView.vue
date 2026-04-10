<template>
  <PageHeader
    title="Live Stream"
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
                  :style="{ backgroundColor: eventTypeColor(et) + '18', color: eventTypeColor(et), borderColor: eventTypeColor(et) + '40' }"
                >{{ et }}</span>
                <a v-if="overflowCount > 0 && !eventTagsExpanded" href="#" class="summary-overflow" @click.prevent="eventTagsExpanded = true">+{{ overflowCount }} more</a>
                <a v-if="eventTagsExpanded && overflowCount > 0" href="#" class="summary-overflow" @click.prevent="eventTagsExpanded = false">Show less</a>
              </div>
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
        <i class="bi bi-collection"></i> {{ totalEventsReceived }} events
      </span>
      <span class="status-strip-item">
        <i class="bi bi-stack"></i> {{ batchCount }} batches
      </span>
      <span v-if="lastBatchTime" class="status-strip-item">
        <i class="bi bi-clock"></i> Last batch: {{ FormattingService.formatTimestamp(lastBatchTime) }}
      </span>
    </div>

    <!-- Events Table -->
    <StreamingEventsTable
      :events="events"
      :event-types="eventTypes"
      :row-attrs="(event) => ({ 'data-session-slot': sessionSlot(event.sessionId), title: event.sessionId })"
    >
      <template #empty>
        <EmptyState
          title="No events received"
          :description="
            connected
              ? 'Waiting for events from the streaming repository...'
              : 'Select one or more sessions and click Subscribe to start receiving events.'
          "
          icon="bi-broadcast"
        />
      </template>
    </StreamingEventsTable>
  </PageHeader>

  <LiveStreamConfigDrawer
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
import StreamingEventsTable from '@/components/streaming/StreamingEventsTable.vue'
import LiveStreamConfigDrawer from '@/components/streaming/LiveStreamConfigDrawer.vue'
import type { LiveStreamConfig, SelectedSession } from '@/components/streaming/LiveStreamConfigDrawer.vue'
import FormattingService from '@/services/FormattingService'
import EventStreamingClient, { type StreamingEvent } from '@/services/api/EventStreamingClient'
import { useNavigation } from '@/composables/useNavigation'

const MAX_VISIBLE_TAGS = 3
const EVENT_TYPE_COLORS = [
  '#5e64ff', '#0d9488', '#f59e0b', '#8b5cf6', '#e63757',
  '#39afd1', '#fd7e14', '#00d27a', '#6f42c1', '#daa520'
]
const SESSION_PALETTE_SIZE = 6

const { workspaceId, projectId } = useNavigation()

const showConfigDrawer = ref(false)
const eventTagsExpanded = ref(false)
const sessions = ref<SelectedSession[]>([])
const sessionIndexMap = ref<Record<string, number>>({})
const failedSessions = ref<Set<string>>(new Set())
const eventTypes = ref<string[]>([])
const connected = ref(false)
const events = ref<StreamingEvent[]>([])
const batchCount = ref(0)
const lastBatchTime = ref<number | null>(null)
const maxEvents = ref(1000)
const totalEventsReceived = ref(0)

let client: EventStreamingClient | null = null

const currentConfig = computed<LiveStreamConfig>(() => ({
  sessions: sessions.value.map((s) => ({ ...s })),
  eventTypes: eventTypes.value,
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

const eventTypeColorMap = computed(() => {
  const map: Record<string, number> = {}
  eventTypes.value.forEach((et, i) => { map[et] = i % EVENT_TYPE_COLORS.length })
  return map
})

function eventTypeColor(et: string): string {
  return EVENT_TYPE_COLORS[eventTypeColorMap.value[et] ?? 0]
}

const sessionsSummaryLabel = computed(() => {
  const count = sessions.value.length
  if (count === 0) return ''
  if (count === 1) return '1 session'
  return `${count} sessions`
})

function onConfigApply(config: LiveStreamConfig) {
  sessions.value = config.sessions.map((s) => ({ ...s }))
  const newMap: Record<string, number> = {}
  sessions.value.forEach((s, idx) => {
    const existing = sessionIndexMap.value[s.id]
    newMap[s.id] = existing != null ? existing : idx
  })
  sessionIndexMap.value = newMap
  failedSessions.value = new Set()
  eventTypes.value = config.eventTypes
  maxEvents.value = config.maxEvents
}

function startStreaming() {
  if (sessions.value.length === 0 || !workspaceId.value || !projectId.value) return

  client = new EventStreamingClient(workspaceId.value, projectId.value)

  // Fresh subscription → clean slate
  events.value = []
  batchCount.value = 0
  totalEventsReceived.value = 0
  lastBatchTime.value = null
  failedSessions.value = new Set()

  client.subscribe(
    sessions.value.map((s) => s.id),
    eventTypes.value,
    (batch) => {
      batchCount.value++
      totalEventsReceived.value += batch.length
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
    }
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
  events.value = []
  batchCount.value = 0
  totalEventsReceived.value = 0
  lastBatchTime.value = null
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
  grid-template-columns: 1fr 1fr;
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
  border: 1px solid transparent;
  font-size: 0.75rem;
  font-weight: 500;
  font-family: var(--font-monospace);
  white-space: nowrap;
}

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

/* ===== Session chips ===== */
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

</style>
