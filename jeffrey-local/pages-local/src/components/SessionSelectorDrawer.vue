<template>
  <Teleport to="body">
    <Transition name="drawer-fade">
      <div v-if="show" class="drawer-overlay" @click="close"></div>
    </Transition>
    <Transition name="drawer-slide">
      <div v-if="show" class="drawer">
        <div class="drawer-header">
          <h3>Select Session</h3>
          <button class="drawer-close" @click="close">&times;</button>
        </div>
        <div class="drawer-search">
          <input
            v-model="searchQuery"
            type="text"
            placeholder="Search by session ID or hostname..."
          />
        </div>
        <div class="drawer-body">
          <LoadingState v-if="loading" message="Loading sessions..." />

          <EmptyState
            v-else-if="instanceGroups.length === 0"
            title="No sessions found"
            description="No instances with sessions are available for this project."
            icon="bi-broadcast"
          />

          <template v-else>
            <div v-for="group in filteredGroups" :key="group.instance.id">
              <div class="drawer-instance-header">
                <span class="instance-dot" :class="group.instance.status === 'ACTIVE' ? 'dot-active' : 'dot-inactive'"></span>
                <span class="instance-hostname">{{ group.instance.hostname }}</span>
                <Badge :value="group.instance.status" :variant="statusVariant(group.instance.status)" size="xs" />
              </div>
              <div
                v-for="session in group.sessions"
                :key="session.id"
                class="drawer-session-card"
                :class="{
                  'active-session': session.isActive,
                  'selected': selectedSessionId === session.id
                }"
                @click="selectSession(session, group.instance)"
              >
                <div class="session-top">
                  <span class="session-dot" :class="session.isActive ? 'dot-active dot-pulse' : 'dot-inactive'"></span>
                  <span class="session-id">{{ session.id }}</span>
                  <Badge
                    :value="session.isActive ? 'Active' : 'Finished'"
                    :variant="session.isActive ? 'active' : 'finished'"
                    size="xxs"
                  />
                </div>
                <div class="session-bottom">
                  <span>Started {{ FormattingService.formatRelativeTime(session.createdAt) }}</span>
                  <span>Duration: {{ FormattingService.formatDurationFromMillis(session.createdAt, session.finishedAt) }}</span>
                </div>
              </div>
            </div>

            <div v-if="filteredGroups.length === 0" class="no-results">
              No sessions matching "{{ searchQuery }}"
            </div>
          </template>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Badge from '@/components/Badge.vue'
import LoadingState from '@/components/LoadingState.vue'
import EmptyState from '@/components/EmptyState.vue'
import FormattingService from '@/services/FormattingService'
import ProjectInstanceClient from '@/services/api/ProjectInstanceClient'
import type ProjectInstance from '@/services/api/model/ProjectInstance'
import type { ProjectInstanceStatus } from '@/services/api/model/ProjectInstance'
import type ProjectInstanceSession from '@/services/api/model/ProjectInstanceSession'

interface InstanceGroup {
  instance: ProjectInstance
  sessions: ProjectInstanceSession[]
}

export interface SessionSelection {
  sessionId: string
  hostname: string
  isActive: boolean
}

const props = defineProps<{
  show: boolean
  workspaceId: string
  projectId: string
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  select: [selection: SessionSelection]
}>()

const loading = ref(false)
const searchQuery = ref('')
const selectedSessionId = ref<string | null>(null)
const instanceGroups = ref<InstanceGroup[]>([])

const filteredGroups = computed(() => {
  if (!searchQuery.value) return instanceGroups.value

  const q = searchQuery.value.toLowerCase()
  return instanceGroups.value
    .map((group) => ({
      instance: group.instance,
      sessions: group.sessions.filter(
        (s) =>
          s.id.toLowerCase().includes(q) || group.instance.hostname.toLowerCase().includes(q)
      )
    }))
    .filter((group) => group.sessions.length > 0)
})

watch(
  () => props.show,
  (visible) => {
    if (visible) {
      searchQuery.value = ''
      loadSessions()
    }
  }
)

async function loadSessions() {
  loading.value = true
  try {
    const client = new ProjectInstanceClient(props.workspaceId, props.projectId)
    const instances = await client.list()

    // Sort instances: ACTIVE first, then by createdAt descending
    instances.sort((a, b) => {
      if (a.status === 'ACTIVE' && b.status !== 'ACTIVE') return -1
      if (a.status !== 'ACTIVE' && b.status === 'ACTIVE') return 1
      return b.createdAt - a.createdAt
    })

    const groups: InstanceGroup[] = []
    for (const instance of instances) {
      const sessions = await client.getSessions(instance.id)
      if (sessions.length > 0) {
        // Sort sessions: newest first
        sessions.sort((a, b) => b.createdAt - a.createdAt)
        groups.push({ instance, sessions })
      }
    }
    instanceGroups.value = groups
  } finally {
    loading.value = false
  }
}

function selectSession(session: ProjectInstanceSession, instance: ProjectInstance) {
  selectedSessionId.value = session.id
  emit('select', {
    sessionId: session.id,
    hostname: instance.hostname,
    isActive: session.isActive ?? false
  })
  close()
}

function close() {
  emit('update:show', false)
}

function statusVariant(status: ProjectInstanceStatus): string {
  switch (status) {
    case 'ACTIVE':
      return 'active'
    case 'FINISHED':
      return 'finished'
    case 'PENDING':
      return 'warning'
    case 'EXPIRED':
      return 'grey'
    default:
      return 'secondary'
  }
}
</script>

<style scoped>
.drawer-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  z-index: 1050;
}

.drawer {
  position: fixed;
  top: 0;
  right: 0;
  width: 420px;
  max-width: 100vw;
  height: 100vh;
  background: var(--color-white);
  box-shadow: -8px 0 32px rgba(0, 0, 0, 0.12);
  z-index: 1051;
  display: flex;
  flex-direction: column;
}

.drawer-header {
  padding: 20px 24px;
  border-bottom: 1px solid var(--color-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.drawer-header h3 {
  font-size: 1rem;
  font-weight: 600;
  margin: 0;
}

.drawer-close {
  width: 32px;
  height: 32px;
  border: none;
  background: var(--color-light);
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 1.25rem;
  color: var(--color-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: var(--transition-fast);
  line-height: 1;
}

.drawer-close:hover {
  background: var(--color-border);
  color: var(--color-body);
}

.drawer-search {
  padding: 12px 24px;
  border-bottom: 1px solid var(--color-border);
}

.drawer-search input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 0.8125rem;
  font-family: inherit;
  color: var(--color-body);
}

.drawer-search input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-light);
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 24px;
}

.drawer-instance-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  margin-top: 20px;
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--color-body);
}

.drawer-instance-header:first-child {
  margin-top: 0;
}

.instance-hostname {
  flex: 1;
}

.instance-dot,
.session-dot {
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

.dot-pulse {
  animation: dotPulse 2s infinite;
}

@keyframes dotPulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(0, 210, 122, 0.4);
  }
  50% {
    box-shadow: 0 0 0 4px rgba(0, 210, 122, 0);
  }
}

.drawer-session-card {
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  margin-bottom: 8px;
  cursor: pointer;
  transition: var(--transition-fast);
}

.drawer-session-card:hover {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-sm);
}

.drawer-session-card.active-session {
  border-left: 3px solid var(--color-success);
}

.drawer-session-card.selected {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
  box-shadow: 0 0 0 3px rgba(94, 100, 255, 0.1);
}

.session-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.session-id {
  font-family: var(--font-monospace);
  font-size: 0.8125rem;
  font-weight: 500;
}

.session-bottom {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 0.6875rem;
  color: var(--color-muted);
  padding-left: 16px;
}

.no-results {
  text-align: center;
  padding: 24px;
  color: var(--color-muted);
  font-size: 0.8125rem;
}

/* Transitions */
.drawer-fade-enter-active,
.drawer-fade-leave-active {
  transition: opacity 0.2s ease;
}

.drawer-fade-enter-from,
.drawer-fade-leave-to {
  opacity: 0;
}

.drawer-slide-enter-active,
.drawer-slide-leave-active {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.drawer-slide-enter-from,
.drawer-slide-leave-to {
  transform: translateX(100%);
}
</style>
