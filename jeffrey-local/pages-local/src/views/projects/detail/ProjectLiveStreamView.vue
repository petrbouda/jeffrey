<template>
  <div>
    <MainCard>
      <template #header>
        <MainCardHeader icon="bi bi-broadcast" title="Live Stream" />
      </template>

      <!-- Summary panel: 3 inline-editable cards + header actions -->
      <div class="summary-panel mb-3" :class="{ 'summary-panel--connected': connected }">
        <div class="summary-row">
          <div class="summary-segment">
            <span
              class="summary-dot"
              :class="connected ? 'dot-active dot-pulse' : 'dot-idle'"
            ></span>
            <span class="summary-status-text">{{ statusText }}</span>
          </div>
          <div class="summary-actions">
            <button
              v-if="!connected"
              class="summary-btn summary-btn--primary"
              :disabled="!canSubscribe"
              @click="startStreaming"
            >
              <i class="bi bi-play-fill"></i> Subscribe
            </button>
            <button v-else class="summary-btn summary-btn--danger" @click="stopStreaming">
              <i class="bi bi-stop-fill"></i> Disconnect
            </button>
            <button
              class="summary-btn summary-btn--ghost"
              :disabled="!hasAnything"
              title="Clear configuration and events"
              @click="clearAll"
            >
              <i class="bi bi-arrow-counterclockwise"></i>
            </button>
          </div>
        </div>

        <div class="summary-details">
          <!-- Sessions card -->
          <div
            class="summary-detail-card"
            :class="{ 'summary-detail-card--editing': editing === 'sessions' }"
          >
            <div class="summary-detail-head" @click="toggleEditing('sessions')">
              <div class="summary-detail-icon"><i class="bi bi-broadcast"></i></div>
              <div class="summary-detail-label-wrap">
                <div class="summary-detail-label">Sessions</div>
                <i v-if="sessions.length > 0" class="bi bi-check-circle-fill summary-detail-check"></i>
                <span v-if="sessions.length > 0" class="summary-detail-count">{{ sessionsSummaryLabel }}</span>
              </div>
              <span class="summary-detail-action">{{ editing === 'sessions' ? 'Done' : sessions.length ? 'Change' : 'Select' }}</span>
            </div>
            <div class="summary-detail-body">
              <template v-if="editing === 'sessions' && workspaceId && projectId">
                <LiveSessionPicker
                  :workspace-id="workspaceId"
                  :project-id="projectId"
                  :selected="sessions"
                  @update:selected="onSessionsChange"
                />
              </template>
              <template v-else-if="sessions.length === 0">
                <div class="summary-detail-placeholder">
                  <i class="bi bi-cursor"></i>
                  Click <em>Select</em> to pick one or more active sessions
                </div>
              </template>
              <template v-else>
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
                    <span
                      v-if="!connected"
                      class="session-chip-remove"
                      title="Remove session"
                      @click.stop="removeSession(s.id)"
                    >×</span>
                  </span>
                </div>
              </template>
            </div>
          </div>

          <!-- Events card -->
          <div
            class="summary-detail-card"
            :class="{ 'summary-detail-card--editing': editing === 'events' }"
          >
            <div class="summary-detail-head" @click="toggleEditing('events')">
              <div class="summary-detail-icon summary-detail-icon--events"><i class="bi bi-lightning"></i></div>
              <div class="summary-detail-label-wrap">
                <div class="summary-detail-label">Event Types</div>
                <i v-if="eventTypes.length > 0" class="bi bi-check-circle-fill summary-detail-check"></i>
              </div>
              <span class="summary-detail-action">{{ editing === 'events' ? 'Done' : eventTypes.length ? 'Change' : 'Select' }}</span>
            </div>
            <div class="summary-detail-body">
              <template v-if="editing === 'events'">
                <div class="events-note">
                  <i class="bi bi-info-circle"></i>
                  Only events committed by the JVM's JFR in real-time are available.
                  CPU profiling events (e.g. jdk.ExecutionSample) collected by
                  async-profiler are merged at dump time and do not appear in the
                  live stream.
                </div>
                <EventTypeSelector v-model="eventTypes" />
              </template>
              <template v-else-if="eventTypes.length === 0">
                <div class="summary-detail-placeholder">
                  <i class="bi bi-cursor"></i>
                  Click <em>Select</em> to choose events
                </div>
              </template>
              <template v-else>
                <div class="summary-tags">
                  <span
                    v-for="et in visibleEventTags"
                    :key="et"
                    class="summary-tag"
                    :style="{
                      backgroundColor: eventTypeColor(et) + '18',
                      color: eventTypeColor(et),
                      borderColor: eventTypeColor(et) + '40'
                    }"
                  >{{ et }}</span>
                  <a
                    v-if="overflowCount > 0 && !eventTagsExpanded"
                    href="#"
                    class="summary-overflow"
                    @click.stop.prevent="eventTagsExpanded = true"
                  >+{{ overflowCount }} more</a>
                  <a
                    v-if="eventTagsExpanded && overflowCount > 0"
                    href="#"
                    class="summary-overflow"
                    @click.stop.prevent="eventTagsExpanded = false"
                  >Show less</a>
                </div>
              </template>
            </div>
          </div>

          <!-- Buffer card -->
          <div
            class="summary-detail-card"
            :class="{ 'summary-detail-card--editing': editing === 'buffer' }"
          >
            <div class="summary-detail-head" @click="toggleEditing('buffer')">
              <div class="summary-detail-icon summary-detail-icon--buffer"><i class="bi bi-stack"></i></div>
              <div class="summary-detail-label-wrap">
                <div class="summary-detail-label">Buffer</div>
              </div>
              <span class="summary-detail-action">{{ editing === 'buffer' ? 'Done' : 'Change' }}</span>
            </div>
            <div class="summary-detail-body">
              <template v-if="editing === 'buffer'">
                <div class="time-field-controls">
                  <button
                    v-for="option in maxEventsOptions"
                    :key="option"
                    class="time-radio"
                    :class="{ 'time-radio--on': !customMaxEvents && maxEvents === option }"
                    @click="selectPresetMaxEvents(option)"
                  >{{ option.toLocaleString() }}</button>
                  <button
                    class="time-radio"
                    :class="{ 'time-radio--on': customMaxEvents }"
                    @click="enableCustomMaxEvents"
                  >Custom</button>
                  <input
                    v-if="customMaxEvents"
                    v-model.number="maxEvents"
                    type="number"
                    min="1"
                    class="time-dt time-dt--narrow"
                  />
                </div>
                <div class="time-hint">
                  Maximum events kept in the table. Oldest events are discarded when the limit is reached.
                </div>
              </template>
              <template v-else>
                <div class="summary-detail-value">{{ maxEvents.toLocaleString() }} events</div>
                <div class="summary-detail-sub">Rolling buffer</div>
              </template>
            </div>
          </div>
        </div>
      </div>

      <!-- Status Strip -->
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
                : canSubscribe
                  ? 'Click Subscribe to start receiving events.'
                  : 'Select one or more sessions and event types, then click Subscribe.'
            "
            icon="bi-broadcast"
          />
        </template>
      </StreamingEventsTable>
    </MainCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import EmptyState from '@/components/EmptyState.vue'
import MainCard from '@/components/MainCard.vue'
import MainCardHeader from '@/components/MainCardHeader.vue'
import StreamingEventsTable from '@/components/streaming/StreamingEventsTable.vue'
import EventTypeSelector from '@/components/streaming/EventTypeSelector.vue'
import LiveSessionPicker from '@/components/streaming/LiveSessionPicker.vue'
import type { SelectedSession } from '@/components/streaming/streamingTypes'
import FormattingService from '@/services/FormattingService'
import EventStreamingClient, { type StreamingEvent } from '@/services/api/EventStreamingClient'
import { useNavigation } from '@/composables/useNavigation'

type EditingCard = 'sessions' | 'events' | 'buffer' | null

const MAX_VISIBLE_TAGS = 3
const EVENT_TYPE_COLORS = [
  '#5e64ff', '#0d9488', '#f59e0b', '#8b5cf6', '#e63757',
  '#39afd1', '#fd7e14', '#00d27a', '#6f42c1', '#daa520'
]
const SESSION_PALETTE_SIZE = 6

const { workspaceId, projectId } = useNavigation()
const route = useRoute()

const editing = ref<EditingCard>(null)
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
const customMaxEvents = ref(false)
const maxEventsOptions = [500, 1000, 5000, 10000]

let client: EventStreamingClient | null = null

const canSubscribe = computed(() => sessions.value.length > 0 && eventTypes.value.length > 0)

const hasAnything = computed(
  () => sessions.value.length > 0 || eventTypes.value.length > 0 || events.value.length > 0
)

const statusText = computed(() => {
  if (connected.value) return 'Connected'
  if (canSubscribe.value) return 'Ready to subscribe'
  return 'Configure sessions and event types'
})

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

function toggleEditing(card: Exclude<EditingCard, null>) {
  if (connected.value && card === 'sessions') return
  editing.value = editing.value === card ? null : card
}

function onSessionsChange(next: SelectedSession[]) {
  sessions.value = next.map((s) => ({ ...s }))
  // Preserve existing palette slots where possible; newly-added sessions get
  // their position-in-array as the initial slot (the wizard's old heuristic).
  const newMap: Record<string, number> = {}
  sessions.value.forEach((s, idx) => {
    const existing = sessionIndexMap.value[s.id]
    newMap[s.id] = existing != null ? existing : idx
  })
  sessionIndexMap.value = newMap
  // Drop failed-session markers for sessions that were removed.
  if (failedSessions.value.size > 0) {
    const activeIds = new Set(sessions.value.map((s) => s.id))
    const pruned = new Set([...failedSessions.value].filter((id) => activeIds.has(id)))
    if (pruned.size !== failedSessions.value.size) {
      failedSessions.value = pruned
    }
  }
}

function removeSession(sessionId: string) {
  onSessionsChange(sessions.value.filter((s) => s.id !== sessionId))
}

function selectPresetMaxEvents(option: number) {
  customMaxEvents.value = false
  maxEvents.value = option
}

function enableCustomMaxEvents() {
  customMaxEvents.value = true
}

function startStreaming() {
  if (sessions.value.length === 0 || !workspaceId.value || !projectId.value) return

  client = new EventStreamingClient(workspaceId.value, projectId.value)

  events.value = []
  batchCount.value = 0
  totalEventsReceived.value = 0
  lastBatchTime.value = null
  failedSessions.value = new Set()
  editing.value = null

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
  customMaxEvents.value = false
  editing.value = null
}

onMounted(() => {
  const sid = route.query.sessionId
  const sinst = route.query.sessionInstance
  if (typeof sid === 'string' && sid.length > 0) {
    const sessionInstance = typeof sinst === 'string' ? sinst : ''
    sessions.value = [{ id: sid, sessionInstance }]
    sessionIndexMap.value = { [sid]: 0 }
    // Arrived from Instances timeline with a session already chosen —
    // jump the user straight to picking events.
    editing.value = 'events'
  }
})

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

.summary-panel--connected {
  border-color: rgba(0, 210, 122, 0.3);
  background: var(--color-white);
}

.summary-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.summary-segment {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.8rem;
  color: var(--color-muted);
  min-width: 0;
  flex: 1;
}

.summary-status-text {
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--color-body);
}

.summary-details {
  display: grid;
  grid-template-columns: 1.3fr 1.3fr 1fr;
  gap: 10px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border-light);
}

.summary-detail-card {
  display: flex;
  flex-direction: column;
  background: var(--color-white);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  overflow: hidden;
  transition: var(--transition-fast);
}
.summary-detail-card:hover:not(.summary-detail-card--editing) {
  border-color: var(--color-primary-border-light);
}
.summary-detail-card--editing {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-light);
}

.summary-detail-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  cursor: pointer;
  user-select: none;
  transition: background-color var(--transition-fast);
}
.summary-detail-head:hover {
  background: var(--color-light);
}
.summary-detail-card--editing .summary-detail-head {
  background: var(--color-primary-lighter);
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

.summary-detail-icon--buffer {
  background: var(--color-teal-light);
  color: var(--color-teal);
}

.summary-detail-label-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex: 1;
}

.summary-detail-label {
  font-size: 0.68rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-muted);
}

.summary-detail-count {
  font-size: 0.68rem;
  color: var(--color-muted);
  font-weight: 500;
}

.summary-detail-check {
  color: var(--color-success);
  font-size: 0.78rem;
}

.summary-detail-action {
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--color-primary);
  text-decoration: none;
}
.summary-detail-head:hover .summary-detail-action {
  text-decoration: underline;
}

.summary-detail-body {
  flex: 1;
  padding: 10px 12px 12px;
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

.summary-detail-placeholder {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.75rem;
  color: var(--color-muted);
  font-style: italic;
}
.summary-detail-placeholder i {
  font-size: 0.7rem;
  opacity: 0.6;
}
.summary-detail-placeholder em {
  font-style: normal;
  font-weight: 600;
  color: var(--color-primary);
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

.dot-active { background-color: var(--color-success); }
.dot-idle { background-color: var(--color-text-light); }

.dot-pulse {
  animation: dotPulse 2s infinite;
}

@keyframes dotPulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(0, 210, 122, 0.4); }
  50% { box-shadow: 0 0 0 4px rgba(0, 210, 122, 0); }
}

.summary-actions {
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

/* ===== Inline editors ===== */
.events-note {
  font-size: 0.72rem;
  color: var(--color-muted);
  margin-bottom: 10px;
  display: flex;
  align-items: baseline;
  gap: 6px;
  line-height: 1.4;
}
.events-note i {
  font-size: 0.7rem;
  flex-shrink: 0;
  color: var(--color-primary);
}

.time-field-controls {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.time-radio {
  padding: 5px 10px;
  border: 1px solid var(--color-border);
  background: var(--color-white);
  color: var(--color-muted);
  font-size: 0.74rem;
  font-weight: 600;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-family: inherit;
  transition: var(--transition-fast);
}
.time-radio:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.time-radio--on {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-light);
}

.time-dt {
  padding: 4px 8px;
  border: 1px solid var(--color-border-input);
  border-radius: var(--radius-sm);
  font-size: 0.74rem;
  font-family: inherit;
  outline: none;
  transition: var(--transition-fast);
}
.time-dt:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px var(--color-primary-light);
}
.time-dt--narrow {
  width: 110px;
}

.time-hint {
  margin-top: 8px;
  font-size: 0.68rem;
  color: var(--color-muted);
  line-height: 1.4;
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

.session-chip-remove {
  cursor: pointer;
  margin-left: 2px;
  color: var(--color-muted);
  font-size: 1rem;
  line-height: 1;
  opacity: 0.7;
}
.session-chip-remove:hover {
  color: var(--color-danger);
  opacity: 1;
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
