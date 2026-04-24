<template>
  <div>
    <MainCard>
      <template #header>
        <MainCardHeader icon="bi bi-collection-play" title="Replay Stream" />
      </template>

      <!-- Summary panel: 3 inline-editable cards + header actions -->
      <div class="summary-panel mb-3" :class="{ 'summary-panel--connected': replaying }">
        <div class="summary-row">
          <div class="summary-segment">
            <span
              class="summary-dot"
              :class="replaying ? 'dot-active dot-pulse' : completed ? 'dot-completed' : 'dot-idle'"
            ></span>
            <span class="summary-status-text">{{ statusText }}</span>
          </div>
          <div class="summary-actions">
            <button
              v-if="!replaying"
              class="summary-btn summary-btn--primary"
              :disabled="!canStart"
              @click="startReplay"
            >
              <i class="bi bi-play-fill"></i> Start Replay
            </button>
            <button v-else class="summary-btn summary-btn--danger" @click="stopReplay">
              <i class="bi bi-stop-fill"></i> Stop
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
          <!-- Session card -->
          <div
            class="summary-detail-card"
            :class="{ 'summary-detail-card--editing': editing === 'session' }"
          >
            <div class="summary-detail-head" @click="toggleEditing('session')">
              <div class="summary-detail-icon"><i class="bi bi-collection-play"></i></div>
              <div class="summary-detail-label-wrap">
                <div class="summary-detail-label">Session</div>
                <i v-if="session" class="bi bi-check-circle-fill summary-detail-check"></i>
              </div>
              <span class="summary-detail-action">{{ editing === 'session' ? 'Done' : session ? 'Change' : 'Select' }}</span>
            </div>
            <div class="summary-detail-body">
              <template v-if="editing === 'session' && workspaceId && projectId">
                <ReplaySessionPicker
                  :workspace-id="workspaceId"
                  :project-id="projectId"
                  :selected="session"
                  @pick="onSessionPick"
                />
              </template>
              <template v-else-if="session">
                <div class="summary-detail-value" :title="`${session.id}\n${session.sessionInstance}`">
                  {{ session.id }}
                </div>
                <div v-if="session.sessionInstance" class="summary-detail-sub">{{ session.sessionInstance }}</div>
              </template>
              <template v-else>
                <div class="summary-detail-placeholder">
                  <i class="bi bi-cursor"></i>
                  Click <em>Select</em> to pick a session
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

          <!-- Time card -->
          <div
            class="summary-detail-card"
            :class="{ 'summary-detail-card--editing': editing === 'time' }"
          >
            <div class="summary-detail-head" @click="toggleEditing('time')">
              <div class="summary-detail-icon summary-detail-icon--time"><i class="bi bi-clock"></i></div>
              <div class="summary-detail-label-wrap">
                <div class="summary-detail-label">Time Range</div>
              </div>
              <span class="summary-detail-action">{{ editing === 'time' ? 'Done' : 'Change' }}</span>
            </div>
            <div class="summary-detail-body">
              <template v-if="editing === 'time'">
                <div class="time-form">
                  <div class="time-field">
                    <span class="time-field-label">From</span>
                    <div class="time-field-controls">
                      <button
                        class="time-radio"
                        :class="{ 'time-radio--on': startMode === 'beginning' }"
                        @click="setStartMode('beginning')"
                      >Beginning</button>
                      <button
                        class="time-radio"
                        :class="{ 'time-radio--on': startMode === 'custom' }"
                        @click="setStartMode('custom')"
                      >Custom</button>
                      <input
                        v-if="startMode === 'custom'"
                        v-model="startTimeInput"
                        type="datetime-local"
                        class="time-dt"
                      />
                    </div>
                  </div>

                  <div class="time-field">
                    <span class="time-field-label">To</span>
                    <div class="time-field-controls">
                      <button
                        class="time-radio"
                        :class="{ 'time-radio--on': endMode === 'latest' }"
                        @click="setEndMode('latest')"
                      >Latest</button>
                      <button
                        class="time-radio"
                        :class="{ 'time-radio--on': endMode === 'custom' }"
                        @click="setEndMode('custom')"
                      >Custom</button>
                      <input
                        v-if="endMode === 'custom'"
                        v-model="endTimeInput"
                        type="datetime-local"
                        class="time-dt"
                      />
                    </div>
                  </div>

                  <div class="time-field time-field--buffer">
                    <span class="time-field-label">Buffer</span>
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
                  </div>
                  <div class="time-hint">
                    Maximum events kept in the table. Oldest events are discarded when the limit is reached.
                  </div>
                </div>
              </template>
              <template v-else>
                <div class="summary-time-lines">
                  <div class="summary-time-line">
                    <span class="summary-time-prefix">From</span>
                    <span class="summary-time-value">{{ timeSummaryStart }}</span>
                  </div>
                  <div class="summary-time-line">
                    <span class="summary-time-prefix">To</span>
                    <span class="summary-time-value">{{ timeSummaryEnd }}</span>
                  </div>
                  <div class="summary-time-line">
                    <span class="summary-time-prefix">Buffer</span>
                    <span class="summary-time-value">{{ maxEvents.toLocaleString() }}</span>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </div>
      </div>

      <!-- Status Strip -->
      <div v-if="replaying || events.length > 0" class="status-strip mb-3">
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
      <StreamingEventsTable :events="events" :event-types="eventTypes">
        <template #empty>
          <EmptyState
            title="No events"
            :description="
              replaying
                ? 'Reading events from recording files...'
                : canStart
                  ? 'Click Start Replay to read historical events.'
                  : 'Configure a session and event types, then click Start Replay.'
            "
            icon="bi-collection-play"
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
import ReplaySessionPicker from '@/components/streaming/ReplaySessionPicker.vue'
import type { ReplayStartMode, ReplayEndMode, SelectedSession } from '@/components/streaming/streamingTypes'
import FormattingService from '@/services/FormattingService'
import ReplayStreamClient from '@/services/api/ReplayStreamClient'
import type { StreamingEvent } from '@/services/api/EventStreamingClient'
import ToastService from '@/services/ToastService'
import { useNavigation } from '@/composables/useNavigation'

type EditingCard = 'session' | 'events' | 'time' | null

const MAX_VISIBLE_TAGS = 3
const EVENT_TYPE_COLORS = [
  '#5e64ff', '#0d9488', '#f59e0b', '#8b5cf6', '#e63757',
  '#39afd1', '#fd7e14', '#00d27a', '#6f42c1', '#daa520'
]

const { workspaceId, projectId } = useNavigation()
const route = useRoute()

const editing = ref<EditingCard>(null)
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
const maxEvents = ref(1000)
const totalEventsReceived = ref(0)
const customMaxEvents = ref(false)
const maxEventsOptions = [500, 1000, 5000, 10000]

let client: ReplayStreamClient | null = null

const canStart = computed(() => session.value != null && eventTypes.value.length > 0)

const hasAnything = computed(
  () => session.value != null || eventTypes.value.length > 0 || events.value.length > 0
)

const statusText = computed(() => {
  if (replaying.value) return 'Replaying...'
  if (completed.value) return 'Replay Complete'
  if (canStart.value) return 'Ready to replay'
  return 'Configure session and event types'
})

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

function datetimeLocalToMillis(value: string): number {
  return new Date(value).getTime()
}

const timeSummaryStart = computed(() => {
  if (startMode.value === 'custom' && startTimeInput.value) {
    return FormattingService.formatDateTime(new Date(startTimeInput.value))
  }
  return 'Beginning'
})

const timeSummaryEnd = computed(() => {
  if (endMode.value === 'custom' && endTimeInput.value) {
    return FormattingService.formatDateTime(new Date(endTimeInput.value))
  }
  return 'Latest'
})

function toggleEditing(card: Exclude<EditingCard, null>) {
  if (replaying.value) return
  editing.value = editing.value === card ? null : card
}

function onSessionPick(value: SelectedSession) {
  session.value = value
  completed.value = false
  editing.value = eventTypes.value.length === 0 ? 'events' : null
}

function setStartMode(mode: ReplayStartMode) {
  startMode.value = mode
  if (mode !== 'custom') startTimeInput.value = ''
}

function setEndMode(mode: ReplayEndMode) {
  endMode.value = mode
  if (mode !== 'custom') endTimeInput.value = ''
}

function selectPresetMaxEvents(option: number) {
  customMaxEvents.value = false
  maxEvents.value = option
}

function enableCustomMaxEvents() {
  customMaxEvents.value = true
}

function startReplay() {
  if (!session.value || !workspaceId.value || !projectId.value) return

  client = new ReplayStreamClient(workspaceId.value, projectId.value)

  const options: { startTime?: number; endTime?: number } = {}
  if (startMode.value === 'custom' && startTimeInput.value) {
    options.startTime = datetimeLocalToMillis(startTimeInput.value)
  }
  if (endMode.value === 'custom' && endTimeInput.value) {
    options.endTime = datetimeLocalToMillis(endTimeInput.value)
  }

  events.value = []
  batchCount.value = 0
  totalEventsReceived.value = 0
  lastBatchTime.value = null
  completed.value = false
  editing.value = null

  client.replay(
    session.value.id,
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
      replaying.value = false
      completed.value = true
    },
    (error) => {
      replaying.value = false
      completed.value = true
      ToastService.error('Replay failed', error)
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
  totalEventsReceived.value = 0
  lastBatchTime.value = null
  maxEvents.value = 1000
  customMaxEvents.value = false
  completed.value = false
  editing.value = null
}

onMounted(() => {
  const sid = route.query.sessionId
  const sinst = route.query.sessionInstance
  if (typeof sid === 'string' && sid.length > 0) {
    session.value = {
      id: sid,
      sessionInstance: typeof sinst === 'string' ? sinst : ''
    }
    // Arrived from Instances timeline with session + default time already valid.
    // Jump straight to the only thing the user still has to pick.
    editing.value = 'events'
  }
})

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
  grid-template-columns: 1fr 1.3fr 1fr;
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

.summary-detail-icon--time {
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
  margin-top: 2px;
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
  min-width: 48px;
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
.dot-completed { background-color: var(--color-primary); }

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

/* ===== Inline Time Editor ===== */
.time-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.time-field {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.time-field--buffer {
  border-top: 1px solid var(--color-border-light);
  padding-top: 10px;
  margin-top: 2px;
}

.time-field-label {
  min-width: 46px;
  font-size: 0.68rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-muted);
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
</style>
