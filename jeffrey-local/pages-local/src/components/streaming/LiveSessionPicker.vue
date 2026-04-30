<template>
  <div class="lsp-root">
    <div class="lsp-search-row">
      <SearchInput v-model="searchQuery" placeholder="Search by session ID..." class="lsp-search" />
      <div v-if="selected.length > 0" class="lsp-selection-count">
        <strong>{{ selected.length }}</strong>
        selected
        <a href="#" class="lsp-clear-link" @click.prevent="clearAll">Clear</a>
      </div>
    </div>

    <div class="lsp-list">
      <LoadingState v-if="loading" message="Loading sessions..." />

      <EmptyState
        v-else-if="instanceGroups.length === 0"
        title="No active sessions"
        description="No active instances with running sessions are available for live streaming."
        icon="bi-broadcast"
      />

      <template v-else>
        <template v-for="group in filteredGroups" :key="group.instance.id">
          <div class="lsp-instance-header">
            <span class="lsp-dot" :class="group.instance.status === 'ACTIVE' ? 'lsp-dot-active' : 'lsp-dot-inactive'"></span>
            <span class="lsp-instance-host">{{ group.instance.instanceName }}</span>
          </div>
          <div
            v-for="session in group.sessions"
            :key="session.id"
            class="lsp-session-row"
            :class="{ 'lsp-session-row--selected': isSelected(session.id) }"
            @click="toggle(session, group.instance)"
          >
            <i
              class="bi lsp-check"
              :class="isSelected(session.id) ? 'bi-check-square-fill' : 'bi-square'"
            ></i>
            <span class="lsp-session-id">{{ session.id }}</span>
            <span class="lsp-session-meta">
              <i class="bi bi-clock"></i>
              {{ FormattingService.formatRelativeTime(session.createdAt) }}
            </span>
            <Badge value="Active" variant="status-active" size="xs" />
          </div>
        </template>

        <div v-if="hasMoreInstances" class="lsp-load-more">
          <button class="lsp-load-more-btn" @click="maxVisibleInstances += 5">
            Show {{ hiddenInstanceCount }} more instance{{ hiddenInstanceCount > 1 ? 's' : '' }}
          </button>
        </div>

        <div v-if="filteredGroups.length === 0 && searchQuery" class="lsp-no-results">
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
  serverId: string
  workspaceId: string
  projectId: string
  selected: SelectedSession[]
}>()

const emit = defineEmits<{
  'update:selected': [value: SelectedSession[]]
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
    const client = new ProjectInstanceClient(props.serverId, props.workspaceId, props.projectId)
    const instances = await client.list(true)

    instances.sort((a, b) => {
      if (a.status === 'ACTIVE' && b.status !== 'ACTIVE') return -1
      if (a.status !== 'ACTIVE' && b.status === 'ACTIVE') return 1
      return b.createdAt - a.createdAt
    })

    const groups: InstanceGroup[] = []
    for (const instance of instances) {
      const sessions = (instance.sessions ?? []).filter((s) => s.isActive === true)
      if (sessions.length > 0) {
        sessions.sort((a, b) => b.createdAt - a.createdAt)
        groups.push({ instance, sessions })
      }
    }
    instanceGroups.value = groups

    // Drop any pre-seeded selection that is no longer visible (e.g. session
    // became inactive between page mount and the picker opening).
    const visibleIds = new Set(groups.flatMap((g) => g.sessions.map((s) => s.id)))
    const pruned = props.selected.filter((s) => visibleIds.has(s.id))
    if (pruned.length !== props.selected.length) {
      emit('update:selected', pruned)
    }
  } finally {
    loading.value = false
  }
}

function isSelected(id: string): boolean {
  return props.selected.some((s) => s.id === id)
}

function toggle(session: ProjectInstanceSession, instance: ProjectInstance) {
  if (isSelected(session.id)) {
    emit('update:selected', props.selected.filter((s) => s.id !== session.id))
  } else {
    emit('update:selected', [
      ...props.selected,
      { id: session.id, sessionInstance: instance.instanceName }
    ])
  }
}

function clearAll() {
  emit('update:selected', [])
}

onMounted(loadSessions)
</script>

<style scoped>
.lsp-root {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.lsp-search-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.lsp-search {
  flex: 1;
  min-width: 180px;
}

.lsp-selection-count {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px;
  background: var(--color-primary-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  color: var(--color-body);
  white-space: nowrap;
}

.lsp-clear-link {
  font-size: 0.72rem;
  color: var(--color-primary);
  text-decoration: none;
}
.lsp-clear-link:hover {
  text-decoration: underline;
}

.lsp-list {
  max-height: 320px;
  overflow-y: auto;
  padding-right: 4px;
}

.lsp-instance-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0 6px;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-body);
}
.lsp-instance-header:first-child { margin-top: 0; }

.lsp-instance-host {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lsp-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
}
.lsp-dot-active { background-color: var(--color-success); }
.lsp-dot-inactive { background-color: var(--color-muted); }

.lsp-session-row {
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
.lsp-session-row:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-lighter);
}
.lsp-session-row--selected {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}

.lsp-check {
  font-size: 0.95rem;
  color: var(--color-text-light);
  flex-shrink: 0;
}
.lsp-session-row--selected .lsp-check {
  color: var(--color-primary);
}

.lsp-session-id {
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

.lsp-session-meta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 0.68rem;
  color: var(--color-text-muted);
  white-space: nowrap;
}
.lsp-session-meta i { font-size: 0.64rem; }

.lsp-load-more {
  text-align: center;
  padding: 8px 0;
}
.lsp-load-more-btn {
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
.lsp-load-more-btn:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}

.lsp-no-results {
  text-align: center;
  padding: 20px;
  color: var(--color-muted);
  font-size: 0.8125rem;
}
</style>
