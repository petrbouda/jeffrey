<template>
  <PageHeader
    title="Event Streaming Dashboard"
    description="Subscribe to live JFR events from a remote session's streaming repository. Select a session and event types to start receiving events in real-time."
    icon="bi-broadcast"
  >
    <!-- Connection Controls -->
    <div class="mb-4">
      <div class="row g-3 align-items-end">
        <!-- Session ID -->
        <div class="col-md-4">
          <label class="form-label form-label-sm">Session ID</label>
          <input
            v-model="sessionId"
            type="text"
            class="form-control form-control-sm"
            placeholder="Enter session ID"
            :disabled="connected"
          />
        </div>
        <!-- Event Types -->
        <div class="col-md-4">
          <label class="form-label form-label-sm">Event Types (comma-separated, empty = all)</label>
          <input
            v-model="eventTypesInput"
            type="text"
            class="form-control form-control-sm"
            placeholder="e.g. jdk.GCHeapSummary,jdk.CPULoad"
            :disabled="connected"
          />
        </div>
        <!-- Actions -->
        <div class="col-md-4 d-flex gap-2">
          <button
            v-if="!connected"
            class="btn btn-sm btn-primary"
            @click="startStreaming"
            :disabled="!sessionId"
          >
            <i class="bi bi-play-fill"></i> Subscribe
          </button>
          <button v-else class="btn btn-sm btn-outline-danger" @click="stopStreaming">
            <i class="bi bi-stop-fill"></i> Disconnect
          </button>
          <button
            class="btn btn-sm btn-outline-secondary"
            @click="clearEvents"
            :disabled="events.length === 0"
          >
            <i class="bi bi-trash"></i> Clear
          </button>
        </div>
      </div>
    </div>

    <!-- Status Bar -->
    <div class="status-bar mb-3">
      <div class="d-flex align-items-center gap-3">
        <div class="d-flex align-items-center gap-1">
          <span class="status-dot" :class="connected ? 'status-connected' : 'status-disconnected'"></span>
          <span class="status-text">{{ connected ? 'Connected' : 'Disconnected' }}</span>
        </div>
        <span class="status-stat">
          <i class="bi bi-collection"></i> {{ events.length }} events
        </span>
        <span class="status-stat">
          <i class="bi bi-stack"></i> {{ batchCount }} batches
        </span>
        <span v-if="lastBatchTime" class="status-stat">
          <i class="bi bi-clock"></i> Last batch: {{ FormattingService.formatTimestamp(lastBatchTime) }}
        </span>
      </div>
    </div>

    <!-- Events Table -->
    <EmptyState
      v-if="events.length === 0"
      title="No events received"
      :description="
        connected
          ? 'Waiting for events from the streaming repository...'
          : 'Enter a session ID and click Subscribe to start receiving events.'
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
          <tr v-for="(event, index) in displayedEvents" :key="index">
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
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import Badge from '@/components/Badge.vue'
import FormattingService from '@/services/FormattingService'
import EventStreamingClient, { type StreamingEvent } from '@/services/api/EventStreamingClient'
import Utils from '@/services/Utils'
import { useNavigation } from '@/composables/useNavigation'

const { workspaceId, projectId } = useNavigation()

const sessionId = ref('')
const eventTypesInput = ref('')
const connected = ref(false)
const events = ref<StreamingEvent[]>([])
const batchCount = ref(0)
const lastBatchTime = ref<number | null>(null)
const maxDisplayed = ref(200)

let client: EventStreamingClient | null = null

const displayedEvents = computed(() => {
  return events.value.slice(-maxDisplayed.value).reverse()
})

function startStreaming() {
  if (!sessionId.value || !workspaceId.value || !projectId.value) return

  client = new EventStreamingClient(workspaceId.value, projectId.value)

  const eventTypes = eventTypesInput.value
    .split(',')
    .map((t) => t.trim())
    .filter((t) => t.length > 0)

  client.subscribe(
    sessionId.value,
    eventTypes,
    (batch) => {
      batchCount.value++
      lastBatchTime.value = Date.now()
      events.value.push(...batch)
    },
    () => {
      connected.value = false
    },
    () => {
      connected.value = false
    }
  )

  connected.value = true
}

function stopStreaming() {
  client?.unsubscribe()
  client = null
  connected.value = false
}

function clearEvents() {
  events.value = []
  batchCount.value = 0
  lastBatchTime.value = null
  maxDisplayed.value = 200
}

onUnmounted(() => {
  stopStreaming()
})
</script>

<style scoped>
.status-bar {
  padding: 8px 12px;
  background: var(--color-light);
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  font-size: 0.8125rem;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.status-connected {
  background-color: var(--color-success);
}

.status-disconnected {
  background-color: var(--color-muted);
}

.status-text {
  font-weight: 500;
  color: var(--color-body);
}

.status-stat {
  color: var(--color-muted);
}

.status-stat i {
  font-size: 0.75rem;
}

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
