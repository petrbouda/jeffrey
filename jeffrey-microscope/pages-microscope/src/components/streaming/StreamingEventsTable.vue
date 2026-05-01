<template>
  <div v-if="events.length === 0">
    <slot name="empty" />
  </div>

  <template v-else>
    <div class="table-responsive">
      <table class="table table-sm table-hover mb-0">
        <thead>
          <tr>
            <th style="width: 180px">Timestamp</th>
            <th style="width: 1px" class="text-nowrap">Event Type</th>
            <th>Fields</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(event, index) in displayedEvents"
            :key="index"
            v-bind="rowAttrs?.(event) ?? {}"
          >
            <td class="text-nowrap">
              <code>{{ FormattingService.formatTimestamp(event.timestamp) }}</code>
            </td>
            <td class="text-nowrap">
              <span class="event-type-badge" :style="{ backgroundColor: eventTypeColor(event.eventType) + '18', color: eventTypeColor(event.eventType), borderColor: eventTypeColor(event.eventType) + '40' }">{{ event.eventType }}</span>
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
  </template>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import FormattingService from '@/services/FormattingService'
import Utils from '@/services/Utils'
import type { StreamingEvent } from '@/services/api/EventStreamingClient'

const EVENT_TYPE_COLORS = [
  '#5e64ff', '#0d9488', '#f59e0b', '#8b5cf6', '#e63757',
  '#39afd1', '#fd7e14', '#00d27a', '#6f42c1', '#daa520'
]

const props = defineProps<{
  events: StreamingEvent[]
  eventTypes: string[]
  rowAttrs?: (event: StreamingEvent) => Record<string, unknown>
}>()

const maxDisplayed = ref(200)

const displayedEvents = computed(() => {
  return props.events.slice(-maxDisplayed.value).reverse()
})

const eventTypeColorMap = computed(() => {
  const map: Record<string, number> = {}
  props.eventTypes.forEach((et, i) => { map[et] = i % EVENT_TYPE_COLORS.length })
  return map
})

function eventTypeColor(et: string): string {
  return EVENT_TYPE_COLORS[eventTypeColorMap.value[et] ?? 0]
}

</script>

<style scoped>
.event-type-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  font-family: var(--font-monospace);
  font-size: 0.73rem;
  font-weight: 500;
  white-space: nowrap;
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
