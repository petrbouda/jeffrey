<template>
  <div class="rsp-root">
    <SearchInput v-model="searchQuery" placeholder="Search by session ID..." class="rsp-search" />

    <div class="rsp-list">
      <LoadingState v-if="loading" message="Loading sessions..." />

      <EmptyState
        v-else-if="instanceGroups.length === 0"
        title="No sessions found"
        description="No instances with sessions are available for this project."
        icon="bi-collection-play"
      />

      <template v-else>
        <template v-for="group in filteredGroups" :key="group.instance.id">
          <div class="rsp-instance-header">
            <span class="rsp-dot" :class="group.instance.status === 'ACTIVE' ? 'rsp-dot-active' : 'rsp-dot-inactive'"></span>
            <span class="rsp-instance-host">{{ group.instance.instanceName }}</span>
          </div>
          <div
            v-for="session in group.sessions"
            :key="session.id"
            class="rsp-session-row"
            :class="{ 'rsp-session-row--selected': selected?.id === session.id }"
            @click="pick(session, group.instance)"
          >
            <i
              class="bi rsp-check"
              :class="selected?.id === session.id ? 'bi-record-circle' : 'bi-circle'"
            ></i>
            <span class="rsp-session-id">{{ session.id }}</span>
            <span class="rsp-session-meta">
              <i class="bi bi-clock"></i>
              {{ FormattingService.formatRelativeTime(session.createdAt) }}
              <template v-if="session.finishedAt">
                · {{ FormattingService.formatDurationFromMillis(session.createdAt, session.finishedAt) }}
              </template>
            </span>
            <Badge
              :value="session.isActive ? 'Active' : 'Finished'"
              :variant="session.isActive ? 'status-active' : 'status-finished'"
              size="xs"
            />
          </div>
        </template>

        <div v-if="hasMoreInstances" class="rsp-load-more">
          <button class="rsp-load-more-btn" @click="maxVisibleInstances += 5">
            Show {{ hiddenInstanceCount }} more instance{{ hiddenInstanceCount > 1 ? 's' : '' }}
          </button>
        </div>

        <div v-if="filteredGroups.length === 0 && searchQuery" class="rsp-no-results">
          No sessions matching "{{ searchQuery }}"
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Badge from '@/components/Badge.vue'
import LoadingState from '@/components/LoadingState.vue'
import EmptyState from '@/components/EmptyState.vue'
import SearchInput from '@/components/form/SearchInput.vue'
import FormattingService from '@/services/FormattingService'
import ProjectInstanceClient from '@/services/api/ProjectInstanceClient'
import type ProjectInstance from '@/services/api/model/ProjectInstance'
import type ProjectInstanceSession from '@/services/api/model/ProjectInstanceSession'
import type { SelectedSession } from './streamingTypes'

interface InstanceGroup {
  instance: ProjectInstance
  sessions: ProjectInstanceSession[]
}

const props = defineProps<{
  workspaceId: string
  projectId: string
  selected: SelectedSession | null
}>()

const emit = defineEmits<{
  pick: [value: SelectedSession]
}>()

const loading = ref(false)
const searchQuery = ref('')
const instanceGroups = ref<InstanceGroup[]>([])
const maxVisibleInstances = ref(5)

const allFilteredGroups = computed(() => {
  if (!searchQuery.value) return instanceGroups.value
  const q = searchQuery.value.toLowerCase()
  return instanceGroups.value
    .map((group) => ({
      instance: group.instance,
      sessions: group.sessions.filter((s) => s.id.toLowerCase().includes(q))
    }))
    .filter((group) => group.sessions.length > 0)
})

const filteredGroups = computed(() => allFilteredGroups.value.slice(0, maxVisibleInstances.value))
const hasMoreInstances = computed(() => allFilteredGroups.value.length > maxVisibleInstances.value)
const hiddenInstanceCount = computed(() => allFilteredGroups.value.length - maxVisibleInstances.value)

async function loadSessions() {
  loading.value = true
  try {
    const client = new ProjectInstanceClient(props.workspaceId, props.projectId)
    const instances = await client.list(true)

    instances.sort((a, b) => {
      if (a.status === 'ACTIVE' && b.status !== 'ACTIVE') return -1
      if (a.status !== 'ACTIVE' && b.status === 'ACTIVE') return 1
      return b.createdAt - a.createdAt
    })

    const groups: InstanceGroup[] = []
    for (const instance of instances) {
      const sessions = instance.sessions ?? []
      if (sessions.length > 0) {
        sessions.sort((a, b) => b.createdAt - a.createdAt)
        groups.push({ instance, sessions })
      }
    }
    instanceGroups.value = groups
  } finally {
    loading.value = false
  }
}

function pick(session: ProjectInstanceSession, instance: ProjectInstance) {
  emit('pick', { id: session.id, sessionInstance: instance.instanceName })
}

onMounted(loadSessions)
</script>

<style scoped>
.rsp-root {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rsp-search {
  flex-shrink: 0;
}

.rsp-list {
  max-height: 320px;
  overflow-y: auto;
  padding-right: 4px;
}

.rsp-instance-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0 6px;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-body);
}
.rsp-instance-header:first-child { margin-top: 0; }

.rsp-instance-host {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rsp-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
}
.rsp-dot-active { background-color: var(--color-success); }
.rsp-dot-inactive { background-color: var(--color-muted); }

.rsp-session-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  margin-bottom: 4px;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: var(--transition-fast);
}
.rsp-session-row:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-lighter);
}
.rsp-session-row--selected {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}

.rsp-check {
  font-size: 0.95rem;
  color: var(--color-text-light);
  flex-shrink: 0;
}
.rsp-session-row--selected .rsp-check { color: var(--color-primary); }

.rsp-session-id {
  font-family: var(--font-monospace);
  font-size: 0.78rem;
  font-weight: 500;
  color: var(--color-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.rsp-session-meta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 0.68rem;
  color: var(--color-text-muted);
  white-space: nowrap;
}
.rsp-session-meta i { font-size: 0.64rem; }

.rsp-load-more {
  text-align: center;
  padding: 8px 0;
}
.rsp-load-more-btn {
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
.rsp-load-more-btn:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}

.rsp-no-results {
  text-align: center;
  padding: 20px;
  color: var(--color-muted);
  font-size: 0.8125rem;
}
</style>
