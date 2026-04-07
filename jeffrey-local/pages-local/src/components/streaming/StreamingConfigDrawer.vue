<template>
  <GenericModal
    modal-id="streaming-config-modal"
    :show="show"
    title="Streaming Configuration"
    icon="bi-gear"
    size="md"
    modal-dialog-class="scm-dialog"
    :show-footer="true"
    @update:show="emit('update:show', $event)"
  >
    <template #footer>
      <button
        v-if="currentStep > 1"
        type="button"
        class="btn btn-light"
        @click="prevStep"
      >
        <i class="bi bi-arrow-left me-1"></i> Back
      </button>
      <div style="flex: 1"></div>
      <button type="button" class="btn btn-light" @click="emit('update:show', false)">
        Cancel
      </button>
      <button
        v-if="currentStep < 3"
        type="button"
        class="btn btn-primary"
        :disabled="!canAdvance"
        @click="nextStep"
      >
        Next <i class="bi bi-arrow-right ms-1"></i>
      </button>
      <button
        v-else
        type="button"
        class="btn btn-primary"
        :disabled="!applyEnabled"
        @click="apply"
      >
        <i class="bi bi-check-lg me-1"></i> Apply & Close
      </button>
    </template>

    <!-- Wizard Steps -->
    <div class="scm-wizard-steps">
      <div
        v-for="step in wizardSteps"
        :key="step.num"
        class="scm-wizard-step"
        :class="{
          'scm-step--done': step.num < currentStep,
          'scm-step--active': step.num === currentStep,
          'scm-step--pending': step.num > currentStep,
          'scm-step--clickable': step.num < currentStep
        }"
        @click="goToStep(step.num)"
      >
        <span class="scm-step-num">
          <i v-if="step.num < currentStep" class="bi bi-check"></i>
          <template v-else>{{ step.num }}</template>
        </span>
        {{ step.label }}
      </div>
    </div>

    <!-- Step 1: Session -->
    <div v-if="currentStep === 1" class="scm-step-content">
      <div class="scm-session-search">
        <SearchInput v-model="sessionSearchQuery" placeholder="Search by session ID..." />
      </div>
      <div v-if="localSessions.length > 0" class="scm-selection-bar">
        <span>
          <strong>{{ localSessions.length }}</strong>
          session{{ localSessions.length === 1 ? '' : 's' }} selected
        </span>
        <a href="#" class="scm-clear-link" @click.prevent="clearSelectedSessions">Clear</a>
      </div>
      <div class="scm-session-list">
        <LoadingState v-if="sessionsLoading" message="Loading sessions..." />

        <EmptyState
          v-else-if="instanceGroups.length === 0"
          title="No sessions found"
          description="No instances with sessions are available for this project."
          icon="bi-broadcast"
        />

        <template v-else>
          <template v-for="group in filteredGroups" :key="group.instance.id">
            <div class="scm-instance-header">
              <span class="scm-dot" :class="group.instance.status === 'ACTIVE' ? 'dot-active' : 'dot-inactive'"></span>
              <span class="scm-instance-host">{{ group.instance.hostname }}</span>
            </div>
            <div
              v-for="session in group.sessions"
              :key="session.id"
              class="session-card scm-session-card-spacing"
              :class="[
                session.isActive ? 'session-card--active' : 'session-card--finished',
                { 'scm-session-selected': isSessionSelected(session.id) }
              ]"
              @click="toggleSession(session, group.instance)"
            >
              <div class="session-card-body session-card-body--compact">
                <div class="d-flex align-items-center justify-content-between gap-2">
                  <div class="d-flex align-items-center gap-2 flex-grow-1 scm-min-width-0">
                    <i
                      class="bi scm-check"
                      :class="isSessionSelected(session.id) ? 'bi-check-square-fill' : 'bi-square'"
                    ></i>
                    <div
                      class="session-card-icon session-card-icon--compact"
                      :class="session.isActive ? 'session-card-icon--active' : 'session-card-icon--finished'"
                    >
                      <i class="bi bi-folder2"></i>
                    </div>
                    <div class="flex-grow-1 scm-min-width-0">
                      <div class="scm-session-id-line">
                        <span class="scm-session-id-label">Session:</span>
                        <span class="scm-session-id">{{ session.id }}</span>
                      </div>
                      <div class="scm-session-meta">
                        <span><i class="bi bi-clock me-1"></i>Started {{ FormattingService.formatRelativeTime(session.createdAt) }}</span>
                        <span v-if="session.finishedAt"><i class="bi bi-stopwatch me-1"></i>{{ FormattingService.formatDurationFromMillis(session.createdAt, session.finishedAt) }}</span>
                      </div>
                    </div>
                  </div>
                  <Badge
                    :value="session.isActive ? 'Active' : 'Finished'"
                    :variant="session.isActive ? 'status-active' : 'status-finished'"
                    size="xs"
                  />
                </div>
              </div>
            </div>
          </template>

          <div v-if="hasMoreInstances" class="scm-load-more">
            <button class="scm-load-more-btn" @click="maxVisibleInstances += 5">
              Show {{ hiddenInstanceCount }} more instance{{ hiddenInstanceCount > 1 ? 's' : '' }}
            </button>
          </div>

          <div v-if="filteredGroups.length === 0" class="scm-no-results">
            No sessions matching "{{ sessionSearchQuery }}"
          </div>
        </template>
      </div>
    </div>

    <!-- Step 2: Event Types -->
    <div v-if="currentStep === 2" class="scm-step-content">
      <div class="scm-note">
        <i class="bi bi-info-circle"></i>
        Only events committed by the JVM's JFR in real-time are available.
        CPU profiling events (e.g. jdk.ExecutionSample) collected by async-profiler
        are merged at dump time and do not appear in the live stream.
      </div>
      <EventTypeSelector v-model="localEventTypes" />
    </div>

    <!-- Step 3: Time Range -->
    <div v-if="currentStep === 3" class="scm-step-content">
      <div class="scm-time-fields">
        <div>
          <label class="scm-label">Start</label>
          <div class="scm-start-options">
            <button
              class="scm-option-btn"
              :class="{ active: startMode === 'beginning' }"
              @click="setStartMode('beginning')"
            >
              From beginning
            </button>
            <button
              class="scm-option-btn"
              :class="{ active: startMode === 'now' }"
              @click="setStartMode('now')"
            >
              Now
            </button>
            <button
              class="scm-option-btn"
              :class="{ active: startMode === 'custom' }"
              @click="setStartMode('custom')"
            >
              Custom
            </button>
          </div>
          <input
            v-if="startMode === 'custom'"
            v-model="localStartTime"
            type="datetime-local"
            class="scm-input"
          />
        </div>
        <div>
          <label class="scm-label">End</label>
          <div class="scm-start-options">
            <button
              class="scm-option-btn"
              :class="{ active: endMode === 'now' }"
              :disabled="localContinuous"
              @click="setEndMode('now')"
            >
              Now
            </button>
            <button
              class="scm-option-btn"
              :class="{ active: endMode === 'custom' }"
              :disabled="localContinuous"
              @click="setEndMode('custom')"
            >
              Custom
            </button>
          </div>
          <input
            v-if="endMode === 'custom'"
            v-model="localEndTime"
            type="datetime-local"
            class="scm-input"
            :disabled="localContinuous"
          />
        </div>
      </div>
      <div class="scm-toggle-row">
        <label class="scm-toggle">
          <input
            v-model="localContinuous"
            type="checkbox"
            class="scm-toggle-input"
          />
          <span class="scm-toggle-track"><span class="scm-toggle-thumb"></span></span>
        </label>
        <div>
          <div class="scm-toggle-label">Continuous streaming</div>
          <div class="scm-toggle-desc">Keep stream open for new events after initial replay</div>
        </div>
      </div>

      <div class="scm-max-events-row">
        <label class="scm-label">Event Buffer Size</label>
        <div class="scm-max-events-options">
          <button
            v-for="option in maxEventsOptions"
            :key="option"
            class="scm-option-btn"
            :class="{ active: localMaxEvents === option }"
            @click="localMaxEvents = option"
          >
            {{ option.toLocaleString() }}
          </button>
        </div>
        <div class="scm-toggle-desc">Maximum number of events kept in the table. Oldest events are discarded when the limit is reached.</div>
      </div>
    </div>
  </GenericModal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import GenericModal from '@/components/GenericModal.vue'
import Badge from '@/components/Badge.vue'
import LoadingState from '@/components/LoadingState.vue'
import EmptyState from '@/components/EmptyState.vue'
import SearchInput from '@/components/form/SearchInput.vue'
import EventTypeSelector from '@/components/streaming/EventTypeSelector.vue'
import '@/styles/shared-components.css'
import FormattingService from '@/services/FormattingService'
import ProjectInstanceClient from '@/services/api/ProjectInstanceClient'
import type ProjectInstance from '@/services/api/model/ProjectInstance'
import type ProjectInstanceSession from '@/services/api/model/ProjectInstanceSession'

interface InstanceGroup {
  instance: ProjectInstance
  sessions: ProjectInstanceSession[]
}

export type StartMode = 'beginning' | 'now' | 'custom'
export type EndMode = 'now' | 'custom'

export interface SelectedSession {
  id: string
  sessionInstance: string
}

export interface StreamingConfig {
  sessions: SelectedSession[]
  eventTypes: string[]
  startMode: StartMode
  startTime: string
  endMode: EndMode
  endTime: string
  continuous: boolean
  maxEvents: number
}

const props = defineProps<{
  show: boolean
  workspaceId: string
  projectId: string
  config: StreamingConfig
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  apply: [config: StreamingConfig]
}>()

// Wizard state
const currentStep = ref(1)

const wizardSteps = [
  { num: 1, label: 'Session' },
  { num: 2, label: 'Event Types' },
  { num: 3, label: 'Time Range' }
]

const canAdvance = computed(() => {
  if (currentStep.value === 1) return localSessions.value.length > 0
  if (currentStep.value === 2) return localEventTypes.value.length > 0
  return true
})

const applyEnabled = computed(() => {
  if (localSessions.value.length === 0 || localEventTypes.value.length === 0) return false
  if (startMode.value === 'custom' && !localStartTime.value) return false
  if (!localContinuous.value && endMode.value === 'custom' && !localEndTime.value) return false

  if (!localContinuous.value && endMode.value === 'custom') {
    const endMs = Date.parse(localEndTime.value)
    if (Number.isNaN(endMs) || endMs > Date.now()) return false
    if (startMode.value === 'custom') {
      const startMs = Date.parse(localStartTime.value)
      if (!Number.isNaN(startMs) && startMs >= endMs) return false
    }
  }
  return true
})

function nextStep() {
  if (canAdvance.value && currentStep.value < 3) currentStep.value++
}

function prevStep() {
  if (currentStep.value > 1) currentStep.value--
}

function goToStep(step: number) {
  if (step < currentStep.value) currentStep.value = step
}

// Session browser state
const sessionsLoading = ref(false)
const sessionSearchQuery = ref('')
const instanceGroups = ref<InstanceGroup[]>([])
const maxVisibleInstances = ref(5)

// Config state
const localSessions = ref<SelectedSession[]>([])
const localEventTypes = ref<string[]>([])
const startMode = ref<StartMode>('beginning')
const localStartTime = ref('')
const endMode = ref<EndMode>('now')
const localEndTime = ref('')
const localContinuous = ref(false)
const localMaxEvents = ref(1000)
const maxEventsOptions = [500, 1000, 5000, 10000]

const allFilteredGroups = computed(() => {
  if (!sessionSearchQuery.value) return instanceGroups.value

  const q = sessionSearchQuery.value.toLowerCase()
  return instanceGroups.value
    .map((group) => ({
      instance: group.instance,
      sessions: group.sessions.filter(
        (s) => s.id.toLowerCase().includes(q)
      )
    }))
    .filter((group) => group.sessions.length > 0)
})

const filteredGroups = computed(() => {
  return allFilteredGroups.value.slice(0, maxVisibleInstances.value)
})

const hasMoreInstances = computed(() => {
  return allFilteredGroups.value.length > maxVisibleInstances.value
})

const hiddenInstanceCount = computed(() => {
  return allFilteredGroups.value.length - maxVisibleInstances.value
})

// Sync local state + load sessions when modal opens
watch(
  () => props.show,
  (visible) => {
    if (visible) {
      currentStep.value = 1
      maxVisibleInstances.value = 5
      localSessions.value = props.config.sessions.map((s) => ({ ...s }))
      localEventTypes.value = [...props.config.eventTypes]
      startMode.value = props.config.startMode
      localStartTime.value = props.config.startTime
      endMode.value = props.config.endMode
      localEndTime.value = props.config.endTime
      localContinuous.value = props.config.continuous
      localMaxEvents.value = props.config.maxEvents
      sessionSearchQuery.value = ''
      loadSessions()
    }
  }
)

async function loadSessions() {
  sessionsLoading.value = true
  try {
    const client = new ProjectInstanceClient(props.workspaceId, props.projectId)
    const instances = await client.list()

    instances.sort((a, b) => {
      if (a.status === 'ACTIVE' && b.status !== 'ACTIVE') return -1
      if (a.status !== 'ACTIVE' && b.status === 'ACTIVE') return 1
      return b.createdAt - a.createdAt
    })

    const groups: InstanceGroup[] = []
    for (const instance of instances) {
      const sessions = await client.getSessions(instance.id)
      if (sessions.length > 0) {
        sessions.sort((a, b) => b.createdAt - a.createdAt)
        groups.push({ instance, sessions })
      }
    }
    instanceGroups.value = groups
  } finally {
    sessionsLoading.value = false
  }
}

function isSessionSelected(sessionId: string): boolean {
  return localSessions.value.some((s) => s.id === sessionId)
}

function toggleSession(session: ProjectInstanceSession, instance: ProjectInstance) {
  const idx = localSessions.value.findIndex((s) => s.id === session.id)
  if (idx >= 0) {
    localSessions.value.splice(idx, 1)
  } else {
    localSessions.value.push({ id: session.id, sessionInstance: instance.hostname })
  }
}

function clearSelectedSessions() {
  localSessions.value = []
}

watch(localContinuous, (on) => {
  if (on) {
    endMode.value = 'now'
    localEndTime.value = ''
  }
})

function setStartMode(mode: StartMode) {
  startMode.value = mode
  if (mode !== 'custom') {
    localStartTime.value = ''
  }
}

function setEndMode(mode: EndMode) {
  endMode.value = mode
  if (mode !== 'custom') {
    localEndTime.value = ''
  }
}

function apply() {
  emit('apply', {
    sessions: localSessions.value.map((s) => ({ ...s })),
    eventTypes: localEventTypes.value,
    startMode: startMode.value,
    startTime: localStartTime.value,
    endMode: endMode.value,
    endTime: localEndTime.value,
    continuous: localContinuous.value,
    maxEvents: localMaxEvents.value
  })
  emit('update:show', false)
}
</script>

<style scoped>
/* ===== Modal sizing override ===== */
:deep(.scm-dialog) {
  max-width: min(1400px, 90vw);
  width: min(1400px, 90vw);
  height: 90vh;
  margin: 5vh auto;
}

:deep(.scm-dialog) .modal-content {
  height: 100%;
  max-height: 100%;
}

:deep(.scm-dialog) .modal-body {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

/* ===== Wizard Steps ===== */
.scm-wizard-steps {
  display: flex;
  gap: 0;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--color-border-light);
}

.scm-wizard-step {
  flex: 1;
  padding: 10px 12px;
  text-align: center;
  font-size: 0.78rem;
  font-weight: 500;
  color: var(--color-text-light);
  border-bottom: 2px solid transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.2s;
}

.scm-step--active {
  color: var(--color-primary);
  font-weight: 600;
  border-bottom-color: var(--color-primary);
}

.scm-step--done {
  color: var(--color-success);
  border-bottom-color: var(--color-success);
}

.scm-step--clickable {
  cursor: pointer;
}

.scm-step--clickable:hover {
  background: var(--color-light);
}

.scm-step-num {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.68rem;
  font-weight: 700;
  flex-shrink: 0;
  background: var(--color-border);
  color: var(--color-text-light);
}

.scm-step--active .scm-step-num {
  background: var(--color-primary);
  color: var(--color-white);
}

.scm-step--done .scm-step-num {
  background: var(--color-success);
  color: var(--color-white);
  font-size: 0.75rem;
}

/* ===== Step Content ===== */
.scm-step-content {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.scm-note {
  font-size: 0.72rem;
  color: var(--color-muted);
  margin-bottom: 10px;
  display: flex;
  align-items: baseline;
  gap: 6px;
  line-height: 1.4;
}

.scm-note i {
  font-size: 0.7rem;
  flex-shrink: 0;
}

/* ===== Session Search ===== */
.scm-session-search {
  margin-bottom: 10px;
}

/* ===== Selection Bar ===== */
.scm-selection-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  margin-bottom: 8px;
  background: var(--color-primary-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 0.78rem;
  color: var(--color-body);
}

.scm-clear-link {
  font-size: 0.75rem;
  color: var(--color-primary);
  text-decoration: none;
}

.scm-clear-link:hover {
  text-decoration: underline;
}

/* ===== Session List ===== */
.scm-session-list {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
}

.scm-instance-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  margin-top: 16px;
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--color-body);
}

.scm-instance-header:first-child {
  margin-top: 0;
}

.scm-instance-host {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== Session Cards (uses shared .session-card utilities) ===== */
.scm-session-card-spacing {
  margin-bottom: 6px;
}

.scm-session-selected .scm-check {
  color: var(--color-primary);
}

.scm-check {
  font-size: 0.95rem;
  color: var(--color-text-light);
  flex-shrink: 0;
}

.scm-session-id-line {
  display: flex;
  align-items: baseline;
  gap: 6px;
  overflow: hidden;
}

.scm-session-id-label {
  font-size: 0.68rem;
  color: var(--color-text-light);
  font-weight: 500;
  flex-shrink: 0;
}

.scm-session-id {
  font-family: var(--font-monospace);
  font-size: 0.78rem;
  font-weight: 500;
  color: var(--color-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scm-session-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 0.67rem;
  color: var(--color-text-muted);
  margin-top: 2px;
}

.scm-min-width-0 {
  min-width: 0;
}

/* Instance header dots: green = JVM online, grey = JVM offline */
.scm-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
}

.dot-active {
  background-color: var(--color-success);
}

.dot-inactive {
  background-color: var(--color-muted);
}

.scm-load-more {
  text-align: center;
  padding: 8px 0;
}

.scm-load-more-btn {
  padding: 6px 16px;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-primary);
  font-size: 0.78rem;
  font-weight: 500;
  font-family: var(--font-base);
  cursor: pointer;
  transition: var(--transition-fast);
}

.scm-load-more-btn:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}

.scm-no-results {
  text-align: center;
  padding: 20px;
  color: var(--color-muted);
  font-size: 0.8125rem;
}

/* ===== Time Range ===== */
.scm-time-fields {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.scm-label {
  display: block;
  font-size: 0.72rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  color: var(--color-muted);
  margin-bottom: 6px;
}

.scm-start-options {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}

.scm-option-btn {
  height: 36px;
  min-width: 120px;
  padding: 0 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-white);
  font-family: var(--font-base);
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--color-muted);
  cursor: pointer;
  transition: var(--transition-fast);
}

.scm-option-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.scm-option-btn.active {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.scm-option-btn:disabled {
  opacity: 0.4;
  cursor: default;
  pointer-events: none;
}

.scm-input {
  width: 100%;
  height: 40px;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-family: var(--font-base);
  font-size: 0.9rem;
  color: var(--color-body);
  background: var(--color-white);
  outline: none;
  transition: all var(--transition-fast);
}

.scm-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-light);
}

.scm-input:disabled {
  background: var(--color-light);
  opacity: 0.5;
}

/* ===== Toggle ===== */
.scm-toggle-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--color-border-light);
}

.scm-toggle {
  position: relative;
  display: inline-block;
  flex-shrink: 0;
  cursor: pointer;
}

.scm-toggle-input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

.scm-toggle-track {
  display: block;
  width: 36px;
  height: 20px;
  background: var(--color-border);
  border-radius: 10px;
  position: relative;
  transition: background 0.2s;
}

.scm-toggle-input:checked + .scm-toggle-track {
  background: var(--color-primary);
}

.scm-toggle-thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  background: var(--color-white);
  border-radius: 50%;
  box-shadow: var(--shadow-sm);
  transition: left 0.2s;
}

.scm-toggle-input:checked + .scm-toggle-track .scm-toggle-thumb {
  left: 18px;
}

.scm-toggle-label {
  font-size: 0.875rem;
  color: var(--color-body);
  font-weight: 600;
}

.scm-toggle-desc {
  font-size: 0.75rem;
  color: var(--color-muted);
  margin-top: 2px;
}

/* ===== Max Events ===== */
.scm-max-events-row {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--color-border-light);
}

.scm-max-events-options {
  display: flex;
  gap: 6px;
  margin-bottom: 6px;
}
</style>
