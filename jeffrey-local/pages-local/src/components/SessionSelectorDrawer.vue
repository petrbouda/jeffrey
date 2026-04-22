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
            placeholder="Search by session ID or instance name..."
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
                <span class="instance-name">{{ group.instance.instanceName }}</span>
              </div>
              <div
                v-for="session in group.sessions"
                :key="session.id"
                class="session-card drawer-session-card-spacing"
                :class="[
                  session.isActive ? 'session-card--active' : 'session-card--finished',
                  { 'drawer-session-selected': selectedSessionId === session.id }
                ]"
                @click="selectSession(session, group.instance)"
              >
                <div class="session-card-body session-card-body--compact">
                  <div class="d-flex align-items-center justify-content-between gap-2">
                    <div class="d-flex align-items-center gap-2 flex-grow-1 drawer-min-width-0">
                      <div
                        class="session-card-icon session-card-icon--compact"
                        :class="session.isActive ? 'session-card-icon--active' : 'session-card-icon--finished'"
                      >
                        <i class="bi bi-folder2"></i>
                      </div>
                      <div class="flex-grow-1 drawer-min-width-0">
                        <div class="drawer-session-id-line">
                          <span class="drawer-session-id-label">Session:</span>
                          <span class="drawer-session-id">{{ session.id }}</span>
                        </div>
                        <div class="drawer-session-meta">
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
import '@/styles/shared-components.css'
import type ProjectInstance from '@/services/api/model/ProjectInstance'
import type ProjectInstanceSession from '@/services/api/model/ProjectInstanceSession'

interface InstanceGroup {
  instance: ProjectInstance
  sessions: ProjectInstanceSession[]
}

export interface SessionSelection {
  sessionId: string
  sessionInstance: string
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
          s.id.toLowerCase().includes(q) || group.instance.instanceName.toLowerCase().includes(q)
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
    const instances = await client.list(true)

    // Sort instances: ACTIVE first, then by createdAt descending
    instances.sort((a, b) => {
      if (a.status === 'ACTIVE' && b.status !== 'ACTIVE') return -1
      if (a.status !== 'ACTIVE' && b.status === 'ACTIVE') return 1
      return b.createdAt - a.createdAt
    })

    const groups: InstanceGroup[] = []
    for (const instance of instances) {
      const sessions = instance.sessions ?? []
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
    sessionInstance: instance.instanceName,
    isActive: session.isActive ?? false
  })
  close()
}

function close() {
  emit('update:show', false)
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

.instance-name {
  flex: 1;
}

/* Instance header dots: green = JVM online, grey = JVM offline */
.instance-dot {
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

/* Session card spacing + selected glow (uses shared .session-card utilities) */
.drawer-session-card-spacing {
  margin-bottom: 8px;
}

.drawer-session-selected {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(94, 100, 255, 0.18);
}

.drawer-session-id-line {
  display: flex;
  align-items: baseline;
  gap: 6px;
  overflow: hidden;
}

.drawer-session-id-label {
  font-size: 0.68rem;
  color: var(--color-text-light);
  font-weight: 500;
  flex-shrink: 0;
}

.drawer-session-id {
  font-family: var(--font-monospace);
  font-size: 0.78rem;
  font-weight: 500;
  color: var(--color-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drawer-session-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 0.67rem;
  color: var(--color-text-muted);
  margin-top: 2px;
}

.drawer-min-width-0 {
  min-width: 0;
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
